# BlueDeck Post-Fix3 Correctness & UI Hardening Specification

**Date:** 2026-08-08  
**Repository:** `ekkus93/bluedesk`  
**Reviewed baseline:** `master` at `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3`  
**Companion implementation plan:** `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_TODO_2026-08-08.md`

---

## 1. Purpose

This specification defines the post-Fix3 hardening work required before BlueDeck should be treated as a robust release candidate.

The existing codebase has several strong foundations: pure permission and backend planners, Redux-style state management, serialized keyboard sending, a foreground-service fail-closed helper, good physical Classic HID evidence, and a substantial set of JVM/instrumented tests. The 2026-08-08 review nevertheless identified a class of correctness problems that are more serious than ordinary polish bugs:

- runtime state can diverge from the actual Bluetooth backend,
- a failed bind can leave a service alive without the Activity knowing it,
- the UI can remain in a connected state while all input silently drops,
- production interfaces contain default no-op operations,
- BLE HOGP exposes Classic-only controls whose actions can silently do nothing,
- discovery state is optimistic rather than authoritative,
- transport failures are frequently log-only or ignored,
- BLE startup can appear alive while GATT/advertising is not actually usable,
- API 26–30 compatibility is inconsistent across permission layers,
- mouse drag lock can survive navigation and leave the remote host stuck mouse-down,
- several UI/error paths still degrade into silent fallback behavior,
- current instrumented UI tests do not exercise the real UI paths that contain these bugs.

This pass is therefore a **correctness and observability hardening pass first, and a UI polish pass second**.

The implementation MUST make backend state explicit, remove silent production no-ops, make failures observable, align UI capabilities with the active backend, and add regression tests that prove the repaired behavior.

---

## 2. Relationship to Earlier Work

This specification builds on, but does not discard, the work recorded in:

- `docs/BLUEDECK_RELEASE_CANDIDATE_FIX3_TODO.md`
- `docs/UIUX_FIXES1_TODO.md`
- `docs/PHYSICAL_HID_TESTING.md`

The following existing work is considered valid and SHOULD be preserved unless a later requirement in this specification necessarily changes its implementation:

- full-state permission re-checking after permission callbacks,
- API-compatible receiver registration,
- BLE connect/advertise entry permission guard,
- DBus `ConnectProfile(HID)` as the preferred Linux/BlueZ Classic HID connection procedure,
- BlueDeck branding cleanup,
- host-authoritative Caps Lock / Scroll Lock state,
- serialized keyboard report sending,
- foreground-service failure handling that stops instead of pretending success,
- the existing physical Classic HID test evidence.

This specification **supersedes the broad release-readiness conclusion** of Fix3. Fix3’s individual patches may remain correct while the current product as a whole still fails the stronger correctness invariants defined below.

---

## 3. Scope

### 3.1 In scope

This hardening pass covers:

1. backend lifecycle and state authority,
2. Classic-vs-BLE capability modeling,
3. startup, binding, switching, teardown, and rollback,
4. connection-state synchronization,
5. Bluetooth discovery correctness,
6. HID send-result handling,
7. BLE HOGP readiness and failure handling,
8. Classic HID registration result handling,
9. Android API compatibility and permission consistency,
10. mouse-button lifecycle safety,
11. backend-aware UI behavior,
12. error visibility and elimination of silent fallback paths,
13. IME bridge edge cases,
14. Settings persistence/error handling,
15. runtime help text consistency,
16. meaningful Compose/instrumented regression tests,
17. CI expansion and release evidence.

### 3.2 Out of scope

Unless required by a correctness repair, this pass MUST NOT:

- redesign the entire visual identity,
- change application ID, namespace, or Kotlin package names,
- rewrite the HID descriptors merely for style,
- replace Redux/store architecture wholesale,
- remove proven physical HID tests,
- add cloud services or network dependencies,
- introduce a new Bluetooth stack,
- add compatibility shims that silently preserve broken behavior,
- mark unsupported behavior as successful simply to preserve an old UI flow.

