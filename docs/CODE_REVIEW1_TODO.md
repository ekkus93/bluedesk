# Code Review Fixes TODO — Round 1

Generated: 2026-06-07T19:03:36Z  
Source: Code review of full Kotlin source tree (BluetoothService, BluetoothHidModule, Middleware, MainActivity, ExtendedKeysScreen, and all supporting files).

Tasks are ordered by severity. Each is self-contained unless a dependency is noted.

---

## Priority 1 — Critical HID Protocol Bugs (B1, B2, B3)

TASK-01 through TASK-03 are deeply related and should be implemented together in a single commit. They all stem from the same root cause: the HID device registers as keyboard-only while mouse reports are sent alongside it using an incompatible format.

---

### TASK-01: Build correct combined keyboard+mouse HID descriptors
**File:** `HidDescriptorVariants.kt`  
**Problem:** The existing `FULL` and `SIMPLE` descriptors have two defects that prevent them from being used directly:
1. No explicit report IDs — keyboard and mouse share report ID 0, making it impossible for the host to tell them apart.
2. No LED output report — the keyboard section lacks the 5-bit LED + 3-bit padding output report, so Caps Lock and Scroll Lock feedback from the host breaks.
3. `FULL` includes a Consumer Control (Play/Pause) entry that adds unnecessary complexity for Windows compatibility.

The correct structure for both variants is:

**Keyboard collection (report ID = 1, 8-byte input + 1-byte LED output):**
```
Usage Page (Generic Desktop), Usage (Keyboard), Collection (Application),
  Report ID (1),
  [modifier byte: 8×1-bit keys 0xE0-0xE7]
  [reserved byte: Report Count(1), Report Size(8), Input(Constant)]
  [LED output: Report Count(5), Report Size(1), Usage Page(LEDs), 
               Usage Min(1), Usage Max(5), Output(Data,Var,Abs)]
  [LED padding: Report Count(1), Report Size(3), Output(Constant)]
  [6-key array: Report Count(6), Report Size(8), Usage Page(Keycodes),
                Usage Min(0), Usage Max(101), Input(Data,Array,Abs)]
End Collection
```

**Mouse collection (report ID = 2):**
- SIMPLE variant: buttons(3 bits) + padding(5 bits) + X(8) + Y(8) = 3 data bytes. No scroll wheels.
- FULL variant: buttons(3 bits) + padding(5 bits) + X(8) + Y(8) + wheel-V(8) + wheel-H(8) = 5 data bytes.

Subtasks:
- [x] Rewrite `HidDescriptorVariants.SIMPLE` as a combined descriptor with report ID 1 (keyboard+LEDs) and report ID 2 (mouse, no scroll).
- [x] Rewrite `HidDescriptorVariants.FULL` as a combined descriptor with report ID 1 (keyboard+LEDs) and report ID 2 (mouse with vertical+horizontal scroll).
- [x] Remove the Consumer Control entry from `FULL` — it complicates Windows enumeration and isn't used.
- [x] Add a constant: `const val REPORT_ID_KEYBOARD: Byte = 1` and `const val REPORT_ID_MOUSE: Byte = 2` in `HidDescriptorVariants` (or in a shared `HidConstants.kt`) so report IDs are never magic-numbered elsewhere.
- [x] Write host JVM unit tests that verify both descriptors parse to expected byte arrays (compare expected byte sequences to catch regressions).

---

### TASK-02: Wire the simplified-descriptor setting through to HID registration
**Files:** `BluetoothHidModule.kt`, `BluetoothService.kt`  
**Problem (B2):** `BluetoothService.onServiceConnected` reads `settings.hidSimplified` into a local `simplified` variable but never passes it to `BluetoothHidModule.registerApp`, which always uses its own internal `keyboardReportDescriptor`. The Windows HID descriptor toggle has zero effect.  
**Depends on:** TASK-01 (new descriptors must exist first).

