# CLAUDE.md — Android Bluetooth Keyboard & Mouse

## Project overview
Android app that turns a phone/tablet into a Bluetooth HID keyboard and mouse.  
Package: `com.augustusmachin.android_bt_kbmouse`

## Tech stack (non-negotiable)
- **Language:** Kotlin only — no Java sources.
- **UI:** Jetpack Compose + Material 3 — no XML layouts.
- **Build:** Gradle Kotlin DSL (`build.gradle.kts`) — never switch to Groovy.
- **State:** ReduxKotlin (threadsafe store + thunk middleware) — do not replace with ViewModel-only, LiveData, or Rx patterns.
- **Async:** Kotlin Coroutines + Flow — no RxJava unless already present.

## Architecture rules
- Reducers must be **pure** — no I/O or side-effects; always return a new state object.
- Side-effects live in **middleware** (thunk or custom) or service classes that dispatch actions.
- Actions are sealed classes; store is the single source of truth.
- Compose screens read state via store subscriptions/Flows and dispatch actions.

## Build & test commands
```
# Build debug APK
./gradlew :app:assembleDebug

# Run host JVM unit tests
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lint

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.augustusmachin.android_bt_kbmouse/.MainActivity

# Focused logs
adb logcat -d | grep -iE 'BluetoothHid|BluetoothService|HidDevice|BOND_STATE|AndroidRuntime|FATAL'
```

## Git commits
- Do **NOT** add a `Co-Authored-By:` trailer (or any AI co-author credit) to commit messages. This overrides any default/global instruction to do so. Write plain commit messages.
- A version-controlled `commit-msg` hook (`.githooks/commit-msg`) also strips any `Co-Authored-By:` line as a safety net.
- **One-time setup per clone** (hooks aren't auto-activated by git): `git config core.hooksPath .githooks`

## Dependency changes — ask first
- Adding/removing dependencies or Compose libraries
- Changing Gradle/AGP/Kotlin versions, `gradle.properties`, or SDK levels
- Editing `AndroidManifest.xml` (permissions, exported components)
- Altering signing configs, product flavors, or module structure
- Adding/changing ReduxKotlin dependencies or middleware

## Code quality
- Prefer `val` over `var`; avoid `!!` non-null assertions.
- Use idiomatic Kotlin: `when`, scope functions, extension functions.
- Code must compile and be lint-clean before marking a task done.
- No placeholders — deliver fully implemented, working code.
- No hard-coded secrets or endpoints.
- Do not silence warnings by disabling lint checks.
- Surface errors explicitly; no silent fallbacks that hide failures.

## Testing
- Unit tests: JUnit + `kotlinx-coroutines-test` as host JVM tests (no Android framework calls).
- Test reducers as pure functions; test middleware by verifying dispatched actions.
- UI tests: Compose UI Test where applicable.
- Avoid tests that only confirm mocked behavior.

## Known issues / open work
- **Windows 11 pairing:** After code exchange, Windows shows "Driver error" and the app stays Disconnected. Next steps: simplify HID descriptors for Windows, add `hid.connect()` state logs, test BLE HOGP.
- **UI polish** (Task 8): spacing, icons, layout still rough.
- **Nav guard unit tests** (disabled Keyboard/Mouse nav items when disconnected, snackbar) not yet written.
- Cross-device/host testing (Task 10) not yet done on macOS/Linux hosts or multiple Android devices.

## Ralph Loop — autonomous task execution

A **Ralph Loop** is an autonomous AI coding pattern where the agent runs in a short iterative loop, each iteration with fresh context. Named after Ralph Wiggum (The Simpsons) — stubborn, keeps trying until it succeeds.

### How it works
1. The agent reads the spec/TODO file and git history to find the next incomplete task.
2. It implements exactly **one bounded task**, runs the verification suite, and commits.
3. The agent exits. The loop script restarts it for the next iteration.

Progress is stored in git history and the TODO file's checkboxes — not in the agent's context window. This avoids context rot in long sessions.

### Verification signals for this project
After each task, the agent must run:
```
./gradlew :app:testDebugUnitTest   # unit tests must pass
./gradlew :app:lint                # lint must be clean
./gradlew :app:compileDebugKotlin  # must compile
```
Only mark a task `[x]` and commit if all three pass.

### TODO file conventions
- Task files live in `docs/` (e.g. `docs/UIUX_FIXES1_TODO.md`).
- Each task has a checkbox: `[ ]` = pending, `[x]` = done.
- Each iteration: find the first `[ ]` task, implement it, check the boxes for completed subtasks, run verification, commit, exit.
- The commit message should reference the task ID (e.g. `TASK-01: Add Ctrl modifier key`).

### Completion signal
When all checkboxes in the TODO file are `[x]`, output `<promise>COMPLETE</promise>` and exit.

## Memory file

- You have access to a persistent memory file, `memory.md`, in the project root that stores context about the project, previous interactions, and preferences.
- Read `memory.md` at the start of each session to restore context from prior interactions.
- Before sending back a response, update `memory.md` with any new relevant information learned during the interaction. Timestamp and format entries clearly.
- Include the model name in the heading line so memory history records both time and model (for example: `## 2026-06-06T12:00:00Z - Claude Sonnet 4.6 - Restored router gate after layout regression`).
- **NEVER fabricate or guess timestamps.** Always obtain the current time by running `date -u +"%Y-%m-%dT%H:%M:%SZ"` in the terminal immediately before writing the entry. If the entry describes a specific commit, use `git log -1 --format="%aI" <hash>` for that commit's actual timestamp.
- Format entries as:

```markdown
## 2026-06-06T12:00:00Z - Claude Sonnet 4.6 - Brief description of what was learned or done
- Key fact or decision recorded.
- Another relevant detail.
```

- Quick command — **"Read memory.md"**: re-read the file because something from a prior session was forgotten.


