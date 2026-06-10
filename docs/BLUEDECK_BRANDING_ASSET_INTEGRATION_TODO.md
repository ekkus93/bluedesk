# BlueDeck Branding, Launcher Icon, and Splash Screen Integration TODO

## Implementation rules

- Use `bluedeck_android_assets.zip` as the asset source.
- Rename the user-facing app to **BlueDeck**.
- Do not rename the package, namespace, or application ID.
- Merge resources carefully; do not blindly overwrite existing project files.
- Do not add a fake timed splash screen.
- Do not break current app functionality.
- Run validation after resource/theme changes.

---

## Phase 1 — Inspect and unpack asset pack

### Task 1.1 — Unpack assets

- [ ] Locate `bluedeck_android_assets.zip`.
- [ ] Unzip it into a temporary working directory.
- [ ] Confirm these files exist:

```text
README_BLUEDECK_ASSETS.md
app/src/main/res/values/bluedeck_colors.xml
app/src/main/res/values/strings_bluedeck.xml
app/src/main/res/drawable/ic_bluedeck_foreground.xml
app/src/main/res/drawable/ic_bluedeck_monochrome.xml
app/src/main/res/drawable/ic_splash_bluedeck.xml
app/src/main/res/drawable/bluedeck_splash_background.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/values-v31/themes_bluedeck_splash.xml
generated_png/bluedeck_icon_1024.png
generated_png/bluedeck_splash_1080x1920.png
generated_png/bluedeck_splash_square_1080.png
```

Acceptance criteria:

- [ ] Asset pack contents are verified before copying.

### Task 1.2 — Inspect current project resources

- [ ] Locate current `strings.xml`.
- [ ] Locate current launcher icon resources.
- [ ] Locate current theme files.
- [ ] Locate `AndroidManifest.xml`.
- [ ] Locate `MainActivity.kt`.
- [ ] Locate `build.gradle.kts`.
- [ ] Note the current post-splash app theme name.

Acceptance criteria:

- [ ] You know where to merge resources safely.

---

## Phase 2 — Rename user-facing app to BlueDeck

### Task 2.1 — Update app name string

- [ ] Open existing `app/src/main/res/values/strings.xml`.
- [ ] Find existing `app_name`.
- [ ] Change it to:

```xml
<string name="app_name">BlueDeck</string>
```

- [ ] Do not create duplicate `app_name` definitions.
- [ ] If importing from `strings_bluedeck.xml`, merge only missing strings.

Acceptance criteria:

- [ ] Exactly one active `app_name` exists per resource configuration.
- [ ] Launcher label resolves to BlueDeck.

### Task 2.2 — Add optional branding strings

Add if not already present:

```xml
<string name="bluedeck_full_name">BlueDeck: Bluetooth Keyboard &amp; Mouse</string>
<string name="bluedeck_tagline">Your phone as a Bluetooth keyboard and mouse.</string>
```

Acceptance criteria:

- [ ] Branding strings are available for docs/about UI if needed.

### Task 2.3 — Update manifest label

- [ ] Open `AndroidManifest.xml`.
- [ ] Ensure application label is:

```xml
android:label="@string/app_name"
```

- [ ] Remove hardcoded old app labels if present.

Acceptance criteria:

- [ ] Android launcher/recent-app labels use BlueDeck.

### Task 2.4 — Update visible app text

Search and update user-facing old names:

```text
Bluetooth Keyboard Mouse
Android BT KBMouse
BT KBMouse
```

Update in:

- [ ] notification titles,
- [ ] Quick Settings tile label,
- [ ] about/help text,
- [ ] README/docs.

Do not rename internal package identifiers unless explicitly requested.

Acceptance criteria:

- [ ] User-facing text says BlueDeck where appropriate.

---

## Phase 3 — Install launcher icon assets

### Task 3.1 — Copy drawable icon assets

Copy into the real project:

```text
app/src/main/res/drawable/ic_bluedeck_foreground.xml
app/src/main/res/drawable/ic_bluedeck_monochrome.xml
```

Acceptance criteria:

- [ ] Vector drawable resources exist in the app.

### Task 3.2 — Copy color resources

Copy or merge:

```text
app/src/main/res/values/bluedeck_colors.xml
```

Acceptance criteria:

- [ ] `bluedeck_icon_background` and splash/icon colors resolve.

### Task 3.3 — Copy adaptive icon XML

Copy:

```text
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

Acceptance criteria:

- [ ] Adaptive icons point to BlueDeck resources.

### Task 3.4 — Verify manifest icon references

In `AndroidManifest.xml`, ensure:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

Acceptance criteria:

- [ ] App uses BlueDeck launcher icon.

### Task 3.5 — Preserve or generate legacy icons

- [ ] Check minSdk.
- [ ] If minSdk < 26, ensure legacy launcher icons still exist in density mipmap folders.
- [ ] Either keep existing legacy files or generate BlueDeck PNGs from `generated_png/bluedeck_icon_1024.png`.
- [ ] Do not delete all pre-26 launcher fallback assets unless minSdk >= 26.

Acceptance criteria:

- [ ] App has valid launcher icons for supported API levels.

### Task 3.6 — Handle monochrome icon compatibility

- [ ] Keep `<monochrome>` if build tooling supports it.
- [ ] If build fails because of `<monochrome>`, move it to an appropriate API-specific resource or remove the line while keeping `ic_bluedeck_monochrome.xml`.

Acceptance criteria:

- [ ] Build is not broken by themed-icon support.

---

## Phase 4 — Add splash screen resources

### Task 4.1 — Copy splash drawables

Copy into the real project:

```text
app/src/main/res/drawable/ic_splash_bluedeck.xml
app/src/main/res/drawable/bluedeck_splash_background.xml
```

Acceptance criteria:

- [ ] Splash resources resolve.

### Task 4.2 — Add AndroidX splash dependency

- [ ] Check whether `androidx.core:core-splashscreen` is already present.
- [ ] If missing, add it using the project’s existing dependency style.
- [ ] If using version catalog, add/use catalog entry.
- [ ] If using direct dependency, use a project-compatible version.
- [ ] Do not introduce a mismatched dependency style.

Acceptance criteria:

- [ ] `installSplashScreen()` import compiles.

### Task 4.3 — Add base splash starting theme

- [ ] Open base theme file under:

```text
app/src/main/res/values/
```

- [ ] Add a base style:

```xml
<style name="Theme.BlueDeck.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/bluedeck_splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_bluedeck</item>
    <item name="windowSplashScreenIconBackgroundColor">@android:color/transparent</item>
    <item name="postSplashScreenTheme">@style/Theme.BluetoothKeyboardMouse</item>
</style>
```

- [ ] Replace `@style/Theme.BluetoothKeyboardMouse` with the real current app theme if different.

Important:

- [ ] Do **not** define `Theme.BlueDeck.Starting` only in `values-v31`.

Acceptance criteria:

- [ ] Manifest can resolve `Theme.BlueDeck.Starting` on all supported API levels.

### Task 4.4 — Optional v31 override

- [ ] Inspect `values-v31/themes_bluedeck_splash.xml` from asset pack.
- [ ] Merge only if useful.
- [ ] Ensure it does not conflict with the base style.
- [ ] Keep resource names consistent.

Acceptance criteria:

- [ ] Android 12+ splash behavior is correct without breaking older devices.

---

## Phase 5 — Wire splash into manifest and MainActivity

### Task 5.1 — Set MainActivity starting theme

In `AndroidManifest.xml`, update `MainActivity`:

```xml
<activity
    android:name=".MainActivity"
    android:theme="@style/Theme.BlueDeck.Starting"
    ...>
```

- [ ] Preserve existing exported/intent-filter/orientation/launchMode attributes.
- [ ] Do not accidentally change service declarations.

Acceptance criteria:

- [ ] Cold launch starts with BlueDeck splash theme.

### Task 5.2 — Call `installSplashScreen()`

In `MainActivity.kt`:

- [ ] Add import:

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

- [ ] Call before `super.onCreate(savedInstanceState)`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    ...
}
```

Acceptance criteria:

- [ ] SplashScreen API is installed correctly.

### Task 5.3 — Do not add artificial delay

- [ ] Do not add a timer.
- [ ] Do not add `Thread.sleep`.
- [ ] Do not block startup to show the splash longer.
- [ ] Do not add fake `SplashActivity`.

Acceptance criteria:

- [ ] Splash is polished but not intrusive.

---

## Phase 6 — Store preview graphics appropriately

### Task 6.1 — Move PNG previews to docs/branding

Create if useful:

```text
docs/branding/
```

Copy:

```text
generated_png/bluedeck_icon_1024.png
generated_png/bluedeck_splash_1080x1920.png
generated_png/bluedeck_splash_square_1080.png
```

Acceptance criteria:

- [ ] High-res PNGs are available for README/store/preview use.
- [ ] They are not accidentally used as huge runtime drawables unless intentional.

