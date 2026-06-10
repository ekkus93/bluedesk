# Android Bluetooth Keyboard/Mouse v0.1 Release Fix TODO

## Implementation rules

- Keep this as a release-stabilization patch, not a rewrite.
- Fix the concrete v0.1 blockers first.
- Prefer small, testable helpers over expanding large methods.
- Update docs in the same patch as behavior changes.
- Add focused regression tests for every release blocker that can reasonably be tested.
- Do not tag `v0.1` until the final validation checklist passes.

---

## Phase 1 — Discovery state must reach the Pairing UI

### Task 1.1 — Dispatch discovered devices when a scan starts

- [ ] Locate discovery start logic in `BluetoothService.kt`.
- [ ] Clear the service's internal `discoveredDevices` list at the beginning of a new scan.
- [ ] Dispatch `Action.UpdateDiscoveredDevices(emptyList())` or equivalent to clear Redux state.
- [ ] If the store has an `isDiscovering`/status field, dispatch the appropriate discovery-start action.
- [ ] Ensure dispatch happens on the main thread if the store is not thread-safe.

Acceptance criteria:

- [ ] Starting a new scan clears stale discovered-device rows from the Pairing UI.
- [ ] No stale devices from prior scans remain unless they are rediscovered.

### Task 1.2 — Dispatch discovered devices on `BluetoothDevice.ACTION_FOUND`

- [ ] In the broadcast receiver handling `BluetoothDevice.ACTION_FOUND`, add/update the found device in the internal list.
- [ ] De-duplicate devices by MAC address.
- [ ] Dispatch `Action.UpdateDiscoveredDevices(getDiscoveredDevices())` or equivalent after every new/updated discovery result.
- [ ] Guard device name/address access with required permission checks where needed.
- [ ] Log a concise discovery message without crashing if permissions are unavailable.

Acceptance criteria:

- [ ] Discovered devices appear in the Pairing UI during the same scan session.
- [ ] Duplicate broadcasts do not produce duplicate UI rows.
- [ ] Discovery results do not require app restart to appear.

### Task 1.3 — Dispatch discovery completion/cancel state

- [ ] Handle discovery-finished broadcast by updating any discovery/status state in Redux.
- [ ] Handle discovery cancellation/failure by updating status state if available.
- [ ] Keep final discovered list visible after scan completion.

Acceptance criteria:

- [ ] The UI can stop showing scanning/progress state when discovery ends.
- [ ] Final discovered devices remain visible until the next scan or explicit clear.

### Task 1.4 — Add discovery regression tests

- [ ] Add/extend reducer tests for `UpdateDiscoveredDevices`.
- [ ] Add a test for list replacement/clear behavior.
- [ ] Add a test for duplicate-device handling if duplicate handling is in a pure helper.
- [ ] Add/extend a Compose/instrumented test proving Pairing UI renders devices from store state if practical.

Acceptance criteria:

- [ ] Tests fail on the old stale-discovery behavior and pass after the fix.

---

## Phase 2 — Gate Classic HID vs BLE HOGP backend startup

### Task 2.1 — Introduce explicit backend mode selection

- [ ] Add or identify a small enum/helper representing backend mode, for example `CLASSIC_HID` and `BLE_HOGP`.
- [ ] Derive backend mode from loaded settings: `useBleHogp == true` means BLE HOGP, otherwise Classic HID.
- [ ] Keep the helper pure and easy to unit-test.

Acceptance criteria:

- [ ] Backend selection is not scattered across ad hoc `if` checks.
- [ ] Tests can verify backend selection from settings.

### Task 2.2 — Start only the selected backend on cold start

- [ ] Update `MainActivity.startServicesAndBind()` or equivalent startup logic.
- [ ] Start/bind `BluetoothService` only in Classic HID mode.
- [ ] Start/bind `BleHogpService` only in BLE HOGP mode.
- [ ] Do not start BLE HOGP service by default.
- [ ] Log which backend is active.

Acceptance criteria:

- [ ] Default app startup starts only Classic HID service.
- [ ] Persisted BLE mode startup starts only BLE HOGP service.
- [ ] There are not two foreground HID services running at once.

