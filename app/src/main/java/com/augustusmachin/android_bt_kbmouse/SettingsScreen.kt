package com.augustusmachin.android_bt_kbmouse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.launch

private const val SDK_INT_MARSHMALLOW = 23
private const val SENSITIVITY_MIN = 0.5f
private const val SENSITIVITY_MAX = 3.0f
private const val SCROLL_SPEED_MIN = 0.5f
private const val SCROLL_SPEED_MAX = 3.0f
private const val KEY_REPEAT_MIN_MS = 150
private const val KEY_REPEAT_MAX_MS = 1000

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < SDK_INT_MARSHMALLOW) return true
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Opens the most-specific battery-optimization screen available. Returns a user-visible error
 * only when both the app-specific request and the bounded general-settings fallback fail.
 */
private fun requestIgnoreBatteryOptimizations(context: android.content.Context): String? {
    if (android.os.Build.VERSION.SDK_INT < SDK_INT_MARSHMALLOW) return null
    return try {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + context.packageName),
            ),
        )
        null
    } catch (
        @Suppress("TooGenericExceptionCaught") first: Exception,
    ) {
        try {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            null
        } catch (
            @Suppress("TooGenericExceptionCaught") second: Exception,
        ) {
            val message =
                "Could not open battery optimization settings: " +
                    (second.message ?: second.javaClass.simpleName)
            DebugLog.e(
                "SettingsScreen",
                "$message (app-specific request also failed: ${first.message ?: first.javaClass.simpleName})",
            )
            message
        }
    }
}

private suspend fun loadImeLabels(
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

// Tracks the battery-optimization exemption, re-checking on resume so the related row
// hides itself after the user grants the exemption and returns to the app.
@Composable
private fun rememberBatteryExemptState(context: android.content.Context): androidx.compose.runtime.State<Boolean> {
    val batteryExempt = remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    batteryExempt.value = isIgnoringBatteryOptimizations(context)
                }
            }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return batteryExempt
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onOpenLogs: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val flow = remember { SettingsManager.flow(context) }
    val settings by flow.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    var imeOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var imeLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var imeLoadError by remember { mutableStateOf<String?>(null) }

    val batteryExempt by rememberBatteryExemptState(context)

    LaunchedEffect(Unit) {
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

    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState),
    ) {
        PointerSettingsSection(settings, context, scope)
        InputBehaviorSection(settings, context, scope)
        if (!batteryExempt) {
            BackgroundReliabilitySection(context)
        }
        CompatibilitySection(settings, context, scope)

        if (onOpenLogs != null) {
            Button(modifier = Modifier.padding(top = 8.dp), onClick = onOpenLogs) {
                Text("Open Logs")
            }
        }

        ImeOverridesSection(
            imeOverrides = imeOverrides,
            imeLabels = imeLabels,
            loadError = imeLoadError,
            context = context,
            scope = scope,
            onOverridesChanged = { newOverrides, newLabels ->
                imeOverrides = newOverrides
                imeLabels = newLabels
                imeLoadError = null
            },
        )
    }
}

