# Unit Test Coverage TODO — Round 1

Goal: close the highest-value **host JVM** unit-test gaps identified in the
coverage review, without changing runtime behavior.

Priority order: **Phase 1 (BleHogpKeySender)** is the biggest gap — it is the
untested twin of the well-covered `BluetoothKeySender`. Phase 2 adds host
coverage for `HidReportSender`. Phase 3 is optional / lower value.

## Implementation rules

- Tests are **host JVM** unit tests under `app/src/test/...` — JUnit +
  `kotlinx-coroutines-test` where coroutines are involved. **No Android
  framework calls** (no `Context`, no real `Service`, no `BluetoothGattServer`).
- Production changes must be **behavior-preserving**. The only production edits
  allowed here are *test seams* (dependency-inversion interfaces) explicitly
  listed below. Do not change report bytes, timing, or control flow.
- Prefer a small **interface seam** over reflection or Robolectric. Mirror the
  existing pattern: `BluetoothKeySender(private val svc: IBluetoothService)` is
  testable because it depends on an interface, not a concrete `Service`.
- Reuse existing test style: see `store/BluetoothKeySenderTest.kt` for the fake
  + argument-capture + exception-propagation patterns to mirror.
- Assert on **exact report bytes** (via `HidReportBuilder` expectations) wherever
  a report is produced — that is what catches real regressions.
- Avoid tests that only confirm mocked behavior; assert real state transitions
  and produced bytes.
- Do **not** add a `Co-Authored-By:` trailer to commits (repo policy).
- Keep `val` over `var`, no `!!`, idiomatic Kotlin; tests must be lint-clean.

## Verification (run after every task; all must pass before checking `[x]`)

```
./gradlew :app:compileDebugKotlin     # must compile (incl. test sources)
./gradlew :app:testDebugUnitTest      # new + existing unit tests pass
./gradlew :app:ktlintCheck            # ktlint clean
./gradlew :app:detekt                 # detekt clean (no new baseline entries)
./gradlew :app:lintDebug              # Android lint clean
```

A task is done only when all five pass. Commit per task, referencing the task ID
(e.g. `UT-01: add HogpNotifier seam`).

---

## Phase 1 — `BleHogpKeySender` (highest value)

`BleHogpKeySender` (115 LOC) has **no test**; its Classic twin
`BluetoothKeySender` is thoroughly tested. It currently depends on the concrete
`BleHogpService` (an Android `Service`), which blocks host testing. Introduce a
tiny notifier seam, then mirror `BluetoothKeySenderTest`.

### UT-01 — Introduce the `HogpNotifier` test seam

- [x] Add an interface (e.g. `HogpNotifier`) with exactly the two methods
      `BleHogpKeySender` uses:
  - [x] `fun notifyKeyboard(report: ByteArray)`
  - [x] `fun notifyMouse(report: ByteArray)`
- [x] Make `BleHogpService` implement `HogpNotifier` (its existing
      `notifyKeyboard`/`notifyMouse` already match these signatures — verify the
      signatures line up exactly; add `override` as needed).
- [x] Change `BleHogpKeySender`'s constructor param type from `BleHogpService`
      to `HogpNotifier` (rename `svc` if desired, e.g. `notifier`). Update the
      body references (`svc.notifyKeyboard` / `svc.notifyMouse`).
- [x] Update the call site that constructs `BleHogpKeySender` (search for
      `BleHogpKeySender(`) so it still passes the `BleHogpService` instance
      (now via the interface) — no behavior change.

Acceptance criteria:
- [x] No runtime behavior change; `BleHogpService` still constructs and uses the
      keysender exactly as before.
- [x] `BleHogpKeySender` no longer references the concrete `BleHogpService` type.
- [x] Full verification suite passes.

### UT-02 — `BleHogpKeySenderTest`: keyboard report logic

Create `app/src/test/java/com/augustusmachin/android_bt_kbmouse/store/BleHogpKeySenderTest.kt`
with a `FakeHogpNotifier` recording the last keyboard/mouse report bytes (and a
list of all reports, for sequence assertions).

- [x] `sendKeyDown` sets the modifier byte and adds the key → asserts the exact
      8-byte report `[mods, 0, code, 0,0,0,0,0]`.
- [x] `sendKeyDown` is idempotent on the same code (no duplicate in the key
      array — dedupe path).
- [x] Multiple distinct `sendKeyDown` calls accumulate keys in order, up to the
      6-key rollover cap (`MAX_ROLLOVER_KEYS`); a 7th key does not appear.
- [x] `sendKeyUp` removes the key and re-emits the report without it.
- [x] `setModifiers` updates the modifier byte and emits a keyboard report with
      the current pressed keys.

Acceptance criteria:
- [x] Every assertion checks the produced `ByteArray` (content equality), not
      just that a method was called.
- [x] Verification suite passes.

### UT-03 — `BleHogpKeySenderTest`: mouse report logic

- [x] `moveMouse(dx, dy)` emits a 3-byte SIMPLE mouse report `[buttons, dx, dy]`
      with the current `buttonsMask` (0 initially).
- [x] `moveMouse` clamps dx/dy via `HidReportBuilder` (e.g. 200 → 127, -200 →
      -127) — assert the clamped bytes.
