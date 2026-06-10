# replies2.md — Replies to Claude Code on BlueDeck Release Hardening Fix 2

These replies address Claude Code's questions and issues from `responses2(19).md`.

## Overall position

The Fix 2 direction is correct. Proceed with the narrow follow-up pass.

The confirmed bugs are real:

1. persisted BLE startup permission selection is still wrong,
2. `BluetoothService` foreground failure handling is still incomplete,
3. `BootReceiver` must become backend-aware,
4. notification permission sequencing should be state-based rather than timer-only,
5. validation wording must not overclaim manual smoke coverage,
6. the minor polish items are valid.

Do not redo the whole previous release-hardening pass. Keep this as a targeted Fix 2.

---

## 1. Q1 — BLE-denial handling at app startup

### Decision

Use option **(a)** from the spec:

```text
On interactive app startup, if persisted BLE mode cannot start because required BLE permissions are missing, persist useBleHogp=false and fall back to Classic.
```

### Required behavior

If:

```text
useBleHogp == true
```

but startup finds missing BLE permissions:

```text
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
```

then:

1. do not start broken BLE,
2. persist:

```text
useBleHogp = false
```

3. fall back to Classic if Classic startup permissions are available,
4. show/log a clear message:

```text
BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.
```

### Rationale

The interactive app startup path is user-visible and can safely make the app usable again. If Android permissions were revoked externally, leaving `useBleHogp=true` would keep the app stuck in a broken mode until the user manually finds the setting.

For v0.1, Classic HID is the stable/default path. Falling back to Classic is the better failure mode.

### Important contrast with boot

This behavior is intentionally different from boot.

At boot, do **not** persistently change the user's BLE preference. Boot is silent/background, so it should not rewrite a user setting without interaction.

At boot:

```text
BLE selected + missing BLE permissions => start nothing, log skip reason.
```

Interactive startup:

```text
BLE selected + missing BLE permissions => persist Classic fallback, start Classic if possible.
```

This asymmetry is deliberate.

---

## 2. Q2 — Notification sequencing approach

### Decision

Use option **(a)**:

```text
Minimal post-startup state flag.
```

Do not add a new Settings row for this pass.

### Required behavior

The app may still auto-request `POST_NOTIFICATIONS`, but only after the startup Bluetooth permission flow has resolved.

Implement a shared startup state/signal such as:

```kotlin
startupPermissionFlowResolved = true
```

or equivalent.

The notification permission launcher must not fire until:

1. settings are loaded,
2. selected backend startup permissions have been requested/checked,
3. any denial/fallback path has completed,
4. startup is no longer actively using the Bluetooth permission launcher.

### Do not rely only on a timer

Remove or demote the timer-only gating.

Bad:

```kotlin
delay(NOTIF_PROMPT_DELAY_MS)
notifLauncher.launch(POST_NOTIFICATIONS)
```

Good:

```kotlin
if (startupPermissionFlowResolved && notificationPermissionNotYetHandled) {
    notifLauncher.launch(POST_NOTIFICATIONS)
}
```

A small visual delay after the state resolves is acceptable, but it must not be the primary race-prevention mechanism.

### Rationale

This is the smallest v0.1-safe fix. It avoids permission-dialog races without adding new UI or expanding the settings surface.

---

## 3. Q3 — XML theme cleanup

### Decision

Yes, do the XML theme cleanup now, carefully.

### Required behavior

Update the XML shell theme colors so they do not visibly clash with the BlueDeck Compose theme and splash.

This includes checking:

```text
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/values/colors.xml
```

and replacing old template colors where safe:

```text
purple_500
purple_700
teal_200
```

with BlueDeck palette colors.

Suggested mapping:

```text
colorPrimary        -> #00D4FF or #4F46E5 depending contrast/context
colorPrimaryDark    -> #07111F
colorAccent         -> #00BFA6
window background   -> #101827 or existing safe app background
```

Use the existing named BlueDeck resources if present, rather than duplicating hex values.

### Constraints

Do not break:

```text
Theme.BlueDeck.Starting
Theme.BluetoothKeyboardMouse
postSplashScreenTheme
```

