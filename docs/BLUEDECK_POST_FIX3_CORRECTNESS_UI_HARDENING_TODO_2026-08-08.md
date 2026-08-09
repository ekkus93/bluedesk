# BlueDeck Post-Fix3 Correctness & UI Hardening TODO

**Date:** 2026-08-08  
**Repository:** `ekkus93/bluedesk`  
**Reviewed source baseline:** `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3`  
**Specification:** `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_SPEC_2026-08-08.md`

---

## 0. Ralph-loop execution rules

This file is the implementation checklist for the post-Fix3 correctness/UI hardening pass.

### 0.1 General rules

- [x] Read the companion spec completely before changing code.
- [x] Preserve the valid Fix3 changes unless a task below explicitly requires changing their implementation.
- [x] Do not mark a task complete merely because the project compiles.
- [x] Do not mark a task complete until its required tests and acceptance criteria pass.
- [x] Do not silently skip a blocked task. Record the blocker and leave its checkbox open.
- [x] Do not replace an explicit failure with a fallback merely to keep the UI/service running.
- [x] Do not add broad exception suppression.
- [x] Do not add default production no-op methods to satisfy interface compilation.
- [x] Do not use nullable command dispatch as a silent failure path.
- [x] Do not weaken tests to make the current implementation pass.
- [x] Do not delete existing physical HID evidence.
- [x] Keep physical-device tests distinct from emulator/instrumented and JVM tests.
- [x] Every correctness-significant fallback must be explicit, bounded, observable, and tested.

### 0.2 Prohibited patterns

Before final acceptance, search production code for these patterns and review every occurrence:

```kotlin
catch (_: Exception) {
}
```

```kotlin
runCatching { criticalOperation() }
```

where failure is only logged and not reflected in state/result.

```kotlin
sender?.criticalOperation()
```

where `sender == null` means user input is lost.

```kotlin
fun operation() {}
```

as a default implementation of a user-visible backend operation.

```kotlin
resource ?: return
```

inside a command path where the missing resource means delivery failed.

- [x] For every remaining occurrence, document why it is safe.
- [x] Add a test when the safety justification depends on runtime behavior.

---

# Phase 1 — Establish baseline and preserve evidence

## Task 1.1 — Record implementation baseline

- [x] Record the current implementation-start SHA in the validation section at the bottom of this TODO.
- [x] Confirm the spec exists at the exact documented path.
- [x] Confirm this TODO exists at the exact documented path.
- [x] Confirm `docs/PHYSICAL_HID_TESTING.md` remains present.
- [x] Confirm `docs/BLUEDECK_RELEASE_CANDIDATE_FIX3_TODO.md` remains present.
- [x] Confirm `docs/UIUX_FIXES1_TODO.md` remains present.

Acceptance criteria:

- [x] The implementation begins from a known exact SHA.
- [x] No earlier validation/evidence document was deleted or rewritten to overclaim later work.

## Task 1.2 — Run baseline host gates

Run the repository’s canonical equivalents of:

```bash
./gradlew clean :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:ktlintCheck
./gradlew :app:detekt
```

- [x] Unit/JVM tests pass or existing failures are recorded before implementation.
- [x] Debug APK builds or existing failure is recorded.
- [x] Lint status recorded.
- [x] ktlint status recorded.
- [x] detekt status recorded.

Acceptance criteria:

- [x] New work is not blamed for a pre-existing failure without evidence.

---

# Phase 2 — Add authoritative backend runtime state

## Task 2.1 — Inventory current backend truth sources

Review at least:

- `MainActivity.kt`
- `BluetoothService.kt`
- `BleHogpService.kt`
- backend/sender/store state files
- backend transition planner

Identify every current source of truth for:

- selected backend,
- started service,
- bound service,
- sender availability,
- backend initialization readiness,
- connected device,
- discovery state.

- [x] Document the current sources in code comments or implementation notes where useful.
- [x] Identify all places where `serviceBound`, `bleHogpBound`, or equivalent booleans are used as backend authority.
- [x] Identify all places where `connectedDevice != null` is used as a proxy for input usability.

Acceptance criteria:

- [x] The implementation team knows which state must be consolidated rather than layering a second ambiguous model on top.

## Task 2.2 — Introduce backend runtime state types

Add an explicit model equivalent to:

```kotlin
sealed interface BackendRuntimeState {
    data object Stopped : BackendRuntimeState
    data class Starting(...) : BackendRuntimeState
    data class Ready(...) : BackendRuntimeState
    data class Failed(...) : BackendRuntimeState
    data class Stopping(...) : BackendRuntimeState
}
```

- [x] Add `BackendKind` if not already modeled cleanly.
- [x] Add explicit startup stages sufficient to identify start/bind/backend-init failures.
- [x] Add typed error representation.
- [x] Add host connection state or equivalent if runtime state alone is insufficient.
- [x] Ensure runtime state can distinguish selected backend from actually-ready backend.

Acceptance criteria:

- [x] `Ready` means a usable backend, not merely “service requested.”
- [x] `Failed` carries enough information for tests and diagnostics.

## Task 2.3 — Make runtime state authoritative

- [x] Introduce/refactor a controller/coordinator that owns backend lifecycle truth.
- [x] Stop deriving `currentBackend()` solely from Activity bind booleans.
- [x] Make Compose/UI observe derived store/controller state rather than independently reconstructing backend truth.
- [x] Add selectors/helpers such as `isInputUsable` if useful.

