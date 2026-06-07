package com.augustusmachin.android_bt_kbmouse

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.Action

@Composable
fun FunctionKeysScreen() {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Title only; modifier toggles are hosted by the parent container so they remain fixed across tabs
        /*
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            Text("Function keys", style = MaterialTheme.typography.titleMedium)
        }
        */
        // Function keys laid out larger for readability
        val functions = (1..12).map { "F$it" }
        // We'll render 4 per row with larger font
        for (rowStart in functions.indices step 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 4) {
                    val idx = rowStart + i
                    if (idx < functions.size) {
                        val label = functions[idx]
                        val code = labelToHid(label)
                        Button(
                            onClick = {
                                if (code != null) {
                                    StoreProvider.dispatch(Action.TrackPreviewKey(label))
                                    if (connected) {
                                        StoreProvider.dispatch(Action.SendKey(code))
                                    } else {
                                        val hex = String.format("0x%02X", code.toInt() and 0xFF)
                                        val msg = "Preview: $label -> $hex"
                                        StoreProvider.dispatch(Action.UpdateMessage(msg))
                                        DebugLog.log("FunctionKeys", msg)
                                        StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                                    }
                                }
                            },
                            enabled = code != null,
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            ResponsiveText(
                                text = label,
                                minSize = keyFontSize,
                                maxSize = keyFontSize
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Tip: F-keys are shown larger on this page for readability.", style = MaterialTheme.typography.bodySmall)
    }
}
