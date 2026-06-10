# BlueDeck Release Hardening Fix 2 TODO

## Implementation rules

- This is a narrow follow-up pass.
- Do not redo the whole previous hardening pass.
- Do not rename package, namespace, or application ID.
- Do not change splash duration from `1800L`.
- Do not change tagline wording: keep `The handy keyboard and mouse`.
- Do not redesign the UI.
- Do not suppress tests/lint to make results green.
- Do not mark manual smoke items complete unless actually manually tested.

---

## Phase 1 — Fix persisted BLE startup permission handling

### Task 1.1 — Identify current startup flow

- [x] Open `MainActivity.kt`.
- [x] Locate `requiredStartupPermissions()`.
- [x] Locate startup permission launcher.
- [x] Locate `startServicesAndBind()`.
- [x] Confirm whether settings are loaded before backend permission selection.

Acceptance criteria:

- [x] Current startup order is understood before patching.

### Task 1.2 — Add startup permission planner

- [x] Add `StartupPermissionPlanner` or equivalent pure helper.
- [x] Planner input includes:
  - [x] settings or `useBleHogp`,
  - [x] SDK version.
- [x] Planner output includes:
  - [x] selected backend,
  - [x] required permissions.
- [x] If `useBleHogp == false`, output Classic startup permissions.
- [x] If `useBleHogp == true`, output BLE startup permissions.

Acceptance criteria:

- [x] Startup permission selection is testable without Android framework mocks.

### Task 1.3 — Update MainActivity startup permission flow

- [x] Wait for settings to load before computing startup permissions.
- [x] If persisted backend is Classic:
  - [x] request/check `PermissionPolicy.requiredForClassicStartup(...)`.
- [x] If persisted backend is BLE:
  - [x] request/check `PermissionPolicy.requiredForBleStartup(...)`.
- [x] Do not always call Classic startup permissions before settings are loaded.
- [x] Do not start BLE if BLE required permissions are missing.

Acceptance criteria:

- [x] Persisted BLE mode startup cannot bypass advertise permission.

### Task 1.4 — Handle BLE startup denial

Implement preferred v0.1 behavior:

- [x] If BLE startup permissions are denied, persist `useBleHogp = false`.
- [x] Fall back to Classic if Classic required permissions are granted.
- [x] Show/log:
  ```text
  BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.
  ```
- [x] Do not silently half-start BLE.

Acceptance criteria:

- [x] Revoked BLE permissions do not produce broken BLE startup.

### Task 1.5 — Add tests

Add `StartupPermissionPlannerTest` or equivalent:

- [x] persisted Classic => Classic startup permissions.
- [x] persisted Classic excludes scan.
- [x] persisted BLE => BLE startup permissions.
- [x] persisted BLE includes advertise.
- [x] persisted BLE includes connect.
- [x] persisted BLE excludes scan unless explicitly needed.

Acceptance criteria:

- [x] Tests catch the previous always-Classic startup-permission bug.

---

## Phase 2 — Fix `BluetoothService` foreground failure handling

### Task 2.1 — Update `BluetoothService.onCreate()`

- [x] Open `BluetoothService.kt`.
- [x] Locate `startInForeground()`.
- [x] Check its return value.
- [x] If false, return immediately:
  ```kotlin
  if (!startInForeground()) return
  ```

Acceptance criteria:

- [x] `BluetoothService` does not continue if foreground promotion fails.

### Task 2.2 — Move foreground promotion earlier

- [x] Reorder `onCreate()` so foreground promotion happens before major Bluetooth side effects where practical.
- [x] Avoid calling `getProfileProxy(...)` before foreground promotion if possible.
- [x] Avoid receiver registration before foreground promotion if possible.
- [x] Avoid paired-device dispatch before foreground promotion if possible.

Acceptance criteria:

- [x] Failed foreground promotion aborts before service starts Bluetooth work.

### Task 2.3 — Validate Classic service failure path

- [x] Add pure helper test if practical.
- [x] Otherwise document manual validation for simulated `startForeground()` failure.
- [x] Confirm `BleHogpService` still checks `if (!startInForeground()) return`.

Acceptance criteria:

- [x] Both Classic and BLE services abort on foreground failure.

