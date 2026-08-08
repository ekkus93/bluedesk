# BlueDeck Post-Fix3 Correctness/UI Hardening — Evidence

Date: 2026-08-08

## Status

**Automated hardening gate: PASS.**

**Overall release/acceptance status: OPEN.** The automated implementation is green, but this hardening pass materially changed Classic HID registration/readiness, transport/error handling, backend lifecycle, and input gating. The required physical Classic HID validation must therefore be rerun against the exact implementation SHA. BLE HOGP device smoke and manual UX smoke are also still pending.

This document records evidence for:

- `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_SPEC_2026-08-08.md`
- `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_TODO_2026-08-08.md`

It supersedes any earlier broad conclusion that Fix3 alone was sufficient evidence for release acceptance. Historical physical HID evidence remains useful history, but it is not evidence for the exact hardening implementation SHA below.

---

## 1. Source identity

| Item | SHA |
|---|---|
| Reviewed baseline | `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3` |
| Hardening spec | `21bc0daadb01fbd3a543afeced2484fded30c113` |
| Hardening TODO creation | `73fed407b487e173958554d4a6e93a9d6d6515dd` |
| Implementation start | `20e85fba1b1b167bb1fd96dbc1e3734cadd005a0` |
| **Final implementation SHA** | **`953df07df97779c7cc85f3f9bc1acb1e77821c7d`** |

The final implementation SHA is intentionally the commit after all temporary one-shot hardening helpers/workflows were removed. Permanent validation checked out this exact SHA.

---

## 2. Permanent CI evidence

Permanent workflow: `.github/workflows/ci.yml`

- Run: **31284953872**
- Job: **93172012610** — `Build, test, lint`
- Exact checkout SHA: `953df07df97779c7cc85f3f9bc1acb1e77821c7d`
- Result: **SUCCESS**

Successful substantive gates:

- `./gradlew :app:assembleDebug --stacktrace`
- `./gradlew :app:testDebugUnitTest --stacktrace`
- `./gradlew :app:compileDebugAndroidTestKotlin --stacktrace`
- `./gradlew :app:lintDebug --stacktrace`
- `./gradlew :app:ktlintCheck --stacktrace`
- `./gradlew :app:detekt --stacktrace`

The final CI run did not regenerate lint/static-analysis baselines and did not relax thresholds. New hardening findings were fixed or, where a fail-fast/Compose structure was deliberate, handled with narrow source-level suppressions accompanied by local rationale.

The compiler still emits the existing Android deprecation warnings for `statusBarColor` and `navigationBarColor` in `ui/theme/Theme.kt`; they are warnings only and did not bypass any gate in this pass.

---

## 3. Permanent API/emulator matrix evidence

Permanent workflow: `.github/workflows/instrumented.yml`

- Run: **31284953866**
- Exact checkout SHA for every matrix job: `953df07df97779c7cc85f3f9bc1acb1e77821c7d`
- Result: **SUCCESS on every supported matrix API**

| API | Job | Result |
|---:|---:|---|
| 28 | `93172016678` | PASS |
| 30 | `93172016686` | PASS |
| 31 | `93172016691` | PASS |
| 34 | `93172016673` | PASS |
| 35 | `93172016657` | PASS |

The API 35 job log records the complete instrumented suite finishing with **107 tests discovered/finished, 13 physical-HID tests skipped by design, and 0 failures**. That leaves 94 non-physical tests exercised by the emulator run. The physical HID tests are intentionally excluded from emulator acceptance and are tracked separately below.

The real-screen instrumented coverage includes, among other cases:

- disconnected Keyboard/Mouse navigation guard behavior;
- safe connected-host label fallback;
- stale connection metadata with lost backend readiness disabling input;
- Classic Pairing controls;
- BLE host-initiated Pairing flow and absence of Classic-only controls;
- BLE startup-failure remediation state;
- scan-start failure resetting scanning state;
- unsupported BLE scroll behavior;
- Drag Lock disposal releasing the mouse button;
- Navigation grid key-height consistency at normal and large font scales.

---

## 4. Correctness hardening completed

### 4.1 Authoritative backend/runtime truth

