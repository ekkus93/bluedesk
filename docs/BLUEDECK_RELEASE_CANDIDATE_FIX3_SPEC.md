# BlueDeck Release Candidate Fix 3 Specification

## 1. Purpose

This specification defines the final focused release-candidate cleanup pass for **BlueDeck** after the Release Hardening Fix 2 implementation.

Fix 2 successfully addressed most of the remaining hardening issues:

- persisted backend startup planning,
- backend-aware boot,
- `BluetoothService` foreground promotion ordering,
- state-based notification permission sequencing,
- BlueDeck theme cleanup,
- Quick Settings tile label polish,
- validation evidence classification.

This Fix 3 pass must **not redo Fix 2**. It is a narrow release-candidate cleanup focused on the last integration bugs and branding/documentation inconsistencies found in review.

## 2. Scope

### 2.1 In scope

Fix 3 must address:

1. Permission callback semantics:
   - `RequestMultiplePermissions` callback maps are partial results.
   - Re-check complete permission state after callback.
   - Fix in `MainActivity` startup flow.
   - Fix in `SettingsScreen` BLE toggle flow.
   - Add tests for partial callback scenarios.

2. `BluetoothService.registerReceiver(...)` API compatibility:
   - avoid unguarded Android 13+ receiver flag overload on API 26–32,
   - use `ContextCompat.registerReceiver(...)` or SDK-gated overloads.

3. Defensive BLE advertise permission check in `BleHogpService`:
   - service must stop if `BLUETOOTH_ADVERTISE` is missing,
   - do not remain foregrounded with GATT initialized but no advertising.

4. Physical HID documentation/comments:
   - consistently promote Linux/BlueZ DBus `ConnectProfile(HID)` as the preferred known-good flow,
   - keep `bluetoothctl connect` only as fallback/diagnostic,
   - update test comments/logs if still centered on `bluetoothctl connect`.

5. Branding cleanup:
   - rebrand Classic HID SDP metadata to BlueDeck,
   - optionally rename `rootProject.name` to `BlueDeck`.

6. Validation wording cleanup:
   - remove or clarify contradictory checked manual-smoke boxes,
   - keep honest evidence categories.

### 2.2 Out of scope

Do not:

- redo backend-aware startup planning,
- redo BootReceiver architecture,
- redo notification state sequencing,
- redo BlueDeck theme work,
- change splash duration,
- change tagline wording,
- rename package/application ID,
- redesign UI,
- rewrite Bluetooth services,
- rewrite HID descriptors,
- remove physical HID tests,
- add broad suppressions.

## 3. Core bug: permission callback semantics

### 3.1 Problem

Android `RequestMultiplePermissions` callbacks usually report the permissions requested in that launcher call, not the complete permission state for all permissions the app cares about.

Current problematic pattern:

```kotlin
PermissionPolicy.missingRequired(result, plan.requiredPermissions).isEmpty()
```

where `result` is the callback map.

This is wrong when only a subset of required permissions was requested because the rest were already granted.

### 3.2 Example failure case

Persisted backend is BLE HOGP.

Required BLE permissions:

```text
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
```

Current state:

```text
BLUETOOTH_CONNECT = already granted
BLUETOOTH_ADVERTISE = missing
```

The app requests only:

```text
BLUETOOTH_ADVERTISE
```

User grants it.

Callback map may be:

```kotlin
mapOf(Manifest.permission.BLUETOOTH_ADVERTISE to true)
```

If the app checks this callback map against the full required list:

```kotlin
missingRequired(callbackResult, listOf(CONNECT, ADVERTISE))
```

then `CONNECT` is absent from the map and may be treated as denied/missing, causing the app to incorrectly fall back to Classic even though all required permissions are now granted.

### 3.3 Required rule

After any permission launcher callback, re-check the complete required permission list against the actual current OS permission state.

Do not treat the callback result map as complete state.

### 3.4 Required helper

Add or reuse a helper equivalent to:

```kotlin
object PermissionGrantChecker {
    fun hasAll(context: Context, permissions: List<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun missing(context: Context, permissions: List<String>): List<String> {
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
    }
}
```

Exact naming may differ.