// This Compose section keeps tightly related pointer controls and transient slider state together.
@Suppress("LongMethod")
@Composable
private fun PointerSettingsSection(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var sensitivity by remember(settings.touchpadSensitivity) { mutableStateOf(settings.touchpadSensitivity) }
    Text("Touchpad sensitivity: ${"%.2f".format(sensitivity)}")
    Slider(
        value = sensitivity,
        onValueChange = { sensitivity = it.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX) },
        onValueChangeFinished = {
            scope.launch { SettingsManager.setTouchpadSensitivity(context, sensitivity) }
        },
        valueRange = SENSITIVITY_MIN..SENSITIVITY_MAX,
    )

    if (settings.hidSimplified) {
        Text(
            "Scrolling requires the full HID descriptor. " +
                "Disable \"Use simplified HID descriptor\" below to enable scroll controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    } else {
        var scrollSpeed by remember(settings.scrollSpeed) { mutableStateOf(settings.scrollSpeed) }
        Text("Scroll speed: ${"%.2f".format(scrollSpeed)}", modifier = Modifier.padding(top = 16.dp))
        Slider(
            value = scrollSpeed,
            onValueChange = { scrollSpeed = it.coerceIn(SCROLL_SPEED_MIN, SCROLL_SPEED_MAX) },
            onValueChangeFinished = {
                scope.launch { SettingsManager.setScrollSpeed(context, scrollSpeed) }
            },
            valueRange = SCROLL_SPEED_MIN..SCROLL_SPEED_MAX,
        )

        Row(Modifier.padding(top = 16.dp)) {
            Text("Invert vertical scroll", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.invertScroll,
                onCheckedChange = { scope.launch { SettingsManager.setInvertScroll(context, it) } },
            )
        }

        Row(Modifier.padding(top = 16.dp)) {
            Text("Enable horizontal scroll", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.enableHorizontalScroll,
                onCheckedChange = { scope.launch { SettingsManager.setEnableHorizontalScroll(context, it) } },
            )
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("Invert horizontal scroll", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.invertHorizontalScroll,
                onCheckedChange = { scope.launch { SettingsManager.setInvertHorizontalScroll(context, it) } },
            )
        }
    }

    Row(Modifier.padding(top = 16.dp)) {
        Text("Enable three-finger middle-click", modifier = Modifier.weight(1f))
        Switch(
            checked = settings.enableMiddleClick,
            onCheckedChange = { scope.launch { SettingsManager.setEnableMiddleClick(context, it) } },
        )
    }
}

// This Compose section is declarative UI wiring; splitting it would separate coupled settings controls.
@Suppress("LongMethod")
@Composable
private fun InputBehaviorSection(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var keyRepeatDelay by remember(settings.keyRepeatDelayMs) { mutableStateOf(settings.keyRepeatDelayMs.toFloat()) }
    Text("Key repeat delay: ${keyRepeatDelay.toInt()} ms", modifier = Modifier.padding(top = 16.dp))
    Slider(
        value = keyRepeatDelay,
        onValueChange = {
            keyRepeatDelay = it.coerceIn(KEY_REPEAT_MIN_MS.toFloat(), KEY_REPEAT_MAX_MS.toFloat())
        },
        onValueChangeFinished = {
            scope.launch { SettingsManager.setKeyRepeatDelay(context, keyRepeatDelay.toInt()) }
        },
        valueRange = KEY_REPEAT_MIN_MS.toFloat()..KEY_REPEAT_MAX_MS.toFloat(),
    )

    Row(Modifier.padding(top = 16.dp)) {
        Text("Key click sound", modifier = Modifier.weight(1f))
        Switch(
            checked = settings.clickSound,
            onCheckedChange = { scope.launch { SettingsManager.setClickSound(context, it) } },
        )
    }

    Row(Modifier.padding(top = 16.dp)) {
        Text("Enable debug logging", modifier = Modifier.weight(1f))
        Switch(
            checked = settings.debugLogging,
            onCheckedChange = { scope.launch { SettingsManager.setDebugLogging(context, it) } },
        )
    }
    Row(Modifier.padding(top = 8.dp)) {
        Text("Offline preview (use keyboard/mouse without Bluetooth)", modifier = Modifier.weight(1f))
        Switch(
            checked = settings.offlinePreview,
            onCheckedChange = { scope.launch { SettingsManager.setOfflinePreview(context, it) } },
        )
    }
    if (settings.debugLogging) {
        Row(Modifier.padding(top = 8.dp)) {
            Text("Log level:", modifier = Modifier.padding(end = 8.dp))
            val sel = settings.logLevel
            Button(
                onClick = { scope.launch { SettingsManager.setLogLevel(context, 0) } },
                modifier = Modifier.padding(end = 4.dp),
                enabled = sel != 0,
            ) { Text("All") }
            Button(
                onClick = { scope.launch { SettingsManager.setLogLevel(context, 1) } },
                modifier = Modifier.padding(end = 4.dp),
                enabled = sel != 1,
            ) { Text("Info") }
            Button(
                onClick = { scope.launch { SettingsManager.setLogLevel(context, 2) } },
                enabled = sel != 2,
            ) { Text("Error") }
        }
    }
    Row(Modifier.padding(top = 16.dp)) {
        Text("Start service on boot", modifier = Modifier.weight(1f))
        Switch(
            checked = settings.startOnBoot,
            onCheckedChange = { scope.launch { SettingsManager.setStartOnBoot(context, it) } },
        )
    }
}

