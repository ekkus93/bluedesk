package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootStartPlannerTest {
    @Test
    fun startOnBootFalse_startsNothing() {
        assertEquals(
            BootStartDecision.StartNothing,
            BootStartPlanner.plan(
                startOnBoot = false,
                useBleHogp = false,
                hasClassicPermissions = true,
                hasBlePermissions = true,
            ),
        )
        assertEquals(
            BootStartDecision.StartNothing,
            BootStartPlanner.plan(
                startOnBoot = false,
                useBleHogp = true,
                hasClassicPermissions = true,
                hasBlePermissions = true,
            ),
        )
    }

    @Test
    fun classicSelectedWithPermissions_startsClassic() {
        assertEquals(
            BootStartDecision.StartClassic,
            BootStartPlanner.plan(
                startOnBoot = true,
                useBleHogp = false,
                hasClassicPermissions = true,
                hasBlePermissions = false,
            ),
        )
    }

    @Test
    fun bleSelectedWithPermissions_startsBle() {
        assertEquals(
            BootStartDecision.StartBle,
            BootStartPlanner.plan(
                startOnBoot = true,
                useBleHogp = true,
                hasClassicPermissions = false,
                hasBlePermissions = true,
            ),
        )
    }

    @Test
    fun classicSelectedMissingPermissions_skips() {
        val d =
            BootStartPlanner.plan(
                startOnBoot = true,
                useBleHogp = false,
                hasClassicPermissions = false,
                hasBlePermissions = true,
            )
        assertTrue(d is BootStartDecision.Skip)
    }

    @Test
    fun bleSelectedMissingPermissions_skipsAndNeverStartsClassic() {
        // hasBlePermissions=false represents either BLUETOOTH_CONNECT or BLUETOOTH_ADVERTISE missing.
        val d =
            BootStartPlanner.plan(
                startOnBoot = true,
                useBleHogp = true,
                hasClassicPermissions = true,
                hasBlePermissions = false,
            )
        assertTrue(d is BootStartDecision.Skip)
        assertNotEquals(BootStartDecision.StartClassic, d)
    }

    @Test
    fun bleSkipReasonMentionsBle() {
        val d =
            BootStartPlanner.plan(
                startOnBoot = true,
                useBleHogp = true,
                hasClassicPermissions = true,
                hasBlePermissions = false,
            ) as BootStartDecision.Skip
        assertTrue(d.reason.isNotBlank())
        assertTrue(d.reason.contains("BLE"))
    }
}
