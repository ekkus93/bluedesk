# BlueDeck — Roadmap & Test Plan

Moved out of `README.md`. Tracks remaining work and the manual QA / unit-test
coverage plan.

## To-Do

### Phase 1 — Project setup & basic UI
- [x] Android project: Kotlin, Jetpack Compose, Gradle Kotlin DSL.
- [x] Permissions in `AndroidManifest.xml` (BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, …).
- [x] Main navigation between Pairing, Keyboard, and Mouse screens.
- [x] Placeholder screens for Pairing, Keyboard, Mouse.

### Phase 2 — Bluetooth connectivity
- [x] Pairing screen UI (device list, scan button, status).
- [x] Background/foreground service to manage the connection.
- [x] Device discovery (scanning).
- [x] Pairing and connection logic.
- [x] Disconnection logic.
- [x] Bluetooth HID profile implementation + report descriptors (keyboard and mouse).
- [x] `BluetoothHidDevice` connect/disconnect (no reflection), persist last device, auto-reconnect, unregister on destroy.

### Phase 3 — Input modes
- [x] Virtual keyboard UI (F-keys and arrows).
- [x] Visual + audio feedback for key presses (toggleable click sound).
- [x] Send keyboard HID reports (press/hold/release).
- [x] Touchpad UI and gesture detection (move, clicks, scroll).
- [x] Send mouse HID reports.
- [x] Configurable sensitivity and scroll speed/invert.

### Phase 4 — Finalization & polish
- [ ] Polish UI for all screens (spacing/icons/layout).
- [x] Brief "Disconnected" overlay; status in TopAppBar.
- [x] Disable Keyboard/Mouse nav items when disconnected.
- [x] Permission UX: rationale dialogs + Settings deeplink when permanently denied.
- [x] Debug logging toggle (persisted) with in-app viewer/export.
- [x] Cross-OS/device tuning: pointer sensitivity, scroll speed, horizontal wheel, media-key compatibility.
- [ ] Test on various Android devices and hosts (Windows, macOS, Linux).

## Manual test plan

- **Devices:** at least 1 phone + 1 tablet (Android 10–14). Disable battery optimizations and verify the foreground notification persists.
- **Permissions:** fresh-install flows on Android 12+/13+ (BLUETOOTH_* + notifications), denial/rationale/settings deeplink.
- **Pairing/Connect:** scan, pair, connect, disconnect, auto-reconnect after service/activity restart and Bluetooth off/on.
- **Keyboard:** modifiers, F1–F12, arrows, nav keys, repeat/hold, Caps/Scroll lock LED sync; media keys in common apps.
- **Mouse:** 1-finger move, 1/2/3-finger taps (left/right/middle), 2-finger vertical/horizontal scroll with invert; sensitivity/scroll tuning.
- **Hosts:** Windows 10/11, macOS 12+/13+/14+, Linux (Wayland/X11). Note OS-specific quirks.
- **Reliability:** service lifecycle (startForeground <5s), survives Doze/app swipe-away, boot autostart (if enabled), reconnection after range loss.
- **Logging:** log viewer/export captures HID and connection events at different levels.
- **Regression:** navigation guards (disable Keyboard/Mouse when disconnected), brief "Disconnected" snackbar on auto-navigation to Pairing.

## Unit-test coverage (host JVM)

- Pairing store:
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
- [ ] UI guards (nav disabled when disconnected, snackbar emission) — not yet unit tested
- [x] Logging (level filtering, bounded buffer, export scope)
- [x] BLE HOGP logic (report map selection, CCCD notify flag)
- [x] Descriptor variants (simplified vs full bytes)
- [x] Quick Settings tile (routing constant, safe noop)

> Keep these as host JVM unit tests with fakes/mocks; avoid Android framework
> calls, or use small pure helpers so logic is testable.
