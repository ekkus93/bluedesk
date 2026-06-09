# Android Bluetooth Keyboard/Mouse Physical HID Test Fix Specification

## 1. Purpose

This specification fixes the physical Android phone ↔ laptop HID integration test flow that was broken after recent changes.

The critical finding from `memory.md` is:

> `BluetoothHidDevice.connect(laptop)` from Android does **not** initiate the L2CAP HID connection for this phone/laptop setup. The **host/laptop must initiate** the HID profile connection to the Android phone, for example with `bluetoothctl connect <PHONE_BT_ADDRESS>`.

The current physical HID send-report test still assumes Android can initiate the HID connection with `hid.connect(target)`. That is the core bug.

This patch must make the test reflect the real working procedure:

1. Android registers as a HID device.
2. The laptop initiates the connection to the Android phone.
3. The Android test waits for the host-initiated `STATE_CONNECTED` callback.
4. The Android test sends keyboard/mouse reports only after the host connection is established.

## 2. Non-goals

Do not use this patch to perform broad app cleanup.

Do not:

- Rewrite the Bluetooth service architecture.
- Redesign the UI.
- Rewrite Redux/middleware.
- Attempt to make physical Bluetooth HID tests fully automated unless there is a working host-side script.
- Claim the test is automated if it still requires manual `bluetoothctl connect`.
- Remove the physical test entirely.
- Hide or ignore failing tests without clear opt-in gating.

Do:

- Fix the physical HID test so it matches the known working host-initiated workflow.
- Add explicit instrumentation arguments.
- Make accidental test execution skip cleanly.
- Document the exact procedure.
- Update app messaging so users understand that Linux/BlueZ hosts may need to initiate the connection.

## 3. Current bug

### 3.1 Current broken assumption

The current `BluetoothHidSendReportTest.kt` contains logic equivalent to:

```kotlin
if (connectionLatch.count > 0) {
    hid.connect(target)
}

if (!connectionLatch.await(45, TimeUnit.SECONDS)) {
    notReadyReason = "Host did not connect within 45 s..."
    return
}
```

This is wrong for the current physical setup. `hid.connect(target)` is at best a best-effort hint; it does not cause the laptop to open the HID L2CAP connection.

### 3.2 Correct working procedure

The correct procedure is host-initiated:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

For the currently known phone address from memory:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

The laptop command must be run after the Android HID app/test has registered the HID device profile and is waiting for the host connection.

### 3.3 Why whole-suite timing is brittle

Running the physical test as part of the whole `connectedDebugAndroidTest` suite is unreliable because:

- JUnit test ordering is not a stable synchronization mechanism.
- Other instrumentation tests can shift timing.
- The host-side `bluetoothctl connect` command may run before or after the actual wait window.
- The test currently waits only 45 seconds.

The physical HID send-report test should be run as a specific test class when manually validating real hardware.

## 4. Required test behavior

### 4.1 Physical test must be opt-in

`BluetoothHidSendReportTest` must not run accidentally in normal CI or normal `connectedDebugAndroidTest`.

Add an instrumentation argument:

```text
runPhysicalHidTests=true
```

If the argument is absent or not `"true"`, skip the test using an assumption:

```kotlin
val runPhysical = InstrumentationRegistry.getArguments()
    .getString("runPhysicalHidTests") == "true"

Assume.assumeTrue(
    "Physical HID tests require runPhysicalHidTests=true and a host-side bluetoothctl connect",
    runPhysical
)
```

Acceptance requirement:

- Normal instrumented test runs must not fail because a physical laptop is not connected.
- Physical tests must require explicit user intent.

### 4.2 Physical test must accept a specific host address

Add an instrumentation argument:

```text
hidHostAddress=<LAPTOP_BT_ADDRESS>
```

The test should use this to select and validate the expected laptop/host.

Behavior:

- If `hidHostAddress` is provided:
  - use that address to find the bonded target host if possible,
  - only count `STATE_CONNECTED` from that address as success.
- If `hidHostAddress` is not provided:
  - either skip with a clear reason, or fall back to the old “first bonded computer” behavior only if documented as less deterministic.

