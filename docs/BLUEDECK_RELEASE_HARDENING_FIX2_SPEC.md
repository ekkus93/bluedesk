# BlueDeck Release Hardening Fix 2 Specification

## 1. Purpose

This specification defines the follow-up hardening pass after the first `BLUEDECK_RELEASE_HARDENING_TODO` implementation.

Claude Code made substantial progress in the first hardening pass. Most of the original release-hardening work should **not** be redone. This Fix 2 pass is intentionally narrow and should close the remaining gaps found in review.

The remaining release blockers are:

1. Persisted BLE mode startup still does not request/check BLE startup permissions.
2. `BluetoothService` does not correctly handle `startInForeground()` failure.
3. `BootReceiver` remains Classic-only and uses blocking startup logic.
4. Notification permission prompt sequencing is still timer-based, not state-based.
5. Validation/TODO wording overclaims manual smoke coverage.
6. Minor polish issues remain in BLE denial copy, README scroll wording, XML theme color consistency, and Quick Settings tile label behavior.

## 2. Scope

### 2.1 In scope

This Fix 2 pass must address:

- startup permission selection after settings are loaded,
- BLE persisted-mode permission gating,
- `BluetoothService` foreground promotion failure handling,
- boot-start behavior, either backend-aware or explicitly Classic-only,
- notification permission sequencing based on startup permission completion,
- validation wording and evidence classification,
- BLE denial copy,
- README scroll wording,
- optional XML theme color cleanup,
- optional Quick Settings tile connected-label polish,
- tests for startup-permission planning.

### 2.2 Out of scope

Do not redo the entire previous hardening pass.

Do not:

- rewrite Bluetooth services,
- rename package/application ID,
- replace Redux/state management,
- redesign the UI,
- rework HID descriptors,
- redo physical HID test harness architecture,
- remove existing passing tests,
- add broad suppressions,
- change splash duration from `1800L`,
- change tagline wording from `The handy keyboard and mouse`.

## 3. Current state summary

The first hardening pass appears to have correctly implemented:

- backend stop helpers,
- most backend switching behavior,
- operation-specific `PermissionPolicy`,
- scan-only Pairing permissions,
- BLE toggle permission gating,
- SIMPLE/FULL scroll policy,
- DebugLog startup force-enable removal,
- Quick Settings BLE guard,
- physical HID `ConnectProfile(HID)` documentation,
- BlueDeck Compose theme palette,
- `versionName = "0.1.0"`.

Do not churn these areas unless needed to fix the specific gaps below.

## 4. Fix persisted BLE startup permission handling

### 4.1 Problem

The current `MainActivity.requiredStartupPermissions()` still always uses Classic startup permissions:

```kotlin
private fun requiredStartupPermissions(): Array<String> =
    PermissionPolicy.requiredForClassicStartup(android.os.Build.VERSION.SDK_INT).toTypedArray()
```

That is correct only when the selected backend is Classic.

If `useBleHogp == true` was persisted, startup must use:

```kotlin
PermissionPolicy.requiredForBleStartup(Build.VERSION.SDK_INT)
```

Otherwise, if Android permissions were later revoked, the app can attempt to start `BleHogpService` without first requesting/checking `BLUETOOTH_ADVERTISE`.

### 4.2 Required behavior

Startup must load settings before deciding required startup permissions.

If persisted settings say:

```text
useBleHogp = false
```

then required startup permissions are:

```text
PermissionPolicy.requiredForClassicStartup(...)
```

If persisted settings say:

```text
useBleHogp = true
```

then required startup permissions are:

```text
PermissionPolicy.requiredForBleStartup(...)
```

If required BLE permissions are missing:

- request them,
- only start BLE service if they are granted,
- if denied, either:
  - stay on Classic and persist `useBleHogp = false`, or
  - show an error and do not start either backend.

Preferred behavior for v0.1:

```text
If persisted BLE mode cannot start due to missing BLE permissions, fall back to Classic, persist useBleHogp=false, and show/log a clear message.
```

### 4.3 Suggested implementation

Replace the fixed `requiredStartupPermissions()` approach with a startup planner.

Example pure helper:

```kotlin
enum class BackendMode {
    CLASSIC,
    BLE_HOGP,
}

data class StartupPermissionPlan(
    val backend: BackendMode,
    val requiredPermissions: List<String>,
)

object StartupPermissionPlanner {
    fun plan(settings: Settings, sdkInt: Int): StartupPermissionPlan {
        return if (settings.useBleHogp) {
            StartupPermissionPlan(
                backend = BackendMode.BLE_HOGP,
                requiredPermissions = PermissionPolicy.requiredForBleStartup(sdkInt),
            )
        } else {
            StartupPermissionPlan(
                backend = BackendMode.CLASSIC,
                requiredPermissions = PermissionPolicy.requiredForClassicStartup(sdkInt),
            )
        }
    }
}
```

`MainActivity` should:

1. wait for settings to load,
2. compute `StartupPermissionPlan`,
3. request missing permissions,
4. start the planned backend only if required permissions are granted,
5. handle BLE denial by falling back to Classic or stopping startup with clear UI/logging.

