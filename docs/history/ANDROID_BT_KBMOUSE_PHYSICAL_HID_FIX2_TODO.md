# Android Bluetooth Keyboard/Mouse Physical HID Fix 2 TODO

## Implementation rules

- This TODO is focused on the physical Android phone ↔ laptop HID test failure.
- Do not rewrite unrelated app architecture.
- Do not delete the physical test.
- Do not use unconditional `@Ignore` to hide it.
- Do not claim Ubuntu/BlueZ is the cause until the test follows the host-initiated workflow and host-side evidence is collected.
- Normal instrumented tests must not fail just because a physical laptop is absent.

---

## Phase 1 — Reconcile `memory.md` with the actual test

### Task 1.1 — Read the relevant `memory.md` entries

- [ ] Open `memory.md`.
- [ ] Locate the earlier entry saying the laptop/host must initiate HID connection.
- [ ] Locate the latest entry blaming Ubuntu/BlueZ.
- [ ] Preserve the durable technical finding:
  - [ ] Android registers HID.
  - [ ] Laptop initiates with `bluetoothctl connect <PHONE_BT_ADDRESS>`.
  - [ ] `BluetoothHidDevice.connect(host)` is not reliable as the L2CAP initiator for this setup.

Acceptance criteria:

- [ ] Implementation follows host-initiated connection, not Android-initiated assumptions.
- [ ] Latest Ubuntu/BlueZ blame is treated as unproven until the corrected test and host logs support it.

### Task 1.2 — Add a corrective memory note

- [ ] Add a concise note to `memory.md` if not already present.
- [ ] Say not to blame Ubuntu/BlueZ until the corrected test harness is used.
- [ ] Mention required evidence: host-side `bluetoothctl connect`, Android `STATE_CONNECTED` result, `journalctl`, and `btmon`.

Suggested note:

```text
Physical HID failures must not be blamed solely on Ubuntu/BlueZ until BluetoothHidSendReportTest uses the host-initiated workflow: runPhysicalHidTests=true, hidHostAddress set, Android HID profile registered, laptop runs bluetoothctl connect <PHONE_BT_ADDRESS>, and journalctl/btmon evidence is collected. BluetoothHidDevice.connect(host) is best-effort only for this setup.
```

Acceptance criteria:

- [ ] Future agents do not overwrite the host-initiated finding with unsupported system-blame claims.

---

## Phase 2 — Make `BluetoothHidSendReportTest` explicitly opt-in

### Task 2.1 — Add `runPhysicalHidTests` argument

- [ ] Open:
  ```text
  app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidSendReportTest.kt
  ```
- [ ] Read instrumentation arguments with `InstrumentationRegistry.getArguments()`.
- [ ] Add support for:
  ```text
  runPhysicalHidTests=true
  ```
- [ ] If missing or not true, skip with `Assume.assumeTrue(...)`.
- [ ] The skip message must mention:
  - [ ] real phone required,
  - [ ] laptop/host required,
  - [ ] host-side `bluetoothctl connect` required.

Acceptance criteria:

- [ ] `./gradlew :app:connectedDebugAndroidTest` does not fail because no physical host connected.
- [ ] The physical test only runs when explicitly enabled.

### Task 2.2 — Add `hidHostAddress` argument

- [ ] Add support for:
  ```text
  hidHostAddress=<LAPTOP_BT_ADDRESS>
  ```
- [ ] Normalize with `uppercase(Locale.US)`.
- [ ] Require it for physical test execution.
- [ ] If missing, skip with a clear message:
  ```text
  Physical HID test requires hidHostAddress=<laptop Bluetooth MAC>.
  ```

Acceptance criteria:

- [ ] Test does not silently pick an arbitrary bonded device.

### Task 2.3 — Select expected host by address

- [ ] Use `hidHostAddress` to locate the bonded laptop/host.
- [ ] Do not default to first bonded device when `hidHostAddress` is provided.
- [ ] If no bonded device matches, skip or fail clearly:
  ```text
  Expected HID host <address> is not bonded. Pair the phone and laptop first.
  ```

Acceptance criteria:

- [ ] Physical test targets the intended laptop.

---

## Phase 3 — Remove reliance on Android-side `hid.connect(target)`

### Task 3.1 — Find existing `hid.connect(target)` call

- [ ] Search in `BluetoothHidSendReportTest.kt` for:
  ```kotlin
  hid.connect(target)
  ```
- [ ] Identify whether the test treats it as the connection initiator.

Acceptance criteria:

- [ ] The old Android-initiated assumption is located.

### Task 3.2 — Remove from required success path

- [ ] Remove `hid.connect(target)` from the required pass path.
- [ ] Or keep only as clearly labeled best-effort, not used for success/failure.
- [ ] Add a code comment:
  ```kotlin
  // On the Linux/BlueZ physical test setup, the host must initiate HID L2CAP:
  // bluetoothctl connect <PHONE_BT_ADDRESS>
  // BluetoothHidDevice.connect(target) is best-effort only.
  ```