- [x] `mouseButtonDown(button)` ORs the mask and emits a report with the button
      bit set; `mouseButtonUp()` resets the mask to 0 and emits a cleared report.
- [x] `leftClick` / `rightClick` / `middleClick` each emit **two** reports: the
      button-pressed report (mask 0x01 / 0x02 / 0x04) then the released report
      (mask 0x00). Assert both, in order, from the recorded sequence.

Acceptance criteria:
- [x] Click tests assert the press→release **sequence** of report bytes.
- [x] `middleClick` uses `MOUSE_BUTTON_MIDDLE` (0x04).
- [x] Verification suite passes.

### UT-04 — `BleHogpKeySenderTest`: lock-key sequences

- [x] `toggleCapsLock()` emits a key-down report containing `0x39` then a
      key-up report without it (assert the two reports / their key bytes).
- [x] `toggleScrollLock()` does the same for `0x47`.
- [x] Lock toggles preserve the current `modifierByte` in the emitted reports.

Acceptance criteria:
- [x] The `Thread.sleep` hold does not need to be asserted; assert only the
      emitted report sequence (keep the test fast).
- [x] Verification suite passes.

### UT-05 — `BleHogpKeySenderTest`: BLE no-op commands

- [x] Confirm the discovery/pairing/connection KeySender methods that are
      documented as no-ops for BLE HOGP do **not** emit any report (the fake
      records zero notifications for them). (Enumerate them from the `KeySender`
      interface; only assert no-op for those BleHogpKeySender implements as such.)

Acceptance criteria:
- [x] Verification suite passes.

---

## Phase 2 — `HidReportSender` host coverage (medium value)

`HidReportSender` (195 LOC) is only exercised by the instrumented
`HidReportInstrumentedTest`. Its stateful logic is host-testable behind a sink
seam.

### UT-06 — Introduce a HID output sink seam

- [x] Identify the exact platform calls `HidReportSender` makes to send a report
      (the `hid.sendReport(...)` / device interaction). Define a minimal
      interface capturing just those (e.g. `HidOutput` with the send method(s)
      actually used).
- [x] Refactor `HidReportSender` to depend on that interface (inject it), with
      the production path wiring the real HID device exactly as today.
- [x] Verify no behavior change on the instrumented path (the existing
      `HidReportInstrumentedTest` / physical HID tests still pass — note these
      require hardware; at minimum compile the androidTest variant).

Acceptance criteria:
- [x] Production report bytes, report IDs, and timing are unchanged.
- [x] `:app:compileDebugAndroidTestKotlin` (or equivalent) still compiles.
- [x] Verification suite passes.

### UT-07 — `HidReportSenderTest`: keyboard + modifier state

- [x] `setModifiers(mods)` is reflected in the next emitted keyboard report.
- [x] `pressKey` / `releaseKey` accumulate and remove keys (rollover cap),
      asserting exact report bytes.
- [x] `sendKeyPress` emits the down→up sequence with the correct key + modifiers.

Acceptance criteria:
- [x] Assertions check produced report bytes via the sink fake.
- [x] Verification suite passes.

### UT-08 — `HidReportSenderTest`: mouse state

- [ ] `sendMouseMove(dx, dy)` emits the expected mouse report (with clamping).
- [ ] `mouseButtonDown(button)` / `mouseButtonUp()` toggle the button mask
      correctly across calls.
- [ ] `sendLeftClick` / `sendRightClick` / `sendMiddleClick` emit the
      press→release sequence with masks 0x01 / 0x02 / 0x04.

Acceptance criteria:
- [ ] Click tests assert the press→release sequence.
- [ ] Verification suite passes.

---

## Phase 3 — Optional / lower value

These are Android-framework-bound; only do them if cheap seams exist. Do **not**
introduce Robolectric or new test dependencies without asking first (repo policy
on dependency changes).

### UT-09 — `ServiceNotifications` (optional)

- [ ] Assess whether notification *content* construction can be separated from
      `NotificationManager`/`Context` into a pure helper that can be host-tested
      (similar to how `ForegroundServiceLogic.buildNotificationText` is already a
      pure, tested helper). If a clean seam exists, extract + test the pure part;
      otherwise leave as instrumented-only and note why.

Acceptance criteria:
- [ ] Either pure content logic is host-tested, or a one-line note records that
      it remains instrumented-only (no forced/awkward seam).
- [ ] Verification suite passes.

### UT-10 — `SettingsViewModel` / `BootReceiver` (optional, likely skip)

- [ ] Evaluate testability; both are thin and Android-bound. If a pure decision
      function can be extracted cheaply, do so and test it; otherwise mark
      skipped with a one-line rationale. Do not add dependencies.

Acceptance criteria:
- [ ] Verification suite passes (or task explicitly marked skipped w/ reason).

---

## Final validation

- [ ] All five verification gradle tasks pass on a clean run.
- [ ] No new `detekt-baseline.xml` entries were added (new code is clean, not
      grandfathered).
- [ ] `git log` shows one focused commit per task, no `Co-Authored-By:` trailer.
- [ ] Run the full instrumented suite once (`:app:connectedDebugAndroidTest`)
      to confirm the Phase 1/2 seams did not regress on-device.
- [ ] Update `memory.md` with a dated entry summarizing the coverage added.

When every checkbox above is `[x]`, output `<promise>COMPLETE</promise>`.
