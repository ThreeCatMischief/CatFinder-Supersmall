#include "Web.h"
#include <WiFi.h>
#include <atomic>
#include <esp_app_desc.h>
#include <esp_camera.h>
#include <esp_http_server.h>
#include <esp_ota_ops.h>
#include <esp_timer.h>
#include <Update.h>
#include "Camera.h"
#include "Config.h"
#include "Drive.h"
#include "Log.h"
#include "Net.h"
#include "Power.h"
#include "WebPage.h"

namespace {
httpd_handle_t pageServer = nullptr;
httpd_handle_t streamServer = nullptr;

std::atomic<bool> otaActive{false};
std::atomic<int> streamClients{0};
std::atomic<uint32_t> framesSent{0};
std::atomic<uint32_t> lastFrameBytes{0};
std::atomic<float> streamFps{0};
// Last /drive with a seq parameter, for measuring command delay from the app.
std::atomic<uint32_t> driveSeq{0};
std::atomic<uint32_t> driveSeqMs{0};

#define PART_BOUNDARY "catfinderframe"
const char* STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
const char* STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
// X-Timestamp: capture time in ms since boot, lets the app measure video delay.
const char* STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\nX-Timestamp: %lu\r\n\r\n";

const char* resetReasonName(esp_reset_reason_t r) {
  switch (r) {
    case ESP_RST_POWERON:  return "power-on";
    case ESP_RST_EXT:      return "external pin";
    case ESP_RST_SW:       return "software";
    case ESP_RST_PANIC:    return "panic";
    case ESP_RST_INT_WDT:  return "interrupt watchdog";
    case ESP_RST_TASK_WDT: return "task watchdog";
    case ESP_RST_WDT:      return "other watchdog";
    case ESP_RST_DEEPSLEEP:return "deep sleep";
    case ESP_RST_BROWNOUT: return "brownout";
    case ESP_RST_SDIO:     return "SDIO";
    default:               return "unknown";
  }
}

// "pending" means the image still has to prove itself; see the rollback guard in main.cpp.
const char* otaStateName() {
  esp_ota_img_states_t st;
  if (esp_ota_get_state_partition(esp_ota_get_running_partition(), &st) != ESP_OK) return "unknown";
  switch (st) {
    case ESP_OTA_IMG_VALID:          return "valid";
    case ESP_OTA_IMG_UNDEFINED:      return "valid";  // serial-flashed image, no OTA state
    case ESP_OTA_IMG_PENDING_VERIFY: return "pending";
    default:                         return "other";
  }
}

// Restarts after the response has gone out.
void restartSoon() {
  const esp_timer_create_args_t args = {.callback = [](void*) { esp_restart(); }, .arg = nullptr,
                                        .dispatch_method = ESP_TIMER_TASK, .name = "restart",
                                        .skip_unhandled_events = true};
  esp_timer_handle_t timer;
  if (esp_timer_create(&args, &timer) == ESP_OK) esp_timer_start_once(timer, 1000 * 1000ULL);
}

// Reads a form-encoded request body into buf. Returns false (with an error sent) if it
// does not fit.
bool readBody(httpd_req_t* req, char* buf, size_t size) {
  if (req->content_len >= size) {
    httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "body too long");
    return false;
  }
  int got = 0;
  while (got < (int)req->content_len) {
    int n = httpd_req_recv(req, buf + got, req->content_len - got);
    if (n <= 0) return false;
    got += n;
  }
  buf[got] = 0;
  return true;
}

// Decodes a form value in place: %XX and '+' for space.
void urlDecode(char* s) {
  char* out = s;
  for (; *s; s++) {
    if (*s == '+') {
      *out++ = ' ';
    } else if (*s == '%' && isxdigit((uint8_t)s[1]) && isxdigit((uint8_t)s[2])) {
      char hex[3] = {s[1], s[2], 0};
      *out++ = (char)strtol(hex, nullptr, 16);
      s += 2;
    } else {
      *out++ = *s;
    }
  }
  *out = 0;
}