The app now models backend runtime truth explicitly instead of using service-bind state or remembered connection metadata as a proxy for usability.

Key implementation points:

- explicit `BackendRuntimeState` including `Stopped`, `Starting`, `Ready`, `Stopping`, and `Failed`;
- explicit backend capability sets;
- one-live-backend lifecycle coordination;
- transactional backend startup/switching with rollback;
- `isInputUsable()` requires backend `Ready`, sender availability, required permissions, and a safe connected-device address;
- Function Keys, Extended Keys, Navigation Keys, Keyboard, and Mouse input paths use authoritative usability rather than `connectedDevice != null` alone.

### 4.2 Command/transport failure contracts

Command execution no longer has a default no-op success path.

- `CommandResult.Success`
- `CommandResult.Unsupported`
- typed `CommandResult.Failure`

Classic HID transport now reports missing proxy/device/permission/API support, `sendReport()` rejection, and exceptions explicitly. Unsupported BLE operations are surfaced as unsupported rather than pretending to succeed.

### 4.3 Classic HID startup/readiness

Classic `registerApp()` readiness is explicit and tested for:

- immediate `registerApp()` rejection;
- immediate acceptance remaining pending until callback confirmation;
- callback success transitioning to Ready;
- callback failure transitioning to Failed.

Foreground-service promotion failure now publishes failed startup state, updates visible app state, records a durable runtime failure, writes an Android system log, posts a user-visible failure notification, and tears down rather than continuing partial initialization.

Corrupt/invalid remembered Classic host addresses no longer silently terminate reconnect. The invalid remembered target is cleared, the failure is recorded and surfaced, and default/target state is reconciled.

### 4.4 BLE HOGP startup/readiness

BLE readiness is fail-closed across startup prerequisites and state-machine transitions.

Evidence covers:

- required BLE startup permissions;
- missing advertiser;
- missing GATT server;
- GATT service registration progress/failure;
- advertising start success/failure;
- late observers seeing durable failed/ready tracker state;
- startup failures recording durable state and producing a user-visible notification;
- GATT response permission/server failures updating visible app state instead of disappearing into debug-only logs.

Changing the local Bluetooth adapter name remains a cosmetic best-effort operation; failure is explicitly logged and does not falsify BLE readiness.

### 4.5 Discovery and permission semantics

Classic discovery now publishes actual adapter outcomes instead of optimistic UI intent.

- scan state becomes true only after successful adapter start;
- failed start, missing adapter, denied permission, and revoked permission reconcile scan state visibly;
- paired-device lookup no longer turns permission/adapter failures into an indistinguishable successful empty list;
- API 28/30 and API 31+ permission plans are tested separately.

The supported/installable product floor is Android 9 / API 28, consistent with the primary Classic HID peripheral workflow.

### 4.6 Silent-failure cleanup

The hardening loop explicitly audited for dangerous fallbacks and quiet failures.

Regression guards now fail the JVM suite if critical runtime code reintroduces:

- empty generic exception catches;
- `runCatchingLogged` suppression in critical runtime;
- nullable `sender?.` dispatch;
- production `Thread.sleep` in input runtime;
- a default no-op `KeySender` command body.

Correctness-significant boot, foreground-start, BLE startup, discovery, GATT response, and remembered-host failures have a stateful/user-visible or durable/system-log path. Optional observer/teardown failures such as Quick Settings refresh are explicitly documented as best-effort and system-logged rather than silently swallowed.

---

## 5. Input timing and cleanup evidence

Blocking sleeps were removed from production input execution. Timing uses coroutine delay plus serialized command sequencing.

Deterministic tests now cover:

- repeated same-key press/release ordering;
- rapid mouse-click down/up pairs;
- lock-toggle serialized press/release ordering;
- cancellation of an active command scope still releasing a held key;
- missing sender producing a visible typed failure;
- unsupported operations not being treated as success.

Drag Lock cleanup is also exercised through the production `MouseScreen` Compose path.

---

## 6. Settings/IME/UI hardening evidence

### IME overrides

IME override loading now has an explicit state model that distinguishes:

- successful empty configuration;
- successful populated configuration;
- storage/read failure.