### 4.4 Acceptance criteria

This fix is complete when:

- startup permission selection depends on persisted `useBleHogp`,
- Classic startup still does not require scan,
- BLE startup requests/checks connect + advertise,
- revoked advertise permission does not result in silent broken BLE startup,
- tests cover persisted Classic and persisted BLE startup permission plans.

## 5. Fix `BluetoothService.startInForeground()` handling

### 5.1 Problem

`ServiceForegroundController.startInForeground()` now returns success/failure and stops service on failure. That part is good.

But `BluetoothService.onCreate()` currently calls `startInForeground()` and ignores the return value.

It also performs Bluetooth side effects before foreground promotion.

### 5.2 Required behavior

`BluetoothService.onCreate()` must abort if foreground promotion fails:

```kotlin
if (!startInForeground()) return
```

Preferably, foreground promotion should happen before:

- `getProfileProxy(...)`,
- receiver registration,
- updating paired devices,
- long-running service setup.

### 5.3 Required ordering

Recommended `BluetoothService.onCreate()` order:

1. `super.onCreate()`
2. create notification channel / basic setup if needed
3. attempt `startInForeground()`
4. if false, return immediately
5. initialize Bluetooth adapter/profile proxy
6. register receivers
7. dispatch paired devices
8. continue service setup

Exact ordering can vary, but the service must not continue after failed foreground promotion.

### 5.4 Acceptance criteria

This fix is complete when:

- `BluetoothService` checks `startInForeground()` result,
- failed foreground promotion aborts `BluetoothService.onCreate()`,
- `BluetoothService` does not continue as a fake foreground service,
- tests or documented manual validation cover this behavior.

## 6. BootReceiver behavior

### 6.1 Problem

`BootReceiver` still reads only `startOnBoot` and always starts Classic `BluetoothService`.

It ignores persisted `useBleHogp`.

It also uses `runBlocking` inside `BroadcastReceiver.onReceive`.

### 6.2 Required decision: Option A only

Implement **Option A — backend-aware boot**.

Do **not** implement the Classic-only fallback option.

Boot startup must respect the selected backend. If the user selected BLE HOGP mode, boot must not silently start Classic behind their back.

### 6.3 Required behavior

On `Intent.ACTION_BOOT_COMPLETED`, `BootReceiver` must read:

```text
startOnBoot
useBleHogp
```

If:

```text
startOnBoot == false
```

then start nothing.

If:

```text
startOnBoot == true && useBleHogp == false
```

then start `BluetoothService` only if Classic startup permissions are present.

If:

```text
startOnBoot == true && useBleHogp == true
```

then start `BleHogpService` only if BLE startup permissions are present:

```text
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
```

If required permissions are missing:

- do not start a broken backend,
- log a clear reason,
- do not silently fall back to Classic,
- do not persistently change `useBleHogp` from the boot receiver.

Preferred missing-BLE-permission log:

```text
Start on boot skipped: BLE HOGP selected but required Bluetooth connect/advertise permissions are missing.
```

### 6.4 Async receiver requirement

Replace `runBlocking` in `BootReceiver` with:

- `goAsync()`,
- coroutine,
- timeout, for example `withTimeoutOrNull(3_000)`,
- guaranteed `pendingResult.finish()` in `finally`.