// Plain-text answer with any status line, e.g. "409 Conflict" (httpd_resp_send_err
// knows only a fixed set of codes).
esp_err_t sendText(httpd_req_t* req, const char* status, const char* text) {
  httpd_resp_set_status(req, status);
  httpd_resp_set_type(req, "text/plain");
  return httpd_resp_sendstr(req, text);
}

// The setup page while the setup hotspot is open, the control page otherwise.
esp_err_t indexHandler(httpd_req_t* req) {
  httpd_resp_set_type(req, "text/html");
  if (Net::portalActive()) return httpd_resp_send(req, SETUP_HTML, sizeof(SETUP_HTML) - 1);
  return httpd_resp_send(req, INDEX_HTML, sizeof(INDEX_HTML) - 1);
}

// Phones probe a fixed URL after joining a network. While the setup hotspot is open,
// every unknown path redirects to the setup page, so the phone offers to open it.
esp_err_t notFoundHandler(httpd_req_t* req, httpd_err_code_t) {
  if (!Net::portalActive()) return httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "Not found");
  static char location[32];
  snprintf(location, sizeof(location), "http://%s/", WiFi.softAPIP().toString().c_str());
  httpd_resp_set_status(req, "302 Found");
  httpd_resp_set_hdr(req, "Location", location);
  return httpd_resp_send(req, nullptr, 0);
}

esp_err_t wifiScanHandler(httpd_req_t* req) {
  String json = Net::scanJson();
  if (json.isEmpty()) return sendText(req, "503 Service Unavailable", "search failed, try again");
  httpd_resp_set_type(req, "application/json");
  return httpd_resp_send(req, json.c_str(), json.length());
}

// POST /wifi, form body ssid=...&pass=... Stores the network and restarts.
esp_err_t wifiSaveHandler(httpd_req_t* req) {
  char body[512], ssid[100], pass[200];
  if (!readBody(req, body, sizeof(body))) return ESP_FAIL;
  // A missing password means an open network; a truncated value is an error.
  esp_err_t ssidOk = httpd_query_key_value(body, "ssid", ssid, sizeof(ssid));
  esp_err_t passOk = httpd_query_key_value(body, "pass", pass, sizeof(pass));
  if (passOk == ESP_ERR_NOT_FOUND) pass[0] = 0;
  if (ssidOk == ESP_OK) urlDecode(ssid);
  if (passOk == ESP_OK) urlDecode(pass);
  if (ssidOk != ESP_OK || (passOk != ESP_OK && passOk != ESP_ERR_NOT_FOUND) || !ssid[0] || strlen(ssid) > 32 ||
      strlen(pass) > 64) {
    return sendText(req, "400 Bad Request", "network name must be 1-32 and password 0-64 characters");
  }
  Net::save(ssid, pass);
  httpd_resp_sendstr(req, "ok");
  restartSoon();
  return ESP_OK;
}

esp_err_t wifiForgetHandler(httpd_req_t* req) {
  Net::forget();
  httpd_resp_sendstr(req, "ok");
  restartSoon();
  return ESP_OK;
}

void endUpdate() {
  otaActive = false;
  Drive::setEnabled(true);
}

uint8_t uploadBuf[4096];  // not on the server task's stack

// Reads and drops the rest of the upload, then answers. Answering before the body has
// been read makes browsers report a reset connection instead of the message.
esp_err_t refuseUpload(httpd_req_t* req, size_t got, const char* status, const char* text) {
  Log::printf("Update: refused, %s", text);
  int timeouts = 0;
  while (got < req->content_len) {
    int n = httpd_req_recv(req, (char*)uploadBuf, min(sizeof(uploadBuf), req->content_len - got));
    if (n == HTTPD_SOCK_ERR_TIMEOUT && ++timeouts < 3) continue;
    if (n <= 0) return ESP_FAIL;
    timeouts = 0;
    got += n;
  }
  return sendText(req, status, text);
}

bool runningImagePending() {
  esp_ota_img_states_t st;
  return esp_ota_get_state_partition(esp_ota_get_running_partition(), &st) == ESP_OK &&
         st == ESP_OTA_IMG_PENDING_VERIFY;
}

