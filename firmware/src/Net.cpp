#include "Net.h"
#include <DNSServer.h>
#include <ESPmDNS.h>
#include <Preferences.h>
#include <WiFi.h>
#include <atomic>
#include <esp_mac.h>
#include "Config.h"
#include "Log.h"
#include "Power.h"

namespace {
constexpr uint32_t FIRST_CONNECT_MS = 30000;  // no connection by then: open the hotspot
constexpr uint32_t LOST_MS = 180000;          // connection lost this long: open the hotspot
constexpr uint32_t RETRY_MS = 20000;          // reconnect attempts while the hotspot is open
constexpr uint32_t SETUP_ACTIVE_MS = 60000;   // a page request this recent means someone is setting up

DNSServer dns;
String storedSsid, storedPass;
std::atomic<bool> portal{false};
std::atomic<uint32_t> disconnects{0};
bool everConnected = false;
bool wasConnected = false;
bool mdnsStarted = false;
uint32_t disconnectedSince = 0;
uint32_t lastAttempt = 0;

void onWifiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  switch (event) {
    case ARDUINO_EVENT_WIFI_STA_GOT_IP:
      Log::printf("WiFi: got IP %s", WiFi.localIP().toString().c_str());
      break;
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED: {
      if (everConnected) disconnects++;
      // Failed attempts repeat every few seconds; log a repeat at most every 20 s so they
      // do not push everything else out of the log buffer.
      static uint8_t lastReason = 0;
      static uint32_t lastLogged = 0;
      uint8_t reason = info.wifi_sta_disconnected.reason;
      if (reason != lastReason || millis() - lastLogged > 20000) {
        Log::printf("WiFi: disconnected, reason %d", reason);
        lastReason = reason;
        lastLogged = millis();
      }
      break;
    }
    default:
      break;
  }
}

void openPortal() {
  if (portal) return;
  uint8_t mac[6];
  esp_read_mac(mac, ESP_MAC_WIFI_STA);
  char name[32];
  snprintf(name, sizeof(name), SETUP_AP_PREFIX "%02X%02X", mac[4], mac[5]);
  WiFi.setSleep(false);  // modem sleep is not available while the hotspot runs
  WiFi.mode(storedSsid.length() ? WIFI_AP_STA : WIFI_AP);
  WiFi.softAP(name);
  // The core's auto-reconnect retries within seconds of every failure. That keeps the
  // radio hopping off the hotspot's channel and makes network scans fail, so while the
  // hotspot is open only loop() retries, every RETRY_MS.
  WiFi.setAutoReconnect(false);
  if (storedSsid.length()) WiFi.disconnect();
  dns.start(53, "*", WiFi.softAPIP());  // every name resolves to the robot: captive portal
  portal = true;
  Log::printf("WiFi: setup hotspot open, join \"%s\" and open http://%s", name,
              WiFi.softAPIP().toString().c_str());
}

void closePortal() {
  if (!portal) return;
  dns.stop();
  WiFi.softAPdisconnect(true);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  portal = false;
  Log::printf("WiFi: setup hotspot closed");
}

void startMdns() {
  if (!MDNS.begin(WIFI_HOSTNAME)) {
    Log::printf("WiFi: mDNS failed to start; use the IP address");
    return;
  }
  MDNS.addService("http", "tcp", HTTP_PORT);
  MDNS.addService("catfinder", "tcp", HTTP_PORT);
  MDNS.addServiceTxt("catfinder", "tcp", "stream", String(STREAM_PORT));
  MDNS.addServiceTxt("catfinder", "tcp", "version", FW_VERSION);
  mdnsStarted = true;
}

void connect() {
  WiFi.begin(storedSsid.c_str(), storedPass.c_str());
  lastAttempt = millis();
}
}  // namespace

