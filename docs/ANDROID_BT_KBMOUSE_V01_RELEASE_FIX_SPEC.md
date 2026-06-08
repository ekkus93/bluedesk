# Android Bluetooth Keyboard/Mouse v0.1 Release Fix Spec

## 1. Purpose

This document defines the final release-fix scope for the Android Bluetooth keyboard/mouse app before tagging `v0.1`.

The goal is not to redesign the app. The goal is to make the existing app reliable enough for a first public or semi-public release by fixing the concrete issues found in the final static code review:

- Bluetooth discovery results must actually reach the Pairing UI.
- Classic Bluetooth HID and BLE HOGP modes must be mutually gated instead of both starting unconditionally.
- Startup must wait for persisted settings before choosing the HID backend.
- Runtime permissions must distinguish required, optional, and mode-specific permissions.
- UI/settings must accurately reflect which HID features are available in the active descriptor mode.
- User-visible settings must actually control behavior.
- Redux state must not go stale after device-management actions.
- Documentation must not claim features that are missing or incomplete.
- Regression tests must cover the release blockers.

Treat this as a stabilization/hardening patch. Keep the implementation small, explicit, and easy to review.

---

## 2. Current project context

The project is a Kotlin Android app using Jetpack Compose, Android Classic Bluetooth HID, experimental BLE HOGP support, DataStore-backed settings, and a Redux-style app store.

Important files and areas:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/MainActivity.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/BleHogpService.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsManager.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsViewModel.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsScreen.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/DebugLog.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/HidDescriptorVariants.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/HidReportBuilder.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/Actions.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/AppState.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/Middleware.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/Reducers.kt`
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/StoreProvider.kt`
- `README.md`
- `docs/PRD.md`
- JVM tests in `app/src/test/java/...`
- Instrumented tests in `app/src/androidTest/java/...`

---

## 3. Release criteria

The app is ready to tag as `v0.1` only when all of the following are true:

1. A user can scan for Bluetooth devices and discovered devices appear in the Pairing UI during the same scan session.
2. The app starts exactly one HID backend at a time:
   - Classic HID when `useBleHogp == false`.
   - BLE HOGP when `useBleHogp == true`.
3. The HID backend is selected after persisted settings have loaded, not from an initial default settings value.
4. Denying Android 13+ notification permission does not completely block the app from starting.
5. `BLUETOOTH_ADVERTISE` is requested only when BLE HOGP mode is enabled or about to be enabled.
6. Scroll UI/settings match the active HID descriptor mode.
7. Debug logging is enabled only when the debug logging setting says it is enabled, except for minimal unavoidable startup logs.
8. Device state in Redux is refreshed after setting default device, forgetting a device, unpairing a device, bond-state changes, and discovery changes.
9. `startDiscovery()` is protected against missing permissions and `SecurityException`.
10. Snackbar state is remembered across recomposition.
11. README/docs do not claim unimplemented features, especially media keys and completed landscape locking unless those features are actually implemented.
12. New regression tests cover the release blockers.
13. `./gradlew test` passes locally.
14. `./gradlew connectedAndroidTest` passes on at least one real device or emulator where Bluetooth-dependent tests are guarded/skipped appropriately.
15. A manual smoke test passes on a real Android device and at least one host computer.

---

## 4. Non-goals for this patch

Do not expand the feature set while fixing v0.1 blockers.

Do not do any of these unless absolutely required by the release blockers:

- Do not rewrite the Redux store.
- Do not split `MainActivity.kt` as part of this patch unless a tiny extraction makes a blocker easier and safer to fix.
- Do not implement a complete media-key subsystem unless it is already essentially complete and only needs docs cleanup.
- Do not redesign the UI.
- Do not introduce a new dependency unless there is no reasonable alternative.
- Do not change package names, minSdk, targetSdk, or app identity.
- Do not change HID report IDs unless tests and descriptors are updated accordingly.
- Do not silently remove existing user settings. Hide/disable misleading settings only when necessary.

---

## 5. Required behavior changes

### 5.1 Bluetooth discovery must update the Pairing UI

#### Problem

`BluetoothService` appears to maintain an internal `discoveredDevices` list, while `PairingScreen` reads discovered devices from Redux state:

```kotlin
val discoveredDevices = appState.connection.discoveredDevices
```

