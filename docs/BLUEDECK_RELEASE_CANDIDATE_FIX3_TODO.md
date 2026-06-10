# BlueDeck Release Candidate Fix 3 TODO

## Implementation rules

- This is a narrow release-candidate cleanup pass.
- Do not redo Fix 2.
- Do not rename package, namespace, or application ID.
- Do not change splash duration from `1800L`.
- Do not change tagline wording: keep `The handy keyboard and mouse`.
- Do not redesign UI.
- Do not rewrite Bluetooth services.
- Do not rewrite HID descriptors.
- Do not delete physical HID tests.
- Do not add broad suppressions.

---

## Phase 1 — Fix permission callback semantics

### Task 1.1 — Find callback sites

- [x] Open `MainActivity.kt`.
- [x] Locate startup permission launcher callback.
- [x] Locate `onStartupPermissionResult(...)` or equivalent.
- [x] Open `SettingsScreen.kt`.
- [x] Locate BLE HOGP toggle permission launcher callback.
- [x] Identify all code that checks `RequestMultiplePermissions` callback maps against a full required permission list.

Acceptance criteria:

- [x] All partial-callback-risk sites are identified.

### Task 1.2 — Add full-state permission checker

- [x] Add `PermissionGrantChecker` or equivalent Android-facing helper.
- [x] Implement:
  - [x] `hasAll(context, permissions)`,
  - [x] `missing(context, permissions)`.
- [x] Use `ContextCompat.checkSelfPermission(...)`.
- [x] Do not treat callback result maps as complete grant state.
- [x] Keep pure permission planning in `PermissionPolicy` / planners.

Acceptance criteria:

- [x] Full current OS permission state can be checked after any callback.

### Task 1.3 — Fix MainActivity startup callback

- [x] In startup permission callback, ignore callback map as full state.
- [x] Re-check all permissions from the current startup plan using `PermissionGrantChecker`.
- [x] If all required permissions are granted:
  - [x] start planned backend,
  - [x] mark startup permission flow resolved.
- [x] If BLE permissions are still missing:
  - [x] persist `useBleHogp=false`,
  - [x] fall back to Classic if Classic permissions are available,
  - [x] show/log `BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.`
- [x] If Classic permissions are still missing:
  - [x] do not start Classic,
  - [x] show/log required permission message,
  - [x] resolve startup permission flow after handling denial.

Acceptance criteria:

- [x] A callback containing only `BLUETOOTH_ADVERTISE=true` does not falsely fail when `BLUETOOTH_CONNECT` was already granted.

### Task 1.4 — Fix SettingsScreen BLE toggle callback

- [x] In BLE toggle permission callback, re-check full BLE required permission state.
- [x] Persist `useBleHogp=true` only if full state grants:
  - [x] `BLUETOOTH_CONNECT`,
  - [x] `BLUETOOTH_ADVERTISE`.
- [x] If still missing, persist/keep `useBleHogp=false`.
- [x] Show/log connect/advertise denial message.
- [x] Do not use callback map as full state.

Acceptance criteria:

- [x] BLE toggle succeeds when only the newly requested missing permission is returned as granted and previously granted permissions remain granted.

### Task 1.5 — Add tests for partial callback maps

Add tests for:

- [x] startup BLE plan requires connect + advertise.
- [x] connect already granted, advertise callback returns true => BLE allowed.
- [x] advertise callback returns false => BLE fallback.
- [x] callback map missing an already-granted permission does not imply denial.
- [x] Settings BLE toggle partial callback succeeds if full state is granted.
- [x] Settings BLE toggle partial callback fails if full state is still missing.

Acceptance criteria:

- [x] Tests would fail under the old callback-map-as-full-state logic.

---

## Phase 2 — Fix BluetoothService receiver registration compatibility

### Task 2.1 — Find unsafe receiver registration

- [x] Open `BluetoothService.kt`.
- [x] Search for:
  ```kotlin
  registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
  ```
