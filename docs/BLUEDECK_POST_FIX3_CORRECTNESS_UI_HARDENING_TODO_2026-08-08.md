# BlueDeck Post-Fix3 Correctness & UI Hardening TODO

**Date:** 2026-08-08  
**Repository:** `ekkus93/bluedesk`  
**Reviewed source baseline:** `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3`  
**Specification:** `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_SPEC_2026-08-08.md`

---

## 0. Ralph-loop execution rules

This file is the implementation checklist for the post-Fix3 correctness/UI hardening pass.

### 0.1 General rules

- [ ] Read the companion spec completely before changing code.
- [ ] Preserve the valid Fix3 changes unless a task below explicitly requires changing their implementation.
- [ ] Do not mark a task complete merely because the project compiles.
- [ ] Do not mark a task complete until its required tests and acceptance criteria pass.
- [ ] Do not silently skip a blocked task. Record the blocker and leave its checkbox open.
- [ ] Do not replace an explicit failure with a fallback merely to keep the UI/service running.
- [ ] Do not add broad exception suppression.
- [ ] Do not add default production no-op methods to satisfy interface compilation.
- [ ] Do not use nullable command dispatch as a silent failure path.
- [ ] Do not weaken tests to make the current implementation pass.
- [ ] Do not delete existing physical HID evidence.
- [ ] Keep physical-device tests distinct from emulator/instrumented and JVM tests.
- [ ] Every correctness-significant fallback must be explicit, bounded, observable, and tested.

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

- [ ] For every remaining occurrence, document why it is safe.
- [ ] Add a test when the safety justification depends on runtime behavior.

---

# Phase 1 — Establish baseline and preserve evidence

## Task 1.1 — Record implementation baseline

- [ ] Record the current implementation-start SHA in the validation section at the bottom of this TODO.
- [ ] Confirm the spec exists at the exact documented path.
- [ ] Confirm this TODO exists at the exact documented path.
- [ ] Confirm `docs/PHYSICAL_HID_TESTING.md` remains present.
- [ ] Confirm `docs/BLUEDECK_RELEASE_CANDIDATE_FIX3_TODO.md` remains present.
- [ ] Confirm `docs/UIUX_FIXES1_TODO.md` remains present.

Acceptance criteria:

- [ ] The implementation begins from a known exact SHA.
- [ ] No earlier validation/evidence document was deleted or rewritten to overclaim later work.

## Task 1.2 — Run baseline host gates

Run the repository’s canonical equivalents of:

```bash
./gradlew clean :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:ktlintCheck
./gradlew :app:detekt
```

- [ ] Unit/JVM tests pass or existing failures are recorded before implementation.
- [ ] Debug APK builds or existing failure is recorded.
- [ ] Lint status recorded.
- [ ] ktlint status recorded.
- [ ] detekt status recorded.

Acceptance criteria:

- [ ] New work is not blamed for a pre-existing failure without evidence.

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

- [ ] Document the current sources in code comments or implementation notes where useful.
- [ ] Identify all places where `serviceBound`, `bleHogpBound`, or equivalent booleans are used as backend authority.
- [ ] Identify all places where `connectedDevice != null` is used as a proxy for input usability.

Acceptance criteria:

- [ ] The implementation team knows which state must be consolidated rather than layering a second ambiguous model on top.

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

- [ ] Add `BackendKind` if not already modeled cleanly.
- [ ] Add explicit startup stages sufficient to identify start/bind/backend-init failures.
- [ ] Add typed error representation.
- [ ] Add host connection state or equivalent if runtime state alone is insufficient.
- [ ] Ensure runtime state can distinguish selected backend from actually-ready backend.

Acceptance criteria:

- [ ] `Ready` means a usable backend, not merely “service requested.”
- [ ] `Failed` carries enough information for tests and diagnostics.

## Task 2.3 — Make runtime state authoritative

- [ ] Introduce/refactor a controller/coordinator that owns backend lifecycle truth.
- [ ] Stop deriving `currentBackend()` solely from Activity bind booleans.
- [ ] Make Compose/UI observe derived store/controller state rather than independently reconstructing backend truth.
- [ ] Add selectors/helpers such as `isInputUsable` if useful.

Acceptance criteria:

- [ ] A started-but-unbound service cannot disappear from the app’s model of a live backend.
- [ ] UI enablement can be computed from one coherent runtime model.

## Task 2.4 — Add state-machine tests

Add deterministic tests for at least:

- [ ] Stopped → Starting.
- [ ] Starting → Ready.
- [ ] Starting → Failed.
- [ ] Ready → Stopping → Stopped.
- [ ] service loss from Ready.
- [ ] illegal/conflicting second-backend start is rejected or serialized.

Acceptance criteria:

- [ ] Tests would fail if backend truth reverted to binding flags only.

