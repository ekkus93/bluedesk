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

private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < SDK_INT_MARSHMALLOW) return
    try {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + context.packageName),
            ),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
    val settings by flow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    val scope = rememberCoroutineScope()
    var imeOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var imeLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val batteryExempt by rememberBatteryExemptState(context)

    LaunchedEffect(Unit) {
        try {
            imeOverrides = SettingsManager.getAllImeOverrides(context)
            imeLabels = loadImeLabels(context, imeOverrides)
        } catch (_: Exception) {
            imeOverrides = emptyMap()
            imeLabels = emptyMap()
        }
    }

    val scrollState = rememberScrollState()
    // Ensure padding and navigation insets are INSIDE the scrollable area so the bottom
    // content isn't hidden behind system bars or a bottom nav/menu. Padding must be
    // applied before verticalScroll so it becomes part of the scrollable content.
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
            context = context,
            scope = scope,
            onOverridesChanged = { newOverrides, newLabels ->
                imeOverrides = newOverrides
                imeLabels = newLabels
            },
        )
    }
}

@Composable
private fun PointerSettingsSection(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Text("Touchpad sensitivity: ${"%.2f".format(settings.touchpadSensitivity)}")
    Slider(value = settings.touchpadSensitivity, onValueChange = {
        scope.launch {
            SettingsManager.setTouchpadSensitivity(
                context,
                it.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX),
            )
        }
    }, valueRange = SENSITIVITY_MIN..SENSITIVITY_MAX)

    if (settings.hidSimplified) {
        Text(
            "Scrolling requires the full HID descriptor. " +
                "Disable \"Use simplified HID descriptor\" below to enable scroll controls.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    } else {
        Text("Scroll speed: ${"%.2f".format(settings.scrollSpeed)}", modifier = Modifier.padding(top = 16.dp))
        Slider(value = settings.scrollSpeed, onValueChange = {
            scope.launch {
                SettingsManager.setScrollSpeed(
                    context,
                    it.coerceIn(SCROLL_SPEED_MIN, SCROLL_SPEED_MAX),
                )
            }
        }, valueRange = SCROLL_SPEED_MIN..SCROLL_SPEED_MAX)

        Row(Modifier.padding(top = 16.dp)) {
            Text("Invert vertical scroll", modifier = Modifier.weight(1f))
            Switch(checked = settings.invertScroll, onCheckedChange = {
                scope.launch { SettingsManager.setInvertScroll(context, it) }
            })
        }

        Row(Modifier.padding(top = 16.dp)) {
            Text("Enable horizontal scroll", modifier = Modifier.weight(1f))
            Switch(checked = settings.enableHorizontalScroll, onCheckedChange = {
                scope.launch { SettingsManager.setEnableHorizontalScroll(context, it) }
            })
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("Invert horizontal scroll", modifier = Modifier.weight(1f))
            Switch(checked = settings.invertHorizontalScroll, onCheckedChange = {
                scope.launch { SettingsManager.setInvertHorizontalScroll(context, it) }
            })
        }
    }

    Row(Modifier.padding(top = 16.dp)) {
        Text("Enable three-finger middle-click", modifier = Modifier.weight(1f))
        Switch(checked = settings.enableMiddleClick, onCheckedChange = {
            scope.launch { SettingsManager.setEnableMiddleClick(context, it) }
        })
    }
}

