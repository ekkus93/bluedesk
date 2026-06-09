# Android Bluetooth Keyboard/Mouse Physical HID Fix 3 TODO

## Implementation rules

- This is a narrow Fix 3 patch for remaining physical HID test failures.
- Do not rewrite Bluetooth service architecture.
- Do not delete or hide the physical test.
- Do not use `BluetoothAdapter.address` as the trusted phone address in physical-test instructions.
- Do not confuse laptop/controller address with phone/remote-device address.
- Do not blame Ubuntu/BlueZ without host-side evidence.
- Normal instrumented tests must still skip physical HID tests unless explicitly enabled.

---

## Phase 1 — Fix address model in the physical HID test

### Task 1.1 — Add `hidPhoneAddress` instrumentation arg

- [ ] Open:
  ```text
  app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidSendReportTest.kt
  ```
- [ ] Add support for:
  ```text
  hidPhoneAddress=<PHONE_BT_ADDRESS>
  ```
- [ ] Read from:
  ```kotlin
  InstrumentationRegistry.getArguments()
  ```
- [ ] Normalize with:
  ```kotlin
  uppercase(Locale.US)
  ```
- [ ] Reject blank values.
- [ ] When `runPhysicalHidTests=true`, require `hidPhoneAddress`.

Acceptance criteria:

- [ ] Physical test cannot run without an explicit phone address.
- [ ] Test no longer depends on `BluetoothAdapter.address` to know the phone address.

### Task 1.2 — Keep `hidHostAddress` as the expected laptop address

- [ ] Preserve existing `hidHostAddress=<LAPTOP_BT_ADDRESS>` behavior.
- [ ] Continue using `hidHostAddress` to identify the expected bonded host.
- [ ] Continue accepting only `STATE_CONNECTED` from `hidHostAddress`.
- [ ] Do not use `hidPhoneAddress` for incoming-connection validation.

Acceptance criteria:

- [ ] `hidHostAddress` and `hidPhoneAddress` have separate roles.

### Task 1.3 — Add clear missing-arg messages

If `hidPhoneAddress` is missing, skip with:

```text
Physical HID test requires hidPhoneAddress=<Android phone Bluetooth MAC>. Find it from the laptop with bluetoothctl devices.
```

If `hidHostAddress` is missing, skip with:

```text
Physical HID test requires hidHostAddress=<laptop/controller Bluetooth MAC>. Find it with bluetoothctl show.
```

Acceptance criteria:

- [ ] Skip messages tell the user exactly how to find the missing address.

---

## Phase 2 — Remove unreliable phone-address logging

### Task 2.1 — Remove `BluetoothAdapter.address` from host-side command text

- [ ] Search `BluetoothHidSendReportTest.kt` for:
  ```kotlin
  adapter.address
  ```
- [ ] Do not use it to build:
  ```text
  bluetoothctl connect ...
  ```
- [ ] Replace with the explicit `hidPhoneAddress` arg.

Acceptance criteria:

- [ ] Test never tells the user to run `bluetoothctl connect ${adapter.address}`.

### Task 2.2 — Log both addresses after HID registration

After HID profile registration succeeds, log:

```text
HID profile registered.

Expected host/laptop address:
<hidHostAddress>

From the laptop, connect to this Android phone:
bluetoothctl connect <hidPhoneAddress>
```

Acceptance criteria:

- [ ] User sees both address roles clearly.
- [ ] The `bluetoothctl connect` command uses the phone address.

### Task 2.3 — Fix timeout message

Timeout must say:

```text
Host did not initiate HID connection within 90 seconds. After Android logs HID registration, run bluetoothctl connect <hidPhoneAddress> from the laptop.
```

Acceptance criteria:

- [ ] Timeout message does not use `adapter.address`.
- [ ] Timeout message points to the correct phone address.

### Task 2.4 — Preserve 90-second wait

- [ ] Keep host-connect wait at least 90 seconds.
- [ ] Prefer a named constant:
  ```kotlin
  private const val HOST_CONNECT_TIMEOUT_SECONDS = 90L
  ```

Acceptance criteria:

- [ ] Wait window remains long enough for manual host initiation.

---

## Phase 3 — Fix app logs/messages in `BluetoothService`

### Task 3.1 — Fix manual connect message

- [x] Open:
  ```text
  app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt
  ```
- [x] Locate manual connect path / `connectDevice(...)`.
- [x] Find messages around:
  ```kotlin
  hid?.connect(device)
  ```
- [x] Remove any message equivalent to:
  ```text
  bluetoothctl connect ${device.address}
  ```