### Task 2.3 — Stop/unbind inactive backend when switching modes

- [ ] When switching from Classic to BLE, unbind/stop Classic service as appropriate.
- [ ] When switching from BLE to Classic, unbind/stop BLE service as appropriate.
- [ ] Reset stale connected-device/backend state during the transition.
- [ ] Ensure Quick Settings tile state is refreshed after the switch.

Acceptance criteria:

- [ ] Runtime mode switch does not leave both services active.
- [ ] UI does not show a stale connected device from the old backend.
- [ ] Logs clearly show backend transition.

### Task 2.4 — Add backend-selection tests

- [ ] Test Classic mode selected when `useBleHogp == false`.
- [ ] Test BLE mode selected when `useBleHogp == true`.
- [ ] Test no service startup before settings are loaded if a loaded-state helper is added.
- [ ] Add service-start tests through pure logic where direct Android service testing is not practical.

Acceptance criteria:

- [ ] Tests protect against accidentally starting both backends again.

---

## Phase 3 — Remove settings-load race

### Task 3.1 — Add explicit settings-loaded state

- [ ] Update `SettingsViewModel` or startup code to distinguish default placeholder settings from loaded persisted settings.
- [ ] Expose a loaded flag/state, or provide a suspend/one-shot loading path before service startup.
- [ ] Avoid reading `settingsViewModel.settings.value.useBleHogp` synchronously before DataStore emits.

Acceptance criteria:

- [ ] Startup backend mode is based on persisted settings, not initial default settings.

### Task 3.2 — Delay service startup until settings are loaded

- [ ] In `MainActivity`, wait for loaded settings before calling service-start logic.
- [ ] Show safe loading UI or keep current UI while services are not started yet.
- [ ] Avoid starting one backend and then immediately switching when settings load.

Acceptance criteria:

- [ ] Cold start with persisted BLE setting starts BLE only.
- [ ] Cold start with persisted Classic setting starts Classic only.
- [ ] There is no startup flicker of the wrong backend.

### Task 3.3 — Add settings-load regression tests

- [ ] Add test for backend startup blocked until settings-loaded flag is true, if helper exists.
- [ ] Add test for persisted settings deciding backend mode.

Acceptance criteria:

- [ ] Tests cover the old race-prone behavior.

---

## Phase 4 — Fix runtime permission policy

### Task 4.1 — Create or update permission classification helper

- [ ] Identify current permission helper logic, likely in `MainActivity.kt` and/or `PermissionUxLogic.kt`.
- [ ] Model permissions as required, optional, scan-specific, and BLE-specific.
- [ ] On Android 12+:
  - [ ] `BLUETOOTH_CONNECT` is required for Bluetooth operations.
  - [ ] `BLUETOOTH_SCAN` is required for scan/discovery.
  - [ ] `BLUETOOTH_ADVERTISE` is required only for BLE HOGP.
- [ ] On Android 13+:
  - [ ] `POST_NOTIFICATIONS` is optional/non-fatal.
- [ ] Preserve older Android behavior already supported by the app.

Acceptance criteria:

- [ ] Optional notification denial no longer blocks app startup.
- [ ] Classic HID startup does not request advertising permission.
- [ ] BLE mode requests advertising permission only when needed.

### Task 4.2 — Update startup permission flow

- [ ] Replace `result.values.all { it }`-style fatal all-or-nothing handling.
- [ ] Check only currently required permissions for current backend startup.
- [ ] If optional notification permission is denied, continue startup and show a non-fatal warning if appropriate.
- [ ] If scan permission is denied, disable/guard scan action rather than blocking unrelated UI.

Acceptance criteria:

- [ ] User can use non-scan parts of the app even if scan permission is denied.
- [ ] User can proceed after denying notification permission.

### Task 4.3 — Update scan permission flow

- [ ] Before scanning, verify/request `BLUETOOTH_SCAN` if required.
- [ ] If denied, show a clear scan-specific message.
- [ ] Do not crash or show a generic fatal dialog.

Acceptance criteria:

- [ ] Scan button gracefully handles missing scan permission.