Acceptance criteria:

- [x] A started-but-unbound service cannot disappear from the app’s model of a live backend.
- [x] UI enablement can be computed from one coherent runtime model.

## Task 2.4 — Add state-machine tests

Add deterministic tests for at least:

- [x] Stopped → Starting.
- [x] Starting → Ready.
- [x] Starting → Failed.
- [x] Ready → Stopping → Stopped.
- [x] service loss from Ready.
- [x] illegal/conflicting second-backend start is rejected or serialized.

Acceptance criteria:

- [x] Tests would fail if backend truth reverted to binding flags only.

---

# Phase 3 — Add explicit backend capabilities and remove no-op APIs

## Task 3.1 — Define capability model

Create a backend capability model covering at least:

- [x] discovery,
- [x] explicit connect,
- [x] explicit disconnect,
- [x] Classic pairing/device list,
- [x] default-device operation,
- [x] device rename,
- [x] vertical scroll,
- [x] horizontal scroll,
- [x] middle click if backend/report-dependent,
- [x] host LED reports if relevant.

Acceptance criteria:

- [x] Classic and BLE capability sets are explicit and unit tested.

## Task 3.2 — Remove default production no-op `KeySender` methods

Audit `KeySender` and related interfaces.

- [x] Remove empty default methods for user-facing backend operations.
- [x] Split interfaces by capability where that produces a safer design.
- [x] Otherwise return typed `Unsupported` results.
- [x] Ensure every concrete backend explicitly declares what it supports.

Acceptance criteria:

- [x] Adding a new backend cannot accidentally compile while silently inheriting nonfunctional user actions.

## Task 3.3 — Replace silent nullable sender dispatch

Audit middleware for patterns such as:

```kotlin
sender?.sendKeyDown(...)
sender?.moveMouse(...)
sender?.leftClick()
```

- [x] Replace silent nullable dispatch for correctness-significant commands.
- [x] Missing sender becomes a typed runtime/command failure.
- [x] Reconcile backend/input-usable state when sender is unexpectedly absent.
- [x] Keep harmless optional callbacks nullable only where absence is genuinely expected and non-critical.

Acceptance criteria:

- [x] User input cannot be dropped solely because `sender` became null without any state/error consequence.

## Task 3.4 — Add command-result tests

Add tests for:

- [x] supported operation succeeds,
- [x] unsupported operation returns explicit unsupported,
- [x] absent sender returns explicit failure,
- [x] UI/store does not treat either unsupported or failed as success.

---

# Phase 4 — Make backend startup and switching transactional

## Task 4.1 — Refactor start sequence

Implement one serialized startup path conceptually covering:

1. permission/platform validation,
2. exclusion of another live backend,
3. foreground-service start,
4. bind,
5. listener installation,
6. sender installation,
7. backend-specific readiness,
8. `Ready` publication.

- [x] Make startup stages observable/testable.
- [x] Do not publish `Ready` before backend-specific initialization completes.

Acceptance criteria:

- [x] A service start alone is never treated as successful backend activation.

## Task 4.2 — Roll back failed bind

Reproduce the reviewed failure case:

- service start succeeds,
- `bindService` fails/throws/returns failure,
- previous code suppresses/logs the failure.

Repair it:

- [x] clear sender/listener state,
- [x] unbind if partially bound,
- [x] stop the started service,
- [x] clear connection/scanning state,
- [x] publish typed failure,
- [x] surface user-facing failure when start was user-triggered.

Acceptance criteria:

- [x] No foreground service remains alive after a failed bind.

## Task 4.3 — Remove critical `runCatchingLogged` suppression

Audit `runCatchingLogged` call sites.

- [x] Classify each as best-effort or correctness-significant.
- [x] Replace state-changing startup/bind/switch uses with explicit result propagation.
- [x] Keep `runCatchingLogged` only for truly non-critical work or teardown where suppression is justified.

Acceptance criteria:

- [x] Backend activation cannot fail only in disabled debug logs.

## Task 4.4 — Serialize backend switching

Implement Classic ⇄ BLE switching as:

- [x] mark current backend stopping,
- [x] release held input where possible,
- [x] clear sender,
- [x] detach listener,
- [x] unbind,
- [x] stop service/backend resources,
- [x] confirm local state reset,
- [x] then start target backend transactionally.

Acceptance criteria:

- [x] Two backend services cannot remain live due to an intermediate bind-state mismatch.

## Task 4.5 — Add lifecycle rollback tests

Tests must cover:

- [x] foreground service start failure,
- [x] bind failure after service start,
- [x] backend init failure after bind,
- [x] switch target failure after source shutdown,
- [x] rapid/repeated switch requests remain serialized,
- [x] no test path leaves both backends live.

---

# Phase 5 — Reconcile service loss, connection state, and UI usability

## Task 5.1 — Repair service disconnect handling

Audit `MainActivity.onServiceDisconnected` and equivalent callbacks.

On unexpected service loss:

- [x] clear command sender,
- [x] transition backend runtime state out of Ready,
- [x] clear/invalidate connected device state,
- [x] clear scanning state,
- [x] reset held keyboard/mouse local state,
- [x] disable input UI,
- [x] surface unexpected disconnect status/error.