@Composable
private fun InputBehaviorSection(
    settings: Settings,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Text("Key repeat delay: ${settings.keyRepeatDelayMs} ms", modifier = Modifier.padding(top = 16.dp))
    Slider(value = settings.keyRepeatDelayMs.toFloat(), onValueChange = {
        scope.launch {
            SettingsManager.setKeyRepeatDelay(
                context,
                it.toInt().coerceIn(KEY_REPEAT_MIN_MS, KEY_REPEAT_MAX_MS),
            )
        }
    }, valueRange = 150f..1000f)

    Row(Modifier.padding(top = 16.dp)) {
        Text("Key click sound", modifier = Modifier.weight(1f))
        Switch(checked = settings.clickSound, onCheckedChange = {
            scope.launch { SettingsManager.setClickSound(context, it) }
        })
    }

    Row(Modifier.padding(top = 16.dp)) {
        Text("Enable debug logging", modifier = Modifier.weight(1f))
        Switch(checked = settings.debugLogging, onCheckedChange = {
            scope.launch { SettingsManager.setDebugLogging(context, it) }
        })
    }
    Row(Modifier.padding(top = 8.dp)) {
        Text("Offline preview (use keyboard/mouse without Bluetooth)", modifier = Modifier.weight(1f))
        Switch(checked = settings.offlinePreview, onCheckedChange = {
            scope.launch { SettingsManager.setOfflinePreview(context, it) }
        })
    }
    if (settings.debugLogging) {
        Row(Modifier.padding(top = 8.dp)) {
            Text("Log level:", modifier = Modifier.padding(end = 8.dp))
            val sel = settings.logLevel
            Button(onClick = {
                scope.launch {
                    SettingsManager.setLogLevel(context, 0)
                }
            }, modifier = Modifier.padding(end = 4.dp), enabled = sel != 0) { Text("All") }
            Button(onClick = {
                scope.launch {
                    SettingsManager.setLogLevel(context, 1)
                }
            }, modifier = Modifier.padding(end = 4.dp), enabled = sel != 1) { Text("Info") }
            Button(
                onClick = { scope.launch { SettingsManager.setLogLevel(context, 2) } },
                enabled = sel != 2,
            ) { Text("Error") }
        }
    }
    Row(Modifier.padding(top = 16.dp)) {
        Text("Start service on boot", modifier = Modifier.weight(1f))
        Switch(checked = settings.startOnBoot, onCheckedChange = {
            scope.launch { SettingsManager.setStartOnBoot(context, it) }
        })
    }
}

@Composable
private fun BackgroundReliabilitySection(context: android.content.Context) {
    // Background reliability — only offered while the app is still battery-optimized.
    Text(
        "Background reliability",
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Disable battery optimization")
            Text(
                "Let Android keep the Bluetooth HID service running so the connection isn't dropped " +
                    "when the screen is off or the app is in the background.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(onClick = {
            requestIgnoreBatteryOptimizations(context)
        }, modifier = Modifier.padding(start = 8.dp)) { Text("Disable") }
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
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(Modifier.padding(top = 4.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text("Use simplified HID descriptor (Windows)")
            Text(
                "Enable if your Windows host shows a \"Driver error\" after pairing.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = settings.hidSimplified, onCheckedChange = {
            scope.launch { SettingsManager.setHidSimplified(context, it) }
        })
    }
    BleHogpToggle(settings, context, scope)
}

// BLE HOGP requires BLUETOOTH_CONNECT + BLUETOOTH_ADVERTISE. Enabling persists the setting
// only after those are granted; if denied, the toggle stays off (avoids a non-advertising
// "on" state). Turning it off always persists immediately.
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
                com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(
                    com.augustusmachin.android_bt_kbmouse.store.Action.UpdateMessage(
                        "BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic.",
                    ),
                )
            }
        }
    Row(Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Use BLE HOGP (experimental)")
            Text(
                "Uses Bluetooth Low Energy instead of Classic BT. Restart the app after changing. " +
                    "May improve Windows compatibility.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun ImeOverridesSection(
    imeOverrides: Map<String, Boolean>,
    imeLabels: Map<String, String>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onOverridesChanged: (Map<String, Boolean>, Map<String, String>) -> Unit,
) {
    // IME overrides management
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
    Text("IME overrides:")
    if (imeOverrides.isEmpty()) {
        Text("(none)", modifier = Modifier.padding(top = 4.dp))
    } else {
        imeOverrides.forEach { (pkg, allowed) ->
            val label = imeLabels[pkg] ?: pkg
            Row(Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label)
                    if (label != pkg) Text("($pkg)", modifier = Modifier.padding(top = 2.dp))
                }
                Text(if (allowed) "Allowed" else "Denied", modifier = Modifier.padding(end = 8.dp))
                Button(onClick = {
                    scope.launch {
                        SettingsManager.removeImeOverride(context, pkg)
                        val updated = SettingsManager.getAllImeOverrides(context)
                        onOverridesChanged(updated, loadImeLabels(context, updated))
                    }
                }) { Text("Remove") }
            }
        }
    }
}