If `BluetoothService` receives `BluetoothDevice.ACTION_FOUND` but does not dispatch `Action.UpdateDiscoveredDevices(...)`, the UI will continue to show an empty list even though discovery is working internally.

#### Required behavior

When discovery starts:

- Clear the service's internal discovered-device list.
- Clear the Redux discovered-device list.
- Dispatch a discovering state update if one already exists in the action/state model.

When a device is found:

- Add/update the device in the service's discovered-device list.
- De-duplicate by Bluetooth MAC address.
- Dispatch the full current discovered-device list to Redux:

```kotlin
StoreProvider.dispatch(Action.UpdateDiscoveredDevices(getDiscoveredDevices()))
```

When discovery finishes or is cancelled:

- Dispatch a discovering-state update if available.
- Leave the final discovered-device list visible until the next scan or explicit clear.

#### Acceptance criteria

- Starting a scan immediately clears stale scan results.
- Every newly found device can appear in Pairing UI without restarting the app.
- Duplicate `ACTION_FOUND` broadcasts do not create duplicate rows.
- Missing `BLUETOOTH_SCAN` permission does not crash the service.
- `SecurityException` during discovery is logged and surfaced as a connection/status error, not as a crash.

---

### 5.2 Classic HID and BLE HOGP must be mutually gated

#### Problem

`MainActivity.startServicesAndBind()` currently starts both `BluetoothService` and `BleHogpService`. BLE HOGP is experimental and should not start unless explicitly enabled.

Starting both can cause:

- Unexpected BLE advertising.
- Extra foreground notification behavior.
- Unnecessary `BLUETOOTH_ADVERTISE` permission requirements.
- Host pairing confusion.
- Battery drain.
- Adapter-name changes from BLE service even when the user intended Classic HID.

#### Required behavior

At app startup, after settings are loaded:

- If `settings.useBleHogp == false`, start/bind only `BluetoothService`.
- If `settings.useBleHogp == true`, start/bind only `BleHogpService`.

If the user changes the BLE HOGP setting at runtime:

- Stop/unbind the currently active backend.
- Start/bind the newly selected backend.
- Reset or refresh connection state so the UI does not show stale connection information from the old backend.
- Ask for additional mode-specific permission only when switching into BLE HOGP mode.

#### Acceptance criteria

- Cold start with default settings does not start BLE HOGP service.
- Cold start with persisted `useBleHogp == true` starts BLE HOGP service, not Classic HID service.
- Toggling the setting at runtime switches backends cleanly.
- Logs clearly state which backend is active.
- Quick Settings tile and UI state reflect the active backend.

---

### 5.3 HID backend selection must wait for persisted settings

#### Problem

Reading `settingsViewModel.settings.value.useBleHogp` synchronously during service connection can observe the default `Settings()` value before DataStore has emitted the persisted value.

#### Required behavior

Backend startup must be based on loaded settings, not the initial default object.

Acceptable implementation options:

1. Add a `settingsLoaded` flag/state in `SettingsViewModel`, then start services only after settings are loaded.
2. Load settings once in `MainActivity` before starting services.
3. Collect settings and start services only after the first DataStore emission is received.

Do not make service startup depend on an initial `StateFlow` value that may still be a placeholder.

#### Acceptance criteria

- Persist `useBleHogp = true`, kill app, cold start app: BLE service starts.
- Persist `useBleHogp = false`, kill app, cold start app: Classic service starts.
- No visible flicker where one backend starts and is immediately replaced by the other.

---

### 5.4 Permissions must be required only when actually required

#### Problem

Startup currently treats all requested permissions as fatal. `POST_NOTIFICATIONS` and `BLUETOOTH_ADVERTISE` should not block Classic HID startup.

#### Required behavior

Permission policy:

- `BLUETOOTH_CONNECT`: required for Bluetooth HID operation on Android 12+.
- `BLUETOOTH_SCAN`: required for discovery/scanning on Android 12+; do not block non-scan UI if not granted, but scanning must request it.
- `BLUETOOTH_ADVERTISE`: required only for BLE HOGP mode.
- `POST_NOTIFICATIONS`: optional but strongly recommended on Android 13+ because foreground service notifications may be affected. Denial must not be treated like a total app failure.
- Older Android Bluetooth/location permission behavior should remain compatible with the app's existing minSdk/targetSdk policy.

Update permission UI copy so users understand:

- Which permissions are essential.
- Which permissions are mode-specific.
- Which permission denial limits which functionality.