- [x] Identify any unguarded receiver flag overloads used on minSdk paths.

Acceptance criteria:

- [x] Unsafe receiver registration sites are identified.

### Task 2.2 — Replace with compatible registration

Use preferred pattern:

```kotlin
ContextCompat.registerReceiver(
    this,
    receiver,
    filter,
    ContextCompat.RECEIVER_NOT_EXPORTED,
)
```

or SDK-gated fallback:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
} else {
    @Suppress("DEPRECATION")
    registerReceiver(receiver, filter)
}
```

- [x] Apply to `BluetoothService`.
- [x] Apply to any similar unguarded receiver registrations if found.
- [x] Keep receiver unregister path safe.

Acceptance criteria:

- [x] No API 33-only receiver overload is called unguarded on API 26–32.

### Task 2.3 — Validate

- [x] Build compiles.
- [x] Lint does not flag unsafe receiver registration.
- [x] Code is safe for minSdk.

Acceptance criteria:

- [x] `BluetoothService` receiver registration is API-compatible.

---

## Phase 3 — Defensively stop BLE service without advertise permission

### Task 3.1 — Add advertise permission guard

- [x] Open `BleHogpService.kt`.
- [x] In `onCreate()`, on Android 12+, check:
  - [x] `BLUETOOTH_CONNECT`,
  - [x] `BLUETOOTH_ADVERTISE`.
- [x] If either is missing:
  - [x] log clear message,
  - [x] call `stopSelf()`,
  - [x] return before BLE setup.

Suggested message:

```text
BLE HOGP requires Bluetooth connect/advertise permissions; stopping BLE service.
```

Acceptance criteria:

- [x] BLE service does not remain running when advertise permission is missing.

### Task 3.2 — Avoid non-advertising foreground state

- [x] Ensure service does not proceed into GATT/advertising setup if advertise is missing.
- [x] Avoid foreground service remaining active while unable to advertise.
- [x] Keep existing MainActivity/BootReceiver prevention logic intact.

Acceptance criteria:

- [x] Service-level defense covers stale/external start paths.

### Task 3.3 — Add/record validation

- [x] Add helper test if permission decision is extracted.
- [x] Otherwise document code-review/manual validation.
- [x] Confirm startup and boot planners still prevent normal bad starts.

Acceptance criteria:

- [x] Advertise-missing path is verified or explicitly documented.

---

## Phase 4 — Make physical HID docs/comments consistently prefer ConnectProfile(HID)

### Task 4.1 — Update physical HID docs

- [ ] Open `docs/PHYSICAL_HID_TESTING.md`.
- [ ] Move DBus `ConnectProfile(HID)` command into the primary Linux/BlueZ procedure.
- [ ] Reword early docs so `bluetoothctl connect` is not presented as the primary known-good path.
- [ ] Explain:
  ```text
  bluetoothctl connect may create a generic connection but may not open HID profile.
  ```

Acceptance criteria:

- [ ] Linux/BlueZ users are directed to `ConnectProfile(HID)` first.

### Task 4.2 — Update README physical test references

- [ ] Open `README.md`.
- [ ] Ensure physical testing section prefers DBus `ConnectProfile(HID)`.
- [ ] Mention `bluetoothctl connect` only as fallback/diagnostic if mentioned at all.

Acceptance criteria:

- [ ] README matches detailed physical HID docs.

### Task 4.3 — Update test comments/logs

- [ ] Open `BluetoothHidSendReportTest.kt`.
- [ ] Search for `bluetoothctl connect`.
- [ ] Reword comments/logs to prefer `ConnectProfile(HID)` on Linux/BlueZ.
- [ ] Keep phone/host address guidance intact.
- [ ] Do not remove the two-address model.

Acceptance criteria:

- [ ] Test output/comments do not mislead users toward generic connect as the known-good Linux flow.

---

## Phase 5 — BlueDeck branding cleanup

### Task 5.1 — Rebrand HID SDP metadata

- [ ] Open the HID registration module, likely `BluetoothHidModule.kt`.
- [ ] Locate `BluetoothHidDeviceAppSdpSettings`.
- [ ] Replace stale strings:
  - [ ] `Bluetooth Keyboard/Mouse`,
  - [ ] `Android Bluetooth HID`,
  - [ ] `Gemini`.
- [ ] Use BlueDeck-branded strings, for example:
  - [ ] `BlueDeck Keyboard/Mouse`,
  - [ ] `BlueDeck Android HID`,
  - [ ] `BlueDeck`.

Acceptance criteria:

- [ ] Host-facing HID metadata no longer uses stale project branding.

### Task 5.2 — Rename root project if low risk

- [ ] Open `settings.gradle.kts`.
- [ ] If present, change:
  ```kotlin
  rootProject.name = "Bluetooth Keyboard Mouse"
  ```
  to:
  ```kotlin
  rootProject.name = "BlueDeck"
  ```
- [ ] Do not change `applicationId`.
- [ ] Do not change namespace.
- [ ] Do not change Kotlin packages.

Acceptance criteria:

- [ ] Build/project name is BlueDeck without package migration.

---

## Phase 6 — Clean validation wording

### Task 6.1 — Find contradictory manual smoke checkboxes

- [ ] Search current TODO/docs/memory for manual smoke checklist.
- [ ] Identify any checked item that final evidence says is pending manual UX smoke.
- [ ] Remove contradiction.

Acceptance criteria:

- [ ] Manual UX status is not overclaimed.

### Task 6.2 — Use explicit evidence labels

Use labels such as:

```text
PASS — manually verified on device
PASS — unit/instrumented verified only
PASS — physical HID verified
PENDING — manual UX smoke needed
FAIL — issue found
N/A — not applicable
```

- [ ] Apply these labels to manual UX smoke items.
- [ ] Keep real automated/physical results intact.

Acceptance criteria:

- [ ] Validation notes are internally consistent.

---

## Phase 7 — Validation

### Task 7.1 — Unit/JVM tests

Run:

```bash
./gradlew clean test
```

Acceptance criteria:

- [ ] Tests pass or failures are documented.

### Task 7.2 — Build

Run:

```bash
./gradlew assembleDebug
```

Acceptance criteria:

- [ ] Debug APK builds.

### Task 7.3 — Lint/static checks

Run:

```bash
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew detekt
```

Acceptance criteria:

- [ ] No release-blocking lint/static-analysis failures.

### Task 7.4 — Instrumented tests

If device/emulator available:

```bash
./gradlew connectedDebugAndroidTest
```

Acceptance criteria:

- [ ] Instrumented tests pass or hardware-dependent skips are documented.

### Task 7.5 — Physical HID test if setup available

Run physical HID test with:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Use DBus `ConnectProfile(HID)` from the Linux host.

Acceptance criteria:

- [ ] Physical HID results are recorded separately from normal instrumented tests.

---

## Phase 8 — Final acceptance checklist

Do not mark Fix 3 complete until all are true:

- [ ] MainActivity re-checks full permission state after startup permission callback.
- [ ] SettingsScreen BLE toggle re-checks full permission state after callback.
- [ ] Partial permission callback maps no longer cause false BLE denial.
- [ ] Tests cover partial callback maps.
- [ ] `BluetoothService` receiver registration is API 26–32 safe.
- [ ] `BleHogpService` stops if advertise permission is missing.
- [ ] Physical HID docs prefer DBus `ConnectProfile(HID)`.
- [ ] Physical HID test comments/logs prefer DBus `ConnectProfile(HID)` on Linux/BlueZ.
- [ ] HID SDP metadata is BlueDeck-branded.
- [ ] Root project name is BlueDeck if changed.
- [ ] Validation wording no longer overclaims manual UX smoke.
- [ ] Build/test/lint/static validation passes or failures are honestly documented.
