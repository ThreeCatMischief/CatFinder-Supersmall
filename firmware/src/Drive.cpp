#include "Drive.h"
#include <Preferences.h>
#include <atomic>
#include <driver/ledc.h>
#include "Config.h"
#include "Log.h"

// The IDF LEDC driver is used directly: the Arduino ledcAttach* allocator does not
// know the camera owns high-speed timer 0 / channel 0 and could reassign it.
namespace {
constexpr ledc_mode_t MODE = LEDC_LOW_SPEED_MODE;
constexpr ledc_timer_t TIMER = LEDC_TIMER_2;
constexpr ledc_channel_t CHANNEL[2] = {LEDC_CHANNEL_2, LEDC_CHANNEL_3};
constexpr int PIN[2] = {PIN_SERVO_A, PIN_SERVO_B};
constexpr uint32_t FREQ_HZ = 50;
constexpr uint32_t PERIOD_US = 1000000 / FREQ_HZ;
constexpr ledc_timer_bit_t RES = LEDC_TIMER_16_BIT;
constexpr int DEADBAND = 3;  // |speed| below this switches the pulse off
constexpr uint32_t SETTINGS_VERSION = 2;
constexpr int BALANCE_MAX_US = 200;

bool ready = false;
std::atomic<int> cmdLeft{0}, cmdRight{0};
std::atomic<uint32_t> cmdAt{0};
std::atomic<int> testServo{-1}, testUs{0};
std::atomic<uint32_t> testAt{0};
std::atomic<bool> enabled{true};
int outLeft = 0, outRight = 0;
uint32_t outDuty[2] = {UINT32_MAX, UINT32_MAX};

portMUX_TYPE settingsLock = portMUX_INITIALIZER_UNLOCKED;
Drive::Settings cfg;

struct Stored {
  uint32_t version;
  Drive::Settings s;
};

// Version 1 layout, before balanceUs. Loaded once and migrated.
struct StoredV1 {
  uint32_t version;
  struct {
    bool swap;
    bool invert[2];
    uint16_t stopUs[2];
    uint16_t spanUs;
    uint16_t timeoutMs;
  } s;
};

uint32_t dutyForUs(uint32_t us) { return us * ((1u << RES) - 1) / PERIOD_US; }

// Balance in µs at |speed| 0..100, interpolated between the calibration points.
int balanceAt(const Drive::Settings& s, int speed) {
  constexpr int STEP = 100 / Drive::BALANCE_POINTS;
  speed = abs(speed);
  int i = speed / STEP;  // segment from point i-1 (or 0 µs at i = 0) to point i
  if (i >= Drive::BALANCE_POINTS) return s.balanceUs[Drive::BALANCE_POINTS - 1];
  int from = i ? s.balanceUs[i - 1] : 0;
  return from + (s.balanceUs[i] - from) * (speed - i * STEP) / STEP;
}

// Pulse offset from stop for one wheel; slowUs shortens it toward the stop pulse.
int wheelOffsetUs(int speed, int spanUs, int slowUs) {
  int mag = max(0, abs(speed) * spanUs / 100 - slowUs);
  return speed < 0 ? -mag : mag;
}

// Speed 0 sends no pulse at all: the servo idles instead of creeping on a
// slightly mistrimmed stop pulse.
uint32_t dutyForWheel(int speed, int offsetUs, bool invert, uint32_t stopUs) {
  if (abs(speed) < DEADBAND) return 0;
  return dutyForUs(stopUs + (invert ? -offsetUs : offsetUs));
}

void write(int servo, uint32_t duty) {
  if (duty == outDuty[servo]) return;
  outDuty[servo] = duty;
  ledc_set_duty(MODE, CHANNEL[servo], duty);
  ledc_update_duty(MODE, CHANNEL[servo]);
}

Drive::Settings clampSettings(Drive::Settings s) {
  for (int i = 0; i < 2; i++) s.stopUs[i] = constrain(s.stopUs[i], 1000, 2000);
  s.spanUs = constrain(s.spanUs, 50, 1000);
  s.timeoutMs = constrain(s.timeoutMs, 100, 5000);
  for (auto& b : s.balanceUs) b = constrain(b, -BALANCE_MAX_US, BALANCE_MAX_US);
  return s;
}
}  // namespace

Drive::Settings Drive::defaults() {
  return {.swap = DRIVE_DEFAULT_SWAP,
          .invert = {DRIVE_DEFAULT_INVERT_A, DRIVE_DEFAULT_INVERT_B},
          .stopUs = {DRIVE_DEFAULT_STOP_US, DRIVE_DEFAULT_STOP_US},
          .spanUs = DRIVE_DEFAULT_SPAN_US,
          .timeoutMs = DRIVE_DEFAULT_TIMEOUT_MS,
          .balanceUs = {0, 0, 0, 0}};
}