---

# Phase 3 — Add explicit backend capabilities and remove no-op APIs

## Task 3.1 — Define capability model

Create a backend capability model covering at least:

- [ ] discovery,
- [ ] explicit connect,
- [ ] explicit disconnect,
- [ ] Classic pairing/device list,
- [ ] default-device operation,
- [ ] device rename,
- [ ] vertical scroll,
- [ ] horizontal scroll,
- [ ] middle click if backend/report-dependent,
- [ ] host LED reports if relevant.

Acceptance criteria:

- [ ] Classic and BLE capability sets are explicit and unit tested.

## Task 3.2 — Remove default production no-op `KeySender` methods

Audit `KeySender` and related interfaces.

- [ ] Remove empty default methods for user-facing backend operations.
- [ ] Split interfaces by capability where that produces a safer design.
- [ ] Otherwise return typed `Unsupported` results.
- [ ] Ensure every concrete backend explicitly declares what it supports.

Acceptance criteria:

- [ ] Adding a new backend cannot accidentally compile while silently inheriting nonfunctional user actions.

## Task 3.3 — Replace silent nullable sender dispatch

Audit middleware for patterns such as:

```kotlin
sender?.sendKeyDown(...)
sender?.moveMouse(...)
sender?.leftClick()
```

- [ ] Replace silent nullable dispatch for correctness-significant commands.
- [ ] Missing sender becomes a typed runtime/command failure.
- [ ] Reconcile backend/input-usable state when sender is unexpectedly absent.
- [ ] Keep harmless optional callbacks nullable only where absence is genuinely expected and non-critical.

Acceptance criteria:

- [ ] User input cannot be dropped solely because `sender` became null without any state/error consequence.

## Task 3.4 — Add command-result tests

Add tests for:

- [ ] supported operation succeeds,
- [ ] unsupported operation returns explicit unsupported,
- [ ] absent sender returns explicit failure,
- [ ] UI/store does not treat either unsupported or failed as success.

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

- [ ] Make startup stages observable/testable.
- [ ] Do not publish `Ready` before backend-specific initialization completes.

Acceptance criteria:

- [ ] A service start alone is never treated as successful backend activation.

## Task 4.2 — Roll back failed bind

Reproduce the reviewed failure case:

- service start succeeds,
- `bindService` fails/throws/returns failure,
- previous code suppresses/logs the failure.

Repair it:

- [ ] clear sender/listener state,
- [ ] unbind if partially bound,
- [ ] stop the started service,
- [ ] clear connection/scanning state,
- [ ] publish typed failure,
- [ ] surface user-facing failure when start was user-triggered.

Acceptance criteria:

- [ ] No foreground service remains alive after a failed bind.

## Task 4.3 — Remove critical `runCatchingLogged` suppression

Audit `runCatchingLogged` call sites.

- [ ] Classify each as best-effort or correctness-significant.
- [ ] Replace state-changing startup/bind/switch uses with explicit result propagation.
- [ ] Keep `runCatchingLogged` only for truly non-critical work or teardown where suppression is justified.

Acceptance criteria:

- [ ] Backend activation cannot fail only in disabled debug logs.

## Task 4.4 — Serialize backend switching

Implement Classic ⇄ BLE switching as:

- [ ] mark current backend stopping,
- [ ] release held input where possible,
- [ ] clear sender,
- [ ] detach listener,
- [ ] unbind,
- [ ] stop service/backend resources,
- [ ] confirm local state reset,
- [ ] then start target backend transactionally.

Acceptance criteria:

- [ ] Two backend services cannot remain live due to an intermediate bind-state mismatch.

## Task 4.5 — Add lifecycle rollback tests

Tests must cover:

- [ ] foreground service start failure,
- [ ] bind failure after service start,
- [ ] backend init failure after bind,
- [ ] switch target failure after source shutdown,
- [ ] rapid/repeated switch requests remain serialized,
- [ ] no test path leaves both backends live.

---

# Phase 5 — Reconcile service loss, connection state, and UI usability

## Task 5.1 — Repair service disconnect handling

Audit `MainActivity.onServiceDisconnected` and equivalent callbacks.

On unexpected service loss:

- [ ] clear command sender,
- [ ] transition backend runtime state out of Ready,
- [ ] clear/invalidate connected device state,
- [ ] clear scanning state,
- [ ] reset held keyboard/mouse local state,
- [ ] disable input UI,
- [ ] surface unexpected disconnect status/error.

Acceptance criteria:

- [ ] The UI cannot remain `Connected` with a missing sender after service loss.

## Task 5.2 — Define input-usability selector

Create a single derived condition for Keyboard/Mouse availability.

It must include all required facts, such as:

- [ ] backend Ready,
- [ ] sender/transport installed,
- [ ] connection state compatible with that backend,
- [ ] required permission state valid.

