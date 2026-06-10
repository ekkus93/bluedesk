# Responses to BlueDeck Release Hardening Spec/TODO (round 1)

Reviewer: Claude (Opus 4.8), cross-checked against the **current** codebase
(commit `0df7af9`). The spec/TODO appear to have been written against current
code (they reference `ServiceForegroundController`, which is new this session),
so most items are accurate. Findings below are grouped; file:line evidence is
included so claims can be trusted without re-deriving them.

---

## A. Confirmed real bugs — agree, will proceed

1. **P1 — backend lifecycle.** `MainActivity.switchBackend(...)` (around
   `MainActivity.kt:370`) only calls `unbindService(...)` for both backends; it
   never calls `stopService(...)`. Started/foreground services can survive after
   unbind. The fix (stop the inactive backend, stop-before-start) is correct.

2. **P2 — permission model (real, confirmed).** In `PermissionPolicy.kt`:
   - `requiredForClassic(31+)` returns **`[BLUETOOTH_SCAN, BLUETOOTH_CONNECT]`** (line 13-17).
   - `startupPermissions(sdkInt) = requiredForClassic(sdkInt)` (line 32).
   - `isClassicStartupBlocked(...)` checks `requiredForClassic(...)` (line 38-44).
   - **Net effect: denying `BLUETOOTH_SCAN` blocks Classic HID startup**, even though
     Classic HID only needs `BLUETOOTH_CONNECT`. The proposed split
     (`requiredForClassicStartup` = connect-only; `requiredForScan` = scan) fixes it.
   - **Extra note:** the `isClassicStartupBlocked` doc comment claims "Denial of
     scan-only ... is not fatal," but the implementation makes scan denial fatal.
     Comment and code currently disagree — worth fixing together.

3. **P3 — pairing scan.** `PairingScreen.requiredBluetoothPermissions()` requests
   scan + connect + advertise + notifications. Over-broad. Fix is correct.

4. **P5.1 — `BleHogpService.onCreate()`.** On missing `BLUETOOTH_CONNECT` it does a
   silent `return` (no `stopSelf()`), confirmed at `BleHogpService.kt:~96-98`.
   Should `stopSelf()` then return. Agree.

5. **P7.1 — forced debug logging.** `DebugLog.setEnabled(true)` is called at
   `MainActivity.onCreate` (`MainActivity.kt:254`). Confirmed. Removing it and
   letting `SettingsViewModel` own logging is correct.

These (plus P8 Quick Settings BLE guard, P2/P3 test updates, P11.2 README, P12
validation) read as accurate and implementable.

---

## B. Decisions needed (conflicts with earlier, deliberate choices)

These are the **questions** — please decide so we don't undo prior intent:

1. **Splash duration (P10.2).** The spec recommends shortening the custom splash
   to **800–1200 ms**. But earlier the product owner explicitly asked to **slow it
   down so the branded name/tagline is visible** — it is currently
   `SPLASH_DISPLAY_MS = 1800L` (`MainActivity.kt:49`) by request. These conflict.
   - **Question:** keep 1800 ms, shorten to ~1000 ms, or compromise (long splash
     on **first launch only**, short thereafter)?

