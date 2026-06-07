# UI/UX Fixes TODO — Round 1

Generated: 2026-06-07T06:58:06Z  
Source: UI/UX code review of `MainActivity.kt`, `ExtendedKeysScreen.kt`, `SettingsScreen.kt`, `FunctionKeysScreen.kt`, `NavigationKeysScreen.kt`, `ui/theme/`.

Issues are grouped by priority. Each task is self-contained and can be implemented independently unless noted.

---

## Priority 1 — Functional Gaps (broken or missing features)

### TASK-01: Add Ctrl modifier key to the keyboard modifier row
**File:** `MainActivity.kt` — `KeyboardScreen` composable  
**Problem:** The modifier row shows Shift, CAPS, Alt, Meta but no Ctrl. Without Ctrl, common shortcuts (Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A, etc.) cannot be sent to the host.

- [x] Add `ctrl` field to `KeyboardState` in `AppState.kt` (alongside `shift`, `alt`, `gui`).
- [x] Add `Action.ToggleCtrl` sealed class entry in `Actions.kt`.
- [x] Handle `Action.ToggleCtrl` in `Reducers.kt` — toggle `keyboard.ctrl`, release on `ReleaseLockedModifiers` alongside the other modifiers.
- [x] Verify that `Middleware.kt` already includes the `ctrl` modifier bit (bit 0, value `0x01`) when building HID reports; add it if missing.
- [x] Add a `Ctrl` toggle button to the modifier row in `KeyboardScreen`, placed between Shift and Alt to match a conventional keyboard layout order: Ctrl, Shift, CAPS, Alt, Meta.
- [x] Style the Ctrl button with the same active/inactive `ButtonDefaults.buttonColors` pattern used for the other modifiers.
- [x] Write a unit test in the existing reducer test file verifying `ToggleCtrl` toggles the field and `ReleaseLockedModifiers` clears it.

---

### TASK-02: Fix unreachable "Connect a device first" snackbar on disabled nav items
**File:** `MainActivity.kt` — `MainScreen` composable, `NavigationBar` block  
**Problem:** `NavigationBarItem` with `enabled = false` does not invoke `onClick` in Material3. The snackbar branch `if (!isEnabled) { ... showSnackbar("Connect a device first") }` can never execute, so users tapping a disabled tab get zero feedback.

- [x] Remove the `if (!isEnabled) / else` split from inside the `onClick` lambda — it is dead code.
- [x] Replace the `NavigationBarItem` approach with a wrapper: keep `enabled = true` always and gate navigation logic inside `onClick`; visual disabled state applied via `NavigationBarItemDefaults.colors` at 38% opacity.
- [ ] Verify the snackbar appears when the user taps Keyboard or Mouse tabs while disconnected. *(manual / instrumented test — requires device)*
- [ ] Write the missing unit / UI test for the nav guard snackbar. *(requires Compose UI Test / instrumented; out of scope for host JVM suite)*

---

### TASK-03: Move "Use simplified HID descriptor (Windows)" out of the debug section
**File:** `SettingsScreen.kt` lines 141–147  
**Problem:** This compatibility setting is only visible when debug logging is on. Windows users who need it will never find it.

- [x] Move the "Use simplified HID descriptor (Windows)" `Row`/`Switch` out of the `if (settings.debugLogging)` block.
- [x] Place it under a new `Text("Compatibility")` section header near the bottom of the settings list, after the "Start service on boot" row.
- [x] Add a one-line helper text below the label: `"Enable if your Windows host shows a 'Driver error' after pairing."` (use `MaterialTheme.typography.bodySmall` and `onSurfaceVariant` color).
- [x] Confirm the setting is visible regardless of debug logging state.

---

## Priority 2 — Bugs

### TASK-04: Fix `KeyButton` repeatable branch missing height
**File:** `MainActivity.kt` — `KeyButton` composable, lines 1046–1073  
**Problem:** When `repeatable = true`, the computed `buttonModifier` does not include `.height(44.dp)`, so repeatable buttons may have inconsistent or zero height.

