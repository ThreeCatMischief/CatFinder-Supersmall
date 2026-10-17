#pragma once
#include <Arduino.h>

// Differential drive on the two continuous-rotation servos A and B.
// Speeds are -100..100 per side, positive is forward.
namespace Drive {
constexpr int BALANCE_POINTS = 4;  // at 25, 50, 75, 100 % speed

struct Settings {
  bool swap;            // false: servo A drives the left wheel
  bool invert[2];       // per servo A, B
  uint16_t stopUs[2];   // per servo A, B
  uint16_t spanUs;
  uint16_t timeoutMs;
  // Speed match between the wheels, per calibration point. > 0 shortens the left
  // wheel's pulse offset by this many µs, < 0 the right wheel's. Linear between
  // points, 0 at standstill, same in reverse.
  int16_t balanceUs[BALANCE_POINTS];
};

// Loads settings from NVS (defaults from Config.h) and starts the PWM outputs.
void begin();

// Safe to call from any task. The command expires after timeoutMs, so the sender
// must repeat it while the robot should keep moving.
void command(int left, int right);

// Raw pulse on one servo (0 = A, 1 = B) with the other off, ignoring side and
// invert. For identifying and trimming the servos. Expires like command().
void test(int servo, int us);

// Applies the current command to the servos. Call from loop().
void update();

// While disabled (OTA in progress) the servos stay off and commands are ignored.
void setEnabled(bool enabled);

Settings settings();
Settings defaults();
// Clamps the values into valid ranges, applies them and stores them in NVS.
void setSettings(Settings s);

int left();
int right();
}  // namespace Drive