---

## 4. Global Correctness Invariants

These invariants are release-blocking.

### INV-01 — One active backend

At most one Bluetooth backend service may be in a live runtime state at a time.

A service that was started but failed to bind, failed initialization, or lost its controlling connection still counts as live until it has been explicitly stopped.

The implementation MUST NOT infer backend exclusivity solely from Activity binding booleans.

### INV-02 — UI state must reflect backend reality

The UI MUST NOT display `Connected` or enable HID input merely because a stale `connectedDevice` object remains in Redux state.

A usable connection requires all of the following to be true:

- the intended backend is the active backend,
- that backend is in a ready runtime state,
- the backend reports a connected/usable host where the backend semantics require one,
- a usable command transport/sender exists,
- required permissions are currently granted.

If any required condition becomes false, the store MUST reconcile promptly.

### INV-03 — No silent command success

A user-triggered backend operation MUST produce one of:

- success,
- explicit failure,
- explicit unsupported result,
- explicit cancellation.

It MUST NOT become a silent no-op because a nullable sender was absent, a default interface method was empty, or an exception was caught only for optional debug logging.

### INV-04 — Backend capabilities are explicit

Classic HID and BLE HOGP have different interaction models. The UI MUST derive available actions from explicit backend capabilities rather than assuming all `KeySender` operations exist for all backends.

### INV-05 — Critical startup is transactional

Backend startup MUST either reach a declared ready state or roll back all partially created runtime resources.

No failed startup path may leave behind:

- a foreground service,
- a bound connection,
- a stale sender,
- a stale connected device,
- a stale scanning flag,
- active advertising,
- an open GATT server,
- a registered Classic HID app,
- UI state claiming the backend is ready.

### INV-06 — Failure visibility does not depend on debug logging

`DebugLog` may provide diagnostics, but correctness-significant failures MUST still update runtime/store state and/or produce user-visible feedback when relevant.

Turning debug logging off MUST NOT make critical failures disappear semantically.

### INV-07 — Unsupported is not failure and is not success

Backend operations that do not exist by design MUST be represented as unsupported capabilities. They MUST NOT be modeled as empty successful methods.

### INV-08 — Teardown releases held input

Navigation, backend switching, service loss, disconnect, Activity teardown, and relevant composable disposal MUST not leave keyboard modifiers or mouse buttons logically held on the remote host when BlueDeck can still send a release.

Where a release cannot be delivered because transport has already failed, local state MUST still be reset and the failure MUST be observable.

### INV-09 — Android-version behavior is deliberate

Every claimed supported API level MUST have a coherent permission and backend path.

The project MUST either:

- fully support its declared `minSdk`, including a usable default backend path, or
- raise `minSdk` / gate unsupported backend modes explicitly.

### INV-10 — Tests verify real behavior

A test that checks a synthetic composable or optimistic state update does not count as coverage for a production UI/backend interaction bug.

Regression tests MUST exercise the real planner/controller/screen/state path appropriate to the bug.

---

## 5. Authoritative Runtime State Model

### 5.1 Required model

Introduce one explicit runtime state representation for the active backend. The exact type names may vary, but the semantics MUST cover at least:

```kotlin
sealed interface BackendRuntimeState {
    data object Stopped : BackendRuntimeState

    data class Starting(
        val backend: BackendKind,
        val stage: StartupStage,
    ) : BackendRuntimeState

    data class Ready(
        val backend: BackendKind,
        val capabilities: BackendCapabilities,
    ) : BackendRuntimeState

    data class Failed(
        val backend: BackendKind,
        val stage: StartupStage?,
        val error: BackendError,
    ) : BackendRuntimeState

    data class Stopping(
        val backend: BackendKind,
    ) : BackendRuntimeState
}
```

A separate connection state MAY be nested in or paired with runtime state, for example:

```kotlin
sealed interface HostConnectionState {
    data object NotApplicable : HostConnectionState
    data object Disconnected : HostConnectionState
    data class Connecting(val device: DeviceRef) : HostConnectionState
    data class Connected(val device: DeviceRef) : HostConnectionState
    data class Failed(val error: BackendError) : HostConnectionState
}
```

The architecture MUST make it impossible for `currentBackend()` to be determined only by local bind flags.

### 5.2 State authority

Runtime state SHOULD be owned by a controller/coordinator that has enough information to know:

- selected backend,
- started service,
- bind status,
- backend initialization/readiness,
- installed event listener,
- installed sender/command port,
- stop/rollback completion.

Compose screens MUST observe state. They MUST NOT reconstruct backend truth from unrelated booleans.

### 5.3 Failure transitions

Any unrecoverable startup/runtime error MUST transition to `Failed` or `Stopped` as defined by the controller contract.

A failure state MUST carry a typed error suitable for:

- logging,
- tests,
- state reconciliation,
- user-visible error text when appropriate.

---

## 6. Backend Capability Model

Define explicit capabilities for the active backend.

Suggested shape:

```kotlin
data class BackendCapabilities(
    val supportsDiscovery: Boolean,
    val supportsExplicitConnect: Boolean,
    val supportsExplicitDisconnect: Boolean,
    val supportsClassicPairing: Boolean,
    val supportsPairedDeviceList: Boolean,
    val supportsDefaultDevice: Boolean,
    val supportsDeviceRename: Boolean,
    val supportsVerticalScroll: Boolean,
    val supportsHorizontalScroll: Boolean,
    val supportsMiddleClick: Boolean,
    val supportsHostLedReports: Boolean,
)
```

Use richer enums/interfaces if they communicate the model more safely.

### 6.1 Classic HID capability expectations

Classic HID normally supports the current device discovery/paired-device/explicit connection workflow, subject to Android-version and permission availability.

### 6.2 BLE HOGP capability expectations

BLE HOGP is host-initiated/advertising-oriented in the current implementation. Therefore the app MUST NOT present Classic-only operations as though they work.

At minimum, BLE mode SHOULD replace Classic Scan/Pair/Connect UI with status/instructions such as:

- BlueDeck is advertising,
- open Bluetooth settings on the host,
- select/pair/connect to BlueDeck from the host,
- current advertising/readiness state,
- connected host state if available.

If scrolling is not supported by the current BLE report format, scroll controls MUST be hidden or disabled with an explicit reason.

---

## 7. Command Port / `KeySender` Contract

### 7.1 Remove dangerous default no-ops

Production command interfaces MUST NOT define empty defaults for operations such as:

- clicks,
- scrolling,
- discovery,
- connect/disconnect,
- pairing/device management,
- rename/default-device operations.

Use one of these patterns instead:

1. required abstract methods for universally supported commands,
2. separate capability-specific interfaces,
3. a typed result such as `Unsupported(Capability.X)` for optional commands.

### 7.2 Nullable sender calls

Patterns such as:

```kotlin
sender?.sendKeyDown(...)
sender?.moveMouse(...)
sender?.leftClick()
```

MUST NOT be used for user-triggered production operations when absence of the sender means the command cannot be delivered.

The missing-sender case MUST reconcile runtime state and produce a typed failure.

### 7.3 Suggested result model

```kotlin
sealed interface CommandResult {
    data object Success : CommandResult
    data class Unsupported(val capability: Capability) : CommandResult
    data class Failed(val error: BackendError) : CommandResult
}
```

Fire-and-forget APIs are acceptable only for operations whose failure is genuinely irrelevant to correctness. HID input delivery does not fall into that category.

---

## 8. Transactional Backend Lifecycle

### 8.1 Start sequence

A backend start SHOULD conceptually be:

1. verify current permissions and platform support,
2. ensure no other backend is live,
3. start foreground service,
4. bind service,
5. install event listener,
6. obtain/install command sender,
7. wait for backend-specific initialization readiness,
8. publish `Ready` state,
9. then enable backend-dependent UI.

### 8.2 Rollback

If any stage fails:

1. clear command sender,
2. detach listener where needed,
3. unbind if bound,
4. stop the partially started service,
5. close backend resources,
6. clear stale connection/scanning state,
7. publish a typed failure,
8. keep the other backend stopped unless an explicit, policy-approved fallback is being performed.

### 8.3 Backend switching

Switching Classic ⇄ BLE MUST be serialized.

Required ordering:

1. stop current backend completely,
2. verify stop completion / clear state,
3. start target backend transactionally,
4. publish target readiness only after success.

The implementation MUST NOT start backend B merely because backend A’s Activity binding boolean is false.

### 8.4 Fallbacks

A fallback is allowed only if all are true:

- the fallback is explicitly part of product policy,
- the triggering failure is known,
- the user is informed when the fallback materially changes behavior,
- state records the backend actually selected,
- the failed backend is fully stopped first,
- the fallback cannot create two live services.

Silent fallback is prohibited.

---

## 9. Service Disconnection and Connection-State Reconciliation

When Android reports a service disconnect or backend loss, the app MUST atomically reconcile all state that depends on that backend.

At minimum:

- clear sender/command port,
- mark backend not ready or failed,
- clear or invalidate `connectedDevice` when it can no longer be considered usable,
- clear scanning state,
- release/reset local held-input state,
- disable Keyboard/Mouse controls until readiness is restored,
- surface a meaningful status/error if the disconnect was unexpected.

The UI MUST NOT remain in `Connected` solely because the old `BluetoothDevice` object remains stored.

---

## 10. Discovery State

### 10.1 Backend authority

The Redux/store layer MUST NOT optimistically set `isScanning=true` before knowing that the backend accepted discovery.

The authoritative sequence SHOULD be:

1. UI requests scan,
2. controller validates capability/permission,
3. Classic backend calls `adapter.startDiscovery()`,
4. only a successful return/event sets `isScanning=true`,
5. failure produces `isScanning=false` and an error,
6. discovery-finished/cancelled events always reset state.

### 10.2 BLE mode

If the BLE backend does not implement Classic discovery, the Scan control MUST not be active in BLE mode.

`Scanning for devices...` MUST never be shown in response to a BLE no-op.

---

## 11. HID Transport Failure Contract

### 11.1 No silent early returns

Transport paths such as:

```kotlin
currentDevice() ?: return
currentHid() ?: return
```

MUST become explicit typed failures when invoked for an input command.

### 11.2 `sendReport()` result

The Boolean result from `BluetoothHidDevice.sendReport(...)` MUST be checked.

A `false` result is not success. It MUST produce a failure result/event and reconcile state if it indicates the transport is no longer usable.

### 11.3 Keyboard/mouse symmetry

Keyboard and mouse delivery failures MUST follow the same error policy.

The current pattern where some keyboard exceptions reach `onError` while mouse failures can be log-only is prohibited.

### 11.4 Permissions

A missing or revoked required Bluetooth permission during send MUST:

- return a typed permission failure,
- update backend usability state,
- guide the user toward remediation when relevant.

It MUST NOT be only a disabled debug log entry.

---

## 12. Classic HID Registration

`BluetoothHidDevice.registerApp(...)` has an immediate Boolean result. The code MUST inspect it.

If registration is rejected immediately:

- do not continue as though async registration is pending,
- emit a typed initialization failure,
- roll back Classic startup resources as appropriate,
- expose a useful diagnostic.

The later callback remains authoritative for asynchronous registration outcome when the immediate call is accepted.

---

## 13. BLE HOGP Readiness State Machine

The BLE foreground service is not `Ready` merely because `onCreate()` returned without a permission exception.

The startup sequence MUST verify at least:

1. adapter is present/enabled as required,
2. `bluetoothLeAdvertiser` is non-null,
3. GATT server opens successfully,
4. required GATT services/characteristics are added successfully,
5. service-add callbacks confirm registration where the Android API is asynchronous,
6. advertising is started,
7. advertising callback reports success,
8. only then publish BLE `Ready`.

Any failure MUST:

- stop advertising if started,
- close GATT server,
- clear callbacks/listeners as needed,
- stop foreground service when the backend cannot serve its purpose,
- publish a typed error that remains available after an early failure,
- avoid relying solely on a listener that may not yet have been installed by the Activity.

### 13.1 Early-event durability

Because BLE initialization can fail before Activity binding completes, backend startup status/error MUST be queryable after binding or stored in an observable state holder. A one-shot callback to a not-yet-installed listener is insufficient.

---

## 14. Android API Compatibility and Permission Policy

### 14.1 Central rule

Lower layers MUST NOT bypass the version-aware permission policy with unconditional checks for Android 12+ permission constants.

For API <31, Classic paths MUST use the legacy permission model appropriate to the operation. For API 31+, use the Nearby Devices permission model.

### 14.2 Review targets

Audit and repair all direct checks/calls in at least:

- `DiscoveryController`,
- `BluetoothService`,
- `BluetoothHidTransport`,
- pairing/device-management code,
- any sender/backend helper that checks `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, or `BLUETOOTH_ADVERTISE`.

### 14.3 API 26–27 disposition

Classic Android HID-device APIs used by BlueDeck require platform support not available on all currently declared API levels.

The implementation MUST choose and document one coherent product policy:

**Option A — raise `minSdk` to the minimum API level on which the supported primary workflow is actually usable**, or

**Option B — retain API 26/27 but explicitly provide a usable supported backend on those levels, with UI/backend gating and tests.**

Installing successfully on API 26/27 while the default/core HID workflow cannot initialize is not acceptable.

### 14.4 Required API matrix

At minimum, automated or explicitly documented validation MUST cover semantic behavior for:

- API 28,
- API 30,
- API 31,
- API 34,
- current high API used by existing test devices/emulators.

If API 26/27 remain supported, they MUST also be included.

---

## 15. Mouse Drag-Lock Safety

### 15.1 Disposal bug

The Drag Lock cleanup MUST always observe the current drag-lock value. A `DisposableEffect(Unit)` cleanup that captures an initial `false` value is insufficient.

Use `rememberUpdatedState`, an effect key that tracks the relevant state, or a controller-level held-button model.

### 15.2 Required release triggers

A held left mouse button MUST be released when possible on:

- Drag toggle off,
- leaving `MouseScreen`,
- backend switch,
- disconnect,
- unexpected service disconnect,
- Activity teardown when the transport is still available.

Local held-button state MUST reset even if the release cannot be delivered.

### 15.3 Test requirement

A real Compose/instrumented regression test MUST reproduce:

1. enter Mouse screen,
2. enable Drag Lock / send mouse-down,
3. navigate away,
4. assert a mouse-up command was requested exactly once,
5. assert local drag state is reset.

---

## 16. UI / UX Requirements

### 16.1 Backend-aware Pairing screen

The Pairing screen MUST be backend-aware.

Classic mode MAY show:

- Scan,
- discovered devices,
- paired devices,
- explicit Connect/Disconnect,
- Classic-specific management controls.

BLE mode SHOULD instead show:

- BLE backend readiness,
- advertising state,
- host-initiated pairing instructions,
- connected-host state if known,
- explicit failure/remediation text when advertising/GATT setup fails.

Classic-only controls MUST not remain clickable in BLE mode.

### 16.2 Input controls

Keyboard and Mouse tabs MUST only be enabled when the runtime state proves input can be delivered.

The nav guard snackbar MAY remain, but its condition MUST derive from backend usability rather than only `connectedDevice != null`.

### 16.3 Scroll controls

Scroll UI MUST derive from backend/report capabilities.

If BLE uses a report format without wheel/pan fields, vertical/horizontal scroll controls MUST not appear functional.

### 16.4 Safe device labels

Compose MUST NOT directly depend on an unsafe `BluetoothDevice.name` read where runtime permission revocation could throw.

Resolve user-facing device labels in a permission-aware service/controller/data-mapping layer. Store a safe display label/identifier in UI state.

### 16.5 Key-grid sizing

Shared key cells and placeholders MUST use an explicit, common height contract rather than relying on Material defaults while spacers use hard-coded dimensions.

The existing UIUX Task 04 / Task 16 visual behavior MUST be revalidated against the current shared button implementation.

### 16.6 Settings BLE copy

Remove stale copy that says the app must be restarted after changing BLE HOGP if the current implementation live-switches backends.

The text MUST describe actual behavior.

### 16.7 Runtime Linux instructions

Runtime user-facing Classic/Linux guidance MUST match `docs/PHYSICAL_HID_TESTING.md`:

- prefer DBus `ConnectProfile(HID)`,
- explain that generic `bluetoothctl connect` may not open the HID profile,
- use `bluetoothctl connect` only as fallback/diagnostic guidance if retained.

---

## 17. Settings and Error-Handling Requirements

### 17.1 Battery optimization flow

A fallback from the app-specific battery-optimization intent to the general settings screen is acceptable.

A terminal empty catch after both paths fail is not.

The final failure MUST produce at least:

- diagnostic logging that does not depend on debug mode, and
- user-visible feedback because the user explicitly requested the action.

### 17.2 IME override loading

Failure to read/resolve IME overrides MUST NOT be indistinguishable from a legitimate empty configuration.

Represent load failure separately, for example:

```kotlin
sealed interface ImeOverrideLoadState {
    data object Loading : ImeOverrideLoadState
    data class Loaded(...) : ImeOverrideLoadState
    data class Failed(val error: ...) : ImeOverrideLoadState
}
```

The UI may retain last-known-good data where appropriate, but MUST disclose the failure.

### 17.3 Slider persistence

Settings sliders SHOULD keep local transient drag state and persist on `onValueChangeFinished` or an appropriately debounced policy.

Do not launch a DataStore write for every pointer movement unless there is a measured reason to require it.

---

## 18. IME Bridge Correctness

The current append/delete heuristic is insufficient for all modern IME behavior.

The implementation MUST define behavior for:

- append,
- deletion,
- equal-length replacement,
- composing-text replacement,
- selection/replacement where visible text changes without a simple prefix relationship,
- desynchronization between remembered text and current editor value.

A full arbitrary editor protocol is not required for this pass, but unsupported transformations MUST NOT disappear silently.

Acceptable strategies include:

- computing a bounded prefix/suffix diff and sending the necessary delete/retype sequence,
- constraining the IME field behavior and explicitly resetting on unsupported composition,
- detecting unsupported transformations and showing a recoverable error/reset path.

Tests MUST include at least one equal-length replacement and one composing/replacement-like case.

---

## 19. Timing / Blocking Requirements

Synchronous `Thread.sleep(...)` on paths that may execute from UI dispatch is discouraged and SHOULD be removed from HID command sequencing.

Prefer:

- coroutine delay,
- serialized command queue,
- backend-owned suspend operations,
- a dedicated dispatcher where genuinely blocking platform operations are unavoidable.

Keyboard and mouse press/release sequencing MUST remain deterministic.

---

## 20. Failure and Fallback Policy

The following patterns are prohibited in correctness-significant production paths unless accompanied by an explicit justification comment and test:

### 20.1 Prohibited silent patterns

```kotlin
catch (_: Exception) {
}
```

```kotlin
runCatching { criticalOperation() }
    .onFailure { DebugLog.d(...) }