### Task 4.4 — Update BLE permission flow

- [ ] When enabling BLE HOGP, verify/request `BLUETOOTH_ADVERTISE` if required.
- [ ] If denied, do not switch into BLE mode or show a clear limited-state message.
- [ ] Keep Classic HID available if BLE-specific permission is denied.

Acceptance criteria:

- [ ] Denying advertising permission does not break Classic HID mode.

### Task 4.5 — Add permission tests

- [ ] Test notification permission denial is non-fatal.
- [ ] Test Classic mode does not require advertise permission.
- [ ] Test BLE mode requires advertise permission on Android versions where applicable.
- [ ] Test scan permission is scan-specific.

Acceptance criteria:

- [ ] Permission logic is covered by JVM tests, not only manual testing.

---

## Phase 5 — Align scroll UI/settings with HID descriptor mode

### Task 5.1 — Choose and implement descriptor/scroll policy

Preferred v0.1 policy:

- [ ] Keep SIMPLE descriptor as default compatibility mode.
- [ ] In SIMPLE mode, disable or hide scroll-related settings/controls.
- [ ] In SIMPLE mode, show explanatory text that scrolling requires FULL descriptor mode.
- [ ] In FULL mode, enable scroll behavior according to settings.

Alternative acceptable policy:

- [ ] Make FULL descriptor default if scrolling is considered core v0.1 functionality.
- [ ] Update docs and tests accordingly.

Acceptance criteria:

- [ ] UI no longer exposes enabled scroll controls that send no report.

### Task 5.2 — Update Settings screen copy/state

- [ ] Update `SettingsScreen.kt` to reflect SIMPLE vs FULL descriptor capabilities.
- [ ] Disable or hide `enableHorizontalScroll`, `scrollSpeed`, and related scroll controls when scroll is unavailable.
- [ ] Add short explanatory copy.

Acceptance criteria:

- [ ] User can understand why scroll is unavailable in simplified mode.

### Task 5.3 — Update gesture/mouse screen behavior if needed

- [ ] Ensure two-finger scroll gestures are disabled, ignored with explanation, or only active in FULL mode.
- [ ] Avoid logging or pretending a scroll event was sent when it was intentionally disabled by descriptor mode.

Acceptance criteria:

- [ ] Gesture behavior matches settings UI.

### Task 5.4 — Add descriptor/scroll tests

- [ ] Test SIMPLE mode does not send scroll reports or UI disables scroll.
- [ ] Test FULL mode sends vertical scroll reports.
- [ ] Test FULL mode sends horizontal scroll reports when enabled.
- [ ] Update existing HID report tests if necessary.

Acceptance criteria:

- [ ] Tests encode the chosen v0.1 descriptor/scroll policy.

---

## Phase 6 — Fix landscape-orientation mismatch

### Task 6.1 — Decide v0.1 orientation policy

Preferred:

- [ ] Make v0.1 landscape-only.

Alternative:

- [ ] Support both portrait and landscape and update docs accordingly.

Acceptance criteria:

- [ ] Runtime behavior and docs agree.

### Task 6.2 — Implement selected policy

For landscape-only:

- [ ] Add `android:screenOrientation="landscape"` to `MainActivity` in `AndroidManifest.xml`.
- [ ] Verify there is no conflict with Android 16/API behavior if target SDK has restrictions; document if applicable.

For both orientations:

- [ ] Remove claims that landscape lock is complete.
- [ ] Verify portrait UI is acceptable.

Acceptance criteria:

- [ ] README/PRD and manifest behavior match.

---

## Phase 7 — Make debug logging respect settings

### Task 7.1 — Remove unconditional debug enable calls

- [ ] Find all unconditional `DebugLog.setEnabled(true)` calls.
- [ ] Replace startup behavior with settings-driven enablement.
- [ ] Apply `DebugLog.setEnabled(settings.debugLogging)` after settings load.
- [ ] Apply log level from settings only when appropriate.

Acceptance criteria:

- [ ] Debug logging is not always enabled.

### Task 7.2 — Apply logging setting immediately when changed