This helper can be Android-facing. Keep pure permission-list planning in `PermissionPolicy` / `StartupPermissionPlanner`.

### 3.5 MainActivity startup flow fix

`MainActivity.onStartupPermissionResult(...)` must not evaluate the callback map as complete permission state.

Required behavior:

1. Use the existing current startup plan.
2. After callback, call `PermissionGrantChecker.hasAll(this, plan.requiredPermissions)` or equivalent.
3. If all permissions are now granted:
   - start planned backend,
   - mark startup permission flow resolved.
4. If not all permissions are granted and planned backend is BLE:
   - persist `useBleHogp=false`,
   - fall back to Classic if Classic permissions are available,
   - show/log the BLE connect/advertise message.
5. If not all permissions are granted and planned backend is Classic:
   - show required permission message,
   - do not start Classic if Classic required permissions are missing,
   - mark startup permission flow resolved only after denial path is handled.

### 3.6 SettingsScreen BLE toggle fix

`SettingsScreen` BLE toggle permission callback must follow the same rule.

Required behavior:

1. User toggles BLE on.
2. Determine required BLE permissions.
3. Request missing permissions.
4. In callback, re-check full required BLE permission state using `ContextCompat.checkSelfPermission`.
5. Persist `useBleHogp=true` only if full state is granted.
6. If still missing, leave/persist `useBleHogp=false` and show/log:

```text
BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.
```

### 3.7 Required tests

Add tests covering at least:

- startup BLE required = connect + advertise,
- callback map contains only advertise=true while connect is already granted,
- final full-state checker says all granted => BLE starts,
- callback map contains advertise=false => BLE fallback,
- Settings BLE toggle with partial callback succeeds when full state is granted,
- Settings BLE toggle with missing full state fails and stays Classic.

Pure tests should cover planner/result interpretation where possible. Android-dependent full-state checks can use wrappers/interfaces.

## 4. `BluetoothService.registerReceiver(...)` API compatibility

### 4.1 Problem

`BluetoothService` contains an unguarded call equivalent to:

```kotlin
registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
```

The receiver flags overload is Android-version sensitive. The app supports API levels below Android 13, so this must not be called unguarded on API 26–32.

### 4.2 Required behavior

Use one of the following safe patterns.

Preferred:

```kotlin
ContextCompat.registerReceiver(
    this,
    receiver,
    filter,
    ContextCompat.RECEIVER_NOT_EXPORTED,
)
```

