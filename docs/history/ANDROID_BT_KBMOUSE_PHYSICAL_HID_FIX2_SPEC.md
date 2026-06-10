# Android Bluetooth Keyboard/Mouse Physical HID Fix 2 Specification

## 1. Purpose

This specification is a stricter follow-up for the broken physical Android phone ↔ laptop HID integration tests.

The current repository and latest `memory.md` discussion have drifted into blaming Ubuntu/BlueZ, but that conclusion is not justified until the repo's physical HID test actually follows the known-good host-initiated connection workflow.

The durable technical finding is:

> `BluetoothHidDevice.connect(host)` from Android is **not** a reliable initiator of the HID L2CAP connection for the current Android phone ↔ Linux laptop setup. The **host/laptop must initiate** the connection after Android registers as a HID device.

For Linux/BlueZ, the host-side command is:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

The test must be fixed to reflect that workflow before anyone concludes the Ubuntu/BlueZ configuration is the root cause.

## 2. Current failure mode

The current physical HID test still behaves like this:

1. Android registers a HID device profile.
2. The test calls `hid.connect(target)`.
3. The test waits for a connection callback.
4. The test times out.
5. Claude Code blames Ubuntu/BlueZ.

That is the wrong causality chain. The test is still relying on the Android-side connect call that the earlier memory entry said does not initiate L2CAP.

The correct workflow is:

1. Android registers a HID device profile.
2. Android test logs: “HID registered; run `bluetoothctl connect <PHONE_BT_ADDRESS>` from the laptop now.”
3. The laptop initiates the HID profile connection.
4. Android receives `BluetoothProfile.STATE_CONNECTED` for the expected host.
5. Android sends keyboard/mouse HID reports.
6. The laptop receives those reports.

## 3. Non-goals

Do not use this patch to perform unrelated release cleanup.

Do not:

- Rewrite the Bluetooth service architecture.
- Redesign the app UI.
- Rewrite Redux or middleware.
- Delete the physical HID test.
- Hide failing physical tests by unconditional `@Ignore`.
- Claim the test is fully automated if it still requires manual host-side `bluetoothctl connect`.
- Claim Ubuntu/BlueZ is the cause without host-side evidence.
- Change HID descriptors unless directly required for the physical test.

Do:

- Make the physical test explicitly opt-in.
- Make the host-initiated connection requirement explicit.
- Require/validate the expected host address.
- Improve timeout/error messages.
- Update docs and app logs to reflect host-initiated reality.
- Collect evidence before blaming host OS configuration.

## 4. Required test behavior

### 4.1 Physical test must be opt-in

`BluetoothHidSendReportTest` must skip unless explicitly enabled by instrumentation argument:

```text
runPhysicalHidTests=true
```

If absent or false:

```kotlin
Assume.assumeTrue(
    "Physical HID tests require runPhysicalHidTests=true and a host-side bluetoothctl connect",
    runPhysical
)
```

This is mandatory because the test requires:

- a real Android phone,
- a real laptop/host,
- existing pairing/bonding,
- human or script-driven host-side connection timing.

Normal `connectedDebugAndroidTest` must not fail simply because no physical host initiated a connection.

### 4.2 Physical test must require `hidHostAddress`

The test must accept:

```text
hidHostAddress=<LAPTOP_BT_ADDRESS>
```

For this physical send-report test, require it. Do not silently select the first bonded device.

If missing, skip with a clear message:

```text
Physical HID send-report test requires hidHostAddress=<laptop Bluetooth MAC>.
```

If provided but no bonded device matches it, skip or fail with a clear message:

```text
Expected HID host <address> is not bonded. Pair the phone and laptop first.
```

Prefer skip if the environment is not set up; prefer fail if the arg was provided and the setup claims to be ready.

### 4.3 Connection success must come from the expected host

In `onConnectionStateChanged(...)`:

- only count `BluetoothProfile.STATE_CONNECTED`,
- compare `device.address.uppercase(Locale.US)` with `hidHostAddress`,
- ignore/log connections from any other device.

Example:

