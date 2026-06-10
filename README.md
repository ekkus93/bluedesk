# BlueDeck

[![CI](https://github.com/ekkus93/android_bt_kbmouse/actions/workflows/ci.yml/badge.svg)](https://github.com/ekkus93/android_bt_kbmouse/actions/workflows/ci.yml)

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
  vertical/horizontal scroll. Adjustable sensitivity and scroll tuning with
  invert options.
- **System keyboard input** — an optional "Use system keyboard" mode types
  committed characters from your Android IME; extra keys (F-keys, navigation,
  punctuation) live on dedicated Extended Key pages.
- **Pairing & connection** — scan, pair, and connect from the Pairing screen;
  auto-reconnect to the last device; a persistent foreground-service
  notification; and a Quick Settings tile.
- **Settings** — touchpad sensitivity, scroll speed/invert, key repeat delay,
  click-sound toggle, horizontal scroll, and middle-click toggle (persisted).

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **State:** ReduxKotlin (threadsafe store + thunk middleware)
- **Async:** Kotlin Coroutines + Flow
- **Build:** Gradle Kotlin DSL (`build.gradle.kts`)

## Usage

1. **Pair.** On the **Pairing** screen, tap **Scan for devices**, then tap your
   computer in the list to connect. The status shows "Connected to [device]"
   once it's ready. (On the host, you may need to confirm the pairing request.)
2. **Type.** Open the **Keyboard** screen and tap keys. Use the Extended Key
   pages for F-keys, navigation, and punctuation; enable "Use system keyboard"
   in Settings to type with your phone's IME.
3. **Point.** Open the **Mouse** screen and use the touchpad area: drag to move,
   tap to click, two-/three-finger taps for right/middle click, two-finger drag
   to scroll.
4. **Tune.** Adjust sensitivity, scroll, and other options on the **Settings**
   screen.

The app is locked to portrait orientation.

## Compatibility

Targets standard Android phones and tablets that support the Bluetooth HID
profile. Amazon Fire HD tablets are **not** supported (Fire OS lacks Bluetooth
HID peripheral support).

- **Scrolling:** Two HID descriptor modes. *Simplified mode* (default,
  Windows-compatible) disables scroll — the scroll controls are hidden. *Full
  mode* enables two-finger vertical/horizontal scroll; switch in
  **Settings → Compatibility**.
- **Scroll direction:** Some hosts treat positive wheel as "up"; invert toggles
  are provided for both vertical and horizontal wheels.
- **Horizontal wheel:** True AC Pan is used; some macOS apps map it to a
  trackpad swipe — invert if it feels reversed.
- **Pointer gain:** Defaults tuned for ~10–12" tablets; adjust in Settings for
  phones or large tablets.
- **Media keys:** Not implemented yet (Consumer Control descriptor planned).
- **BLE HOGP:** Experimental — enable in **Settings → Compatibility** (requires
  BLUETOOTH_ADVERTISE). Classic Bluetooth HID is the default, recommended
  transport.

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
so pre-existing findings are grandfathered and only new ones fail; regenerate with
`ktlintGenerateBaseline` / `detektBaseline`.

CI runs Android Lint, ktlint, detekt, build, and unit tests on every push and pull request to `master`.
Physical HID tests are opt-in — see
[docs/PHYSICAL_HID_TESTING.md](docs/PHYSICAL_HID_TESTING.md).

## Known issues

- **Windows 11 pairing:** after the code exchange, Windows may show a "Driver
  error" and the app stays Disconnected (STATE_CONNECTED not reached). In-app
  discovery can be unreliable; pairing via Android system settings sometimes
  works better. Investigation ongoing (HID descriptor simplification, more
  connection-state logging).

## More docs

- [Roadmap & test plan](docs/ROADMAP.md)
- [Development notes (archive)](docs/DEVELOPMENT_NOTES.md)
- [Physical HID testing](docs/PHYSICAL_HID_TESTING.md)
- [Extended keys](docs/EXTENDED_KEYS.md)
