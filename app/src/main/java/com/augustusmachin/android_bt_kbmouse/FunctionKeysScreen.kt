// Grid column data classes document each field with an inline comment; keep them inline.
@file:Suppress("ktlint:standard:discouraged-comment-location")

package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

// Col 5 has no modifier (spacer); represented with modLabel = null
private data class FnGridCol(
    val modLabel: String?,
    val modActive: Boolean,
    val modAction: Action?,
    val fnKey0: String, // F1–F6
    val fnKey1: String, // F7–F12
)

@Composable
fun FunctionKeysScreen() {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val ks = appState.keyboard

    val colsPerPage = 3
    val maxPage = 1 // 6 cols / 3 per page

    // Column-centric: modifier + F(1–6) + F(7–12) per column
    val gridCols =
        listOf(
            FnGridCol("Ctrl", ks.ctrl, Action.ToggleCtrl, "F1", "F7"),
            FnGridCol("Shift", ks.shift, Action.ToggleShift, "F2", "F8"),
            FnGridCol("CAPS", appState.connection.capsLock, Action.ToggleCapsLock, "F3", "F9"),
            FnGridCol("Alt", ks.alt, Action.ToggleAlt, "F4", "F10"),
            FnGridCol("Meta", ks.gui, Action.ToggleGui, "F5", "F11"),
            FnGridCol(null, false, null, "F6", "F12"),
        )

    var page by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    fun dispatchKey(label: String) {
        val code = labelToHid(label) ?: return
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

    val inactiveColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val activeColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val arrowWidthDp = 36f
            val gapDp = 6f
            val contentWidthDp = maxWidth.value - arrowWidthDp * 2
            val btnWidthDp = (contentWidthDp - gapDp * (colsPerPage - 1)) / colsPerPage
            val colStrideDp = btnWidthDp + gapDp

            val density = LocalDensity.current

            LaunchedEffect(page, colStrideDp) {
                val targetPx = with(density) { (colStrideDp * colsPerPage * page).dp.roundToPx() }
                scrollState.animateScrollTo(targetPx)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
                    if (page > 0) {
                        IconButton(onClick = { page-- }) {
                            Text("❮", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState, enabled = false),
                    horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
                ) {
                    gridCols.forEach { col ->
                        Column(
                            modifier = Modifier.width(btnWidthDp.dp),
                            verticalArrangement = Arrangement.spacedBy(gapDp.dp),
                        ) {
                            // Modifier toggle (or spacer for col 5)
                            if (col.modLabel != null && col.modAction != null) {
                                Button(
                                    onClick = {
                                        StoreProvider.dispatch(Action.TrackPreviewKey(col.modLabel))
                                        StoreProvider.dispatch(col.modAction)
                                        if (!connected) {
                                            StoreProvider.dispatch(
                                                Action.UpdateMessage("Preview: ${col.modLabel} toggled"),
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (col.modActive) activeColors else inactiveColors,
                                ) {
                                    ResponsiveText(
                                        text = col.modLabel,
                                        minSize = 8.sp,
                                        maxSize = keyFontSize,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(48.dp))
                            }
                            // F1–F6
                            Button(
                                onClick = { dispatchKey(col.fnKey0) },
                                enabled = labelToHid(col.fnKey0) != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ResponsiveText(text = col.fnKey0, minSize = keyFontSize, maxSize = keyFontSize)
                            }
                            // F7–F12
                            Button(
                                onClick = { dispatchKey(col.fnKey1) },
                                enabled = labelToHid(col.fnKey1) != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ResponsiveText(text = col.fnKey1, minSize = keyFontSize, maxSize = keyFontSize)
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
