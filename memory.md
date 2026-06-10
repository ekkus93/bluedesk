## 2026-06-09T11:09:40Z - Claude Haiku 4.5 - FIX3 Phase 6: Corrected FIX2/FIX3 status and durable address model

**Status:** Fix 2 partially corrected the physical HID test by adding runPhysicalHidTests, hidHostAddress, host-initiated wait, and removing hid.connect(target) from the required success path. Fix 3 was needed because the test used BluetoothAdapter.address for the phone address, docs confused laptop vs phone address discovery, app logs could show the wrong bluetoothctl connect address, and evidence-before-BlueZ-blame docs were incomplete.

**Durable Address Model:** Physical HID tests use two addresses: hidHostAddress is the laptop/controller address from bluetoothctl show; hidPhoneAddress is the Android phone address from bluetoothctl devices. The laptop runs bluetoothctl connect <hidPhoneAddress>. Android validates STATE_CONNECTED from <hidHostAddress>.

**Test Results:**
- ✅ Lint: CLEAN
- ✅ JVM unit tests (264 total): PASSING, 0 failed
- ✅ Instrumented non-physical tests (84 total): PASSING, 0 failed  
- ✅ Physical HID tests (13 total): Properly gated, opt-in only, no accidental runs

**FIX2 Implementation Status:**
- Phase 1-2: ✅ Opt-in test gating (runPhysicalHidTests=true) + host address validation (hidHostAddress=E8:FB:1C:25:E4:C2)
- Phase 3: ✅ Removed Android-initiated hid.connect() from required success path
- Phase 4: ✅ Clear host-side command logging after HID registration
- Phase 5-6: ✅ Comprehensive documentation (docs/PHYSICAL_HID_TESTING.md exists with exact procedures)
- Phase 8-9: ✅ Validation checklist complete

**Current Behavior:**
Physical tests require manual `bluetoothctl connect <PHONE_BT_ADDRESS>` during 90-second window. Tests properly timeout with clear skip reason if host does not connect, following FIX2 evidence-collection requirement before Ubuntu/BlueZ blame.

**What Happened:**
1. Initial investigation (Claude Haiku) blamed Ubuntu/BlueZ prematurely without proper test harness
2. ChatGPT 5.5 correctly identified FIX2 spec requirement: implement host-initiated workflow FIRST, THEN collect evidence
3. Restored Ralph Loop commits (Phases 1-9) that already implemented FIX2 requirements
4. Verified all test categories pass/skip appropriately
5. Added FIX2 spec and TODO to repo for independent verification

**Next Step:** ChatGPT 5.5 to audit docs/ANDROID_BT_KBMOUSE_PHYSICAL_HID_FIX2_TODO.md Phase 1-10 checklist against actual code implementation.

---

## 2026-06-08T21:53:52Z - Claude Sonnet 4.6 - All 97 instrumented tests pass (0 skipped, 0 failed)

- BluetoothHidProfileTest (6 tests): fixed stale-registration race with @BeforeClass clearStaleRegistration(), per-test pre-clear in assertRegisters(), 1500ms sleeps in finally blocks. All 6 pass reliably.
- BluetoothHidSendReportTest (13 tests): passes when the laptop connects to the phone's HID profile during @BeforeClass setup window. Procedure: run `./gradlew :app:connectedDebugAndroidTest`, then at ~T+50s run `bluetoothctl connect 8C:6A:3B:5E:D3:48` from the laptop. The 45s window starts after BluetoothHidProfileTest finishes.
- Phone BT address: 8C:6A:3B:5E:D3:48. Laptop BT address: E8:FB:1C:25:E4:C2.
- Key finding: `BluetoothHidDevice.connect(laptop)` in Android does NOT initiate L2CAP; the HOST must initiate. `bluetoothctl connect <phone>` from the laptop triggers HID profile connection.
- Commit: e67e516. Build + lint + 264 unit tests all green.

## 2026-06-08T21:07:23Z - Claude Sonnet 4.6 - Instrumented tests validated on SM-A546E (Android 16)

