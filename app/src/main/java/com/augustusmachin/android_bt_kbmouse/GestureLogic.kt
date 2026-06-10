package com.augustusmachin.android_bt_kbmouse

import kotlin.math.abs
import kotlin.math.roundToInt

private const val MOUSE_MOVE_MIN = -20
private const val MOUSE_MOVE_MAX = 20
private const val SCROLL_STEP_BASE_PX = 24f
private const val MIN_SCROLL_SPEED = 0.1f
private const val TAP_TIMEOUT_MS = 220
private const val THREE_FINGER_TAP = 3

/** Pure helpers mirroring MouseScreen gesture math, for unit testing. */
object GestureLogic {
    /** Translate one-finger movement by sensitivity, clamped to [-20, 20] like MouseScreen. */
    fun moveDelta(
        dxPx: Float,
        dyPx: Float,
        sensitivity: Float,
    ): Pair<Int, Int> {
        val dx = (dxPx * sensitivity).roundToInt().coerceIn(MOUSE_MOVE_MIN, MOUSE_MOVE_MAX)
        val dy = (dyPx * sensitivity).roundToInt().coerceIn(MOUSE_MOVE_MIN, MOUSE_MOVE_MAX)
        return dx to dy
    }

    data class ScrollResult(
        val verticalSteps: List<Int>,
        val horizontalSteps: List<Int>,
        val accumV: Float,
        val accumH: Float,
    )

    /**
     * Accumulate two-finger scroll deltas and emit step-wise scroll events.
     * stepPx = 24f / max(scrollSpeed, 0.1f)
     */
    fun accumulateTwoFingerScroll(
        accumV: Float,
        accumH: Float,
        dySum: Float,
        dxSum: Float,
        scrollSpeed: Float,
        invertV: Boolean,
        enableH: Boolean,
        invertH: Boolean,
    ): ScrollResult {
        val stepPx = (SCROLL_STEP_BASE_PX / scrollSpeed.coerceAtLeast(MIN_SCROLL_SPEED))
        var vAcc = accumV + dySum
        var hAcc = accumH
        val vSteps = mutableListOf<Int>()
        val hSteps = mutableListOf<Int>()
        while (abs(vAcc) >= stepPx) {
            val step = if (vAcc > 0) 1 else -1
            val send = if (invertV) -step else step
            vSteps += send
            vAcc -= stepPx * step
        }
        if (enableH) {
            hAcc += dxSum
            while (abs(hAcc) >= stepPx) {
                val step = if (hAcc > 0) 1 else -1
                val send = if (invertH) -step else step
                hSteps += send
                hAcc -= stepPx * step
            }
        }
        return ScrollResult(vSteps, hSteps, vAcc, hAcc)
    }

    enum class Tap { None, Left, Right, Middle }

    /** Return tap action given gesture summary. Mirrors MouseScreen: duration < 220ms and no move. */
    fun tapAction(
        moved: Boolean,
        durationMs: Long,
        maxPointers: Int,
        enableMiddle: Boolean,
    ): Tap {
        if (moved || durationMs >= TAP_TIMEOUT_MS) return Tap.None
        return when (maxPointers) {
            1 -> Tap.Left
            2 -> Tap.Right
            THREE_FINGER_TAP -> if (enableMiddle) Tap.Middle else Tap.None
            else -> Tap.None
        }
    }
}