Alternative:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
} else {
    @Suppress("DEPRECATION")
    registerReceiver(receiver, filter)
}
```

Use the same safe pattern consistently for service receivers.

### 4.3 Acceptance criteria

This fix is complete when:

- no unguarded Android 13+ receiver flag overload remains in code paths used on minSdk devices,
- `BluetoothService` is safe on API 26–32,
- build/lint does not complain about receiver export flags.

## 5. Defensive BLE advertise permission check in `BleHogpService`

### 5.1 Problem

Startup and boot planners should prevent launching BLE without advertise permission, but the service itself should still be defensive.

If `BleHogpService` starts without `BLUETOOTH_ADVERTISE`, it may remain foregrounded with GATT initialized but no advertising.

That is misleading and wasteful.

### 5.2 Required behavior

In `BleHogpService.onCreate()`, on Android 12+:

- require `BLUETOOTH_CONNECT`,
- require `BLUETOOTH_ADVERTISE`,
- if either is missing:
  - log clear message,
  - call `stopSelf()`,
  - return before starting foreground work/GATT/advertising.

Recommended message:

```text
BLE HOGP requires Bluetooth connect/advertise permissions; stopping BLE service.
```

### 5.3 Ordering

Check required BLE permissions before expensive BLE setup.

If foreground service promotion must happen early for Android policy, still do not proceed into GATT/advertising setup when advertise is missing.

### 5.4 Acceptance criteria

This fix is complete when:

- `BleHogpService` stops if connect permission is missing,
- `BleHogpService` stops if advertise permission is missing,
- the service does not remain foregrounded in a non-advertising state,
- behavior is covered by helper tests or documented manual/code-review validation.

## 6. Physical HID docs/comments: prefer `ConnectProfile(HID)`

### 6.1 Problem

Docs now include DBus `ConnectProfile(HID)`, but early text and some comments still lead with:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

The known-good Linux/BlueZ physical HID path is the DBus `ConnectProfile(HID)` call for UUID:

```text
00001124-0000-1000-8000-00805f9b34fb
```

### 6.2 Required wording

Docs and test comments should consistently state:

```text
Host initiates the HID profile connection. On Linux/BlueZ, prefer DBus ConnectProfile(HID). bluetoothctl connect is only a fallback/diagnostic because it may create a generic connection without opening the HID profile.
```

### 6.3 Required command

Keep the known-good command prominent:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_<PHONE_BT_ADDRESS_UNDERSCORE> \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Example:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_8C_6A_3B_5E_D3_48 \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

### 6.4 Acceptance criteria

This is complete when:

- `docs/PHYSICAL_HID_TESTING.md` clearly prefers `ConnectProfile(HID)`,
- README physical testing section does not imply `bluetoothctl connect` is primary,
- physical HID test logs/comments do not mislead Linux users toward generic connect as the known-good path,
- `bluetoothctl connect` remains documented only as fallback/diagnostic.

## 7. BlueDeck branding cleanup

### 7.1 HID SDP metadata

`BluetoothHidDeviceAppSdpSettings` should use BlueDeck-branded metadata.

Replace stale strings like:

```text
Bluetooth Keyboard/Mouse
Android Bluetooth HID
Gemini
```

with BlueDeck strings such as:

```text
BlueDeck Keyboard/Mouse
BlueDeck Android HID
BlueDeck
```

Exact wording can vary, but it should be recognizably BlueDeck.

### 7.2 Root project name

If low risk, change:

```kotlin
rootProject.name = "Bluetooth Keyboard Mouse"
```

to:

```kotlin
rootProject.name = "BlueDeck"
```

This does not change package name or application ID.

### 7.3 Acceptance criteria

Branding cleanup is complete when:

- app label remains `BlueDeck`,
- SDP metadata no longer says old project name/vendor,
- root project name is BlueDeck if changed,
- package/application ID remains unchanged.

## 8. Validation wording cleanup

### 8.1 Problem

The final validation evidence is now much more honest, but the checked manual-smoke checklist can still appear contradictory if it says everything is checked while final notes say some UX smoke is pending.

### 8.2 Required behavior

Update validation notes/TODO/memory so each manual UX item is clearly classified:

```text
PASS — manually verified on device
PASS — unit/instrumented verified only
PASS — physical HID verified
PENDING — manual UX smoke needed
FAIL — issue found
N/A — not applicable
```

Do not use checked boxes in a way that implies pending UX items were manually performed.

### 8.3 Acceptance criteria

This is complete when:

- validation text does not conflict with itself,
- pending manual UX items are visibly pending,
- unit/instrumented/physical/manual evidence remains separated.

## 9. Tests and validation

### 9.1 Required tests

Add/update tests for:

- partial permission callback result in startup flow,
- partial permission callback result in BLE toggle flow,
- permission full-state checker behavior through an interface/wrapper if needed,
- BLE service advertise-missing decision if a pure helper is extracted.

### 9.2 Required commands

Run:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew detekt
```

If device/emulator is available:

```bash
./gradlew connectedDebugAndroidTest
```

If physical setup is available:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Use DBus `ConnectProfile(HID)` on the Linux host.

## 10. Final acceptance criteria

Fix 3 is complete when:

- MainActivity re-checks full permission state after startup permission callback,
- SettingsScreen BLE toggle re-checks full permission state after callback,
- partial callback maps no longer cause false BLE denial,
- tests cover partial callback maps,
- `BluetoothService` receiver registration is API 26–32 safe,
- `BleHogpService` stops when advertise permission is missing,
- physical HID docs/comments prefer DBus `ConnectProfile(HID)`,
- HID SDP metadata is BlueDeck-branded,
- root project name is BlueDeck if changed,
- validation wording no longer overclaims manual smoke,
- build/test/lint/static validation passes or failures are honestly documented.
