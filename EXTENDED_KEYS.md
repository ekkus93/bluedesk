# Extended keys inventory — android_bt_kbmouse

Last updated: 2025-11-10

Purpose
- Inventory the typical "extended" keys found on a standard US physical keyboard which are not normally produced by the Android soft keyboard (IME).
- For each key: indicate whether the app supports sending the corresponding HID usage, how it is implemented (UI mapping, IME-to-HID mapping, or BluetoothKeySender toggle/helper), and point to the source files that implement the mapping or sending.

How to read this doc
- "Supported" means the codebase currently has an explicit mapping or send path for that HID usage.
- "Exposed in UI" means there is a button or screen (currently `ExtendedKeysScreen`) that allows the user to send the key.
- Implementation references point to the file and the relevant function (no line numbers are guaranteed stable; open the file and search for the named symbol).

Key sources to inspect in the repo
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/HidImeMapper.kt` — function `charToHid(ch: Char): Pair<Byte, Int>?` maps IME characters to HID usage bytes and shift modifiers (printable characters, punctuation, space/tab/enter).
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/ExtendedKeysScreen.kt` — UI screen that exposes a set of explicit extended keys and maps button labels to HID bytes (function `map(label: String): Byte?`).
 - `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/BluetoothKeySender.kt` — `BluetoothKeySender` forwards key/mouse/toggle calls to `IBluetoothService`; contains toggle helpers `toggleCapsLock()` and `toggleScrollLock()` that call `svc.sendKeyPress(...)` with specific HID codes. (Note: `toggleNumLock()` was removed from the codebase per project decision.)
- `app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/Middleware.kt` and `store/Actions.kt` — actions such as `Action.SendKey` and toggles are dispatched by UI and handled by middleware which uses the `KeySender` implementation.

Inventory (grouped) — supported keys and status

1) Function & basic control keys
- Escape (Esc)
  - HID usage: 0x29
  - Supported: Yes
  - How: `ExtendedKeysScreen.map` maps "ESC" -> 0x29; button present in `ExtendedKeysScreen`.
  - Files: `ExtendedKeysScreen.kt` (map function)
  - Exposed in UI: Yes

- Function keys F1..F12
  - HID usages: F1..F12 mapped by `ExtendedKeysScreen` (computed by label -> numeric offset in `map`)
  - Supported: Yes
  - How: `ExtendedKeysScreen.map` handles labels starting with "F" and computes usage `(0x39 + n)` where `n` is F-number.
  - Files: `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes

- Print Screen / SysRq
 - Print Screen / SysRq
  - HID usage: 0x46
  - Supported: Yes
  - How: `ExtendedKeysScreen` maps label `PRTSC` -> 0x46 and dispatches `Action.SendKey` to the store which forwards to the sender/service.
  - Files: `ExtendedKeyMappings.kt` / `ExtendedKeysScreen.kt` (mapping uses `labelToHid` helper)
  - Exposed in UI: Yes (button added)

- Scroll Lock
  - HID usage used by app: 0x47 (sent by `BluetoothKeySender.toggleScrollLock()`)
  - Supported: Yes (sender-level)
  - How: `BluetoothKeySender.toggleScrollLock()` calls `svc.sendKeyPress(0x47.toByte(), 0)`; `Middleware` exposes `Action.ToggleScrollLock` to call sender.
  - Files: `store/BluetoothKeySender.kt`, `store/Middleware.kt`, `store/Actions.kt`
  - Exposed in UI: No (no button in `ExtendedKeysScreen` for it currently)

- Pause / Break
  - HID usage: 0x48
  - Supported: Yes
  - How: `ExtendedKeysScreen` maps label `PAUSE` -> 0x48; dispatch path same as other extended keys.
  - Files: `ExtendedKeyMappings.kt` / `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes (button added)

2) Navigation / editing cluster
- Insert
 - Insert
  - HID usage: 0x49
  - Supported: Yes
  - How: `ExtendedKeysScreen` maps label `INS` -> 0x49 and dispatches `Action.SendKey`.
  - Files: `ExtendedKeyMappings.kt` / `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes (button added)

- Home / End
  - HID usages: `HOME` -> 0x4A, `END` -> 0x4D (as implemented in `ExtendedKeysScreen.map`)
  - Supported: Yes
  - Files: `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes

- Page Up / Page Down
  - HID usages: `PGUP` -> 0x4B, `PGDN` -> 0x4E
  - Supported: Yes
  - Files: `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes

- Delete (Forward Delete)
  - HID usage: `DEL` -> 0x4C
  - Supported: Yes
  - Files: `ExtendedKeysScreen.kt`
  - Exposed in UI: Yes

- Backspace
  - Supported: Indirectly — handled by IME / Android text input (not as an explicit Extended key). `HidImeMapper` focuses on printable characters; platform IME/backspace behavior remains the normal path.
  - Files: `HidImeMapper.kt` for char mappings; backspace is not part of `charToHid`.
  - Exposed in UI: No

3) Cursor (arrow) keys
- Left / Right / Up / Down arrows
  - HID usages: ExtendedKeysScreen uses Unicode arrow symbols mapped to HID bytes: left (0x50), right (0x4F), up (0x52), down (0x51)
  - Supported: Yes
  - Files: `ExtendedKeysScreen.kt` (map entries for the arrow symbols)
  - Exposed in UI: Yes

4) Lock keys & toggles
- Caps Lock
  - HID usage: 0x39
  - Supported: Yes
  - How: `ExtendedKeysScreen` has a "CAPS" button mapped to 0x39 and `BluetoothKeySender.toggleCapsLock()` sends 0x39.
  - Files: `ExtendedKeysScreen.kt`, `store/BluetoothKeySender.kt`
  - Exposed in UI: Yes (CAPS button)

- Num Lock
 - Num Lock
  - HID usage (typical): 0x53
  - Supported: No — `NumLock` support (toggle helper and action) was intentionally removed from the codebase per project decision. If needed again, it can be reintroduced at sender/middleware level.
  - Files: Previously referenced `store/BluetoothKeySender.kt` and middleware/actions; those references have been removed.
  - Exposed in UI: No

- Scroll Lock
  - (See above) Supported at sender level, not UI.

5) Numpad cluster (numpad digits, keypad Enter/decimal, keypad arithmetic)
- Distinct Numpad HID usages (e.g., Keypad 0..9, Keypad Enter, Keypad Decimal)
  - Supported: No — there is no separate numpad keypad UI or distinct HID usages mapped in the repo. `HidImeMapper` maps the top-row digits as printable characters but does not provide keypad-specific HID usage values.
  - Files: `HidImeMapper.kt` (maps characters like '1'..'0' to HID usages for typing, not explicit keypad usages)
  - Exposed in UI: No

6) Windows / Meta / Context / Application / Menu keys
- Left/Right GUI (Windows / Command), Menu/Application key
  - Supported: No
  - Files: N/A
  - Exposed in UI: No

7) Media / Consumer control keys (volume, play/pause, next/prev, mute, etc.)
- Supported: No — consumer controls use a separate HID usage page and are not present in the keyboard HID mapping currently in the repo.
- Files: N/A
- Exposed in UI: No

8) Miscellaneous keys
- SysReq / Print Screen: see Function & basic control keys above (PRTSC is supported and exposed in UI).
- Function Lock (Fn) / OEM-specific keys: Hardware-level — not supported
- Application-specific launch keys: No

9) Regular printable characters / punctuation / whitespace
- Letters, digits, punctuation, space, tab, newline
  - Supported: Yes — `HidImeMapper.charToHid` maps letters a–z/A–Z (with shift), digits '0'..'9', punctuation and shifted punctuation (e.g., '!' -> 0x1E + shift), space (0x2C), tab (0x2B), and newline/enter (0x28).
  - Files: `HidImeMapper.kt`
  - Exposed in UI: Not via `ExtendedKeysScreen`; these are sent via IME input flow.

Notes on implementation locations
- `HidImeMapper.kt` (function `charToHid`) is the canonical mapping for characters typed through the IME -> it returns a Pair<HID byte, shift-modifiers-int> or null for unmapped characters.
- `ExtendedKeysScreen.kt` contains the explicit dedicated keys UI. The `map(label: String)` function maps labels ("ESC", "HOME", "F1" etc.) to HID bytes and buttons dispatch `Action.SendKey(code)` to the store.
 - `store/BluetoothKeySender.kt` contains convenience methods for toggles (`toggleCapsLock()`, `toggleScrollLock()`) which call `svc.sendKeyPress(hidByte, 0)` on the platform service. (NumLock toggle was removed.)
 - `store/Middleware.kt` dispatch hooks and `store/Actions.kt` declare actions like `SendKey` and `ToggleScrollLock`. (References to `ToggleNumLock` were removed.)

Recommendations / next steps
1. If you want complete physical-keyboard parity, prioritize these additions:
   - Add Insert and Print Screen mappings to `ExtendedKeysScreen.map` or add a separate "system keys" section.
   - Add a NumPad view (grid) that maps keypad HID usages (keypad 0..9, keypad Enter, keypad Decimal, keypad / * - +) if you need a full numpad.
  - Add UI button for `ToggleScrollLock` (sender/middleware helper exists). `NumLock` was intentionally removed; reintroduce only if desired.
   - If you want consumer/media keys, implement a small consumer-control sender using the consumer usage page and add `BluetoothKeySender` helpers that call `IBluetoothService` (and extend `IBluetoothService`/BluetoothService implementation to send consumer reports if needed).

2. Implementation checklist for adding a key to the UI
   - Add label -> HID mapping in `ExtendedKeysScreen.map` (or in a shared mapping helper function).
   - Add a button to `ExtendedKeysScreen` rows or create a new row/screen for the key(s).
   - If the key is a toggle, dispatch `Action.ToggleXxx` (middleware already calls sender for toggles); otherwise dispatch `Action.SendKey(code)`.
   - Add unit tests: a small test asserting `map(label)` returns the correct byte and a middleware/sender test that verifies `svc.sendKeyPress` is invoked with expected code for toggles or `Action.SendKey` flows.

3. I can implement any of the above improvements. Pick one and I will:
  - Add UI button for `Scroll Lock` (quick, low-risk) and unit tests for its dispatch. Reintroducing `Num Lock` is possible but was removed intentionally and requires re-adding sender/middleware hooks.
   - Add `Insert`, `PrintScreen`, and `Pause/Break` mappings to `ExtendedKeysScreen` and tests.
   - Add a Numpad screen with keypad HID codes and tests (larger change).

Files changed/created by this doc
- New file: `/EXTENDED_KEYS.md` (this file). It references the existing implementation files rather than changing code.

Status update
- Created `EXTENDED_KEYS.md` in the repo root describing the inventory and recommendations.

What next?
- Tell me which of the recommended implementations you want (small -> add NumLock/ScrollLock UI buttons; medium -> add Insert/PrintScreen/Pause; larger -> add Numpad screen or consumer-control support). I will then implement that change, add tests, and run the unit tests.

If you'd prefer, I can also open a PR-style patch with the small changes (NumLock/ScrollLock UI) now.