void Net::begin() {
  Preferences prefs;
  if (prefs.begin("wifi", true)) {
    storedSsid = prefs.getString("ssid", "");
    storedPass = prefs.getString("pass", "");
    prefs.end();
  }
  WiFi.persistent(false);  // credentials are kept in our own NVS namespace
  WiFi.setHostname(WIFI_HOSTNAME);
  WiFi.setSleep(false);  // modem sleep costs frame rate and latency; Power enables it when idle
  WiFi.onEvent(onWifiEvent);
  disconnectedSince = millis();
  if (storedSsid.isEmpty()) {
    Log::printf("WiFi: no network stored");
    openPortal();
    return;
  }
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  Log::printf("WiFi: connecting to \"%s\"", storedSsid.c_str());
  connect();
}

void Net::loop() {
  if (portal) dns.processNextRequest();
  if (storedSsid.isEmpty()) return;

  uint32_t now = millis();
  bool isConnected = WiFi.status() == WL_CONNECTED;
  if (isConnected && !wasConnected) {
    everConnected = true;
    if (!mdnsStarted) startMdns();
    closePortal();
    Log::printf("WiFi: connected to \"%s\", http://%s.local or http://%s, RSSI %d dBm", storedSsid.c_str(),
                WIFI_HOSTNAME, WiFi.localIP().toString().c_str(), WiFi.RSSI());
  } else if (!isConnected && wasConnected) {
    Log::printf("WiFi: connection lost, reconnecting");
    disconnectedSince = lastAttempt = now;
  }
  wasConnected = isConnected;
  if (isConnected) return;

  uint32_t limit = everConnected ? LOST_MS : FIRST_CONNECT_MS;
  if (!portal && now - disconnectedSince > limit) openPortal();
  if (!portal) return;  // the core's auto-reconnect handles retries until then
  // A connection attempt can move the radio off the hotspot's channel, so none while
  // someone on the hotspot is using the setup page. A phone that merely sits on the
  // hotspot (it may rejoin it by itself) does not block reconnecting.
  bool settingUp = WiFi.softAPgetStationNum() > 0 && Power::idleMs() < SETUP_ACTIVE_MS;
  if (now - lastAttempt > RETRY_MS && !settingUp) connect();
}

bool Net::portalActive() { return portal; }

bool Net::connected() { return WiFi.status() == WL_CONNECTED; }

String Net::ssid() { return storedSsid; }

uint32_t Net::reconnects() { return disconnects; }

void Net::save(const char* ssid, const char* password) {
  Preferences prefs;
  if (prefs.begin("wifi", false)) {
    prefs.putString("ssid", ssid);
    prefs.putString("pass", password);
    prefs.end();
  }
  Log::printf("WiFi: saved network \"%s\"", ssid);
}

void Net::forget() {
  Preferences prefs;
  if (prefs.begin("wifi", false)) {
    prefs.clear();
    prefs.end();
  }
  Log::printf("WiFi: stored network erased");
}

String Net::scanJson() {
  int n = WiFi.scanNetworks();
  if (n < 0) return "";
  String out = "[";
  bool first = true;
  for (int i = 0; i < n; i++) {
    String name = WiFi.SSID(i);
    if (name.isEmpty()) continue;
    bool duplicate = false;  // results are sorted by signal, so the first one is the strongest
    for (int j = 0; j < i && !duplicate; j++) duplicate = WiFi.SSID(j) == name;
    if (duplicate) continue;
    if (!first) out += ',';
    first = false;
    out += "{\"ssid\":" + jsonQuoted(name.c_str()) + ",\"rssi\":" + String(WiFi.RSSI(i)) +
           ",\"secure\":" + (WiFi.encryptionType(i) != WIFI_AUTH_OPEN ? "true" : "false") + "}";
  }
  WiFi.scanDelete();
  return out + "]";
}

String Net::jsonQuoted(const char* s) {
  String out = "\"";
  for (; *s; s++) {
    char c = *s;
    if (c == '"' || c == '\\') {
      out += '\\';
      out += c;
    } else if ((uint8_t)c < 0x20) {
      char esc[8];
      snprintf(esc, sizeof(esc), "\\u%04x", (uint8_t)c);
      out += esc;
    } else {
      out += c;
    }
  }
  return out + "\"";
}
