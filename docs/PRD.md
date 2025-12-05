## Product Requirements Document — Android Bluetooth Keyboard & Mouse

### Document Control
| Version | Date       | Author        | Notes                             |
|---------|------------|---------------|-----------------------------------|
| 0.1     | 2025-12-05 | GitHub Copilot| Initial draft for waterfall plan. |

---

### 1. Executive Summary
Android Bluetooth Keyboard & Mouse (ABKM) is a premium Android app that turns phones and tablets into full-featured Bluetooth HID peripherals. Users can pair once and then type, navigate, and control media on nearby computers or tablets. The product must feel appliance-grade: predictable setup, reliable reconnection, low-latency input, and a polished Compose UI optimized for landscape use. This PRD formalizes scope, requirements, milestones, and success criteria so the team can shift from iterative “agile” deliveries to a staged, waterfall execution model.

### 2. Product Goals & Success Metrics
- **Primary Goal:** Provide a trustworthy “carry-anywhere” Bluetooth keyboard/touchpad replacement for travelers, IT staff, and accessibility users.
- **Success Metrics:**
	- 95% of connection attempts succeed on first try (measured via in-app telemetry/log review) across Windows, macOS, and Linux.
	- <75 ms median end-to-end keypress latency over Bluetooth within 3 m range during lab tests.
	- <1% crash-free sessions over seven days (Play Console or internal QA).
	- ≥4.5/5 user satisfaction rating in structured usability study covering setup, typing, gestures, and settings.
	- Battery impact: <4% drain per hour on a 5,000 mAh device during continuous use (measured via Android battery stats).

### 3. Target Personas & Use Cases
- **Traveler/Presenter (Alex):** Needs portable keyboard/mouse to control laptops during meetings or media playback in hotels. Values fast reconnection and multi-host compatibility.
- **IT/Support Engineer (Riley):** Uses app to control headless devices or kiosks during maintenance. Needs reliable pairing and diagnostics/logging.
- **Accessibility/Ergonomics User (Morgan):** Prefers large tablet surface for gestures and programmable settings. Needs high-contrast UI, customizable sensitivity, and scroll inversion.

Key scenarios:
1. Pairing a new host from a tablet, verifying locks and extended keys work, and switching between keyboard and mouse modes during a presentation.
2. Reconnecting automatically to the last-used host after reopening the app, with a foreground notification to indicate readiness.
3. Using the system IME TextField to inject characters not on the on-screen layout, while still dispatching HID media keys via separate tabs.
4. Logging HID events in local preview mode on an emulator or when Bluetooth is unavailable.

### 4. Scope
**In Scope:**
- HID keyboard (multi-key rollover, modifiers, extended keys, media keys).
- HID mouse (pointer movement, 2-finger vertical/horizontal scroll, tap gestures for clicks, configurable sensitivity/invert, middle-click toggle).
- Bluetooth discovery, pairing, connection management, and auto-reconnect via `BluetoothHidDevice` foreground service.
- Settings persisted through DataStore: sensitivity, scroll speed/invert, click sound, middle-click enable, horizontal scroll behaviors, IME allow/deny list.
- System IME integration with TextField, heuristics for non-Latin detection, and ability to send `[DEL]`/modifier previews through Redux middleware.
- ReduxKotlin single-store architecture with middleware for HID side effects and preview logging.
- Diagnostics: in-app log viewer/export, quick settings tile, local preview mode, and permission UX.
- Landscape-only Compose UI with top navigation bar that remains accessible when the system IME is visible.

**Out of Scope:**
- BLE HOGP implementation beyond current HID classic profile (future consideration).
- Multi-device simultaneous connections (only one host at a time).
- Cloud sync, analytics dashboards, or remote firmware updates.
- Support for Amazon Fire OS devices (not supported per platform limitations).