// POST /update, body = the firmware image (firmware.bin). Restarts into it on success;
// the rollback guard in main.cpp decides whether it is kept.
esp_err_t updateHandler(httpd_req_t* req) {
  size_t total = req->content_len;
  if (total == 0) return sendText(req, "400 Bad Request", "expected the firmware file as the request body");
  // Also set from a successful upload until the restart.
  if (otaActive) return refuseUpload(req, 0, "409 Conflict", "an update is already running");
  // The new firmware has to join the home WiFi to be kept. This also keeps anyone near
  // the open setup hotspot from installing firmware.
  if (Net::portalActive() || !Net::connected()) {
    return refuseUpload(req, 0, "409 Conflict", "connect the robot to your WiFi first");
  }
  // The other app slot holds the previous firmware, the fallback until this one is confirmed.
  if (runningImagePending()) {
    return refuseUpload(req, 0, "409 Conflict", "the last update is still being checked, try again in a minute");
  }
  // Auto mode treats any file not starting with an app header as encrypted and reports
  // "Decryption error" instead of a wrong file.
  Update.setCryptMode(U_AES_DECRYPT_NONE);
  if (!Update.begin(total)) return refuseUpload(req, 0, "400 Bad Request", Update.errorString());
  otaActive = true;  // closes the stream
  Drive::setEnabled(false);
  Log::printf("Update: receiving %u bytes", (unsigned)total);

  size_t got = 0;
  int timeouts = 0;
  unsigned lastPct = 0;
  while (got < total) {
    int n = httpd_req_recv(req, (char*)uploadBuf, min(sizeof(uploadBuf), total - got));
    if (n == HTTPD_SOCK_ERR_TIMEOUT && ++timeouts < 3) continue;
    if (n <= 0) {
      Update.abort();
      endUpdate();
      Log::printf("Update: upload interrupted after %u bytes", (unsigned)got);
      return ESP_FAIL;  // the connection is gone; closing it is the only answer
    }
    timeouts = 0;
    got += n;
    if (Update.write(uploadBuf, n) != (size_t)n) {
      // The -factory image starts with padding, not an app header.
      bool badImage = Update.getError() == UPDATE_ERROR_MAGIC_BYTE;
      const char* reason = badImage ? "wrong file: use catfinder-X.Y.Z.bin, not the -factory file"
                                    : Update.errorString();
      Update.abort();
      endUpdate();
      return refuseUpload(req, got, badImage ? "400 Bad Request" : "500 Internal Server Error", reason);
    }
    unsigned pct = got * 100 / total;
    if (pct / 25 != lastPct / 25) Log::printf("Update: %u%%", pct);
    lastPct = pct;
    Power::touch();
  }
  if (!Update.end()) {
    const char* reason = Update.errorString();
    endUpdate();
    Log::printf("Update: failed, %s", reason);
    return sendText(req, "400 Bad Request", reason);
  }
  Log::printf("Update: done, restarting");
  httpd_resp_sendstr(req, "ok");
  restartSoon();
  return ESP_OK;
}