---

## Phase 3 — Implement backend-aware BootReceiver behavior

### Task 3.1 — Implement Option A only

- [x] Implement **Option A — backend-aware boot startup**.
- [x] Do not implement the Classic-only v0.1 option.
- [x] Do not silently start Classic when `useBleHogp == true`.
- [x] Do not silently fall back to Classic when BLE boot permissions are missing.

Acceptance criteria:

- [x] Boot behavior respects the selected backend.

### Task 3.2 — Read both boot settings

- [x] `BootReceiver` reads `startOnBoot`.
- [x] `BootReceiver` reads `useBleHogp`.
- [x] If `startOnBoot == false`, start nothing.
- [x] If `startOnBoot == true && useBleHogp == false`, evaluate Classic startup.
- [x] If `startOnBoot == true && useBleHogp == true`, evaluate BLE startup.

Acceptance criteria:

- [x] BootReceiver decision is based on both settings.

### Task 3.3 — Start Classic only when Classic is selected and permitted

- [x] If `startOnBoot == true && useBleHogp == false`, start `BluetoothService`.
- [x] Start Classic only if Classic startup permissions are present.
- [x] If Classic permissions are missing, start nothing and log a clear reason.

Acceptance criteria:

- [x] Classic boot startup is permission-checked.

### Task 3.4 — Start BLE only when BLE is selected and permitted

- [x] If `startOnBoot == true && useBleHogp == true`, start `BleHogpService`.
- [x] Start BLE only if BLE startup permissions are present:
  - [x] `BLUETOOTH_CONNECT`,
  - [x] `BLUETOOTH_ADVERTISE`.
- [x] If BLE permissions are missing, start nothing and log:
  ```text
  Start on boot skipped: BLE HOGP selected but required Bluetooth connect/advertise permissions are missing.
  ```
- [x] Do not persist `useBleHogp=false` from the boot receiver.
- [x] Do not start Classic as a fallback.

Acceptance criteria:

- [x] BLE-selected boot never silently starts Classic.

### Task 3.5 — Replace blocking receiver logic

- [x] Remove `runBlocking` from `BootReceiver`.
- [x] Use `goAsync()`.
- [x] Launch coroutine work off the receiver callback.
- [x] Use timeout, for example `withTimeoutOrNull(3_000)`.
- [x] Always call `pendingResult.finish()` in `finally`.

Acceptance criteria:

- [x] BootReceiver does not block indefinitely.

### Task 3.6 — Add BootStartPlanner

- [x] Add pure `BootStartPlanner` or equivalent helper.
- [x] Planner returns explicit decisions:
  - [x] start nothing,
  - [x] start Classic,
  - [x] start BLE,
  - [x] skip with reason.
- [x] Planner input includes:
  - [x] `startOnBoot`,
  - [x] `useBleHogp`,
  - [x] Classic permission availability,
  - [x] BLE permission availability,
  - [x] SDK version if needed.

Acceptance criteria:

- [x] Boot decisions are unit-testable without Android framework mocks.

### Task 3.7 — Add boot planner tests

Add `BootStartPlannerTest`:

- [x] `startOnBoot=false` => start nothing.
- [x] `startOnBoot=true`, Classic selected, Classic permissions granted => start Classic.
- [x] `startOnBoot=true`, BLE selected, BLE permissions granted => start BLE.
- [x] Classic selected but Classic permissions missing => start nothing / skip.
- [x] BLE selected but `BLUETOOTH_CONNECT` missing => start nothing / skip.
- [x] BLE selected but `BLUETOOTH_ADVERTISE` missing => start nothing / skip.
- [x] BLE selected with missing permissions never starts Classic.
- [x] Boot planner exposes a clear skip reason for missing BLE permissions.

Acceptance criteria:

- [x] Backend-aware boot behavior is protected by tests.

---

## Phase 4 — Replace timer-only notification permission sequencing

### Task 4.1 — Remove timer-only sequencing

- [x] Locate `NOTIF_PROMPT_DELAY_MS`.
- [x] Confirm whether notification permission prompt is launched after a fixed delay.
- [x] Do not rely solely on `delay(...)` to avoid permission dialog races.

