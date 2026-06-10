# Android Bluetooth Keyboard/Mouse Physical HID Fix 3 Specification

## 1. Purpose

This specification fixes the remaining physical Android phone ↔ laptop HID test failures after the Fix 2 pass.

Fix 2 partially corrected the test by adding:

- `runPhysicalHidTests=true`,
- `hidHostAddress=<LAPTOP_BT_ADDRESS>`,
- expected-host validation,
- removal of `hid.connect(target)` from the required physical-test success path,
- 90-second host-connect wait,
- basic host-initiated workflow docs.

However, Fix 2 still left critical address-handling and documentation bugs that can keep the physical test failing even when the Android HID registration works.

The most important remaining issue is:

> The host-side command `bluetoothctl connect <PHONE_BT_ADDRESS>` must use the **Android phone's Bluetooth address**, but the test currently derives/logs that address from `BluetoothAdapter.address`, which is unreliable on modern Android and may return `02:00:00:00:00:00` or another unusable value.

This Fix 3 patch must make the phone and host Bluetooth addresses explicit and unambiguous.

## 2. Core address model

There are two different Bluetooth addresses involved:

### 2.1 `hidHostAddress`

`hidHostAddress` is the **laptop / host / local Linux controller address**.

On the laptop, find it with:

```bash
bluetoothctl show
```

Expected output shape:

```text
Controller E8:FB:1C:25:E4:C2 arisu [default]
```

Use the `Controller` address as:

```text
hidHostAddress=E8:FB:1C:25:E4:C2
```

This is the address Android sees when the laptop connects to the phone.

### 2.2 `hidPhoneAddress`

`hidPhoneAddress` is the **Android phone / HID peripheral / remote device address** as seen by the laptop.

On the laptop, find it with:

```bash
bluetoothctl devices
bluetoothctl info <PHONE_BT_ADDRESS>
```

Known remembered example from the project history:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

Use the phone address as:

```text
hidPhoneAddress=8C:6A:3B:5E:D3:48
```

This is the address the laptop must connect to.

### 2.3 Do not confuse the two

Wrong:

```text
Use bluetoothctl devices to find the laptop address.
```

Correct:

```text
Use bluetoothctl show to find the laptop/controller address.
Use bluetoothctl devices to find the Android phone/remote-device address.
```

Wrong:

```text
From the laptop, run bluetoothctl connect <hidHostAddress>
```

Correct:

```text
From the laptop, run bluetoothctl connect <hidPhoneAddress>
```

Wrong:

```text
In Android app logs, say bluetoothctl connect ${device.address}
```

because `device.address` in `BluetoothService` is usually the host/laptop address from Android's perspective.

Correct:

```text
On Linux, run bluetoothctl connect <this phone's Bluetooth address> from the host.
```

## 3. Current Fix 2 deficiencies

The latest reviewed code was closer, but still had these defects:

1. `BluetoothHidSendReportTest` logged the phone address using `BluetoothAdapter.address`.
2. `BluetoothAdapter.address` is not reliable for this purpose on modern Android.
3. The test did not accept or require `hidPhoneAddress`.
4. The host-side command could be wrong even if the test harness otherwise worked.
5. `docs/PHYSICAL_HID_TESTING.md` gave incorrect address-discovery instructions for the laptop address.
6. App logs in `BluetoothService` still risk saying `bluetoothctl connect ${device.address}`, where `${device.address}` is the laptop address, not the phone address.
7. The docs did not sufficiently require `journalctl`/`btmon`/`bluetoothctl` evidence before blaming Ubuntu/BlueZ.
8. `memory.md` overclaimed Fix 2 completion.

## 4. Required test behavior

### 4.1 Add `hidPhoneAddress`

`BluetoothHidSendReportTest` must accept:

```text
hidPhoneAddress=<PHONE_BT_ADDRESS>
```

When `runPhysicalHidTests=true`, the test must require both:

```text
hidHostAddress=<LAPTOP_BT_ADDRESS>
hidPhoneAddress=<PHONE_BT_ADDRESS>
```

If `hidPhoneAddress` is missing, skip with a clear message:

```text
Physical HID test requires hidPhoneAddress=<Android phone Bluetooth MAC>. Find it from the laptop with bluetoothctl devices.
```

### 4.2 Normalize addresses

Normalize both addresses consistently:

```kotlin
val expectedHostAddress = args.getString("hidHostAddress")
    ?.uppercase(Locale.US)

val phoneAddress = args.getString("hidPhoneAddress")
    ?.uppercase(Locale.US)
```

Validation should reject blank strings.

Optionally validate MAC-shape:

```text
AA:BB:CC:DD:EE:FF
```

Do not over-engineer validation; a clear skip/fail message is more important.

### 4.3 Stop using `BluetoothAdapter.address` for host-side command text

Remove `BluetoothAdapter.address` from the `bluetoothctl connect ...` instruction.

Do not log:

```kotlin
bluetoothctl connect ${adapter.address}
```

Instead log:

```kotlin
bluetoothctl connect $phoneAddress
```

### 4.4 Keep `hidHostAddress` for connection validation

`hidHostAddress` remains the expected connecting laptop address.

In `onConnectionStateChanged(...)`, only count a connection as success if:

```kotlin
device.address.uppercase(Locale.US) == expectedHostAddress
```

The test must not confuse `hidPhoneAddress` with `hidHostAddress`.

### 4.5 Log both addresses clearly

After HID profile registration succeeds, log:

```text
HID profile registered.

Expected host/laptop address:
<hidHostAddress>

From the laptop, connect to this Android phone:
bluetoothctl connect <hidPhoneAddress>
```

Timeout message must also use `hidPhoneAddress`:

```text
Host did not initiate HID connection within 90 seconds. After Android logs HID registration, run bluetoothctl connect <hidPhoneAddress> from the laptop.
```

### 4.6 Recommended physical test command

Document and support:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Concrete remembered example:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=E8:FB:1C:25:E4:C2 \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=8C:6A:3B:5E:D3:48
```

Then from the laptop:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

## 5. App log/message requirements

### 5.1 Fix manual connect message

In `BluetoothService.connectDevice(...)` or equivalent, do not log:

```text
bluetoothctl connect ${device.address}
```

because `device.address` is the host/laptop address as seen by Android, while `bluetoothctl connect ...` must be run on the laptop against the phone's address.

Use wording that does not insert the wrong address:

```text
Requested HID connection. Some Linux/BlueZ hosts must initiate the HID connection from the host side. On Linux, run bluetoothctl connect <this phone's Bluetooth address> from the laptop.
```

### 5.2 Fix auto-reconnect message

For auto-reconnect paths that call `hid?.connect(target)`, keep it as best-effort only.

Use wording:

```text
Auto-reconnect requested via BluetoothHidDevice.connect. Some Linux/BlueZ hosts still require the host to initiate the HID connection with bluetoothctl connect <this phone's Bluetooth address>.
```

### 5.3 Do not imply Android can guarantee host connection

Any app log/UI text around `hid.connect(...)` must avoid implying that Android can always initiate the HID connection.

Acceptable phrases:

- "best-effort"
- "requested"
- "host may still need to initiate"
- "from Linux, connect to this phone from the host"

Avoid phrases:

- "connected"
- "connecting to host" unless a connection callback confirms it
- `bluetoothctl connect ${device.address}` from Android-side code

## 6. Documentation requirements

### 6.1 Update `docs/PHYSICAL_HID_TESTING.md`

The doc must clearly distinguish:

```text
Laptop/controller address:
  bluetoothctl show

Phone/remote paired device address:
  bluetoothctl devices
```

Required section:

```markdown
## Bluetooth address roles