Acceptance criteria:

- [ ] Screens do not use `connectedDevice != null` alone to decide whether input works.

## Task 5.3 — Add regression tests

- [ ] Start connected/usable.
- [ ] Simulate service loss.
- [ ] Assert sender cleared.
- [ ] Assert connection unusable/cleared.
- [ ] Assert Keyboard/Mouse navigation becomes unavailable.
- [ ] Assert a subsequent command produces explicit failure rather than no-op.

---

# Phase 6 — Fix mouse Drag Lock lifecycle safety

## Task 6.1 — Fix stale cleanup capture

Audit `MouseScreen` Drag Lock disposal.

- [ ] Replace stale `DisposableEffect(Unit)` capture with `rememberUpdatedState`, an appropriate effect key, or controller-owned held-button state.
- [ ] Ensure cleanup sees the latest drag-lock value.

Acceptance criteria:

- [ ] Navigating away after enabling Drag Lock requests mouse-up.

## Task 6.2 — Release held mouse state on all lifecycle exits

Handle:

- [ ] Drag Lock turned off,
- [ ] navigation away,
- [ ] explicit host disconnect,
- [ ] unexpected service disconnect,
- [ ] backend switch,
- [ ] Activity teardown when transport remains usable.

- [ ] Always reset local held-button state even if release cannot be delivered.
- [ ] If release delivery fails, record/surface that failure according to command policy.

Acceptance criteria:

- [ ] No normal lifecycle transition can intentionally leave a remote mouse button held.

## Task 6.3 — Real Compose regression test

Use the production `MouseScreen` path:

- [ ] enter Mouse screen,
- [ ] enable Drag Lock,
- [ ] verify mouse-down command,
- [ ] navigate away/dispose screen,
- [ ] verify exactly one mouse-up command,
- [ ] verify local drag state reset.

Acceptance criteria:

- [ ] Test fails against the reviewed stale-capture implementation.

---

# Phase 7 — Make discovery state authoritative

## Task 7.1 — Remove optimistic `isScanning=true`

Audit middleware/store actions that immediately set scanning state.

- [ ] UI dispatches a scan request, not a fake successful scan state.
- [ ] backend/controller validates capability and permission.
- [ ] Classic `adapter.startDiscovery()` result is checked.
- [ ] set `isScanning=true` only after accepted discovery.
- [ ] set `isScanning=false` on immediate rejection.
- [ ] set `isScanning=false` on finish/cancel/failure.

Acceptance criteria:

- [ ] `startDiscovery()==false` cannot leave `Scanning for devices...` active.

## Task 7.2 — Make BLE scan unsupported in current workflow

Unless BLE discovery is genuinely implemented as a product feature:

- [ ] BLE capability reports `supportsDiscovery=false`.
- [ ] Pairing screen does not expose an active Classic Scan action in BLE mode.
- [ ] dispatching a scan action in BLE mode returns explicit unsupported if it can still be reached programmatically.

## Task 7.3 — Add tests

- [ ] successful Classic scan → scanning true,
- [ ] failed Classic scan → scanning false + error,
- [ ] scan finished → scanning false,
- [ ] BLE scan → unsupported,
- [ ] real Pairing screen never shows `Scanning...` after failed scan.

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

- [ ] no current device → explicit failure,
- [ ] no HID proxy/module → explicit failure,
- [ ] permission missing/revoked → explicit failure,
- [ ] unsupported API → explicit failure.

## Task 8.2 — Check `BluetoothHidDevice.sendReport()` return value

- [ ] Capture the Boolean result.
- [ ] Treat `false` as report rejection/failure.
- [ ] Notify state/controller if transport should no longer be considered usable.
- [ ] Ensure failure is visible even when debug logging is disabled.

Acceptance criteria:

- [ ] A rejected report cannot be reported as success.

## Task 8.3 — Unify keyboard and mouse failure behavior

- [ ] Remove mouse-only log-and-ignore exception behavior.
- [ ] Route keyboard and mouse transport errors through the same typed error/result pipeline.
- [ ] Keep message severity/user presentation appropriate without hiding correctness failure.

## Task 8.4 — Tests

Add tests/mocks for:

- [ ] device missing,
- [ ] HID proxy missing,
- [ ] permission revoked,
- [ ] `sendReport()==false`,
- [ ] keyboard exception,
- [ ] mouse exception.

Acceptance criteria:

- [ ] Every scenario yields explicit failure and no silent success.

---

# Phase 9 — Harden Classic HID registration

## Task 9.1 — Check immediate `registerApp()` result

Audit `BluetoothHidModule.registerApp`.

- [ ] Capture `BluetoothHidDevice.registerApp(...)` Boolean.
- [ ] If false, publish Classic initialization failure immediately.
- [ ] Do not wait indefinitely for a callback after immediate rejection.
- [ ] Roll back startup resources appropriately.