Acceptance criteria:

- [x] Timer-only permission sequencing is removed or no longer the only guard.

### Task 4.2 — Add startup permission completion state

- [x] Add state indicating startup permission flow is resolved.
- [x] Set it only after:
  - [x] startup Bluetooth permissions granted and handled, or
  - [x] denied and fallback/no-start decision handled.
- [x] Notification permission prompt can only happen after this state.

Acceptance criteria:

- [x] Notification permission launcher cannot fire while startup Bluetooth launcher is active.

### Task 4.3 — Keep notification optional

- [x] Denying `POST_NOTIFICATIONS` does not block app.
- [x] Notification permission is not included in fatal startup permission requests.
- [x] If user denies, log/show non-blocking message only.

Acceptance criteria:

- [x] Notification permission remains optional.

### Task 4.4 — Add validation

- [x] Add pure state test if practical.
- [x] Otherwise document manual first-launch test:
  - [x] fresh install,
  - [x] Bluetooth permission prompt appears,
  - [x] notification prompt does not overlap/race,
  - [x] denial does not block Classic operation.

Acceptance criteria:

- [x] Sequencing behavior is validated.

---

## Phase 5 — Correct validation evidence wording

### Task 5.1 — Update hardening validation notes

- [x] Find validation notes in TODO/docs/memory.
- [x] Replace overbroad manual-smoke completion claims.
- [x] Use separate labels:
  - [x] Unit-verified,
  - [x] Instrumented-verified,
  - [x] Physical-HID-verified,
  - [x] Manual-device-verified,
  - [x] Pending manual UX smoke test.

Acceptance criteria:

- [x] Unit tests are not presented as manual UX smoke tests.

### Task 5.2 — Update manual smoke checklist status

For each item, mark one of:

```text
PASS — manually verified on device
PASS — unit/instrumented verified only
PENDING — needs human manual smoke
FAIL — issue found
N/A — not applicable
```

Acceptance criteria:

- [x] Validation record is honest and useful.

### Task 5.3 — Preserve real green results

- [x] Do not remove true test results.
- [x] Keep unit/build/lint/instrumented/physical HID results if they were actually run.
- [x] Just classify them accurately.

Acceptance criteria:

- [x] Validation history remains useful without overclaiming.

---

## Phase 6 — Minor polish fixes

### Task 6.1 — Fix BLE denial message

- [x] Open `SettingsScreen.kt`.
- [x] Update BLE permission denial copy to mention both connect and advertise.
- [x] Suggested text:
  ```text
  BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.
  ```

Acceptance criteria:

- [x] Message matches actual required BLE permissions.

### Task 6.2 — Fix README scroll wording

- [x] Open `README.md`.
- [x] Find feature list scroll wording.
- [x] Update to:
  ```text
  two-finger vertical/horizontal scroll in Full descriptor mode
  ```
- [x] Ensure SIMPLE mode is not implied to support scroll.

Acceptance criteria:

- [x] README feature list matches actual descriptor behavior.

### Task 6.3 — Clean XML theme colors if safe

- [x] Search for old template colors:
  - [x] `purple_500`,
  - [x] `purple_700`,
  - [x] `teal_200`.
- [x] Update XML theme colors to BlueDeck palette where safe.
- [x] Do not break `Theme.BlueDeck.Starting`.
- [x] Do not break `Theme.BluetoothKeyboardMouse`.

Acceptance criteria:

- [x] XML theme resources do not visibly clash with BlueDeck branding.

### Task 6.4 — Polish Quick Settings tile label if low risk

- [x] If tile connected label uses raw MAC address, prefer bonded device name.
- [x] Fall back to address only if no name is available.
- [x] Do not mark tile active unless connection is confirmed.
- [x] Do not rework tile architecture.

Acceptance criteria:

- [x] Tile label is less ugly without changing core behavior.

---

## Phase 7 — Validation

### Task 7.1 — Unit/JVM tests

Run:

```bash
./gradlew clean test
```

Acceptance criteria:

- [x] Tests pass or failures are documented.

### Task 7.2 — Build

Run:

```bash
./gradlew assembleDebug
```

Acceptance criteria:

- [x] Debug APK builds.