esp_err_t statusHandler(httpd_req_t* req) {
  static const esp_reset_reason_t resetReason = esp_reset_reason();
  size_t pageSockets = CONFIG_LWIP_MAX_SOCKETS;
  int fds[CONFIG_LWIP_MAX_SOCKETS];
  if (httpd_get_client_list(pageServer, &pageSockets, fds) != ESP_OK) pageSockets = 0;
  char elfSha[17];
  esp_app_get_elf_sha256(elfSha, sizeof(elfSha));
  String ssid = Net::jsonQuoted(Net::ssid().c_str());
  char json[1280];
  snprintf(json, sizeof(json),
           "{\"version\":\"%s\",\"build\":\"%s %s\",\"elf_sha\":\"%s\",\"ota_state\":\"%s\",\"partition\":\"%s\",\"uptime_s\":%lu,\"reset_reason\":\"%s\","
           "\"ip\":\"%s\",\"ssid\":%s,\"setup_mode\":%s,\"rssi\":%d,\"channel\":%d,\"wifi_reconnects\":%lu,"
           "\"heap_free\":%lu,\"heap_min\":%lu,\"psram_free\":%lu,\"psram_size\":%lu,"
           "\"sketch_size\":%lu,\"sketch_free\":%lu,\"cpu_mhz\":%lu,\"chip_rev\":%d,"
           "\"cam_ok\":%s,\"cam_sensor\":\"%s\",\"cam_framesize\":\"%s\",\"video_level\":%d,"
           "\"cam_on\":%s,\"modem_sleep\":%s,\"idle_s\":%lu,"
           "\"stream_clients\":%d,\"stream_fps\":%.2f,\"frame_bytes\":%lu,\"frames_sent\":%lu,"
           "\"drive_l\":%d,\"drive_r\":%d,\"drive_seq\":%lu,\"drive_seq_age_ms\":%lu,\"page_sockets\":%u,\"now_ms\":%lu}",
           FW_VERSION, __DATE__, __TIME__, elfSha, otaStateName(), esp_ota_get_running_partition()->label,
           (unsigned long)(millis() / 1000), resetReasonName(resetReason),
           WiFi.localIP().toString().c_str(), ssid.c_str(), Net::portalActive() ? "true" : "false",
           WiFi.RSSI(), WiFi.channel(), (unsigned long)Net::reconnects(),
           (unsigned long)ESP.getFreeHeap(), (unsigned long)ESP.getMinFreeHeap(),
           (unsigned long)ESP.getFreePsram(), (unsigned long)ESP.getPsramSize(),
           (unsigned long)ESP.getSketchSize(), (unsigned long)ESP.getFreeSketchSpace(),
           (unsigned long)ESP.getCpuFreqMHz(), ESP.getChipRevision(),
           Camera::ok() ? "true" : "false", Camera::sensorName(), Camera::frameSizeName(), Camera::level(),
           Camera::isOn() ? "true" : "false", Power::modemSleep() ? "true" : "false",
           (unsigned long)(Power::idleMs() / 1000),
           streamClients.load(), streamFps.load(), (unsigned long)lastFrameBytes, (unsigned long)framesSent,
           Drive::left(), Drive::right(), (unsigned long)driveSeq, (unsigned long)(millis() - driveSeqMs),
           (unsigned)pageSockets, (unsigned long)millis());
  httpd_resp_set_type(req, "application/json");
  return httpd_resp_sendstr(req, json);
}

esp_err_t logHandler(httpd_req_t* req) {
  static char out[4096];  // httpd runs handlers on one task, so a static buffer is safe
  size_t len = Log::snapshot(out, sizeof(out));
  httpd_resp_set_type(req, "text/plain");
  return httpd_resp_send(req, out, len);
}

// GET /drive?l=<-100..100>&r=<-100..100>[&seq=<n>]. The page repeats it while a
// control is held. Answers "ok <ms since boot>".
esp_err_t driveHandler(httpd_req_t* req) {
  char query[48], l[8], r[8], seq[12];
  if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
      httpd_query_key_value(query, "l", l, sizeof(l)) != ESP_OK ||
      httpd_query_key_value(query, "r", r, sizeof(r)) != ESP_OK) {
    httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "expected l and r");
    return ESP_FAIL;
  }
  Drive::command(atoi(l), atoi(r));
  uint32_t now = millis();
  if (httpd_query_key_value(query, "seq", seq, sizeof(seq)) == ESP_OK) {
    driveSeq = strtoul(seq, nullptr, 10);
    driveSeqMs = now;
  }
  char reply[16];
  snprintf(reply, sizeof(reply), "ok %lu", (unsigned long)now);
  return httpd_resp_sendstr(req, reply);
}

// GET /video?level=<0..Camera::LEVELS-1>: stream quality, 0 = best.
esp_err_t videoHandler(httpd_req_t* req) {
  char query[32], lv[4];
  if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
      httpd_query_key_value(query, "level", lv, sizeof(lv)) != ESP_OK) {
    httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "expected level");
    return ESP_FAIL;
  }
  Camera::setLevel(atoi(lv));
  char json[96];
  snprintf(json, sizeof(json), "{\"level\":%d,\"levels\":%d,\"framesize\":\"%s\"}", Camera::level(),
           Camera::LEVELS, Camera::frameSizeName());
  httpd_resp_set_type(req, "application/json");
  return httpd_resp_sendstr(req, json);
}

