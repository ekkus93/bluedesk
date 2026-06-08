package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private suspend fun loadImeLabels(
    context: android.content.Context,
    overrides: Map<String, Boolean>
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

@Composable
fun SettingsScreen(contentPadding: PaddingValues = PaddingValues(), onOpenLogs: (() -> Unit)? = null) {
    val context = LocalContext.current
    val flow = remember { SettingsManager.flow(context) }
    val settings by flow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    val scope = rememberCoroutineScope()
    var imeOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var imeLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
            .verticalScroll(scrollState)
    ) {
        Text("Touchpad sensitivity: ${"%.2f".format(settings.touchpadSensitivity)}")
        Slider(value = settings.touchpadSensitivity, onValueChange = {
            scope.launch { SettingsManager.setTouchpadSensitivity(context, it.coerceIn(0.5f, 3.0f)) }
        }, valueRange = 0.5f..3.0f)

        if (settings.hidSimplified) {
            Text(
                "Scrolling requires the full HID descriptor. Disable \"Use simplified HID descriptor\" below to enable scroll controls.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text("Scroll speed: ${"%.2f".format(settings.scrollSpeed)}", modifier = Modifier.padding(top = 16.dp))
            Slider(value = settings.scrollSpeed, onValueChange = {
                scope.launch { SettingsManager.setScrollSpeed(context, it.coerceIn(0.5f, 3.0f)) }
            }, valueRange = 0.5f..3.0f)

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

        Text("Key repeat delay: ${settings.keyRepeatDelayMs} ms", modifier = Modifier.padding(top = 16.dp))
        Slider(value = settings.keyRepeatDelayMs.toFloat(), onValueChange = {
            scope.launch { SettingsManager.setKeyRepeatDelay(context, it.toInt().coerceIn(150, 1000)) }
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
                Button(onClick = { scope.launch { SettingsManager.setLogLevel(context, 0) } }, modifier = Modifier.padding(end = 4.dp), enabled = sel != 0) { Text("All") }
                Button(onClick = { scope.launch { SettingsManager.setLogLevel(context, 1) } }, modifier = Modifier.padding(end = 4.dp), enabled = sel != 1) { Text("Info") }
                Button(onClick = { scope.launch { SettingsManager.setLogLevel(context, 2) } }, enabled = sel != 2) { Text("Error") }
            }
        }
        Row(Modifier.padding(top = 16.dp)) {
            Text("Start service on boot", modifier = Modifier.weight(1f))
            Switch(checked = settings.startOnBoot, onCheckedChange = {
                scope.launch { SettingsManager.setStartOnBoot(context, it) }
            })
        }

        // Compatibility
        Text("Compatibility", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
        Row(Modifier.padding(top = 4.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("Use simplified HID descriptor (Windows)")
                Text(
                    "Enable if your Windows host shows a \"Driver error\" after pairing.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = settings.hidSimplified, onCheckedChange = {
                scope.launch { SettingsManager.setHidSimplified(context, it) }
            })
        }
        Row(Modifier.padding(top = 8.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("Use BLE HOGP (experimental)")
                Text(
                    "Uses Bluetooth Low Energy instead of Classic BT. Restart the app after changing. May improve Windows compatibility.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = settings.useBleHogp, onCheckedChange = {
                scope.launch { SettingsManager.setUseBleHogp(context, it) }
            })
        }

        if (onOpenLogs != null) {
            Button(modifier = Modifier.padding(top = 8.dp), onClick = onOpenLogs) {
                Text("Open Logs")
            }
        }

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
                            imeOverrides = SettingsManager.getAllImeOverrides(context)
                            imeLabels = loadImeLabels(context, imeOverrides)
                        }
                    }) { Text("Remove") }
                }
            }
        }
    }
}