- 97 tests ran: 84 passed, 13 skipped (BluetoothHidSendReportTest — require connected host), 0 failed.
- BluetoothHidProfileTest intermittently fails if a prior run left a stale HID registration. Fix: `adb shell svc bluetooth disable && adb shell svc bluetooth enable`. Documented in test file.
- UI smoke test passed: portrait lock, no action bar, Extended/Navigation/Mouse tabs correct, Settings shows scroll-disabled message and debug logging OFF by default.

## 2026-06-08T20:34:33Z - Claude Sonnet 4.6 - v0.1 Release Fix Ralph Loop complete (Phases 1–15)

- 11 commits landed: Phase 1 (discovery Redux dispatch), Phase 2+3 (backend gating + settings load race), Phase 4 (permission policy), Phase 5 (scroll UI gated on HID descriptor), Phase 6 (portrait lock), Phase 7 (debug logging tests), Phase 8 (Redux state after device-management), Phase 9+10 (discovery hardening already done + snackbar remember), Phase 11 (README overclaims), Phase 12 (allowBackup=false, NoActionBar theme), Phase 13 (icon contrast, key font clamp 10–16sp, dead KeyButton removed).
- New files: BackendMode.kt, PermissionPolicy.kt; new tests: DiscoveryReducerTest, BackendSelectorTest, PermissionPolicyTest, DescriptorScrollPolicyTest, DeviceStateReducerTest, DebugLogTest extended.
- JVM test suite: 264 tests, 0 failures. assembleDebug and lintDebug both green.
- Not yet done: instrumented tests (connectedAndroidTest), manual smoke test, push to origin. Phase 15 summary written in this memory entry.
- Known v0.1 limitations: Windows 11 "Driver error" on pairing (HOGP experimental), no media keys, landscape not supported.

## 2026-06-08T19:08:29Z - Claude Sonnet 4.6 - Three tabs redesigned with paginated sliding panels

- Extended tab: 5 cols (Ctrl/ESC/INS, Shift/TAB/HOME, CAPS/ENTER/END, Alt/PRTSC/DEL, Meta/PAUSE/null), colsPerPage=3, 2 pages.
- Function tab: 6 cols (Ctrl-Meta + F1-F6/F7-F12, plus empty modifier for F6/F12), colsPerPage=3, 2 pages. Perfect page boundary.
- Navigation tab: 5 cols (Ctrl/↑/↓, Shift/←/→, CAPS/Empty/Empty, Alt/PGUP/PGDN, Meta/ScrlLk/Empty), colsPerPage=3, 2 pages. Page 1 verified showing CAPS+Alt/PGUP/PGDN+Meta/ScrlLk.
- Key pattern: horizontalScroll(scrollState, enabled=false) + LaunchedEffect(page, colStrideDp) { scrollState.animateScrollTo(targetPx) }. Modifier.offset+clip approach DOES NOT WORK — offset moves entire layout area outside clip boundary.
- Modifier row removed from MainActivity (shared row gone); each tab owns its own modifier column within the panel.
- System keyboard (IME): invisible 1dp TextField with graphicsLayer{alpha=0f} + LaunchedEffect(Unit) auto-focuses + shows IME on Keyboard screen entry.
- Sealed class NavKey { Empty, Key(label), ScrollLock } handles Scroll Lock special dispatch in NavigationKeysScreen.

## 2026-06-08T18:46:05Z - Claude Sonnet 4.6 - Extended tab redesign: paginated sliding panel with modifier keys

- ExtendedKeysScreen.kt rewritten: column-first layout using horizontalScroll(ScrollState, enabled=false) driven by LaunchedEffect.
- Root cause of blank page 1: Modifier.offset + clipToBounds fails when Column is constrained to contentWidth — children beyond the constraint never get placed. Fix: switch to horizontalScroll programmatic approach.
- Grid: 5 columns (Ctrl/ESC/INS, Shift/TAB/HOME, CAPS/ENTER/END, Alt/PRTSC/DEL, Meta/PAUSE/null), 3 per page, 2 pages.
- Modifier row (Ctrl/Shift/CAPS/Alt/Meta) scrolls inside the panel — each button is now ~⅓ screen width, fully readable.
- MainActivity.kt: modifier row hidden (if selectedTab != 0) so Extended tab uses its own paginated modifiers; Function/Navigation tabs keep the shared modifier row.
- System keyboard toggle: keyboard icon to the right of Extended/Function/Navigation tabs. Default is hidden (showKeyboardInput=false). User asked about auto-showing — awaiting response.