// GET /servo_test?s=<0|1>&us=<pulse>: raw pulse on servo A or B, repeated while held.
esp_err_t servoTestHandler(httpd_req_t* req) {
  char query[32], sv[4], us[8];
  if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
      httpd_query_key_value(query, "s", sv, sizeof(sv)) != ESP_OK ||
      httpd_query_key_value(query, "us", us, sizeof(us)) != ESP_OK) {
    httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "expected s and us");
    return ESP_FAIL;
  }
  Drive::test(atoi(sv), atoi(us));
  return httpd_resp_sendstr(req, "ok");
}

esp_err_t sendDriveConfig(httpd_req_t* req) {
  Drive::Settings s = Drive::settings();
  char json[320];
  snprintf(json, sizeof(json),
           "{\"pin_a\":%d,\"pin_b\":%d,\"swap\":%d,\"inv_a\":%d,\"inv_b\":%d,"
           "\"stop_a\":%u,\"stop_b\":%u,\"span\":%u,\"timeout\":%u,\"bal\":[%d,%d,%d,%d]}",
           PIN_SERVO_A, PIN_SERVO_B, s.swap, s.invert[0], s.invert[1],
           s.stopUs[0], s.stopUs[1], s.spanUs, s.timeoutMs,
           s.balanceUs[0], s.balanceUs[1], s.balanceUs[2], s.balanceUs[3]);
  httpd_resp_set_type(req, "application/json");
  return httpd_resp_sendstr(req, json);
}

esp_err_t driveConfigGetHandler(httpd_req_t* req) { return sendDriveConfig(req); }

// POST /drive_cfg, form-encoded body with any subset of the keys in the GET
// response (balance points as bal0..bal3); reset=1 restores the defaults from
// Config.h. Replies with the result.
esp_err_t driveConfigPostHandler(httpd_req_t* req) {
  char body[256];
  if (!readBody(req, body, sizeof(body))) return ESP_FAIL;

  Drive::Settings s = Drive::settings();
  char v[8];
  auto num = [&](const char* key, auto& field) {
    if (httpd_query_key_value(body, key, v, sizeof(v)) == ESP_OK) field = constrain(atoi(v), 0, 65535);
  };
  auto snum = [&](const char* key, int16_t& field) {
    if (httpd_query_key_value(body, key, v, sizeof(v)) == ESP_OK) field = constrain(atoi(v), -1000, 1000);
  };
  auto flag = [&](const char* key, bool& field) {
    if (httpd_query_key_value(body, key, v, sizeof(v)) == ESP_OK) field = atoi(v) != 0;
  };
  bool reset = false;
  flag("reset", reset);
  if (reset) s = Drive::defaults();
  flag("swap", s.swap);
  flag("inv_a", s.invert[0]);
  flag("inv_b", s.invert[1]);
  num("stop_a", s.stopUs[0]);
  num("stop_b", s.stopUs[1]);
  num("span", s.spanUs);
  num("timeout", s.timeoutMs);
  for (int i = 0; i < Drive::BALANCE_POINTS; i++) {
    char key[8];
    snprintf(key, sizeof(key), "bal%d", i);
    snum(key, s.balanceUs[i]);
  }
  Drive::setSettings(s);
  return sendDriveConfig(req);
}

