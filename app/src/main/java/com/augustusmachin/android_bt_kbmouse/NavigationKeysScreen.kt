package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.DebugLog
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val arrowFontSize = (keyFontSize.value * 2f).sp
    val scrollActive = appState.connection.scrollLock
    val navKeyWidth = 120.dp
    val navKeyHeight = 56.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
    // Text("Navigation keys")

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
                    keyFontSize = arrowFontSize,
                    connected = connected
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationKeyButton(
                        label = "\u2190",
                        keyFontSize = arrowFontSize,
                        connected = connected
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    NavigationKeyButton(
                        label = "\u2192",
                        keyFontSize = arrowFontSize,
                        connected = connected
                    )
                }

                NavigationKeyButton(
                    label = "\u2193",
                    keyFontSize = arrowFontSize,
                    connected = connected
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.wrapContentWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        StoreProvider.dispatch(Action.TrackPreviewKey("Scroll Lock"))
                        StoreProvider.dispatch(Action.ToggleScrollLock)
                    },
                    modifier = Modifier
                        .width(navKeyWidth)
                        .height(navKeyHeight)
                ) {
                    ResponsiveText(
                        text = if (scrollActive) "Scroll Lock (On)" else "Scroll Lock",
                        minSize = keyFontSize,
                        maxSize = keyFontSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
                NavigationKeyButton(
                    label = "PGUP",
                    keyFontSize = keyFontSize,
                    connected = connected,
                    modifier = Modifier
                        .width(navKeyWidth)
                        .height(navKeyHeight),
                    width = navKeyWidth,
                    height = navKeyHeight
                )
                NavigationKeyButton(
                    label = "PGDN",
                    keyFontSize = keyFontSize,
                    connected = connected,
                    modifier = Modifier
                        .width(navKeyWidth)
                        .height(navKeyHeight),
                    width = navKeyWidth,
                    height = navKeyHeight
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
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 72.dp,
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
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
                    DebugLog.log("NavigationKeys", msg)
                    StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                }
            }
        },
        enabled = code != null,
        modifier = modifier.defaultMinSize(minWidth = width, minHeight = height)
    ) {
        ResponsiveText(
            text = label,
            minSize = keyFontSize,
            maxSize = keyFontSize
        )
    }
}
