#include "Power.h"
#include <WiFi.h>
#include <atomic>
#include "Camera.h"
#include "Config.h"
#include "Log.h"
#include "Net.h"
#include "Web.h"

namespace {
std::atomic<uint32_t> lastActivity{0};
bool modemSleeping = false;
bool cpuSlow = false;

// touch() runs on other tasks and can store a time later than `now`; that counts as 0.
uint32_t idleAt(uint32_t now) {
  int32_t d = (int32_t)(now - lastActivity.load());
  return d > 0 ? d : 0;
}
}  // namespace

void Power::begin() { lastActivity = millis(); }

void Power::touch() { lastActivity = millis(); }

void Power::update() {
  uint32_t now = millis();
  if (Web::streamClients() > 0 || Web::otaActive()) lastActivity = now;
  bool idle = idleAt(now) >= IDLE_AFTER_MS;

  // Modem sleep does not work while the setup hotspot runs.
  bool wantModemSleep = idle && !Net::portalActive();
  if (wantModemSleep != modemSleeping) {
    WiFi.setSleep(wantModemSleep);
    modemSleeping = wantModemSleep;
    Log::printf("Power: modem sleep %s", wantModemSleep ? "on" : "off");
  }
  if (idle != cpuSlow) {
    setCpuFrequencyMhz(idle ? CPU_IDLE_MHZ : CPU_ACTIVE_MHZ);
    cpuSlow = idle;
    Log::printf("Power: CPU %lu MHz", (unsigned long)getCpuFrequencyMhz());
  }
  Camera::powerDownIfIdle(CAM_OFF_AFTER_MS);
}

uint32_t Power::idleMs() { return idleAt(millis()); }
bool Power::modemSleep() { return modemSleeping; }
