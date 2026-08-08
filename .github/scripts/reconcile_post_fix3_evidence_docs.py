from pathlib import Path

TODO = Path("docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_TODO_2026-08-08.md")
UIUX = Path("docs/UIUX_FIXES1_TODO.md")


def replace_exact(text: str, old: str, new: str, label: str, expected: int = 1) -> str:
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} matches, found {count}")
    return text.replace(old, new)


def check_region(text: str, start: str, end: str) -> str:
    start_idx = text.index(start)
    end_idx = text.index(end, start_idx)
    region = text[start_idx:end_idx]
    return text[:start_idx] + region.replace("- [ ]", "- [x]") + text[end_idx:]


# Main hardening TODO: implementation/automated phases are complete. Physical/manual
# validation remains explicitly open below.
todo = TODO.read_text()

todo = check_region(todo, "## 0. Ralph-loop execution rules", "# Phase 22 — Full validation")

# Conditional alternatives in Phase 19 were not needed because stable permanent emulator
# infrastructure and the full requested matrix exist.
todo = replace_exact(
    todo,
    """If infrastructure cannot support a stable emulator job:\n\n- [x] document the concrete blocker,\n- [x] add the strongest repeatable alternative available,\n- [x] do not label unrun instrumented tests as CI-covered.\n""",
    """If infrastructure cannot support a stable emulator job:\n\n- [x] N/A — stable permanent emulator infrastructure is available; no blocker path is required.\n- [x] N/A — the canonical `connectedDebugAndroidTest` path runs in permanent CI.\n- [x] N/A — instrumented coverage is run and recorded rather than inferred.\n""",
    "Phase 19.2 conditional alternative",
)
todo = replace_exact(
    todo,
    """If full emulator matrix is too expensive:\n\n- [x] keep deterministic version-policy tests on every CI run,\n- [x] run a smaller representative emulator matrix.\n""",
    """If full emulator matrix is too expensive:\n\n- [x] N/A — the full API 28/30/31/34/35 emulator matrix is practical and permanent.\n- [x] N/A — no reduced representative matrix is needed.\n""",
    "Phase 19.3 conditional alternative",
)

# Phase 22 automated validation through the API matrix is complete.
todo = check_region(todo, "## Task 22.1 — JVM/unit suite", "## Task 22.6 — Physical Classic HID validation")

# Physical Classic: requirement/disposition is known, but the exact-SHA run itself is pending.
todo = replace_exact(
    todo,
    "- [ ] Required / Not required is explicitly recorded.",
    "- [x] Required — exact-SHA Classic physical HID rerun is required because registration/report/connection behavior changed materially.",
    "Task 22.6 requirement disposition",
)
todo = replace_exact(
    todo,
    "- [ ] If run, result recorded against exact SHA.",
    "- [ ] PENDING — run physical Classic HID validation against `953df07df97779c7cc85f3f9bc1acb1e77821c7d` and record the result.",
    "Task 22.6 exact-SHA result",
)
todo = replace_exact(
    todo,
    "- [ ] If not run, existing historical evidence remains labeled historical rather than current exact-SHA evidence.",
    "- [x] Existing physical HID evidence remains explicitly labeled historical; it is not current exact-SHA evidence.",
    "Task 22.6 historical evidence",
)

# Phase 23 integrity gates are automated/code-review gates and are satisfied. Its
# validation-integrity item explicitly permits physical/manual evidence to be accurately
# labeled pending, which it is.
todo = check_region(todo, "# Phase 23 — Final no-regression and release acceptance", "# Phase 24 — Final evidence record")

# Fill the permanent evidence fields with exact identities/results.
todo = replace_exact(todo, "- TODO commit: `<fill after this file is created>`", "- TODO commit: `73fed407b487e173958554d4a6e93a9d6d6515dd`", "TODO commit")
todo = replace_exact(todo, "- Implementation-start SHA: `<pending>`", "- Implementation-start SHA: `20e85fba1b1b167bb1fd96dbc1e3734cadd005a0`", "implementation start")
todo = replace_exact(todo, "- Final implementation SHA: `<pending>`", "- Final implementation SHA: `953df07df97779c7cc85f3f9bc1acb1e77821c7d`", "final implementation")
todo = replace_exact(todo, "- Final documentation/evidence SHA: `<pending>`", "- Final documentation/evidence SHA: `a652c2edc4f78f41058625e50e46c52ec4ac1354` (evidence-record commit; this TODO reconciliation is a documentation-only follow-up)", "evidence SHA")

todo = replace_exact(todo, "- JVM/unit: `<pending>`", "- JVM/unit: `PASS — permanent CI run 31284953872 / job 93172012610; Gradle console did not print a stable aggregate JVM count`", "JVM evidence")
todo = replace_exact(todo, "- Build: `<pending>`", "- Build: `PASS — :app:assembleDebug on exact final implementation SHA`", "build evidence")
todo = replace_exact(todo, "- Lint: `<pending>`", "- Lint: `PASS — :app:lintDebug`", "lint evidence")
todo = replace_exact(todo, "- ktlint: `<pending>`", "- ktlint: `PASS — :app:ktlintCheck`", "ktlint evidence")
todo = replace_exact(todo, "- detekt: `<pending>`", "- detekt: `PASS — :app:detekt; no baseline regeneration or threshold relaxation`", "detekt evidence")
todo = replace_exact(todo, "- Instrumented/Compose: `<pending>`", "- Instrumented/Compose: `PASS — permanent run 31284953866 across API 28/30/31/34/35; API 35 finished 107 tests, 13 physical tests skipped by design, 0 failed`", "instrumented evidence")
todo = replace_exact(todo, "- CI run/job URL or IDs: `<pending>`", "- CI run/job URL or IDs: `CI 31284953872 / 93172012610; instrumented matrix 31284953866 / API28 93172016678 / API30 93172016686 / API31 93172016691 / API34 93172016673 / API35 93172016657`", "run ids")

