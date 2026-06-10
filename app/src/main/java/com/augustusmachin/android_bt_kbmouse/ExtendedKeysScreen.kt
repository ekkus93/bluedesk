// Grid column data classes document each field with an inline comment; keep them inline.
@file:Suppress("ktlint:standard:discouraged-comment-location")

package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

// Per-column data (mod button + two key rows)
private data class GridCol(
    val modLabel: String,
    val modActive: Boolean,
    val modAction: Action,
    val key0: String,
    val key1: String?, // null = empty spacer on row 1
)

@Composable
fun ExtendedKeysScreen() {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val ks = appState.keyboard

    val colsPerPage = 3
    val maxPage = 1 // 5 cols / 3 per page → 2 pages → maxPage index = 1

    // Column-centric layout: each entry is one vertical column of (modifier, key0, key1?)
    val gridCols =
        listOf(
            GridCol("Ctrl", ks.ctrl, Action.ToggleCtrl, "ESC", "INS"),
            GridCol("Shift", ks.shift, Action.ToggleShift, "TAB", "HOME"),
            GridCol("CAPS", appState.connection.capsLock, Action.ToggleCapsLock, "ENTER", "END"),
            GridCol("Alt", ks.alt, Action.ToggleAlt, "PRTSC", "DEL"),
            GridCol("Meta", ks.gui, Action.ToggleGui, "PAUSE", null),
        )

    var page by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val previewHistory = remember { mutableStateListOf<String>() }

    fun dispatchKey(
        label: String,
        code: Byte?,
    ) {
        if (code == null) return
        StoreProvider.dispatch(Action.TrackPreviewKey(label))
        if (connected) {
            StoreProvider.dispatch(Action.SendKey(code))
        } else {
            val hex = String.format(java.util.Locale.US, "0x%02X", code.toInt() and 0xFF)
            val msg = "Preview: $label -> $hex"
            StoreProvider.dispatch(Action.UpdateMessage(msg))
            DebugLog.log("ExtendedKeys", msg)
            previewHistory.add(0, "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $msg")
            if (previewHistory.size > 200) previewHistory.removeAt(previewHistory.lastIndex)
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

            // Scroll to the correct column when page changes
            LaunchedEffect(page, colStrideDp) {
                val targetPx = with(density) { (colStrideDp * colsPerPage * page).dp.roundToPx() }
                scrollState.animateScrollTo(targetPx)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Left arrow — always reserves space, only shown on page > 0
                Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
                    if (page > 0) {
                        IconButton(onClick = { page-- }) {
                            Text("❮", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                // Horizontal-scroll panel (touch disabled; programmatic only)
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
                            // Modifier toggle button
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
                            // Key row 0 button
                            Button(
                                onClick = { dispatchKey(col.key0, labelToHid(col.key0)) },
                                enabled = labelToHid(col.key0) != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ResponsiveText(text = col.key0, minSize = keyFontSize, maxSize = keyFontSize)
                            }
                            // Key row 1: button or invisible spacer
                            if (col.key1 != null) {
                                Button(
                                    onClick = { dispatchKey(col.key1, labelToHid(col.key1)) },
                                    enabled = labelToHid(col.key1) != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ResponsiveText(text = col.key1, minSize = keyFontSize, maxSize = keyFontSize)
                                }
                            } else {
                                Spacer(Modifier.height(48.dp)) // match button height
                            }
                        }
                    }
                }

                // Right arrow
                Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
                    if (page < maxPage) {
                        IconButton(onClick = { page++ }) {
                            Text("❯", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }

        // Preview console (offline only)
        if (!connected) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 220.dp)
                        .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Preview console", style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { previewHistory.clear() }, enabled = previewHistory.isNotEmpty()) {
                            Text("Clear")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(previewHistory) { line ->
                            Text(line, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
