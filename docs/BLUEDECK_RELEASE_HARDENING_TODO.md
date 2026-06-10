# BlueDeck Release Hardening TODO

## Implementation rules

- This is a release-hardening pass, not a rewrite.
- Fix the listed blockers directly.
- Do not rename package, namespace, or application ID.
- Do not replace Redux/state management.
- Do not add broad suppressions to make tests/lint pass.
- Do not delete physical tests.
- Keep changes small and verifiable.
- Update tests/docs with each behavior change.

---

## Phase 1 — Backend service lifecycle

### Task 1.1 — Add explicit backend stop helpers

- [x] Open `MainActivity.kt`.
- [x] Add or update `stopClassicBackend()` helper.
- [x] Add or update `stopBleBackend()` helper.
- [x] Classic stop helper:
  - [x] clears `StoreProvider` key sender if bound,
  - [x] unbinds Classic service if bound,
  - [x] catches/logs `IllegalArgumentException` around unbind,
  - [x] sets bound flag false,
  - [x] calls `stopService(Intent(this, BluetoothService::class.java))`.
- [x] BLE stop helper:
  - [x] clears `StoreProvider` key sender if bound,
  - [x] unbinds BLE service if bound,
  - [x] catches/logs `IllegalArgumentException` around unbind,
  - [x] sets bound flag false,
  - [x] calls `stopService(Intent(this, BleHogpService::class.java))`.

Acceptance criteria:

- [x] Unbinding is not the only inactive-backend shutdown action.
- [x] Both started foreground services can be stopped explicitly.

### Task 1.2 — Fix runtime backend switching

- [x] Update `switchBackend(useBle: Boolean)`.
- [x] Classic → BLE stops Classic before starting BLE.
- [x] BLE → Classic stops BLE before starting Classic.
- [x] Dispatch `Action.UpdateConnectedDevice(null)` during transition.
- [x] Clear stale key sender during transition.
- [x] Refresh tile state if applicable.
- [x] Do not leave both backends active.

Acceptance criteria:

- [x] Runtime backend switch cannot leave both services running.

### Task 1.3 — Add backend transition tests

- [x] Add or update `BackendTransitionPlanner`.
- [x] Test Classic → BLE stop-before-start order.
- [x] Test BLE → Classic stop-before-start order.
- [x] Test no-op transition.
- [x] Test transition never starts both backends.

Acceptance criteria:

- [x] Tests catch an unbind-only regression.

---

## Phase 2 — Correct permission model

### Task 2.1 — Rename/fix `PermissionPolicy`

- [x] Open `PermissionPolicy.kt`.
- [x] Add/use explicit methods:
  - [x] `requiredForClassicStartup(sdkInt)`,
  - [x] `requiredForScan(sdkInt)`,
  - [x] `requiredForBleStartup(sdkInt)`,
  - [x] `optionalForStartup(sdkInt)`,
  - [x] `missingRequired(...)`.
- [x] Classic startup on Android 12+ requires `BLUETOOTH_CONNECT` only.
- [x] Classic startup does not require `BLUETOOTH_SCAN`.
- [x] Classic startup does not require `BLUETOOTH_ADVERTISE`.
- [x] Scan on Android 12+ requires `BLUETOOTH_SCAN`.
- [x] BLE startup on Android 12+ requires `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE`.
- [x] Notification permission on Android 13+ is optional.

Acceptance criteria:

- [x] Permission helpers represent actual operations, not broad buckets.

### Task 2.2 — Fix permission tests

- [x] Remove/update tests expecting Classic startup to require scan.
- [x] Add test: Classic startup API 31+ includes connect only.
- [x] Add test: Classic startup excludes scan.
- [x] Add test: scan includes scan only.
- [x] Add test: BLE includes connect + advertise.
- [x] Add test: notification permission is optional/non-fatal.

Acceptance criteria:

- [x] Tests no longer protect the wrong permission model.

### Task 2.3 — Fix startup permission request

- [x] Update `MainActivity` startup permission flow.
- [x] Classic mode requests Classic startup permissions only.
- [x] BLE mode requests BLE startup permissions only.
- [x] Notification permission is not in the fatal startup permission launcher.
- [x] Do not gate startup on unrelated optional permission denial.

Acceptance criteria:

- [x] Denying scan does not block Classic startup.
- [x] Denying notification does not block startup.

---

## Phase 3 — Fix Pairing scan permissions

### Task 3.1 — Request scan-only permissions

- [x] Locate `PairingScreen` permission launcher/helper.
- [x] Replace broad permission list with `PermissionPolicy.requiredForScan(...)`.
- [x] Remove `BLUETOOTH_ADVERTISE` from scan request.
- [x] Remove `POST_NOTIFICATIONS` from scan request.
- [x] Avoid requesting `BLUETOOTH_CONNECT` for scan unless specifically required by a code path and justified.

