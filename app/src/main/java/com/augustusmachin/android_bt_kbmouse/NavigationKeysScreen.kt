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
import com.augustusmachin.android_bt_kbmouse.store.isInputUsable

private const val COLS_PER_PAGE = 3
private const val MAX_PAGE_INDEX = 1
private const val KEY_CODE_BYTE_MASK = 0xFF
private const val ARROW_WIDTH_DP = 36f
private const val COLUMN_GAP_DP = 6f

// A cell in a navigation column's two key rows.
private sealed interface NavCell {
    object Empty : NavCell // invisible spacer (keeps grid aligned)

    data class Key(val label: String) : NavCell // normal key dispatched via labelToHid

    object ScrollLock : NavCell // special toggle (Scroll Lock)
}

// Bundles the navigation grid's interaction state + callbacks so the per-column and
// per-cell composables stay within the parameter-count limit.
private data class NavGridHandlers(
    val connected: Boolean,
    val scrollLockActive: Boolean,
    val dispatchKey: (String) -> Unit,
    val dispatchScrollLock: () -> Unit,
)

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

// Column-centric layout: each column is (modifier, key0, key1, key2), matching the
// Extended and Function tabs. Arrows form a D-pad cross over cols 1-3:
//          ↑              (Shift col, row0)
//      ←       →          (Ctrl col / CAPS col, row1)
//          ↓              (Shift col, row2)
// A 6th empty column pads the grid to a clean 2 pages of 3 so page 2 shows only the
// Alt/Meta columns (PGUP/PGDN/Scrl Lk), never the cross's → key.
private fun navGridColumns(
    ks: com.augustusmachin.android_bt_kbmouse.store.KeyboardState,
    capsLock: Boolean,
): List<NavGridCol> =
    listOf(
        NavGridCol("Ctrl", ks.ctrl, Action.ToggleCtrl, NavCell.Empty, NavCell.Key("←"), NavCell.Empty),
        NavGridCol("Shift", ks.shift, Action.ToggleShift, NavCell.Key("↑"), NavCell.Empty, NavCell.Key("↓")),
        NavGridCol("CAPS", capsLock, Action.ToggleCapsLock, NavCell.Empty, NavCell.Key("→"), NavCell.Empty),
        NavGridCol("Alt", ks.alt, Action.ToggleAlt, NavCell.Key("PGUP"), NavCell.Key("PGDN"), NavCell.Empty),
        NavGridCol("Meta", ks.gui, Action.ToggleGui, NavCell.ScrollLock, NavCell.Empty, NavCell.Empty),
        NavGridCol(null, false, null, NavCell.Empty, NavCell.Empty, NavCell.Empty),
    )

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.isInputUsable()
    val ks = appState.keyboard
    val scrollLockActive = appState.connection.scrollLock

    val colsPerPage = COLS_PER_PAGE
    val maxPage = MAX_PAGE_INDEX // 6 cols / 3 per page → 2 pages → maxPage index = 1
    val gridCols = navGridColumns(ks, appState.connection.capsLock)

    var page by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val style = rememberKeyGridStyle()

    fun dispatchKey(label: String) {
        val code = labelToHid(label) ?: return
        StoreProvider.dispatch(Action.TrackPreviewKey(label))
        if (connected) {
            StoreProvider.dispatch(Action.SendKey(code))
        } else {
            val hex = String.format(java.util.Locale.US, "0x%02X", code.toInt() and KEY_CODE_BYTE_MASK)
            StoreProvider.dispatch(Action.UpdateMessage("Preview: $label -> $hex"))
            DebugLog.log("NavigationKeys", "Preview: $label -> $hex")
            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
        }
    }

    fun dispatchScrollLock() {
        StoreProvider.dispatch(Action.TrackPreviewKey("Scrl Lk"))
        StoreProvider.dispatch(Action.ToggleScrollLock)
    }

    val handlers = NavGridHandlers(connected, scrollLockActive, ::dispatchKey, ::dispatchScrollLock)

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val arrowWidthDp = ARROW_WIDTH_DP
            val gapDp = COLUMN_GAP_DP
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
                NavPageArrow(visible = page > 0, glyph = "❮", arrowWidthDp = arrowWidthDp) { page-- }

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
                            NavGridColumn(col, style, handlers)
                        }
                    }
                }

                NavPageArrow(visible = page < maxPage, glyph = "❯", arrowWidthDp = arrowWidthDp) { page++ }
            }
        }
    }
}

@Composable
private fun NavPageArrow(
    visible: Boolean,
    glyph: String,
    arrowWidthDp: Float,
    onClick: () -> Unit,
) {
    Box(Modifier.width(arrowWidthDp.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            IconButton(onClick = onClick) {
                Text(glyph, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun NavGridColumn(
    col: NavGridCol,
    style: KeyGridStyle,
    handlers: NavGridHandlers,
) {
    // Modifier toggle button (or spacer for the padding column)
    if (col.modLabel != null && col.modAction != null) {
        KeyModifierButton(col.modLabel, col.modActive, col.modAction, handlers.connected, style)
    } else {
        Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp))
    }
    // Key rows 0–2 (three rows form the D-pad cross)
    NavKeyCell(col.key0, style, handlers)
    NavKeyCell(col.key1, style, handlers)
    NavKeyCell(col.key2, style, handlers)
}

@Composable
private fun NavKeyCell(
    cell: NavCell,
    style: KeyGridStyle,
    handlers: NavGridHandlers,
) {
    when (cell) {
        is NavCell.Empty -> Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp)) // shared key-cell height
        is NavCell.Key -> KeyCellButton(cell.label, style) { handlers.dispatchKey(cell.label) }
        is NavCell.ScrollLock ->
            Button(
                onClick = handlers.dispatchScrollLock,
                modifier = Modifier.fillMaxWidth().height(KEY_CELL_HEIGHT_DP.dp),
                colors = if (handlers.scrollLockActive) style.activeColors else style.inactiveColors,
            ) {
                ResponsiveText(
                    text = "Scrl Lk",
                    minSize = 8.sp,
                    maxSize = style.keyFontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
    }
}
