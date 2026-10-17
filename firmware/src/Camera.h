#pragma once
#include <Arduino.h>

// The camera is powered only while a stream needs it: acquire() powers it up,
// powerDownIfIdle() switches it off (driver deinit, sensor PWDN high) once no
// user has held it for a while.
namespace Camera {
// First power-up at boot; detects the sensor.
bool begin();
// The last power-up succeeded.
bool ok();
bool isOn();
// Powers the camera up if needed and counts one user. Every successful
// acquire() needs a release().
bool acquire();
void release();
// Powers down when no user holds the camera and the last release is idleMs ago.
void powerDownIfIdle(uint32_t idleMs);

const char* sensorName();
const char* frameSizeName();

// Stream quality ladder, 0 = best. Lower levels cost less bandwidth so drive
// commands do not queue behind video on a slow link. The app picks the level.
// Kept while the camera is off and applied at the next power-up.
constexpr int LEVELS = 5;
void setLevel(int level);
int level();
// Minimum time between stream frames at the current level; 0 = camera rate.
uint32_t frameIntervalMs();
}  // namespace Camera