Recommended shape:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeoutOrNull(3_000) {
                val settings = SettingsManager.flow(context).first()
                val decision = BootStartPlanner.plan(
                    startOnBoot = settings.startOnBoot,
                    useBleHogp = settings.useBleHogp,
                    hasClassicPermissions = ...,
                    hasBlePermissions = ...,
                    sdkInt = Build.VERSION.SDK_INT,
                )
                applyDecision(context, decision)
            }
        } finally {
            pendingResult.finish()
        }
    }
}
```

Exact implementation may vary, but it must not block indefinitely in `onReceive`.

### 6.5 BootStartPlanner requirement

Add a pure `BootStartPlanner` or equivalent helper.

It should produce decisions such as:

```kotlin
sealed class BootStartDecision {
    data object StartNothing : BootStartDecision()
    data object StartClassic : BootStartDecision()
    data object StartBle : BootStartDecision()
    data class Skip(val reason: String) : BootStartDecision()
}
```

The planner must cover:

- `startOnBoot=false` => start nothing,
- `startOnBoot=true`, Classic selected, Classic permissions granted => start Classic,
- `startOnBoot=true`, BLE selected, BLE permissions granted => start BLE,
- Classic selected but Classic permissions missing => skip/start nothing,
- BLE selected but connect or advertise missing => skip/start nothing,
- BLE selected must never silently start Classic.

### 6.6 Not acceptable

It is not acceptable to:

- always start Classic at boot,
- start Classic when `useBleHogp == true`,
- silently fall back to Classic when BLE boot permissions are missing,
- keep a misleading setting that implies boot starts the selected backend while implementation always starts Classic,
- block indefinitely in `runBlocking`,
- omit tests for boot decisions.

### 6.7 Acceptance criteria

Boot behavior is complete when:

- Option A backend-aware boot is implemented,
- BootReceiver reads both `startOnBoot` and `useBleHogp`,
- Classic-selected boot starts Classic only when Classic permissions are present,
- BLE-selected boot starts BLE only when connect + advertise permissions are present,
- BLE-selected boot never starts Classic silently,
- missing permissions produce a clear log/skip reason,
- `runBlocking` is removed from BootReceiver,
- `goAsync()` + coroutine + timeout are used,
- `BootStartPlannerTest` covers all required cases.

## 7. Replace timer-based notification permission sequencing

### 7.1 Problem

The notification permission prompt is delayed by a timer:

```kotlin
delay(NOTIF_PROMPT_DELAY_MS)
```

This reduces race risk but does not guarantee sequencing. If the user is still responding to the Bluetooth permission dialog after the delay, the notification launcher may still conflict.

### 7.2 Required behavior

Notification permission prompting must be state-based.

The app should request notification permission only after startup permission flow is resolved.

A valid trigger is one of:

- startup Bluetooth permissions granted/denied and handled,
- backend service start attempted/completed,
- main UI reaches stable loaded state after startup permission launcher is done,
- explicit user action in Settings.

### 7.3 Preferred v0.1 behavior

Use explicit user action or a clear post-startup state flag.

Recommended:

- Do not automatically request `POST_NOTIFICATIONS` during initial Bluetooth permission prompt.
- Show a non-blocking Settings row or Snackbar explaining notifications are optional.
- Request notification permission only when user taps enable notifications or when foreground service notification cannot be displayed clearly.

### 7.4 Acceptance criteria

This is complete when:

- notification permission launcher cannot fire while startup Bluetooth permission launcher is active,
- notification denial does not block Classic or BLE operation,
- no timer-only sequencing is used as the sole protection.

## 8. Validation evidence wording

### 8.1 Problem

The previous TODO marked all manual smoke tests complete, but the recorded evidence says some were verified at the logic-helper level rather than truly exercised manually on a device.

That is overclaiming.

### 8.2 Required behavior

Update validation docs/checklists to distinguish:

```text
Unit-verified
Instrumented-verified
Physical-HID-verified
Manual-device-verified
Pending manual UX smoke test
```

Do not mark a manual smoke item complete unless it was actually performed on a device by a human.

### 8.3 Acceptance criteria

This is complete when:

- validation notes no longer imply unit tests equal manual smoke tests,
- any unperformed manual UX checks are marked pending,
- physical HID results remain separate from normal instrumented tests.

## 9. Minor polish fixes

### 9.1 BLE denial message

Current denial message only mentions advertise permission.

Update to mention both required BLE permissions:

```text
BLE HOGP needs Bluetooth connect and advertise permissions; staying on Classic.
```

or:

```text
BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.
```

### 9.2 README scroll wording

Update feature list to say:

```text
two-finger vertical/horizontal scroll in Full descriptor mode
```

Do not imply SIMPLE descriptor mode supports scroll.

### 9.3 XML theme color cleanup

Compose theme is now BlueDeck-branded, but XML theme resources may still reference old template colors such as:

```text
purple_500
purple_700
teal_200
```

Clean these if safe.

Do not break splash/post-splash theme resolution.

### 9.4 Quick Settings tile label polish

If Quick Settings tile shows a raw MAC address as the connected label, improve if low risk.

Preferred order:

1. display bonded device name if available,
2. fall back to address if no name exists,
3. avoid misleading "connected" state unless actual connection confirmed.

Do not rework tile architecture.

## 10. Tests to add/update

Required:

- `StartupPermissionPlannerTest`
  - persisted Classic => Classic startup permissions,
  - persisted BLE => BLE startup permissions,
  - BLE plan includes advertise,
  - Classic plan excludes scan.

- `BluetoothService` foreground failure validation
  - pure helper if possible,
  - otherwise document manual validation.

- `BootStartPlannerTest` if implementing backend-aware or explicitly Classic-only boot.

- Notification sequencing test/helper if a pure state model is introduced.

Update existing tests as needed, but do not remove useful coverage.

## 11. Validation commands

Run:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew detekt
```

If device available:

```bash
./gradlew connectedDebugAndroidTest
```

If physical HID setup is available:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Then from Linux host, use the documented DBus `ConnectProfile(HID)` command.

## 12. Final acceptance criteria

This Fix 2 pass is complete when:

- persisted Classic startup requests/checks Classic startup permissions,
- persisted BLE startup requests/checks BLE startup permissions,
- missing BLE startup permissions do not silently start broken BLE service,
- `BluetoothService` aborts on failed foreground promotion,
- boot behavior is backend-aware and documented,
- `BootReceiver` never silently starts Classic while BLE mode is selected,
- notification permission prompt is state-sequenced, not timer-only,
- validation wording distinguishes unit/instrumented/physical/manual evidence,
- BLE denial message mentions connect + advertise,
- README says scroll is Full descriptor mode,
- new planner tests pass,
- build/test/lint/detekt validation passes or failures are honestly documented.
