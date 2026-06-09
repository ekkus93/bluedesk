# Android Bluetooth Keyboard/Mouse Physical HID Test Fix TODO

## Implementation rules

- This is a focused fix for broken physical Android phone ↔ laptop HID tests.
- Do not rewrite the Bluetooth service architecture.
- Do not hide the physical test by deleting it.
- Do not claim the physical test is automated if it still requires manual `bluetoothctl connect`.
- Make the real host-initiated workflow explicit in code, logs, and docs.
- Normal instrumented test runs must not fail just because a physical laptop is absent.

---

## Phase 1 — Confirm and preserve the memory.md finding

### Task 1.1 — Read the latest `memory.md` entry

- [x] Open `memory.md`.
- [x] Locate the entry about physical HID send-report tests.
- [x] Confirm it says the laptop/host must initiate HID connection.
- [x] Confirm it says `BluetoothHidDevice.connect(laptop)` does not initiate L2CAP for this setup.
- [x] Confirm the known host command is similar to:
  ```bash
  bluetoothctl connect <PHONE_BT_ADDRESS>
  ```

Acceptance criteria:

- [x] The implementation is based on the host-initiated connection model, not Android-initiated L2CAP.

### Task 1.2 — Add a concise durable note if needed

- [x] If `memory.md` does not already have a concise durable note, add one.
- [x] Keep it short and actionable.
- [x] Do not add long debug history.

Suggested note:

```text
Physical Android↔Linux HID send-report tests require host-initiated connection. After Android registers HID, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop. Do not rely on BluetoothHidDevice.connect(host) to initiate L2CAP.
```

Acceptance criteria:

- [x] Future agents will not repeat the Android-initiated connection mistake.

---

## Phase 2 — Gate the physical HID send-report test

### Task 2.1 — Add `runPhysicalHidTests` instrumentation argument

- [x] Open `BluetoothHidSendReportTest.kt`.
- [x] Read instrumentation args with `InstrumentationRegistry.getArguments()`.
- [x] Add:
  ```text
  runPhysicalHidTests=true
  ```
- [x] If missing or not true, skip the test with `Assume.assumeTrue(...)`.
- [x] The skip message must explain that a real phone/laptop and host-side `bluetoothctl connect` are required.

Suggested implementation:

```kotlin
val runPhysical = InstrumentationRegistry.getArguments()
    .getString("runPhysicalHidTests") == "true"

Assume.assumeTrue(
    "Physical HID tests require runPhysicalHidTests=true and host-side bluetoothctl connect",
    runPhysical
)
```

Acceptance criteria:

- [x] Normal `connectedDebugAndroidTest` does not fail because the physical laptop is absent.
- [x] Physical test requires explicit opt-in.

### Task 2.2 — Add `hidHostAddress` instrumentation argument

- [x] Add instrumentation arg:
  ```text
  hidHostAddress=<LAPTOP_BT_ADDRESS>
  ```
- [x] Normalize it with `uppercase(Locale.US)`.
- [x] Prefer requiring it for this physical test.
- [x] Skip with a clear reason if it is missing.
- [x] Use it to identify the expected laptop/host.

Suggested implementation:

```kotlin
val expectedHostAddress = InstrumentationRegistry.getArguments()
    .getString("hidHostAddress")
    ?.uppercase(Locale.US)

Assume.assumeTrue(
    "Physical HID tests require hidHostAddress=<laptop Bluetooth MAC>",
    !expectedHostAddress.isNullOrBlank()
)
```

Acceptance criteria:

- [x] The test does not accidentally connect to or pass with the wrong bonded device.

### Task 2.3 — Select target host deterministically

- [x] If the test needs a `BluetoothDevice` target, look it up by `hidHostAddress`.
- [x] Do not default silently to the first bonded device when `hidHostAddress` was provided.
- [x] If no bonded device matches `hidHostAddress`, skip or fail with a clear message:
  ```text
  Expected HID host <address> is not bonded. Pair the phone and laptop first.
  ```

Acceptance criteria:

- [x] The physical test targets the intended laptop.

---

## Phase 3 — Remove Android-initiated connection dependency

### Task 3.1 — Remove or downgrade `hid.connect(target)`

- [x] Find the section that calls:
  ```kotlin
  hid.connect(target)
  ```
- [x] Remove it from the required success path.
- [x] If kept, mark it explicitly as best-effort only.
- [x] Do not rely on its return value for test success.
- [x] Do not imply it initiates the laptop’s HID L2CAP connection.

Preferred code comment:

```kotlin
// Do not rely on BluetoothHidDevice.connect(target) here.
// On the Linux/BlueZ host setup used for physical testing, the host must
// initiate the HID L2CAP connection after Android registers the HID app.
```