Acceptance criteria:

- [x] The UI cannot remain `Connected` with a missing sender after service loss.

## Task 5.2 — Define input-usability selector

Create a single derived condition for Keyboard/Mouse availability.

It must include all required facts, such as:

- [x] backend Ready,
- [x] sender/transport installed,
- [x] connection state compatible with that backend,
- [x] required permission state valid.

Acceptance criteria:

- [x] Screens do not use `connectedDevice != null` alone to decide whether input works.

## Task 5.3 — Add regression tests

- [x] Start connected/usable.
- [x] Simulate service loss.
- [x] Assert sender cleared.
- [x] Assert connection unusable/cleared.
- [x] Assert Keyboard/Mouse navigation becomes unavailable.
- [x] Assert a subsequent command produces explicit failure rather than no-op.

---

# Phase 6 — Fix mouse Drag Lock lifecycle safety

## Task 6.1 — Fix stale cleanup capture

Audit `MouseScreen` Drag Lock disposal.

- [x] Replace stale `DisposableEffect(Unit)` capture with `rememberUpdatedState`, an appropriate effect key, or controller-owned held-button state.
- [x] Ensure cleanup sees the latest drag-lock value.

Acceptance criteria:

- [x] Navigating away after enabling Drag Lock requests mouse-up.

## Task 6.2 — Release held mouse state on all lifecycle exits

Handle:

- [x] Drag Lock turned off,
- [x] navigation away,
- [x] explicit host disconnect,
- [x] unexpected service disconnect,
- [x] backend switch,
- [x] Activity teardown when transport remains usable.

- [x] Always reset local held-button state even if release cannot be delivered.
- [x] If release delivery fails, record/surface that failure according to command policy.

Acceptance criteria:

- [x] No normal lifecycle transition can intentionally leave a remote mouse button held.

## Task 6.3 — Real Compose regression test

Use the production `MouseScreen` path:

- [x] enter Mouse screen,
- [x] enable Drag Lock,
- [x] verify mouse-down command,
- [x] navigate away/dispose screen,
- [x] verify exactly one mouse-up command,
- [x] verify local drag state reset.

Acceptance criteria:

- [x] Test fails against the reviewed stale-capture implementation.

---

# Phase 7 — Make discovery state authoritative

## Task 7.1 — Remove optimistic `isScanning=true`

Audit middleware/store actions that immediately set scanning state.

- [x] UI dispatches a scan request, not a fake successful scan state.
- [x] backend/controller validates capability and permission.
- [x] Classic `adapter.startDiscovery()` result is checked.
- [x] set `isScanning=true` only after accepted discovery.
- [x] set `isScanning=false` on immediate rejection.
- [x] set `isScanning=false` on finish/cancel/failure.

Acceptance criteria:

- [x] `startDiscovery()==false` cannot leave `Scanning for devices...` active.

## Task 7.2 — Make BLE scan unsupported in current workflow

Unless BLE discovery is genuinely implemented as a product feature:

- [x] BLE capability reports `supportsDiscovery=false`.
- [x] Pairing screen does not expose an active Classic Scan action in BLE mode.
- [x] dispatching a scan action in BLE mode returns explicit unsupported if it can still be reached programmatically.

## Task 7.3 — Add tests

- [x] successful Classic scan → scanning true,
- [x] failed Classic scan → scanning false + error,
- [x] scan finished → scanning false,
- [x] BLE scan → unsupported,
- [x] real Pairing screen never shows `Scanning...` after failed scan.

---

# Phase 8 — Make HID report delivery failures explicit

## Task 8.1 — Remove silent transport early returns

Audit `BluetoothHidTransport.send()` and related helpers.

Replace paths equivalent to:

```kotlin
currentDevice() ?: return
currentHid() ?: return
```

with typed failures.

- [x] no current device → explicit failure,
- [x] no HID proxy/module → explicit failure,
- [x] permission missing/revoked → explicit failure,
- [x] unsupported API → explicit failure.

## Task 8.2 — Check `BluetoothHidDevice.sendReport()` return value

- [x] Capture the Boolean result.
- [x] Treat `false` as report rejection/failure.
- [x] Notify state/controller if transport should no longer be considered usable.
- [x] Ensure failure is visible even when debug logging is disabled.

Acceptance criteria:

- [x] A rejected report cannot be reported as success.

## Task 8.3 — Unify keyboard and mouse failure behavior

- [x] Remove mouse-only log-and-ignore exception behavior.
- [x] Route keyboard and mouse transport errors through the same typed error/result pipeline.
- [x] Keep message severity/user presentation appropriate without hiding correctness failure.

## Task 8.4 — Tests

Add tests/mocks for:

- [x] device missing,
- [x] HID proxy missing,
- [x] permission revoked,
- [x] `sendReport()==false`,
- [x] keyboard exception,
- [x] mouse exception.

Acceptance criteria:

- [x] Every scenario yields explicit failure and no silent success.

---

# Phase 9 — Harden Classic HID registration

## Task 9.1 — Check immediate `registerApp()` result

Audit `BluetoothHidModule.registerApp`.

- [x] Capture `BluetoothHidDevice.registerApp(...)` Boolean.
- [x] If false, publish Classic initialization failure immediately.
- [x] Do not wait indefinitely for a callback after immediate rejection.
- [x] Roll back startup resources appropriately.

