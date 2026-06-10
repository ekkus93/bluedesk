# BlueDeck Release Hardening Specification

## 1. Purpose

This specification defines the final release-hardening pass for **BlueDeck**, the Android Bluetooth keyboard/mouse app.

The app now has usable branding, better documentation, stronger HID report structure, and a much better physical HID test harness. However, the latest code review still found core release blockers in these areas:

- Classic/BLE backend service lifecycle.
- Android Bluetooth permission model.
- Pairing scan permissions.
- BLE HOGP enablement gating.
- Foreground service failure handling.
- SIMPLE/FULL descriptor mouse scroll behavior.
- Debug logging privacy/default behavior.
- Quick Settings tile behavior in BLE mode.
- Physical HID testing documentation around Linux/BlueZ `ConnectProfile(HID)`.
- BlueDeck theme polish and stale resource cleanup.
- Release version and final validation.

The goal of this pass is to make the app safer, more predictable, and more honest before a public or semi-public release.

## 2. Scope

### 2.1 In scope

This hardening pass must address:

1. Stop inactive foreground services when switching Classic/BLE backends.
2. Correct the permission model:
   - Classic startup requires connect, not scan.
   - Scan requires scan only.
   - BLE HOGP requires connect + advertise.
   - Notifications are optional and non-fatal.
3. Fix Pairing screen scan permission request.
4. Gate BLE HOGP mode on required BLE permissions before persisting the setting.
5. Stop services safely on missing permissions or `startForeground()` failure.
6. Fix Mouse screen scroll behavior in SIMPLE descriptor mode.
7. Update physical HID docs to include the DBus `ConnectProfile(HID)` Linux/BlueZ flow.
8. Remove forced startup debug logging.
9. Sequence notification permission prompts so they do not race startup Bluetooth permission prompts.
10. Guard Quick Settings tile in BLE mode.
11. Apply BlueDeck color palette to the in-app theme.
12. Clean stale icon/resource/docs leftovers.
13. Set the intended release version.
14. Add/update tests and validation notes.

### 2.2 Out of scope

Do not use this pass to:

- Rewrite the app architecture.
- Replace Redux/state management.
- Rename the package, namespace, or application ID.
- Rework all UI screens.
- Implement new major features.
- Make BLE HOGP production-grade beyond the explicit fixes here.
- Rewrite the physical HID test harness again unless needed for `ConnectProfile(HID)` documentation consistency.
- Suppress tests or lint warnings to make the build appear green.

## 3. Release decision

Do **not** call this a clean public `1.0` until the blockers in this spec are fixed and validated.

If this is intended as an early release, prefer:

```kotlin
versionName = "0.1.0"
```

If the version remains:

```kotlin
versionName = "1.0"
```

then the remaining lifecycle/permission bugs are too serious.

## 4. Backend service lifecycle

### 4.1 Problem

`MainActivity.switchBackend(...)` currently unbinds services but does not stop inactive started foreground services.

Unbinding is not enough. Both Classic and BLE backends are started services / foreground services. A service can remain alive after all clients unbind.

### 4.2 Required behavior

When switching Classic → BLE:

1. Clear current key sender.
2. Unbind Classic service if bound.
3. Call `stopService(Intent(this, BluetoothService::class.java))`.
4. Clear connected device state.
5. Start/bind BLE service only after Classic stop path is invoked.

When switching BLE → Classic:

1. Clear current key sender.
2. Unbind BLE service if bound.
3. Call `stopService(Intent(this, BleHogpService::class.java))`.
4. Clear connected device state.
5. Start/bind Classic service only after BLE stop path is invoked.

### 4.3 Implementation guidance

Add explicit helpers in `MainActivity` or equivalent:

```kotlin
private fun stopClassicBackend() {
    if (serviceBound) {
        StoreProvider.setKeySender(null)
        try {
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            DebugLog.w("MainActivity", "Classic unbind failed: ${e.message}")
        }
        serviceBound = false
    }
    stopService(Intent(this, BluetoothService::class.java))
}

private fun stopBleBackend() {
    if (bleHogpBound) {
        StoreProvider.setKeySender(null)
        try {
            unbindService(bleHogpConnection)
        } catch (e: IllegalArgumentException) {
            DebugLog.w("MainActivity", "BLE unbind failed: ${e.message}")
        }
        bleHogpBound = false
    }
    stopService(Intent(this, BleHogpService::class.java))
}
```

Exact code may differ, but the behavior must be equivalent.

### 4.4 Test guidance

Prefer a pure `BackendTransitionPlanner` test instead of Android framework mocks.

Required scenarios:

- Classic → BLE includes stop Classic before start BLE.
- BLE → Classic includes stop BLE before start Classic.
- No-op transition does not stop/restart.
- Transition plan never starts both backends.

## 5. Permission model

### 5.1 Required permission categories

Use explicit operation-specific methods.

#### Classic startup

Android 12+:

```text
BLUETOOTH_CONNECT
```

Classic startup must **not** require:

```text
BLUETOOTH_SCAN
BLUETOOTH_ADVERTISE
POST_NOTIFICATIONS
```

#### Discovery / scan

Android 12+:

```text
BLUETOOTH_SCAN
```

Discovery/scan must **not** require:

```text
BLUETOOTH_ADVERTISE
POST_NOTIFICATIONS
```

#### BLE HOGP startup / enablement

Android 12+:

```text
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
```

#### Notifications

Android 13+:

```text
POST_NOTIFICATIONS
```

This permission is optional and non-fatal. Denying it must not block startup, scanning, Classic HID operation, or BLE setting changes.

### 5.2 Required helper names

Rename or add explicit methods in `PermissionPolicy.kt`:

```kotlin
fun requiredForClassicStartup(sdkInt: Int): List<String>
fun requiredForScan(sdkInt: Int): List<String>
fun requiredForBleStartup(sdkInt: Int): List<String>
fun optionalForStartup(sdkInt: Int): List<String>
fun missingRequired(
    grants: Map<String, Boolean>,
    required: List<String>
): List<String>
```

Do not keep using ambiguous internal callers like `requiredForClassic(...)` if the function actually means startup permissions.

Deprecated wrappers are acceptable temporarily, but app code and tests should use the explicit names.

### 5.3 Startup permission flow

`MainActivity` must request only the required permissions for the selected backend:

- Classic mode: Classic startup permissions.
- BLE mode: BLE startup permissions.

Do not request `POST_NOTIFICATIONS` in the same fatal startup launcher as Bluetooth permissions.

Do not gate startup on:

```kotlin
granted.values.all { it }
```

when the request includes optional or unrelated permissions.

### 5.4 Pairing scan flow

The Pairing screen scan button must request only scan-required permissions.

Bad:

```kotlin
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
POST_NOTIFICATIONS
```

Good:

```kotlin
PermissionPolicy.requiredForScan(Build.VERSION.SDK_INT)
```

The permission result must check only the scan-required permissions.

### 5.5 BLE HOGP toggle gating

The Settings screen must not persist:

```kotlin
useBleHogp = true
```

until required BLE permissions are granted.

Required behavior:

1. User toggles BLE on.
2. Compute missing BLE startup permissions.
3. If missing, request them.
4. If granted, persist `useBleHogp = true`.
5. If denied, leave `useBleHogp = false`.
6. Show Snackbar/dialog explaining BLE HOGP requires Bluetooth advertising permission.

Toggling BLE off should always persist `useBleHogp = false`.

## 6. Foreground service and missing-permission safety

### 6.1 `BleHogpService.onCreate()` missing permission

If `BleHogpService` is started and required permissions are missing, do not simply `return`.

Bad:

```kotlin
if (!hasConnect) {
    DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; deferring BLE init")
    return
}
```

Good:

```kotlin
if (!hasConnect) {
    DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; stopping BLE service")
    stopSelf()
    return
}
```

If `BLUETOOTH_ADVERTISE` is required before advertising starts, guard that path similarly.

### 6.2 `startForeground()` failure