- [ ] In the `if (repeatable)` branch, chain `.height(44.dp)` before `.pointerInput(Unit)`:
  ```kotlin
  val buttonModifier = if (repeatable)
      modifier.height(44.dp).pointerInput(Unit) { ... }
  else
      modifier.height(44.dp)
  ```
- [ ] Visually verify that arrow key buttons and other repeatable keys have consistent height with non-repeatable keys.

---

### TASK-05: Fix redundant null-safe call in connected branch of `PairingScreen`
**File:** `MainActivity.kt` — `PairingScreen` composable, line 735  
**Problem:** `connectedDevice?.name` is called inside the `else` branch of `if (connectedDevice == null)`, so `connectedDevice` is guaranteed non-null. The `?.` is misleading.

- [ ] Change `"Status: Connected to ${connectedDevice?.name}"` to `"Status: Connected to ${connectedDevice.name}"`.

---

### TASK-06: Fix `appState` / `connected` double-collection in `MainScreen`
**File:** `MainActivity.kt` — `MainScreen` composable, lines 317–318 and 466–468  
**Problem:** `StoreProvider.asStateFlow().collectAsState()` is called twice and `connected` / `appState` are declared twice, creating two separate subscribers to the same flow.

- [ ] Remove the second `val appState by StoreProvider.asStateFlow().collectAsState()` and `val connected = ...` inside the `Scaffold` lambda.
- [ ] Replace all references inside the lambda with the outer `appState` and `connected` variables already in scope.

---

## Priority 3 — Visual / UX Polish

### TASK-07: Make Scroll Lock button use color-toggle active state
**File:** `NavigationKeysScreen.kt` — Scroll Lock `Button`, lines 83–99  
**Problem:** All other modifier buttons (Shift, CAPS, Alt, Meta) change background color when active. Scroll Lock only changes its text label ("Scroll Lock (On)").

- [ ] Replace the plain `Button` for Scroll Lock with the same `activeColors` / `inactiveColors` `ButtonDefaults.buttonColors` pattern used in `KeyboardScreen`.
- [ ] Keep the text as just `"Scrl Lk"` (or `"Scroll Lock"`) — the color is sufficient to show active state; the "(On)" suffix is redundant once color feedback is added.

---

### TASK-08: Hide preview console when connected to a real device
**File:** `ExtendedKeysScreen.kt` — preview console `Card`, lines 80–101  
**Problem:** The preview console is always rendered, consuming vertical space even when a Bluetooth host is connected and the console serves no purpose.

- [ ] Read `connected` from `appState.connection.connectedDevice != null` (already in scope at the top of the composable).
- [ ] Read `offlinePreview` from settings or `appState` if available.
- [ ] Wrap the preview console `Card` in `if (!connected || offlinePreview)` so it only renders when useful.

---

### TASK-09: Remove orphaned `Text("Extended keys")` label
**File:** `ExtendedKeysScreen.kt` line 77  
**Problem:** `Text("Extended keys")` is an unstyled, visually orphaned string between the key grid and the preview console. It was likely a debugging placeholder.

- [ ] Delete line 77: `Text("Extended keys")`.
- [ ] If a section label is genuinely desired, add a properly styled `Text("Extended Keys", style = MaterialTheme.typography.titleSmall)` at the top of the composable before the key rows, with appropriate padding.

---

### TASK-10: Make Discovered / Paired device lists height-adaptive in `PairingScreen`
**File:** `MainActivity.kt` — `PairingScreen` composable, lines 642–733  
**Problem:** Both `LazyColumn` lists use `Modifier.weight(1f)`, so each always takes exactly half the available space regardless of item count. An empty "Discovered Devices" list wastes half the screen.

- [ ] Replace the `weight(1f)` modifier on the Discovered Devices `LazyColumn` with `heightIn(min = 0.dp, max = 200.dp)` so it only grows when there are actual results.
- [ ] Keep `weight(1f)` on the Paired Devices `LazyColumn` so it takes the remaining space.
- [ ] Alternatively, combine both lists into a single `LazyColumn` with `item {}` section headers.

---