Subtasks:
- [x] Change `BluetoothHidModule.registerApp(proxy: BluetoothProfile)` to `registerApp(proxy: BluetoothProfile, simplified: Boolean)`.
- [x] Inside `registerApp`, replace the hardcoded `keyboardReportDescriptor` argument in `BluetoothHidDeviceAppSdpSettings` with `HidDescriptorVariants.select(simplified)`.
- [x] Change the SDP subclass from `0x40` (keyboard boot-protocol only) to `0xC0` (keyboard+mouse combo) so the host enumerates both collections. Note: test whether changing the subclass helps or hurts Windows pairing — if it causes regression, revert to `0x40` and document the finding.
- [x] Delete the `keyboardReportDescriptor` and `mouseReportDescriptor` fields from `BluetoothHidModule` — they are now superseded by `HidDescriptorVariants`.
- [x] In `BluetoothService.onServiceConnected`, pass `simplified` to the updated `registerApp(proxy, simplified)` call.
- [ ] Fix the `runBlocking` call on the callback thread at the same time (see TASK-04 below — move setting read before `registerApp` or use a pre-cached value).

---

### TASK-03: Fix HID report IDs and restore scroll
**Files:** `BluetoothService.kt`  
**Problem (B1, B3):** `sendCurrentKeyboardReport` and `sendMouseReport`/`clickMouse` all call `hid.sendReport(device, 0, report)` with report ID 0. After TASK-01 adds explicit report IDs, ID 0 is invalid — the host will reject all reports. Additionally, `sendScroll` and `sendScrollH` are empty method bodies so two-finger scroll never reaches the host.  
**Depends on:** TASK-01 (report ID constants), TASK-02 (descriptor registration).

Subtasks:
- [x] Update `sendCurrentKeyboardReport` to call `hid.sendReport(device, HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), report)`. The `report` byte array remains 8 bytes (mods + reserved + 6 keys) — unchanged.
- [x] Update `sendMouseReport` to call `hid.sendReport(device, HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), report)`. Report byte array changes by variant:
  - SIMPLE: 3 bytes `[buttons][dx][dy]`
  - FULL: 5 bytes `[buttons][dx][dy][wheelV][wheelH]`
  Track which variant is active (store it as a field when `registerApp` is called).
- [x] Update `clickMouse` similarly — use report ID 2, size 3 or 5 depending on variant.
- [x] Restore `sendScroll(delta: Int)`: in the FULL descriptor variant, send a mouse report with buttons=0, dx=0, dy=0, wheelV=delta. In the SIMPLE variant, this should remain a no-op (scroll wheels are not in that descriptor) — log a debug message rather than silently doing nothing.
- [x] Restore `sendScrollH(delta: Int)`: same pattern as vertical scroll but wheelH byte.
- [x] Add a private field `var activeDescriptorSimplified: Boolean = true` (or read from a stored flag) so `sendMouseReport` knows which report size to use.
- [x] Verify that `pressedKeys` 6-key array and modifier byte still go into the keyboard report unchanged — the keyboard format itself does not change, only the report ID header.
- [x] Write a unit test (host JVM) using `HidReportBuilder` that verifies keyboard report ID=1 and mouse report ID=2 byte layout matches what `sendCurrentKeyboardReport` and `sendMouseReport` would produce.

---

## Priority 2 — Medium Bugs

### TASK-04: Eliminate `runBlocking` on the Bluetooth profile callback thread
**File:** `BluetoothService.kt:222-226`  
**Problem (B5):** `kotlinx.coroutines.runBlocking { SettingsManager.flow(...).first() }` is called inside `profileListener.onServiceConnected`, which runs on the Bluetooth stack's internal callback thread. If DataStore has a cold start or disk contention, this blocks the callback thread and can trigger an ANR.

Subtasks:
- [x] Add a `@Volatile private var cachedSimplified: Boolean = true` field to `BluetoothService`.
- [x] In `onCreate`, launch a coroutine (using `CoroutineScope(Dispatchers.IO + Job())`) to read `SettingsManager.flow(this).first().hidSimplified` and store it in `cachedSimplified`. The coroutine should complete well before `onServiceConnected` fires in practice.
- [x] In `onServiceConnected`, replace the `runBlocking` block with `cachedSimplified`.
- [x] Cancel the coroutine scope in `onDestroy`.
- [x] Alternatively, subscribe to the settings flow and keep `cachedSimplified` up to date continuously (so a settings change mid-session takes effect on next registration).

---

