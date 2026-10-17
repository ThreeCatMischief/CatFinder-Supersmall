#include "Log.h"
#include <stdarg.h>

namespace {
constexpr size_t kBufSize = 4096;
char buf[kBufSize];
size_t head = 0;      // next write position
bool wrapped = false;
portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;
}  // namespace

void Log::printf(const char* fmt, ...) {
  char line[256];
  int n = snprintf(line, sizeof(line), "[%8lu] ", (unsigned long)millis());
  va_list args;
  va_start(args, fmt);
  n += vsnprintf(line + n, sizeof(line) - n, fmt, args);
  va_end(args);
  if (n >= (int)sizeof(line) - 1) n = sizeof(line) - 2;
  line[n++] = '\n';
  line[n] = '\0';

  Serial.print(line);

  portENTER_CRITICAL(&mux);
  for (int i = 0; i < n; i++) {
    buf[head++] = line[i];
    if (head == kBufSize) {
      head = 0;
      wrapped = true;
    }
  }
  portEXIT_CRITICAL(&mux);
}

size_t Log::snapshot(char* out, size_t outSize) {
  if (outSize == 0) return 0;
  size_t len = 0;
  portENTER_CRITICAL(&mux);
  if (wrapped) {
    size_t tail = kBufSize - head;
    size_t a = min(tail, outSize - 1);
    memcpy(out, buf + head, a);
    len = a;
  }
  size_t b = min(head, outSize - 1 - len);
  memcpy(out + len, buf, b);
  len += b;
  portEXIT_CRITICAL(&mux);
  out[len] = '\0';
  return len;
}