@Composable
private fun BackgroundReliabilitySection(context: android.content.Context) {
    Text(
        "Background reliability",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Disable battery optimization")
            Text(
                "Let Android keep the Bluetooth HID service running so the connection isn't dropped " +
                    "when the screen is off or the app is in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(
            onClick = {
                requestIgnoreBatteryOptimizations(context)?.let { message ->
                    StoreProvider.dispatch(Action.UpdateMessage(message))
                }
            },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Disable") }
    }
}

@Composable
private fun CompatibilitySection(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Text(
        "Compatibility",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Use simplified HID descriptor (Windows)")
            Text(
                "Enable if your Windows host shows a \"Driver error\" after pairing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = settings.hidSimplified,
            onCheckedChange = { scope.launch { SettingsManager.setHidSimplified(context, it) } },
        )
    }
    BleHogpToggle(settings, context, scope)
}

// BLE HOGP requires BLUETOOTH_CONNECT + BLUETOOTH_ADVERTISE. Enabling persists the setting
// only after those are granted; if denied, the toggle stays off. MainActivity observes the
// persisted setting and switches backends live.
@Composable
private fun BleHogpToggle(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val sdk = android.os.Build.VERSION.SDK_INT
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Callback maps are partial; re-read actual OS state before persisting.
            if (PermissionGrantChecker.hasAll(context, PermissionPolicy.requiredForBleStartup(sdk))) {
                scope.launch { SettingsManager.setUseBleHogp(context, true) }
            } else {
                StoreProvider.dispatch(
                    Action.UpdateMessage(
                        "BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.",
                    ),
                )
            }
        }
    Row(Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Use BLE HOGP (experimental)")
            Text(
                "Switches the HID backend live after required permissions are granted. " +
                    "In BLE mode the host initiates pairing/connection to BlueDeck.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = settings.useBleHogp,
            onCheckedChange = { enabled ->
                if (!enabled) {
                    scope.launch { SettingsManager.setUseBleHogp(context, false) }
                } else {
                    val missing = PermissionGrantChecker.missing(context, PermissionPolicy.requiredForBleStartup(sdk))
                    if (missing.isEmpty()) {
                        scope.launch { SettingsManager.setUseBleHogp(context, true) }
                    } else {
                        launcher.launch(missing.toTypedArray())
                    }
                }
            },
        )
    }
}

// Compose state, Android services, and the update callback are distinct UI dependencies here.
@Suppress("LongParameterList")
@Composable
private fun ImeOverridesSection(
    imeOverrides: Map<String, Boolean>,
    imeLabels: Map<String, String>,
    loadError: String?,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onOverridesChanged: (Map<String, Boolean>, Map<String, String>) -> Unit,
) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
    Text("IME overrides:")
    if (loadError != null) {
        Text(
            loadError,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.error,
        )
    } else if (imeOverrides.isEmpty()) {
        Text("(none)", modifier = Modifier.padding(top = 4.dp))
    }
    imeOverrides.forEach { (pkg, allowed) ->
        val label = imeLabels[pkg] ?: pkg
        Row(Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label)
                if (label != pkg) Text("($pkg)", modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (allowed) "Allowed" else "Denied", modifier = Modifier.padding(end = 8.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            SettingsManager.removeImeOverride(context, pkg)
                            val updated = SettingsManager.getAllImeOverrides(context)
                            onOverridesChanged(updated, loadImeLabels(context, updated))
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            val message =
                                "Could not update IME overrides: ${e.message ?: e.javaClass.simpleName}"
                            DebugLog.e("SettingsScreen", message)
                            StoreProvider.dispatch(Action.UpdateMessage(message))
                        }
                    }
                },
            ) { Text("Remove") }
        }
    }
}
