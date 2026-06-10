# replies1.md — Replies to Claude Code on BlueDeck Release Hardening

These replies address Claude Code's questions and issues from `responses1(22).md`.

## Overall position

The confirmed bugs listed in Claude's Section A are real and should be fixed. The release-hardening spec is directionally correct. The only changes are clarifications around product choices, the exact scope of SIMPLE-mode scroll work, and a few implementation details where the sample code was illustrative rather than mandatory.

Proceed phase-by-phase. Do not convert this into a broad rewrite.

---

## 1. Confirmed real bugs — proceed

Claude's confirmed bug list is accurate. Proceed with fixes for:

1. Backend lifecycle: `MainActivity.switchBackend(...)` must stop inactive started/foreground services, not merely unbind.
2. Permission model: Classic startup must not require `BLUETOOTH_SCAN`.
3. Pairing scan permissions: scan must not request advertise/notifications.
4. `BleHogpService.onCreate()` missing-permission path: call `stopSelf()` before returning.
5. Forced startup debug logging: remove `DebugLog.setEnabled(true)` from `MainActivity.onCreate()`.
6. Quick Settings BLE guard.
7. Permission tests.
8. README/physical HID docs.
9. Validation.

Also fix the `PermissionPolicy` doc/comment mismatch. If a comment says scan denial is not fatal, the implementation and tests must match that.

---

## 2. Open decision: splash duration

### Decision

Keep the current `SPLASH_DISPLAY_MS = 1800L` for this hardening pass.

### Rationale

Phil explicitly wanted the BlueDeck branding/name/tagline to be visible. The spec's 800–1200 ms recommendation was a general utility-app polish suggestion, but it conflicts with that product choice.

Do **not** shorten the splash in this pass.

### Implementation guidance

- Keep `1800L`.
- Do not add additional delay beyond the existing one.
- Do not make a larger splash refactor.
- Do use string resources for the title/tagline if practical.
- Do not change the tagline wording.

Future optional improvement:

```text
Show the longer 1800 ms branded splash only on first launch, then use a shorter splash on later launches.
```

But do not implement first-launch-only behavior in this pass unless it is trivial and low risk.

---

## 3. Open decision: `startForeground()` failure behavior

### Decision

Yes. Change `startForeground()` failure handling to stop the service and return failure. Drop the current "post normal notification and continue" fallback.

### Rationale

A normal notification is not a foreground service. If `startForeground(...)` fails, continuing as if the service is foregrounded is unsafe and can be killed unpredictably by Android.

### Required behavior

Change `ServiceForegroundController.startInForeground(...)` to return a Boolean or equivalent result:

```kotlin
fun startInForeground(...): Boolean
```

On success:

```kotlin
return true
```

On failure:

```kotlin
DebugLog.e(TAG, "startForeground failed: ${e.message}")
service.stopSelf()
return false
```

Callers must abort/return if it returns false.

### Diagnostic notification

Do not keep the current behavior where `mgr.notify(id, notif)` is used as a fake foreground fallback.

If you want a diagnostic notification, it must not allow the service to continue pretending it is foregrounded. The safe default is: log, `stopSelf()`, return false.

---

## 4. Open decision: release version

### Decision

Use:

```kotlin
versionCode = 1
versionName = "0.1.0"
```

### Rationale

This app is not ready to be called `1.0`. The remaining lifecycle, permission, BLE, and service-failure issues are too serious for a public 1.0 label.

The existing `v0.1` git tag does not justify `versionName = "1.0"`. Align the Android-visible version with the project maturity.

### Important note

Do not rewrite git history or retag automatically. Just set the Gradle app version correctly.

If this APK has already been published somewhere that requires monotonically increasing `versionCode`, bump `versionCode` appropriately. For this local/project release pass, `versionCode = 1` is fine unless there is an external distribution constraint.

---

## 5. Phase 6 scope: SIMPLE/FULL scroll

### Decision