todo = replace_exact(todo, "- API 26/27 disposition: `<pending>`", "- API 26/27 disposition: `N/A — intentionally unsupported/not installable; product minimum is API 28`", "API26/27")
todo = replace_exact(todo, "- API 28: `<pending>`", "- API 28: `PASS — job 93172016678`", "API28")
todo = replace_exact(todo, "- API 30: `<pending>`", "- API 30: `PASS — job 93172016686`", "API30")
todo = replace_exact(todo, "- API 31: `<pending>`", "- API 31: `PASS — job 93172016691`", "API31")
todo = replace_exact(todo, "- API 34: `<pending>`", "- API 34: `PASS — job 93172016673`", "API34")
todo = replace_exact(todo, "- Current high API: `<pending>`", "- Current high API: `API 35 PASS — job 93172016657`", "high API")

todo = replace_exact(todo, "- Physical Classic HID exact-SHA run: `<pending / not required with rationale>`", "- Physical Classic HID exact-SHA run: `PENDING — REQUIRED on 953df07df97779c7cc85f3f9bc1acb1e77821c7d`", "physical Classic")
todo = replace_exact(todo, "- BLE device smoke: `<pending>`", "- BLE device smoke: `PENDING — physical device smoke required`", "BLE smoke")
todo = replace_exact(todo, "- Manual UX smoke: `<pending>`", "- Manual UX smoke: `PENDING — real-device UX pass required`", "manual smoke")

status_note = """# Current Ralph-loop disposition\n\n- **Automated hardening:** PASS on exact implementation SHA `953df07df97779c7cc85f3f9bc1acb1e77821c7d`.\n- **Permanent CI:** PASS — run `31284953872`, job `93172012610`.\n- **Permanent instrumented matrix:** PASS — run `31284953866`, APIs 28/30/31/34/35.\n- **Overall acceptance:** OPEN. Task 22.6 physical Classic exact-SHA validation and Task 22.7 / BLE real-device smoke remain pending.\n- Permanent evidence: `docs/BLUEDECK_POST_FIX3_CORRECTNESS_UI_HARDENING_EVIDENCE_2026-08-08.md` at evidence commit `a652c2edc4f78f41058625e50e46c52ec4ac1354`.\n\n---\n\n"""
todo = replace_exact(todo, "# Completion condition\n", status_note + "# Completion condition\n", "status note insertion")
TODO.write_text(todo)

# Historical UI/UX bookkeeping: check only items now demonstrated by exact real-screen
# or source evidence. The Task-04 manual visual box intentionally remains open.
uiux = UIUX.read_text()
uiux = replace_exact(
    uiux,
    '- [ ] Verify the snackbar appears when the user taps Keyboard or Mouse tabs while disconnected. *(manual / instrumented test — requires device)*',
    '- [x] Verify the snackbar appears when the user taps Keyboard or Mouse tabs while disconnected. *(instrumented production-screen evidence: post-Fix3 hardening API 28/30/31/34/35 matrix)*',
    "UIUX TASK-02 snackbar verification",
)
uiux = replace_exact(
    uiux,
    '- [ ] Write the missing unit / UI test for the nav guard snackbar. *(requires Compose UI Test / instrumented; out of scope for host JVM suite)*',
    '- [x] Write the missing UI test for the nav guard snackbar. *(implemented as real Compose/instrumented production-screen coverage in the post-Fix3 hardening pass)*',
    "UIUX TASK-02 UI test",
)
uiux = replace_exact(
    uiux,
    '- [ ] Replace the plain `Button` for Scroll Lock with the same `activeColors` / `inactiveColors` `ButtonDefaults.buttonColors` pattern used in `KeyboardScreen`.',
    '- [x] Replace the plain `Button` for Scroll Lock with active/inactive `ButtonDefaults.buttonColors` state styling; verified in current production `NavigationKeysScreen`.',
    "UIUX TASK-07 color state",
)
uiux = replace_exact(
    uiux,
    '- [ ] Keep the text as just `"Scrl Lk"` (or `"Scroll Lock"`) — the color is sufficient to show active state; the "(On)" suffix is redundant once color feedback is added.',
    '- [x] Keep the text as `"Scrl Lk"`; active state is conveyed by color rather than an `"(On)"` suffix.',
    "UIUX TASK-07 label",
)
uiux = replace_exact(
    uiux,
    '- [ ] Visually verify that arrow key buttons and other repeatable keys have consistent height with non-repeatable keys. *(manual verification on device)*',
    '- [ ] Visually verify that arrow key buttons and other repeatable keys have consistent height with non-repeatable keys. *(manual verification on device still pending; automated normal/large-font Navigation grid height tests now pass)*',
    "UIUX TASK-04 manual remains open",
)
UIUX.write_text(uiux)
