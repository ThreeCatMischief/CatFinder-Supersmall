#pragma once
#include <Arduino.h>

// Battery saving by idle time. The camera powers down after CAM_OFF_AFTER_MS without
// a stream. After IDLE_AFTER_MS without any request or stream, the WiFi modem sleeps
// between beacons and the CPU runs at CPU_IDLE_MHZ. The robot stays reachable; the
// first request after an idle phase takes a little longer.
namespace Power {
void begin();

// Any client request; resets the idle timer. Safe from any task.
void touch();

// Applies camera, modem and CPU power by idle time. Call from loop().
void update();

uint32_t idleMs();
bool modemSleep();
}  // namespace Power