- [x] Replace with:
  ```text
  Requested HID connection. Some Linux/BlueZ hosts must initiate the HID connection from the host side. On Linux, run bluetoothctl connect <this phone's Bluetooth address> from the laptop.
  ```

Acceptance criteria:

- [x] App no longer tells Linux users to connect to the host/laptop address.

### Task 3.2 — Fix auto-reconnect message

- [x] Locate auto-reconnect logic.
- [x] Find messages around:
  ```kotlin
  hid?.connect(target)
  ```
- [x] Make clear that `BluetoothHidDevice.connect(...)` is best-effort only.
- [x] Use:
  ```text
  Some Linux/BlueZ hosts still require the host to initiate the HID connection with bluetoothctl connect <this phone's Bluetooth address>.
  ```

Acceptance criteria:

- [x] Auto-reconnect logs no longer overstate Android-side connection ability.

### Task 3.3 — Avoid false “connected” wording

- [x] Search logs/messages for misleading phrases like:
  - [x] `Immediate auto-connect`
  - [x] `hid.connect(...) immediate`
  - [x] `connected` before connection callback
- [x] Reword to:
  - [x] `best-effort connect request`
  - [x] `connection requested`
  - [x] `waiting for host connection`

Acceptance criteria:

- [x] Logs distinguish request from actual connection.

---

## Phase 4 — Fix physical HID documentation

### Task 4.1 — Update `docs/PHYSICAL_HID_TESTING.md`

- [ ] Open:
  ```text
  docs/PHYSICAL_HID_TESTING.md
  ```
- [ ] Add or update a section:
  ```markdown
  ## Bluetooth address roles
  ```
- [ ] Include this mapping:

| Name | Meaning | Used for | How to find |
|---|---|---|---|
| `hidHostAddress` | Laptop/local controller address | Android validates incoming `STATE_CONNECTED` device | `bluetoothctl show` |
| `hidPhoneAddress` | Android phone/remote paired device address | Laptop runs `bluetoothctl connect` against this address | `bluetoothctl devices` |

Acceptance criteria:

- [ ] Docs no longer tell users to find the laptop address with `bluetoothctl devices`.

### Task 4.2 — Correct address-discovery instructions

Document:

```bash
# Laptop/controller address
bluetoothctl show

# Phone/remote paired device address
bluetoothctl devices
bluetoothctl info <PHONE_BT_ADDRESS>
```

Acceptance criteria:

- [ ] User can correctly identify both addresses.

### Task 4.3 — Update Gradle command

Replace old command with:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Acceptance criteria:

- [ ] Docs include both required addresses.

### Task 4.4 — Update concrete example

If using remembered addresses, show:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=E8:FB:1C:25:E4:C2 \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=8C:6A:3B:5E:D3:48
```

Then:

```bash
bluetoothctl connect 8C:6A:3B:5E:D3:48
```

Acceptance criteria:

- [ ] Example makes clear that `bluetoothctl connect` uses the phone address.

### Task 4.5 — Remove misleading BlueZ wording

- [ ] Review any claim that says or implies the laptop is the HID peripheral.
- [ ] Reword so:
  - [ ] Android phone is HID peripheral/device.
  - [ ] Laptop is HID host.
  - [ ] BlueZ may be failing/rejecting the HID host connection only if logs prove it.

Acceptance criteria:

- [ ] Docs use correct HID roles.

---

## Phase 5 — Add evidence checklist before blaming Ubuntu/BlueZ

### Task 5.1 — Add evidence commands

In `docs/PHYSICAL_HID_TESTING.md`, add:

```bash
bluetoothctl --version
uname -a
bluetoothctl show
bluetoothctl devices
bluetoothctl info <PHONE_BT_ADDRESS>
journalctl -u bluetooth --since "10 minutes ago"
sudo btmon
```

Acceptance criteria:

- [ ] Docs tell users exactly what to collect before blaming host OS.

### Task 5.2 — Define when Ubuntu/BlueZ blame is plausible

Document that Ubuntu/BlueZ is plausible only if:

- [ ] Android logs HID profile registration.
- [ ] Test logs correct `hidHostAddress`.
- [ ] Test logs correct `hidPhoneAddress`.
- [ ] Laptop runs `bluetoothctl connect <hidPhoneAddress>` during wait window.
- [ ] Android does not receive `STATE_CONNECTED`.
- [ ] `journalctl` or `btmon` shows host-side rejection/failure.

Acceptance criteria:

- [ ] Diagnosis is evidence-based, not assumed.

### Task 5.3 — Add failure report template

Add a short template:

```markdown
## Failure report template

