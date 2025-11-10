# Android Bluetooth Keyboard and Mouse

This document outlines the plan for creating an Android application that functions as a Bluetooth keyboard and mouse.

## Development

The project will be developed using the following tools and technologies:

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose
*   **Build Tool:** Gradle with Kotlin DSL (.gradle.kts)

## Features

*   **Settings:** Touchpad sensitivity, scroll speed/invert, key repeat delay, click-sound toggle, horizontal scroll enable/invert, and middle-click toggle (persisted via DataStore).
*   **Bluetooth Connectivity (Pairing Page):** A dedicated screen for managing Bluetooth connections.
    *   **Layout:**
        *   **List of Available Devices:** Displays discoverable Bluetooth devices.
        *   **Scan Button:** Initiates or restarts device discovery.
        *   **Connection Status:** Shows current connection state (e.g., "Disconnected," "Connected to [Device Name]").
        *   **Disconnect Button:** Appears when connected, allowing disconnection.
    *   **Workflow:**
        *   **Discovery:** User taps "Scan" to find devices. The list populates with discovered devices.
        *   **Pairing & Connecting:** User taps a device in the list to connect. Status updates during the process. Once connected, the user can switch to input modes.
        *   **Disconnecting:** User taps "Disconnect" on the pairing page to end the current connection.
*   **Input Modes:**
    *   **Keyboard Mode:** US QWERTY with modifiers, arrows, F1–F12, Insert/Delete/Home/End/PageUp/PageDown; multi‑key chords (6‑key rollover); media keys via Consumer Control; visual feedback and optional click audio; Caps/Num/Scroll lock LED sync.
    *   **Mouse Mode:** Single‑finger move; tap to left‑click; two‑finger tap right‑click; two‑finger vertical and horizontal scroll (AC Pan); three‑finger tap middle‑click; sensitivity and scroll tuning with invert options.
*   **Mode Switching:** A clear and intuitive way for the user to switch between keyboard, mouse, and settings screens.
*   **Orientation:** The application will be locked to landscape mode for a more natural keyboard and touchpad experience.
*   **Foreground Service:** Persistent notification; auto‑reconnect to last device; direct BluetoothHidDevice connect/disconnect.
*   **Connection UX:** Top bar shows current device/disconnected; brief “Disconnected” snackbar when auto‑navigating to Pairing.

## Compatibility

### Cross‑platform behavior notes

- Scrolling: Some hosts interpret positive vertical wheel as up (Windows/macOS); the app provides invert toggles for both vertical and horizontal wheels.
- Horizontal wheel: True h‑wheel (AC Pan) is used; some apps on macOS may map it to trackpad swipe; invert if behavior feels reversed.
- Pointer gain: Sensitivity defaults tuned for typical 10–12" tablets; adjust in Settings for phones or large tablets.
- Media keys: Consumer Control usages vary by OS/app; some targets may ignore certain keys.

This application targets standard Android devices (phones and tablets) that support the Bluetooth Human Interface Device (HID) profile. Amazon Fire HD tablets are not supported due to Fire OS limitations regarding Bluetooth HID peripheral functionality.

Last updated: 2025-11-09T12:00:00.000Z

## Current Status

### 2025-11-10 — Recent progress

- Added non-numpad extended keys in the Extended Keys UI and mapping: PrintScreen (`PRTSC`), Pause/Break (`PAUSE`) and Insert (`INS`).
- Extracted `labelToHid` mapping helper and added `ExtendedKeyMappingsTest` (positive, negative and boundary cases).
- Removed NumLock support across UI, actions, middleware and sender implementations (per project decision). CapsLock and ScrollLock remain.
- Host JVM unit tests (including the new mapping tests and existing `BluetoothKeySender` tests) run locally and are passing.

The rest of the status section below describes the broader project surface; the bullets above are the most recent, repo-level changes.

- Core HID keyboard and mouse implemented; touchpad gesture stack implemented via Compose pointer APIs (multi-finger move, vertical/horizontal scroll, middle-click, right-click); media keys and settings available.
- ReduxKotlin store scaffolding in place: keyboard modifiers and HID intents now dispatch through the store/middleware, with connection/settings slices defined for the upcoming migration of service state.
 - Keyboard and Mouse screens migrated to use the central Redux store: Compose screens now read UI state via `StoreProvider.asStateFlow()` and dispatch HID intents (KeyDown/KeyUp/SendKey, MoveMouse/LeftClick/Scroll) so middleware handles platform side-effects.
 - Added host JVM unit tests for `BluetoothKeySender` that verify forwarding to `IBluetoothService`, exception propagation, and use `verifyNoMoreInteractions` to prevent unexpected calls.
 - Host JVM unit tests are passing locally (ran via `./gradlew :app:test`).
