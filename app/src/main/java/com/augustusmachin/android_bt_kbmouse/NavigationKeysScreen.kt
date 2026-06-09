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

private data class NavPage(val buttons: List<NavButton>)
private data class NavButton(val label: String, val code: String? = null, val action: Action? = null, val isActive: Boolean = false)

@Composable
fun NavigationKeysScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val ks = appState.keyboard
    val scrollLockActive = appState.connection.scrollLock

    val mods = listOf(
        NavButton("Ctrl",  action = Action.ToggleCtrl, isActive = ks.ctrl),
        NavButton("Shift", action = Action.ToggleShift, isActive = ks.shift),
        NavButton("CAPS",  action = Action.ToggleCapsLock, isActive = appState.connection.capsLock),
        NavButton("Alt",   action = Action.ToggleAlt, isActive = ks.alt),
        NavButton("Meta",  action = Action.ToggleGui, isActive = ks.gui),
    )

    val pages = listOf(
        // Page 0: D-pad
        NavPage(listOf(
            NavButton("", code = null),
            NavButton("↑", code = "↑"),
            NavButton("", code = null),
            NavButton("←", code = "←"),
            NavButton("", code = null),
            NavButton("→", code = "→"),
            NavButton("", code = null),
            NavButton("↓", code = "↓"),
            NavButton("", code = null),
        )),
        // Page 1: Scrl Lk, PgUp, PgDn
        NavPage(listOf(
            NavButton("Scrl Lk", code = null, action = Action.ToggleScrollLock, isActive = scrollLockActive),
            NavButton("PGUP", code = "PGUP"),
            NavButton("PGDN", code = "PGDN"),
        ))
    )

    var page by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

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
        // Modifier strip
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
                            StoreProvider.dispatch(mod.action!!)
                            if (!connected) StoreProvider.dispatch(
                                Action.UpdateMessage("Preview: ${mod.label} toggled")
                            )
                        },
                        modifier = Modifier.width(modBtnWidthDp.dp),
                        colors = if (mod.isActive) activeColors else inactiveColors
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

        // Paged content with smooth scrolling
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val arrowWidthDp = 36f
            val gapDp = 6f
            val contentWidthDp = maxWidth.value - arrowWidthDp * 2
            val colWidthDp = (contentWidthDp - gapDp * 2) / 3
            val pageWidthDp = contentWidthDp

            LaunchedEffect(page, pageWidthDp) {
                val targetPx = with(density) { (pageWidthDp * page).dp.roundToPx() }
                scrollState.animateScrollTo(targetPx)
            }

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {

                Box(Modifier.width(arrowWidthDp.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (page > 0) {
                        IconButton(onClick = { page-- }) {
                            Text("❮", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState, enabled = false),
                    horizontalArrangement = Arrangement.spacedBy(gapDp.dp)
                ) {
                    pages.forEach { p ->
                        Column(
                            modifier = Modifier.width(pageWidthDp.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(gapDp.dp, Alignment.CenterVertically)
                        ) {
                            if (p == pages[0]) {
                                // D-pad: 3 rows of 3 columns
                                repeat(3) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(gapDp.dp)) {
                                        repeat(3) { col ->
                                            val idx = row * 3 + col
                                            val btn = p.buttons[idx]
                                            if (btn.label.isEmpty()) {
                                                Spacer(Modifier.width(colWidthDp.dp).height(48.dp))
                                            } else {
                                                Button(
                                                    onClick = { dispatchKey(btn.code!!) },
                                                    enabled = btn.code != null,
                                                    modifier = Modifier.width(colWidthDp.dp)
                                                ) {
                                                    ResponsiveText(btn.label, minSize = keyFontSize, maxSize = keyFontSize)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Page 1: 3 horizontal buttons
                                Row(
                                    modifier = Modifier.fillMaxHeight(),
                                    horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    p.buttons.forEach { btn ->
                                        Button(
                                            onClick = {
                                                if (btn.action != null) {
                                                    StoreProvider.dispatch(Action.TrackPreviewKey(btn.label))
                                                    StoreProvider.dispatch(btn.action)
                                                } else {
                                                    dispatchKey(btn.code!!)
                                                }
                                            },
                                            modifier = Modifier.width(colWidthDp.dp),
                                            colors = if (btn.isActive) activeColors else inactiveColors
                                        ) {
                                            ResponsiveText(
                                                btn.label,
                                                minSize = 8.sp,
                                                maxSize = keyFontSize,
                                                fontWeight = if (btn.action != null) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.width(arrowWidthDp.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (page < pages.size - 1) {
                        IconButton(onClick = { page++ }) {
                            Text("❯", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}
