#include <Arduino.h>
#include <WiFi.h>
#include <esp_ota_ops.h>
#include <esp_timer.h>
#include "Camera.h"
#include "Config.h"
#include "Drive.h"
#include "Log.h"
#include "Net.h"
#include "Power.h"
#include "Web.h"

// --- Rollback guard ---------------------------------------------------------
// A firmware update arrives over WiFi, so a new image must prove that WiFi and the
// web server work before it is kept. A freshly installed image boots as "pending
// verify". It is marked valid in loop() once it has run for OTA_CONFIRM_MS with WiFi
// connected and the web server answering a request. Any reset before that (crash,
// hang, watchdog, the deadline timer) makes the bootloader boot the previous image.
static constexpr uint32_t OTA_CONFIRM_MS = 30000;
static constexpr uint32_t OTA_DEADLINE_MS = 120000;
static constexpr uint32_t SELF_TEST_EVERY_MS = 5000;

static bool s_imagePending = false;
static bool s_webUp = false;
static esp_timer_handle_t s_deadline = nullptr;

// Overrides the weak core hook: without it initArduino() marks every image valid
// before setup() runs, and rollback never happens.
extern "C" bool verifyRollbackLater() { return true; }

static void otaDeadline(void*) {
  if (!s_imagePending) return;
  Log::printf("Update: new firmware not confirmed after %lu s, going back to the previous one",
              OTA_DEADLINE_MS / 1000);
  delay(100);
  esp_ota_mark_app_invalid_rollback_and_reboot();
  esp_restart();  // only reached if no other image is valid
}

static void startRollbackGuard() {
  esp_ota_img_states_t state;
  if (esp_ota_get_state_partition(esp_ota_get_running_partition(), &state) != ESP_OK) return;
  s_imagePending = (state == ESP_OTA_IMG_PENDING_VERIFY);
  if (!s_imagePending) return;
  Log::printf("Update: new firmware pending, must confirm within %lu s", OTA_DEADLINE_MS / 1000);
  // esp_timer runs on its own task, so this fires even if setup() or loop() hangs.
  const esp_timer_create_args_t args = {.callback = otaDeadline, .arg = nullptr,
                                        .dispatch_method = ESP_TIMER_TASK, .name = "ota_deadline",
                                        .skip_unhandled_events = true};
  if (esp_timer_create(&args, &s_deadline) == ESP_OK) esp_timer_start_once(s_deadline, OTA_DEADLINE_MS * 1000ULL);
}

// Fetches /status from the robot's own web server, so an image whose server crashes or
// hangs on a request is not kept: updates go through that server.
static bool webServerAnswers() {
  WiFiClient c;
  if (!c.connect(IPAddress(127, 0, 0, 1), HTTP_PORT, 2000)) return false;
  c.print("GET /status HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n");
  c.setTimeout(2000);
  String statusLine = c.readStringUntil('\n');
  c.stop();
  return statusLine.startsWith("HTTP/1.1 200");
}

static void confirmImageIfHealthy() {
  static uint32_t lastTest = 0;
  if (!s_imagePending) return;
  uint32_t now = millis();
  if (now < OTA_CONFIRM_MS || !s_webUp || !Net::connected()) return;
  if (lastTest && now - lastTest < SELF_TEST_EVERY_MS) return;
  lastTest = now;
  if (!webServerAnswers()) {
    Log::printf("Update: web server self-test failed");
    return;
  }
  // If the deadline timer has already fired, it decides; marking valid here would race it.
  if (s_deadline && esp_timer_stop(s_deadline) != ESP_OK) return;
  if (esp_ota_mark_app_valid_cancel_rollback() == ESP_OK) {
    s_imagePending = false;
    Log::printf("Update: new firmware confirmed");
  } else if (s_deadline) {
    // Not confirmed: put the deadline back where it was.
    uint32_t left = now < OTA_DEADLINE_MS ? OTA_DEADLINE_MS - now : 1;
    esp_timer_start_once(s_deadline, left * 1000ULL);
  }
}
// ----------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  startRollbackGuard();  // first, so a hang anywhere below still rolls back
  Power::begin();
  pinMode(PIN_FLASH_LED, OUTPUT);
  digitalWrite(PIN_FLASH_LED, LOW);
  Log::printf("CatFinder %s, build %s %s", FW_VERSION, __DATE__, __TIME__);

  Camera::begin();  // a failed camera still leaves WiFi, updates and the web page up
  Drive::begin();
  Net::begin();
  s_webUp = Web::begin();
}

void loop() {
  Net::loop();
  confirmImageIfHealthy();
  Drive::update();
  Power::update();
  delay(10);
}