- Foreground service with persistent notification, auto-reconnect, and connection UX (status in TopAppBar, brief “Disconnected” overlay) is in place.
- Debug logging (toggle + viewer/export + level filter) is implemented; Quick Settings tile and permission UX added.
- System IME integration: a "Use system keyboard" toggle and a small TextField that accepts committed characters from the Android IME and translates them to HID reports using the app's char→HID mapper. A runtime heuristic samples committed characters and auto-disables system IME when non-Latin input is detected to avoid sending incorrect HID keycodes.
 - System IME integration: a "Use system keyboard" toggle and a small TextField that accepts committed characters from the Android IME and translates them to HID reports using the app's char→HID mapper. A runtime heuristic samples committed characters and auto-disables system IME when non-Latin input is detected to avoid sending incorrect HID keycodes. Keys that are not normally exposed by the system IME (F-keys, extended navigation keys, some punctuation/media controls) are available on separate Extended Key pages — split into logical groups (e.g., Function keys, Navigation/Editing keys, Media & Consumer controls, and Punctuation) rather than trying to cram a full US layout onto a single page.
- Per-IME persistence: users can "Always allow" or "Never allow" the current IME; these choices are persisted via DataStore (SettingsManager) and exposed in Settings (human-friendly IME labels shown). The IME-reject dialog can persist allow/deny decisions.
- Local preview mode: when running in environments without Bluetooth (emulator), a "Local preview" toggle shows a human-readable log of HID events (codes, modifiers, unmapped characters) instead of attempting to send them. This makes it easy to validate keyboard and IME mapping on an emulator.
- Unit tests (host JVM) are passing and the project builds successfully. See the Tests section for commands.


## Known Issues (Windows Pairing)

- Pairs with code exchange, then Windows shows "Driver error"; app remains Status: Disconnected (STATE_CONNECTED not reached).
- Sometimes listed as "Unknown device"; in-app discovery can be unreliable; pairing via Android system settings sometimes works better.
- Action items: simplify HID descriptors for Windows, add more hid.connect() state logs, test BLE HID (HOGP), capture Windows Bluetooth logs.

## Session Notes (2025-11-06T20:50:13.316Z)

- Observed: App starts then stops/returns to launcher shortly after launch.
- Hypotheses: Foreground service not started/kept, crash during init (Bluetooth/HID/permissions), missing Android 12+ runtime Bluetooth permissions, battery optimization killing the service, or unhandled exception in navigation.
- Next steps:
  - Capture logs: adb logcat | grep -iE 'AndroidRuntime|FATAL|BT|Hid|Service|Crash' immediately after launch; share stacktrace.
  - Verify Foreground Service: notification visible and startForeground invoked within 5s of start; confirm START_STICKY if service should persist.
  - Re-check runtime permissions on Android 12+: BLUETOOTH_CONNECT/SCAN/ADVERTISE in manifest and requested at runtime before HID registration.
  - Test with battery optimizations disabled for the app (Settings > Apps > Special access > Battery optimization).
  - Add guard logs around HID registration and service lifecycle (onCreate/onStartCommand/onDestroy) to pinpoint stop.
  - Ensure Activity finish doesn't stop service; decouple UI from service lifecycle.
- Repro data to capture: device model, Android version, host OS, first-run vs post-permission grant, and whether the foreground notification appears.

## Session Notes (2025-11-08T08:50:42.824Z)

- UI polish: app title uses string resource and larger style; nav icons have content descriptions; touchpad hint styled; dividers added to paired list; minor layout tweaks in Logs.
- Build: Debug APK built successfully at app/build/outputs/apk/debug/app-debug.apk.
- Next: Smoke test on a phone and a tablet; verify launch, foreground notification, permissions UX, navigation, keyboard/mouse basics; record OS-specific quirks (Windows/macOS/Linux).
 - Build: Debug APK built successfully at app/build/outputs/apk/debug/app-debug.apk. Unit tests are green.
 - Next: Smoke test on a phone and a tablet; verify launch, foreground notification, permissions UX, navigation, keyboard/mouse basics; record OS-specific quirks (Windows/macOS/Linux). Also validate system IME behavior for the specific IMEs you expect users to run (Gboard, AOSP LatinIME, SwiftKey), and test the stored per-IME allow/deny behavior.
- If the launch-stop issue reproduces, follow the diagnostics under the earlier Session Notes and capture logcat immediately.

## Session Notes (2025-11-09T10:05:00.000Z)

- Integrated upstream ReduxKotlin 0.5.5 and expanded the store to include keyboard, UI, connection, and settings slices; middleware now routes HID and connection intents via a pluggable `KeySender` bridge.
 - Existing Compose screens now read state from the Redux store; actions for discovery, pairing, HID events, and settings are defined and the UI dispatches them into middleware for side-effects.
