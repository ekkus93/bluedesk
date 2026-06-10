package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    // --- requiredForClassic ---

    @Test
    fun requiredForClassic_api31_includesConnectAndScan() {
        val perms = PermissionPolicy.requiredForClassic(31)
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun requiredForClassic_api30_usesLocation() {
        val perms = PermissionPolicy.requiredForClassic(30)
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    // --- requiredForBle ---

    @Test
    fun requiredForBle_api31_includesAdvertise() {
        val perms = PermissionPolicy.requiredForBle(31)
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun requiredForBle_api30_isEmpty() {
        assertTrue(PermissionPolicy.requiredForBle(30).isEmpty())
    }

    // --- startupPermissions excludes ADVERTISE and POST_NOTIFICATIONS ---

    @Test
    fun startupPermissions_api33_doesNotIncludeAdvertise() {
        val perms = PermissionPolicy.startupPermissions(33)
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun startupPermissions_api33_doesNotIncludePostNotifications() {
        val perms = PermissionPolicy.startupPermissions(33)
        assertFalse(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    // --- isClassicStartupBlocked ---

    @Test
    fun isClassicStartupBlocked_connectDenied_returnsTrue() {
        val result =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to false,
                Manifest.permission.BLUETOOTH_SCAN to true,
            )
        assertTrue(PermissionPolicy.isClassicStartupBlocked(result, 31))
    }

    @Test
    fun isClassicStartupBlocked_scanDenied_returnsTrue() {
        val result =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_SCAN to false,
            )
        assertTrue(PermissionPolicy.isClassicStartupBlocked(result, 31))
    }

    @Test
    fun isClassicStartupBlocked_onlyAdvertiseDenied_returnsFalse() {
        val result =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_SCAN to true,
                Manifest.permission.BLUETOOTH_ADVERTISE to false,
            )
        assertFalse(PermissionPolicy.isClassicStartupBlocked(result, 31))
    }

    @Test
    fun isClassicStartupBlocked_onlyNotificationsDenied_returnsFalse() {
        val result =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_SCAN to true,
                Manifest.permission.POST_NOTIFICATIONS to false,
            )
        assertFalse(PermissionPolicy.isClassicStartupBlocked(result, 33))
    }

    @Test
    fun isClassicStartupBlocked_allRequiredGranted_returnsFalse() {
        val result =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_SCAN to true,
            )
        assertFalse(PermissionPolicy.isClassicStartupBlocked(result, 31))
    }
}