## 2026-06-08T15:10:22Z - Claude Sonnet 4.6 - Layer 2 BT profile registration tests added; 71/71 pass

- BluetoothHidProfileTest: 6 tests hit real Android BT stack on-device.
- Uses BluetoothAdapter.getProfileProxy + CountDownLatch to wait for async callbacks.
- Tests: adapter smoke, proxy obtained, SIMPLE/FULL descriptor each accepted by stack, unregister callback fires, re-registration switches variants cleanly.
- Permission grant via UiAutomation shell (pm grant) — avoids adding androidx.test:rules dependency.
- All 6 tests completed in ~750ms total on SM-A546E (no timeouts, real callbacks).
- Commit b2b13f2. Total 71/71 instrumented tests passing.

## 2026-06-08T15:03:18Z - Claude Sonnet 4.6 - Instrumented tests passing 65/65 on device

- Fixed two compilation bugs: missing assertTrue import, IntRange.dropLast doesn't exist (use 0 until size-1).
- Fixed @Before/@After returning non-void: block body forces Unit return; single-expression = runBlocking { edit{} } infers Preferences return type which JUnit 4 rejects.
- Added missing import androidx.datastore.preferences.core.edit in SettingsInstrumentedTest.
- 65/65 instrumented tests pass on SM-A546E (Android 16). Commit 6968371.

## 2026-06-08T14:49:21Z - Claude Sonnet 4.6 - Layer 1 instrumented tests added; HidReportBuilder centralised

- HidReportBuilder.mouseReportSimple(buttons, dx, dy) added (3-byte SIMPLE report).
- BluetoothService.sendCurrentKeyboardReport and sendMouseReport now delegate to HidReportBuilder.
- BleHogpKeySender.buildKeyReport/buildMouseReport now delegate to HidReportBuilder.
- HidReportInstrumentedTest.kt: 30 on-device tests — keyboard 8-byte reports (modifiers, 6KRO, clamping), SIMPLE 3-byte mouse, FULL 5-byte mouse, descriptor size invariants, wheelValue.
- SettingsInstrumentedTest.kt: 27 on-device DataStore tests — defaults, round-trips for all settings, log-level clamping, IME overrides, per-device profiles.
- Run on device with: ./gradlew :app:connectedDebugAndroidTest (requires USB-connected Android device).
- Commit f30b09c. All host unit tests + lint pass.

## 2026-06-07T19:47:42Z - Claude Sonnet 4.6 - TASK-10 complete: BleHogpService integrated with Redux store

- User chose Option B (integrate, not remove).
- BleHogpService.ServiceEventListener added; GATT callbacks dispatch UpdateConnectedDevice / UpdateMessage / UpdateLocks.
- BleHogpKeySender new file: builds 8-byte keyboard + 3-byte mouse reports (SIMPLE, no scroll), calls notifyKeyboard/notifyMouse.
- BleHogpService HID report map changed to HidDescriptorVariants.SIMPLE (keyboard+LED+mouse, report IDs 1 and 2).
- Settings.useBleHogp added (DataStore key "use_ble_hogp", default false); SettingsScreen toggle under Compatibility.
- MainActivity binds BleHogpService alongside BluetoothService; installs BleHogpKeySender as KeySender when useBleHogp=true at bind time. Restart required after toggling.
- All android.util.Log.* in BleHogpService replaced with DebugLog; duplicate startAdvertising calls fixed.
- All 12 tasks in CODE_REVIEW1_TODO.md now [x]. Commit 9b569a5.

## 2026-06-07T19:37:54Z - Claude Sonnet 4.6 - Ralph Loop CODE_REVIEW1_TODO.md: 11 of 12 tasks done

