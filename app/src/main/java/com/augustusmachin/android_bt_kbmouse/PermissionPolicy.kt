package com.augustusmachin.android_bt_kbmouse

import android.Manifest

/**
 * Pure helper that classifies Bluetooth/notification permissions by category.
 * All methods are stateless and accept sdkInt to allow JVM-level unit testing.
 */
object PermissionPolicy {

    /** Permissions essential for Classic HID to function at all. */
    fun requiredForClassic(sdkInt: Int): List<String> = when {
        sdkInt >= 31 -> listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Additional permission required only for BLE HOGP advertising (API 31+). */
    fun requiredForBle(sdkInt: Int): List<String> = when {
        sdkInt >= 31 -> listOf(Manifest.permission.BLUETOOTH_ADVERTISE)
        else -> emptyList()
    }

    /**
     * Permissions to request at Classic HID startup.
     * POST_NOTIFICATIONS is intentionally excluded — it is requested separately in the UI
     * so that denial does not block the Bluetooth startup flow.
     * BLUETOOTH_ADVERTISE is excluded — it is requested only when switching to BLE mode.
     */
    fun startupPermissions(sdkInt: Int): List<String> = requiredForClassic(sdkInt)

    /**
     * Returns true if Classic HID startup is blocked because a required permission was denied.
     * Denial of scan-only or BLE-only permissions is not fatal for Classic HID.
     */
    fun isClassicStartupBlocked(result: Map<String, Boolean>, sdkInt: Int): Boolean {
        val required = requiredForClassic(sdkInt).toSet()
        return result.any { (perm, granted) -> perm in required && !granted }
    }
}
