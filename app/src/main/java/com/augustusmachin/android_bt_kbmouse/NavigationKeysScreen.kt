package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

// A cell in a navigation column's two key rows.
private sealed interface NavCell {
    object Empty : NavCell // invisible spacer (keeps grid aligned)

    data class Key(val label: String) : NavCell // normal key dispatched via labelToHid

    object ScrollLock : NavCell // special toggle (Scroll Lock)
}

// Per-column data: modifier button on top (null = spacer), then three key rows below.
// Three rows let the arrow keys form a D-pad cross across the first columns:
//   col1 .  ←  .      col2 ↑  .  ↓      col3 .  →  .
private data class NavGridCol(
    val modLabel: String?,
    val modActive: Boolean,
    val modAction: Action?,
    val key0: NavCell,
    val key1: NavCell,
    val key2: NavCell,
)

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val ks = appState.keyboard
    val scrollLockActive = appState.connection.scrollLock

    val colsPerPage = 3
    val maxPage = 1 // 6 cols / 3 per page → 2 pages → maxPage index = 1

    // Column-centric layout: each column is (modifier, key0, key1, key2), matching
    // the Extended and Function tabs. Arrows form a D-pad cross over cols 1-3:
    //          ↑              (Shift col, row0)
    //      ←       →          (Ctrl col / CAPS col, row1)
    //          ↓              (Shift col, row2)
    // A 6th empty column pads the grid to a clean 2 pages of 3 so page 2 shows
    // only the Alt/Meta columns (PGUP/PGDN/Scrl Lk), never the cross's → key.
    val gridCols =
        listOf(
            NavGridCol("Ctrl", ks.ctrl, Action.ToggleCtrl, NavCell.Empty, NavCell.Key("←"), NavCell.Empty),
            NavGridCol("Shift", ks.shift, Action.ToggleShift, NavCell.Key("↑"), NavCell.Empty, NavCell.Key("↓")),
            NavGridCol("CAPS", appState.connection.capsLock, Action.ToggleCapsLock, NavCell.Empty, NavCell.Key("→"), NavCell.Empty),
            NavGridCol("Alt", ks.alt, Action.ToggleAlt, NavCell.Key("PGUP"), NavCell.Key("PGDN"), NavCell.Empty),
            NavGridCol("Meta", ks.gui, Action.ToggleGui, NavCell.ScrollLock, NavCell.Empty, NavCell.Empty),
            NavGridCol(null, false, null, NavCell.Empty, NavCell.Empty, NavCell.Empty),
        )

    var page by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    fun dispatchKey(label: String) {
        val code = labelToHid(label) ?: return
        StoreProvider.dispatch(Action.TrackPreviewKey(label))
        if (connected) {
            StoreProvider.dispatch(Action.SendKey(code))
        } else {
            val hex = String.format(java.util.Locale.US, "0x%02X", code.toInt() and 0xFF)
            StoreProvider.dispatch(Action.UpdateMessage("Preview: $label -> $hex"))
            DebugLog.log("NavigationKeys", "Preview: $label -> $hex")
            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
        }
    }

    fun dispatchScrollLock() {
        StoreProvider.dispatch(Action.TrackPreviewKey("Scrl Lk"))
        StoreProvider.dispatch(Action.ToggleScrollLock)
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

    @Composable
    fun KeyCell(cell: NavCell) {
        when (cell) {
            is NavCell.Empty -> Spacer(Modifier.height(48.dp)) // match button height
            is NavCell.Key ->
                Button(
                    onClick = { dispatchKey(cell.label) },
                    enabled = labelToHid(cell.label) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ResponsiveText(text = cell.label, minSize = keyFontSize, maxSize = keyFontSize)
                }
            is NavCell.ScrollLock ->
                Button(
                    onClick = ::dispatchScrollLock,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (scrollLockActive) activeColors else inactiveColors,
                ) {
                    ResponsiveText(
                        text = "Scrl Lk",
                        minSize = 8.sp,
                        maxSize = keyFontSize,
                        fontWeight = FontWeight.Bold,
                    )
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                            // Modifier toggle button (or spacer for the padding column)
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
                            // Key rows 0–2 (three rows form the D-pad cross)
                            KeyCell(col.key0)
                            KeyCell(col.key1)
                            KeyCell(col.key2)
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
    }
}
