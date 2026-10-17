# CatFinder Supersmall: developer notes

For people who want to build or change the software. The build guide is the
[README](../README.md).

## Project layout

| Folder | Contents |
| --- | --- |
| `firmware/src/` | Robot firmware (PlatformIO, Arduino-ESP32 3.x). `Config.h` holds pins, names and timings. |
| `firmware/src/WebPage.h` | The robot's web pages (control page and WiFi setup page). |
| `android/` | Android app (Kotlin, Jetpack Compose). |
| `docs/` | These notes and the README illustrations (`docs/img/*.svg`). |
| `.github/workflows/` | Release build. |

## Firmware

Install [PlatformIO](https://platformio.org) (the VS Code extension or
`pip install platformio`), then from this folder:

```sh
pio run -t upload -t monitor     # first install over USB (ESP32-CAM-MB)
pio run -e ota -t upload         # update over WiFi (change upload_port in platformio.ini to the IP if needed)
```

You can also update from the web page: **Setup → Firmware update** with
`catfinder-X.Y.Z.bin` (not the `-factory` file). Updates are accepted only while the robot is
connected to your WiFi. A new firmware must join WiFi, run for 30 seconds and answer a request
on its own web server before it is kept. If it crashes or does not get there within 2
minutes, the robot goes back to the previous firmware by itself.

| Feature | Details |
| --- | --- |
| Camera | OV2640, VGA JPEG, two frame buffers in PSRAM. Five quality levels (`/video?level=`); the app picks one from the measured command delay. Powered only while streaming. |
| Drive | Two continuous-rotation servos on IO13 / IO2, 50 Hz via LEDC timer 2, channels 2 and 3 (the camera uses timer 0 / channel 0). Servos stop when no `/drive` arrives within the timeout (500 ms default). Speed 0 sends no pulse. |
| WiFi | Credentials in NVS. Setup hotspot `CatFinder-XXXX` (open, captive portal) when nothing is stored, when the stored network is not joined within 30 s of boot, or after 3 min without connection. mDNS `catfinder.local`, service `_catfinder._tcp`. |
| Power | CPU at 160 MHz. Camera off 30 s after the last stream; WiFi modem sleep and CPU 80 MHz after 30 s without requests. No deep sleep: nothing could wake the board over the network. |
| Log | `/log` shows the last 4 KB of log messages; also on the serial port at 115200 baud. |

## Android app

Needs JDK 17. From `android/`:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The APK is `android/app/build/outputs/apk/debug/CatFinder.apk`. Android 8.0 or newer.

The **Robot address** setting takes an IP address, a host name, or a URL. Plain addresses use
`http://<address>` for commands and `http://<address>:81/stream` for video. An `https://` URL
is treated as a reverse proxy that serves everything, including `/stream`, on one address.

## HTTP API

Port 80 unless noted. Every request counts as use and keeps the robot out of its power-saving
state.

| Request | Answer |
| --- | --- |
| `GET /` | Control page; the WiFi setup page while the setup hotspot is open. |
| `GET /status` | JSON: version, WiFi, memory, camera, power, drive state. |
| `GET /log` | Recent log messages as text. |
| `GET /drive?l=<-100..100>&r=<-100..100>[&seq=<n>]` | `ok <ms since boot>`. Repeat at least every 500 ms while moving. |
| `GET /video?level=<0..4>` | Stream quality, 0 = best. |
| `GET /drive_cfg`, `POST /drive_cfg` | Wheel settings as JSON; POST takes form fields `swap`, `inv_a`, `inv_b`, `stop_a`, `stop_b`, `span`, `timeout`, `bal0`..`bal3`, `reset=1`. |
| `GET /servo_test?s=<0\|1>&us=<pulse>` | Raw pulse on one servo, for setup. |
| `GET /wifi/scan` | Networks in range as JSON. |
| `POST /wifi` | Form `ssid`, `pass`: store and restart. |
| `POST /wifi/forget` | Erase the stored network and restart into setup. |
| `POST /update` | Body: `firmware.bin`. Installs it and restarts. |
| `GET :81/stream` | MJPEG video. Each frame carries `X-Timestamp` (capture time in ms since boot). |

## Security

The robot trusts your home network, like most hobby devices: anyone on your WiFi can watch the
camera, drive the robot, change its settings and install firmware. The setup hotspot has no
password and is open only while the robot is not connected to your WiFi. For remote access,
use Tailscale or a VPN, not port forwarding.

## Releasing

Push a tag `vX.Y.Z`. The release workflow builds the firmware and the app and attaches
`catfinder-X.Y.Z.bin`, `catfinder-X.Y.Z-factory.bin` and `CatFinder-X.Y.Z.apk` to a GitHub
release. The version number goes into the firmware and the app.

To sign the APK with your own key, so that users can install updates over an older
version, create a key once and add four repository secrets
(**Settings → Secrets and variables → Actions**):

```sh
keytool -genkeypair -v -keystore catfinder.jks -keyalg RSA -keysize 2048 -validity 10000 -alias catfinder
base64 -i catfinder.jks | pbcopy   # macOS; paste as ANDROID_KEYSTORE_BASE64
```

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The keystore, base64-encoded. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | `catfinder` |
| `ANDROID_KEY_PASSWORD` | Key password. |

Keep the keystore file safe and out of the repository. Without these secrets the release
contains a debug-signed APK, and users have to uninstall the old app before installing a
new one.