esp_err_t streamHandler(httpd_req_t* req) {
  Power::touch();
  if (!Camera::acquire()) {
    httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "camera not initialised");
    return ESP_FAIL;
  }
  esp_err_t res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
  if (res != ESP_OK) {
    Camera::release();
    return res;
  }
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

  streamClients++;
  Log::printf("Stream: client connected (%d)", streamClients.load());

  char partHeader[96];
  uint32_t windowStart = millis();
  uint32_t windowFrames = 0;
  uint32_t frameStart = millis();

  while (!otaActive) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Log::printf("Stream: frame capture failed");
      res = ESP_FAIL;
      break;
    }
    unsigned long capturedMs = fb->timestamp.tv_sec * 1000UL + fb->timestamp.tv_usec / 1000;
    size_t hlen = snprintf(partHeader, sizeof(partHeader), STREAM_PART, (unsigned)fb->len, capturedMs);
    res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
    if (res == ESP_OK) res = httpd_resp_send_chunk(req, partHeader, hlen);
    if (res == ESP_OK) res = httpd_resp_send_chunk(req, (const char*)fb->buf, fb->len);
    lastFrameBytes = fb->len;
    esp_camera_fb_return(fb);
    if (res != ESP_OK) break;

    framesSent++;
    windowFrames++;
    uint32_t now = millis();
    uint32_t interval = Camera::frameIntervalMs();
    if (interval && now - frameStart < interval) {
      delay(interval - (now - frameStart));
      now = millis();
    }
    frameStart = now;
    if (now - windowStart >= 1000) {
      streamFps = windowFrames * 1000.0f / (now - windowStart);
      windowStart = now;
      windowFrames = 0;
    }
  }

  if (--streamClients == 0) streamFps = 0;
  Camera::release();
  Power::touch();
  Log::printf("Stream: client disconnected (%d)", streamClients.load());
  return res;
}

// Every page server request counts as use and keeps the board awake.
esp_err_t touched(httpd_req_t* req) {
  Power::touch();
  return reinterpret_cast<esp_err_t (*)(httpd_req_t*)>(req->user_ctx)(req);
}

void addPage(const char* uri, httpd_method_t method, esp_err_t (*handler)(httpd_req_t*)) {
  const httpd_uri_t u = {.uri = uri, .method = method, .handler = touched, .user_ctx = (void*)handler};
  httpd_register_uri_handler(pageServer, &u);
}
}  // namespace

bool Web::begin() {
  httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
  cfg.server_port = HTTP_PORT;
  cfg.ctrl_port = 32768;
  cfg.max_uri_handlers = 16;
  cfg.stack_size = 8192;
  if (httpd_start(&pageServer, &cfg) != ESP_OK) {
    Log::printf("Web: page server failed to start");
    return false;
  }
  addPage("/", HTTP_GET, indexHandler);
  addPage("/status", HTTP_GET, statusHandler);
  addPage("/log", HTTP_GET, logHandler);
  addPage("/drive", HTTP_GET, driveHandler);
  addPage("/servo_test", HTTP_GET, servoTestHandler);
  addPage("/drive_cfg", HTTP_GET, driveConfigGetHandler);
  addPage("/drive_cfg", HTTP_POST, driveConfigPostHandler);
  addPage("/video", HTTP_GET, videoHandler);
  addPage("/wifi/scan", HTTP_GET, wifiScanHandler);
  addPage("/wifi", HTTP_POST, wifiSaveHandler);
  addPage("/wifi/forget", HTTP_POST, wifiForgetHandler);
  addPage("/update", HTTP_POST, updateHandler);
  httpd_register_err_handler(pageServer, HTTPD_404_NOT_FOUND, notFoundHandler);

  // The stream handler never returns while a client watches, so it gets its own
  // server task. One client at a time keeps frame rate predictable.
  httpd_config_t scfg = HTTPD_DEFAULT_CONFIG();
  scfg.server_port = STREAM_PORT;
  scfg.ctrl_port = 32769;
  scfg.max_open_sockets = 2;
  scfg.lru_purge_enable = true;  // a reloaded tab drops its stale stream socket
  if (httpd_start(&streamServer, &scfg) != ESP_OK) {
    Log::printf("Web: stream server failed to start");
    return false;
  }
  const httpd_uri_t stream = {.uri = "/stream", .method = HTTP_GET, .handler = streamHandler, .user_ctx = nullptr};
  httpd_register_uri_handler(streamServer, &stream);

  Log::printf("Web: http://%s.local (port %d), stream on port %d", WIFI_HOSTNAME, HTTP_PORT, STREAM_PORT);
  return true;
}

void Web::setOtaActive(bool active) { ::otaActive = active; }

bool Web::otaActive() { return ::otaActive; }

int Web::streamClients() { return ::streamClients; }