Do not do a broad theme rewrite. This is cosmetic consistency only.

### Rationale

The app is now branded as BlueDeck. A purple/teal template shell theme can create a visible mismatch during splash handoff, recents/app switcher previews, or non-Compose system surfaces.

---

## 4. Q4 — Quick Settings tile label

### Decision

Yes, proceed with the proposed tile label plan.

### Required behavior

At confirmed connection time, store a friendly connected label:

```kotlin
device.name ?: device.address
```

Preferred name:

```text
bonded device name
```

Fallback:

```text
MAC address
```

Do not show a raw MAC address if a usable device name exists.

### Preserve existing safety behavior

Keep the Fix 1 behavior:

- do not mark tile active merely because a connect broadcast was sent,
- do not send Classic broadcasts in BLE mode,
- do not rework tile architecture,
- keep fallback to address when no name exists.

### Rationale

This is low-risk and improves UX. A raw MAC address in the tile looks unfinished when the bonded host has a usable name.

---

## 5. Notes on implementation details

### 5.1 Reuse existing `BackendMode`

Claude is correct. Reuse the existing enum:

```kotlin
BackendMode.CLASSIC_HID
BackendMode.BLE_HOGP
```

Do not create a duplicate `BackendMode { CLASSIC, BLE_HOGP }`.

The spec's enum was illustrative only.

### 5.2 Combine startup planner and notification sequencing

This is a good plan.

Backend-aware startup permission handling naturally creates the state signal needed for notification prompt sequencing.

Recommended flow:

1. load settings,
2. use `StartupPermissionPlanner`,
3. request/check selected backend permissions,
4. apply fallback if BLE denied,
5. start selected/fallback backend,
6. set startup permission flow as resolved,
7. only then allow notification permission prompt.

### 5.3 Validation wording

Claude's plan is correct.

Use accurate evidence categories:

```text
Unit-verified
Instrumented-verified
Physical-HID-verified
Manual-device-verified
Pending manual UX smoke test
```

Do not mark permission-dialog UX items as manually verified unless someone actually performed them on a device.

### 5.4 Boot foreground service start

Agreed: starting a foreground service from `BOOT_COMPLETED` is allowed under the boot exemption, but the receiver still needs to be careful.

Use:

- `goAsync()`,
- coroutine,
- timeout,
- explicit permission checks,
- pure `BootStartPlanner`,
- no silent Classic fallback when BLE is selected.

---

## 6. Direct answers to Claude's open questions

### Q1. BLE-denial on startup: persist `useBleHogp=false` or keep setting?

Persist `useBleHogp=false` on **interactive app startup** denial and fall back to Classic if Classic permissions are available.

At **boot**, do not persistently change the setting. Skip startup and log the reason.

### Q2. Notifications: minimal post-startup flag or explicit Settings row?

Use the minimal post-startup state flag for v0.1.

No new Settings row in this pass.

### Q3. XML theme cleanup: do it now or defer?

Do it now, carefully.

Keep the existing theme names and splash handoff intact. Only replace stale template colors with BlueDeck-consistent colors where safe.

### Q4. Tile label: store bonded name with address fallback?

Yes.

Use:

```kotlin
device.name ?: device.address
```

or equivalent, and preserve the rule that active/connected state must only reflect confirmed connection.

---

## 7. Final implementation contract

Proceed with Fix 2 using these decisions:

```text
Interactive BLE startup denial:
Persist useBleHogp=false and fall back to Classic if possible.

Boot BLE denial:
Do not persist setting changes. Start nothing. Log clear skip reason.

Notification prompt:
Use post-startup state flag. No timer-only sequencing. No new Settings row.

XML theme:
Clean up stale purple/teal shell-theme colors carefully.

Quick Settings tile:
Use bonded device name, fallback to MAC address.

Backend enum:
Reuse existing BackendMode.CLASSIC_HID / BackendMode.BLE_HOGP.

Validation:
Classify evidence honestly. Unit tests are not manual UX smoke tests.
```

Keep the pass narrow. Fix the remaining gaps; do not reopen already-completed hardening work.