### 5. Functional Requirements
#### 5.1 Pairing & Connectivity
- Display discoverable devices with live refresh; “Scan” restarts discovery and shows progress state.
- Tap device to initiate pairing; show status (“Pairing”, “Connecting”, “Connected to <name>”).
- Provide “Disconnect” button once connected; disable keyboard/mouse tabs until connection is established.
- Foreground service must start within 5 seconds of launch, post a persistent notification, and auto-reconnect to the last bonded device on service resume or Bluetooth power cycle.
- When connection drops, navigate the UI back to Pairing and show a brief “Disconnected” snackbar.

#### 5.2 Keyboard Mode
- Layout: US QWERTY base + modifiers + navigation cluster + F1–F12 + media controls accessible via tabs.
- Support six-key rollover and chords (e.g., Ctrl+Alt+Del).
- Redux actions ensure latched modifiers preview in the console and release automatically after next non-mod key, mirroring physical keyboard behavior.
- CapsLock and ScrollLock states show optimistic UI indicators and remain synchronized with host reports; NumLock intentionally omitted per requirements.
- Provide Extended Keys pages (Function, Navigation/Editing, Media & Consumer, etc.) accessible via top navigation; each key dispatches HID code and logs preview text even offline.
- System IME TextField converts committed characters to HID codes; non-printable keys like `[DEL]` are mapped through native key detection. Allow per-IME allow/deny persistence.

#### 5.3 Mouse Mode
- Full-screen touchpad optimized for landscape orientation with pointer acceleration and configurable sensitivity.
- Gestures:
	- Single-finger move (continuous pointer reports, smoothing to reduce jitter).
	- Single-finger tap → left click.
	- Two-finger tap → right click.
	- Two-finger vertical scroll (wheel) with invert toggle.
	- Two-finger horizontal scroll (AC Pan) with enable/invert options.
	- Three-finger tap → middle click (toggle in settings).
- Provide on-screen affordances (subtle hints) for first-run users describing gesture mapping; hints are dismissible.

#### 5.4 Settings & Data Persistence
- DataStore-backed preferences for sensitivity, scroll speeds, invert toggles, click sound, logging level, IME approvals, and “Use system keyboard” flag.
- Settings screen accessible via navigation bar; all options documented with tooltips or helper text.
- Allow export of log buffer for debugging; share intent includes plaintext file with timestamp metadata.

#### 5.5 Diagnostics & Preview
- Local preview toggle allows running without Bluetooth hardware; displays textual log of HID events, including codes and modifiers.
- Logging subsystem with adjustable levels (Info/Debug/Verbose) and bounded buffer to prevent memory pressure.
- Provide quick actions: Quick Settings tile to toggle service on/off, and optional debugging shortcuts (e.g., “Copy last HID events”).

