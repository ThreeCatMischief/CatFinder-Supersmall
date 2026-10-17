# CatFinder Supersmall

A tiny camera robot you drive from your phone to check on your cat.

## What is it?

CatFinder Supersmall is a small two-wheeled robot built around the ESP32-CAM, a cheap camera
board with WiFi. You see live video from the robot and steer it with a joystick on your phone
or in a web browser. Drive it under the sofa, into the next room, or wherever your cat hides.

- Live video and joystick control in an Android app or any web browser (also on iPhone).
- Set up from your phone: no programming and no passwords in the software.
- Works on your home WiFi. With the free Tailscale app it also works when you are away from
  home (see [Use it away from home](#use-it-away-from-home)).
- Saves battery when nobody is using it.
- No account, no cloud, no subscription.

## What you need

| Part | Notes |
| --- | --- |
| AI Thinker ESP32-CAM | The common ESP32-CAM board with the OV2640 camera. |
| ESP32-CAM-MB adapter | Small USB board the ESP32-CAM plugs into. Needed once, to install the software. |
| 2 × continuous-rotation servo | SG90 360° (or FS90R). These drive the wheels. Normal 180° servos do not work. |
| 2 wheels and a body | Anything that holds the board, the servos and the battery. |
| 5 V power supply | For example a small USB power bank. It must deliver at least 1.5 A: WiFi and the servos draw short current peaks, and weak supplies make the board restart. |
| USB data cable | For the first install. Some cables only charge; those do not work. |

## Wiring

| From | To |
| --- | --- |
| Left servo, signal wire (orange or yellow) | ESP32-CAM pin **IO13** |
| Right servo, signal wire | ESP32-CAM pin **IO2** |
| Both servos, red wire | **5V** |
| Both servos, brown or black wire | **GND** |
| Power supply + and − | ESP32-CAM **5V** and **GND** |

Do not put a memory card into the ESP32-CAM: the card slot shares pins with the servos.

Which servo counts as left, and which direction is forward, can be changed later in the
settings. If you mix them up, nothing breaks.

## Get started

### 1. Install the software on the robot

You do this once, with a computer and the Chrome or Edge browser.

1. Download the file **`catfinder-X.Y.Z-factory.bin`** (X.Y.Z is the version number) from the
   [releases page](https://github.com/ThreeCatMischief/CatFinder-Supersmall/releases).
2. Plug the ESP32-CAM onto the ESP32-CAM-MB adapter and connect the adapter to your computer
   with the USB cable.
3. Open Espressif's web installer: <https://espressif.github.io/esptool-js/>.
4. Click **Connect** and choose the port that appears (often called *USB Serial* or
   *CH340*).
5. Enter `0x0` as **Flash Address**, choose the downloaded file, and click **Program**.
   This takes about a minute.
6. When it says it is finished, press the **RST** button on the ESP32-CAM.

If **Connect** fails: hold the **IO0** button on the adapter, press and release **RST**,
release **IO0**, and try again.

### 2. Connect the robot to your WiFi

1. Switch the robot on. After a few seconds it opens its own WiFi network called
   **CatFinder-XXXX** (four letters or numbers at the end).
2. On your phone, open the WiFi settings and join **CatFinder-XXXX**. It has no password.
3. A setup page opens by itself. If it does not, open a browser and go to
   <http://192.168.4.1>.
4. Tap your home WiFi, enter its password and tap **Connect**.
5. The robot restarts and joins your WiFi. Switch your phone back to your home WiFi.

If your phone says the CatFinder network has no internet, choose to stay connected.

If the robot cannot join your WiFi (for example because of a typo in the password), the
**CatFinder-XXXX** network comes back within a minute and you can try again.

### 3. Drive

**Android app.** Download **`CatFinder-X.Y.Z.apk`** from the
[releases page](https://github.com/ThreeCatMischief/CatFinder-Supersmall/releases) and open
it on your phone. Android asks whether your browser may install apps; allow it. The app is
not in the Play Store. In the app, open **Settings** and tap **Find on my WiFi**.

**Web browser** (any phone, tablet or computer, including iPhone). Open
<http://catfinder.local>. If that page does not open, use the robot's address instead, for
example `http://192.168.1.50`. You find the address in the app (**Find on my WiFi**) or in your
router's list of devices, where the robot is called **catfinder**.

Drag the circle to drive. On a computer you can also use the arrow keys or W, A, S, D, and
the space bar to stop. The robot stops by itself when it loses the connection.

The camera needs about a second to start when you open the video.

### 4. Set up the wheels (first time only)

Open the web page, then **Setup → Wheels**.

1. **Stop point.** Continuous-rotation servos have a "stand still" signal that differs a
   little from servo to servo. Press **■** for one servo. If its wheel still creeps, change
   **Stop point** a few steps at a time until it stands still. Repeat for the other servo.
2. **Which wheel.** Press **▶** for servo A. If the right wheel turns, set **Which wheel** to
   *right*.
3. **Reverse direction.** Drive forward with the joystick. If a wheel turns backwards, tick
   **Reverse direction** for it.
4. **Drive straight.** Under **Setup → Drive straight**, pick a speed, press **Run** and
   watch the robot. If it curves, adjust with **+** and **−** until it goes straight. Do this
   for each speed.

Every change is saved on the robot immediately.

## Use it away from home

The robot only talks to devices on your home WiFi. To reach it from anywhere, use
[Tailscale](https://tailscale.com), a free app that connects your devices privately over the
internet. It needs **one device that stays at home, switched on and connected to your WiFi**:
an old Android phone or Android TV box on its charger, an Apple TV, a Raspberry Pi, or a
computer that does not go to sleep. The robot itself does not need Tailscale.

1. **Give the robot a fixed address.** In your router's settings, reserve its current
   address for the device called **catfinder** (often called *DHCP reservation* or *static
   lease*). Otherwise the address can change and the app loses the robot.
2. **Create a Tailscale account** and install Tailscale on your phone and on the home device.
   Sign in with the same account on both.
3. **Let the home device share your home network.** Tailscale calls this a *subnet router*.
   You enter your home network's address range: take the robot's address, replace the last
   number with 0 and add `/24`. For `192.168.1.50` that is `192.168.1.0/24`.
   - Android: in the Tailscale app, tap your account picture, then **Settings → Subnet
     routing → Add route**, enter the range, tap **OK**.
   - Apple TV, Mac, Windows, Linux, Raspberry Pi: follow Tailscale's guide,
     <https://tailscale.com/kb/1019/subnets>.
4. **Approve it.** Open the [Tailscale admin console](https://login.tailscale.com/admin/machines),
   select the home device, go to **Subnets → Edit**, tick the range and **Save**.
5. **In the CatFinder app**, enter the robot's address (for example `192.168.1.50`) in
   **Settings → Robot address**. Names like `catfinder.local` do not work over Tailscale;
   the address does.

Away from home, switch Tailscale on on your phone and use the app as usual. In a browser,
open `http://192.168.1.50` (with your robot's address).

Do not make the robot reachable from the internet through your router (port forwarding).
Anyone could then watch the camera and drive it.

## Battery

The robot saves power by itself:

- The camera switches off 30 seconds after nobody watches the video.
- After 30 seconds without use, WiFi and the processor slow down. The robot stays
  reachable; the first command after a break takes a moment longer.

It does not switch itself off completely, because it could then not be woken up over WiFi.
Switch it off when you do not need it for a long time.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| The robot restarts again and again, or never joins WiFi. | The power supply is too weak. Use one that delivers at least 1.5 A, and a short, thick cable. |
| `catfinder.local` does not open. | Some phones and networks do not support these names. Use the robot's address (see [Drive](#3-drive)). |
| The app says "CatFinder not reachable". | Check that the robot is on and the phone is on the same WiFi. Away from home: is Tailscale on? Is the address right? |
| A wheel creeps when the robot should stand still. | Adjust its **Stop point** (see [Set up the wheels](#4-set-up-the-wheels-first-time-only)). |
| The video is slow or jerky. | Move closer to the WiFi router. In the app, set **Video quality** to *Auto*. |
| I changed my WiFi router or password. | When the robot cannot find its WiFi for 3 minutes, it opens **CatFinder-XXXX** again. Connect it as in step 2. To change the network while it is still connected: **Setup → WiFi → Change WiFi network**. |

---

## Reference

The rest of this page is for people who want to build or change the software.

### Project layout

| Folder | Contents |
| --- | --- |
| `firmware/src/` | Robot firmware (PlatformIO, Arduino-ESP32 3.x). `Config.h` holds pins, names and timings. |
| `firmware/src/WebPage.h` | The robot's web pages (control page and WiFi setup page). |
| `android/` | Android app (Kotlin, Jetpack Compose). |
| `.github/workflows/` | Release build. |

### Firmware

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

### Android app

Needs JDK 17. From `android/`:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The APK is `android/app/build/outputs/apk/debug/CatFinder.apk`. Android 8.0 or newer.

The **Robot address** setting takes an IP address, a host name, or a URL. Plain addresses use
`http://<address>` for commands and `http://<address>:81/stream` for video. An `https://` URL
is treated as a reverse proxy that serves everything, including `/stream`, on one address.

### HTTP API

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

### Security

The robot trusts your home network, like most hobby devices: anyone on your WiFi can watch the
camera, drive the robot, change its settings and install firmware. The setup hotspot has no
password and is open only while the robot is not connected to your WiFi. For remote access,
use Tailscale or a VPN, not port forwarding.

### Releasing

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

## License

MIT, see [LICENSE](LICENSE). Third-party notices in [NOTICE](NOTICE).
