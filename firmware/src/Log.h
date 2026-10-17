#pragma once
#include <Arduino.h>

// printf-style logging to Serial and to a RAM ring buffer served at /log,
// so messages remain readable once the board is only reachable over WiFi.
namespace Log {
void printf(const char* fmt, ...) __attribute__((format(printf, 1, 2)));

// Copies the buffer (oldest first) into out. Returns bytes written.
size_t snapshot(char* out, size_t outSize);
}  // namespace Log