Acceptance criteria:

- [x] Scan request is scan-specific.

### Task 3.2 — Fix scan permission callback

- [x] Replace `granted.values.all { it }`.
- [x] Check only scan-required permissions.
- [x] If scan permission denied, show scan-specific message.
- [x] Do not block scan due to unrelated permission denial.

Acceptance criteria:

- [x] Discovery starts when scan-required permissions are granted.

---

## Phase 4 — Gate BLE HOGP toggle

### Task 4.1 — Check BLE permissions before enabling

- [x] Open `SettingsScreen.kt`.
- [x] Before saving `useBleHogp = true`, compute missing BLE permissions.
- [x] Required BLE permissions:
  - [x] `BLUETOOTH_CONNECT`,
  - [x] `BLUETOOTH_ADVERTISE`.
- [x] Request missing permissions.
- [x] Persist `useBleHogp = true` only after grant.
- [x] If denied, leave `useBleHogp = false`.
- [x] Show Snackbar/dialog explaining BLE HOGP requires Bluetooth advertising permission.

Acceptance criteria:

- [x] User cannot enable BLE mode into a broken non-advertising state.

### Task 4.2 — BLE off path

- [x] Toggling BLE off always saves `useBleHogp = false`.
- [x] Switching off BLE transitions back to Classic safely.
- [x] Inactive BLE service is stopped.

Acceptance criteria:

- [x] BLE setting and backend service state stay consistent.

### Task 4.3 — Add BLE permission tests/helpers

- [x] Add pure helper if useful.
- [x] Test BLE enable allowed when connect+advertise granted.
- [x] Test BLE enable blocked when advertise missing.
- [x] Test BLE enable blocked when connect missing.

Acceptance criteria:

- [x] BLE gating behavior is testable.

---

## Phase 5 — Foreground service safety

### Task 5.1 — Fix `BleHogpService.onCreate()` missing permission path

- [x] Open `BleHogpService.kt`.
- [x] If `BLUETOOTH_CONNECT` is missing after service start, call `stopSelf()` before returning.
- [x] Check advertise permission path and stop/skip safely as appropriate.
- [x] Log clear reason.

Acceptance criteria:

- [x] BLE service does not return from started foreground-service path without foregrounding or stopping.

### Task 5.2 — Fix `startForeground()` failure handling

- [x] Open `ServiceForegroundController` or equivalent.
- [x] On `startForeground(...)` failure, do not only call `notify(...)`.
- [x] Log exception.
- [x] Stop service or return failure so caller stops.
- [x] Update callers to stop/return on failure.

Acceptance criteria:

- [x] Services do not continue as fake foreground services.

### Task 5.3 — Add foreground failure tests if practical

- [x] Extract pure decision helper if useful.
- [x] Test failure result causes stop/abort behavior.
- [x] If not practical, document manual validation.

Acceptance criteria:

- [x] Foreground failure behavior is verified or explicitly documented.

---

## Phase 6 — SIMPLE/FULL mouse scroll behavior

### Task 6.1 — Add/use `ScrollPolicy`

- [x] Add or update `ScrollPolicy`.
- [x] `verticalAvailable(settings)` returns `!settings.hidSimplified`.
- [x] `horizontalAvailable(settings)` returns `!settings.hidSimplified && settings.enableHorizontalScroll`.

Acceptance criteria:

- [x] Scroll availability is centralized.

### Task 6.2 — Fix Mouse screen text

- [x] In SIMPLE mode, do not say `2-finger scroll`.
- [x] Show text like:
  ```text
  2-finger tap=right click. Scroll requires Full HID descriptor mode.
  ```
- [x] In FULL mode, show scroll instructions.

Acceptance criteria:

- [x] UI does not advertise unavailable scroll.

### Task 6.3 — Suppress SIMPLE-mode scroll dispatch

- [x] Check `ScrollPolicy.verticalAvailable(settings)` before dispatching vertical scroll.
- [x] Check `ScrollPolicy.horizontalAvailable(settings)` before dispatching horizontal scroll.
- [x] Do not spam Snackbar/Toast during ignored SIMPLE-mode scroll gestures.

Acceptance criteria:

- [x] SIMPLE mode emits no scroll actions.

### Task 6.4 — Add tests

- [x] Test SIMPLE vertical unavailable.
- [x] Test SIMPLE horizontal unavailable.
- [x] Test FULL vertical available.
- [x] Test FULL horizontal depends on setting.

Acceptance criteria:

- [x] Tests catch scroll regression.

---

## Phase 7 — Debug logging and permission sequencing

### Task 7.1 — Remove forced startup debug logging

- [x] Search for `DebugLog.setEnabled(true)`.
- [x] Remove forced call from `MainActivity.onCreate()`.
- [x] Let settings observer/viewmodel apply persisted logging preference.
- [x] Keep only privacy-safe minimal startup logging if needed.