void Drive::begin() {
  cfg = defaults();
  Preferences prefs;
  Stored st;
  StoredV1 v1;
  bool migrate = false;
  if (prefs.begin("drive", true)) {
    size_t len = prefs.getBytesLength("cfg");
    if (len == sizeof(st) && prefs.getBytes("cfg", &st, sizeof(st)) == sizeof(st) &&
        st.version == SETTINGS_VERSION) {
      cfg = clampSettings(st.s);
      Log::printf("Drive: settings loaded from NVS");
    } else if (len == sizeof(v1) && prefs.getBytes("cfg", &v1, sizeof(v1)) == sizeof(v1) && v1.version == 1) {
      cfg.swap = v1.s.swap;
      for (int i = 0; i < 2; i++) {
        cfg.invert[i] = v1.s.invert[i];
        cfg.stopUs[i] = v1.s.stopUs[i];
      }
      cfg.spanUs = v1.s.spanUs;
      cfg.timeoutMs = v1.s.timeoutMs;
      cfg = clampSettings(cfg);
      migrate = true;
    }
    prefs.end();
  }
  if (migrate) {
    Log::printf("Drive: settings migrated from version 1");
    setSettings(cfg);
  }

  const ledc_timer_config_t timer = {.speed_mode = MODE, .duty_resolution = RES, .timer_num = TIMER,
                                     .freq_hz = FREQ_HZ, .clk_cfg = LEDC_AUTO_CLK};
  if (ledc_timer_config(&timer) != ESP_OK) {
    Log::printf("Drive: LEDC timer config failed");
    return;
  }
  for (int i = 0; i < 2; i++) {
    const ledc_channel_config_t c = {.gpio_num = PIN[i], .speed_mode = MODE, .channel = CHANNEL[i],
                                     .intr_type = LEDC_INTR_DISABLE, .timer_sel = TIMER, .duty = 0, .hpoint = 0};
    if (ledc_channel_config(&c) != ESP_OK) {
      Log::printf("Drive: LEDC channel config failed on GPIO %d", PIN[i]);
      return;
    }
  }
  ready = true;
  Log::printf("Drive: servo A on GPIO %d, B on GPIO %d, left is %c", PIN[0], PIN[1], cfg.swap ? 'B' : 'A');
}

void Drive::command(int left, int right) {
  cmdLeft = constrain(left, -100, 100);
  cmdRight = constrain(right, -100, 100);
  cmdAt = millis();
  testServo = -1;
}

void Drive::test(int servo, int us) {
  if (servo < 0 || servo > 1) return;
  testUs = constrain(us, 500, 2500);
  testServo = servo;
  testAt = millis();
  cmdLeft = cmdRight = 0;
}

void Drive::update() {
  if (!ready) return;
  Settings s = settings();
  uint32_t now = millis();
  int l = cmdLeft, r = cmdRight;
  if (!enabled || now - cmdAt > s.timeoutMs) l = r = 0;
  int ts = testServo;
  if (!enabled || now - testAt > s.timeoutMs) ts = -1;

  if ((l == 0 && r == 0) && (outLeft || outRight)) Log::printf("Drive: stop");
  outLeft = l;
  outRight = r;

  if (ts >= 0) {
    write(ts, dutyForUs(testUs));
    write(1 - ts, 0);
    return;
  }
  // Balance is looked up at the faster wheel's speed and slows only its own side.
  int bal = balanceAt(s, max(abs(l), abs(r)));
  int speed[2], offset[2];
  int a = s.swap ? 1 : 0, b = 1 - a;  // servo index of the left and right wheel
  speed[a] = l;
  speed[b] = r;
  offset[a] = wheelOffsetUs(l, s.spanUs, max(bal, 0));
  offset[b] = wheelOffsetUs(r, s.spanUs, max(-bal, 0));
  for (int i = 0; i < 2; i++) write(i, dutyForWheel(speed[i], offset[i], s.invert[i], s.stopUs[i]));
}

void Drive::setEnabled(bool on) {
  enabled = on;
  if (!on) {
    cmdLeft = cmdRight = 0;
    testServo = -1;
  }
}

Drive::Settings Drive::settings() {
  portENTER_CRITICAL(&settingsLock);
  Settings s = cfg;
  portEXIT_CRITICAL(&settingsLock);
  return s;
}

void Drive::setSettings(Settings s) {
  s = clampSettings(s);
  portENTER_CRITICAL(&settingsLock);
  cfg = s;
  portEXIT_CRITICAL(&settingsLock);

  Stored st = {.version = SETTINGS_VERSION, .s = s};
  Preferences prefs;
  if (prefs.begin("drive", false)) {
    prefs.putBytes("cfg", &st, sizeof(st));
    prefs.end();
  }
  Log::printf("Drive: settings saved (left=%c, invert A=%d B=%d, stop %u/%u us, span %u us, timeout %u ms, "
              "balance %d/%d/%d/%d us)",
              s.swap ? 'B' : 'A', s.invert[0], s.invert[1], s.stopUs[0], s.stopUs[1], s.spanUs, s.timeoutMs,
              s.balanceUs[0], s.balanceUs[1], s.balanceUs[2], s.balanceUs[3]);
}

int Drive::left() { return outLeft; }
int Drive::right() { return outRight; }
