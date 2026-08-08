from pathlib import Path

path = Path("app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsScreen.kt")
text = path.read_text()

old_labels = '''private suspend fun loadImeLabels(
    context: android.content.Context,
    overrides: Map<String, Boolean>,
): Map<String, String> {
    val pm = context.packageManager
    return overrides.keys.associateWith { pkg ->
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))?.toString() ?: pkg
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // A missing/stale package label is not an IME override storage failure. Keep the
            // stable package id visible while recording the diagnostic.
            DebugLog.e("SettingsScreen", "IME label lookup failed for $pkg: ${e.message}")
            pkg
        }
    }
}
'''
new_labels = '''private suspend fun loadImeLabels(
    context: android.content.Context,
    overrides: Map<String, Boolean>,
): Map<String, String> {
    val pm = context.packageManager
    return overrides.keys.associateWith { pkg ->
        val resolution =
            resolveImeLabel(pkg) {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
            }
        resolution.diagnostic?.let { DebugLog.e("SettingsScreen", it) }
        resolution.label
    }
}
'''

old_effect = '''    LaunchedEffect(Unit) {
        try {
            val loaded = SettingsManager.getAllImeOverrides(context)
            imeOverrides = loaded
            imeLabels = loadImeLabels(context, loaded)
            imeLoadError = null
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Do not transform a storage/read failure into a successful empty configuration.
            // Preserve any last-known-good in-memory values and expose the failure separately.
            val message = "Could not load IME overrides: ${e.message ?: e.javaClass.simpleName}"
            DebugLog.e("SettingsScreen", message)
            imeLoadError = message
        }
    }
'''
new_effect = '''    LaunchedEffect(Unit) {
        val loadState =
            loadImeOverridesState(
                previousOverrides = imeOverrides,
                previousLabels = imeLabels,
                readOverrides = { SettingsManager.getAllImeOverrides(context) },
                resolveLabels = { loaded -> loadImeLabels(context, loaded) },
            )
        imeOverrides = loadState.overrides
        imeLabels = loadState.labels
        imeLoadError = loadState.errorMessage
        loadState.errorMessage?.let { DebugLog.e("SettingsScreen", it) }
    }
'''

for old, new, name in [
    (old_labels, new_labels, "loadImeLabels"),
    (old_effect, new_effect, "LaunchedEffect"),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one {name} block, found {count}")
    text = text.replace(old, new)

path.write_text(text)