Preferred v0.1 behavior:

- Require `hidHostAddress` for the physical send-report test.

Example:

```kotlin
val expectedHostAddress = InstrumentationRegistry.getArguments()
    .getString("hidHostAddress")
    ?.uppercase(Locale.US)

Assume.assumeTrue(
    "Physical HID tests require hidHostAddress=<laptop Bluetooth MAC>",
    !expectedHostAddress.isNullOrBlank()
)
```

Then:

```kotlin
override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
    if (state == BluetoothProfile.STATE_CONNECTED) {
        if (device.address.uppercase(Locale.US) == expectedHostAddress) {
            connectedDevice = device
            connectionLatch.countDown()
        }
    }
}
```

Acceptance requirement:

- The test must not accidentally pass because some unrelated bonded device connected.

### 4.3 Physical test must log clear host-side instructions

After HID app registration succeeds and before waiting for the connection, print/log an instruction like:

```text
HID profile registered. From the laptop, run:
bluetoothctl connect <PHONE_BT_ADDRESS>
```

The test should obtain the Android phone’s Bluetooth address if possible. If Android no longer exposes the local adapter MAC address on the device/API level, use a placeholder and document that the user must supply the phone address from Android Bluetooth settings or from `bluetoothctl devices`.

Minimum acceptable log:

```text
HID profile registered. Now initiate connection from the laptop:
bluetoothctl connect <PHONE_BT_ADDRESS>
Expected host address: <hidHostAddress>
```

Acceptance requirement:

- A human running the test can tell exactly when to run the host-side command.

### 4.4 Do not rely on `hid.connect(target)`

Remove the call to:

```kotlin
hid.connect(target)
```

from the required success path.

Acceptable options:

1. Remove it completely from `BluetoothHidSendReportTest`.
2. Keep it only as an explicitly logged best-effort call, but the test must still clearly require host-side connection.

Preferred behavior:

```kotlin
// Do not call hid.connect(target). On Linux/BlueZ host setups, the host
// must initiate the HID L2CAP connection after Android registers the HID app.
```

Acceptance requirement:

- The test must not describe `hid.connect(target)` as initiating the real connection.

### 4.5 Use a longer host-connect window

Increase the wait window from 45 seconds to 90 seconds.

```kotlin
connectionLatch.await(90, TimeUnit.SECONDS)
```

Failure message must be explicit:

```text
Host did not initiate HID connection within 90s. After the test logs that HID profile is registered, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop.
```

Acceptance requirement:

- Timeout failure explains the host-side action, not just “ensure laptop Bluetooth is on.”

### 4.6 Run as a single class

Document and prefer this command:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

Then, from the laptop during the test wait window:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Acceptance requirement:

- Docs must not tell users to rely on “run the whole suite and guess the T+50 second timing.”

## 5. App behavior requirement

The app’s user-facing behavior should acknowledge host-initiated connection reality.

### 5.1 `BluetoothService.connectDevice(...)`

If the app currently calls:

```kotlin
hid?.connect(device)
```

that may remain as best-effort, but the code and user messaging must not imply it is guaranteed to initiate the HID connection on Linux/BlueZ hosts.

Add or update log/UI messages around connect attempts:

```text
Attempted host connection. If the host does not connect automatically, initiate connection from the host Bluetooth menu or run bluetoothctl connect <phone-address> on Linux.
```

### 5.2 Auto-reconnect

Auto-reconnect may also call `hid?.connect(target)` as best-effort, but logs should make clear that host initiation may still be required.

Recommended wording:

```text
Auto-reconnect requested. Some hosts, especially Linux/BlueZ, must initiate the HID connection from the host side.
```

### 5.3 Pairing UI / Logs

Where practical, surface this in logs or troubleshooting UI:

```text
Linux host tip: after the phone registers as a Bluetooth HID device, run bluetoothctl connect <phone Bluetooth address> from the laptop.
```

Do not overload the main UI with too much text if it would make the app clunky. At minimum, update debug logs and documentation.

## 6. Documentation requirements

