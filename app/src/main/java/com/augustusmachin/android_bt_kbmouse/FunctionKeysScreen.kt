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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.isInputUsable

private const val COLS_PER_PAGE = 3
private const val MAX_PAGE_INDEX = 1
private const val KEY_CODE_BYTE_MASK = 0xFF
private const val ARROW_WIDTH_DP = 36f
private const val COLUMN_GAP_DP = 6f

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
    val connected = appState.isInputUsable()
    val ks = appState.keyboard

    val colsPerPage = COLS_PER_PAGE
    val maxPage = MAX_PAGE_INDEX // 6 cols / 3 per page

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
    val style = rememberKeyGridStyle()

    fun dispatchKey(label: String) {
        val code = labelToHid(label) ?: return
        StoreProvider.dispatch(Action.TrackPreviewKey(label))
        if (connected) {
            StoreProvider.dispatch(Action.SendKey(code))
        } else {
            val hex = String.format(java.util.Locale.US, "0x%02X", code.toInt() and KEY_CODE_BYTE_MASK)
            val msg = "Preview: $label -> $hex"
            StoreProvider.dispatch(Action.UpdateMessage(msg))
            DebugLog.log("FunctionKeys", msg)
            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val arrowWidthDp = ARROW_WIDTH_DP
            val gapDp = COLUMN_GAP_DP
            val contentWidthDp = maxWidth.value - arrowWidthDp * 2
            val btnWidthDp = (contentWidthDp - gapDp * (colsPerPage - 1)) / colsPerPage
            val colStrideDp = btnWidthDp + gapDp

            val density = LocalDensity.current

            LaunchedEffect(page, colStrideDp) {
                val targetPx = with(density) { (colStrideDp * colsPerPage * page).dp.roundToPx() }
                scrollState.animateScrollTo(targetPx)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FnPageArrow(visible = page > 0, glyph = "❮", arrowWidthDp = arrowWidthDp) { page-- }

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
                            FnGridColumn(col, connected, style, ::dispatchKey)
                        }
                    }
                }

                FnPageArrow(visible = page < maxPage, glyph = "❯", arrowWidthDp = arrowWidthDp) { page++ }
            }
        }
    }
}

@Composable
private fun FnPageArrow(
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
private fun FnGridColumn(
    col: FnGridCol,
    connected: Boolean,
    style: KeyGridStyle,
    dispatchKey: (String) -> Unit,
) {
    // Modifier toggle (or spacer for col 5)
    if (col.modLabel != null && col.modAction != null) {
        KeyModifierButton(col.modLabel, col.modActive, col.modAction, connected, style)
    } else {
        Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp))
    }
    // F1–F6
    KeyCellButton(col.fnKey0, style) { dispatchKey(col.fnKey0) }
    // F7–F12
    KeyCellButton(col.fnKey1, style) { dispatchKey(col.fnKey1) }
}