- [ ] Update `SettingsViewModel` or settings update flow so toggling debug logging immediately affects `DebugLog`.
- [ ] Ensure disabling logging stops new debug entries.
- [ ] Ensure enabling logging resumes entries.

Acceptance criteria:

- [ ] No app restart is required for the logging toggle to work.

### Task 7.3 — Avoid sensitive logs when disabled

- [ ] Ensure Bluetooth MAC/device identifiers and key preview labels are not appended while debug logging is disabled.
- [ ] Keep any unavoidable startup logs minimal.

Acceptance criteria:

- [ ] Debug log setting provides real privacy control.

### Task 7.4 — Add debug logging tests

- [ ] Test disabled logging does not append new entries.
- [ ] Test enabled logging appends entries at selected level.
- [ ] Test toggling behavior if `DebugLog` supports it.

Acceptance criteria:

- [ ] Tests fail if logging is forced on again.

---

## Phase 8 — Keep Redux state fresh after device-management actions

### Task 8.1 — Update state after setting default device

- [ ] Locate `setDefaultDevice()` service/middleware path.
- [ ] After persisting default address, dispatch `Action.UpdateDefaultDevice(device.address)` or equivalent.
- [ ] If dispatch belongs in middleware rather than service, implement consistently with existing architecture.

Acceptance criteria:

- [ ] Star/default UI updates immediately after setting default.

### Task 8.2 — Update state after clearing default device

- [ ] Locate clear-default path if present.
- [ ] Remove persisted default address.
- [ ] Dispatch `Action.UpdateDefaultDevice(null)` or equivalent.

Acceptance criteria:

- [ ] Star/default UI clears immediately.

### Task 8.3 — Update state after forget/unpair

- [ ] Locate `forgetDevice()` and unpair logic.
- [ ] Clear alias/default state if the forgotten device had them.
- [ ] Disconnect/clear connected state if the forgotten device was connected.
- [ ] Refresh paired-device list.
- [ ] Refresh discovered-device list if necessary.
- [ ] Dispatch all relevant Redux updates.

Acceptance criteria:

- [ ] Forgotten/unpaired device does not remain as connected/default in UI.
- [ ] Paired list refreshes after forget/unpair.

### Task 8.4 — Handle `BOND_NONE` in bond-state receiver

- [ ] Update bond-state receiver to handle `BluetoothDevice.BOND_NONE`.
- [ ] Refresh paired devices on both `BOND_BONDED` and `BOND_NONE`.
- [ ] Clear default/connected state if bond loss applies to those devices.

Acceptance criteria:

- [ ] External unpairing from Android settings is reflected in the app UI.

### Task 8.5 — Add device-state regression tests

- [ ] Test setting default updates store state.
- [ ] Test clearing default updates store state.
- [ ] Test forgetting default clears default state.
- [ ] Test forgetting connected device clears connected state.
- [ ] Test bond none refresh behavior through pure helper or reducer where practical.

Acceptance criteria:

- [ ] Tests prevent stale Redux state regressions.

---

## Phase 9 — Harden `startDiscovery()`

### Task 9.1 — Guard adapter and permission state

- [ ] Check `bluetoothAdapter != null` before scanning.
- [ ] Check adapter is enabled before scanning.
- [ ] Check `BLUETOOTH_SCAN` permission on Android versions where required.
- [ ] If unavailable, dispatch/log a clear scan failure instead of continuing.

Acceptance criteria:

- [ ] Missing adapter, disabled adapter, or missing permission does not crash.

### Task 9.2 — Wrap discovery calls safely

- [ ] Wrap `cancelDiscovery()` in permission checks and `try/catch(SecurityException)`.
- [ ] Wrap `startDiscovery()` in permission checks and `try/catch(SecurityException)`.
- [ ] Capture return value from `startDiscovery()` and handle `false` as a scan-start failure.

Acceptance criteria:

- [ ] `SecurityException` from scan APIs is handled gracefully.
- [ ] Failed scan start produces a useful user-visible or log-visible status.

### Task 9.3 — Add discovery error tests where practical

- [ ] Extract scan-start precondition logic into a pure helper if useful.
- [ ] Test missing permission path.
- [ ] Test disabled adapter path if helper supports it.
- [ ] Test scan-start failure return handling if helper supports it.

