package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPermissionPlannerTest {
    @Test
    fun persistedClassic_usesClassicStartupPermissions() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = false), 31)
        assertEquals(BackendMode.CLASSIC_HID, plan.backend)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), plan.requiredPermissions)
    }

    @Test
    fun persistedClassic_excludesScan() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = false), 33)
        assertFalse(plan.requiredPermissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun persistedBle_usesBleStartupPermissions() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = true), 31)
        assertEquals(BackendMode.BLE_HOGP, plan.backend)
        assertTrue(plan.requiredPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(plan.requiredPermissions.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun persistedBle_excludesScan() {
        val plan = StartupPermissionPlanner.plan(Settings().copy(useBleHogp = true), 33)
        assertFalse(plan.requiredPermissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }
}
