package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

private data class NavModBtn(val label: String, val active: Boolean, val action: Action)

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val ks = appState.keyboard
    val scrollLockActive = appState.connection.scrollLock

    val mods = listOf(
        NavModBtn("Ctrl",  ks.ctrl,                     Action.ToggleCtrl),
        NavModBtn("Shift", ks.shift,                    Action.ToggleShift),
        NavModBtn("CAPS",  appState.connection.capsLock, Action.ToggleCapsLock),
        NavModBtn("Alt",   ks.alt,                      Action.ToggleAlt),
        NavModBtn("Meta",  ks.gui,                      Action.ToggleGui),
    )

    var page by remember { mutableStateOf(0) }
    val maxPage = 1

    fun dispatchKey(label: String) {
        val code = labelToHid(label) ?: return
        StoreProvider.dispatch(Action.TrackPreviewKey(label))
        if (connected) {
            StoreProvider.dispatch(Action.SendKey(code))
        } else {
            val hex = String.format("0x%02X", code.toInt() and 0xFF)
            StoreProvider.dispatch(Action.UpdateMessage("Preview: $label -> $hex"))
            DebugLog.log("NavigationKeys", "Preview: $label -> $hex")
            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
        }
    }

    fun dispatchScrollLock() {
        StoreProvider.dispatch(Action.TrackPreviewKey("Scrl Lk"))
        StoreProvider.dispatch(Action.ToggleScrollLock)
    }

    val inactiveColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val activeColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Modifier strip — all 5 always visible, no panning
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gapDp = 6f
            val modBtnWidthDp = (maxWidth.value - gapDp * (mods.size - 1)) / mods.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gapDp.dp)
            ) {
                mods.forEach { mod ->
                    Button(
                        onClick = {
                            StoreProvider.dispatch(Action.TrackPreviewKey(mod.label))
                            StoreProvider.dispatch(mod.action)
                            if (!connected) StoreProvider.dispatch(
                                Action.UpdateMessage("Preview: ${mod.label} toggled")
                            )
                        },
                        modifier = Modifier.width(modBtnWidthDp.dp),
                        colors = if (mod.active) activeColors else inactiveColors
                    ) {
                        ResponsiveText(
                            text = mod.label,
                            minSize = 8.sp,
                            maxSize = keyFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Paged content area with left/right arrows
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val arrowWidthDp = 36f
            val gapDp = 6f
            val contentWidthDp = maxWidth.value - arrowWidthDp * 2
            val cellWidthDp = (contentWidthDp - gapDp * 2) / 3

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
                    if (page > 0) {
                        IconButton(onClick = { page-- }) {
                            Text("❮", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    if (page == 0) {
                        // D-pad cross layout
                        Column(verticalArrangement = Arrangement.spacedBy(gapDp.dp)) {
                            // Row 1: [empty] [↑] [empty]
                            Row(horizontalArrangement = Arrangement.spacedBy(gapDp.dp)) {
                                Spacer(Modifier.width(cellWidthDp.dp).height(48.dp))
                                Button(
                                    onClick = { dispatchKey("↑") },
                                    modifier = Modifier.width(cellWidthDp.dp)
                                ) {
                                    ResponsiveText("↑", minSize = keyFontSize, maxSize = keyFontSize)
                                }
                                Spacer(Modifier.width(cellWidthDp.dp).height(48.dp))
                            }
                            // Row 2: [←] [empty] [→]
                            Row(horizontalArrangement = Arrangement.spacedBy(gapDp.dp)) {
                                Button(
                                    onClick = { dispatchKey("←") },
                                    modifier = Modifier.width(cellWidthDp.dp)
                                ) {
                                    ResponsiveText("←", minSize = keyFontSize, maxSize = keyFontSize)
                                }
                                Spacer(Modifier.width(cellWidthDp.dp).height(48.dp))
                                Button(
                                    onClick = { dispatchKey("→") },
                                    modifier = Modifier.width(cellWidthDp.dp)
                                ) {
                                    ResponsiveText("→", minSize = keyFontSize, maxSize = keyFontSize)
                                }
                            }
                            // Row 3: [empty] [↓] [empty]
                            Row(horizontalArrangement = Arrangement.spacedBy(gapDp.dp)) {
                                Spacer(Modifier.width(cellWidthDp.dp).height(48.dp))
                                Button(
                                    onClick = { dispatchKey("↓") },
                                    modifier = Modifier.width(cellWidthDp.dp)
                                ) {
                                    ResponsiveText("↓", minSize = keyFontSize, maxSize = keyFontSize)
                                }
                                Spacer(Modifier.width(cellWidthDp.dp).height(48.dp))
                            }
                        }
                    } else {
                        // Page 1: Scrl Lk, PgUp, PgDn in a row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gapDp.dp)
                        ) {
                            Button(
                                onClick = ::dispatchScrollLock,
                                modifier = Modifier.width(cellWidthDp.dp),
                                colors = if (scrollLockActive) activeColors else inactiveColors
                            ) {
                                ResponsiveText(
                                    text = "Scrl Lk",
                                    minSize = 8.sp,
                                    maxSize = keyFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { dispatchKey("PGUP") },
                                modifier = Modifier.width(cellWidthDp.dp)
                            ) {
                                ResponsiveText("PGUP", minSize = keyFontSize, maxSize = keyFontSize)
                            }
                            Button(
                                onClick = { dispatchKey("PGDN") },
                                modifier = Modifier.width(cellWidthDp.dp)
                            ) {
                                ResponsiveText("PGDN", minSize = keyFontSize, maxSize = keyFontSize)
                            }
                        }
                    }
                }

                Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
                    if (page < maxPage) {
                        IconButton(onClick = { page++ }) {
                            Text("❯", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}