Acceptance criteria:

- [ ] Permission-safe scan behavior is covered by tests where feasible.

---

## Phase 10 — Fix snackbar recomposition bug

### Task 10.1 — Remember snackbar host state

- [ ] Locate `SnackbarHostState` creation in `MainScreen()` or equivalent.
- [ ] Replace direct construction with `remember { SnackbarHostState() }`.
- [ ] Ensure imports use Material3 `SnackbarHostState` consistently.

Acceptance criteria:

- [ ] Snackbar state survives recomposition.

### Task 10.2 — Quick UI check

- [ ] Trigger snackbar-producing actions.
- [ ] Navigate/recompose while snackbar is visible.
- [ ] Confirm snackbar does not disappear unexpectedly due to state recreation.

Acceptance criteria:

- [ ] Snackbar behavior is stable enough for v0.1.

---

## Phase 11 — Fix docs/README overclaims

### Task 11.1 — Audit README feature claims

- [ ] Review `README.md` for claims about:
  - [ ] Media keys / Consumer Control.
  - [ ] Landscape lock/support.
  - [ ] BLE HOGP stability.
  - [ ] Scroll support.
  - [ ] Permissions.
  - [ ] Descriptor modes.
- [ ] Remove or qualify claims that are not fully implemented.

Acceptance criteria:

- [ ] README describes actual v0.1 behavior.

### Task 11.2 — Update descriptor/scroll documentation

- [ ] Document SIMPLE descriptor compatibility mode.
- [ ] Document FULL descriptor feature mode.
- [ ] Clearly state whether scroll requires FULL mode.

Acceptance criteria:

- [ ] Users understand why scroll may be disabled.

### Task 11.3 — Update BLE HOGP documentation

- [ ] Clearly mark BLE HOGP as experimental unless verified end-to-end.
- [ ] Document extra permission requirements for BLE mode.
- [ ] Document how to switch between Classic and BLE mode if supported.

Acceptance criteria:

- [ ] Users do not accidentally enable BLE mode expecting stable default behavior.

### Task 11.4 — Update orientation docs

- [ ] If landscape-only is implemented, document it.
- [ ] If both orientations are supported, document that instead.

Acceptance criteria:

- [ ] Docs and manifest are consistent.

---

## Phase 12 — Optional privacy/security hardening before v0.1

These are recommended. Do them if they are low-risk after blockers are fixed.

### Task 12.1 — Make backup policy intentional

- [ ] Review `android:allowBackup`, `backup_rules.xml`, and `data_extraction_rules.xml`.
- [ ] Either set `android:allowBackup="false"` or explicitly exclude Bluetooth identifiers/preferences from backup.
- [ ] Document chosen policy briefly in code comments or docs if helpful.

Acceptance criteria:

- [ ] Bluetooth MAC addresses/aliases/default-device data are not accidentally cloud-backed unless intentionally allowed.

### Task 12.2 — Review `BootReceiver` exported state

- [ ] Inspect whether `BootReceiver` needs to be exported.
- [ ] Set `android:exported="false"` if compatible with boot-completed behavior.
- [ ] If it must remain exported, document why and constrain it where possible.

Acceptance criteria:

- [ ] Receiver exposure is minimized or justified.

### Task 12.3 — Compose/no-actionbar theme cleanup

- [ ] Review app theme parent in `res/values/themes.xml` and `values-night/themes.xml`.
- [ ] Prefer a no-actionbar parent for Compose-first UI if safe.
- [ ] Verify no duplicate action bar appears.

Acceptance criteria:

- [ ] Compose UI owns the app chrome cleanly.

---

## Phase 13 — Optional UI polish before v0.1

Do only after release blockers are fixed.

### Task 13.1 — Fix top app bar icon contrast

- [ ] Review top app bar container color.
- [ ] Use icon tint matching the actual container, such as `onSurfaceVariant`, or explicitly set top app bar colors.
- [ ] Verify light and dark themes.

Acceptance criteria:

- [ ] Top bar icons are readable in light and dark mode.