## Task 9.2 — Preserve async callback semantics

- [ ] Immediate `true` means request accepted, not fully registered.
- [ ] Async callback confirms success/failure.
- [ ] Runtime state becomes Ready only after required registration success.

## Task 9.3 — Tests

- [ ] immediate false → failed startup,
- [ ] immediate true + callback success → continue/ready,
- [ ] immediate true + callback failure → failed startup,
- [ ] no false-ready intermediate state.

---

# Phase 10 — Harden BLE HOGP startup into a real readiness state machine

## Task 10.1 — Persist startup status independent of Activity listener timing

- [ ] BLE service/backend exposes durable/queryable startup state.
- [ ] Early failures occurring before Activity binding are not lost.
- [ ] Installing an event listener after failure still allows Activity/controller to discover the failure.

Acceptance criteria:

- [ ] BLE startup correctness does not depend on callback installation timing.

## Task 10.2 — Validate advertiser availability

- [ ] Treat `bluetoothLeAdvertiser == null` as initialization failure.
- [ ] Stop/rollback BLE backend.
- [ ] Publish typed diagnostic.

## Task 10.3 — Validate GATT server creation

- [ ] Treat `openGattServer(...) == null` as initialization failure.
- [ ] Do not proceed as though GATT is available.
- [ ] Roll back advertiser/GATT/service resources.

## Task 10.4 — Validate GATT service registration

- [ ] Check immediate `addService(...)` acceptance where applicable.
- [ ] Observe `onServiceAdded`/equivalent completion callbacks.
- [ ] Require all mandatory HOGP services/characteristics to be registered before advertising-ready state.
- [ ] Fail closed on registration error.

## Task 10.5 — Validate advertising start

- [ ] Start advertising only after required GATT setup.
- [ ] Observe advertising callback success/failure.
- [ ] Publish `Ready` only after advertising success.
- [ ] On advertising failure, stop service and close GATT resources.

## Task 10.6 — Cleanup BLE resources idempotently

- [ ] stop advertising if active,
- [ ] close GATT server,
- [ ] clear service/callback references,
- [ ] clear sender/readiness state,
- [ ] make repeated cleanup safe,
- [ ] suppress teardown exceptions only with explicit justification.

## Task 10.7 — BLE readiness tests

Cover at least:

- [ ] permission denied at service entry,
- [ ] advertiser null,
- [ ] GATT server null,
- [ ] GATT add-service rejected,
- [ ] GATT service callback failure,
- [ ] advertising callback failure,
- [ ] full success → Ready,
- [ ] failure before Activity listener install remains observable after bind.

Acceptance criteria:

- [ ] BLE foreground service cannot remain apparently active while incapable of advertising/serving HOGP.

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

- [ ] `PermissionPolicy`,
- [ ] `PermissionGrantChecker`,
- [ ] `DiscoveryController`,
- [ ] `BluetoothService`,
- [ ] `BluetoothHidTransport`,
- [ ] Pairing/device-management code,
- [ ] BLE service/backend code.

Acceptance criteria:

- [ ] No lower layer independently imposes API31+ permission requirements on pre-31 devices.

## Task 11.2 — Centralize version-aware checks

- [ ] Reuse `PermissionPolicy` / a central Android-facing checker for version-aware permission semantics.
- [ ] API <31 Classic scan uses the appropriate legacy/location rule.
- [ ] API 31+ scan/connect/advertise use Nearby Devices permissions as applicable.
- [ ] Avoid checking permission constants on versions where they are not the governing runtime permission model.

## Task 11.3 — Decide API 26/27 support policy

Make an explicit product decision:

### Option A — Raise minSdk

- [ ] Set minSdk to the lowest API level where the supported primary BlueDeck HID workflow is coherent.
- [ ] Update README/docs.
- [ ] Remove dead compatibility branches below the new minimum where appropriate.

### Option B — Keep API 26/27

- [ ] Provide an actually usable supported backend for API 26/27.
- [ ] Gate unsupported Classic HID functionality in UI/runtime.
- [ ] Add API 26/27 tests.
- [ ] Document backend limitations honestly.

Exactly one option must be completed.

Acceptance criteria:

- [ ] App installation support and actual core-product support no longer contradict each other.

## Task 11.4 — API behavior tests

Add version-boundary tests for at least:

- [ ] API 28,
- [ ] API 30,
- [ ] API 31,
- [ ] API 34,
- [ ] current high API target/test level.

If API 26/27 remain supported:

- [ ] API 26,
- [ ] API 27.

Test:

- [ ] startup permissions,
- [ ] Classic scan permissions,
- [ ] Classic connection/send permissions,
- [ ] BLE startup permissions,
- [ ] unsupported platform/backend decision.

