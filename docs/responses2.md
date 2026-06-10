# Responses to BlueDeck Release Hardening Fix 2 Spec/TODO (round 2)

Reviewer: Claude (Opus 4.8), cross-checked against the **current** codebase
(Fix 1 complete, `origin/master` @ `01829e0`). Every claim in the Fix 2 spec was
verified against the code and is accurate. File:line evidence is included so
claims can be trusted without re-deriving them.

This pass is well-scoped and mostly already-decided (Option A boot,
fall-back-to-Classic on BLE denial, state-based notifications). No objections to
the direction; the items below are confirmations + small decisions.

---

## A. Confirmed-valid bugs (all verified — will fix)

1. **P1 — persisted BLE startup permissions.** `MainActivity.onCreate` requests
   `requiredForClassicStartup` **synchronously, before settings load**, then
   `startServicesAndBind()` waits for settings and picks the backend. So the
   startup permission request is **always Classic**, even when `useBleHogp=true`
   is persisted. If advertise was later revoked, `BleHogpService` starts but
   cannot advertise → silent broken BLE. Confirmed.

2. **P2 — `BluetoothService` foreground.** `BluetoothService.onCreate` calls
   `startInForeground()` as the **last line and discards the Boolean**, and runs
   `getProfileProxy(...)`, `registerReceiver(...)`, and
   `UpdatePairedDevices` **before** it (BluetoothService.kt onCreate ~279–336).
   Confirmed — should `if (!startInForeground()) return` and promote earlier.

3. **P3 — BootReceiver.** Uses `runBlocking` in `onReceive`, reads **only
   `startOnBoot`**, and always starts Classic `BluetoothService` (ignores
   `useBleHogp`). Confirmed.

4. **P4 — notification sequencing.** Prompt is gated by `delay(NOTIF_PROMPT_DELAY_MS)`
   in `MainScreen` — timer-based, not state-based. Confirmed.

5. **P6.1 — BLE denial copy.** Current message is *"BLE HOGP needs the Bluetooth
   advertise permission; staying on Classic."* — mentions advertise only. Confirmed.

6. **P6.3 — XML theme colors.** `values/themes.xml` and `values-night/themes.xml`
   still reference `purple_500` / `purple_700` / `teal_200` (defined in
   `values/colors.xml`). Confirmed.

7. **P6.4 — tile label.** `BtDevicePrefs.setConnectedName(device.address)` stores
   the **MAC address** (BluetoothService.kt:239), so the QS tile label is a raw MAC.
   Confirmed.

---

## B. Questions / decisions needed before implementing

### Q1. Intentional asymmetry in BLE-denial handling (P1 vs P3)?

- **Phase 1 (4.2):** on BLE permission denial at app startup → **persist
  `useBleHogp=false`** + fall back to Classic.
- **Phase 3 (6.3):** at boot → **do NOT persist `useBleHogp=false`**, just skip.

So opening the app once while advertise is revoked **permanently downgrades the
user's saved BLE preference to Classic**, whereas boot leaves it intact. I believe
this is deliberate (interactive path can re-prompt; boot is silent), but please
confirm. Alternative for the interactive path: keep `useBleHogp=true`, don't start
BLE, show a "re-grant Bluetooth advertise to use BLE" message — preserves the
user's choice instead of silently flipping it.

**Decision needed:** (a) persist `useBleHogp=false` on startup denial (as written),
or (b) keep the setting and show a re-grant message.

### Q2. Notification sequencing approach (P4 / 7.3)

Two compliant flavors:
- **(a) Minimal state flag** — auto-request `POST_NOTIFICATIONS` only after the
  startup Bluetooth permission flow resolves (a "startup resolved" signal).
  Smaller change, no new UI.
- **(b) Explicit user action** — no auto-request; add an "Enable notifications"
  Settings row / non-blocking Snackbar. More correct UX, more work.

Acceptance criteria only require state-sequencing, so (a) satisfies them.
**Recommendation: (a) for v0.1.** Confirm, or pick (b).

### Q3. XML theme cleanup (P6.3) — confirm wanted

This is the **MaterialComponents shell theme** (`Theme.BluetoothKeyboardMouse`,
window background + splash→app handoff), separate from the Compose theme rebranded
in Fix 1. It is cosmetic and slightly risky (a mismatched color can flash during
the splash handoff). Marked optional in the spec. I'll map it to the BlueDeck
palette carefully and preserve `Theme.BlueDeck.Starting` /
`Theme.BluetoothKeyboardMouse`. **Confirm you want it done** (vs deferring).

### Q4. Tile label plan (P6.4)

Plan: store the bonded **name** at connect time (`device.name ?: device.address`)
so the tile shows a friendly label, falling back to address; never mark the tile
active without a confirmed connection (already true after Fix 1). Low-risk, no
architecture change. **Confirm OK** to proceed this way.

---

## C. Minor notes (will handle; not blockers)

- **Reuse existing `BackendMode`.** The spec's `StartupPermissionPlanner` example
  introduces `BackendMode { CLASSIC, BLE_HOGP }`, but the codebase already has
  `BackendMode { CLASSIC_HID, BLE_HOGP }` (BackendMode.kt). I'll reuse it instead
  of creating a duplicate enum.

- **P1 restructures `onCreate` + composes with P4.** Making startup permissions
  backend-aware requires moving the permission request to **after settings load**
  (it currently fires immediately). That naturally produces the "startup resolved"
  signal P4 needs, so I'll implement **one shared signal** that both the backend
  start and the notification prompt hook into.

- **P5 wording.** I'll reclassify validation evidence honestly: only items I can
  actually drive (cold launch, version, physical HID, instrumented) marked
  verified; permission-dialog UX items marked **"Pending manual UX smoke"** rather
  than inferred from unit tests.

- **P3 boot + foreground-start.** Starting a foreground service from
  `BOOT_COMPLETED` is allowed (boot is an exemption to background-start limits).
  The `BootStartPlanner` will be pure (takes `hasClassicPermissions` /
  `hasBlePermissions` booleans computed in the receiver via `checkSelfPermission`).

---

## D. Accurate as written (no concerns)

P2 reordering, P3 `goAsync()` + timeout + `BootStartPlanner`, the new
`StartupPermissionPlannerTest` / `BootStartPlannerTest`, and P6.2 (README scroll
wording) are all accurate and implementable.

---

## Summary of open questions

1. **Q1** BLE-denial on startup: persist `useBleHogp=false` (as written) vs keep it + show re-grant message?
2. **Q2** Notifications: minimal post-startup state flag (recommended) vs explicit Settings row?
3. **Q3** XML theme cleanup: do it now (carefully) vs defer?
4. **Q4** Tile label: store bonded name with address fallback — OK?

Everything else is ready to implement once these are settled.
