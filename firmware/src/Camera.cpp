#include "Camera.h"
#include <driver/gpio.h>
#include <esp_camera.h>
#include "Config.h"
#include "Log.h"

namespace {
struct Level {
  framesize_t size;
  int quality;  // JPEG quality, lower is better
  uint32_t intervalMs;
};
// Frame buffers are sized for the init frame size, so no level may exceed it.
constexpr Level LADDER[Camera::LEVELS] = {
    {FRAMESIZE_VGA, 12, 0},
    {FRAMESIZE_VGA, 18, 0},
    {FRAMESIZE_CIF, 15, 0},
    {FRAMESIZE_QVGA, 15, 83},   // 12 fps
    {FRAMESIZE_QVGA, 22, 167},  // 6 fps
};

// Guards power state and sensor access: the stream task powers up, loop() powers
// down, the page server sets the level.
SemaphoreHandle_t mutex = nullptr;
bool initOk = false;
bool on = false;
int users = 0;
uint32_t releasedAt = 0;
framesize_t maxSize = FRAMESIZE_VGA;
framesize_t frameSize = FRAMESIZE_VGA;
int currentLevel = 0;
char sensor[16] = "none";

struct Lock {
  Lock() { xSemaphoreTake(mutex, portMAX_DELAY); }
  ~Lock() { xSemaphoreGive(mutex); }
};

camera_config_t config() {
  camera_config_t c = {};
  c.pin_pwdn = CAM_PIN_PWDN;
  c.pin_reset = CAM_PIN_RESET;
  c.pin_xclk = CAM_PIN_XCLK;
  c.pin_sccb_sda = CAM_PIN_SIOD;
  c.pin_sccb_scl = CAM_PIN_SIOC;
  c.pin_d7 = CAM_PIN_D7;
  c.pin_d6 = CAM_PIN_D6;
  c.pin_d5 = CAM_PIN_D5;
  c.pin_d4 = CAM_PIN_D4;
  c.pin_d3 = CAM_PIN_D3;
  c.pin_d2 = CAM_PIN_D2;
  c.pin_d1 = CAM_PIN_D1;
  c.pin_d0 = CAM_PIN_D0;
  c.pin_vsync = CAM_PIN_VSYNC;
  c.pin_href = CAM_PIN_HREF;
  c.pin_pclk = CAM_PIN_PCLK;
  c.xclk_freq_hz = 20000000;
  c.ledc_timer = LEDC_TIMER_0;
  c.ledc_channel = LEDC_CHANNEL_0;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size = maxSize;
  if (psramFound()) {
    c.jpeg_quality = 12;
    c.fb_count = 2;
    c.fb_location = CAMERA_FB_IN_PSRAM;
    c.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    c.jpeg_quality = 15;
    c.fb_count = 1;
    c.fb_location = CAMERA_FB_IN_DRAM;
    c.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  }
  return c;
}

void applyLevel(sensor_t* s, int level) {
  const Level& l = LADDER[level];
  framesize_t size = l.size > maxSize ? maxSize : l.size;
  if (size != frameSize) s->set_framesize(s, size);
  s->set_quality(s, l.quality);
  frameSize = size;
  currentLevel = level;
}

// PWDN high puts the sensor in standby. The driver leaves the pin as an output.
void sensorStandby() {
  gpio_set_direction((gpio_num_t)CAM_PIN_PWDN, GPIO_MODE_OUTPUT);
  gpio_set_level((gpio_num_t)CAM_PIN_PWDN, 1);
}

// Mutex held.
bool powerUp() {
  if (on) return true;
  uint32_t t0 = millis();
  camera_config_t c = config();
  esp_err_t err = esp_camera_init(&c);
  if (err != ESP_OK) {
    Log::printf("Camera: init failed 0x%x", err);
    initOk = false;
    sensorStandby();
    return false;
  }
  initOk = on = true;
  frameSize = maxSize;
  if (sensor_t* s = esp_camera_sensor_get()) {
    // The sensor's default readout is mirrored left to right; flip it back.
    s->set_hmirror(s, !s->status.hmirror);
    int level = currentLevel;
    currentLevel = 0;
    if (level != 0) applyLevel(s, level);
    camera_sensor_info_t* info = esp_camera_sensor_get_info(&s->id);
    strlcpy(sensor, info ? info->name : "unknown", sizeof(sensor));
  }
  Log::printf("Camera: on, %s @ %s, level %d, %lu ms", sensor, Camera::frameSizeName(), currentLevel,
              (unsigned long)(millis() - t0));
  return true;
}

// Mutex held.
void powerDownLocked() {
  if (on) {
    esp_camera_deinit();
    on = false;
    Log::printf("Camera: off");
  }
  sensorStandby();
}
}  // namespace

bool Camera::begin() {
  if (!mutex) mutex = xSemaphoreCreateMutex();
  if (!psramFound()) {
    maxSize = frameSize = FRAMESIZE_QVGA;
    Log::printf("Camera: no PSRAM, falling back to QVGA");
  }
  Lock lock;
  releasedAt = millis();
  return powerUp();
}

bool Camera::ok() { return initOk; }

bool Camera::isOn() { return on; }

bool Camera::acquire() {
  Lock lock;
  if (!powerUp()) return false;
  users++;
  return true;
}

void Camera::release() {
  Lock lock;
  if (users > 0) users--;
  releasedAt = millis();
}

void Camera::powerDownIfIdle(uint32_t idleMs) {
  if (!on) return;
  Lock lock;
  if (users == 0 && millis() - releasedAt >= idleMs) powerDownLocked();
}

void Camera::setLevel(int level) {
  level = constrain(level, 0, LEVELS - 1);
  Lock lock;
  if (level == currentLevel) return;
  if (!on) {
    // Applied at the next power-up; frameSizeName() reports the target size meanwhile.
    currentLevel = level;
    frameSize = LADDER[level].size > maxSize ? maxSize : LADDER[level].size;
    return;
  }
  sensor_t* s = esp_camera_sensor_get();
  if (!s) return;
  applyLevel(s, level);
  Log::printf("Camera: level %d, %s q%d", level, frameSizeName(), LADDER[level].quality);
}

int Camera::level() { return currentLevel; }

uint32_t Camera::frameIntervalMs() { return LADDER[currentLevel].intervalMs; }

const char* Camera::sensorName() { return sensor; }

const char* Camera::frameSizeName() {
  switch (frameSize) {
    case FRAMESIZE_QVGA: return "QVGA 320x240";
    case FRAMESIZE_CIF:  return "CIF 400x296";
    case FRAMESIZE_VGA:  return "VGA 640x480";
    case FRAMESIZE_SVGA: return "SVGA 800x600";
    default:             return "other";
  }
}