If `startForeground(...)` fails, do not continue as a fake foreground service by only posting a notification.

Bad:

```kotlin
try {
    service.startForeground(id, notification)
} catch (e: Exception) {
    notificationManager.notify(id, notification)
}
```

Good:

```kotlin
try {
    service.startForeground(id, notification)
} catch (e: Exception) {
    DebugLog.e(TAG, "startForeground failed: ${e.message}")
    service.stopSelf()
    return false
}
```

Update `ServiceForegroundController` to return success/failure if needed:

```kotlin
fun startInForeground(...): Boolean
```

Then callers must stop or return on failure.

## 7. SIMPLE/FULL descriptor mouse scroll behavior

### 7.1 Problem

SIMPLE descriptor mode disables scroll, but `MouseScreen` still advertises and dispatches scroll gestures.

### 7.2 Required behavior

In SIMPLE descriptor mode:

- do not display “2-finger scroll” as available,
- do not dispatch vertical scroll,
- do not dispatch horizontal scroll,
- optionally show: “Scroll requires Full HID descriptor mode.”

In FULL descriptor mode:

- vertical scroll is available,
- horizontal scroll is available only when `enableHorizontalScroll == true`.

### 7.3 Implementation guidance

Add or use a pure helper:

```kotlin
object ScrollPolicy {
    fun verticalAvailable(settings: Settings): Boolean =
        !settings.hidSimplified

    fun horizontalAvailable(settings: Settings): Boolean =
        !settings.hidSimplified && settings.enableHorizontalScroll
}
```

`MouseScreen` must use this helper for both UI copy and gesture dispatch.

## 8. Debug logging

### 8.1 Problem

`MainActivity.onCreate()` still force-enables logging:

```kotlin
DebugLog.setEnabled(true)
```

This undermines settings-driven debug logging.

### 8.2 Required behavior

Remove startup force-enable.

`SettingsViewModel` or the existing settings observer should be the only owner that applies:

```kotlin
DebugLog.setEnabled(settings.debugLogging)
DebugLog.setLevel(...)
```

If minimal startup logging is necessary, keep it privacy-safe and do not override the user's persisted debug logging preference.

## 9. Notification permission sequencing

### 9.1 Problem

The app may request notification permission from Compose at about the same time as `MainActivity` requests required Bluetooth permissions.

Multiple permission launchers at first launch can produce unpredictable UX.

### 9.2 Required behavior

Startup should prioritize required Bluetooth permissions for the selected backend.

Notification permission should be requested:

- after startup permissions are resolved,
- or from an explicit user action,
- or as a non-blocking later prompt.

Denying notification permission must not block the app.

## 10. Quick Settings tile

### 10.1 Problem

The tile sends Classic-only service broadcasts and does not guard BLE mode.

### 10.2 Required behavior

For this release, the tile may remain Classic-only, but it must be honest and guarded.

If Classic mode is active:

- tile may send Classic connect/disconnect broadcasts.

If BLE mode is active:

- tile must not send Classic connect/disconnect broadcasts,
- tile should show unavailable/disabled state or a clear label,
- documentation should say tile controls Classic HID only.

### 10.3 State source

Prefer `SettingsManager` or a small shared helper over raw ad hoc SharedPreferences. If using raw prefs remains necessary for a quick patch, document the limitation and keep behavior correct.

## 11. Physical HID Linux/BlueZ documentation

### 11.1 Problem

The latest project memory says physical tests passed only after using a DBus `ConnectProfile(HID)` command for UUID:

```text
00001124-0000-1000-8000-00805f9b34fb
```

Generic:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

may create a generic ACL connection but not necessarily open the HID profile.

### 11.2 Required docs update

Update `docs/PHYSICAL_HID_TESTING.md` to make the Linux/BlueZ procedure accurate.

Preferred Linux command after Android logs `READY_FOR_HOST_CONNECT`:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_<PHONE_BT_ADDRESS_UNDERSCORE> \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Example for phone address `8C:6A:3B:5E:D3:48`:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_8C_6A_3B_5E_D3_48 \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Docs should explain:

- `bluetoothctl connect` may be useful but is not always enough.
- `ConnectProfile(HID)` is the known-good Linux/BlueZ physical-test command.
- clean re-pair may be required before running the test.
- collect `btmon` and `journalctl` if it fails.

### 11.3 Test harness docs

Ensure docs still include:

- `runPhysicalHidTests=true`,
- `hidHostAddress=<LAPTOP_BT_ADDRESS>`,
- `hidPhoneAddress=<PHONE_BT_ADDRESS>`,
- laptop/controller address comes from `bluetoothctl show`,
- phone address comes from `bluetoothctl devices`.

## 12. BlueDeck theme and polish

### 12.1 In-app theme colors

The launcher/splash now use BlueDeck colors, but the app theme still appears to use old Android template colors.

Update app/Compose theme colors to use the BlueDeck palette:

```text
Navy:       #101827
Navy dark:  #07111F
Cyan:       #00D4FF
Teal:       #00BFA6
Indigo:     #4F46E5
Soft white: #F8FAFC
```

Do not make readability worse. Validate light/dark mode contrast.

### 12.2 Splash duration

The custom Compose splash currently forces a fixed duration. This may be acceptable but should be tuned for utility-app use.

Recommendation:

- reduce to 800–1200 ms,
- or only show long branded splash on first launch,
- do not delay repeated utility launches unnecessarily.

### 12.3 String resources

Move hardcoded splash title/tagline strings into resources if practical:

```xml
<string name="app_name">BlueDeck</string>
<string name="bluedeck_tagline">Your phone as a Bluetooth keyboard and mouse.</string>
```

### 12.4 Stale resources

Remove or archive unreferenced old launcher resources if safe:

```text
ic_launcher_background.xml
ic_launcher_foreground.xml
```

Do not remove anything still referenced by legacy launchers.

## 13. Versioning

Set the intended release version deliberately.

If this is still an early public test release:

```kotlin
versionCode = 1
versionName = "0.1.0"
```

If keeping:

```kotlin
versionName = "1.0"
```

then all release blockers in this spec should be fixed first.

## 14. Documentation cleanup

### 14.1 README

Update README to:

- reflect BlueDeck brand,
- say scroll is available in Full descriptor mode,
- avoid implying simplified mode supports scroll,
- mention Linux/BlueZ `ConnectProfile(HID)` for physical tests,
- distinguish Classic HID stable path from BLE HOGP experimental path.

### 14.2 Historical docs

Move old spec/TODO documents to:

```text
docs/history/
```

if they clutter the main docs directory. Do not delete useful history unless requested.

## 15. Validation requirements

Run:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew lintDebug
```

If device available:

```bash
./gradlew connectedDebugAndroidTest
```

Manual smoke tests:

1. Cold launch shows BlueDeck splash.
2. Launcher shows BlueDeck icon/name.
3. Classic startup works without scan permission.
4. Scan prompts only scan permission.
5. Notification denial does not block app.
6. BLE toggle denied advertise permission leaves BLE off.
7. Classic/BLE switch stops inactive service.
8. SIMPLE mode does not dispatch scroll.
9. FULL mode scroll works.
10. Quick Settings tile does not send Classic broadcasts in BLE mode.
11. Physical HID test docs match actual passing command.

## 16. Acceptance criteria

This release-hardening pass is complete when:

- inactive backend foreground services are stopped during switches,
- permission policy is operation-specific and correct,
- scan permissions are scan-only,
- BLE toggle is permission-gated,
- missing BLE permission stops BLE service safely,
- `startForeground()` failure stops service safely,
- SIMPLE mode does not advertise or dispatch scroll,
- debug logging is not force-enabled at startup,
- notification permission prompt does not race startup Bluetooth permissions,
- Quick Settings tile is guarded in BLE mode,
- physical HID docs include DBus `ConnectProfile(HID)` known-good flow,
- BlueDeck theme colors are applied in-app,
- stale launcher/resources/docs are cleaned or archived,
- release version is deliberate,
- validation commands pass or failures are clearly documented.