### TASK-11: Replace hardcoded green connection indicator color
**File:** `MainActivity.kt` — `MainScreen` composable, line 430  
**Problem:** `Color(0xFF4CAF50)` is hardcoded and does not respond to theme or dark/light mode changes.

- [ ] Replace `Color(0xFF4CAF50)` with a theme-aware color. Options:
  - Define `val Connected = Color(0xFF4CAF50)` in `Color.kt` and create a light/dark pair in `Theme.kt`.
  - Or use `MaterialTheme.colorScheme.tertiary` as a reasonable semantic stand-in for a "positive" state color.

---

### TASK-12: Rename theme color variables to match actual colors
**File:** `ui/theme/Color.kt`  
**Problem:** Variables are named `Purple80`, `Purple40`, etc. (Android Studio template defaults) but the actual colors are teal, lavender, and pink. Confusing to anyone reading theme code.

- [ ] Rename:
  - `Purple80` → `TealLight` (or `Teal200`)
  - `PurpleGrey80` → `LavenderLight`
  - `Pink80` → `PinkLight`
  - `Purple40` → `TealDark` (or `Teal700`)
  - `PurpleGrey40` → `IndigoDark`
  - `Pink40` → `PinkMedium`
- [ ] Update references in `Theme.kt` (`darkColorScheme` and `lightColorScheme` calls).

---

### TASK-13: Improve connected-device view in `PairingScreen`
**File:** `MainActivity.kt` — `PairingScreen`, lines 734–739  
**Problem:** When connected, the screen shows only "Status: Connected to [name]" and a Disconnect button. Users cannot scan for or switch to another device without disconnecting first, and there is no contextual information.

- [ ] Show the paired device list even when connected so the user can see all paired devices.
- [ ] Mark the currently connected device with a visual indicator (already done on the card in the disconnected view — reuse that for the connected view).
- [ ] Keep the Disconnect button.
- [ ] Consider adding a "Scan" button even while connected (dispatches `Action.StartDiscovery`) so the user can find new devices without disconnecting.

---

## Priority 4 — Code Quality

### TASK-14: Remove dead code — `ExtendedModButton`
**File:** `MainActivity.kt` lines 948–965  
**Problem:** `ExtendedModButton` is defined but never called anywhere.

- [ ] Delete the entire `ExtendedModButton` composable.

---

### TASK-15: Remove dead parameter — `FunctionKeysScreen.onBack`
**File:** `FunctionKeysScreen.kt` line 21  
**Problem:** `onBack: () -> Unit = {}` is declared but never invoked within the function.

- [ ] Remove the `onBack` parameter from `FunctionKeysScreen`.
- [ ] Remove all call-sites that pass the parameter (search for `FunctionKeysScreen(` to find any).

---

### TASK-16: Simplify `NavigationKeyButton` sizing API
**File:** `NavigationKeysScreen.kt` lines 127–159  
**Problem:** `NavigationKeyButton` accepts both a `modifier` (with `.width()/.height()` from the caller) and separate `width`/`height` parameters fed into `defaultMinSize`. This results in redundant size constraints being applied.

- [ ] Remove the `width` and `height` parameters from `NavigationKeyButton`.
- [ ] Remove the `defaultMinSize` call inside the function.
- [ ] Let the caller's `modifier` fully control size (as it already does via `.width()/.height()` at call sites).
- [ ] Update all call sites accordingly.

---

### TASK-17: Fix `SettingsScreen` coroutine scope leak
**File:** `SettingsScreen.kt` line 34  
**Problem:** `val scope = remember { CoroutineScope(Dispatchers.IO) }` creates a scope not bound to the composable lifecycle. Coroutines launched on this scope will never be cancelled when the composable leaves composition.

- [ ] Replace `remember { CoroutineScope(Dispatchers.IO) }` with `rememberCoroutineScope()`.
- [ ] If IO dispatching is needed, wrap launches with `withContext(Dispatchers.IO)` inside the coroutine body.

---

### TASK-18: Extract duplicated IME label refresh logic in `SettingsScreen`
**File:** `SettingsScreen.kt` — `LaunchedEffect` block (lines 38–57) and Remove button `onClick` (lines 176–193)  
**Problem:** The logic that resolves package names to human-friendly IME labels is duplicated in two places.