#### Acceptance criteria

- Denying `POST_NOTIFICATIONS` does not show the fatal permission dialog.
- Classic HID mode does not request `BLUETOOTH_ADVERTISE` on startup.
- Entering BLE HOGP mode requests `BLUETOOTH_ADVERTISE` if needed.
- Scanning requests `BLUETOOTH_SCAN` if needed and fails gracefully if denied.
- Permission test logic is covered in unit tests where possible.

---

### 5.5 Scroll settings must match the active HID descriptor

#### Problem

`Settings.hidSimplified` defaults to `true`. In simplified mode, `BluetoothService.sendScroll()` and `sendScrollH()` are no-ops, but the UI/settings still expose scrolling as if it works.

#### Required behavior

Pick one release policy and implement it consistently.

Preferred v0.1 policy:

- Keep SIMPLE descriptor as the default for compatibility.
- In SIMPLE mode, clearly disable or hide scroll controls/settings that are not supported.
- Show explanatory text such as: `Scrolling requires the full HID descriptor. Disable simplified compatibility mode to enable scroll.`
- When FULL descriptor is active, enable vertical/horizontal scroll behavior according to the existing settings.

Alternative acceptable policy:

- Make FULL descriptor the default if scrolling is considered core v0.1 functionality.
- Update README/docs to mention that SIMPLE descriptor is a compatibility fallback that disables scroll.

Do not leave the UI in a state where a visible enabled scroll setting produces no HID report.

#### Acceptance criteria

- In SIMPLE mode, users are not led to believe scrolling is active.
- In FULL mode, vertical scroll sends a report.
- In FULL mode, horizontal scroll sends a report when enabled.
- Settings screen accurately explains the descriptor/scroll tradeoff.
- Tests cover SIMPLE-mode no-op behavior and FULL-mode scroll report forwarding.

---

### 5.6 Landscape orientation must be implemented or docs must be corrected

#### Problem

README/PRD imply landscape support/lock is complete, but the manifest does not appear to lock `MainActivity` to landscape.

#### Required behavior

Choose one policy:

#### Policy A: v0.1 is landscape-only

Set the activity orientation explicitly:

```xml
android:screenOrientation="landscape"
```

Also verify the UI still works on common landscape phone/tablet sizes.

#### Policy B: v0.1 supports both portrait and landscape

Do not lock orientation, but update README/PRD to remove claims that the app is landscape-locked. Ensure the UI is usable in portrait.

Preferred v0.1 policy: Policy A, because keyboard/mouse UI is likely designed around landscape.

#### Acceptance criteria

- Runtime behavior matches README/PRD.
- No doc says landscape lock is complete unless it is actually implemented.

---

### 5.7 Debug logging setting must actually control logging

#### Problem

`DebugLog.setEnabled(true)` is called unconditionally in startup/settings code. This ignores the user setting.

#### Required behavior

- On settings load, call `DebugLog.setEnabled(settings.debugLogging)`.
- On settings change, immediately apply the new value.
- `DebugLog.setLevel(...)` should still respect the selected level when logging is enabled.
- Logs screen may remain available, but it should accurately indicate when logging is disabled.
- Avoid recording user-input previews or Bluetooth MAC addresses when debug logging is off.

Minimal startup logs are acceptable only if needed for crash diagnosis, but they should be sparse and not include user input/device identifiers.

#### Acceptance criteria

- With debug logging disabled, new debug events are not appended.
- With debug logging enabled, events are appended at the selected level.
- Toggling debug logging changes behavior without app restart.
- Unit tests cover enable/disable behavior.

---

### 5.8 Redux state must refresh after device-management actions

#### Problem

The service updates internal state/preferences after operations like set-default, forget, unpair, and bond changes, but the Redux state is not always refreshed.

#### Required behavior

After setting default device:

- Persist default device address.
- Dispatch `Action.UpdateDefaultDevice(device.address)` or equivalent.
- Update UI immediately.

After clearing default device:

- Remove persisted default device address.
- Dispatch `Action.UpdateDefaultDevice(null)`.

After forgetting/unpairing a device:

- Remove alias/default if applicable.
- Disconnect if it is the connected device.
- Refresh paired devices.
- Refresh discovered devices if relevant.
- Dispatch connected/default/paired updates so UI is immediately consistent.

On `BluetoothDevice.ACTION_BOND_STATE_CHANGED`:

- Handle `BOND_BONDED` and `BOND_NONE`.
- Refresh paired devices on both events.
- If `BOND_NONE` applies to the default/connected device, clear affected state.

#### Acceptance criteria

- Star/default UI updates immediately after setting default.
- Star/default UI clears immediately after clearing/forgetting the default device.
- Forget/unpair removes the device from the paired list after the system reports the bond change.
- Connected state clears if the connected device is forgotten/unpaired.
- Regression tests cover reducer/action behavior and service/middleware update flow where practical.

---

### 5.9 `startDiscovery()` must be permission-safe and crash-safe

#### Problem

Discovery cancellation may be guarded, but `bluetoothAdapter.startDiscovery()` itself can throw on Android 12+ if permission is missing.

#### Required behavior

Before calling `startDiscovery()`:

- Check adapter availability.
- Check adapter enabled state.
- Check `BLUETOOTH_SCAN` permission when required.
- Call `cancelDiscovery()` only with permission.
- Call `startDiscovery()` inside `try/catch(SecurityException)`.
- Dispatch/log a user-visible failure state if scanning cannot start.

#### Acceptance criteria

- Missing scan permission does not crash.
- Disabled Bluetooth does not crash.
- `SecurityException` does not crash.
- Scan button can surface a useful failure message.

---

### 5.10 Snackbar host state must survive recomposition

#### Problem

`SnackbarHostState` is currently created directly inside composition instead of `remember`ed.

#### Required behavior