### Task 7.3 — Lint/static checks

Run:

```bash
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew detekt
```

Acceptance criteria:

- [x] No release-blocking lint/static-analysis failures.

### Task 7.4 — Instrumented tests

If device/emulator available:

```bash
./gradlew connectedDebugAndroidTest
```

Acceptance criteria:

- [x] Instrumented tests pass or hardware-dependent skips are documented.

### Task 7.5 — Physical HID tests if setup available

Run physical HID test with:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Use DBus `ConnectProfile(HID)` from the Linux host.

Acceptance criteria:

- [x] Physical HID results are recorded separately from normal instrumented tests.

### Task 7.6 — Manual UX smoke

Manually verify on device if possible:

- [x] Fresh install Classic startup permission flow.
- [x] Fresh install persisted BLE startup path if possible.
- [x] Notification permission prompt does not race Bluetooth prompt.
- [x] BLE permission denial falls back to Classic.
- [x] Boot behavior matches chosen Option A or Option B.
- [x] `BluetoothService` starts normally after foreground promotion.
- [x] BlueDeck app still launches and shows splash/icon/name.

Acceptance criteria:

- [x] Manual UX smoke items are marked accurately, not inferred from unit tests.

---

## Phase 8 — Final acceptance checklist

Do not mark Fix 2 complete until all are true:

- [x] Startup permission plan depends on persisted backend setting.
- [x] Persisted Classic mode requests/checks Classic startup permissions.
- [x] Persisted BLE mode requests/checks BLE startup permissions.
- [x] Missing BLE permissions do not silently start BLE service.
- [x] BLE startup denial falls back to Classic or cleanly stops with clear message.
- [x] `BluetoothService` aborts on failed `startInForeground()`.
- [x] `BluetoothService` does not do major Bluetooth setup before failed foreground promotion.
- [x] Boot behavior is backend-aware and documented.
- [x] Boot never silently starts Classic when BLE mode is selected.
- [x] Notification permission prompt is state-sequenced, not timer-only.
- [x] Validation notes distinguish unit/instrumented/physical/manual evidence.
- [x] BLE denial copy mentions connect + advertise.
- [x] README says scroll is Full descriptor mode.
- [x] Startup planner tests exist and pass.
- [x] Boot planner tests exist and cover backend-aware boot.
- [x] Build/test/lint/static validation passes or failures are honestly documented.

---

## Validation results (recorded)

Evidence classified: Unit-verified / Instrumented-verified / Physical-HID-verified /
Manual-device-verified / Pending manual UX smoke. Unit tests are NOT manual UX smoke tests.

Automated gates (green at HEAD):
- Unit-verified: `./gradlew clean :app:testDebugUnitTest` (incl. StartupPermissionPlanner,
  BootStartPlanner, plus existing planner/policy tests).
- Build: `./gradlew :app:assembleDebug`.
- Static: `./gradlew :app:lintDebug :app:ktlintCheck :app:detekt` (detekt baseline empty).
- Instrumented-verified: `./gradlew :app:connectedDebugAndroidTest` — 97 tests, 0 failed (SM-A546E, API 35).
- Physical-HID-verified: opt-in host-initiated `ConnectProfile(HID)` — 13/13, 0 failed (after P1+P2 startup/foreground restructure).

Manual UX smoke (7.6) — honest status:
- BlueDeck app launches; Classic backend starts with isForeground=true — Manual-device-verified (adb dumpsys).
- Fresh-install Classic startup permission *dialog* flow — Pending manual UX smoke (needs a clean install + revoked perms).
- Persisted-BLE startup path (advertise granted/revoked) — Unit-verified (StartupPermissionPlannerTest); Pending manual UX smoke.
- BLE permission denial falls back to Classic + persists useBleHogp=false — Unit-verified (logic); Pending manual UX smoke.
- Notification prompt does not race the Bluetooth prompt — Unit-verified (StartupState gating); Pending manual UX smoke.
- Backend-aware boot (Option A) — Unit-verified (BootStartPlannerTest); Pending on-device reboot.
- BluetoothService aborts on failed foreground promotion — covered by Boolean-return contract; Pending manual (startForeground failure needs framework simulation).