A read failure preserves the last-known-good in-memory overrides/labels and exposes the cause. Package-label lookup failure falls back to the stable package identifier with a diagnostic rather than turning the whole load into a false empty-success state.

### Navigation/accessibility sizing

The keyboard-cell height is a shared contract. Function, Extended, and Navigation placeholders consume that same contract, and Scroll Lock has the same explicit cell height. Real Compose tests verify Navigation-grid height consistency at both normal (`1.0`) and large (`1.6`) font scales.

### Battery-optimization diagnostics

If both app-specific and general battery-optimization settings intents fail, the user receives an actionable visible message and the terminal diagnostic is also written to the Android system log. The failure no longer depends on debug logging for observability.

---

## 7. Documentation/product-contract reconciliation

`README.md` now documents the hardened product contract:

- Classic HID is the default device discovery/management workflow;
- BLE HOGP pairing/connection is host-initiated;
- BLE does not present Classic-only Scan/Connect/Rename/default-device behavior as supported;
- backend/capability state can restrict input/scroll controls;
- Android 9 / API 28 is the supported minimum;
- API 28–30 and API 31+ permission models differ;
- Linux/BlueZ HID connection documentation uses D-Bus `ConnectProfile(HID)` as the primary known-good procedure rather than treating `bluetoothctl connect` as authoritative.

---

## 8. Ralph-loop defects discovered and closed

The hardening loop did not simply validate the first implementation. It found and closed additional defects/gaps, including:

1. five ktlint line-length failures;
2. 24 new detekt findings from hardening code, resolved without baseline regeneration or threshold relaxation;
3. boot-start failures visible only through debug logging;
4. Classic foreground-start failure that could remain debug-only;
5. paired-device lookup failures collapsing into an apparently successful empty list;
6. invalid remembered Classic hosts silently terminating reconnect;
7. BLE pre-bind startup and GATT-response failures with insufficient user-visible persistence;
8. Function/Extended/Navigation screens gating on stale connection metadata instead of backend usability;
9. duplicated Navigation placeholder sizing and missing large-font grid proof;
10. IME load failure lacking explicit successful-empty-vs-failure testable state;
11. missing deterministic rapid-click/lock-toggle/cancellation timing tests;
12. missing Classic registration callback transition tests;
13. README product-contract drift around BLE pairing and minimum supported Android API.

All of those automated/code-review blockers are closed on the final implementation SHA.

---

## 9. Remaining physical/manual gates

These are **not** waived by green CI.

### 9.1 Classic physical HID exact-SHA validation

**Status: PENDING — REQUIRED.**

Reason: this hardening materially changed Classic HID registration/readiness, transport result handling, reconnect/error visibility, and backend/input gating. Historical physical results do not validate `953df07df97779c7cc85f3f9bc1acb1e77821c7d`.

Required evidence should exercise the physical keyboard/mouse report path and record the exact tested SHA/host/device outcome using `docs/PHYSICAL_HID_TESTING.md`.

### 9.2 BLE HOGP physical smoke

**Status: PENDING.**

Required device smoke should confirm advertising/readiness, host-initiated pair/connect, keyboard/mouse delivery for supported capabilities, and visible failure behavior for a deliberately unavailable prerequisite where practical.

### 9.3 Manual UX smoke

**Status: PENDING.**

Manual device review should cover Pairing, Keyboard, Mouse, Function Keys, Extended Keys, Navigation Keys, Settings, backend switching, permission loss/recovery, and large-font visual usability.

---

## 10. Completion decision

### Automated completion

**PASS** for implementation SHA `953df07df97779c7cc85f3f9bc1acb1e77821c7d`.

- permanent CI: PASS;
- permanent API 28/30/31/34/35 real-screen matrix: PASS;
- static-analysis/style gates: PASS;
- no gate/baseline weakening used to obtain green status;
- no temporary hardening workflow/helper remains in the final implementation tree.

### Overall release acceptance

**OPEN — not yet eligible for final acceptance.**

The remaining blockers are physical/manual evidence, not an unresolved automated code failure. Final acceptance requires the required physical Classic exact-SHA result plus the BLE/manual smoke disposition described above.
