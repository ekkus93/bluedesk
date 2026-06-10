package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    // ── Classic startup: connect only ───────────────────────────────────────

    @Test
    fun classicStartup_api31_isConnectOnly() {
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), PermissionPolicy.requiredForClassicStartup(31))
    }

    @Test
    fun classicStartup_api31_excludesScanAdvertiseNotifications() {
        val perms = PermissionPolicy.requiredForClassicStartup(33)
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertFalse(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun classicStartup_preApi31_needsNoRuntimePermission() {
        assertTrue(PermissionPolicy.requiredForClassicStartup(30).isEmpty())
    }

    // ── Scan: scan only ─────────────────────────────────────────────────────

    @Test
    fun scan_api31_isScanOnly() {
        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN), PermissionPolicy.requiredForScan(31))
    }

    @Test
    fun scan_api31_excludesConnectAdvertiseNotifications() {
        val perms = PermissionPolicy.requiredForScan(33)
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertFalse(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun scan_preApi31_usesFineLocation() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), PermissionPolicy.requiredForScan(30))
    }

    // ── BLE startup: connect + advertise ────────────────────────────────────

    @Test
    fun bleStartup_api31_includesConnectAndAdvertise() {
        val perms = PermissionPolicy.requiredForBleStartup(31)
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun bleStartup_preApi31_isEmpty() {
        assertTrue(PermissionPolicy.requiredForBleStartup(30).isEmpty())
    }

    // ── Notifications: optional ─────────────────────────────────────────────

    @Test
    fun optional_api33_includesPostNotifications() {
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), PermissionPolicy.optionalForStartup(33))
    }

    @Test
    fun optional_preApi33_isEmpty() {
        assertTrue(PermissionPolicy.optionalForStartup(31).isEmpty())
    }

    // ── missingRequired ─────────────────────────────────────────────────────

    @Test
    fun missingRequired_reportsUngrantedRequired() {
        val grants = mapOf(Manifest.permission.BLUETOOTH_CONNECT to false)
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForClassicStartup(31)),
        )
    }

    @Test
    fun missingRequired_emptyWhenAllGranted() {
        val grants = mapOf(Manifest.permission.BLUETOOTH_CONNECT to true)
        assertTrue(PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForClassicStartup(31)).isEmpty())
    }

    @Test
    fun classicStartup_notBlockedByNotificationDenial() {
        // Connect granted, notifications denied — Classic startup must not be blocked.
        val grants =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.POST_NOTIFICATIONS to false,
            )
        assertTrue(PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForClassicStartup(33)).isEmpty())
    }

    @Test
    fun classicStartup_notBlockedByScanDenial() {
        // Connect granted, scan denied — Classic startup must NOT be blocked (the original bug).
        val grants =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_SCAN to false,
            )
        assertTrue(PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForClassicStartup(31)).isEmpty())
    }

    // ── BLE HOGP toggle gating (Phase 4) ────────────────────────────────────

    @Test
    fun bleEnable_allowedWhenConnectAndAdvertiseGranted() {
        val grants =
            mapOf(
                Manifest.permission.BLUETOOTH_CONNECT to true,
                Manifest.permission.BLUETOOTH_ADVERTISE to true,
            )
        assertTrue(PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForBleStartup(31)).isEmpty())
    }

    @Test
    fun bleEnable_blockedWhenAdvertiseMissing() {
        val grants = mapOf(Manifest.permission.BLUETOOTH_CONNECT to true)
        assertTrue(
            PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForBleStartup(31))
                .contains(Manifest.permission.BLUETOOTH_ADVERTISE),
        )
    }

    @Test
    fun bleEnable_blockedWhenConnectMissing() {
        val grants = mapOf(Manifest.permission.BLUETOOTH_ADVERTISE to true)
        assertTrue(
            PermissionPolicy.missingRequired(grants, PermissionPolicy.requiredForBleStartup(31))
                .contains(Manifest.permission.BLUETOOTH_CONNECT),
        )
    }
}