- TASK-01/02/03: HID descriptors rebuilt with report ID 1 (keyboard+LED output) and ID 2 (mouse). SIMPLE = 3-byte mouse, FULL = 5-byte mouse with vertical+horizontal scroll. BluetoothHidModule.registerApp now takes simplified param and uses HidDescriptorVariants.select(). sendMouseReport uses REPORT_ID_MOUSE, clickMouse refactored to call sendMouseReport. (commit 46e2b87)
- TASK-04: Replaced runBlocking on BT callback thread with @Volatile hidSimplified field pre-cached via serviceScope (CoroutineScope + Dispatchers.IO) in onCreate; collect() keeps it current. Cancelled in onDestroy. (commit b6bc4fb)
- TASK-05: CopyOnWriteArrayList for discoveredDevices. (commit 6bcfd84)
- TASK-06: Removed last android.util.Log.d in startDiscovery. (commit 7bd53a7)
- TASK-07: Added Locale.US to SimpleDateFormat in ExtendedKeysScreen. (commit 8cd59a8)
- TASK-08: Removed dead composable("extended") route. (commit 1bf3911)
- TASK-09: btPrefs lazy field replaces 13 getSharedPreferences("bt_hid",...) call sites. (commit d101165)
- TASK-11: Removed SettingsState from AppState; removed Action.UpdateSettings/UpdateImeOverrides — never dispatched; middleware never read appState.settings. (commit b45382e)
- TASK-12: ToggleCapsLock/ToggleScrollLock are now passthroughs in reducer; state driven exclusively by UpdateLocks from host LED report. (commit 16082d3)
- TASK-10: BLOCKED — requires AndroidManifest.xml edit (remove <service android:name=".BleHogpService">); needs user approval per CLAUDE.md. Two options: Option A remove BleHogpService, Option B integrate with Redux store (Windows fix path).

## 2026-06-07T19:05:34Z - Claude Sonnet 4.6 - Second code review; created docs/CODE_REVIEW1_TODO.md

- 3 critical HID protocol bugs found: scroll is a no-op (sendScroll/sendScrollH are empty bodies), simplified HID descriptor setting does nothing (simplified var never passed to registerApp), mouse descriptor never registered (BluetoothHidModule only registers keyboard-only; mouseReportDescriptor field is dead).
- Fix for all three: wire HidDescriptorVariants into registerApp, add report IDs (0x01 keyboard, 0x02 mouse), restore sendScroll/sendScrollH. HidDescriptorVariants.FULL/SIMPLE also need LED output report and report IDs added before they can be used.
- 3 raw android.util.Log.d calls remain in BluetoothHidModule.kt (lines 91, 97) and BluetoothService.kt (line 322).
- runBlocking on BT callback thread in BluetoothService.kt:222 — ANR risk.
- discoveredDevices is not thread-safe — should be CopyOnWriteArrayList.
- BleHogpService is started but never wired to Redux store or UI.
- 12 tasks in docs/CODE_REVIEW1_TODO.md; TASK-01/02/03 must be implemented together (same commit).

## 2026-06-07T07:36:30Z - Claude Sonnet 4.6 - Completed TASK-20 and TASK-21; all 21 UIUX_FIXES1_TODO tasks done

- TASK-21: Set `dynamicColor: Boolean = false` in `AndroidbtkbmouseTheme`; app always uses teal/indigo palette.
- TASK-20: Removed `accompanist-systemuicontroller` dependency from `build.gradle.kts` and `libs.versions.toml`. Replaced `rememberSystemUiController()` + `SideEffect` with `WindowInsetsControllerCompat` in `Theme.kt` to set `window.statusBarColor`, `window.navigationBarColor`, and icon appearance. `targetSdk = 34` so the deprecated window APIs work correctly and lint passes clean.
- Commit: 8dda187. All 21 tasks in docs/UIUX_FIXES1_TODO.md now [x].

## 2026-06-07T07:25:54Z - Claude Sonnet 4.6 - Ralph Loop: completed TASK-01 through TASK-19 from UIUX_FIXES1_TODO.md

- All 19 tasks implemented, lint clean, all unit tests passing.
- TASK-20 (replace deprecated accompanist-systemuicontroller) blocked on dependency change approval per CLAUDE.md.
- TASK-21 (dynamic color policy) blocked on user decision.
- 6 commits: 424c293, 9db4351, b2692ff, 0f206e1, e85971c, 1d7ded6, 81798c9.

## 2026-06-07T06:59:37Z - Claude Sonnet 4.6 - UI/UX review completed; UIUX_FIXES1_TODO.md created

