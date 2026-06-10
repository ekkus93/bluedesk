# replies3.md — Replies to Claude Code on BlueDeck Release Candidate Fix 3

These replies address Claude Code's questions and issues from `responses3(14).md`.

## Overall position

Claude Code's verification is correct: the Release Candidate Fix 3 scope is narrow and accurate.

Proceed with Fix 3. Do not reopen Fix 2, do not do a broad rewrite, and do not change package/application ID, splash duration, tagline, or HID descriptor architecture.

The confirmed bugs are real:

1. `RequestMultiplePermissions` callback maps are being treated as full permission state in `MainActivity` and `SettingsScreen`.
2. `BluetoothService.registerReceiver(...)` uses an unguarded API-33-style receiver flags overload on a minSdk 26 app.
3. `BleHogpService` checks `BLUETOOTH_CONNECT` but not `BLUETOOTH_ADVERTISE`.
4. Classic HID SDP metadata still uses stale pre-BlueDeck branding.
5. `rootProject.name` is still the old project name.
6. Physical HID docs/comments still lean too much on `bluetoothctl connect` even though DBus `ConnectProfile(HID)` is the known-good Linux/BlueZ procedure.
7. Validation wording still needs cleanup so pending manual UX smoke is not represented as manually verified.

Proceed with the implementation after applying the decisions below.

---

## B1. Permission-decision testability

### Decision

Yes, add a pure decision helper.

Claude's proposed direction is correct. The current code mixes:

- permission callback interpretation,
- full-state permission checking,
- backend startup decisions,
- UI/dialog side effects.

That makes the partial-callback bug hard to test meaningfully.

Add a pure helper such as:

```kotlin
object StartupPermissionDecision {
    fun decide(
        plan: StartupPermissionPlan,
        grantedFullState: Set<String>,
    ): Decision
}
```

or equivalent.

The exact names can differ, but the structure should be:

1. Android-facing code re-checks full permission state using `ContextCompat.checkSelfPermission`.
2. The resulting full granted set or missing-permission set is passed to a pure decision helper.
3. `MainActivity` applies the decision with side effects:
   - start planned backend,
   - fallback to Classic,
   - persist `useBleHogp=false`,
   - show dialog/message,
   - mark startup permission flow resolved.

### Required decisions

The helper should represent outcomes like:

```kotlin
sealed class StartupPermissionDecision {
    data object StartPlannedBackend : StartupPermissionDecision()
    data object FallbackBleToClassic : StartupPermissionDecision()
    data object ShowClassicPermissionDenied : StartupPermissionDecision()
    data object StartNothing : StartupPermissionDecision()
}
```

Exact shape is flexible.

### Required test cases

Add tests that would fail with the old callback-map-as-full-state logic:

1. BLE plan requires connect + advertise.
2. Full state grants connect + advertise, even if callback contained only advertise.
3. Decision starts BLE when full state is granted.
4. Decision falls back to Classic when advertise is still missing.
5. Classic plan does not require scan.
6. Settings BLE toggle partial callback succeeds when full state is granted.
7. Settings BLE toggle partial callback fails when full state is still missing.

### Important

Do not create an Android-framework-heavy test just to test decision logic. Keep the decision helper pure and put Android permission reads behind a thin checker/wrapper.

---

## B2. `registerReceiver` exported fallback

### Decision

Drop the exported fallback.

Use the clean `NOT_EXPORTED` registration path.

Preferred implementation:

```kotlin
ContextCompat.registerReceiver(
    this,
    receiver,
    filter,
    ContextCompat.RECEIVER_NOT_EXPORTED,
)
```

This is available because the project already has `core-ktx` 1.16.0.

### Rationale

The old try/catch fallback to `RECEIVER_EXPORTED` was a workaround around the unsafe unguarded call. It should not be preserved unless there is a specific receiver that truly needs exported behavior.

For the internal service receiver, exported fallback is not desirable. It broadens exposure and makes the app less strict than it needs to be.

### Required behavior

- Replace the unguarded receiver registration in `BluetoothService`.
- Keep the receiver non-exported.
- Do not add an exported fallback.
- If another receiver truly requires exported behavior, document why. Otherwise use not-exported.

---

## B3. BLE advertised device name

### Decision

Rebrand the BLE advertised/local device name too, but keep it simple and document the pairing impact.

Change:

```kotlin
adapter.name = "Bluetooth Keyboard"
```

to something BlueDeck-branded, preferably:

```kotlin
adapter.name = "BlueDeck"
```

or:

```kotlin
adapter.name = "BlueDeck Keyboard"
```

I prefer:

```text
BlueDeck
```

because it is short and clean in host Bluetooth UIs.

### Rationale

This is host-facing branding. If the host sees "Bluetooth Keyboard" while the app is named BlueDeck, it looks unfinished.

### Caveat

Changing the advertised/local Bluetooth name may require clean re-pairing on some hosts or may only affect future discovery.

That is acceptable for v0.1, but document it in the physical HID / troubleshooting docs if needed.

### Required safety

- Do not fail BLE startup if setting the adapter name fails.
- Log failures safely.
- Keep permission guards around setting adapter name.
- Do not rename package/application ID.

---

## B4. Physical HID docs rewrite depth

### Decision

Use option **(a)**.

Promote DBus `ConnectProfile(HID)` to the primary Linux/BlueZ command in the main procedure, demote `bluetoothctl connect` to a clearly-labeled fallback/diagnostic subsection, and fix the 4 test comments/logs.

Do not do a fuller restructure of the whole walkthrough unless it is necessary to remove contradictions.

