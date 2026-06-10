# BlueDeck Branding, Launcher Icon, and Splash Screen Integration Spec

## 1. Purpose

This spec explains how to integrate `bluedeck_android_assets.zip` into the Android Bluetooth Keyboard/Mouse app and finish the BlueDeck rebrand.

The goals are:

1. Rename the user-facing Android app from the current placeholder name to **BlueDeck**.
2. Replace the default launcher icon with the provided BlueDeck adaptive icon.
3. Add a polished Android startup splash screen using the provided BlueDeck splash assets.
4. Keep the package/application ID unchanged unless explicitly requested later.
5. Merge resources safely without breaking existing themes, manifests, Gradle configuration, or tests.

The asset pack to use is:

```text
bluedeck_android_assets.zip
```

The app’s new user-facing name is:

```text
BlueDeck
```

Full descriptive name:

```text
BlueDeck: Bluetooth Keyboard & Mouse
```

Tagline:

```text
Your phone as a Bluetooth keyboard and mouse.
```

## 2. Important scope rules

### 2.1 Rename the app, not the package

Do **not** rename the Kotlin package, Gradle namespace, or application ID as part of this branding pass.

Keep these stable unless Phil explicitly requests a package/application ID change later:

```text
namespace
applicationId
Kotlin package path
existing Java/Kotlin imports
existing service/action package names
```

This pass is a branding polish task, not a package migration.

### 2.2 Merge resources; do not blindly overwrite

The asset zip contains ready-to-use Android resources, but some files are snippets or examples. Merge them carefully.

Do not blindly overwrite:

```text
existing strings.xml
existing themes.xml
existing AndroidManifest.xml
existing build.gradle.kts
```

Instead:

- copy drawable/icon resources,
- merge color resources,
- update existing string values,
- add the splash theme to the app’s real theme files,
- update manifest icon/theme references safely.

### 2.3 No fake splash delay

Do not add an artificial timer or fake splash Activity.

Use Android’s splash screen theme/API. The splash should disappear as soon as the app is ready.

### 2.4 Keep the app functional

After branding integration:

- Classic HID service must still start.
- Foreground service notification must still show.
- Quick Settings tile must still work as currently implemented.
- Existing tests should still compile.
- Resource references must not break pre-Android-12 devices.

## 3. Asset pack contents

The generated asset pack contains this structure:

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

### 3.1 Production Android resources

Use these in the app:

```text
bluedeck_colors.xml
ic_bluedeck_foreground.xml
ic_bluedeck_monochrome.xml
ic_splash_bluedeck.xml
bluedeck_splash_background.xml
ic_launcher.xml
ic_launcher_round.xml
```

### 3.2 Snippet resources

Treat these as merge snippets:

```text
strings_bluedeck.xml
themes_bluedeck_splash.xml
```

Do not create duplicate `app_name` resources. If the project already has `app_name`, update it in the existing file or remove the duplicate from the imported snippet.

### 3.3 Preview/source graphics

Do not put high-resolution PNG preview graphics directly into normal runtime drawables unless there is a deliberate reason.

These are for docs, previews, README, store listing, or future design work:

```text
generated_png/bluedeck_icon_1024.png
generated_png/bluedeck_splash_1080x1920.png
generated_png/bluedeck_splash_square_1080.png
```

Recommended location if keeping them in the repo:

```text
docs/branding/bluedeck_icon_1024.png
docs/branding/bluedeck_splash_1080x1920.png
docs/branding/bluedeck_splash_square_1080.png
```

## 4. Rename requirements

### 4.1 User-facing app name

Set:

```xml
<string name="app_name">BlueDeck</string>
```

If the current project already has:

```xml
<string name="app_name">Bluetooth Keyboard Mouse</string>
```

replace it with:

```xml
<string name="app_name">BlueDeck</string>
```

Do not leave two `app_name` resources in the same resource configuration.

### 4.2 Add optional branding strings

Add these if useful:

```xml
<string name="bluedeck_full_name">BlueDeck: Bluetooth Keyboard &amp; Mouse</string>
<string name="bluedeck_tagline">Your phone as a Bluetooth keyboard and mouse.</string>
```