Acceptance criteria:

- [ ] Test pass/fail no longer depends on Android initiating L2CAP.

### Task 3.3 — Accept only `STATE_CONNECTED` from expected host

- [ ] Update `onConnectionStateChanged(...)`.
- [ ] Check `state == BluetoothProfile.STATE_CONNECTED`.
- [ ] Compare `device.address.uppercase(Locale.US)` to `hidHostAddress`.
- [ ] Only matching host counts down the latch.
- [ ] Log ignored connections from unexpected devices.

Acceptance criteria:

- [ ] Test cannot pass because the wrong bonded device connected.

---

## Phase 4 — Improve wait window and failure messages

### Task 4.1 — Increase timeout

- [ ] Replace 45-second wait with at least 90 seconds.
- [ ] Add constant:
  ```kotlin
  private const val HOST_CONNECT_TIMEOUT_SECONDS = 90L
  ```

Acceptance criteria:

- [ ] Human has enough time to run host-side command.

### Task 4.2 — Log host command after HID registration

- [ ] After HID profile registration succeeds, log:
  ```text
  HID profile registered.
  From the laptop, run:
  bluetoothctl connect <PHONE_BT_ADDRESS>
  Expected host: <hidHostAddress>
  ```
- [ ] Ensure this log appears when Android is actually ready for the laptop to connect.
- [ ] If phone address cannot be read, use `<PHONE_BT_ADDRESS>` and point to docs.

Acceptance criteria:

- [ ] User knows exactly when and what to run on laptop.

### Task 4.3 — Fix timeout message

- [ ] Replace vague timeout messages.
- [ ] New timeout must say:
  ```text
  Host did not initiate HID connection within 90 seconds. After Android logs HID registration, run bluetoothctl connect <PHONE_BT_ADDRESS> from the laptop.
  ```

Acceptance criteria:

- [ ] Timeout error identifies the real missing action.

---

## Phase 5 — Document single-class physical test flow

### Task 5.1 — Create `docs/PHYSICAL_HID_TESTING.md`

- [ ] Create:
  ```text
  docs/PHYSICAL_HID_TESTING.md
  ```
- [ ] Explain:
  - [ ] Android is the HID peripheral/device.
  - [ ] Laptop is the HID host.
  - [ ] Android registers HID.
  - [ ] Laptop initiates connection.
  - [ ] Android sends reports after `STATE_CONNECTED`.

Acceptance criteria:

- [ ] Physical workflow is documented accurately.

### Task 5.2 — Add exact Gradle command

Document:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

Acceptance criteria:

- [ ] User runs the physical test as a single class.

### Task 5.3 — Add exact host-side command

Document:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Also mention the known remembered example if appropriate:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

Acceptance criteria:

- [ ] Host-side command is explicit.

### Task 5.4 — Document address discovery

Include:

```bash
bluetoothctl devices
bluetoothctl info <device-address>
```

Explain:

- [ ] how to identify the Android phone address,
- [ ] how to identify the laptop/host address,
- [ ] how to confirm devices are bonded/trusted.

Acceptance criteria:

- [ ] User can fill in both required addresses.

### Task 5.5 — Document why whole-suite timing is unreliable

- [ ] Explain not to rely on full `connectedDebugAndroidTest` timing.
- [ ] Explain JUnit order/timing can change.
- [ ] Explain to run the physical class directly.

Acceptance criteria:

- [ ] No more T+50-second brittle workflow.

---

## Phase 6 — Update app logs/messages

### Task 6.1 — Update manual connect messaging

- [ ] Open `BluetoothService.kt`.
- [ ] Locate manual connect path / `connectDevice(...)`.
- [ ] Find logs/messages around `hid?.connect(device)`.
- [ ] Update wording so `hid.connect(...)` is best-effort only.
- [ ] Add Linux/BlueZ hint:
  ```text
  If the host does not connect automatically, initiate connection from the host Bluetooth menu or run bluetoothctl connect <phone-address> on Linux.
  ```

Acceptance criteria:

- [ ] App no longer implies Android-side `hid.connect(...)` guarantees host connection.

### Task 6.2 — Update auto-reconnect messaging

- [ ] Locate auto-reconnect logic.
- [ ] Find logs/messages around `hid?.connect(target)`.
- [ ] Update wording to say host-side initiation may still be required.
- [ ] Keep `hid.connect(...)` as best-effort if desired.

Acceptance criteria:

- [ ] Auto-reconnect logs match known behavior.

### Task 6.3 — Add troubleshooting hint if low-risk

- [ ] Add concise Linux host tip in logs or troubleshooting UI if low-risk.
- [ ] Avoid cluttering main UI.
- [ ] Documentation-only is acceptable if UI change is intrusive.

Acceptance criteria:

- [ ] User can discover host-side connection requirement.

---

## Phase 7 — Require evidence before blaming Ubuntu/BlueZ

### Task 7.1 — Add evidence checklist to docs

In `docs/PHYSICAL_HID_TESTING.md`, add a section:

```text
Before blaming Ubuntu/BlueZ, collect:
```

Include:

```bash
bluetoothctl --version
uname -a
bluetoothctl info <PHONE_BT_ADDRESS>
bluetoothctl info <LAPTOP_BT_ADDRESS>
journalctl -u bluetooth --since "10 minutes ago"
sudo btmon
```

Acceptance criteria:

- [ ] Docs require evidence before system blame.

### Task 7.2 — Define Ubuntu/BlueZ failure evidence

Document that Ubuntu/BlueZ blame is plausible only if:

- [ ] Android HID registration succeeds.
- [ ] Laptop runs `bluetoothctl connect <PHONE_BT_ADDRESS>` during the wait window.
- [ ] Android does not receive `STATE_CONNECTED`.
- [ ] `journalctl` or `btmon` shows BlueZ rejecting/failing the HID/L2CAP connection.
- [ ] The same corrected test works on another host or BlueZ version, if available.

Acceptance criteria:

- [ ] Failure diagnosis is evidence-based.

### Task 7.3 — Add warning to release/test notes

- [ ] If release notes or test docs discuss failures, add:
  ```text
  Do not conclude Ubuntu/BlueZ is the root cause until the corrected host-initiated test procedure has been followed and host-side logs have been collected.
  ```

Acceptance criteria:

- [ ] Future agents cannot prematurely blame Ubuntu.

---

## Phase 8 — Optional config helper and tests

### Task 8.1 — Add `PhysicalHidTestConfig` only if useful

Optional:

```text
app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/PhysicalHidTestConfig.kt
```

Possible structure:

```kotlin
data class PhysicalHidTestConfig(
    val enabled: Boolean,
    val hostAddress: String?
)
```

Use only if it makes `BluetoothHidSendReportTest.kt` clearer.

Acceptance criteria:

- [ ] Helper reduces test complexity if added.

### Task 8.2 — Add config tests if helper exists

If helper is added:

- [ ] Test disabled when `runPhysicalHidTests` missing.
- [ ] Test enabled when `runPhysicalHidTests=true`.
- [ ] Test missing host address invalid/skipped.
- [ ] Test MAC address uppercase normalization.

Acceptance criteria:

- [ ] Config parsing is covered if extracted.

---

## Phase 9 — Validation

### Task 9.1 — Normal instrumented run

Run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected:

- [ ] physical HID send-report test is skipped unless explicitly enabled,
- [ ] no physical laptop required,
- [ ] unrelated instrumented tests still pass or failures are documented.

Acceptance criteria:

- [ ] Normal instrumented test run is not broken by physical host dependency.

### Task 9.2 — Physical single-class run

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
```

After Android logs HID registration, run on laptop:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Record:

- [ ] Android phone model.
- [ ] Android version.
- [ ] Laptop OS/version.
- [ ] BlueZ version.
- [ ] Phone Bluetooth address.
- [ ] Laptop Bluetooth address.
- [ ] Whether `STATE_CONNECTED` occurred.
- [ ] Whether keyboard report was observed.
- [ ] Whether mouse report was observed.
- [ ] If failed, collect `journalctl` and `btmon`.

Acceptance criteria:

- [ ] Test result is tied to the corrected host-initiated procedure.

### Task 9.3 — JVM tests

Run:

```bash
./gradlew test
```

Acceptance criteria:

- [ ] No JVM regressions from config/helper changes.

---

## Phase 10 — Final acceptance checklist

Do not mark this complete until all are true:

- [ ] `BluetoothHidSendReportTest` skips unless `runPhysicalHidTests=true`.
- [ ] `BluetoothHidSendReportTest` requires or validates `hidHostAddress`.
- [ ] Test targets expected bonded host by address.
- [ ] Test success only counts `STATE_CONNECTED` from expected host.
- [ ] `hid.connect(target)` is removed from the required success path or explicitly best-effort only.
- [ ] Test logs `bluetoothctl connect <PHONE_BT_ADDRESS>` after HID registration.
- [ ] Test waits at least 90 seconds for host initiation.
- [ ] Timeout message explains host-initiated connection.
- [ ] `docs/PHYSICAL_HID_TESTING.md` exists.
- [ ] Docs include exact Gradle command.
- [ ] Docs include exact host-side `bluetoothctl` command.
- [ ] Docs include address discovery.
- [ ] Docs include evidence checklist before blaming Ubuntu/BlueZ.
- [ ] `BluetoothService` logs/messages describe `hid.connect(...)` as best-effort.
- [ ] Normal instrumented test run skips the physical test without failure.
- [ ] Physical single-class test result is recorded or clearly marked pending.