### Required wording

Use language like:

```text
On Linux/BlueZ, the known-good host-initiated HID profile connection is DBus ConnectProfile(HID). Generic bluetoothctl connect may create an ACL/generic connection but may not open the HID profile, so treat it as a fallback or diagnostic command.
```

### Required command

Make this the primary command:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_<PHONE_BT_ADDRESS_UNDERSCORE> \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Concrete example:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_8C_6A_3B_5E_D3_48 \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

### Test comments/logs

Update `BluetoothHidSendReportTest.kt` comments/logs so they point users to `ConnectProfile(HID)` first on Linux/BlueZ. Keep `bluetoothctl connect` only as fallback/diagnostic.

---

## B5. Validation wording source of truth

### Decision

Edit the Fix2 TODO in place.

The contradictory checked boxes live in `docs/BLUEDECK_RELEASE_HARDENING_FIX2_TODO.md`, and that file is part of the current repo history/docs. Leaving it contradictory will confuse future review passes.

### Required behavior

Do not pretend the old checkbox checklist is complete if the final evidence section says some items are pending manual UX smoke.

Update the Fix2 TODO validation section to use explicit labels:

```text
PASS — manually verified on device
PASS — unit/instrumented verified only
PASS — physical HID verified
PENDING — manual UX smoke needed
FAIL — issue found
N/A — not applicable
```

### Specific instruction

For any item that was verified only by unit/helper/instrumented tests, do **not** mark it as manual-device-verified.

For any item that still needs real human device interaction, mark it:

```text
PENDING — manual UX smoke needed
```

### Also update

Also update forward-looking docs if present:

- `memory.md`,
- new Fix3 TODO/validation notes,
- any release notes that summarize validation.

### Rationale

This is not rewriting history. It is correcting an inaccurate validation record.

---

## Additional implementation guidance

### 1. Permission callback fix: full-state check is mandatory

Do not try to patch this by merging the callback result map with old assumptions unless that merged map is backed by real `checkSelfPermission` state.

Correct model:

```kotlin
val allGranted = PermissionGrantChecker.hasAll(context, requiredPermissions)
```

Not:

```kotlin
PermissionPolicy.missingRequired(callbackResult, requiredPermissions).isEmpty()
```

Callback result maps are partial. Treat them as notification that a permission request completed, not as the full truth.

### 2. MainActivity fallback behavior remains unchanged

Interactive BLE startup denial should still:

```text
persist useBleHogp=false
fall back to Classic if possible
show/log connect/advertise message
```

Boot BLE denial should still:

```text
start nothing
do not persist setting changes
log skip reason
```

Do not change the interactive-vs-boot asymmetry from Fix 2.

### 3. Settings BLE toggle behavior

If user toggles BLE on and the full state after callback grants connect + advertise:

```text
persist useBleHogp=true
```

If full state is still missing:

```text
persist/keep useBleHogp=false
show/log connect/advertise message
```

Do not fail just because the callback map omitted a permission that was already granted.

### 4. BLE service advertise guard

Add the advertise check beside the existing connect check.

Do not rework BLE service architecture.

Required logic on Android 12+:

```text
if missing connect or advertise:
    log clear reason
    stopSelf()
    return
```

### 5. SDP metadata branding

Use BlueDeck names for Classic HID SDP metadata.

Recommended:

```text
Name: BlueDeck Keyboard/Mouse
Description: BlueDeck Android HID
Provider: BlueDeck
```

Do not overthink this. The goal is simply to remove stale "Bluetooth Keyboard/Mouse", "Android Bluetooth HID", and "Gemini" branding.

### 6. Root project name

Proceed with:

```kotlin
rootProject.name = "BlueDeck"
```

Do not change:

```text
applicationId
namespace
Kotlin package names
```

### 7. Validation expectations

Run the normal validation gates after implementation:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew detekt
```

Run instrumented and physical HID tests if hardware is attached.

Classify evidence honestly. Do not mark manual UX smoke as passed unless actually performed.

---

## Direct answers summary

### Q B1 — Add pure decision helper?

Yes. Add it. This is the right way to make the partial-permission-callback bug testable.

### Q B2 — Drop exported fallback?

Yes. Drop it. Use `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` and do not preserve the exported fallback.

### Q B3 — Rebrand BLE advertised device name?

Yes. Rebrand it to `BlueDeck` or `BlueDeck Keyboard`; prefer `BlueDeck`.

Document that hosts may need re-pairing if the name change matters.

### Q B4 — Docs rewrite depth?

Use option (a). Promote `ConnectProfile(HID)` as the primary Linux/BlueZ command, demote `bluetoothctl connect` to fallback/diagnostic, and fix test comments/logs.

### Q B5 — Validation wording source of truth?

Edit the Fix2 TODO in place and also update any forward-looking validation notes. Do not leave contradictory checked manual-smoke boxes.

---

## Final implementation contract

Proceed with Fix 3 using these decisions:

```text
Permission decisions:
Add pure helper. Re-check full OS permission state after callbacks.

Receiver registration:
Use NOT_EXPORTED through ContextCompat. Drop exported fallback.

BLE advertised name:
Rebrand to BlueDeck, safely.

Physical HID docs:
ConnectProfile(HID) is primary on Linux/BlueZ. bluetoothctl connect is fallback/diagnostic.

Validation docs:
Fix the contradictory Fix2 TODO in place. Pending manual UX smoke stays pending.

Project branding:
Rename rootProject.name to BlueDeck. Do not rename package/application ID.
```

Keep this pass narrow and release-candidate focused.