Use the full name in README/docs/about text, not necessarily as the launcher label.

### 4.3 Manifest label

Ensure the application label resolves to the app name:

```xml
<application
    android:label="@string/app_name"
    ...>
```

If activities/services/tile labels use a hardcoded old app name, update them to `@string/app_name` or a BlueDeck-specific label.

### 4.4 Notifications and Quick Settings tile

Update visible text from the old placeholder app name to BlueDeck where appropriate:

- foreground service notification title,
- Quick Settings tile label,
- debug/about screen labels,
- README title,
- docs title.

Examples:

```text
BlueDeck is running
BlueDeck HID service active
BlueDeck
```

Avoid overly long notification titles.

## 5. Launcher icon requirements

### 5.1 Adaptive icon files

Copy these into the real app resource tree:

```text
app/src/main/res/drawable/ic_bluedeck_foreground.xml
app/src/main/res/drawable/ic_bluedeck_monochrome.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

Also copy/merge color resources:

```text
app/src/main/res/values/bluedeck_colors.xml
```

### 5.2 Manifest icon references

Ensure `AndroidManifest.xml` references:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

### 5.3 Legacy launcher fallback

The asset pack provides adaptive icons for `mipmap-anydpi-v26`. If the app supports API levels below 26, make sure legacy fallback icons still exist.

Options:

1. Keep the existing legacy `mipmap-mdpi` / `mipmap-hdpi` / `mipmap-xhdpi` / `mipmap-xxhdpi` / `mipmap-xxxhdpi` launcher PNGs if already present.
2. Generate new BlueDeck PNG launcher icons for legacy densities from `generated_png/bluedeck_icon_1024.png`.

Do not delete all legacy launcher PNGs unless the app’s minSdk is 26+.

### 5.4 Android 13 themed icon

The adaptive icon XML includes:

```xml
<monochrome android:drawable="@drawable/ic_bluedeck_monochrome" />
```

Keep this if the project/build supports it.

If the project’s Android Gradle Plugin or min tooling complains about `monochrome`, either:

- keep it only in a v33 resource variant, or
- remove the `monochrome` line and keep `ic_bluedeck_monochrome.xml` for later.

Do not break the build for themed-icon support.

## 6. Splash screen requirements

### 6.1 Use AndroidX SplashScreen

Use the AndroidX core splashscreen API if it is not already present.

In Gradle, add the dependency using the project’s existing dependency style.

Examples:

If the project uses version catalogs:

```kotlin
implementation(libs.androidx.core.splashscreen)
```

If it uses direct dependencies:

```kotlin
implementation("androidx.core:core-splashscreen:<project-approved-version>")
```

Do not invent an incompatible version. Prefer matching the project’s existing AndroidX version management.

### 6.2 Splash resources

Copy:

```text
app/src/main/res/drawable/ic_splash_bluedeck.xml
app/src/main/res/drawable/bluedeck_splash_background.xml
```

### 6.3 Splash theme must exist in base resources

Important: do not define the starting splash theme only in `values-v31`.

The manifest references the starting theme at build/runtime. There must be a base resource definition available in:

```text
app/src/main/res/values/themes.xml
```

or another file under:

```text
app/src/main/res/values/
```

Recommended base style:

```xml
<style name="Theme.BlueDeck.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/bluedeck_splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_bluedeck</item>
    <item name="windowSplashScreenIconBackgroundColor">@android:color/transparent</item>
    <item name="postSplashScreenTheme">@style/Theme.BluetoothKeyboardMouse</item>
</style>
```

If the app’s real post-splash theme has a different name, use that instead of:

```xml
@style/Theme.BluetoothKeyboardMouse
```

### 6.4 Optional Android 12+ override

The asset pack includes:

```text
app/src/main/res/values-v31/themes_bluedeck_splash.xml
```

Treat it as an optional override/snippet. It is not enough by itself unless a base `Theme.BlueDeck.Starting` exists in `values/`.

### 6.5 Manifest activity theme

Set `MainActivity` to use the starting splash theme:

```xml
<activity
    android:name=".MainActivity"
    android:theme="@style/Theme.BlueDeck.Starting"
    ...>