- [ ] Extract a private suspend function `loadImeLabels(context: Context, overrides: Map<String, Boolean>): Map<String, String>`.
- [ ] Call it from both `LaunchedEffect(Unit)` and the Remove button's `onClick`.

---

### TASK-19: Remove leftover `android.util.Log.d` debug calls from `PairingScreen`
**File:** `MainActivity.kt` — Scan button `onClick`, lines 626–629  
**Problem:** Raw `android.util.Log.d("BTKB", ...)` calls are left in production code. All logging should go through `DebugLog`.

- [ ] Delete or replace the two `android.util.Log.d` calls with `DebugLog.log(...)` equivalents, or remove them entirely if the information is redundant.

---

## Priority 5 — Theme & Dependencies

### TASK-20: Replace deprecated `accompanist-systemuicontroller`
**File:** `ui/theme/Theme.kt`  
**Problem:** `com.google.accompanist:accompanist-systemuicontroller` is deprecated and no longer maintained by Google.

- [ ] Remove the `accompanist-systemuicontroller` dependency from `build.gradle.kts` (**ask first per CLAUDE.md policy**).
- [ ] Replace the `rememberSystemUiController()` + `SideEffect` block in `Theme.kt` with `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity.onCreate` and use `WindowInsetsControllerCompat` to set system bar color/icon brightness.
- [ ] Verify status bar and navigation bar colors are unchanged after the migration.

---

### TASK-21: Decide on dynamic color behavior
**File:** `ui/theme/Theme.kt` line 45  
**Problem:** `dynamicColor = true` by default means the teal/indigo palette is overridden by the system wallpaper color on Android 12+ devices. The app has no visual identity on those devices.

- [ ] Decide policy: keep dynamic color (system-adaptive, no brand identity) or disable it (`dynamicColor = false`, always use the app's teal/indigo palette).
- [ ] If disabling, change the default parameter in `AndroidbtkbmouseTheme` to `dynamicColor: Boolean = false`.
- [ ] If keeping it, document the decision here so it is not accidentally changed later.

---

## Checklist Summary

| ID | Description | Priority | Status |
|----|-------------|----------|--------|
| TASK-01 | Add Ctrl modifier key | P1 Functional | [x] |
| TASK-02 | Fix unreachable nav snackbar | P1 Bug | [x] |
| TASK-03 | Move HID descriptor setting out of debug section | P1 Functional | [x] |
| TASK-04 | Fix `KeyButton` repeatable height | P2 Bug | [ ] |
| TASK-05 | Fix null-safe call in connected branch | P2 Bug | [ ] |
| TASK-06 | Fix double `appState` collection in `MainScreen` | P2 Bug | [ ] |
| TASK-07 | Scroll Lock color-toggle active state | P3 Polish | [ ] |
| TASK-08 | Hide preview console when connected | P3 Polish | [ ] |
| TASK-09 | Remove orphaned `Text("Extended keys")` | P3 Polish | [ ] |
| TASK-10 | Height-adaptive device lists in PairingScreen | P3 Polish | [ ] |
| TASK-11 | Replace hardcoded green connection color | P3 Polish | [ ] |
| TASK-12 | Rename theme color variables | P3 Polish | [ ] |
| TASK-13 | Improve connected-device view in PairingScreen | P3 Polish | [ ] |
| TASK-14 | Remove dead `ExtendedModButton` | P4 Quality | [ ] |
| TASK-15 | Remove dead `onBack` parameter | P4 Quality | [ ] |
| TASK-16 | Simplify `NavigationKeyButton` sizing API | P4 Quality | [ ] |
| TASK-17 | Fix `SettingsScreen` coroutine scope leak | P4 Quality | [ ] |
| TASK-18 | Extract duplicated IME label refresh logic | P4 Quality | [ ] |
| TASK-19 | Remove leftover `Log.d` debug calls | P4 Quality | [ ] |
| TASK-20 | Replace deprecated `accompanist-systemuicontroller` | P5 Deps | [ ] |
| TASK-21 | Decide on dynamic color behavior | P5 Theme | [ ] |
