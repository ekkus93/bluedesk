package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.augustusmachin.android_bt_kbmouse.DebugLog
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Navigation keys", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationKeyButton(
                    label = "\u2191",
                    keyFontSize = keyFontSize,
                    connected = connected
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationKeyButton(
                        label = "\u2190",
                        keyFontSize = keyFontSize,
                        connected = connected
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    NavigationKeyButton(
                        label = "\u2192",
                        keyFontSize = keyFontSize,
                        connected = connected
                    )
                }

                NavigationKeyButton(
                    label = "\u2193",
                    keyFontSize = keyFontSize,
                    connected = connected
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.wrapContentWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationKeyButton(
                    label = "PGUP",
                    keyFontSize = keyFontSize,
                    connected = connected,
                    modifier = Modifier.width(96.dp)
                )
                NavigationKeyButton(
                    label = "PGDN",
                    keyFontSize = keyFontSize,
                    connected = connected,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    }
}

@Composable
private fun NavigationKeyButton(
    label: String,
    keyFontSize: androidx.compose.ui.unit.TextUnit,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val code = labelToHid(label)
    Button(
        onClick = {
            if (code != null) {
                if (connected) {
                    StoreProvider.dispatch(Action.SendKey(code))
                } else {
                    val hex = String.format("0x%02X", code.toInt() and 0xFF)
                    val msg = "Preview: $label -> $hex"
                    StoreProvider.dispatch(Action.UpdateMessage(msg))
                    DebugLog.log("NavigationKeys", msg)
                }
            }
        },
        enabled = code != null,
        modifier = modifier.defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
    ) {
        ResponsiveText(
            text = label,
            minSize = keyFontSize,
            maxSize = keyFontSize
        )
    }
}
