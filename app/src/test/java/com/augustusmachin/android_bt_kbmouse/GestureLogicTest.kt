package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureLogicTest {
    @Test
    fun oneFinger_move_to_dxdy() {
        // simple scale and clamp
        val (dx, dy) = GestureLogic.moveDelta(3.2f, -1.6f, sensitivity = 1.5f)
        assertEquals(5, dx) // 3.2*1.5=4.8~5
        assertEquals(-2, dy) // -1.6*1.5=-2.4~-2
        val (dx2, dy2) = GestureLogic.moveDelta(100f, -100f, sensitivity = 1.5f)
        assertEquals(20, dx2) // clamped
        assertEquals(-20, dy2) // clamped
    }

    @Test
    fun twoFinger_vertical_scroll_thresholds_and_accumulation() {
        var accV = 0f
        var accH = 0f
        val r1 =
            GestureLogic.accumulateTwoFingerScroll(
                accV,
                accH,
                dySum = 30f,
                dxSum = 0f,
                config =
                    GestureLogic.ScrollConfig(
                        scrollSpeed = 1.0f,
                        invertVertical = false,
                        enableHorizontal = false,
                        invertHorizontal = false,
                    ),
            )
        assertEquals(listOf(1), r1.verticalSteps) // 30 >= 24 => one step
        assertTrue(r1.horizontalSteps.isEmpty())
        // remaining accum approx 6
        assertTrue(r1.accumV in 5.5f..6.5f)
        // Now negative movement large enough for one -step
        val r2 =
            GestureLogic.accumulateTwoFingerScroll(
                r1.accumV,
                r1.accumH,
                dySum = -50f,
                dxSum = 0f,
                config =
                    GestureLogic.ScrollConfig(
                        scrollSpeed = 1.0f,
                        invertVertical = false,
                        enableHorizontal = false,
                        invertHorizontal = false,
                    ),
            )
        assertEquals(listOf(-1), r2.verticalSteps)
        // ~6-50 = -44 -> after one step (add 24) ~ -20 remains
        assertTrue(r2.accumV in -21f..-19f)
    }

    @Test
    fun twoFinger_horizontal_scroll_invert_behavior() {
        val r =
            GestureLogic.accumulateTwoFingerScroll(
                0f,
                0f,
                dySum = 0f,
                dxSum = 25f,
                config =
                    GestureLogic.ScrollConfig(
                        scrollSpeed = 1.0f,
                        invertVertical = false,
                        enableHorizontal = true,
                        invertHorizontal = true,
                    ),
            )
        assertEquals(emptyList<Int>(), r.verticalSteps)
        assertEquals(listOf(-1), r.horizontalSteps) // invert flips + to -
        assertTrue(r.accumH in 0.5f..1.5f)
    }

    @Test
    fun threeFinger_middleClick_detection_debounce() {
        assertEquals(
            GestureLogic.Tap.Middle,
            GestureLogic.tapAction(moved = false, durationMs = 150, maxPointers = 3, enableMiddle = true),
        )
        assertEquals(
            GestureLogic.Tap.None,
            GestureLogic.tapAction(moved = false, durationMs = 300, maxPointers = 3, enableMiddle = true),
        )
        assertEquals(
            GestureLogic.Tap.None,
            GestureLogic.tapAction(moved = true, durationMs = 150, maxPointers = 3, enableMiddle = true),
        )
        assertEquals(
            GestureLogic.Tap.None,
            GestureLogic.tapAction(moved = false, durationMs = 150, maxPointers = 3, enableMiddle = false),
        )
    }
}
