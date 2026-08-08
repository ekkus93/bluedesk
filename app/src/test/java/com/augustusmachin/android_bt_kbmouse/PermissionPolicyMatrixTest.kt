package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyMatrixTest {
    @Test
    fun `Classic startup API 28 and 30 use install-time legacy permissions`() {
        assertTrue(PermissionPolicy.requiredForClassicStartup(28).isEmpty())
        assertTrue(PermissionPolicy.requiredForClassicStartup(30).isEmpty())
    }

    @Test
    fun `Classic startup API 31 and newer require CONNECT`() {
        val expected = listOf(Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals(expected, PermissionPolicy.requiredForClassicStartup(31))
        assertEquals(expected, PermissionPolicy.requiredForClassicStartup(34))
        assertEquals(expected, PermissionPolicy.requiredForClassicStartup(36))
    }

    @Test
    fun `BLE startup API 28 and 30 have no runtime Nearby Devices requirement`() {
        assertTrue(PermissionPolicy.requiredForBleStartup(28).isEmpty())
        assertTrue(PermissionPolicy.requiredForBleStartup(30).isEmpty())
    }

    @Test
    fun `BLE startup API 31 and newer require CONNECT and ADVERTISE`() {
        val expected =
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        assertEquals(expected, PermissionPolicy.requiredForBleStartup(31))
        assertEquals(expected, PermissionPolicy.requiredForBleStartup(34))
        assertEquals(expected, PermissionPolicy.requiredForBleStartup(36))
    }

    @Test
    fun `scan API 28 and 30 require fine location`() {
        val expected = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(expected, PermissionPolicy.requiredForScan(28))
        assertEquals(expected, PermissionPolicy.requiredForScan(30))
    }

    @Test
    fun `scan API 31 and newer require SCAN`() {
        val expected = listOf(Manifest.permission.BLUETOOTH_SCAN)
        assertEquals(expected, PermissionPolicy.requiredForScan(31))
        assertEquals(expected, PermissionPolicy.requiredForScan(34))
        assertEquals(expected, PermissionPolicy.requiredForScan(36))
    }

    @Test
    fun `selected-backend startup planner stays consistent across supported matrix`() {
        listOf(28, 30, 31, 34, 36).forEach { api ->
            assertEquals(
                PermissionPolicy.requiredForClassicStartup(api),
                StartupPermissionPlanner.plan(Settings(useBleHogp = false), api).requiredPermissions,
            )
            assertEquals(
                PermissionPolicy.requiredForBleStartup(api),
                StartupPermissionPlanner.plan(Settings(useBleHogp = true), api).requiredPermissions,
            )
        }
    }
}
