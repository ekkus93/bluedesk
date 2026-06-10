package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendTransitionPlannerTest {
    @Test
    fun classicToBle_stopsClassicBeforeStartingBle() {
        val steps = BackendTransitionPlanner.plan(BackendMode.CLASSIC_HID, BackendMode.BLE_HOGP)
        assertEquals(
            listOf(
                BackendStep.Stop(BackendMode.CLASSIC_HID),
                BackendStep.Start(BackendMode.BLE_HOGP),
            ),
            steps,
        )
    }

    @Test
    fun bleToClassic_stopsBleBeforeStartingClassic() {
        val steps = BackendTransitionPlanner.plan(BackendMode.BLE_HOGP, BackendMode.CLASSIC_HID)
        assertEquals(
            listOf(
                BackendStep.Stop(BackendMode.BLE_HOGP),
                BackendStep.Start(BackendMode.CLASSIC_HID),
            ),
            steps,
        )
    }

    @Test
    fun noOpTransition_producesNoSteps() {
        assertTrue(BackendTransitionPlanner.plan(BackendMode.CLASSIC_HID, BackendMode.CLASSIC_HID).isEmpty())
        assertTrue(BackendTransitionPlanner.plan(BackendMode.BLE_HOGP, BackendMode.BLE_HOGP).isEmpty())
    }

    @Test
    fun initialStart_whenNothingRunning_onlyStartsTarget() {
        assertEquals(
            listOf(BackendStep.Start(BackendMode.CLASSIC_HID)),
            BackendTransitionPlanner.plan(null, BackendMode.CLASSIC_HID),
        )
    }

    @Test
    fun planNeverStartsBothBackends() {
        for (current in listOf(null, BackendMode.CLASSIC_HID, BackendMode.BLE_HOGP)) {
            for (target in BackendMode.entries) {
                val starts = BackendTransitionPlanner.plan(current, target).filterIsInstance<BackendStep.Start>()
                // At most one Start, and it is always the target.
                assertTrue("at most one start", starts.size <= 1)
                starts.forEach { assertEquals(target, it.mode) }
            }
        }
    }

    @Test
    fun stopAlwaysPrecedesStart() {
        val steps = BackendTransitionPlanner.plan(BackendMode.CLASSIC_HID, BackendMode.BLE_HOGP)
        val stopIdx = steps.indexOfFirst { it is BackendStep.Stop }
        val startIdx = steps.indexOfFirst { it is BackendStep.Start }
        assertTrue("stop before start", stopIdx in 0 until startIdx)
    }
}