### TASK-05: Fix `discoveredDevices` list thread safety
**File:** `BluetoothService.kt:40, 62, 339`  
**Problem (B6):** `discoveredDevices` is a plain `mutableListOf<BluetoothDevice>()`. The `ACTION_FOUND` broadcast adds items to it; `getDiscoveredDevices()` calls `toList()` which iterates it. While broadcast receivers typically run on the main thread, the list is accessed from middleware (runs on `Dispatchers.Default`) without synchronization. A concurrent add during iteration can throw `ConcurrentModificationException`.

Subtasks:
- [ ] Replace `private val discoveredDevices = mutableListOf<BluetoothDevice>()` with `private val discoveredDevices = java.util.concurrent.CopyOnWriteArrayList<BluetoothDevice>()`.
- [ ] `CopyOnWriteArrayList.toList()` is safe under concurrent writes — no other changes needed.
- [ ] Verify `discoveredDevices.clear()` in `startDiscovery()` still works (`CopyOnWriteArrayList` supports `clear()`).
- [ ] Verify `discoveredDevices.contains(it)` is still correct (it is — `CopyOnWriteArrayList` is a `List`).

---

### TASK-06: Remove remaining `android.util.Log.d` calls missed in TASK-19
**Files:** `BluetoothHidModule.kt:91,97`, `BluetoothService.kt:322`  
**Problem (B4):** TASK-19 removed raw `Log.d` calls from `PairingScreen` but three remain in service code.

- `BluetoothHidModule.kt:91` — `android.util.Log.d("BTKB", "onAppStatusChanged ...")` in `onAppStatusChanged` callback
- `BluetoothHidModule.kt:97` — `android.util.Log.d("BTKB", "onConnectionStateChanged ...")` in `onConnectionStateChanged` callback
- `BluetoothService.kt:322` — `android.util.Log.d("BTKB", "BluetoothService.startDiscovery invoked")` in `startDiscovery()`

Subtasks:
- [ ] In `BluetoothHidModule.kt:91`, remove the raw `Log.d` call. The `DebugLog.log(...)` call immediately above it already covers this.
- [ ] In `BluetoothHidModule.kt:97`, remove the raw `Log.d` call. Same — `DebugLog.log(...)` is already present.
- [ ] In `BluetoothService.kt:322`, remove the raw `Log.d("BTKB", "BluetoothService.startDiscovery invoked")` line. The `DebugLog.log("BluetoothService", "startDiscovery")` on the line above is sufficient.
- [ ] Run lint to confirm no other `android.util.Log` calls exist outside `DebugLog.kt` itself.

---

## Priority 3 — Minor Bugs

### TASK-07: Fix missing `Locale` in `SimpleDateFormat` in `ExtendedKeysScreen`
**File:** `ExtendedKeysScreen.kt:56`  
**Problem (B7):** `java.text.SimpleDateFormat("HH:mm:ss")` uses the device locale for formatting, which can produce unexpected results in non-Latin locales and triggers a lint warning.

Subtasks:
- [ ] Change:
  ```kotlin
  java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
  ```
  to:
  ```kotlin
  java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
  ```
- [ ] Verify lint passes clean after the fix.

---

## Priority 4 — Code Quality

### TASK-08: Remove dead code — unreachable `"extended"` composable route
**File:** `MainActivity.kt:497`  
**Problem (Q4):** `NavHost` registers `composable("extended") { ExtendedKeysScreen() }` but no navigation item in the `Screen` enum, no `navController.navigate("extended")` call, and no back-stack pop targets this route. The `ExtendedKeysScreen` is rendered inline as `selectedTab == 0` inside `KeyboardScreen`. This route is orphaned.