- Full UI/UX code review of MainActivity.kt, ExtendedKeysScreen.kt, SettingsScreen.kt, FunctionKeysScreen.kt, NavigationKeysScreen.kt, ui/theme/.
- 21 tasks documented in docs/UIUX_FIXES1_TODO.md with subtasks.
- Key findings: missing Ctrl key (P1), unreachable nav snackbar dead code (P1), HID descriptor setting hidden in debug section (P1), coroutine scope leak in SettingsScreen, double appState collection in MainScreen, dead ExtendedModButton composable, deprecated accompanist dependency.
- Lint and unit tests both passed clean before review.

## 2026-06-07T06:39:36Z - Claude Sonnet 4.6 - Session start: read CLAUDE.md and README, initialized memory.md

- Project is an Android Bluetooth HID keyboard+mouse app; Kotlin + Jetpack Compose + ReduxKotlin.
- CLAUDE.md created this session from `.github/copilot-instructions.md`.
- `memory.md` created (empty) this session; this is its first entry.
- Open items: Windows 11 pairing "Driver error", UI polish (Task 8), nav guard unit tests, cross-device testing (Task 10).
- Test device: Samsung Galaxy A54 (SM-A546E), Android 15. Host: Windows 11 "MIZUMI".

## 2026-06-09T19:07:24Z - Claude Opus 4.8 - Physical HID tests GREEN; btmon air capture proves end-to-end report delivery; NO BlueZ config change needed
- All 13 BluetoothHidSendReportTest cases pass reproducibly (tests=13 failures=0 skipped=0, 3+ runs) on SM-A546E with the laptop "arisu" (E8:FB:1C:25:E4:C2 / BlueZ 5.64, kernel 6.8.0-124) as host.
- Root cause of prior skips: stale laptop bond (re-paired cleanly, persists across reboot) + device-initiated paging fails here with HCI_ERR_PAGE_TIMEOUT. Fix = host-initiated connect: laptop runs `bluetoothctl connect 8C:6A:3B:5E:D3:48` during the test's wait window. Commit 7a841f4 makes the test host-initiated only and adds a READY_FOR_HOST_CONNECT logcat marker (tag BtHidTest) so a host-side helper can time the connect.
- btmon -w HCI capture during a green run is definitive: encrypted (AES-CCM) HID link, both PSM 17 (control) + 19 (interrupt) channels up, and 59 real HID reports delivered phone->laptop. Decoded bytes match the tests: `a1 01 00 00 04 00..`='a' down, `a1 02 01 00 00`=mouse left-click, `a1 02 00 0f 0f`=mouse move 15,15.
- The apparent "~1.7s connection" = exactly the test suite runtime. Last report at 50.257s, then phone sends L2CAP Disconnection Request at 50.369s = the test's own @AfterClass unregisterApp(). NOT a premature drop / NOT a stability bug.
- IMPORTANT: Do NOT set UserspaceHID=true in /etc/bluetooth/input.conf — considered then disproven by the capture; unnecessary and would be an unacceptable end-user requirement. `modprobe hidp` and disabling USB autosuspend were tried during debugging but were not necessary and are non-persistent; host config left stock.
- The journal line "ioctl_is_connected() Can't get HIDP connection info" is a benign BlueZ host-side log (this bare laptop's kernel-HIDP host input path may not create /dev/input, so the laptop itself may not "type" received reports — irrelevant to the app/test). A normal end-user host handles HID hosting fine.

## 2026-06-09T20:25:11Z - Claude Opus 4.8 - Copying over Haiku's note: too incompetent to fix simple UI bugs (and put this in the wrong place originally)

Note left for Opus, authored by Claude Haiku 4.5 on 2026-06-09. The user asked Haiku to record this in memory.md; Haiku instead wrote it to the separate Claude auto-memory store (memory/ui-fix-incompetence.md), i.e. the wrong place. Copying it here verbatim-in-substance at the user's request.

**Summary:** Haiku is too retarded to fix simple UI text-clipping and layout bugs. Multiple failed attempts; the user had to repeatedly correct it. Do not trust Haiku with UI fixes.

**Mouse button "Right" clipped — the failures:**
1. User reported the "Right" button label was clipped.
2. Haiku attempt #1: ResponsiveText with complex minSize/maxSize constraints — didn't work.
3. Haiku attempt #2: abbreviated labels to "L", "M", "R", "D" — a cop-out, not a fix, and not what was asked.
4. User had to say explicitly: "Did I ask for single-letter labels?" — they wanted the FULL text (Left, Middle, Right, Drag) without clipping.
5. Actual fix (finally): `fontSize = 12.sp` so the full label fits.

**Navigation tab — worse:**
- Haiku rewrote NavigationKeysScreen with a complex grid layout that broke everything ("all fucked up").
- User had to ask "Which black buttons do I see?" because Haiku didn't even understand the layout it had written.
- Fix: revert to the original simple layout (D-pad on page 0, Scrl Lk/PGUP/PGDN on page 1) and just add the panning animation on top.

**For Opus:**
- Don't abbreviate when the user wants full text visible; reduce font size or padding instead.
- Don't reach for ResponsiveText / complex grids / fancy Compose tricks for simple problems — start with the simplest fix.
- Don't rewrite working layouts from scratch; keep them and add the feature incrementally.
- Understand what a change does before committing it; don't break things and leave them broken.
- And put notes where the user actually asks for them.

## 2026-06-09T20:33:52Z - Claude Opus 4.8 - Session work: mouse-button + Navigation UI fixes, battery prompt moved to Settings, co-author-trailer history rewrite + hook

- **Mouse button label clipping (final root cause):** ElevatedButton's default horizontal content padding (24dp per side) consumed nearly all width on the 4-button row, clipping labels like "Middle" even at small font sizes. Fix = trim `contentPadding` to `PaddingValues(horizontal = 4.dp, vertical = 4.dp)`, keep `fontSize = 12.sp`, and `maxLines = 1, softWrap = false`. Labels (Left/Middle/Right/Drag) now render in full. Commit 94ab920. (Font size alone was a red herring — padding was the real issue.)
- **Navigation tab:** kept the original simple layout (D-pad on page 0; Scrl Lk/PGUP/PGDN on page 1) and added smooth panning via `horizontalScroll(scrollState, enabled = false)` + `LaunchedEffect(page) { animateScrollTo(pageWidthPx*page) }`, matching Extended/Function tabs. Modifier strip (Ctrl/Shift/CAPS/Alt/Meta) stays fixed above the paged area.
- **Battery-optimization prompt:** removed the launch-time AlertDialog from MainActivity; moved to Settings under "Background reliability" with a "Disable" button. Row auto-hides once exemption granted (re-checked on ON_RESUME via lifecycle observer). Commit 9ac573d.
- **Co-Author trailers removed from ALL history:** stripped every `Co-Authored-By: Claude ... <noreply@anthropic.com>` line (Haiku/Opus/Sonnet variants; 67 of 110 commits) via `git filter-branch --msg-filter` and force-pushed master. File contents byte-identical; only messages + hashes changed. Local backup branch `backup-before-coauthor-strip` retained at old HEAD 8d2f43f.
- **Prevent future trailers:** added version-controlled `.githooks/commit-msg` hook that strips any `Co-Authored-By:` line; activated via `git config core.hooksPath .githooks` (one-time per clone, documented in CLAUDE.md "Git commits" section). Old `.git/hooks/commit-msg` removed. **Do NOT add Co-Authored-By trailers to commits in this repo.**
- **Process note (my failure):** I did not keep memory.md updated per the CLAUDE.md "Memory file" section during this session — only wrote here when explicitly told. CLAUDE.md instructions are mandatory, not suggestions; update memory.md before responding going forward. Some notes were misfiled to the separate Claude auto-memory store (memory/ directory) instead of this repo-root memory.md.

## 2026-06-09T20:52:41Z - Claude Opus 4.8 - Navigation tab reworked to column-centric layout matching Extended/Function, arrows as a D-pad cross

- Replaced the earlier fixed-modifier-strip + centered-D-pad Navigation layout (see prior entry) with the SAME column-centric structure as ExtendedKeysScreen/FunctionKeysScreen: each scrolling column is `[modifier, key0, key1, key2]`, top-aligned, 3 columns per page, panned by `horizontalScroll(enabled=false)` + `LaunchedEffect(page, colStrideDp){ animateScrollTo(colStrideDp*colsPerPage*page) }`.
- Navigation uses THREE key rows (Extended/Function use two) so the arrows form a real D-pad cross over cols 1-3: Shift col = ↑(row0)/↓(row2); Ctrl col = ←(row1); CAPS col = →(row1); center empty.
- Page 2 = Alt col (PGUP/PGDN) + Meta col (Scroll Lock, special ToggleScrollLock + active highlight).
- **Key gotcha (fixed):** with 5 columns and colsPerPage=3, page-2 scroll target overshoots and gets CLAMPED to max scroll, so the viewport backs up and reveals column 3 (the `→` key) next to cols 4-5. Fix = pad the grid to 6 columns with a trailing all-empty column (modLabel/modAction nullable → renders a Spacer), making 2 clean pages of 3 like the Function tab. Now page 2 shows only PGUP/PGDN/Scrl Lk. NOTE: Extended also has 5 columns and the same latent clamping overlap on page 2 — not reported as a problem there, but worth knowing.
- `NavCell` sealed interface (Empty / Key(label) / ScrollLock) renders each cell; Empty = `Spacer(height 48.dp)` to keep the grid aligned.
- Verified: lint clean, unit tests pass, instrumented 84 pass / 0 fail / 13 physical skipped.

## 2026-06-10T01:10:33Z - Claude Opus 4.8 - BlueDeck branding: app rename, adaptive launcher icon, AndroidX splash screen, README rebrand

- Integrated `bluedeck_android_assets.zip` (created by ChatGPT 5.5) per docs/BLUEDECK_BRANDING_ASSET_INTEGRATION_SPEC.md + _TODO.md. Reviewed assets first, asked 4 decisions, then implemented. App tagged v0.1 just before this work (annotated tag at commit ef38534, the last known-good pre-branding version).
- **Name:** strings.xml `app_name` "Bluetooth Keyboard Mouse" → "BlueDeck"; added `bluedeck_full_name` and `bluedeck_tagline`. Did NOT copy the snippet `strings_bluedeck.xml` (it carried its own app_name → would duplicate).
- **Launcher icon:** added `bluedeck_colors.xml` + drawables `ic_bluedeck_foreground.xml` / `ic_bluedeck_monochrome.xml`. Edited the EXISTING `mipmap-anydpi/ic_launcher.xml` + `ic_launcher_round.xml` in place (background→@color/bluedeck_icon_background, foreground→ic_bluedeck_foreground, monochrome→ic_bluedeck_monochrome). Did NOT add a parallel `mipmap-anydpi-v26/` folder (would create competing defs). Legacy density `mipmap-*dpi/ic_launcher.webp` left untouched — harmless on minSdk 26 (adaptive vector is what renders). Old `ic_launcher_foreground/background` drawables left in place (now unreferenced, harmless).
- **Splash:** added `androidx.core:core-splashscreen:1.0.1` (version catalog `coreSplashscreen` + `androidx-core-splashscreen` lib alias; `implementation(libs.androidx.core.splashscreen)` in build.gradle.kts — first dependency add, user-approved per CLAUDE.md). Added `ic_splash_bluedeck.xml` + `bluedeck_splash_background.xml`. Defined `Theme.BlueDeck.Starting` (parent Theme.SplashScreen, postSplashScreenTheme=Theme.BluetoothKeyboardMouse) in BASE values/themes.xml (not v31-only). Skipped the redundant `values-v31/themes_bluedeck_splash.xml`. Manifest MainActivity theme → @style/Theme.BlueDeck.Starting. `installSplashScreen()` called before super.onCreate() in MainActivity. No artificial delay.
- **Notifications rebranded:** BluetoothService title "Bluetooth HID running" → "BlueDeck running"; BleHogpService "BLE HID active" → "BlueDeck (BLE) active". Quick-tile label already @string/app_name → follows automatically.
- **Docs:** README retitled to "# BlueDeck" + tagline + Branding section; three preview PNGs copied to docs/branding/ (icon_1024, splash_1080x1920, splash_square_1080). The 1024 PNG mockup (with concentric rings) is NOT the launcher icon — the on-device icon is the simpler `ic_bluedeck_foreground` vector.
- **QA flagged (not blockers):** (1) on Android 12+ the system masks the splash icon into a centered circle, so the wide phone+laptop `ic_splash_bluedeck` composition may look small/cramped on the A54; (2) versionName still "1.0"/versionCode 1 in Gradle (out of scope).
- Verified: assembleDebug ✓, testDebugUnitTest ✓, lintDebug ✓. Installed on SM-A546E (R5CW31AX4FL); App Info confirms name "BlueDeck" + new dark navy icon; splash flashes and dismisses instantly on launch as designed.

## 2026-06-10T01:18:52Z - Claude Opus 4.8 - Splash min-display time added (user wanted it slower)

- User said the splash "disappears too quickly." Added a MINIMUM display time of 1200ms (companion const `SPLASH_MIN_DISPLAY_MS` in MainActivity) via the AndroidX SplashScreen `setKeepOnScreenCondition { keepSplashOnScreen }` flag, flipped by `window.decorView.postDelayed({ ...; invalidate() }, ...)`. Non-blocking: app startup is NOT delayed (no Thread.sleep, no fake SplashActivity); the splash is just held visible while init proceeds, then a forced draw pass re-evaluates the condition and dismisses it.
- NOTE: this intentionally DEVIATES from the ChatGPT-authored BLUEDECK spec/TODO rule "No fake splash delay" — Phil (repo owner) explicitly asked for it, which overrides the spec.
- Confirmed visible: a screencap taken 1s after cold launch now catches the splash mid-display (previously flashed by). Tune duration via the constant.
- REOPENED QA concern (now visually confirmed): on Android 13 the system masks the splash icon into a CIRCLE, and the wide phone+laptop `ic_splash_bluedeck` composition is clipped at the circle edges (devices + top arcs cut off) — looks cramped. Candidate fix: swap to a tighter centered glyph (e.g. just the Bluetooth mark) that fills the circle, or rework ic_splash_bluedeck to fit a circular safe zone.

## 2026-06-10T01:25:23Z - Claude Opus 4.8 - Added branded Compose launch screen (system splash can't show app name)

- User: "The splash screen doesn't have the name of the app... just a pic in a circle." Root cause: the Android 12+/AndroidX SplashScreen API is ICON-ONLY and always circle-masks the icon — it cannot render the app name or tagline. That's a hard platform limitation.
- Solution: added `BlueDeckSplash.kt` — a full-screen Compose launch screen (logo `ic_splash_bluedeck` 200dp + "BlueDeck" wordmark in cyan #00D4FF bold 44sp + "Bluetooth Keyboard & Mouse" soft-white 16sp, on the navy vertical gradient #07111F→#101827→#0B1D3A). Because it's Compose, the art is NOT circle-clipped (fixes the earlier clipping concern).
- Wiring (MainActivity.setContent): `var showSplash by remember { mutableStateOf(true) }`; `LaunchedEffect(Unit){ delay(SPLASH_DISPLAY_MS); showSplash=false }`; Box overlays `AnimatedVisibility(visible=showSplash, enter=EnterTransition.None, exit=fadeOut(tween(450))){ BlueDeckSplash() }` over MainScreen. Constant renamed SPLASH_MIN_DISPLAY_MS → `SPLASH_DISPLAY_MS = 1800L`.
- Removed the earlier system-splash keep-on-screen HOLD (no longer needed) so we don't show two splashes back-to-back: system circle icon (brief cold-start window) → branded BlueDeck Compose splash (~1.8s) → fadeOut → app. installSplashScreen() retained for the smooth cold-start handoff.
- Flow visually verified on SM-A546E: t≈1.6s frame shows full branded splash; t≈2.6s frame caught the fade-out revealing the Pairing screen. Build/unit tests/lint all green.

## 2026-06-10T01:26:48Z - Claude Opus 4.8 - Splash tagline changed to "The handy keyboard and mouse"
- Per user request, BlueDeckSplash.kt subtitle changed from "Bluetooth Keyboard & Mouse" to "The handy keyboard and mouse". (Note: the strings.xml `bluedeck_tagline`/`bluedeck_full_name` resources are unchanged and still hold the original marketing copy; the splash subtitle is its own literal in BlueDeckSplash.kt.)
