package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenLogs: (() -> Unit)? = null) {
    val context = LocalContext.current
    val flow = remember { SettingsManager.flow(context) }
    val settings by flow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Touchpad sensitivity: ${"%.2f".format(settings.touchpadSensitivity)}")
        Slider(value = settings.touchpadSensitivity, onValueChange = {
            scope.launch { SettingsManager.setTouchpadSensitivity(context, it.coerceIn(0.5f, 3.0f)) }
        }, valueRange = 0.5f..3.0f)

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

        if (onOpenLogs != null) {
            Button(modifier = Modifier.padding(top = 8.dp), onClick = onOpenLogs) {
                Text("Open Logs")
            }
        }
    }
}
