# BlueDeck — Development Notes (archive)

Historical status logs, session notes, and debugging aids moved out of the
top-level `README.md` to keep it focused. Kept for reference; not actively
maintained.

## Current Status (as of 2025-11-10)

### Recent progress

- Added non-numpad extended keys in the Extended Keys UI and mapping: PrintScreen (`PRTSC`), Pause/Break (`PAUSE`) and Insert (`INS`).
- Extracted `labelToHid` mapping helper and added `ExtendedKeyMappingsTest` (positive, negative and boundary cases).
- Removed NumLock support across UI, actions, middleware and sender implementations (per project decision). CapsLock and ScrollLock remain.
- Host JVM unit tests (including the new mapping tests and existing `BluetoothKeySender` tests) run locally and are passing.
- Removed the large app title from the top bar and moved the bottom `NavigationBar` into the top bar so navigation isn't occluded by the system IME (`MainActivity.kt`).
- Updated `ExtendedKeysScreen` to remove the manual Show/Hide IME button, keep `autoShowKeyboard` behavior, and continue to provide the offline preview console when no host is connected (`ExtendedKeysScreen.kt`).

### Broader project surface

- Core HID keyboard and mouse implemented; touchpad gesture stack implemented via Compose pointer APIs (multi-finger move, vertical/horizontal scroll, middle-click, right-click); media keys and settings available.
- ReduxKotlin store scaffolding in place: keyboard modifiers and HID intents dispatch through the store/middleware, with connection/settings slices defined.
- Keyboard and Mouse screens read UI state via `StoreProvider.asStateFlow()` and dispatch HID intents (KeyDown/KeyUp/SendKey, MoveMouse/LeftClick/Scroll) so middleware handles platform side-effects.
- Foreground service with persistent notification, auto-reconnect, and connection UX (status in TopAppBar, brief "Disconnected" overlay) is in place.
- Debug logging (toggle + viewer/export + level filter) is implemented; Quick Settings tile and permission UX added.
- System IME integration: a "Use system keyboard" toggle and a small TextField that accepts committed characters from the Android IME and translates them to HID reports using the app's char→HID mapper. A runtime heuristic samples committed characters and auto-disables system IME when non-Latin input is detected. Keys not normally exposed by the system IME (F-keys, extended navigation keys, some punctuation/media controls) live on separate Extended Key pages, split into logical groups (Function, Navigation/Editing, Media & Consumer, Punctuation).
- Per-IME persistence: users can "Always allow" or "Never allow" the current IME; choices persisted via DataStore (SettingsManager) and exposed in Settings.
- Local preview mode: when running without Bluetooth (emulator), a "Local preview" toggle shows a human-readable log of HID events instead of sending them.

## Session Notes (2025-11-06)

- Observed: App starts then stops/returns to launcher shortly after launch.
- Hypotheses: Foreground service not started/kept, crash during init (Bluetooth/HID/permissions), missing Android 12+ runtime Bluetooth permissions, battery optimization killing the service, or unhandled exception in navigation.
- Next steps:
  - Capture logs: `adb logcat | grep -iE 'AndroidRuntime|FATAL|BT|Hid|Service|Crash'` immediately after launch; share stacktrace.
  - Verify Foreground Service: notification visible and `startForeground` invoked within 5s; confirm `START_STICKY` if service should persist.
  - Re-check runtime permissions on Android 12+: BLUETOOTH_CONNECT/SCAN/ADVERTISE in manifest and requested at runtime before HID registration.
  - Test with battery optimizations disabled for the app.
  - Add guard logs around HID registration and service lifecycle (onCreate/onStartCommand/onDestroy).
  - Ensure Activity finish doesn't stop service; decouple UI from service lifecycle.

## Session Notes (2025-11-08)

- UI polish: app title uses string resource and larger style; nav icons have content descriptions; touchpad hint styled; dividers added to paired list; minor layout tweaks in Logs.
- Build: Debug APK built successfully; unit tests green.
- Next: Smoke test on a phone and a tablet; verify launch, foreground notification, permissions UX, navigation, keyboard/mouse basics; record OS-specific quirks. Validate system IME behavior for common IMEs (Gboard, AOSP LatinIME, SwiftKey) and the stored per-IME allow/deny behavior.

## Session Notes (2025-11-09)

- Integrated upstream ReduxKotlin 0.5.5 and expanded the store (keyboard, UI, connection, settings slices); middleware routes HID and connection intents via a pluggable `KeySender` bridge.
- Compose screens read state from the Redux store; actions for discovery, pairing, HID events, and settings dispatched into middleware.
- `./gradlew :app:compileDebugKotlin` passes with the Redux scaffolding.

## Next steps (short)

- Implement a concrete `KeySender` connecting Redux middleware to `BluetoothService`/`IBluetoothService` operations.
- Once Redux migration is complete, regression-test keyboard and mouse flows (modifiers, gestures, scroll) against the new dispatch pipeline.
- Resume HID descriptor simplification and Windows pairing investigation; keep detailed logs around `hid.connect()`.
- Continue improving discovery reliability (debounce/cancel-start sequence).

## Repro commands

- Build/install debug: `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch: `adb shell am start -n com.augustusmachin.android_bt_kbmouse/.MainActivity`
- Logs (focused): `adb logcat -d | grep -iE 'BluetoothHid|BluetoothService|HidDevice|BOND_STATE|onConnectionStateChanged|AndroidRuntime|FATAL'`
- Live logs: `adb logcat | grep -iE 'BluetoothHid|BluetoothService|HidDevice|BOND_STATE|onConnectionStateChanged'`

## Debugging cheatsheet

- Reset BT on phone: `adb shell service call bluetooth_manager 8 && sleep 2 && adb shell service call bluetooth_manager 6`
- Clear app data: `adb shell pm clear com.augustusmachin.android_bt_kbmouse`
- Check BT adapter state: `adb shell dumpsys bluetooth_manager | sed -n '1,120p'`
- Verify HID profile state changes: `adb logcat | grep -i 'onConnectionStateChanged\|BOND_STATE\|HidDevice'`

## Environment matrix (reference)

- Phone: Samsung Galaxy A54 (SM-A546E)
- Host: Windows 11, device name "MIZUMI"