### Task 13.2 — Clamp key label font sizes

- [ ] Review `KEY_FONT_SCALE` and responsive text sizing.
- [ ] Clamp key label sizes to a legible range, for example 10sp to 16sp unless design requires otherwise.
- [ ] Verify common landscape phone/tablet widths.

Acceptance criteria:

- [ ] Key labels are not tiny on normal devices.

### Task 13.3 — Remove or wire dead key-button code

- [ ] Locate unused `KeyButton()` or other dead UI helpers.
- [ ] Remove dead code or wire it into active screens if intended.
- [ ] Avoid broad UI refactor.

Acceptance criteria:

- [ ] Obvious stale UI code does not remain in the release path.

---

## Phase 14 — Test suite and validation

### Task 14.1 — Run JVM tests

- [ ] Run:

```bash
./gradlew clean test
```

- [ ] Fix any failing tests.
- [ ] Do not suppress failing tests to make the build pass.

Acceptance criteria:

- [ ] JVM tests pass.

### Task 14.2 — Run debug build

- [ ] Run:

```bash
./gradlew assembleDebug
```

- [ ] Fix any compilation or packaging errors.

Acceptance criteria:

- [ ] Debug APK builds successfully.

### Task 14.3 — Run lint

- [ ] Run:

```bash
./gradlew lintDebug
```

- [ ] Review warnings/errors.
- [ ] Fix release-relevant issues.
- [ ] Do not hide meaningful warnings with broad suppressions.

Acceptance criteria:

- [ ] Lint is clean or any remaining warnings are explicitly justified.

### Task 14.4 — Run instrumented tests

- [ ] Run on a real device or emulator:

```bash
./gradlew connectedAndroidTest
```

- [ ] Ensure Bluetooth-hardware-dependent tests are guarded/skipped when the environment lacks Bluetooth support.
- [ ] Fix any non-hardware-related failures.

Acceptance criteria:

- [ ] Instrumented tests pass or hardware-dependent skips are clearly documented.

### Task 14.5 — Manual smoke test on real hardware

Run on a real Android device and host computer:

- [ ] Fresh install app.
- [ ] Launch with default settings.
- [ ] Grant required Bluetooth permissions.
- [ ] Deny notification permission if prompted; verify app still proceeds.
- [ ] Scan for devices.
- [ ] Confirm discovered devices appear in Pairing UI.
- [ ] Pair/connect to host computer.
- [ ] Send normal keyboard keys.
- [ ] Send modifier combos.
- [ ] Use mouse movement and clicks.
- [ ] Verify scroll behavior matches descriptor mode.
- [ ] Set default device; verify UI star updates immediately.
- [ ] Kill/restart app; verify default persists.
- [ ] Forget/unpair device; verify stale connected/default state clears.
- [ ] Toggle debug logging off; verify new detailed logs stop.
- [ ] Toggle debug logging on; verify logs resume.
- [ ] Optional: enable BLE HOGP; verify only BLE backend starts.
- [ ] Optional: disable BLE HOGP; verify only Classic backend starts.

Acceptance criteria:

- [ ] Manual smoke test passes or all remaining issues are documented as known v0.1 limitations.

---

## Phase 15 — Final release notes

### Task 15.1 — Implementation summary

- [ ] Summarize files changed.
- [ ] Summarize release blockers fixed.
- [ ] Summarize tests added/updated.
- [ ] Include command results:
  - [ ] `./gradlew clean test`
  - [ ] `./gradlew assembleDebug`
  - [ ] `./gradlew lintDebug`
  - [ ] `./gradlew connectedAndroidTest`
- [ ] Include manual smoke test results.
- [ ] List remaining known limitations.

Acceptance criteria:

- [ ] The project maintainer can decide whether to tag `v0.1` based on the summary.

### Task 15.2 — Do not tag until complete

- [ ] Confirm all release blockers are fixed.
- [ ] Confirm docs match behavior.
- [ ] Confirm tests/builds pass.
- [ ] Confirm manual smoke test has been run or explicitly deferred with reason.

Acceptance criteria:

- [ ] Safe to tag `v0.1`.