2. **`startForeground()` failure handling (P5.2).** Today
   `ServiceForegroundController.startInForeground()` *deliberately* falls back to
   `mgr.notify(id, notif)` (keep the notification even if true-FGS isn't granted).
   The spec wants to **drop that fallback** and `stopSelf()` + return `false`.
   This reverses an intentional "defensive" decision.
   - **Recommendation:** agree with the spec (a fake FGS can be killed
     unpredictably). **Question:** confirm we should change it to stop-on-failure
     and make `startInForeground(): Boolean`.

3. **Release version (P11.1 / P3 of spec).** Currently `versionName = "1.0"`,
   `versionCode = 1` (`app/build.gradle.kts:17-18`), and a **`v0.1` git tag already
   exists**. The spec recommends `0.1.0`.
   - **Question:** target `versionName = "0.1.0"` (with `versionCode` bump as
     appropriate), stay at `1.0`, or another value? Note the existing `v0.1` tag.

---

## C. Already done / narrower than the spec implies (avoid redundant work)

1. **P6 — SIMPLE/FULL scroll is *already gated at the HID layer*.**
   `HidReportSender.sendScroll`/`sendScrollH` already no-op in SIMPLE mode
   (`HidReportSender.kt:100-106`: `if (!isSimplified()) sendMouseReport(...)`), so
   **no scroll reports are sent in SIMPLE today.** The remaining gap is only the
   **MouseScreen UI** (it still labels "2-finger scroll" and dispatches the
   gesture, which becomes a harmless downstream no-op). So Phase 6 = UI copy +
   a `ScrollPolicy` helper + (optionally) skipping the gesture dispatch — **not** a
   HID-layer change. (A `DescriptorScrollPolicyTest` already exists.)
   - **Suggestion:** reword the spec/TODO so Phase 6 is scoped to UI + dispatch
     gating, and clarify whether "SIMPLE emits no scroll actions" means no
     `Action.ScrollVertical` dispatched (currently it *is* dispatched but no-ops).

2. **P10.3 — strings already exist.** Both `app_name` ("BlueDeck") and
   `bluedeck_tagline` are already string resources (`res/values/strings.xml:2,4`).
   - **Important:** the tagline is intentionally **"The handy keyboard and mouse"**.
     The spec's example wording ("Your phone as a Bluetooth keyboard and mouse.")
     is different — please **do not change the wording**. Only possible work left
     is making the splash *use* `@string/...` if it currently hardcodes the text.

---

## D. Minor inaccuracies in the spec's sample code (will adapt; not blockers)

1. **`DebugLog.w(...)` does not exist.** `DebugLog` exposes only `setEnabled`,
   `log`, and `e` (`DebugLog.kt`). The sample helpers in P4.3 use `DebugLog.w(...)`.
   I'll use `DebugLog.e`/`log` (or add a `w` method) when implementing.

2. **P10.4 — stale launcher resources still exist *and may be referenced*.**
   `drawable/ic_launcher_foreground.xml` and `drawable/ic_launcher_background.xml`
   exist. Before removing, I must confirm the BlueDeck adaptive icon
   (`mipmap-anydpi/...`) doesn't still reference them — I won't delete blindly.
   - **Question:** is it expected that the BlueDeck adaptive icon replaced these,
     or do they back the current launcher? (Will verify regardless.)

---

## E. Confirmed *not* redundant (good)

- **P9 — physical HID docs.** Verified: `docs/PHYSICAL_HID_TESTING.md` currently has
  **zero** mentions of `ConnectProfile` or the HID UUID, even though the passing
  physical runs depend on the DBus `ConnectProfile(HID)` command. So this doc
  update is genuinely needed (not already covered by the earlier FIX3 work).

- **P10.1 — theme palette.** Confirmed the in-app theme still uses
  template-ish colors (`ui/theme/Color.kt`: teal/lavender/pink/indigo; `Theme.kt`
  default M3 background/surface). Applying the BlueDeck palette is legitimate.

---

## F. Posture note (FYI, not a question)

The spec's rule "do not add broad suppressions to make the build appear green" is
fully aligned with the current state: the detekt **baseline is empty** (zero
grandfathered findings). A few things are *intentional and documented* and should
be preserved rather than "fixed":
- `TooManyFunctions` / `LargeClass` thresholds were deliberately raised (documented
  in `detekt.yml`) because the HID services are large but cohesive.
- A handful of `@Suppress` annotations are documented and justified (defensive
  log-and-continue catches around platform calls; HID byte-table `MagicNumber`;
  API/deprecation compat). These are not "fake green."

---

## G. Suggested execution approach (FYI)

This is a 13-phase pass touching the live Bluetooth path. Recommend implementing
**phase-by-phase**, each verified with: `compileDebugKotlin`, `testDebugUnitTest`,
`ktlintCheck`, `detekt`, `lintDebug`, and — for the BT-touching phases (1, 2, 4,
5, 8) — the instrumented suite **plus the 13 physical HID tests** (host-initiated
via DBus `ConnectProfile`). The pure-logic helpers the spec proposes
(`BackendTransitionPlanner`, `ScrollPolicy`, `QuickTilePolicy`,
`missingRequired`) are good and keep the risky parts unit-testable.

---

## Summary of open questions for ChatGPT / product owner

1. Splash duration: 1800 ms (keep) vs ~1000 ms (spec) vs first-launch-only?
2. `startForeground` failure: switch to `stopSelf` + `Boolean` return (drop notify-fallback)? (recommended: yes)
3. Release version: `0.1.0` vs `1.0`? (note existing `v0.1` tag)
4. Phase 6 scope: confirm it's UI/dispatch gating (HID layer already gates SIMPLE scroll).
5. Tagline wording: keep "The handy keyboard and mouse" (do not adopt the spec's example wording) — confirm.
6. Anything that should **not** be touched (theme palette, moving docs to `docs/history/`, etc.)?
