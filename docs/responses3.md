# Responses to BlueDeck Release Candidate Fix 3 Spec/TODO (round 3)

Reviewer: Claude (Opus 4.8), cross-checked against the **current** codebase
(Fix 2 complete, `origin/master` @ `109a332`). Every "current problematic
pattern" the Fix 3 spec cites was verified against the live code. File:line
evidence is included so the claims can be trusted without re-deriving them.

This pass is narrow and well-scoped. No objections to the direction. The items
below are: (A) confirmations of the cited bugs, and (B) a short list of genuine
decisions I'd like settled before writing code.

---

## A. Confirmed-valid items (all verified)

1. **§3 permission callback bug — real, both sites.**
   - `MainActivity.onStartupPermissionResult` (MainActivity.kt:360) calls
     `PermissionPolicy.missingRequired(result, plan.requiredPermissions)` against
     the **callback result map**, not actual OS state.
   - `SettingsScreen` BLE launcher callback (SettingsScreen.kt:351) does the same:
     `PermissionPolicy.missingRequired(granted, requiredForBleStartup(sdk))`.
   - The partial-map false-denial described in §3.2 is genuine.
   - Note: `SettingsScreen.onCheckedChange` already re-checks full state via
     `ContextCompat.checkSelfPermission` **before** launching the request, so only
     the **post-callback** path is wrong. The fix is localized to the callbacks.

2. **§4 `registerReceiver` — real (runtime crash risk).**
   `BluetoothService.kt:299` calls
   `registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)`
   **unguarded**. That 3-arg int-flags overload is API 33+, so on `minSdk = 26`
   it throws `NoSuchMethodError` at runtime (not just on preview SDKs — the
   existing comment understates this). The second receiver (BluetoothService.kt:314)
   is already SDK-gated with `>= TIRAMISU`. `core-ktx` is **1.16.0**, so
   `ContextCompat.registerReceiver` is available — **no dependency change required**.

3. **§5 BleHogpService — partially already done.**
   `BleHogpService.onCreate` (BleHogpService.kt:90–99) already checks
   `BLUETOOTH_CONNECT` and `stopSelf()`s if missing. It just does **not** check
   `BLUETOOTH_ADVERTISE`. So Phase 3 is adding the advertise check beside the
   existing connect check — not new architecture.

4. **§7 branding/SDP — real.**
   `BluetoothHidModule.kt:68–75` →
   `BluetoothHidDeviceAppSdpSettings("Bluetooth Keyboard/Mouse",
   "Android Bluetooth HID", "Gemini", …)`.
   `settings.gradle.kts:22` → `rootProject.name = "Bluetooth Keyboard Mouse"`.

5. **§6 physical-HID docs — partially already done.**
   `ConnectProfile(HID)` is **already labeled "Preferred"** in
   `PHYSICAL_HID_TESTING.md`. The remaining gap is that the step-by-step
   walkthrough still uses `bluetoothctl connect` as the actual command (~27
   mentions) and `BluetoothHidSendReportTest.kt` has 4 comment/log references
   centered on it.

---

## B. Decisions I'd like settled before coding

### B1. Permission-decision testability (Phase 1) — most important
Task 1.5 requires tests that "would fail under the old logic." The current code
mixes the **decision** (`when{}`) with **side-effects** (`startPlannedBackend`,
dialogs), which is not unit-testable. Proposal: extract a **pure decision
function** —

```
StartupPermissionDecision.decide(plan, grantedFullState: Set<String>): Decision
   // -> StartBackend | FallbackToClassic | ShowDenied
```

— and have `MainActivity`/`SettingsScreen` (a) re-check full OS state via a thin
Android `PermissionGrantChecker`, then (b) feed that into the pure function. This
matches the existing pure-helper pattern (`StartupPermissionPlanner`,
`BootStartPlanner`) and makes Task 1.5 meaningful.

**Q: OK to add that pure decision helper, or do you want the checker/tests
structured differently?**

### B2. `registerReceiver` — keep the exported fallback?
The existing code does `NOT_EXPORTED`, then on `SecurityException` retries with
`RECEIVER_EXPORTED`. `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`
handles SDK-gating cleanly but does **not** replicate that try/catch-to-EXPORTED
fallback.

**Q: Drop the exported fallback (cleaner, matches the spec's preferred pattern),
or preserve it?** I lean toward dropping it — it was a workaround for the
unguarded call we're removing.

### B3. BLE advertised device name — in scope?
Branding (§7) names only the SDP `BluetoothHidDeviceAppSdpSettings`. But
`BleHogpService.kt:107` sets `adapter.name = "Bluetooth Keyboard"`, which is
**host-facing** (the BLE device name a host sees during HOGP).

**Q: Leave it as-is (changing it could affect already-paired hosts / re-pairing),
or rebrand it to "BlueDeck …" too?** Default: leave it unless told otherwise.

### B4. Phase 4 docs — how deep a rewrite?
`ConnectProfile(HID)` is already "Preferred," but the walkthrough still drives
`bluetoothctl connect`.

**Q: Intent (a) — promote `ConnectProfile` to the primary command in the main
procedure, demote `bluetoothctl connect` to a clearly-labeled fallback/diagnostic
subsection, and fix the 4 test comments — or (b) a fuller restructure of the
whole walkthrough?** I'd do (a).

### B5. Phase 6 validation wording — which file is the source of truth?
The contradictory checked boxes live in
`docs/BLUEDECK_RELEASE_HARDENING_FIX2_TODO.md` (e.g. lines 293–294 check **both**
"Manual-device-verified" **and** "Pending manual UX smoke" — internally
contradictory).

**Q: Edit the Fix2 TODO in place to apply the `PASS / PENDING / N-A` labels, or
only fix forward-looking docs (`memory.md` + the new Fix3 TODO) and leave the
historical Fix2 TODO untouched?**

---

## C. Non-issues (will run as written)
- Phase 2 acceptance criteria — unambiguous.
- Phase 5 `rootProject.name` rename — low risk; does not touch `applicationId`,
  namespace, or Kotlin packages. Will do.
- Phase 7 gate commands (`clean test`, `assembleDebug`, `lintDebug`,
  `ktlintCheck`, `detekt`) — fine; `connectedDebugAndroidTest` and the physical
  HID test are conditional on hardware being attached at run time.
