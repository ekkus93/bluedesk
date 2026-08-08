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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.augustusmachin.android_bt_kbmouse.store.isInputUsable
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MOUSE_MOVE_MIN = -20
private const val MOUSE_MOVE_MAX = 20
private const val SCROLL_MOVE_THRESHOLD_PX = 0.5f
private const val SCROLL_STEP_BASE_PX = 24f
private const val MIN_SCROLL_SPEED = 0.1f
private const val TAP_TIMEOUT_MS = 220
private const val THREE_FINGER_TAP = 3

internal data class MouseFeatureAvailability(
    val middleClick: Boolean,
    val verticalScroll: Boolean,
    val horizontalScroll: Boolean,
)

internal fun mouseFeatureAvailability(
    settings: Settings,
    capabilities: BackendCapabilities,
): MouseFeatureAvailability =
    MouseFeatureAvailability(
        middleClick = settings.enableMiddleClick && capabilities.middleClick,
        verticalScroll = ScrollPolicy.verticalAvailable(settings) && capabilities.verticalScroll,
        horizontalScroll = ScrollPolicy.horizontalAvailable(settings) && capabilities.horizontalScroll,
    )

@Composable
fun MouseScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val appState by StoreProvider.asStateFlow().collectAsState()
    val inputUsable = appState.isInputUsable()
    val deviceAddress = appState.connection.connectedDeviceAddress
    val settingsFlow = remember(deviceAddress) { SettingsManager.flowForDevice(context, deviceAddress) }
    val settings by settingsFlow.collectAsState(initial = Settings())
    val capabilities =
        (appState.backend.runtime as? BackendRuntimeState.Ready)?.capabilities
            ?: BackendCapabilitySets.forMode(appState.backend.selectedBackend)
    val features = mouseFeatureAvailability(settings, capabilities)
    var dragLock by remember { mutableStateOf(false) }
    val latestDragLock by rememberUpdatedState(dragLock)

    DisposableEffect(Unit) {
        onDispose {
            if (latestDragLock) StoreProvider.dispatch(Action.MouseButtonUp)
        }
    }

    LaunchedEffect(inputUsable) {
        if (!inputUsable && dragLock) {
            StoreProvider.dispatch(Action.MouseButtonUp)
            dragLock = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).navigationBarsPadding()) {
        TouchpadArea(
            settings = settings,
            features = features,
            dragLock = dragLock,
            onDragLockChange = { dragLock = it },
            modifier = Modifier.weight(1f),
        )
        MouseButtonRow(
            features = features,
            dragLock = dragLock,
            onDragLockChange = { dragLock = it },
        )
    }
}

private class GestureState {
    var maxPointers = 1
    var moved = false
    var scrollAccumV = 0f
    var scrollAccumH = 0f
}