- `./gradlew :app:compileDebugKotlin` passes with the new Redux scaffolding; no runtime verification performed yet.
 - Pending follow-up: connect the Bluetooth services to the KeySender bridge, finish any small wiring between service and middleware, and ensure screens dispatch the canonical actions handled by middleware.

## To-Do List

**Phase 1: Project Setup & Basic UI**

*   **Task 1: Create Android Project**
    *   [x] Subtask 1.1: Set up a new Android project with Kotlin, Jetpack Compose, and Gradle (`.gradle.kts`).
    *   [x] Subtask 1.2: Configure necessary permissions in `AndroidManifest.xml` (e.g., `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`).
*   **Task 2: Implement Basic UI Shell**
    *   [x] Subtask 2.1: Create the main navigation structure to switch between the Pairing, Keyboard, and Mouse screens.
    *   [x] Subtask 2.2: Create placeholder screens for Pairing, Keyboard, and Mouse.
    *   [x] Subtask 2.3: Lock the app to landscape mode.

**Phase 2: Bluetooth Connectivity**

*   **Task 3: Implement Pairing Screen UI**
    *   [x] Subtask 3.1: Create the UI for the pairing screen, including the device list, scan button, and status display.
*   **Task 4: Implement Bluetooth Service**
    *   [x] Subtask 4.1: Create a background service to manage the Bluetooth connection (foreground service on modern Android).
    *   [x] Subtask 4.2: Implement Bluetooth device discovery (scanning).
    *   [x] Subtask 4.3: Implement pairing and connection logic.
    *   [x] Subtask 4.4: Implement disconnection logic.
*   **Task 5: Implement Bluetooth HID Module**
    *   [x] Subtask 5.1: Create the Bluetooth HID profile implementation.
    *   [x] Subtask 5.2: Define HID report descriptors for the keyboard and mouse.
    *   [x] Subtask 5.3: Use BluetoothHidDevice connect/disconnect (no reflection), persist last device, auto-reconnect, unregister on destroy.

**Phase 3: Input Modes**

*   **Task 6: Implement Keyboard Screen**
    *   [x] Subtask 6.1: Create the UI for the virtual keyboard (with F-keys and arrows).
    *   [x] Subtask 6.2: Implement visual feedback for key presses (color change).
    *   [x] Subtask 6.3: Implement audio feedback for key presses (clicking sound, toggleable).
    *   [x] Subtask 6.4: Send keyboard HID reports to the connected device (press/hold/release support).
*   **Task 7: Implement Mouse Screen**
    *   [x] Subtask 7.1: Create the UI for the touchpad.
    *   [x] Subtask 7.2: Implement gesture detection for cursor movement, clicks, and scrolling.
    *   [x] Subtask 7.3: Send mouse HID reports to the connected device.
    *   [x] Subtask 7.4: Add configurable sensitivity and scroll speed/invert.

**Phase 4: Finalization & Polish**

*   **Task 8: Refine UI and UX**
    *   [ ] Polish the UI for all screens (spacing/icons/layout).
    *   [x] Show brief “Disconnected” overlay and keep status in TopAppBar.
    *   [x] Disable Keyboard/Mouse nav items when disconnected.
    *   [x] Permission UX: show rationale dialogs and a Settings deeplink when permanently denied.
*   **Task 9: Diagnostics**
    *   [x] Debug logging toggle (persisted) with in‑app log viewer/export; log HID/connection events.
*   **Task 10: Tuning & Testing**
    *   [x] Cross‑OS/device tuning: pointer sensitivity, scroll speed, horizontal wheel behavior, media key compatibility.
    *   [ ] Test the app on various Android devices and host devices (Windows, macOS, Linux).

## Build and Smoke Test Instructions

- Build debug APK:
  - ./gradlew --no-daemon :app:assembleDebug
- Locate APK: app/build/outputs/apk/debug/app-debug.apk
- List connected devices:
  - adb devices
- Install on specific device (replace <serial>):
  - adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
- Launch activity:
  - adb -s <serial> shell am start -n com.augustusmachin.android_bt_kbmouse/.MainActivity
- Quick checks: foreground notification appears within 5s; navigation between Pairing/Keyboard/Mouse/Settings; basic input works.
- If issues: capture logs
  - adb -s <serial> logcat -d | grep -iE 'AndroidRuntime|FATAL|Hid|Service|Bluetooth'

## Testing Plan

