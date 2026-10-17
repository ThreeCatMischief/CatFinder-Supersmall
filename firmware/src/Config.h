#pragma once

// Set by the release build; "dev" for local builds.
#ifndef FW_VERSION
#define FW_VERSION "dev"
#endif

// mDNS name: http://catfinder.local. Must match upload_port of env:ota in platformio.ini.
#define WIFI_HOSTNAME "catfinder"
// Setup hotspot name; the last two bytes of the MAC address are appended.
#define SETUP_AP_PREFIX "CatFinder-"

#define HTTP_PORT   80   // page, /status, /log
#define STREAM_PORT 81   // MJPEG stream (own server so it does not block the page)

// AI Thinker ESP32-CAM camera pins
#define CAM_PIN_PWDN   32
#define CAM_PIN_RESET  -1
#define CAM_PIN_XCLK    0
#define CAM_PIN_SIOD   26
#define CAM_PIN_SIOC   27
#define CAM_PIN_D7     35
#define CAM_PIN_D6     34
#define CAM_PIN_D5     39
#define CAM_PIN_D4     36
#define CAM_PIN_D3     21
#define CAM_PIN_D2     19
#define CAM_PIN_D1     18
#define CAM_PIN_D0      5
#define CAM_PIN_VSYNC  25
#define CAM_PIN_HREF   23
#define CAM_PIN_PCLK   22

#define PIN_FLASH_LED   4

// Drive: two continuous-rotation SG90 on servo A and B. Which one is left, the
// direction of each and the pulse trims are set on the web page and stored in NVS;
// the values below are the defaults before anything is saved.
#define PIN_SERVO_A  13
#define PIN_SERVO_B   2
#define DRIVE_DEFAULT_SWAP     false  // false: A is left, B is right
#define DRIVE_DEFAULT_INVERT_A false
#define DRIVE_DEFAULT_INVERT_B true   // servos face opposite directions
#define DRIVE_DEFAULT_STOP_US  1500   // pulse width at which a servo stands still
#define DRIVE_DEFAULT_SPAN_US   500   // pulse offset from stop at full speed
#define DRIVE_DEFAULT_TIMEOUT_MS 500  // servos stop if no command arrives for this long

// Power. The camera powers down CAM_OFF_AFTER_MS after the last stream ended. With
// no request and no stream for IDLE_AFTER_MS the WiFi modem sleeps between beacons
// and the CPU drops to CPU_IDLE_MHZ; the next request brings both back.
#define CAM_OFF_AFTER_MS  30000
#define IDLE_AFTER_MS     30000
#define CPU_IDLE_MHZ         80
#define CPU_ACTIVE_MHZ      160  // must match board_build.f_cpu in platformio.ini