Subtasks:
- [ ] Delete `composable("extended") { ExtendedKeysScreen() }` from the `NavHost` block.
- [ ] Search for any remaining `navigate("extended")` or `route == "extended"` references and remove them.
- [ ] Verify that `ExtendedKeysScreen` still renders correctly as an inline tab composable (it does — the route deletion doesn't affect the tab rendering path).

---

### TASK-09: Factor out repeated `getSharedPreferences("bt_hid", ...)` calls
**File:** `BluetoothService.kt` (12+ call sites)  
**Problem (Q5):** The same `getSharedPreferences("bt_hid", MODE_PRIVATE)` expression is scattered across `onCreate`, `connectDevice`, `disconnectDevice`, `setDefaultDevice`, `getAlias`, `setAlias`, `forgetDevice`, `notifActionReceiver`, and several lambdas. Every call site duplicates the string key `"bt_hid"`.

Subtasks:
- [ ] Add `private val btPrefs by lazy { getSharedPreferences("bt_hid", MODE_PRIVATE) }` as a field in `BluetoothService`.
- [ ] Replace all `getSharedPreferences("bt_hid", MODE_PRIVATE)` call sites with `btPrefs`.
- [ ] Confirm that `lazy` initialization is safe here — `getSharedPreferences` is available as soon as `Service.onCreate()` is called, and `btPrefs` is first accessed after `onCreate`, so this is correct.

---

### TASK-10: Integrate `BleHogpService` with the Redux store or remove it
**Files:** `BleHogpService.kt`, `BleHogpLogic.kt`, `MainActivity.kt:287-288`  
**Problem (Q7):** `BleHogpService` is started as a foreground service on every launch alongside `BluetoothService`, but it has no service event listener wired to the Redux store, no corresponding `ServiceConnection`, and no UI to enable/switch to it. Its connection events (connected, disconnected, key sends) are invisible to the rest of the app. It either needs to be fully integrated or removed.

Two options — choose one:

**Option A: Remove (if BLE HOGP is not ready)**
- [ ] Remove `BleHogpService` and `BleHogpLogic` from `startServicesAndBind()`.
- [ ] Remove the `<service android:name=".BleHogpService" ...>` entry from `AndroidManifest.xml`.
- [ ] Delete `BleHogpService.kt` and `BleHogpLogic.kt`.
- [ ] Update `CLAUDE.md` Known Issues to note BLE HOGP was removed pending a proper implementation.

**Option B: Integrate (if BLE HOGP is the desired Windows fix path)**
- [ ] Add `BleHogpService.ServiceEventListener` (mirroring `BluetoothService.ServiceEventListener`) and dispatch the same `Action.UpdateConnectedDevice`, `Action.UpdateMessage`, `Action.UpdateLocks` actions from it.
- [ ] Wire `BleHogpService` key/mouse send calls through a second `KeySender` or extend `StoreProvider` to hold two senders and fan out.
- [ ] Add a Settings toggle ("Use BLE HOGP instead of Classic BT") that controls which service is started and bound.
- [ ] Add a `ServiceConnection` for `BleHogpService` in `MainActivity` (same pattern as `BluetoothService`).
- [ ] Document the trade-offs (BLE HOGP: better Windows compatibility but higher latency; Classic: lower latency but Windows driver issues).

---

### TASK-11: Resolve the three-parallel-settings-sources problem
**Files:** `SettingsScreen.kt`, `MainScreen` in `MainActivity.kt`, `store/AppState.kt`  
**Problem (Q3):** Settings are read from three different sources depending on which composable you're in:
1. `SettingsScreen` reads directly from `SettingsManager.flow(context)`.
2. `MainScreen` reads from `SettingsViewModel.settings` (which also reads from `SettingsManager.flow()`).
3. The Redux store has a `SettingsState` field that `Action.UpdateSettings` populates, but no composable outside of middleware reads from it authoritatively.

This means a settings change in `SettingsScreen` triggers a `DataStore` write, which propagates to `SettingsViewModel`, but the Redux store's `SettingsState` is only updated if `SettingsViewModel` also dispatches `Action.UpdateSettings`. The store's settings are not the canonical source.

Subtasks:
- [ ] Decide on the canonical source: recommend keeping `SettingsManager`/`DataStore` as the canonical store, and making `SettingsViewModel` the single collector in the UI layer. Remove `SettingsState` from `AppState` and the `Action.UpdateSettings`/`Action.UpdateImeOverrides` actions if they are not used by reducers or middleware (audit first).
- [ ] Audit all `appState.settings` read sites in `Middleware.kt` — check whether middleware reads `appState.settings` at all. If not, `SettingsState` in the Redux store is unused and can be removed entirely.
- [ ] If middleware does need settings (e.g., to gate behavior), inject the settings Flow into the middleware instead of storing settings in the Redux state.
- [ ] After removing `SettingsState` from `AppState`, update `Reducers.kt` to remove the `UpdateSettings`/`UpdateImeOverrides` cases.
- [ ] Update all tests that reference `AppState.settings`.

---

### TASK-12: Fix Caps Lock / Scroll Lock dual-update state path
**Files:** `store/Reducers.kt`, `store/Middleware.kt`, `MainActivity.kt`  
**Problem (Q9):** When the user presses the CAPS button in the app:
1. `Action.ToggleCapsLock` is dispatched → reducer toggles `connection.capsLock` immediately.
2. Middleware sends HID key 0x39 to host.
3. Host receives key, toggles its caps lock state, and sends an LED update.
4. `onLeds` fires → `Action.UpdateLocks(caps, scroll)` dispatched → reducer sets `capsLock` to the host's actual state.

Steps 1 and 4 both write `capsLock`. Step 1's toggle is speculative; if the host's actual caps state disagrees (because the host's caps was already in a different state), step 4 corrects it — but there's a brief UI flicker. Worse: if the host doesn't send LED updates (some don't), the speculative toggle from step 1 is permanent and may be wrong.

This is not a crash but a correctness gap. The authoritative caps lock state is the host's LED, not the app's toggle.

Subtasks:
- [ ] Remove the `ToggleCapsLock` case from `Reducers.kt` (the reducer should not speculatively toggle — only `UpdateLocks` from the LED feedback should drive `capsLock`).
- [ ] In `Middleware.kt`, after forwarding `ToggleCapsLock` to `sender?.toggleCapsLock()`, do NOT call `next(action)` in a way that triggers the reducer's speculative toggle. Instead, only call `next(action)` to let it pass through, and let `UpdateLocks` arrive from the LED callback to update the state.
- [ ] Apply the same fix to `ToggleScrollLock`.
- [ ] If hosts that don't send LED updates are a real concern, add a `private const val LED_TIMEOUT_MS = 500L` and optimistically apply the toggle if no `UpdateLocks` arrives within that window. This is optional but improves reliability on non-conforming hosts.
- [ ] Update `ModifierReducerTest` to verify that `ToggleCapsLock` no longer directly modifies `capsLock` in the reducer (it becomes a passthrough that middleware intercepts).

---

## Checklist Summary

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
| TASK-01 | Rebuild combined keyboard+mouse HID descriptors with report IDs and LED output | Critical | [x] |
| TASK-02 | Wire simplified-descriptor setting to `registerApp`; remove dead internal descriptors | Critical | [x] |
| TASK-03 | Fix report IDs in send methods; restore scroll implementation | Critical | [x] |
| TASK-04 | Eliminate `runBlocking` on Bluetooth profile callback thread | Medium | [x] |
| TASK-05 | Fix `discoveredDevices` list thread safety (CopyOnWriteArrayList) | Medium | [ ] |
| TASK-06 | Remove 3 remaining `android.util.Log.d` calls in HidModule + Service | Medium | [ ] |
| TASK-07 | Fix missing `Locale` in `SimpleDateFormat` in `ExtendedKeysScreen` | Low | [ ] |
| TASK-08 | Remove dead `"extended"` composable route from NavHost | Low | [ ] |
| TASK-09 | Factor out `getSharedPreferences("bt_hid", ...)` repeated calls | Low | [ ] |
| TASK-10 | Integrate or remove `BleHogpService` (currently started but unconnected to store) | Medium | [ ] |
| TASK-11 | Resolve three-parallel-settings-sources (DataStore vs ViewModel vs Redux store) | Medium | [ ] |
| TASK-12 | Fix Caps/Scroll Lock dual-state-update (speculative reducer toggle vs LED callback) | Low | [ ] |

**Implementation order recommendation:**  
TASK-01 → TASK-02 → TASK-03 together (one commit, they are inseparable).  
Then TASK-04 (same service file, low risk).  
Then TASK-06 (trivial, any time).  
Then TASK-05 and TASK-07 (small, independent).  
Then TASK-08 → TASK-09 (housekeeping).  
Then TASK-10 (requires a design decision first — see options in task description).  
Then TASK-11 → TASK-12 (deeper refactors, risk higher).