### 6. Non-Functional Requirements
- **Performance:** Input latency <75 ms median; pointer updates at ≥60 Hz when device hardware allows. UI renders at 60 fps on mid-range hardware.
- **Reliability:** Foreground service survives process restarts; auto-reconnect attempts follow exponential backoff with cap; store state persists across configuration changes.
- **Security/Privacy:** No user content leaves device. Bluetooth permissions (ANDROID 12+ `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `POST_NOTIFICATIONS`) requested contextually with rationale and settings deep link.
- **Accessibility:** High-contrast theme, content descriptions on navigation icons, large tap targets (>48 dp), support for TalkBack focus on settings toggles.
- **Compliance:** Follow Google Play foreground service policy (notification with action, user-facing explanations). No hidden data collection.
- **Localization:** English-only for v1, but copy stored in string resources to enable future localization.

### 7. User Experience Direction
- Lock orientation to landscape; keep top bar containing device status, navigation tabs (Pairing, Keyboard, Mouse, Extended, Settings), and connection iconography (keyboard/mouse icons tinted for visibility against Material3 colors).
- Compose-first UI with Material 3 components, but customized typography for readability (e.g., monospaced font for key previews and logs).
- Ensure keyboard/mouse tabs stay visible when system IME is open (top navigation placement already implemented).
- Provide feedback loops: pressed keys highlight, modifier latching states, pointer movement indicator on first use, snackbar for connection changes.

### 8. Dependencies & Interfaces
- Android SDK 34+, Kotlin, Jetpack Compose, ReduxKotlin 0.5.5+, coroutines, DataStore preferences.
- Bluetooth stack: `BluetoothManager`, `BluetoothAdapter`, `BluetoothHidDevice` profile, HID descriptors stored within app resources.
- Internal service interface (`IBluetoothService`) bridging Redux middleware to platform operations.
- Optional host logging/metrics pipeline (local only) for QA.

### 9. Release Plan (Waterfall)
| Phase | Duration | Exit Criteria | Deliverables |
|-------|----------|---------------|--------------|
| **Requirements & Design (Weeks 1–3)** | Finalize PRD, system architecture diagrams, HID descriptor review, UX wireframes. | PRD sign-off, UX spec, architecture doc. |
| **Implementation (Weeks 4–10)** | Build pairing service, Redux wiring, keyboard/mouse UI, settings, IME integration. | Feature-complete app behind feature flags; unit tests passing. |
| **Integration & Verification (Weeks 11–13)** | Full Gradle builds, lint clean, instrumentation smoke tests, host OS matrix coverage. | Test report, defect log, updated documentation. |
| **Stabilization (Weeks 14–15)** | Bug fixes from verification, performance tuning, battery validation, localization review (English). | Release candidate build, release notes. |
| **Launch (Week 16)** | Store listing prep, final approvals, rollout plan. | Signed APK/AAB, marketing copy, support guide. |

Gate reviews occur at the end of each phase; downstream phases cannot start until exit criteria are met.

### 10. Acceptance Criteria
- All functional requirements validated via unit tests (`./gradlew :app:testDebugUnitTest`) and manual checklists (pairing, keyboard, mouse, IME, settings).
- Successful regression across Windows 11, macOS 14, and Ubuntu 24.04 hosts using at least one phone and one tablet device.
- Foreground service notification appears within 5 seconds in 100% of cold starts across QA runs.
- Lint and static analysis reports are clean (no new warnings).
- Play Console pre-launch report passes (when applicable).

### 11. Risks & Mitigations
- **Windows driver errors after pairing:** Simplify HID descriptors and add verbose logs around `hid.connect()`; maintain Windows-specific test machine. Target mitigation by end of Implementation phase.
- **Gesture latency on low-end devices:** Provide adjustable sensitivity curve, allow disabling animations, and profile Compose touch processing early.
- **Bluetooth permission regressions on Android 12+:** Centralize permission handling with instrumentation tests; document QA matrix.
- **Foreground service killed in Doze:** Emphasize START_STICKY, request battery optimization exclusion in onboarding, monitor via QA logs.
- **Complex state management:** Keep reducers pure, expand middleware tests, and document action flows to avoid regressions during waterfall handoffs.

### 12. Metrics & Telemetry Plan
- Internal QA build records (locally) anonymized counts of connection attempts, success/failure reasons, and HID error rates for post-test analysis.
- Track battery consumption via Android dumpsys batterystats scripts in lab runs.
- Manual usability study uses SUS (System Usability Scale) questionnaire plus qualitative notes.

### 13. Open Questions
1. Should BLE (HOGP) be prioritized for Windows compatibility in Phase 2 or deferred to a future release?
2. Do we need a customizable key macro system for power users, or is base layout sufficient for v1?
3. What is the policy for hosting debug logs/support exports (local only vs. optional share to support email)?
4. Will the app support split-screen/multi-window use cases, or remain landscape full-screen only?

### 14. Appendix
- **Reference Materials:** README.md, architecture notes in repo, lint and unit test reports under `app/build/reports`.
- **Test Commands:**
	- `./gradlew :app:assembleDebug`
	- `./gradlew :app:testDebugUnitTest`
	- `./gradlew :app:connectedDebugAndroidTest`
- **Artifacts:** Emulator screenshots (`keyboard_ime.png`), UI dumps (`uidump_after_install.xml`, `uidump_after_tap2.xml`).

---
End of Document.