- Devices: test on at least 1 phone + 1 tablet (Android 10–14). Disable battery optimizations and verify foreground notification persists.
- Permissions: fresh install flows on Android 12+/13+ (BLUETOOTH_* + notifications), denial/rationale/settings deeplink.
- Pairing/Connect: scan, pair, connect, disconnect, auto-reconnect after service/activity restart and Bluetooth off/on.
- Keyboard: verify modifiers, F1–F12, arrows, nav keys, repeat/hold, Caps/Num/Scroll lock LEDs sync; media keys in common apps.
- Mouse: 1-finger move, 1/2/3-finger taps (left/right/middle), 2-finger vertical/horizontal scroll with invert options; sensitivity/scroll speed tuning.
- Hosts: Windows 10/11, macOS 12+/13+/14+, Linux (Wayland/X11). Note any OS-specific quirks (e.g., horizontal scroll mapping on macOS).
- Reliability: service lifecycle (startForeground <5s), survives Doze/app swipe-away, boot autostart (if enabled), reconnection after range loss.
- Logging: ensure log viewer/export captures HID and connection events at different log levels.
- Regression: navigation guards (disable Keyboard/Mouse when disconnected), brief “Disconnected” snackbar on auto-navigation to Pairing.

## Next steps (short)
- Implement a concrete `KeySender` that connects Redux middleware to `BluetoothService`/`IBluetoothService` operations.
 - Pairing view-model state and side-effects have been migrated into Redux actions/middleware; Compose screens now consume the store instead of relying on a ViewModel.
- Once Redux migration is complete, regression-test keyboard and mouse flows (modifiers, gestures, scroll) against the new dispatch pipeline.
- After Redux work, resume HID descriptor simplification and Windows pairing investigation; keep detailed logs around `hid.connect()` as part of that effort.
- Continue improving discovery reliability (debounce/cancel-start sequence) once connection state lives in the Redux store.

## Repro commands
- Build/install debug: ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
- Launch: adb shell am start -n com.augustusmachin.android_bt_kbmouse/.MainActivity
- Logs (focused): adb logcat -d | grep -iE 'BluetoothHid|BluetoothService|HidDevice|BOND_STATE|onConnectionStateChanged|AndroidRuntime|FATAL'
- Live logs: adb logcat | grep -iE 'BluetoothHid|BluetoothService|HidDevice|BOND_STATE|onConnectionStateChanged'

## Environment matrix
- Phone: Samsung Galaxy A54 (SM-A546E), Android 15 (model seen by adb)
- Host: Windows 11 (exact build: fill in), device name "MIZUMI"
- App: debug build as of 2025-11-08T17:22:21.918Z

## Debugging cheatsheet
- Reset BT on phone: adb shell service call bluetooth_manager 8 && sleep 2 && adb shell service call bluetooth_manager 6
- Clear app data: adb shell pm clear com.augustusmachin.android_bt_kbmouse
- Check BT adapter state: adb shell dumpsys bluetooth_manager | sed -n '1,120p'
- Verify HID profile state changes: adb logcat | grep -i 'onConnectionStateChanged\|BOND_STATE\|HidDevice'

## Planned Unit Tests (coverage roadmap)

- Pairing (store) tests status:
  - [x] initialState_isEmpty
  - [x] startDiscovery_setsMessage_and_callsService
  - [x] stopDiscovery_callsService_and_clearsMessage
  - [x] updatesDiscovered_andPairedLists_fromServiceCallbacks
  - [x] connect_onSelection_invokesService_and_updatesState
  - [x] disconnect_updatesState_and_message
  - [x] surfacesErrorMessages_and_clearsOnNavigate
- [x] BluetoothService/IBluetoothService integration (connection/bond callbacks, reconnect decisions)
- [x] HID report builders (keyboard rollover, media, mouse move/buttons, scroll invert bytes)
- [x] DataStore settings (defaults, persist/restore, migration safety)
- [x] Gesture translation (1-finger move, 2-finger v/h scroll accumulation, 3-finger middle tap debounce)
- [x] Auto-reconnect (persist last device, exponential backoff, stop conditions)
- [x] Foreground service lifecycle (5s guard, START_STICKY, notification text)
- [x] Permissions UX logic (rationale vs settings, gating actions)
- [ ] UI guards (nav disabled when disconnected, snackbar emission)  <-- not yet unit tested
- [x] Logging (level filtering, bounded buffer, export scope)
- [x] BLE HOGP logic (report map selection, CCCD notify flag)
- [x] Descriptor variants (simplified vs full bytes)
- [x] Quick Settings tile (routing constant, safe noop)


Note: keep these as host JVM unit tests with fakes/mocks; avoid Android framework calls or use small pure helpers so logic is testable.

## Tests
- Unit tests: ./gradlew :app:testDebugUnitTest
- Connected UI tests: ./gradlew :app:connectedDebugAndroidTest


Status update (2025-11-08T23:53:19.007Z)
- MainActivity restored to HEAD; project builds successfully.
- Added Extended screen: enum entry + NavHost route; ExtendedKeysScreen.kt present.
- KeyboardScreen remains original (IME-based simplification not applied yet).
- Next steps: decide on IME KeyboardScreen swap and wire Extended keys to send HID events.