---

# Phase 12 — Make Pairing and input UI backend-aware

## Task 12.1 — Refactor Pairing screen by capabilities

Classic mode may expose:

- [ ] Scan,
- [ ] discovered devices,
- [ ] paired devices,
- [ ] explicit Connect/Disconnect,
- [ ] Classic device-management actions.

BLE mode must expose:

- [ ] BLE readiness state,
- [ ] advertising state,
- [ ] host-initiated pair/connect instructions,
- [ ] connected-host status if known,
- [ ] initialization failure/remediation text.

BLE mode must not present functioning-looking controls for unsupported Classic operations.

Acceptance criteria:

- [ ] A user cannot tap Scan/Connect/Rename/etc. in BLE mode and trigger a silent no-op.

## Task 12.2 — Capability-drive Mouse controls

- [ ] Vertical scroll shown/enabled only if supported.
- [ ] Horizontal scroll shown/enabled only if supported.
- [ ] Middle click capability handled explicitly if report/backend-dependent.
- [ ] Do not derive availability solely from `hidSimplified` if backend capabilities differ.

Acceptance criteria:

- [ ] BLE simplified/current report format cannot display unsupported scroll as a working control.

## Task 12.3 — Capability-drive navigation/input enablement

- [ ] Keyboard tab uses `isInputUsable` or equivalent.
- [ ] Mouse tab uses `isInputUsable` plus required mouse capability.
- [ ] disconnected/unready nav feedback remains available.

## Task 12.4 — Production Compose tests

Using real screens:

- [ ] Classic Pairing shows Classic workflow.
- [ ] BLE Pairing shows host-initiated workflow.
- [ ] BLE Pairing does not expose active Scan/Connect/Rename/default-device actions.
- [ ] BLE Mouse hides/disables unsupported scrolling.
- [ ] backend loss disables Keyboard/Mouse.

---

# Phase 13 — Fix safe device labels and stale runtime copy

## Task 13.1 — Remove unsafe direct `BluetoothDevice.name` reads from Compose

- [ ] Find direct `.name` reads in composables.
- [ ] Resolve labels in permission-aware controller/service/data-mapper layer.
- [ ] Store safe display name plus stable fallback identifier in UI state.
- [ ] Handle null names and permission revocation.

Acceptance criteria:

- [ ] Compose cannot crash merely because `BLUETOOTH_CONNECT` was revoked before a device-name read.

## Task 13.2 — Fix BLE Settings copy

- [ ] Remove/rewrite `Restart the app after changing` if backend changes are live-switched.
- [ ] Describe actual switching behavior.
- [ ] If restart truly becomes required after refactor, enforce/document it consistently instead of leaving ambiguous copy.

## Task 13.3 — Fix Linux runtime connection guidance

Search runtime strings/log/info callbacks for `bluetoothctl connect`.

- [ ] Make DBus `ConnectProfile(HID)` the primary known-good Linux/BlueZ instruction.
- [ ] Keep `bluetoothctl connect` only as fallback/diagnostic if useful.
- [ ] Match `docs/PHYSICAL_HID_TESTING.md`.

Acceptance criteria:

- [ ] Runtime guidance and docs no longer contradict each other.

---

# Phase 14 — Fix remaining Settings silent failures and excessive writes

## Task 14.1 — Battery optimization terminal failure

Audit the two-stage battery-optimization intent flow.

- [ ] Keep app-specific intent first.
- [ ] Keep general settings fallback if justified.
- [ ] Replace terminal empty catch.
- [ ] Show snackbar/dialog/message if both intents fail.
- [ ] Log durable diagnostic independent of optional debug logging.

Acceptance criteria:

- [ ] User-triggered battery optimization request cannot fail with zero feedback.

## Task 14.2 — IME overrides load-state model

- [ ] Stop converting all exceptions to empty maps.
- [ ] Distinguish empty-success from load-failure.
- [ ] Preserve last-known-good data if useful and safe.
- [ ] Show recoverable error state/action.
- [ ] Log diagnostic cause.

Tests:

- [ ] successful empty config,
- [ ] successful populated config,
- [ ] storage/read failure,
- [ ] package-label resolution failure behavior.

## Task 14.3 — Debounce/defer slider persistence

For touchpad sensitivity, scroll speed, key-repeat delay, and similar sliders:

- [ ] keep local transient slider state,
- [ ] persist on `onValueChangeFinished` or tested debounce,
- [ ] prevent a DataStore write per pointer movement,
- [ ] ensure value survives recomposition/process persistence as expected.

Acceptance criteria:

- [ ] dragging a slider does not launch an unbounded sequence of storage writes.

---

# Phase 15 — Harden IME text forwarding

## Task 15.1 — Define text-diff behavior

Replace/extend the current append/delete-only heuristic.