Acceptance criteria:

- [x] The physical test waits for host-initiated connection, not Android-initiated connection.

### Task 3.2 — Accept only expected host connection

- [x] In `onConnectionStateChanged(...)`, check `state == BluetoothProfile.STATE_CONNECTED`.
- [x] If `hidHostAddress` is provided, require `device.address` to match it.
- [x] Only then set `connectedDevice` and count down the connection latch.
- [x] Log ignored connection events from other devices.

Suggested behavior:

```kotlin
if (state == BluetoothProfile.STATE_CONNECTED) {
    val address = device.address.uppercase(Locale.US)
    if (address == expectedHostAddress) {
        connectedDevice = device
        connectionLatch.countDown()
    } else {
        Log.w(TAG, "Ignoring HID connection from unexpected host $address")
    }
}
```

Acceptance criteria:

- [x] Test success requires the expected laptop to connect.

### Task 3.3 — Extend host connection timeout

- [x] Increase wait window from 45 seconds to 90 seconds.
- [x] Use a constant:
  ```kotlin
  private const val HOST_CONNECT_TIMEOUT_SECONDS = 90L
  ```
- [x] Failure message must mention host-initiated connection.

Acceptance criteria:

- [x] User has enough time to run the laptop-side command.
- [x] Timeout failure explains the real fix.

---

## Phase 4 — Add clear host-side instructions in test logs

### Task 4.1 — Log instructions after HID registration

- [x] After HID profile/app registration succeeds, log a message:
  ```text
  HID profile registered. From the laptop, run:
  bluetoothctl connect <PHONE_BT_ADDRESS>
  ```
- [x] Include expected host address:
  ```text
  Expected host: <hidHostAddress>
  ```
- [x] If the phone Bluetooth address is not programmatically available, use `<PHONE_BT_ADDRESS>` placeholder and explain how to find it in docs.

Acceptance criteria:

- [x] Test runner knows exactly when to run `bluetoothctl connect`.

### Task 4.2 — Improve timeout / skip messages

- [x] Replace vague timeout text like:
  ```text
  ensure laptop Bluetooth is on
  ```
- [x] Use explicit text:
  ```text
  Host did not initiate HID connection within 90s. After HID registration, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop.
  ```

Acceptance criteria:

- [x] Failure messages point to the correct host-side action.

### Task 4.3 — Recommend single-class test execution

- [x] Add a test comment or doc string saying this physical test should be run as a single class.
- [x] Do not rely on whole-suite timing.

Acceptance criteria:

- [x] Future runs do not depend on brittle T+50s timing after unrelated tests.

---

## Phase 5 — Document exact physical test procedure

### Task 5.1 — Create `docs/PHYSICAL_HID_TESTING.md`

- [ ] Create a new doc:
  ```text
  docs/PHYSICAL_HID_TESTING.md
  ```
- [ ] Include overview:
  - [ ] Android registers as HID device.
  - [ ] Laptop/host initiates the HID connection.
  - [ ] Android then sends reports.
- [ ] State clearly:
  ```text
  BluetoothHidDevice.connect(host) is best-effort and may not initiate L2CAP on Linux/BlueZ.
  ```

Acceptance criteria:

- [ ] The host-initiated test model is documented.

### Task 5.2 — Document prerequisites

Include:

- [ ] Android phone with app/test APK installed.
- [ ] Laptop/host with Bluetooth.
- [ ] Phone and laptop already paired/bonded.
- [ ] Bluetooth enabled on both devices.
- [ ] Linux/BlueZ host uses `bluetoothctl`.
- [ ] Laptop Bluetooth MAC address for `hidHostAddress`.
- [ ] Phone Bluetooth MAC address for `bluetoothctl connect`.

Acceptance criteria:

- [ ] User knows what hardware/state is required before running the test.

### Task 5.3 — Document exact Gradle command

Add:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

Acceptance criteria:

- [ ] Physical test is run directly as one class.

### Task 5.4 — Document host-side command

Add:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Also include the known remembered example if appropriate:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

Make clear that addresses may differ by device.

Acceptance criteria:

- [ ] The required host-side action is explicit.

### Task 5.5 — Document how to find addresses

Include examples:

```bash
bluetoothctl devices
bluetoothctl info <device-address>
```

Also mention Android Bluetooth settings if useful.

Acceptance criteria:

- [ ] User can find both laptop and phone Bluetooth addresses.

### Task 5.6 — Add troubleshooting section

Include common failure cases:

- [ ] Test skipped because `runPhysicalHidTests=true` missing.
- [ ] Test skipped because `hidHostAddress` missing.
- [ ] Expected host not bonded.
- [ ] Timeout waiting for host connection.
- [ ] Ran `bluetoothctl connect` too early/too late.
- [ ] Whole test suite timing is unreliable.
- [ ] Host connected to wrong device.
- [ ] Linux host cached stale pairing; remove/re-pair if needed.

Acceptance criteria:

- [ ] Failure modes are actionable.

---

## Phase 6 — Update app messaging around host-initiated connection

### Task 6.1 — Update `BluetoothService.connectDevice(...)` logs/messages

- [ ] Open `BluetoothService.kt`.
- [ ] Locate `connectDevice(...)`.
- [ ] If it calls `hid?.connect(device)`, keep only as best-effort.
- [ ] Add log/message:
  ```text
  Requested HID connection. If the host does not connect automatically, initiate connection from the host Bluetooth menu or run bluetoothctl connect <phone-address> on Linux.
  ```

Acceptance criteria:

- [ ] Logs do not imply Android can always initiate HID connection.

### Task 6.2 — Update auto-reconnect logs/messages

- [ ] Locate auto-reconnect path.
- [ ] If it calls `hid?.connect(target)`, mark as best-effort in comments/logs.
- [ ] Add Linux/BlueZ host-initiation hint.

Acceptance criteria:

- [ ] Auto-reconnect messaging matches actual host behavior.

### Task 6.3 — Optional UI hint

- [ ] If there is a low-risk place in Pairing/Connection UI, add a concise host-side hint.
- [ ] Do not clutter the UI.
- [ ] It is acceptable to keep this in logs/docs only if UI changes would be intrusive.

Acceptance criteria:

- [ ] User-facing diagnostics are less misleading.

---

## Phase 7 — Optional physical test config helper

### Task 7.1 — Add helper only if useful

Optional file:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/PhysicalHidTestConfig.kt
```

Use it only if it simplifies `BluetoothHidSendReportTest.kt`.

Possible fields:

```kotlin
data class PhysicalHidTestConfig(
    val enabled: Boolean,
    val hostAddress: String?
)
```

Acceptance criteria:

- [ ] Helper reduces duplication or improves clarity.
- [ ] Do not add it if it is unnecessary ceremony.

### Task 7.2 — Add helper tests if practical

If helper is pure/testable:

- [ ] Test disabled when `runPhysicalHidTests` missing.
- [ ] Test enabled when `runPhysicalHidTests=true`.
- [ ] Test missing host address.
- [ ] Test MAC address normalization.

Acceptance criteria:

- [ ] Config parsing is reliable if extracted.

---

## Phase 8 — Run validation

### Task 8.1 — Run normal instrumented tests without physical opt-in

Run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected:

- [ ] Physical HID send-report test is skipped, not failed.
- [ ] Non-hardware tests pass or unrelated failures are documented.

Acceptance criteria:

- [ ] Normal test suite is not blocked by absent physical laptop.

### Task 8.2 — Run physical HID send-report test as a single class

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

During the wait window on the laptop:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Record:

- [ ] Android device model.
- [ ] Laptop OS.
- [ ] Phone Bluetooth address.
- [ ] Laptop Bluetooth address.
- [ ] Whether `STATE_CONNECTED` was observed.
- [ ] Whether keyboard report worked.
- [ ] Whether mouse report worked.
- [ ] Any host-side errors.

Acceptance criteria:

- [ ] Physical test follows the documented host-initiated workflow.

### Task 8.3 — Run relevant JVM tests

Run:

```bash
./gradlew test
```

Acceptance criteria:

- [ ] No JVM regressions from the test/config/doc changes.

---

## Phase 9 — Final acceptance checklist

Do not mark this fix complete until all are true:

- [ ] `BluetoothHidSendReportTest` is opt-in via `runPhysicalHidTests=true`.
- [ ] `BluetoothHidSendReportTest` uses or requires `hidHostAddress`.
- [ ] Test success requires connection from the expected host.
- [ ] Test no longer relies on Android-side `hid.connect(target)` to initiate L2CAP.
- [ ] Test logs say the laptop must run `bluetoothctl connect <PHONE_BT_ADDRESS>`.
- [ ] Test waits 90 seconds or another documented sufficient window.
- [ ] Timeout message explains host-initiated connection.
- [ ] Physical test docs exist.
- [ ] App logs/messages describe `hid.connect(...)` as best-effort where relevant.
- [ ] Normal instrumented test run skips the physical test unless explicitly enabled.
- [ ] Physical single-class test command is documented.
- [ ] Manual validation result is recorded, or clearly marked pending.