```

when failure does not propagate into runtime/store state.

```kotlin
sender?.criticalCommand()
```

when `sender == null` means the user command was dropped.

```kotlin
fun unsupportedProductionOperation() {}
```

as a default interface implementation.

```kotlin
resource ?: return
```

inside a command path where absence means delivery failed.

### 20.2 Allowed best-effort behavior

Best-effort cleanup MAY suppress a secondary teardown exception if:

- the primary state transition has already been recorded,
- the exception cannot change user-visible correctness,
- it is logged through a durable diagnostic path,
- a comment explains why suppression is safe.

### 20.3 Fallback checklist

Any fallback MUST answer all of these in code or testable policy:

1. What failed?
2. Why is the fallback safe?
3. Is the failed resource fully shut down?
4. Is the user-visible behavior different?
5. If yes, how is the user informed?
6. What state records the fallback?
7. How is this behavior tested?

---

## 21. Diagnostics and Error Taxonomy

Introduce or consolidate a typed error taxonomy sufficient to distinguish at least:

- permission missing/revoked,
- platform/API unsupported,
- Bluetooth adapter unavailable/disabled,
- foreground start failure,
- bind failure,
- service disconnected,
- Classic HID registration rejected,
- Classic HID registration callback failure,
- no current HID device,
- HID report rejected,
- discovery start rejected,
- GATT server unavailable,
- GATT service registration failed,
- BLE advertiser unavailable,
- BLE advertising start failed,
- unsupported backend capability,
- settings/storage failure.

Errors MAY carry a user-safe message and a diagnostic cause separately.

The UI does not need to expose stack traces or internal details.

---

## 22. Testing Strategy

### 22.1 JVM/unit tests

Add deterministic unit coverage for:

- backend transition state machine,
- start failure rollback planning,
- backend switch serialization,
- capability sets for Classic and BLE,
- unsupported command behavior,
- sender-missing command failure,
- discovery success/failure state transitions,
- service-disconnect reconciliation,
- API-version permission policy,
- BLE startup-stage state transitions using extracted pure logic where practical,
- IME diff/replacement logic,
- safe UI enablement selectors.

### 22.2 Real Compose tests

Tests MUST use production composables/screens for UI behavior under review.

Required cases include:

- disconnected Keyboard/Mouse nav guard,
- Classic Pairing shows Classic controls,
- BLE Pairing hides/disables Classic-only controls and shows host-initiated guidance,
- failed scan does not leave `Scanning...`,
- sender/backend loss disables input UI,
- Drag Lock sends release on navigation/disposal,
- unsupported scroll controls are absent/disabled in BLE mode,
- runtime error is visible for at least one startup/send failure path.

The existing synthetic `TestScan()` coverage MUST NOT be considered sufficient.

A placeholder test such as `assertTrue(true)` MUST be removed or replaced.

### 22.3 Service/controller tests

Where Android framework objects make pure unit tests impractical, extract policy/state logic and use instrumented tests for integration boundaries.

Required integration scenarios include:

- service start succeeds but bind fails → service is stopped,
- service disconnect clears sender and usable connection state,
- BLE advertiser unavailable → backend does not become ready,
- GATT server unavailable → backend does not become ready,
- advertising callback failure → backend does not become ready,
- Classic `registerApp()` immediate false → startup fails,
- runtime permission revocation during operation → explicit error/state transition.

### 22.4 API matrix

At least semantic/unit coverage MUST encode the API behavior differences. CI SHOULD add emulator/instrumented coverage at representative API boundaries if practical.

### 22.5 Physical HID

Preserve the existing physical Classic HID procedure and evidence.

A new physical run is required only when implementation changes touch Classic HID report construction/registration/connection behavior or when final release acceptance intentionally requires a fresh exact-SHA hardware record.

If a new run is performed, record it against the exact commit SHA.

---

## 23. CI Requirements

Current CI runs build, JVM tests, lint, ktlint, and detekt. That is useful but insufficient for the UI/runtime bugs in this specification.

The final CI design SHOULD include a stable instrumented test job, using an emulator or managed device, for non-physical tests.

At minimum CI MUST prevent regression of:

- real Compose navigation state,
- backend-aware Pairing UI,
- Drag Lock disposal,
- scan failure state,
- backend-loss UI reconciliation.

Physical Bluetooth HID tests remain opt-in/manual unless the project has reliable dedicated hardware automation.

CI MUST distinguish:

- host/JVM tests,
- emulator/instrumented tests,
- physical HID tests,
- manual UX smoke.

No category may be reported as another.

---

## 24. Documentation Requirements

Update documentation so it accurately reflects the implemented runtime model.

At minimum:

- document Classic vs BLE interaction differences,
- document supported Android API range after the compatibility decision,
- document BLE host-initiated pairing/advertising behavior,
- keep Linux Classic HID `ConnectProfile(HID)` guidance consistent in runtime and docs,
- record exact validation SHA(s),
- correct stale checkbox bookkeeping in `docs/UIUX_FIXES1_TODO.md` where implementation and task-body state conflict,
- do not rewrite historical evidence to claim tests that were not run.

---

## 25. UIUX_FIXES1 Disposition

The 2026-08-08 review found the earlier UIUX round mostly implemented, but this hardening pass MUST explicitly disposition the remaining concerns:

- **TASK-02:** implementation exists; add real production Compose/instrumented verification of the disconnected navigation guard.
- **TASK-04:** revalidate explicit key-cell height in the current shared button implementation; do not rely on the historical checkbox alone.
- **TASK-07:** code appears implemented but task-body checkboxes are stale; fix bookkeeping after verification.
- **TASK-16:** revalidate Navigation-key sizing visually/instrumentally in conjunction with the shared key-height contract.

All other earlier UIUX tasks may remain closed unless regression tests reveal otherwise.

---

## 26. Release Acceptance Gates

BlueDeck MUST NOT be called ready under this specification until all of the following are true.

### Runtime integrity

- only one backend can remain live,
- failed start/bind rolls back the service,
- backend switching is serialized,
- service disconnect cannot leave stale connected/usable UI state,
- sender absence cannot silently drop commands.

### Capability integrity

- no production default no-op backend methods remain for user-facing actions,
- Classic and BLE capabilities are explicit,
- BLE UI does not expose Classic-only actions as functional,
- unsupported scroll behavior is represented honestly.

### Transport integrity

- `sendReport()` Boolean results are checked,
- missing device/HID objects are explicit failures,
- keyboard and mouse errors follow the same policy,
- Classic `registerApp()` immediate result is handled,
- BLE only becomes ready after confirmed GATT/advertising initialization.

### Platform integrity

- API compatibility policy is coherent with `minSdk`,
- permission checks are version-aware throughout lower layers,
- representative API-boundary tests pass.

### UI integrity

- Drag Lock cannot strand mouse-down on navigation,
- real Pairing/Main/Mouse screen tests cover the repaired paths,
- device labels are permission-safe,
- stale BLE/runtime help text is corrected,
- Settings and IME failures are not silently converted to empty/no-op state.

### Validation integrity

- JVM tests green,
- assemble green,
- lint green,
- ktlint green,
- detekt green,
- required instrumented/Compose tests green,
- physical/manual evidence is labeled accurately,
- final documentation records the exact validated SHA.

---

## 27. Definition of Done

This hardening pass is complete when BlueDeck has a single authoritative backend runtime model, explicit backend capabilities, transactional startup/teardown, observable command failures, coherent Android-version behavior, backend-aware UI, lifecycle-safe held-input handling, and regression tests that exercise the actual screens and service boundaries where the reviewed bugs occurred.

The standard is not merely that the app builds or that existing tests remain green. The standard is that the app **cannot quietly pretend an input/backend operation succeeded when it did not**, and that its UI communicates the real capabilities and state of the active Bluetooth backend.