@Composable
private fun TouchpadArea(
    settings: Settings,
    features: MouseFeatureAvailability,
    dragLock: Boolean,
    onDragLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                .semantics { this[SemanticsProperties.Role] = Role.Button }
                .pointerInput(settings, features) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val state = GestureState()
                        val startTime = System.currentTimeMillis()
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed > state.maxPointers) state.maxPointers = pressed
                            when {
                                pressed == 1 -> handleMove(event, settings, state)
                                pressed == 2 -> handleScroll(event, settings, features, state)
                                pressed > 2 ->
                                    if (event.changes.any { it.positionChange() != Offset.Zero }) state.moved = true
                            }
                        } while (event.changes.any { it.pressed })
                        val duration = System.currentTimeMillis() - startTime
                        if (!state.moved && duration < TAP_TIMEOUT_MS) {
                            resolveTap(state.maxPointers, features, dragLock, onDragLockChange)
                        }
                    }
                },
    ) {
        Text(
            text =
                if (features.verticalScroll) {
                    "Use this area as a touchpad\n• 1-finger move/tap\n" +
                        "• 2-finger scroll/tap=right\n• 3-finger tap=middle"
                } else {
                    "Use this area as a touchpad\n• 1-finger move/tap\n" +
                        "• 2-finger tap=right\n• 3-finger tap=middle\n" +
                        "Scrolling is unavailable for the active backend/descriptor."
                },
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun handleMove(
    event: androidx.compose.ui.input.pointer.PointerEvent,
    settings: Settings,
    state: GestureState,
) {
    event.changes.filter { it.pressed }.forEach { change ->
        val d = change.positionChange()
        if (d != Offset.Zero) {
            state.moved = true
            val dx = (d.x * settings.touchpadSensitivity).roundToInt().coerceIn(MOUSE_MOVE_MIN, MOUSE_MOVE_MAX)
            val dy = (d.y * settings.touchpadSensitivity).roundToInt().coerceIn(MOUSE_MOVE_MIN, MOUSE_MOVE_MAX)
            if (dx != 0 || dy != 0) StoreProvider.dispatch(Action.MoveMouse(dx, dy))
            change.consume()
        }
    }
}

private fun handleScroll(
    event: androidx.compose.ui.input.pointer.PointerEvent,
    settings: Settings,
    features: MouseFeatureAvailability,
    state: GestureState,
) {
    var dySum = 0f
    var dxSum = 0f
    event.changes.filter { it.pressed }.forEach { change ->
        val d = change.positionChange()
        dySum += d.y
        dxSum += d.x
    }
    state.moved = state.moved || (abs(dySum) > SCROLL_MOVE_THRESHOLD_PX || abs(dxSum) > SCROLL_MOVE_THRESHOLD_PX)
    val stepPx = SCROLL_STEP_BASE_PX / settings.scrollSpeed.coerceAtLeast(MIN_SCROLL_SPEED)
    if (features.verticalScroll) {
        state.scrollAccumV += dySum
        while (abs(state.scrollAccumV) >= stepPx) {
            val step = if (state.scrollAccumV > 0) 1 else -1
            StoreProvider.dispatch(Action.ScrollVertical(if (settings.invertScroll) -step else step))
            state.scrollAccumV -= stepPx * step
        }
    }
    if (features.horizontalScroll) {
        state.scrollAccumH += dxSum
        while (abs(state.scrollAccumH) >= stepPx) {
            val step = if (state.scrollAccumH > 0) 1 else -1
            StoreProvider.dispatch(Action.ScrollHorizontal(if (settings.invertHorizontalScroll) -step else step))
            state.scrollAccumH -= stepPx * step
        }
    }
    event.changes.forEach { it.consume() }
}

private fun resolveTap(
    maxPointers: Int,
    features: MouseFeatureAvailability,
    dragLock: Boolean,
    onDragLockChange: (Boolean) -> Unit,
) {
    if (dragLock) {
        onDragLockChange(false)
        StoreProvider.dispatch(Action.MouseButtonUp)
    } else {
        when (maxPointers) {
            1 -> StoreProvider.dispatch(Action.LeftClick)
            2 -> StoreProvider.dispatch(Action.RightClick)
            THREE_FINGER_TAP -> if (features.middleClick) StoreProvider.dispatch(Action.MiddleClick)
        }
    }
}

@Composable
private fun MouseButtonRow(
    features: MouseFeatureAvailability,
    dragLock: Boolean,
    onDragLockChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val mouseBtnPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ElevatedButton(
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = mouseBtnPadding,
            onClick = { StoreProvider.dispatch(Action.LeftClick) },
        ) { Text("Left", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        ElevatedButton(
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = mouseBtnPadding,
            enabled = features.middleClick,
            onClick = { if (features.middleClick) StoreProvider.dispatch(Action.MiddleClick) },
        ) { Text("Middle", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        ElevatedButton(
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = mouseBtnPadding,
            onClick = { StoreProvider.dispatch(Action.RightClick) },
        ) { Text("Right", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        ElevatedButton(
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = mouseBtnPadding,
            onClick = {
                val newLock = !dragLock
                onDragLockChange(newLock)
                if (newLock) StoreProvider.dispatch(Action.MouseButtonDown(0x01)) else StoreProvider.dispatch(Action.MouseButtonUp)
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
