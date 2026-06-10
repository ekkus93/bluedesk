package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MouseScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice
    val settingsFlow = remember(connected) { SettingsManager.flowForDevice(context, connected?.address) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    var dragLock by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { if (dragLock) StoreProvider.dispatch(Action.MouseButtonUp) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).navigationBarsPadding()) {
        // Touchpad area
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                    .semantics { this[SemanticsProperties.Role] = Role.Button }
                    .pointerInput(settings) {
                        awaitEachGesture {
                            awaitFirstDown() // wait for the gesture to start
                            var maxPointers = 1
                            var moved = false
                            val startTime = System.currentTimeMillis()
                            var scrollAccumV = 0f
                            var scrollAccumH = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed > maxPointers) maxPointers = pressed
                                if (pressed == 1) {
                                    event.changes.filter { it.pressed }.forEach { change ->
                                        val d = change.positionChange()
                                        if (d != Offset.Zero) {
                                            moved = true
                                            val dx = (d.x * settings.touchpadSensitivity).roundToInt().coerceIn(-20, 20)
                                            val dy = (d.y * settings.touchpadSensitivity).roundToInt().coerceIn(-20, 20)
                                            if (dx != 0 || dy != 0) StoreProvider.dispatch(Action.MoveMouse(dx, dy))
                                            change.consume()
                                        }
                                    }
                                } else if (pressed == 2) {
                                    var dySum = 0f
                                    var dxSum = 0f
                                    event.changes.filter { it.pressed }.forEach { change ->
                                        val d = change.positionChange()
                                        dySum += d.y
                                        dxSum += d.x
                                    }
                                    moved = moved || (abs(dySum) > 0.5f || abs(dxSum) > 0.5f)
                                    val stepPx = (24f / settings.scrollSpeed.coerceAtLeast(0.1f))
                                    scrollAccumV += dySum
                                    while (abs(scrollAccumV) >= stepPx) {
                                        val step = if (scrollAccumV > 0) 1 else -1
                                        val send = if (settings.invertScroll) -step else step
                                        StoreProvider.dispatch(Action.ScrollVertical(send))
                                        scrollAccumV -= stepPx * step
                                    }
                                    if (settings.enableHorizontalScroll) {
                                        scrollAccumH += dxSum
                                        while (abs(scrollAccumH) >= stepPx) {
                                            val step = if (scrollAccumH > 0) 1 else -1
                                            val send = if (settings.invertHorizontalScroll) -step else step
                                            StoreProvider.dispatch(Action.ScrollHorizontal(send))
                                            scrollAccumH -= stepPx * step
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pressed > 2) {
                                    if (event.changes.any { it.positionChange() != Offset.Zero }) moved = true
                                }
                            } while (event.changes.any { it.pressed })
                            val duration = System.currentTimeMillis() - startTime
                            if (!moved && duration < 220) {
                                if (dragLock) {
                                    dragLock = false
                                    StoreProvider.dispatch(Action.MouseButtonUp)
                                } else {
                                    when (maxPointers) {
                                        1 -> StoreProvider.dispatch(Action.LeftClick)
                                        2 -> StoreProvider.dispatch(Action.RightClick)
                                        3 -> if (settings.enableMiddleClick) StoreProvider.dispatch(Action.MiddleClick)
                                    }
                                }
                            }
                        }
                    },
        ) {
            Text(
                text =
                    "Use this area as a touchpad\n• 1-finger move/tap\n" +
                        "• 2-finger scroll/tap=right\n• 3-finger tap=middle",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Quick mouse buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val mouseBtnPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ElevatedButton(modifier = Modifier.weight(1f).height(44.dp), contentPadding = mouseBtnPadding, onClick = {
                StoreProvider.dispatch(Action.LeftClick)
            }) { Text("Left", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            ElevatedButton(
                modifier =
                    Modifier.weight(
                        1f,
                    ).height(44.dp),
                contentPadding = mouseBtnPadding,
                enabled = settings.enableMiddleClick,
                onClick = {
                    if (settings.enableMiddleClick) StoreProvider.dispatch(Action.MiddleClick)
                },
            ) { Text("Middle", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            ElevatedButton(modifier = Modifier.weight(1f).height(44.dp), contentPadding = mouseBtnPadding, onClick = {
                StoreProvider.dispatch(Action.RightClick)
            }) { Text("Right", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            ElevatedButton(
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = mouseBtnPadding,
                onClick = {
                    dragLock = !dragLock
                    if (dragLock) {
                        StoreProvider.dispatch(Action.MouseButtonDown(0x01))
                    } else {
                        StoreProvider.dispatch(Action.MouseButtonUp)
                    }
                },
                colors =
                    if (dragLock) {
                        ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        ButtonDefaults.elevatedButtonColors()
                    },
            ) { Text("Drag", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        }
    }
}
