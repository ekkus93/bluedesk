package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the post-callback startup decision. These pass only because the decision is made from the
 * actual granted set, not the partial `RequestMultiplePermissions` callback map: a callback that
 * reported only ADVERTISE would have falsely failed the old `missingRequired(callbackMap, …)` check
 * when CONNECT was already granted.
 */
class StartupPermissionDecisionTest {
    private val connect = Manifest.permission.BLUETOOTH_CONNECT
    private val advertise = Manifest.permission.BLUETOOTH_ADVERTISE

    @Test
    fun blePlan_requiresConnectAndAdvertise() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = true), 31)
        assertTrue(plan.requiredPermissions.contains(connect))
        assertTrue(plan.requiredPermissions.contains(advertise))
    }

    @Test
    fun ble_fullStateGranted_startsPlannedBackend_evenWhenCallbackHadOnlyAdvertise() {
        // Connect was already granted before this round; the callback map would contain only
        // advertise=true, but the actual OS state has both.
        val plan = StartupPermissionPlan(BackendMode.BLE_HOGP, listOf(connect, advertise))
        assertEquals(
            StartupDecision.StartPlannedBackend,
            StartupPermissionDecision.decide(plan, setOf(connect, advertise)),
        )
    }

    @Test
    fun ble_startsWhenFullStateGranted() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = true), 31)
        assertEquals(
            StartupDecision.StartPlannedBackend,
            StartupPermissionDecision.decide(plan, setOf(connect, advertise)),
        )
    }

    @Test
    fun ble_fallsBackToClassicWhenAdvertiseStillMissing() {
        val plan = StartupPermissionPlan(BackendMode.BLE_HOGP, listOf(connect, advertise))
        assertEquals(
            StartupDecision.FallbackBleToClassic,
            StartupPermissionDecision.decide(plan, setOf(connect)),
        )
    }

    @Test
    fun classic_startsWhenConnectGranted() {
        val plan = StartupPermissionPlan(BackendMode.CLASSIC_HID, listOf(connect))
        assertEquals(
            StartupDecision.StartPlannedBackend,
            StartupPermissionDecision.decide(plan, setOf(connect)),
        )
    }

    @Test
    fun classic_showsDeniedWhenConnectMissing() {
        val plan = StartupPermissionPlan(BackendMode.CLASSIC_HID, listOf(connect))
        assertEquals(
            StartupDecision.ShowClassicPermissionDenied,
            StartupPermissionDecision.decide(plan, emptySet()),
        )
    }

    @Test
    fun classicPlan_doesNotRequireScan() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = false), 33)
        assertFalse(plan.requiredPermissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun bleToggle_partialCallback_succeedsWhenFullStateGranted() {
        // Mirrors SettingsScreen: only the full granted set matters, not the callback map.
        val required = PermissionPolicy.requiredForBleStartup(31)
        assertTrue(StartupPermissionDecision.allGranted(required, setOf(connect, advertise)))
    }

    @Test
    fun bleToggle_partialCallback_failsWhenFullStateMissing() {
        val required = PermissionPolicy.requiredForBleStartup(31)
        assertFalse(StartupPermissionDecision.allGranted(required, setOf(connect)))
    }
}
