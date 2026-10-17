#pragma once
#include <Arduino.h>

namespace Web {
bool begin();

// Set while an OTA update runs; open streams close so the flash write gets the bandwidth.
void setOtaActive(bool active);
bool otaActive();
int streamClients();
}  // namespace Web
