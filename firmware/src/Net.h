#pragma once
#include <Arduino.h>

// WiFi with setup at runtime. The home network's name and password are stored on the
// board (NVS) and entered through the setup hotspot "CatFinder-XXXX": it opens when no
// network is stored, when the stored one cannot be joined within 30 s of boot, and when
// the connection has been lost for 3 minutes. Reconnect attempts continue while it is
// open; it closes once the home network is joined.
namespace Net {
void begin();
// Call from loop().
void loop();

bool portalActive();
bool connected();
String ssid();
// Disconnects counted since the first connection.
uint32_t reconnects();

// Stores or erases the credentials; the caller restarts the board.
void save(const char* ssid, const char* password);
void forget();

// Networks in range, strongest first: [{"ssid":"...","rssi":-52,"secure":true}].
// Blocks for the scan, 2-4 s. Empty string if the scan failed.
String scanJson();

// s as a JSON string literal, quotes included.
String jsonQuoted(const char* s);
}  // namespace Net
