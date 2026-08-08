package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice

internal data class BluetoothDeviceDisplay(
    val label: String,
    val address: String?,
)

/** Resolve permission-gated BluetoothDevice properties outside Compose with bounded fallbacks. */
internal fun resolveBluetoothDeviceDisplay(device: BluetoothDevice): BluetoothDeviceDisplay {
    val address =
        try {
            device.address
        } catch (e: SecurityException) {
            DebugLog.e("BluetoothDeviceDisplay", "Bluetooth address unavailable: ${e.message}")
            null
        }
    val name =
        try {
            device.name?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            DebugLog.e("BluetoothDeviceDisplay", "Bluetooth device name unavailable: ${e.message}")
            null
        }
    return BluetoothDeviceDisplay(
        label = name ?: address ?: "Bluetooth host",
        address = address,
    )
}