Handle at least:

- [ ] append,
- [ ] delete,
- [ ] equal-length replacement,
- [ ] suffix replacement,
- [ ] composing/replacement-like update,
- [ ] desynchronization/reset.

## Task 15.2 — Implement bounded deterministic diff/reset strategy

Choose one explicit strategy:

- [ ] prefix/suffix diff + delete/retype,
- [ ] constrained editor/reset behavior,
- [ ] another documented deterministic approach.

Rules:

- [ ] unsupported transformation cannot silently disappear,
- [ ] excessive/unbounded diff must fail/reset safely,
- [ ] text state remains synchronized after recovery.

## Task 15.3 — Tests

- [ ] simple append,
- [ ] multi-character append,
- [ ] backspace/delete,
- [ ] equal-length replacement,
- [ ] replacement of a suffix,
- [ ] composition-like replacement,
- [ ] desync/reset.

Acceptance criteria:

- [ ] Tests demonstrate behavior that the old `current.startsWith(previous)` heuristic did not cover.

---

# Phase 16 — Remove blocking sleeps from UI-sensitive command paths

## Task 16.1 — Audit `Thread.sleep`

Search production code for `Thread.sleep`.

- [ ] Identify keyboard press/release sleeps.
- [ ] Identify mouse click sleeps.
- [ ] Identify lock-toggle sleeps.
- [ ] Determine which calls can execute on UI dispatch.

## Task 16.2 — Move timing into async serialized command execution

Prefer:

- [ ] suspend sender functions,
- [ ] coroutine `delay`,
- [ ] command queue/mutex,
- [ ] dedicated worker context if required.

Acceptance criteria:

- [ ] UI event handler does not block the main thread for HID timing delays.
- [ ] down/up ordering remains deterministic.

## Task 16.3 — Timing tests

- [ ] rapid key sends remain serialized,
- [ ] rapid clicks produce discrete down/up pairs,
- [ ] lock toggles preserve required timing/order,
- [ ] cancellation/teardown does not strand local held state.

---

# Phase 17 — Reopen and verify remaining UIUX_FIXES1 concerns

## Task 17.1 — TASK-02 disconnected nav feedback

Use production `MainScreen`.

- [ ] Verify tapping Keyboard while unusable/disconnected shows feedback.
- [ ] Verify tapping Mouse while unusable/disconnected shows feedback.
- [ ] Add real Compose/instrumented regression test.
- [ ] Update `docs/UIUX_FIXES1_TODO.md` manual/test checkbox only after evidence exists.

## Task 17.2 — TASK-04 explicit key-cell height

Audit current shared key button implementation.

- [ ] Define an explicit common key-cell height.
- [ ] Apply it consistently to repeatable/non-repeatable cells.
- [ ] Align empty grid placeholders to the same value.
- [ ] Do not rely on undocumented Material default minimums for one branch while hard-coding another.

Acceptance criteria:

- [ ] Grid row alignment is deterministic.

## Task 17.3 — TASK-07 Scroll Lock bookkeeping

- [ ] Verify current Scroll Lock active colors and label in production screen.
- [ ] Update stale unchecked task-body boxes in `docs/UIUX_FIXES1_TODO.md` only after verification.
- [ ] Do not change historical meaning; simply reconcile implementation status.

## Task 17.4 — TASK-16 Navigation sizing visual verification

- [ ] Verify Navigation keys align with the shared sizing contract.
- [ ] Test normal font scale.
- [ ] Test at least one larger accessibility font scale.
- [ ] Ensure labels do not clip or force inconsistent row heights.

---

# Phase 18 — Replace misleading instrumented UI tests with real coverage

## Task 18.1 — Remove synthetic scan proof

Audit `UiComposeInstrumentedTest`.

- [ ] Stop treating synthetic `TestScan()` as proof that `PairingScreen` works.
- [ ] Replace with production screen/state/controller integration where feasible.

## Task 18.2 — Remove placeholder assertions

- [ ] Find `assertTrue(true)` or equivalent placeholder tests.
- [ ] Replace with meaningful assertion or delete the fake test if it asserts nothing.

Acceptance criteria:

- [ ] Instrumented test count no longer includes tests that cannot fail meaningfully.

## Task 18.3 — Required real-screen regression suite

Add real Compose/instrumented tests for:

- [ ] disconnected nav guard,
- [ ] Classic Pairing controls,
- [ ] BLE Pairing host-initiated workflow,
- [ ] failed scan state reset,
- [ ] service/backend loss disables input,
- [ ] Drag Lock release on navigation,
- [ ] BLE unsupported scroll controls,
- [ ] safe connected-device label fallback,
- [ ] at least one visible backend startup/send failure.

---

# Phase 19 — Add CI coverage for non-physical instrumented tests

