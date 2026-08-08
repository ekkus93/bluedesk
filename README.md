# BlueDeck

[![CI](https://github.com/ekkus93/bluedesk/actions/workflows/ci.yml/badge.svg)](https://github.com/ekkus93/bluedesk/actions/workflows/ci.yml)

Turn your Android phone into a Bluetooth keyboard and mouse.

BlueDeck pairs with a computer (or any host that supports the Bluetooth HID
profile) and acts as a wireless keyboard and touchpad — *the handy keyboard and
mouse*.

## Features

- **Keyboard** — US QWERTY with modifiers, arrows, F1–F12, and
  Insert/Delete/Home/End/PageUp/PageDown. Multi-key chords (6-key rollover),
  visual feedback, optional click sound, and Caps/Scroll lock LED sync.
- **Mouse / touchpad** — single-finger move, tap to left-click, two-finger tap
  for right-click, three-finger tap for middle-click, and two-finger
  vertical/horizontal scroll in Full descriptor mode (Simplified mode has no
  scroll). Adjustable sensitivity and scroll tuning with invert options.
- **System keyboard input** — an optional "Use system keyboard" mode types
  committed characters from your Android IME; extra keys (F-keys, navigation,
  punctuation) live on dedicated Extended Key pages.
- **Two HID backends** — Classic Bluetooth HID is the default workflow and
  supports device discovery and explicit device management. Experimental BLE
  HOGP advertises from the phone and uses a host-initiated pair/connect flow.
- **Connection lifecycle** — backend readiness, sender availability, discovery,
  connection loss, and transport failures are tracked explicitly; Keyboard and
  Mouse controls are enabled only when the selected backend is actually usable.
- **Settings** — touchpad sensitivity, scroll speed/invert, key repeat delay,
  click-sound toggle, horizontal scroll, and middle-click toggle (persisted).

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **State:** ReduxKotlin (threadsafe store + thunk middleware)
- **Async:** Kotlin Coroutines + Flow
- **Build:** Gradle Kotlin DSL (`build.gradle.kts`)

## Usage

### Classic Bluetooth HID (default)

1. **Pair/connect.** On the **Pairing** screen, tap **Scan for devices**, then
   select your computer. The screen exposes the Classic discovered/paired-device
   workflow and reports connection or startup failures explicitly. On Linux/BlueZ,
   the known-good HID-profile connection procedure is D-Bus
   `org.bluez.Device1.ConnectProfile` for the HID UUID; see
   [docs/PHYSICAL_HID_TESTING.md](docs/PHYSICAL_HID_TESTING.md).
2. **Type.** Open the **Keyboard** screen and tap keys. Use the Extended Key
   pages for F-keys, navigation, and punctuation; enable **Use system keyboard**
   in Settings to type with your phone's IME.
3. **Point.** Open the **Mouse** screen and use the touchpad area: drag to move,
   tap to click, two-/three-finger taps for right/middle click, and use scrolling
   only when the active backend/report capabilities support it.
4. **Tune.** Adjust sensitivity, scroll, and other options on the **Settings**
   screen.

### BLE HOGP (experimental)

1. Enable **BLE HOGP** under **Settings → Compatibility**. BlueDeck switches
   backends live; an app restart is not required.
2. Open **Pairing** and wait for BLE startup to reach its advertising-ready
   state. The BLE screen intentionally does not expose Classic Scan/Connect/
   Rename/default-device controls.
3. Start pairing/connecting from the **host** while the phone is advertising.
4. If BLE initialization fails, BlueDeck keeps the backend out of Ready state and
   surfaces the failure/remediation instead of presenting input as usable.

The app is locked to portrait orientation.

## Compatibility

BlueDeck requires **Android 9 / API 28 or newer**. API 28 is the minimum because
that is the first supported Android level for the app's primary Classic
`BluetoothHidDevice` peripheral workflow. API 26/27 are intentionally outside
the supported/installable product matrix rather than being advertised with a
partially unusable core workflow.

Targets standard Android phones and tablets that support the Bluetooth HID
peripheral APIs. Amazon Fire HD tablets are **not** supported (Fire OS lacks the
required Bluetooth HID peripheral support).

- **Android permissions:** API 28–30 use the legacy/location permission model for
  Classic discovery. API 31+ uses Nearby Devices permissions such as
  `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, and `BLUETOOTH_ADVERTISE` according to
  the selected operation/backend.
- **Scrolling:** Two HID descriptor modes. *Simplified mode* (default,
  Windows-compatible) disables scroll — the scroll controls are hidden. *Full
  mode* enables two-finger vertical/horizontal scroll; switch in
  **Settings → Compatibility**. Backend capabilities can further restrict which
  controls are shown.
- **Scroll direction:** Some hosts treat positive wheel as "up"; invert toggles
  are provided for both vertical and horizontal wheels.
- **Horizontal wheel:** True AC Pan is used; some macOS apps map it to a
  trackpad swipe — invert if it feels reversed.
- **Pointer gain:** Defaults tuned for ~10–12" tablets; adjust in Settings for
  phones or large tablets.
- **Media keys:** Not implemented yet (Consumer Control descriptor planned).
- **BLE HOGP:** Experimental. The phone advertises a HOGP GATT profile and the
  host initiates pairing/connection. Classic-only discovery/device-management
  controls and unsupported scroll operations are not presented as working BLE
  actions.

## Building

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Install on a connected device (use `adb devices` to find the serial)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.augustusmachin.android_bt_kbmouse/.MainActivity
```

Builds run on JDK 21; the app targets Java 17 bytecode (Android's supported
ceiling).

Tagged releases (`v*`) are built by CI, which publishes a GitHub Release with
the APK attached.

## Testing

```bash
# Host JVM unit tests
./gradlew :app:testDebugUnitTest

# Lint (Android Lint) + static analysis / style
./gradlew :app:lintDebug
./gradlew :app:ktlintCheck   # style; ./gradlew :app:ktlintFormat to auto-fix
./gradlew :app:detekt        # static analysis

# Instrumented tests (requires a connected device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

ktlint and detekt use baselines (`app/ktlint-baseline.xml`, `app/detekt-baseline.xml`)
so pre-existing findings are grandfathered and only new ones fail. Hardening work
must fix new findings or use narrowly justified source-level suppressions; the
baseline should not be regenerated merely to make a new finding disappear.

CI runs Android Lint, ktlint, detekt, build, JVM tests, and instrumented-test
compilation on every push and pull request to `master`. The permanent
Instrumented UI workflow also runs the non-physical real-screen suite across API
28, 30, 31, 34, and 35 emulators.

Physical HID tests are opt-in and host-initiated — on Linux/BlueZ the host opens
the HID profile with `dbus-send … org.bluez.Device1.ConnectProfile
string:00001124-…` (not just `bluetoothctl connect`). See
[docs/PHYSICAL_HID_TESTING.md](docs/PHYSICAL_HID_TESTING.md).

## Known issues

- **Windows 11 pairing:** after the code exchange, Windows may show a "Driver
  error" and the app stays Disconnected (STATE_CONNECTED not reached). In-app
  discovery can be unreliable; pairing via Android system settings sometimes
  works better. Investigation ongoing (HID descriptor simplification, more
  connection-state logging).
- **BLE HOGP device name:** while the BLE HOGP backend is active it sets the
  phone's Bluetooth name to "BlueDeck" (restored on stop). A host that paired
  under the old name may need to re-pair to pick up the new name.

## More docs

- [Roadmap & test plan](docs/ROADMAP.md)
- [Development notes (archive)](docs/DEVELOPMENT_NOTES.md)
- [Physical HID testing](docs/PHYSICAL_HID_TESTING.md)
- [Extended keys](docs/EXTENDED_KEYS.md)