```kotlin
override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
    val address = device.address.uppercase(Locale.US)
    if (state == BluetoothProfile.STATE_CONNECTED) {
        if (address == expectedHostAddress) {
            connectedDevice = device
            connectionLatch.countDown()
        } else {
            Log.w(TAG, "Ignoring HID connection from unexpected host $address")
        }
    }
}
```

The test must not pass because some unrelated bonded device connected.

### 4.4 Do not rely on `hid.connect(target)`

The physical test must remove `hid.connect(target)` from the required success path.

Acceptable alternatives:

1. Remove the call entirely.
2. Keep it only as a clearly labeled best-effort hint, after logging that host initiation is still required.

Preferred:

```kotlin
// Do not call BluetoothHidDevice.connect(target) as the connection mechanism.
// On the Linux/BlueZ physical test setup, the laptop must initiate HID L2CAP:
// bluetoothctl connect <PHONE_BT_ADDRESS>
```

The test's pass/fail condition must be based on the host-initiated `STATE_CONNECTED` callback.

### 4.5 Wait window must support manual host initiation

Increase the host-connect timeout to at least 90 seconds:

```kotlin
private const val HOST_CONNECT_TIMEOUT_SECONDS = 90L
```

Failure text must say exactly what the user needed to do:

```text
Host did not initiate HID connection within 90 seconds. After Android logs HID registration, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop.
```

Do not use vague messages like:

```text
ensure laptop Bluetooth is on
```

That is insufficient.

### 4.6 Test must log the host-side command after HID registration

After HID profile registration succeeds, print/log:

```text
HID profile registered.
From the laptop, run:
bluetoothctl connect <PHONE_BT_ADDRESS>
Expected host: <hidHostAddress>
```

If the local phone Bluetooth address cannot be read programmatically, use the placeholder `<PHONE_BT_ADDRESS>` and document how to find it.

The log must appear after Android is actually ready for the host to connect.

### 4.7 Physical test should be run as a single class

The docs and test comments must recommend running only the physical send-report class:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

Then run from the laptop when Android logs HID registration:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

The test must not rely on brittle whole-suite timing such as “run `bluetoothctl connect` around T+50 seconds.”

## 5. App behavior requirements

### 5.1 `hid.connect(...)` must be documented as best-effort

In `BluetoothService`, existing calls like:

```kotlin
hid?.connect(device)
```

may remain, but logs/UI must not imply Android can always initiate a working HID connection.

Update manual connect and auto-reconnect messages to say:

```text
Requested HID connection. Some hosts, especially Linux/BlueZ, must initiate the HID connection from the host side. On Linux, run bluetoothctl connect <phone-address> if the host does not connect automatically.
```

### 5.2 Auto-reconnect messaging

If auto-reconnect calls `hid?.connect(target)`, keep it as best-effort only.

Recommended log:

```text
Auto-reconnect requested via BluetoothHidDevice.connect. Some Linux/BlueZ hosts still require host-side bluetoothctl connect <phone-address>.
```

### 5.3 Pairing/debug logs

Where practical, add a concise diagnostic hint:

```text
Linux HID host tip: after this phone registers as a HID device, connect from the laptop with bluetoothctl connect <phone Bluetooth address>.
```

Do not clutter the main UI if this would make the app worse. Logs and docs are sufficient if UI change is intrusive.

## 6. Ubuntu/BlueZ blame policy

Claude Code must not claim Ubuntu/BlueZ is the sole cause unless the fixed test harness follows the host-initiated workflow and host-side logs show the host rejecting or failing the HID connection.

Before blaming Ubuntu/BlueZ, collect evidence such as:

```bash
bluetoothctl --version
uname -a
bluetoothctl info <PHONE_BT_ADDRESS>
bluetoothctl info <LAPTOP_BT_ADDRESS>
journalctl -u bluetooth --since "10 minutes ago"
sudo btmon
```

Evidence that would support an Ubuntu/BlueZ issue:

- Android logs HID profile registered successfully.
- The laptop runs `bluetoothctl connect <PHONE_BT_ADDRESS>` during the 90-second wait window.
- Android does not receive `STATE_CONNECTED`.
- `journalctl` or `btmon` shows BlueZ rejecting, aborting, or failing the HID profile/L2CAP negotiation.
- The same corrected test works against another host OS or another BlueZ version.

Without that evidence, the correct conclusion is:

```text
The physical test is not yet correctly implementing the host-initiated workflow, so Ubuntu/BlueZ cannot be blamed as the sole cause.
```

## 7. Documentation requirements

### 7.1 Create or update `docs/PHYSICAL_HID_TESTING.md`

This document must explain:

- the physical test is host-assisted,
- Android registers as a HID peripheral,
- the laptop/host initiates the HID connection,
- `BluetoothHidDevice.connect(host)` is best-effort only,
- the exact Gradle command,
- the exact `bluetoothctl connect <PHONE_BT_ADDRESS>` command,
- how to find phone and laptop Bluetooth addresses,
- how to run the test as a single class,
- how to collect Ubuntu/BlueZ evidence if connection fails.

### 7.2 Add troubleshooting section

Include these cases:

- physical test skipped because `runPhysicalHidTests=true` missing,
- physical test skipped because `hidHostAddress` missing,
- expected host is not bonded,
- user ran `bluetoothctl connect` too early,
- user ran `bluetoothctl connect` too late,
- laptop connected to wrong device,
- Android test did not reach HID registration,
- BlueZ reports connection rejected,
- stale pairing cache requires remove/re-pair.

### 7.3 Update `memory.md`

Add a concise note if needed:

```text
Do not blame Ubuntu/BlueZ for physical HID test failures until BluetoothHidSendReportTest is using host-initiated connection correctly: runPhysicalHidTests=true, hidHostAddress set, Android HID registered, laptop runs bluetoothctl connect <PHONE_BT_ADDRESS>, and btmon/journalctl evidence is collected.
```

## 8. Test expectations

### 8.1 Normal test run

Running:

```bash
./gradlew :app:connectedDebugAndroidTest
```

must not fail because the physical HID host is absent. The physical send-report test should skip unless explicitly enabled.

### 8.2 Physical test run

Running:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

must produce logs telling the user when to run:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

### 8.3 Optional helper test

If a `PhysicalHidTestConfig` helper is added, test:

- disabled when `runPhysicalHidTests` missing,
- enabled only when `runPhysicalHidTests=true`,
- missing `hidHostAddress` is invalid,
- host address is normalized.

## 9. Evidence requirements

When reporting the result of this fix, include:

- exact Gradle command used,
- Android phone model,
- laptop OS/version,
- phone Bluetooth address,
- laptop Bluetooth address,
- whether host-side `bluetoothctl connect` was run after HID registration,
- whether Android received `STATE_CONNECTED`,
- whether keyboard/mouse reports were observed,
- if failed, relevant `journalctl` and `btmon` excerpts.

Do not write “Ubuntu is broken” without logs.

## 10. Likely files to change

Primary:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidSendReportTest.kt
app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt
docs/PHYSICAL_HID_TESTING.md
memory.md
```

Optional:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/PhysicalHidTestConfig.kt
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/PhysicalHidTestConfigTest.kt
README.md
docs/v0.1_RELEASE_NOTES.md
```

## 11. Acceptance criteria

This fix is complete only when:

- `BluetoothHidSendReportTest` skips unless `runPhysicalHidTests=true`.
- `BluetoothHidSendReportTest` requires or strongly validates `hidHostAddress`.
- test success only counts connection from `hidHostAddress`.
- `hid.connect(target)` is removed from the required success path or labeled best-effort only.
- test logs host-side `bluetoothctl connect <PHONE_BT_ADDRESS>` after HID registration.
- host-connect timeout is at least 90 seconds.
- timeout failure explains host-initiated connection.
- docs explain the exact physical test procedure.
- app logs do not imply Android-side `hid.connect(...)` is guaranteed to initiate Linux HID connection.
- Ubuntu/BlueZ is not blamed without `journalctl`/`btmon`/`bluetoothctl` evidence.