| Name | Meaning | Used for | How to find |
|---|---|---|---|
| hidHostAddress | Laptop/local controller address | Android validates incoming STATE_CONNECTED device | bluetoothctl show |
| hidPhoneAddress | Android phone/remote device address | Laptop runs bluetoothctl connect against this address | bluetoothctl devices |
```

### 6.2 Correct the Gradle command

Docs must include `hidPhoneAddress`.

Old command without phone address is incomplete.

Correct:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

### 6.3 Correct host-side command

Docs must say:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

not:

```bash
bluetoothctl connect <LAPTOP_BT_ADDRESS>
```

### 6.4 Remove or reword misleading BlueZ claims

The laptop is the HID host. Android is the HID peripheral/device.

Do not write vague claims like:

```text
BlueZ has limited support for HID peripherals
```

in a way that implies the laptop is the HID peripheral in this setup.

Better:

```text
If the corrected host-initiated flow still fails, BlueZ may be rejecting or failing the HID host connection attempt. Collect journalctl and btmon evidence before concluding that Ubuntu/BlueZ is the root cause.
```

### 6.5 Add evidence checklist before blaming Ubuntu/BlueZ

Docs must include:

```bash
bluetoothctl --version
uname -a
bluetoothctl show
bluetoothctl devices
bluetoothctl info <PHONE_BT_ADDRESS>
journalctl -u bluetooth --since "10 minutes ago"
sudo btmon
```

Evidence that supports Ubuntu/BlueZ as the issue:

- Android logs HID profile registration.
- Test logs correct `hidHostAddress`.
- Test logs correct `hidPhoneAddress`.
- Laptop runs `bluetoothctl connect <hidPhoneAddress>` during the wait window.
- Android does not receive `STATE_CONNECTED`.
- `journalctl` or `btmon` shows host-side rejection/failure.

## 7. Memory requirements

Update `memory.md` so it does not overclaim Fix 2 completion.

Required correction:

```text
Fix 2 partially corrected the physical HID test by adding runPhysicalHidTests, hidHostAddress, host-initiated wait, and removal of hid.connect(target) from the required success path. Fix 3 is still needed because the test used BluetoothAdapter.address for the phone address, docs confused laptop vs phone address discovery, and evidence-before-BlueZ-blame docs were incomplete.
```

Do not say:

```text
All verification complete.
```

unless the actual physical test passed with the corrected `hidHostAddress` and `hidPhoneAddress` flow.

## 8. Validation requirements

### 8.1 Normal instrumented run

Run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected:

- physical HID test is skipped unless `runPhysicalHidTests=true`,
- no real laptop required,
- unrelated tests pass or failures are documented.

### 8.2 Physical single-class run

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Then from laptop:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Record:

- Android device model,
- Android version,
- laptop OS/version,
- BlueZ version,
- `hidHostAddress`,
- `hidPhoneAddress`,
- whether Android received `STATE_CONNECTED`,
- whether keyboard report was observed,
- whether mouse report was observed,
- if failed, `journalctl` and `btmon` excerpts.

### 8.3 Documentation validation

Manually verify that a user following the docs would:

1. use `bluetoothctl show` for the laptop/controller address,
2. use `bluetoothctl devices` for the phone/remote address,
3. pass both addresses to Gradle,
4. run `bluetoothctl connect <PHONE_BT_ADDRESS>` from the laptop.

## 9. Likely files to change

Primary:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidSendReportTest.kt
app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt
docs/PHYSICAL_HID_TESTING.md
memory.md
```

Optional:

```text
README.md
docs/v0.1_RELEASE_NOTES.md
```

## 10. Acceptance criteria

This Fix 3 patch is complete only when:

- `BluetoothHidSendReportTest` requires `hidPhoneAddress` when `runPhysicalHidTests=true`.
- `BluetoothHidSendReportTest` uses `hidPhoneAddress` in all `bluetoothctl connect ...` instructions.
- `BluetoothHidSendReportTest` no longer uses `BluetoothAdapter.address` for the phone address in physical-test instructions.
- `hidHostAddress` remains the expected laptop/controller address for validating `STATE_CONNECTED`.
- Docs say `bluetoothctl show` finds the laptop/controller address.
- Docs say `bluetoothctl devices` finds the phone/remote address.
- App logs do not say `bluetoothctl connect ${device.address}` when `${device.address}` is the host address.
- Evidence checklist exists before blaming Ubuntu/BlueZ.
- `memory.md` no longer overclaims Fix 2 completion.
- Normal test run skips physical HID test unless explicitly enabled.
- Physical single-class command includes both `hidHostAddress` and `hidPhoneAddress`.