```

Keep existing launch mode, exported, intent filters, orientation, and other activity attributes unchanged unless they directly conflict.

### 6.6 MainActivity installSplashScreen

In `MainActivity.onCreate`, call:

```kotlin
installSplashScreen()
```

before:

```kotlin
super.onCreate(savedInstanceState)
```

Example:

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    ...
}
```

Do not delay app startup just to show the splash longer.

### 6.7 Avoid fake SplashActivity

Do not add a separate `SplashActivity` unless absolutely necessary. The AndroidX SplashScreen approach is preferred.

## 7. Visual QA requirements

### 7.1 Launcher icon

Verify launcher icon:

- appears as BlueDeck, not default Android robot,
- is readable at small size,
- works with round/circle masks,
- works with rounded-square masks,
- has no clipped important details,
- does not have tiny unreadable text.

### 7.2 Splash screen

Verify splash screen:

- appears on cold launch,
- uses BlueDeck icon/art,
- transitions to the app’s normal theme,
- does not flash white,
- does not show the old app icon,
- does not show the old app name,
- does not delay launch unnecessarily.

### 7.3 App naming

Verify visible name:

- launcher says BlueDeck,
- recent apps label says BlueDeck,
- notification title uses BlueDeck or a sensible BlueDeck service title,
- Quick Settings tile uses BlueDeck if applicable.

## 8. Documentation requirements

Update README/docs to use BlueDeck branding.

### 8.1 README title

Change from old name to:

```markdown
# BlueDeck

Turn your Android phone into a Bluetooth keyboard and mouse.
```

### 8.2 Add branding note

Add a short section:

```markdown
## Branding

App name: BlueDeck  
Full name: BlueDeck: Bluetooth Keyboard & Mouse  
Tagline: Your phone as a Bluetooth keyboard and mouse.
```

### 8.3 Remove stale old-name references

Search for:

```text
Bluetooth Keyboard Mouse
Android BT KBMouse
android_bt_kbmouse
```

Rules:

- User-facing docs should say BlueDeck.
- Internal package/repo identifiers may remain unchanged if changing them would be a risky migration.
- Do not rename the repository unless Phil explicitly asks.

## 9. Build and validation requirements

Run:

```bash
./gradlew clean test
./gradlew assembleDebug
./gradlew lintDebug
```

If an Android device/emulator is available, run:

```bash
./gradlew connectedDebugAndroidTest
```

At minimum, confirm:

- resources compile,
- no duplicate `app_name`,
- manifest theme resolves,
- launcher icon resources resolve,
- splash theme resources resolve,
- app launches.

## 10. Files likely to change

Expected:

```text
app/src/main/res/values/strings.xml
app/src/main/res/values/bluedeck_colors.xml
app/src/main/res/drawable/ic_bluedeck_foreground.xml
app/src/main/res/drawable/ic_bluedeck_monochrome.xml
app/src/main/res/drawable/ic_splash_bluedeck.xml
app/src/main/res/drawable/bluedeck_splash_background.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/values/themes.xml
app/src/main/AndroidManifest.xml
app/src/main/java/.../MainActivity.kt
app/build.gradle.kts
README.md
```

Possible:

```text
app/src/main/res/mipmap-mdpi/ic_launcher.png
app/src/main/res/mipmap-hdpi/ic_launcher.png
app/src/main/res/mipmap-xhdpi/ic_launcher.png
app/src/main/res/mipmap-xxhdpi/ic_launcher.png
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
docs/branding/
docs/README or release notes
```

## 11. Acceptance criteria

The BlueDeck branding integration is complete when:

- the launcher label is `BlueDeck`,
- the app uses the BlueDeck adaptive launcher icon,
- the app no longer shows the default Android robot icon,
- `MainActivity` uses a proper splash starting theme,
- `installSplashScreen()` is called correctly,
- the splash screen uses the BlueDeck splash icon/art,
- the splash transitions to the normal app theme,
- resources compile without duplicate-name errors,
- old user-facing app name references are updated,
- package/application ID remains unchanged,
- README/docs reflect the BlueDeck brand,
- Gradle build/test/lint validation passes or issues are documented.