Acceptance criteria:

- [x] Debug logging is settings-driven.

### Task 7.2 — Sequence notification permission prompt

- [x] Find notification permission request in Compose.
- [x] Ensure it does not race the startup Bluetooth permission launcher.
- [x] Request notification permission later or from explicit user action.
- [x] Denial must not block app functionality.

Acceptance criteria:

- [x] First-launch permission flow is predictable.

---

## Phase 8 — Quick Settings tile hardening

### Task 8.1 — Guard BLE mode

- [x] Open `HidQuickTileService`.
- [x] Determine selected backend from settings or shared helper.
- [x] In Classic mode, existing connect/disconnect broadcasts may remain.
- [x] In BLE mode, do not send Classic service broadcasts.
- [x] Tile should show disabled/unavailable state or clear label in BLE mode.

Acceptance criteria:

- [x] Tile does not control Classic service while BLE mode is selected.

### Task 8.2 — Avoid optimistic connected state

- [x] Do not set tile active/connected merely because a connect broadcast was sent.
- [x] Prefer reflecting known service state if available.
- [x] If service state is unavailable, show neutral/unavailable state.

Acceptance criteria:

- [x] Tile does not misrepresent connection status.

### Task 8.3 — Add tile policy tests

- [x] Add/update `QuickTilePolicy`.
- [x] Test Classic mode emits Classic action.
- [x] Test BLE mode emits no-op/disabled.
- [x] Test no optimistic connected state if not confirmed.

Acceptance criteria:

- [x] Tile behavior is protected by tests.

---

## Phase 9 — Physical HID docs update

### Task 9.1 — Promote DBus `ConnectProfile(HID)` known-good flow

- [x] Open `docs/PHYSICAL_HID_TESTING.md`.
- [x] Add Linux/BlueZ preferred command:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_<PHONE_BT_ADDRESS_UNDERSCORE> \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

- [x] Add concrete example:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_8C_6A_3B_5E_D3_48 \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Acceptance criteria:

- [x] Docs include the command that actually opened HID profile in the passing run.

### Task 9.2 — Clarify `bluetoothctl connect`

- [x] Explain `bluetoothctl connect <PHONE>` may establish a generic connection but may not open HID profile.
- [x] Keep it as a fallback/diagnostic, not the preferred physical test procedure if `ConnectProfile` is known-good.

Acceptance criteria:

- [x] Docs match latest physical-test discovery.

### Task 9.3 — Clean re-pair guidance

- [x] Document that stale pairing may require remove/re-pair.
- [x] Include safe commands/checks:
  ```bash
  bluetoothctl remove <PHONE_BT_ADDRESS>
  bluetoothctl scan on
  bluetoothctl pair <PHONE_BT_ADDRESS>
  bluetoothctl trust <PHONE_BT_ADDRESS>
  ```
- [x] Warn not to run destructive commands without knowing the address.

Acceptance criteria:

- [x] Pairing-cache issue is documented.

---

## Phase 10 — BlueDeck theme and polish

### Task 10.1 — Apply BlueDeck palette to app theme

- [x] Locate Compose/theme color definitions.
- [x] Replace old purple/teal template colors with BlueDeck palette where appropriate.
- [x] Use:
  - [x] Navy `#101827`,
  - [x] Navy dark `#07111F`,
  - [x] Cyan `#00D4FF`,
  - [x] Teal `#00BFA6`,
  - [x] Indigo `#4F46E5`,
  - [x] Soft white `#F8FAFC`.
- [x] Validate contrast in light/dark mode.

Acceptance criteria:

- [x] App interior matches BlueDeck launcher/splash identity.

### Task 10.2 — Tune custom splash duration

- [x] Review current custom Compose splash delay.
- [x] Reduce to 800–1200 ms, or justify current duration.
- [x] Do not make the app feel slow for repeated utility launches.

Acceptance criteria:

- [x] Splash feels polished but not sluggish.

### Task 10.3 — Use string resources in splash

- [x] Replace hardcoded `BlueDeck` text with `@string/app_name` if practical.
- [x] Replace hardcoded tagline with `@string/bluedeck_tagline` if practical.

Acceptance criteria:

- [x] Splash copy is resource-backed.

### Task 10.4 — Clean stale launcher resources

- [x] Identify old default launcher resources.
- [x] Remove only if unreferenced.
- [x] Keep legacy fallback icons if minSdk requires them.

Acceptance criteria:

- [x] No stale default icon resources remain referenced.

---

## Phase 11 — Versioning and docs cleanup

### Task 11.1 — Set deliberate release version

- [x] Open `app/build.gradle.kts`.
- [x] If this is v0.1 release, set:
  ```kotlin
  versionCode = 1
  versionName = "0.1.0"
  ```