- Android device:
- Android version:
- Laptop OS:
- BlueZ version:
- hidHostAddress from `bluetoothctl show`:
- hidPhoneAddress from `bluetoothctl devices`:
- Exact Gradle command:
- Exact `bluetoothctl connect` command:
- Did Android log HID registration?
- Did Android receive STATE_CONNECTED?
- journalctl excerpt:
- btmon excerpt:
```

Acceptance criteria:

- [ ] Future failure reports contain useful data.

---

## Phase 6 — Update `memory.md`

### Task 6.1 — Correct overclaim from Fix 2

- [ ] Open `memory.md`.
- [ ] Find any statement equivalent to:
  ```text
  Physical HID test infrastructure properly implements host-initiated connection workflow. All verification complete.
  ```
- [ ] Replace or amend it with a more accurate statement.

Required meaning:

```text
Fix 2 partially corrected the physical HID test by adding runPhysicalHidTests, hidHostAddress, host-initiated wait, and removing hid.connect(target) from the required success path. Fix 3 was needed because the test used BluetoothAdapter.address for the phone address, docs confused laptop vs phone address discovery, app logs could show the wrong bluetoothctl connect address, and evidence-before-BlueZ-blame docs were incomplete.
```

Acceptance criteria:

- [ ] `memory.md` no longer says verification is complete unless physical Fix 3 validation really passed.

### Task 6.2 — Preserve durable address rule

Add concise rule:

```text
Physical HID tests use two addresses: hidHostAddress is the laptop/controller address from bluetoothctl show; hidPhoneAddress is the Android phone address from bluetoothctl devices. The laptop runs bluetoothctl connect <hidPhoneAddress>. Android validates STATE_CONNECTED from <hidHostAddress>.
```

Acceptance criteria:

- [ ] Future agents do not confuse the two addresses.

---

## Phase 7 — Validation

### Task 7.1 — Normal instrumented test path

Run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected:

- [ ] physical HID test skips unless `runPhysicalHidTests=true`,
- [ ] no physical host required,
- [ ] unrelated tests pass or failures are documented.

Acceptance criteria:

- [ ] Normal test path is not broken by physical test requirements.

### Task 7.2 — Physical single-class path

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Then from the laptop:

```bash
bluetoothctl connect <PHONE_BT_ADDRESS>
```

Record:

- [ ] Android device model.
- [ ] Android version.
- [ ] Laptop OS/version.
- [ ] BlueZ version.
- [ ] `hidHostAddress`.
- [ ] `hidPhoneAddress`.
- [ ] Whether HID profile registration logged.
- [ ] Whether `STATE_CONNECTED` was received.
- [ ] Whether keyboard report was observed.
- [ ] Whether mouse report was observed.
- [ ] If failed, `journalctl` excerpt.
- [ ] If failed, `btmon` excerpt.

Acceptance criteria:

- [ ] Physical result is tied to the corrected two-address workflow.

### Task 7.3 — Documentation walkthrough

- [ ] Follow the docs exactly.
- [ ] Confirm `bluetoothctl show` is used for laptop/controller address.
- [ ] Confirm `bluetoothctl devices` is used for phone address.
- [ ] Confirm Gradle command includes both addresses.
- [ ] Confirm host-side command uses phone address.

Acceptance criteria:

- [ ] A user following docs will not swap the addresses.

---

## Phase 8 — Final acceptance checklist

Do not mark this Fix 3 complete until all are true:

- [ ] `hidPhoneAddress` instrumentation arg exists.
- [ ] `hidPhoneAddress` is required when `runPhysicalHidTests=true`.
- [ ] `hidPhoneAddress` is used in every logged `bluetoothctl connect ...` command.
- [ ] `BluetoothAdapter.address` is not used as the phone address in physical-test instructions.
- [ ] `hidHostAddress` remains the expected laptop/controller address for validating `STATE_CONNECTED`.
- [ ] Docs say laptop/controller address comes from `bluetoothctl show`.
- [ ] Docs say phone/remote address comes from `bluetoothctl devices`.
- [ ] Gradle command in docs includes both addresses.
- [ ] App logs do not say `bluetoothctl connect ${device.address}` when `${device.address}` is the host address.
- [ ] Evidence checklist exists before blaming Ubuntu/BlueZ.
- [ ] Failure report template exists.
- [ ] `memory.md` no longer overclaims Fix 2 completion.
- [ ] Normal instrumented test path skips physical test unless explicitly enabled.
- [ ] Physical single-class test path has been run or clearly marked pending with reason.