## Task 19.1 — Inspect current workflow

Audit `.github/workflows/ci.yml`.

- [ ] Preserve build.
- [ ] Preserve JVM unit tests.
- [ ] Preserve lint.
- [ ] Preserve ktlint.
- [ ] Preserve detekt.

## Task 19.2 — Add stable emulator/managed-device job

If GitHub-hosted or available self-hosted infrastructure supports it:

- [ ] boot representative emulator/managed device,
- [ ] run non-physical `connectedDebugAndroidTest`,
- [ ] exclude opt-in physical HID tests by their existing gating mechanism,
- [ ] upload useful test reports/artifacts on failure.

If infrastructure cannot support a stable emulator job:

- [ ] document the concrete blocker,
- [ ] add the strongest repeatable alternative available,
- [ ] do not label unrun instrumented tests as CI-covered.

## Task 19.3 — API-boundary CI strategy

Add at least one lower-version and one modern-version instrumentation/API check if practical.

Suggested boundaries:

- [ ] API 28 or 30,
- [ ] API 31,
- [ ] API 34+.

If full emulator matrix is too expensive:

- [ ] keep deterministic version-policy tests on every CI run,
- [ ] run a smaller representative emulator matrix.

Acceptance criteria:

- [ ] Critical real-screen regressions are automatically detected before merge/release where infrastructure permits.

---

# Phase 20 — Diagnostics and no-silent-failure sweep

## Task 20.1 — Add/consolidate typed error taxonomy

Ensure errors distinguish at least:

- [ ] permission missing/revoked,
- [ ] unsupported API/backend,
- [ ] adapter unavailable/disabled,
- [ ] foreground start failure,
- [ ] bind failure,
- [ ] service disconnect,
- [ ] Classic registration rejection,
- [ ] no HID device/proxy,
- [ ] HID report rejection,
- [ ] discovery rejection,
- [ ] GATT server failure,
- [ ] GATT service failure,
- [ ] advertiser unavailable,
- [ ] advertising failure,
- [ ] unsupported capability,
- [ ] settings/storage failure.

## Task 20.2 — Search for silent catches

Search production code for:

- `catch (_:`
- `catch (`
- `runCatching`
- `getOrNull`
- `getOrDefault`
- `getOrElse`

For each failure-swallowing occurrence:

- [ ] classify harmless best-effort vs correctness-significant,
- [ ] propagate/record correctness-significant failures,
- [ ] comment narrowly justified teardown suppression.

## Task 20.3 — Search for nullable no-op dispatch

Search for:

- `sender?.`
- `eventListener?.`
- `advertiser?.`
- `gattServer?.`
- other `?.operation()` in state-changing backend paths.

- [ ] Replace unsafe optional dispatch.
- [ ] Retain genuinely optional observer notifications only when durable state exists independently.

## Task 20.4 — Search for silent early returns

Audit `?: return` and bare `return` in backend/transport methods.

- [ ] Convert command/startup failure cases to explicit result/state transitions.
- [ ] Leave only semantically legitimate no-work returns.

Acceptance criteria:

- [ ] No known critical failure depends solely on `DebugLog` being enabled to be discoverable.

---

# Phase 21 — Documentation cleanup

## Task 21.1 — Document Classic vs BLE workflows

Update README/docs as appropriate:

- [ ] Classic discovery/pair/connect model,
- [ ] BLE advertising/host-initiated model,
- [ ] backend capability differences,
- [ ] scroll/report limitations where applicable.

## Task 21.2 — Document Android support policy

- [ ] Document final minSdk/support decision.
- [ ] Document relevant API31 permission split.
- [ ] Avoid claiming support for an API level whose core workflow is unusable.

## Task 21.3 — Reconcile runtime Linux instructions

- [ ] README matches `PHYSICAL_HID_TESTING.md`.
- [ ] runtime messages match both.
- [ ] DBus `ConnectProfile(HID)` remains primary Linux/BlueZ procedure.

## Task 21.4 — Reconcile historical TODO bookkeeping

- [ ] Update only demonstrably stale `UIUX_FIXES1_TODO.md` task-body checkboxes after new evidence exists.
- [ ] Do not rewrite Fix3 evidence to claim this hardening work was previously validated.
- [ ] Add a note to Fix3 or release documentation if needed that post-Fix3 review superseded the earlier broad release-ready conclusion.

---

# Phase 22 — Full validation

## Task 22.1 — JVM/unit suite

Run:

```bash
./gradlew clean :app:testDebugUnitTest
```

- [ ] PASS.
- [ ] Record exact test count/result if available.

## Task 22.2 — Build

Run:

```bash
./gradlew :app:assembleDebug
```

- [ ] PASS.

## Task 22.3 — Static analysis

Run:

```bash
./gradlew :app:lintDebug
./gradlew :app:ktlintCheck
./gradlew :app:detekt
```

- [ ] lint PASS.
- [ ] ktlint PASS.
- [ ] detekt PASS.

## Task 22.4 — Non-physical instrumented suite

Run the canonical non-physical suite, normally:

```bash
./gradlew :app:connectedDebugAndroidTest
```

- [ ] PASS on an unlocked emulator/device.
- [ ] Physical tests remain skipped unless explicitly enabled.
- [ ] Record device/API used.
- [ ] Record test count.

## Task 22.5 — API matrix validation

Record results for the final supported matrix.

- [ ] lower supported boundary,
- [ ] API 30 where applicable,
- [ ] API 31 permission boundary,
- [ ] API 34,
- [ ] current high API test device/emulator.

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

- [ ] Required / Not required is explicitly recorded.
- [ ] If run, result recorded against exact SHA.
- [ ] If not run, existing historical evidence remains labeled historical rather than current exact-SHA evidence.

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

- [ ] only one backend can be live,
- [ ] failed bind stops the service,
- [ ] failed backend init rolls back resources,
- [ ] backend switching is serialized,
- [ ] service loss invalidates stale connected/input state,
- [ ] sender absence cannot silently drop user commands.

## Task 23.2 — Capability integrity gate

- [ ] no user-facing production operation relies on an empty default implementation,
- [ ] Classic capabilities explicit,
- [ ] BLE capabilities explicit,
- [ ] BLE UI does not expose Classic-only operations as functional,
- [ ] unsupported scrolling is not presented as working.

## Task 23.3 — Transport integrity gate

- [ ] `sendReport()` false handled,
- [ ] missing device/HID object handled,
- [ ] permission revocation handled,
- [ ] keyboard and mouse failures use same policy,
- [ ] Classic `registerApp()` immediate false handled,
- [ ] BLE readiness requires GATT + advertising success.

## Task 23.4 — Platform integrity gate

- [ ] minSdk/support policy coherent,
- [ ] lower-layer permission checks version-aware,
- [ ] API-boundary tests green.

## Task 23.5 — UI integrity gate

- [ ] Drag Lock cannot strand mouse-down through normal navigation/lifecycle transitions,
- [ ] real production-screen Compose tests cover repaired paths,
- [ ] safe device labels used,
- [ ] stale BLE restart copy fixed,
- [ ] Linux runtime guidance fixed,
- [ ] Settings failures visible,
- [ ] IME replacement behavior defined/tested,
- [ ] key-grid sizing deterministic.

## Task 23.6 — Silent-failure gate

- [ ] production empty catches reviewed,
- [ ] correctness-significant `runCatching` reviewed,
- [ ] correctness-significant nullable calls reviewed,
- [ ] correctness-significant `?: return` reviewed,
- [ ] remaining fallbacks are explicit and tested,
- [ ] critical error visibility does not depend on debug logging.

## Task 23.7 — Validation integrity gate

- [ ] JVM tests green,
- [ ] build green,
- [ ] lint green,
- [ ] ktlint green,
- [ ] detekt green,
- [ ] required instrumented tests green,
- [ ] CI green on exact final SHA,
- [ ] physical/manual evidence accurately labeled,
- [ ] final exact SHA recorded below.

---

# Phase 24 — Final evidence record

Do not fill these fields optimistically. Record actual results only.

## Implementation start

- Source review baseline: `9f50fa30ebe12951e7e5c80bb1fe49ca679c10f3`
- Spec commit: `21bc0daadb01fbd3a543afeced2484fded30c113`
- TODO commit: `<fill after this file is created>`
- Implementation-start SHA: `<pending>`

## Final source

- Final implementation SHA: `<pending>`
- Final documentation/evidence SHA: `<pending>`

## Automated evidence

- JVM/unit: `<pending>`
- Build: `<pending>`
- Lint: `<pending>`
- ktlint: `<pending>`
- detekt: `<pending>`
- Instrumented/Compose: `<pending>`
- CI run/job URL or IDs: `<pending>`

## API evidence

- API 26/27 disposition: `<pending>`
- API 28: `<pending>`
- API 30: `<pending>`
- API 31: `<pending>`
- API 34: `<pending>`
- Current high API: `<pending>`

## Physical/manual evidence

- Physical Classic HID exact-SHA run: `<pending / not required with rationale>`
- BLE device smoke: `<pending>`
- Manual UX smoke: `<pending>`

---

# Completion condition

This TODO is complete only when BlueDeck satisfies the companion specification’s core rule:

> A backend or HID operation must never quietly appear successful when it was unsupported, unavailable, rejected, disconnected, or otherwise not delivered.

A green build alone is not completion. Existing green tests alone are not completion. Every required state transition, failure path, UI behavior, and regression test above must either pass or remain explicitly open.