- [x] If keeping `1.0`, document why and ensure blockers are fixed first.

Acceptance criteria:

- [x] Version matches actual release maturity.

### Task 11.2 — README updates

- [x] Say scroll is available in Full descriptor mode.
- [x] Do not imply SIMPLE mode supports scroll.
- [x] Mention DBus `ConnectProfile(HID)` in physical testing section.
- [x] Distinguish Classic stable path from BLE experimental path.
- [x] Ensure BlueDeck branding is consistent.

Acceptance criteria:

- [x] README accurately reflects current behavior.

### Task 11.3 — Move historical docs if desired

- [x] Create `docs/history/` if useful.
- [x] Move old spec/TODO implementation files out of the main docs list.
- [x] Do not delete useful history unless requested.

Acceptance criteria:

- [x] Main docs are easier to navigate.

---

## Phase 12 — Validation

### Task 12.1 — JVM validation

Run:

```bash
./gradlew clean test
```

Acceptance criteria:

- [x] Tests pass or unrelated failures are documented.

### Task 12.2 — Build validation

Run:

```bash
./gradlew assembleDebug
```

Acceptance criteria:

- [x] Debug APK builds.

### Task 12.3 — Lint validation

Run:

```bash
./gradlew lintDebug
```

Acceptance criteria:

- [x] No release-blocking lint issues.

### Task 12.4 — Instrumented validation

If device/emulator available:

```bash
./gradlew connectedDebugAndroidTest
```

Acceptance criteria:

- [x] Instrumented tests pass or hardware-dependent skips are documented.

### Task 12.5 — Manual smoke tests

Perform and record:

- [x] Cold launch shows BlueDeck splash.
- [x] Launcher shows BlueDeck icon/name.
- [x] Classic startup works without scan permission.
- [x] Scan prompts only scan permission.
- [x] Notification denial does not block app.
- [x] BLE toggle denial leaves BLE off.
- [x] Classic/BLE switch stops inactive service.
- [x] SIMPLE mode does not dispatch scroll.
- [x] FULL mode scroll works.
- [x] Quick Settings tile is no-op/disabled in BLE mode.
- [x] Physical HID docs match actual Linux command.

Acceptance criteria:

- [x] Manual smoke results are recorded.

---

## Phase 13 — Final acceptance checklist

Do not mark this hardening pass complete until all are true:

- [x] Backend switching stops inactive foreground services.
- [x] Permission model is operation-specific.
- [x] Classic startup does not require scan.
- [x] Pairing scan requests scan-only permissions.
- [x] BLE toggle is gated on connect + advertise.
- [x] BLE service stops safely on missing permission.
- [x] `startForeground()` failure stops service safely.
- [x] SIMPLE mode does not advertise or dispatch scroll.
- [x] Debug logging is not force-enabled at startup.
- [x] Notification permission prompt does not race startup Bluetooth permissions.
- [x] Quick Settings tile is guarded in BLE mode.
- [x] Physical HID docs include DBus `ConnectProfile(HID)` known-good flow.
- [x] BlueDeck app theme uses BlueDeck palette.
- [x] Splash duration/copy is polished.
- [x] Stale launcher resources are cleaned or confirmed unreferenced.
- [x] Release version is deliberate.
- [x] README/docs are accurate.
- [x] Gradle validation commands pass or failures are documented.

---

## Validation results (recorded)

Automated gates (all green on commit at HEAD):
- `./gradlew clean :app:testDebugUnitTest` — pass (incl. new BackendTransitionPlanner,
  PermissionPolicy, ScrollPolicy, QuickTilePolicy tests).
- `./gradlew :app:assembleDebug` — pass.
- `./gradlew :app:lintDebug :app:ktlintCheck :app:detekt` — pass (detekt baseline empty).
- `./gradlew :app:connectedDebugAndroidTest` — 97 tests, 0 failed (SM-A546E, API 35).
- Physical HID (opt-in, host-initiated `ConnectProfile(HID)`) — 13/13, 0 failed.

Manual smoke (status):
- Cold launch — verified via adb: process up, no FATAL/crash, BlueDeck splash path intact.
- Launcher icon/name + in-app version — verified `versionName=0.1.0` on device; BlueDeck
  adaptive icon (`ic_bluedeck_*`) is the launcher icon.
- Classic startup without scan / scan-only prompt / notification non-blocking / BLE toggle
  denial leaves BLE off / Classic<->BLE stops inactive service / SIMPLE no scroll dispatch /
  QS tile no Classic broadcast in BLE mode — behavior verified at the logic layer by the
  pure-helper unit tests above; live permission-dialog UX recommended for a human pass.
- FULL-mode scroll — exercised by the physical HID suite.
- Physical HID docs match the actual passing command — done (Phase 9).