Use:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
```

or equivalent.

#### Acceptance criteria

- Snackbar messages do not disappear because of routine recomposition.
- No duplicate/stale snackbar host behavior during navigation changes.

---

### 5.11 README/docs must match implemented v0.1 behavior

#### Problem

README appears to claim features that are not fully implemented, especially media keys via Consumer Control and completed landscape behavior.

#### Required behavior

Update docs to clearly state:

- Classic Bluetooth HID is the primary v0.1 mode.
- BLE HOGP is experimental if it remains experimental.
- SIMPLE vs FULL HID descriptor tradeoff, including scroll availability.
- Which Android permissions are needed and why.
- Which features are supported in v0.1.
- Which features are planned/not yet implemented.
- Media keys should not be advertised as supported unless there is a working descriptor, report path, and UI/API to send them.
- Landscape behavior must match the manifest behavior.

#### Acceptance criteria

- README does not overclaim incomplete features.
- PRD or project docs do not contradict actual app behavior.
- Users can understand why scroll may be disabled in simplified descriptor mode.

---

## 6. Optional but recommended hardening

These are not mandatory blockers if time is tight, but they are strongly recommended before v0.1.

### 6.1 Backup/privacy policy

Current manifest allows backup with sample/empty backup rules. The app stores Bluetooth MAC addresses, aliases, last-device info, and settings.

Recommended options:

- Set `android:allowBackup="false"`, or
- Add explicit backup/data-extraction rules excluding Bluetooth device identifiers and preferences.

Acceptance criteria:

- Backup policy is intentional and documented.
- Device identifiers are not accidentally synced to cloud backup unless intentionally allowed.

### 6.2 Boot receiver export policy

If `BootReceiver` does not need to be externally invoked, set `android:exported="false"` where compatible with the app's boot behavior, or otherwise constrain it appropriately.

Acceptance criteria:

- Receiver export state is intentional and minimal.

### 6.3 Theme cleanup

The app is Compose-first but the theme parent appears to use `DarkActionBar`. Prefer a no-actionbar Material theme to avoid accidental duplicate chrome.

Acceptance criteria:

- No unexpected action bar appears.
- Theme remains visually consistent in light/dark mode.

### 6.4 Top app bar icon contrast

Top bar icons should use a color that matches the actual top app bar container. Avoid `onPrimary` unless the container is explicitly `primary`.

Acceptance criteria:

- Icons have good contrast in light and dark themes.

### 6.5 Key font size clamp

Current responsive key font math can produce very small text. Clamp key labels to a reasonable range.

Suggested behavior:

```kotlin
val keyFontSize = calculated.coerceIn(10f, 16f).sp
```

Acceptance criteria:

- Key labels remain legible on common landscape phone/tablet widths.

---

## 7. Testing requirements

Add or update tests for the exact regressions found in review.

### 7.1 Unit tests

Add JVM tests where possible for:

- Permission classification logic:
  - Required vs optional permissions.
  - BLE-only advertising permission.
  - Notification permission denial is non-fatal.
- Backend selection logic:
  - Classic when `useBleHogp == false`.
  - BLE when `useBleHogp == true`.
  - No backend selected until settings are loaded.
- Debug logging:
  - Disabled means new logs are not appended.
  - Enabled means logs are appended at the selected level.
- Redux reducers/actions:
  - `UpdateDiscoveredDevices` replaces/updates discovered list as intended.
  - `UpdateDefaultDevice` updates/clears default address.
  - Forget/unpair-related actions clear stale connected/default state where reducers own that behavior.
- Descriptor/scroll policy:
  - SIMPLE mode disables or hides scroll behavior.
  - FULL mode allows scroll reports.
- Snackbar host behavior may not be easy to unit-test; do not over-engineer a test if a Compose test is clearer.

### 7.2 Instrumented tests

Add or update instrumented tests where practical for:

- Pairing screen displays discovered devices when store updates.
- Pairing screen shows empty/loading/error states correctly.
- Settings screen disables/explains scroll controls in SIMPLE mode.
- Settings screen enables scroll controls in FULL mode.
- Permission UI copy or permission-denied states if existing test infrastructure supports it.

Bluetooth hardware-dependent tests should be guarded/skipped when the environment lacks Bluetooth support. Do not make CI fail just because no physical Bluetooth adapter is available.

### 7.3 Manual smoke test

Run on a real Android device before tagging:

1. Install fresh app.
2. Launch with default settings.
3. Grant required Bluetooth permissions.
4. Deny notification permission if prompted; verify app still opens and can proceed.
5. Scan for devices; verify discovered devices appear.
6. Pair/connect to a host computer.
7. Send basic keyboard keys.
8. Send modifier combos such as Ctrl+C/Ctrl+V if supported.
9. Use mouse movement/clicks.
10. Verify scroll behavior matches descriptor setting.
11. Set a default device; verify star updates immediately.
12. Kill/restart app; verify default persists.
13. Forget/unpair default device; verify UI clears stale state.
14. Toggle debug logging off; verify new detailed logs stop.
15. Toggle debug logging on; verify logs resume.
16. Optional: enable BLE HOGP and verify only BLE backend starts.
17. Optional: disable BLE HOGP and verify Classic backend starts again.

---

## 8. Implementation guidance

### 8.1 Prefer small helper functions

If logic becomes awkward inside `MainActivity.kt` or services, extract small pure helpers rather than expanding already-large files.

Good candidates:

- `PermissionPolicy.kt`
- `BackendMode.kt`
- `DescriptorFeaturePolicy.kt`

Keep helpers simple and covered by JVM tests.

### 8.2 Keep dispatch serialized where practical

Service callbacks can occur off the main thread. If the Redux store is not explicitly thread-safe, dispatch service-originated actions through a main-thread helper.

Example pattern:

```kotlin
private val mainHandler = Handler(Looper.getMainLooper())

private fun dispatchOnMain(action: Action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        StoreProvider.dispatch(action)
    } else {
        mainHandler.post { StoreProvider.dispatch(action) }
    }
}
```

Use this for Bluetooth callbacks and broadcast receivers if needed.

### 8.3 Do not swallow important errors silently

For Bluetooth permission/security failures:

- Log a concise debug/error message if logging is enabled.
- Update connection/status state if the UI has a place to show the problem.
- Avoid crashing.

### 8.4 Do not overclaim BLE HOGP

Unless BLE HOGP is verified end-to-end on real devices, keep it labeled experimental in UI/docs.

### 8.5 Docs must be edited in the same patch

If behavior changes, update README/docs immediately. Do not leave docs cleanup as a later task.

---

## 9. Suggested validation commands

Run locally from the project root:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew connectedAndroidTest
```

If connected tests require a real Bluetooth-capable Android device, document what was skipped and why.

Also run Android Studio lint if configured:

```bash
./gradlew lintDebug
```

Do not tag `v0.1` until the command results and manual smoke test notes are recorded in the implementation summary.

---

## 10. Expected implementation summary from Claude Code

When done, provide a concise summary containing:

- Files changed.
- Release blockers fixed.
- Tests added/updated.
- Commands run and pass/fail results.
- Manual test results or manual test instructions if hardware was unavailable.
- Any remaining known limitations that should be documented before `v0.1`.