### 6.1 Add physical HID test documentation

Add a section to README or a dedicated doc file:

Recommended file:

```text
docs/PHYSICAL_HID_TESTING.md
```

It must include:

- Purpose of the physical HID test.
- Required hardware:
  - Android phone.
  - Laptop/host with Bluetooth.
  - Paired phone and laptop.
- Required instrumentation args:
  - `runPhysicalHidTests=true`
  - `hidHostAddress=<LAPTOP_BT_ADDRESS>`
- Exact Gradle command.
- Exact host-side `bluetoothctl connect <PHONE_BT_ADDRESS>` command.
- How to find phone and laptop Bluetooth addresses.
- Note that the host must initiate the HID connection.
- Note that `BluetoothHidDevice.connect(host)` from Android is best-effort and may not initiate L2CAP on Linux/BlueZ.
- Troubleshooting section.

### 6.2 Update release notes or test docs

If `docs/v0.1_RELEASE_NOTES.md` exists, update it to say physical HID tests are manual/host-assisted, not fully automated.

If it does not exist, do not create a full release notes file just for this patch unless the broader release process needs it. A dedicated physical test doc is enough for this task.

### 6.3 Update memory or developer notes if appropriate

If the repository has a `memory.md` or developer notes file, add a concise durable entry:

```text
Physical Android↔Linux HID send-report tests require host-initiated connection. After Android registers HID, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop. Do not rely on BluetoothHidDevice.connect(host) to initiate L2CAP.
```

Do not duplicate long debugging history; keep it actionable.

## 7. Test expectations

### 7.1 Unit/instrumented tests

The physical test itself is instrumented and hardware-dependent. It should skip unless explicitly enabled.

No JVM unit test can prove the real Bluetooth L2CAP host-initiated behavior. That must be validated manually.

### 7.2 Optional pure helper tests

If you extract a helper for reading instrumentation args, add a simple test if practical.

Examples:

```kotlin
PhysicalHidTestConfig.fromArgs(...)
```

Could validate:

- disabled when `runPhysicalHidTests` is missing,
- enabled when `runPhysicalHidTests=true`,
- requires `hidHostAddress`,
- normalizes MAC address uppercase.

This is optional but useful.

### 7.3 Manual validation

Manual validation must be documented.

Required sequence:

1. Ensure phone and laptop are already paired.
2. Run the single-class Gradle command with `runPhysicalHidTests=true` and `hidHostAddress=<LAPTOP_BT_ADDRESS>`.
3. Watch test logs for HID profile registration.
4. From laptop, run:
   ```bash
   bluetoothctl connect <PHONE_BT_ADDRESS>
   ```
5. Confirm Android receives `STATE_CONNECTED` for the expected host address.
6. Confirm keyboard/mouse HID reports are sent and observed on the laptop.
7. Record pass/fail and any host logs.

## 8. Suggested implementation locations

Likely files to inspect/change:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidSendReportTest.kt
app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt
README.md
docs/PHYSICAL_HID_TESTING.md
memory.md
```

Optional new file:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/PhysicalHidTestConfig.kt
```

Only add the optional config helper if it reduces duplication or improves clarity.

## 9. Acceptance criteria

This patch is complete when:

- [ ] `BluetoothHidSendReportTest` skips unless `runPhysicalHidTests=true`.
- [ ] `BluetoothHidSendReportTest` requires or strongly validates `hidHostAddress`.
- [ ] `BluetoothHidSendReportTest` no longer relies on `hid.connect(target)` as the connection mechanism.
- [ ] The test logs clear host-side `bluetoothctl connect <PHONE_BT_ADDRESS>` instructions after HID registration.
- [ ] The test waits at least 90 seconds for the host-initiated connection.
- [ ] The test failure message explains that the host must initiate connection.
- [ ] The recommended run command runs the physical test as a single class.
- [ ] App logs/UI clarify that host-side connection may be required.
- [ ] Docs explain the physical HID testing procedure.
- [ ] Normal instrumented test runs do not fail because the physical laptop is absent.
- [ ] Manual validation has been run or clearly marked as pending.