Claude is correct that the HID layer already gates scroll in SIMPLE mode. Phase 6 should be scoped to UI copy plus UI/action dispatch gating.

### Required interpretation

When the TODO says:

```text
SIMPLE mode emits no scroll actions.
```

it means:

```text
MouseScreen should not dispatch Action.ScrollVertical or Action.ScrollHorizontal in SIMPLE mode.
```

It does **not** mean you need to change the HID layer again if `HidReportSender` already no-ops scroll in SIMPLE mode.

### Required work

Do:

- add/use `ScrollPolicy`,
- update `MouseScreen` text,
- prevent `Action.ScrollVertical` and `Action.ScrollHorizontal` dispatch in SIMPLE mode if practical,
- keep HID-layer no-op as defensive protection,
- add/update tests for policy behavior.

Do not:

- rework HID descriptors,
- rework `HidReportSender` unless tests reveal a real bug,
- treat downstream no-op as sufficient for UI correctness.

### Reason

Even if the HID layer drops the report, dispatching scroll actions from the UI while telling the user scroll works is still misleading and complicates debugging.

---

## 6. Tagline wording

### Decision

Keep the current tagline:

```text
The handy keyboard and mouse
```

Do **not** replace it with the spec's example wording:

```text
Your phone as a Bluetooth keyboard and mouse.
```

The spec wording was an example, not an instruction to change the product copy.

### Required work

If the splash currently hardcodes the title/tagline, use existing string resources if practical:

```xml
<string name="app_name">BlueDeck</string>
<string name="bluedeck_tagline">The handy keyboard and mouse</string>
```

But preserve the actual wording.

---

## 7. Things that should not be touched broadly

### 7.1 Detekt thresholds and existing justified suppressions

Do not undo the current detekt policy just because the spec says not to add broad suppressions.

Preserve intentional, documented choices such as:

- raised `TooManyFunctions` / `LargeClass` thresholds for large but cohesive HID services,
- documented `@Suppress` annotations for platform defensive catches,
- HID byte-table `MagicNumber`,
- compatibility/deprecation handling.

Do not add new broad suppressions to hide new problems.

### 7.2 Package/application ID

Do not rename package, namespace, or application ID.

The app is now branded as BlueDeck, but internal package names can remain stable.

### 7.3 Broad UI redesign

Do not redesign screens beyond the explicit theme/palette polish and bug-fix UI text changes.

### 7.4 Moving old docs

Moving old spec/TODO files to `docs/history/` is optional. Do it only if low risk and not distracting from hardening blockers.

If you do move them, do not break README links.

---

## 8. Minor implementation details

### 8.1 `DebugLog.w(...)` sample code

Claude is correct: if `DebugLog.w(...)` does not exist, do not blindly use it.

Acceptable options:

1. Use `DebugLog.log(...)` for non-error warnings.
2. Use `DebugLog.e(...)` for exceptional/unexpected failures.
3. Add a small `DebugLog.w(...)` method only if it fits the existing logging style.

Do not add a logging API just to satisfy the sample code.

### 8.2 Stale launcher resources

Do not delete launcher resources blindly.

Expected outcome:

- BlueDeck adaptive icon should be the launcher icon.
- Old default Android robot icon resources should not be referenced by active launcher icons.
- If old resources are truly unreferenced, they may be removed.
- If legacy pre-26 fallback icons still reference them, either keep them or replace them with BlueDeck fallbacks.

Verification before deletion is required.

### 8.3 Theme palette

Yes, applying the BlueDeck palette is in scope.

Keep this modest and safe:

- update app color scheme,
- preserve contrast,
- do not redesign layouts,
- do not create a new design system.

---

## 9. Physical HID docs

### Decision

Claude is correct: updating physical HID docs with DBus `ConnectProfile(HID)` is genuinely needed.

`docs/PHYSICAL_HID_TESTING.md` must include the known-good Linux/BlueZ command:

```bash
dbus-send --system --print-reply \
  --dest=org.bluez \
  /org/bluez/hci0/dev_8C_6A_3B_5E_D3_48 \
  org.bluez.Device1.ConnectProfile \
  string:00001124-0000-1000-8000-00805f9b34fb
```

Also explain:

- `bluetoothctl connect <PHONE>` may create a generic connection but may not open the HID profile.
- `ConnectProfile(HID)` is the known-good physical-test flow.
- Clean re-pair may be required.
- Keep the two-address model:
  - `hidHostAddress` = laptop/controller address from `bluetoothctl show`,
  - `hidPhoneAddress` = phone address from `bluetoothctl devices`.
- If the test fails, collect `journalctl` and `btmon` before blaming Ubuntu/BlueZ.

---

## 10. Execution approach

Claude's suggested phase-by-phase approach is correct.

Recommended batches:

1. Backend lifecycle.
2. Permission model + Pairing scan.
3. BLE toggle gating + BLE service permission safety.
4. Foreground service failure handling.
5. SIMPLE/FULL MouseScreen UI/dispatch.
6. Debug logging + notification permission sequencing.
7. Quick Settings tile BLE guard.
8. Physical HID docs.
9. BlueDeck theme/polish/version/docs.
10. Full validation.

For each batch, run the fastest relevant checks first.

Suggested baseline per batch:

```bash
./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
```

Then as appropriate:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew lintDebug
```

For Bluetooth-touching phases, run instrumented tests if a device is available.

For physical HID, run the 13 physical tests only when the hardware setup is ready and the DBus `ConnectProfile(HID)` command can be issued from the host.

---

## 11. Specific answers to Claude's summary questions

### Q1. Splash duration: 1800 ms vs ~1000 ms vs first-launch-only?

Keep `1800 ms` for now.

Do not change it in this pass. First-launch-only can be considered later, but it is not part of the hardening pass.

### Q2. `startForeground` failure: switch to `stopSelf` + Boolean return?

Yes.

Implement `startInForeground(): Boolean` or equivalent. On failure, log, stop the service, and return false. Do not continue with notification-only fallback.

### Q3. Release version: `0.1.0` vs `1.0`?

Use:

```kotlin
versionName = "0.1.0"
versionCode = 1
```

unless there is an external publishing constraint requiring a `versionCode` bump.

Do not call this `1.0`.

### Q4. Phase 6 scope?

Scope Phase 6 to:

- MouseScreen UI copy,
- UI/action dispatch gating,
- `ScrollPolicy` helper/tests.

The HID layer already gates SIMPLE-mode scroll and does not need rework unless tests reveal a bug.

### Q5. Tagline wording?

Keep:

```text
The handy keyboard and mouse
```

Do not replace it with the spec's example tagline.

### Q6. Anything that should not be touched?

Do not touch broadly:

- package/application ID,
- Redux/state architecture,
- broad UI layouts,
- detekt thresholds that are already documented,
- existing justified suppressions,
- old docs movement unless low risk,
- splash duration,
- tagline wording.

Do touch:

- service lifecycle,
- permission model,
- BLE gating,
- foreground failure behavior,
- SIMPLE-mode MouseScreen copy/dispatch,
- Quick Settings BLE guard,
- physical HID docs,
- theme palette,
- release version.

---

## 12. Final implementation contract

Proceed with the hardening work using these decisions:

```text
Splash duration:
Keep 1800 ms.

Foreground failure:
Stop service and return failure. No fake FGS fallback.

Version:
0.1.0.

SIMPLE scroll:
HID layer already no-ops; still fix MouseScreen text and dispatch.

Tagline:
Keep "The handy keyboard and mouse".

Theme:
Apply BlueDeck palette modestly.

Docs:
Add DBus ConnectProfile(HID) as known-good Linux physical HID flow.

Suppressions:
Do not add broad new suppressions; preserve documented intentional ones.

Scope:
Release hardening only, not architecture rewrite.
```