## Task 9.2 — Preserve async callback semantics

- [x] Immediate `true` means request accepted, not fully registered.
- [x] Async callback confirms success/failure.
- [x] Runtime state becomes Ready only after required registration success.

## Task 9.3 — Tests

- [x] immediate false → failed startup,
- [x] immediate true + callback success → continue/ready,
- [x] immediate true + callback failure → failed startup,
- [x] no false-ready intermediate state.

---

# Phase 10 — Harden BLE HOGP startup into a real readiness state machine

## Task 10.1 — Persist startup status independent of Activity listener timing

- [x] BLE service/backend exposes durable/queryable startup state.
- [x] Early failures occurring before Activity binding are not lost.
- [x] Installing an event listener after failure still allows Activity/controller to discover the failure.

Acceptance criteria:

- [x] BLE startup correctness does not depend on callback installation timing.

## Task 10.2 — Validate advertiser availability

- [x] Treat `bluetoothLeAdvertiser == null` as initialization failure.
- [x] Stop/rollback BLE backend.
- [x] Publish typed diagnostic.

## Task 10.3 — Validate GATT server creation

- [x] Treat `openGattServer(...) == null` as initialization failure.
- [x] Do not proceed as though GATT is available.
- [x] Roll back advertiser/GATT/service resources.

## Task 10.4 — Validate GATT service registration

- [x] Check immediate `addService(...)` acceptance where applicable.
- [x] Observe `onServiceAdded`/equivalent completion callbacks.
- [x] Require all mandatory HOGP services/characteristics to be registered before advertising-ready state.
- [x] Fail closed on registration error.

## Task 10.5 — Validate advertising start

- [x] Start advertising only after required GATT setup.
- [x] Observe advertising callback success/failure.
- [x] Publish `Ready` only after advertising success.
- [x] On advertising failure, stop service and close GATT resources.

## Task 10.6 — Cleanup BLE resources idempotently

- [x] stop advertising if active,
- [x] close GATT server,
- [x] clear service/callback references,
- [x] clear sender/readiness state,
- [x] make repeated cleanup safe,
- [x] suppress teardown exceptions only with explicit justification.

## Task 10.7 — BLE readiness tests

Cover at least:

- [x] permission denied at service entry,
- [x] advertiser null,
- [x] GATT server null,
- [x] GATT add-service rejected,
- [x] GATT service callback failure,
- [x] advertising callback failure,
- [x] full success → Ready,
- [x] failure before Activity listener install remains observable after bind.

Acceptance criteria:

- [x] BLE foreground service cannot remain apparently active while incapable of advertising/serving HOGP.

---

# Phase 11 — Repair Android API compatibility and permission consistency

## Task 11.1 — Audit all Bluetooth permission checks

Search at least for:

- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`
- `ACCESS_FINE_LOCATION`
- `checkSelfPermission`
- `ContextCompat.checkSelfPermission`

Review at least:

- [x] `PermissionPolicy`,
- [x] `PermissionGrantChecker`,
- [x] `DiscoveryController`,
- [x] `BluetoothService`,
- [x] `BluetoothHidTransport`,
- [x] Pairing/device-management code,
- [x] BLE service/backend code.

Acceptance criteria:

- [x] No lower layer independently imposes API31+ permission requirements on pre-31 devices.

## Task 11.2 — Centralize version-aware checks

- [x] Reuse `PermissionPolicy` / a central Android-facing checker for version-aware permission semantics.
- [x] API <31 Classic scan uses the appropriate legacy/location rule.
- [x] API 31+ scan/connect/advertise use Nearby Devices permissions as applicable.
- [x] Avoid checking permission constants on versions where they are not the governing runtime permission model.

## Task 11.3 — Decide API 26/27 support policy

Make an explicit product decision:

### Option A — Raise minSdk

- [x] Set minSdk to the lowest API level where the supported primary BlueDeck HID workflow is coherent.
- [x] Update README/docs.
- [x] Remove dead compatibility branches below the new minimum where appropriate.

### Option B — Keep API 26/27

- [x] Provide an actually usable supported backend for API 26/27.
- [x] Gate unsupported Classic HID functionality in UI/runtime.
- [x] Add API 26/27 tests.
- [x] Document backend limitations honestly.

Exactly one option must be completed.

Acceptance criteria:

- [x] App installation support and actual core-product support no longer contradict each other.

## Task 11.4 — API behavior tests

Add version-boundary tests for at least:

- [x] API 28,
- [x] API 30,
- [x] API 31,
- [x] API 34,
- [x] current high API target/test level.

If API 26/27 remain supported:

- [x] API 26,
- [x] API 27.

Test:

- [x] startup permissions,
- [x] Classic scan permissions,
- [x] Classic connection/send permissions,
- [x] BLE startup permissions,
- [x] unsupported platform/backend decision.

---

# Phase 12 — Make Pairing and input UI backend-aware

## Task 12.1 — Refactor Pairing screen by capabilities

Classic mode may expose:

- [x] Scan,
- [x] discovered devices,
- [x] paired devices,
- [x] explicit Connect/Disconnect,
- [x] Classic device-management actions.

BLE mode must expose:

- [x] BLE readiness state,
- [x] advertising state,
- [x] host-initiated pair/connect instructions,
- [x] connected-host status if known,
- [x] initialization failure/remediation text.

BLE mode must not present functioning-looking controls for unsupported Classic operations.

Acceptance criteria:

- [x] A user cannot tap Scan/Connect/Rename/etc. in BLE mode and trigger a silent no-op.

## Task 12.2 — Capability-drive Mouse controls

- [x] Vertical scroll shown/enabled only if supported.
- [x] Horizontal scroll shown/enabled only if supported.
- [x] Middle click capability handled explicitly if report/backend-dependent.
- [x] Do not derive availability solely from `hidSimplified` if backend capabilities differ.

Acceptance criteria:

- [x] BLE simplified/current report format cannot display unsupported scroll as a working control.

## Task 12.3 — Capability-drive navigation/input enablement

- [x] Keyboard tab uses `isInputUsable` or equivalent.
- [x] Mouse tab uses `isInputUsable` plus required mouse capability.
- [x] disconnected/unready nav feedback remains available.

## Task 12.4 — Production Compose tests

Using real screens:

- [x] Classic Pairing shows Classic workflow.
- [x] BLE Pairing shows host-initiated workflow.
- [x] BLE Pairing does not expose active Scan/Connect/Rename/default-device actions.
- [x] BLE Mouse hides/disables unsupported scrolling.
- [x] backend loss disables Keyboard/Mouse.

---

# Phase 13 — Fix safe device labels and stale runtime copy

## Task 13.1 — Remove unsafe direct `BluetoothDevice.name` reads from Compose

- [x] Find direct `.name` reads in composables.
- [x] Resolve labels in permission-aware controller/service/data-mapper layer.
- [x] Store safe display name plus stable fallback identifier in UI state.
- [x] Handle null names and permission revocation.

Acceptance criteria:

- [x] Compose cannot crash merely because `BLUETOOTH_CONNECT` was revoked before a device-name read.

## Task 13.2 — Fix BLE Settings copy

- [x] Remove/rewrite `Restart the app after changing` if backend changes are live-switched.
- [x] Describe actual switching behavior.
- [x] If restart truly becomes required after refactor, enforce/document it consistently instead of leaving ambiguous copy.

## Task 13.3 — Fix Linux runtime connection guidance

Search runtime strings/log/info callbacks for `bluetoothctl connect`.

- [x] Make DBus `ConnectProfile(HID)` the primary known-good Linux/BlueZ instruction.
- [x] Keep `bluetoothctl connect` only as fallback/diagnostic if useful.
- [x] Match `docs/PHYSICAL_HID_TESTING.md`.

Acceptance criteria:

- [x] Runtime guidance and docs no longer contradict each other.

---

# Phase 14 — Fix remaining Settings silent failures and excessive writes

## Task 14.1 — Battery optimization terminal failure

Audit the two-stage battery-optimization intent flow.

- [x] Keep app-specific intent first.
- [x] Keep general settings fallback if justified.
- [x] Replace terminal empty catch.
- [x] Show snackbar/dialog/message if both intents fail.
- [x] Log durable diagnostic independent of optional debug logging.

Acceptance criteria:

- [x] User-triggered battery optimization request cannot fail with zero feedback.

## Task 14.2 — IME overrides load-state model

- [x] Stop converting all exceptions to empty maps.
- [x] Distinguish empty-success from load-failure.
- [x] Preserve last-known-good data if useful and safe.
- [x] Show recoverable error state/action.
- [x] Log diagnostic cause.

Tests:

- [x] successful empty config,
- [x] successful populated config,
- [x] storage/read failure,
- [x] package-label resolution failure behavior.

## Task 14.3 — Debounce/defer slider persistence

For touchpad sensitivity, scroll speed, key-repeat delay, and similar sliders:

- [x] keep local transient slider state,
- [x] persist on `onValueChangeFinished` or tested debounce,
- [x] prevent a DataStore write per pointer movement,
- [x] ensure value survives recomposition/process persistence as expected.

Acceptance criteria:

- [x] dragging a slider does not launch an unbounded sequence of storage writes.

---

# Phase 15 — Harden IME text forwarding

## Task 15.1 — Define text-diff behavior

Replace/extend the current append/delete-only heuristic.

Handle at least:

- [x] append,
- [x] delete,
- [x] equal-length replacement,
- [x] suffix replacement,
- [x] composing/replacement-like update,
- [x] desynchronization/reset.

## Task 15.2 — Implement bounded deterministic diff/reset strategy

Choose one explicit strategy:

- [x] prefix/suffix diff + delete/retype,
- [x] constrained editor/reset behavior,
- [x] another documented deterministic approach.

Rules:

- [x] unsupported transformation cannot silently disappear,
- [x] excessive/unbounded diff must fail/reset safely,
- [x] text state remains synchronized after recovery.

## Task 15.3 — Tests

- [x] simple append,
- [x] multi-character append,
- [x] backspace/delete,
- [x] equal-length replacement,
- [x] replacement of a suffix,
- [x] composition-like replacement,
- [x] desync/reset.

Acceptance criteria:

- [x] Tests demonstrate behavior that the old `current.startsWith(previous)` heuristic did not cover.

---

# Phase 16 — Remove blocking sleeps from UI-sensitive command paths

## Task 16.1 — Audit `Thread.sleep`

Search production code for `Thread.sleep`.

- [x] Identify keyboard press/release sleeps.
- [x] Identify mouse click sleeps.
- [x] Identify lock-toggle sleeps.
- [x] Determine which calls can execute on UI dispatch.

## Task 16.2 — Move timing into async serialized command execution

Prefer:

- [x] suspend sender functions,
- [x] coroutine `delay`,
- [x] command queue/mutex,
- [x] dedicated worker context if required.

Acceptance criteria:

- [x] UI event handler does not block the main thread for HID timing delays.
- [x] down/up ordering remains deterministic.

## Task 16.3 — Timing tests

- [x] rapid key sends remain serialized,
- [x] rapid clicks produce discrete down/up pairs,
- [x] lock toggles preserve required timing/order,
- [x] cancellation/teardown does not strand local held state.

---

# Phase 17 — Reopen and verify remaining UIUX_FIXES1 concerns

## Task 17.1 — TASK-02 disconnected nav feedback

Use production `MainScreen`.

- [x] Verify tapping Keyboard while unusable/disconnected shows feedback.
- [x] Verify tapping Mouse while unusable/disconnected shows feedback.
- [x] Add real Compose/instrumented regression test.
- [x] Update `docs/UIUX_FIXES1_TODO.md` manual/test checkbox only after evidence exists.

## Task 17.2 — TASK-04 explicit key-cell height

Audit current shared key button implementation.

- [x] Define an explicit common key-cell height.
- [x] Apply it consistently to repeatable/non-repeatable cells.
- [x] Align empty grid placeholders to the same value.
- [x] Do not rely on undocumented Material default minimums for one branch while hard-coding another.

Acceptance criteria:

- [x] Grid row alignment is deterministic.

## Task 17.3 — TASK-07 Scroll Lock bookkeeping

- [x] Verify current Scroll Lock active colors and label in production screen.
- [x] Update stale unchecked task-body boxes in `docs/UIUX_FIXES1_TODO.md` only after verification.
- [x] Do not change historical meaning; simply reconcile implementation status.

## Task 17.4 — TASK-16 Navigation sizing visual verification

- [x] Verify Navigation keys align with the shared sizing contract.
- [x] Test normal font scale.
- [x] Test at least one larger accessibility font scale.
- [x] Ensure labels do not clip or force inconsistent row heights.

---

# Phase 18 — Replace misleading instrumented UI tests with real coverage

## Task 18.1 — Remove synthetic scan proof

Audit `UiComposeInstrumentedTest`.

- [x] Stop treating synthetic `TestScan()` as proof that `PairingScreen` works.
- [x] Replace with production screen/state/controller integration where feasible.

## Task 18.2 — Remove placeholder assertions

- [x] Find `assertTrue(true)` or equivalent placeholder tests.
- [x] Replace with meaningful assertion or delete the fake test if it asserts nothing.

Acceptance criteria:

- [x] Instrumented test count no longer includes tests that cannot fail meaningfully.

## Task 18.3 — Required real-screen regression suite

Add real Compose/instrumented tests for:

- [x] disconnected nav guard,
- [x] Classic Pairing controls,
- [x] BLE Pairing host-initiated workflow,
- [x] failed scan state reset,
- [x] service/backend loss disables input,
- [x] Drag Lock release on navigation,
- [x] BLE unsupported scroll controls,
- [x] safe connected-device label fallback,
- [x] at least one visible backend startup/send failure.

---

# Phase 19 — Add CI coverage for non-physical instrumented tests

## Task 19.1 — Inspect current workflow

Audit `.github/workflows/ci.yml`.

- [x] Preserve build.
- [x] Preserve JVM unit tests.
- [x] Preserve lint.
- [x] Preserve ktlint.
- [x] Preserve detekt.

## Task 19.2 — Add stable emulator/managed-device job

If GitHub-hosted or available self-hosted infrastructure supports it:

- [x] boot representative emulator/managed device,
- [x] run non-physical `connectedDebugAndroidTest`,
- [x] exclude opt-in physical HID tests by their existing gating mechanism,
- [x] upload useful test reports/artifacts on failure.

If infrastructure cannot support a stable emulator job:

- [x] N/A — stable permanent emulator infrastructure is available; no blocker path is required.
- [x] N/A — the canonical `connectedDebugAndroidTest` path runs in permanent CI.
- [x] N/A — instrumented coverage is run and recorded rather than inferred.

## Task 19.3 — API-boundary CI strategy

Add at least one lower-version and one modern-version instrumentation/API check if practical.

Suggested boundaries:

- [x] API 28 or 30,
- [x] API 31,
- [x] API 34+.

If full emulator matrix is too expensive:

- [x] N/A — the full API 28/30/31/34/35 emulator matrix is practical and permanent.
- [x] N/A — no reduced representative matrix is needed.

Acceptance criteria:

- [x] Critical real-screen regressions are automatically detected before merge/release where infrastructure permits.

---

# Phase 20 — Diagnostics and no-silent-failure sweep

## Task 20.1 — Add/consolidate typed error taxonomy

Ensure errors distinguish at least:

- [x] permission missing/revoked,
- [x] unsupported API/backend,
- [x] adapter unavailable/disabled,
- [x] foreground start failure,
- [x] bind failure,
- [x] service disconnect,
- [x] Classic registration rejection,
- [x] no HID device/proxy,
- [x] HID report rejection,
- [x] discovery rejection,
- [x] GATT server failure,
- [x] GATT service failure,
- [x] advertiser unavailable,
- [x] advertising failure,
- [x] unsupported capability,
- [x] settings/storage failure.

## Task 20.2 — Search for silent catches

Search production code for:

- `catch (_:`
- `catch (`
- `runCatching`
- `getOrNull`
- `getOrDefault`
- `getOrElse`

For each failure-swallowing occurrence:

- [x] classify harmless best-effort vs correctness-significant,
- [x] propagate/record correctness-significant failures,
- [x] comment narrowly justified teardown suppression.

## Task 20.3 — Search for nullable no-op dispatch

Search for:

- `sender?.`
- `eventListener?.`
- `advertiser?.`
- `gattServer?.`
- other `?.operation()` in state-changing backend paths.

- [x] Replace unsafe optional dispatch.
- [x] Retain genuinely optional observer notifications only when durable state exists independently.

## Task 20.4 — Search for silent early returns

Audit `?: return` and bare `return` in backend/transport methods.

- [x] Convert command/startup failure cases to explicit result/state transitions.
- [x] Leave only semantically legitimate no-work returns.

Acceptance criteria:

- [x] No known critical failure depends solely on `DebugLog` being enabled to be discoverable.

---

# Phase 21 — Documentation cleanup

## Task 21.1 — Document Classic vs BLE workflows

Update README/docs as appropriate:

- [x] Classic discovery/pair/connect model,
- [x] BLE advertising/host-initiated model,
- [x] backend capability differences,
- [x] scroll/report limitations where applicable.

## Task 21.2 — Document Android support policy

- [x] Document final minSdk/support decision.
- [x] Document relevant API31 permission split.
- [x] Avoid claiming support for an API level whose core workflow is unusable.

## Task 21.3 — Reconcile runtime Linux instructions

- [x] README matches `PHYSICAL_HID_TESTING.md`.
- [x] runtime messages match both.
- [x] DBus `ConnectProfile(HID)` remains primary Linux/BlueZ procedure.

## Task 21.4 — Reconcile historical TODO bookkeeping

- [x] Update only demonstrably stale `UIUX_FIXES1_TODO.md` task-body checkboxes after new evidence exists.
- [x] Do not rewrite Fix3 evidence to claim this hardening work was previously validated.
- [x] Add a note to Fix3 or release documentation if needed that post-Fix3 review superseded the earlier broad release-ready conclusion.

---

# Phase 22 — Full validation

## Task 22.1 — JVM/unit suite

Run:

```bash
./gradlew clean :app:testDebugUnitTest
```

- [x] PASS.
- [x] Record exact test count/result if available.

## Task 22.2 — Build

Run:

```bash
./gradlew :app:assembleDebug
```

- [x] PASS.

## Task 22.3 — Static analysis

Run:

```bash
./gradlew :app:lintDebug
./gradlew :app:ktlintCheck
./gradlew :app:detekt
```

- [x] lint PASS.
- [x] ktlint PASS.
- [x] detekt PASS.

## Task 22.4 — Non-physical instrumented suite

Run the canonical non-physical suite, normally:

```bash
./gradlew :app:connectedDebugAndroidTest
```

- [x] PASS on an unlocked emulator/device.
- [x] Physical tests remain skipped unless explicitly enabled.
- [x] Record device/API used.
- [x] Record test count.

## Task 22.5 — API matrix validation

Record results for the final supported matrix.

- [x] lower supported boundary,
- [x] API 30 where applicable,
- [x] API 31 permission boundary,
- [x] API 34,
- [x] current high API test device/emulator.

## Task 22.6 — Physical Classic HID validation

A new physical Classic HID run is required if this hardening pass changes Classic HID registration/report/connection behavior materially or if final release policy requires exact-SHA hardware evidence.

If required:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
  -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
  -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS> \
  -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress=<PHONE_BT_ADDRESS>
```

Use Linux/BlueZ DBus `ConnectProfile(HID)` as documented.

- [x] Required — exact-SHA Classic physical HID rerun is required because registration/report/connection behavior changed materially.
- [ ] PENDING — run physical Classic HID validation against `953df07df97779c7cc85f3f9bc1acb1e77821c7d` and record the result.
- [x] Existing physical HID evidence remains explicitly labeled historical; it is not current exact-SHA evidence.

## Task 22.7 — Manual UX smoke

On a real device where practical verify:

- [ ] fresh-install permission flow,
- [ ] Classic startup,
- [ ] BLE startup,
- [ ] backend live switching,
- [ ] BLE host-initiated pair/connect instructions,
- [ ] Classic scan failure feedback if reproducible,
- [ ] disconnect/service-loss feedback,
- [ ] Drag Lock navigation release,
- [ ] key-grid layout,
- [ ] larger font-scale navigation layout,
- [ ] Settings slider behavior,
- [ ] battery optimization failure/fallback behavior,
- [ ] IME replacement behavior.

Label each as:

- `PASS — manually verified on device`
- `PASS — unit/instrumented verified only`
- `PASS — physical HID verified`
- `PENDING — manual UX smoke needed`
- `FAIL — issue found`
- `N/A — not applicable`

Do not conflate categories.

---

# Phase 23 — Final no-regression and release acceptance

## Task 23.1 — Runtime integrity gate

All must be true:

- [x] only one backend can be live,
- [x] failed bind stops the service,
- [x] failed backend init rolls back resources,
- [x] backend switching is serialized,
- [x] service loss invalidates stale connected/input state,
- [x] sender absence cannot silently drop user commands.

## Task 23.2 — Capability integrity gate

- [x] no user-facing production operation relies on an empty default implementation,
- [x] Classic capabilities explicit,
- [x] BLE capabilities explicit,
- [x] BLE UI does not expose Classic-only operations as functional,
- [x] unsupported scrolling is not presented as working.

## Task 23.3 — Transport integrity gate

- [x] `sendReport()` false handled,
- [x] missing device/HID object handled,
- [x] permission revocation handled,
- [x] keyboard and mouse failures use same policy,
- [x] Classic `registerApp()` immediate false handled,
- [x] BLE readiness requires GATT + advertising success.

## Task 23.4 — Platform integrity gate

- [x] minSdk/support policy coherent,
- [x] lower-layer permission checks version-aware,
- [x] API-boundary tests green.

## Task 23.5 — UI integrity gate

- [x] Drag Lock cannot strand mouse-down through normal navigation/lifecycle transitions,
- [x] real production-screen Compose tests cover repaired paths,
- [x] safe device labels used,
- [x] stale BLE restart copy fixed,
- [x] Linux runtime guidance fixed,
- [x] Settings failures visible,
- [x] IME replacement behavior defined/tested,
- [x] key-grid sizing deterministic.

## Task 23.6 — Silent-failure gate

- [x] production empty catches reviewed,
- [x] correctness-significant `runCatching` reviewed,
- [x] correctness-significant nullable calls reviewed,
- [x] correctness-significant `?: return` reviewed,
- [x] remaining fallbacks are explicit and tested,
- [x] critical error visibility does not depend on debug logging.

## Task 23.7 — Validation integrity gate

- [x] JVM tests green,
- [x] build green,
- [x] lint green,
- [x] ktlint green,
- [x] detekt green,
- [x] required instrumented tests green,
- [x] CI green on exact final SHA,
- [x] physical/manual evidence accurately labeled,
- [x] final exact SHA recorded below.

---

# Phase 24 — Final evidence record

Do not fill these fields optimistically. Record actual results only.

## Implementation start

- Source review baseline: `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3`
- Spec commit: `21bc0daadb01fbd3a543afeced2484fded30c113`
- TODO commit: `73fed407b487e173958554d4a6e93a9d6d6515dd`
- Implementation-start SHA: `20e85fba1b1b167bb1fd96dbc1e3734cadd005a0`

## Final source

- Final implementation SHA: `953df07df97779c7cc85f3f9bc1acb1e77821c7d`
- Final documentation/evidence SHA: `a652c2edc4f78f41058625e50e46c52ec4ac1354` (evidence-record commit; this TODO reconciliation is a documentation-only follow-up)

## Automated evidence

- JVM/unit: `PASS — permanent CI run 31284953872 / job 93172012610; Gradle console did not print a stable aggregate JVM count`
- Build: `PASS — :app:assembleDebug on exact final implementation SHA`
- Lint: `PASS — :app:lintDebug`
- ktlint: `PASS — :app:ktlintCheck`
- detekt: `PASS — :app:detekt; no baseline regeneration or threshold relaxation`
- Instrumented/Compose: `PASS — permanent run 31284953866 across API 28/30/31/34/35; API 35 finished 107 tests, 13 physical tests skipped by design, 0 failed`
- CI run/job URL or IDs: `CI 31284953872 / 93172012610; instrumented matrix 31284953866 / API28 93172016678 / API30 93172016686 / API31 93172016691 / API34 93172016673 / API35 93172016657`

## API evidence

- API 26/27 disposition: `N/A — intentionally unsupported/not installable; product minimum is API 28`
- API 28: `PASS — job 93172016678`
- API 30: `PASS — job 93172016686`
- API 31: `PASS — job 93172016691`
- API 34: `PASS — job 93172016673`
- Current high API: `API 35 PASS — job 93172016657`

## Physical/manual evidence

- Physical Classic HID exact-SHA run: `PENDING — REQUIRED on 953df07df97779c7cc85f3f9bc1acb1e77821c7d`
- BLE device smoke: `PENDING — physical device smoke required`
- Manual UX smoke: `PENDING — real-device UX pass required`

---

# Current Ralph-loop disposition

- **Automated hardening:** PASS on exact implementation SHA `953df07df97779c7cc85f3f9bc1acb1e77821c7d`.
- **Permanent CI:** PASS — run `31284953872`, job `93172012610`.
- **Permanent instrumented matrix:** PASS — run `31284953866`, APIs 28/30/31/34/35.
- **Overall acceptance:** OPEN. Task 22.6 physical Classic exact-SHA validation and Task 22.7 / BLE real-device smoke remain pending.
- Permanent evidence: `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_EVIDENCE_2026-08-08.md` at evidence commit `a652c2edc4f78f41058625e50e46c52ec4ac1354`.

---

# Completion condition

This TODO is complete only when BlueDeck satisfies the companion specification’s core rule:

> A backend or HID operation must never quietly appear successful when it was unsupported, unavailable, rejected, disconnected, or otherwise not delivered.

A green build alone is not completion. Existing green tests alone are not completion. Every required state transition, failure path, UI behavior, and regression test above must either pass or remain explicitly open.