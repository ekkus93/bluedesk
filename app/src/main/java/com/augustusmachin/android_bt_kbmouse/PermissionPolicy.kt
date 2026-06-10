package com.augustusmachin.android_bt_kbmouse

import android.Manifest

private const val SDK_INT_S = 31
private const val SDK_INT_TIRAMISU = 33

/**
 * Pure helper that classifies Bluetooth/notification permissions by the operation that needs
 * them (not broad buckets). All methods are stateless and accept sdkInt for JVM-level unit
 * testing.
 *
 * Operation model (Android 12 / API 31+):
 *  - Classic HID startup needs only BLUETOOTH_CONNECT (connecting to a bonded device + acting
 *    as a HID device). It does NOT need BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, or POST_NOTIFICATIONS.
 *  - Scanning/discovery needs BLUETOOTH_SCAN (ACCESS_FINE_LOCATION below API 31).
 *  - BLE HOGP startup needs BLUETOOTH_CONNECT + BLUETOOTH_ADVERTISE (it advertises a GATT server).
 *  - POST_NOTIFICATIONS (API 33+) is optional and non-fatal.
 */
object PermissionPolicy {
    /** Permissions to request at Classic HID startup: connect only. */
    fun requiredForClassicStartup(sdkInt: Int): List<String> =
        if (sdkInt >= SDK_INT_S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    /** Permissions required to scan/discover devices. */
    fun requiredForScan(sdkInt: Int): List<String> =
        if (sdkInt >= SDK_INT_S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Permissions to request when enabling/starting BLE HOGP: connect + advertise. */
    fun requiredForBleStartup(sdkInt: Int): List<String> =
        if (sdkInt >= SDK_INT_S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyList()
        }

    /** Optional, non-fatal startup permissions (notifications on Android 13+). */
    fun optionalForStartup(sdkInt: Int): List<String> =
        if (sdkInt >= SDK_INT_TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    /** The entries of [required] that were not granted in [grants] (absent == not granted). */
    fun missingRequired(
        grants: Map<String, Boolean>,
        required: List<String>,
    ): List<String> = required.filter { grants[it] != true }
}