### Task 6.2 — Optional README image reference

- [ ] If README already uses screenshots/images, optionally reference the BlueDeck preview image.
- [ ] Keep README lightweight.

Acceptance criteria:

- [ ] Branding previews are available without bloating app runtime resources.

---

## Phase 7 — Update docs and README

### Task 7.1 — Update README title

Change README title to:

```markdown
# BlueDeck

Turn your Android phone into a Bluetooth keyboard and mouse.
```

Acceptance criteria:

- [ ] README reflects new brand.

### Task 7.2 — Add branding section

Add:

```markdown
## Branding

App name: BlueDeck  
Full name: BlueDeck: Bluetooth Keyboard & Mouse  
Tagline: Your phone as a Bluetooth keyboard and mouse.
```

Acceptance criteria:

- [ ] Brand naming is documented.

### Task 7.3 — Clean old user-facing names

Search docs for:

```text
Bluetooth Keyboard Mouse
Android BT KBMouse
BT KBMouse
```

- [ ] Replace user-facing references with BlueDeck.
- [ ] Leave internal package/repo references unchanged if changing them would be risky.
- [ ] Do not rename repository/package unless Phil asks.

Acceptance criteria:

- [ ] User-facing docs consistently use BlueDeck.

---

## Phase 8 — Visual QA

### Task 8.1 — Launcher icon QA

Install/run the app and verify:

- [ ] Launcher icon is BlueDeck, not Android robot/default.
- [ ] Icon reads clearly at launcher size.
- [ ] Icon is not clipped by round mask.
- [ ] Icon is not clipped by rounded-square mask.
- [ ] Themed/monochrome icon does not break if available.

Acceptance criteria:

- [ ] Launcher icon looks production-ready.

### Task 8.2 — Splash QA

Cold-launch the app and verify:

- [ ] BlueDeck splash appears.
- [ ] Splash uses BlueDeck icon/art.
- [ ] No white flash.
- [ ] Splash transitions to normal app UI.
- [ ] No artificial delay.
- [ ] No old app icon/name appears.

Acceptance criteria:

- [ ] Splash feels polished and native.

### Task 8.3 — App name QA

Verify:

- [ ] launcher label says BlueDeck,
- [ ] recent-apps label says BlueDeck,
- [ ] foreground service notification title uses BlueDeck or a sensible BlueDeck service title,
- [ ] Quick Settings tile label uses BlueDeck if applicable.

Acceptance criteria:

- [ ] User-visible naming is consistent.

---

## Phase 9 — Build and test validation

### Task 9.1 — Resource/build validation

Run:

```bash
./gradlew assembleDebug
```

Fix any:

- [ ] duplicate resource errors,
- [ ] missing theme errors,
- [ ] missing icon resource errors,
- [ ] missing splash resource errors,
- [ ] dependency/import errors.

Acceptance criteria:

- [ ] Debug APK builds.

### Task 9.2 — JVM tests

Run:

```bash
./gradlew test
```

Acceptance criteria:

- [ ] Tests pass or unrelated failures are documented.

### Task 9.3 — Lint

Run:

```bash
./gradlew lintDebug
```

Acceptance criteria:

- [ ] No release-blocking lint errors from branding/splash changes.

### Task 9.4 — Instrumented smoke test if device available

Run:

```bash
./gradlew connectedDebugAndroidTest
```

or manually install and launch APK.

Acceptance criteria:

- [ ] App launches with BlueDeck splash and reaches normal UI.

---

## Phase 10 — Final acceptance checklist

Do not mark complete until all are true:

- [ ] App launcher label is BlueDeck.
- [ ] Manifest label uses `@string/app_name`.
- [ ] User-facing old app name references are cleaned up.
- [ ] BlueDeck adaptive launcher icon is installed.
- [ ] Default Android robot launcher icon is gone.
- [ ] Legacy launcher fallback exists if minSdk < 26.
- [ ] Splash drawables are installed.
- [ ] `Theme.BlueDeck.Starting` exists in base `values/`, not only `values-v31`.
- [ ] `MainActivity` uses `Theme.BlueDeck.Starting`.
- [ ] `installSplashScreen()` is called before `super.onCreate`.
- [ ] No fake splash delay or fake SplashActivity was added.
- [ ] README/docs use BlueDeck branding.
- [ ] `assembleDebug` passes.
- [ ] `test` passes or unrelated failures are documented.
- [ ] `lintDebug` has no branding-related blockers.
- [ ] Manual launch shows BlueDeck icon/splash/name correctly.
