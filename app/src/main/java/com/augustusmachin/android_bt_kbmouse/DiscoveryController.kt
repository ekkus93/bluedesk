package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import java.util.concurrent.CopyOnWriteArrayList

/** Owns Classic discovery and publishes actual adapter state rather than optimistic UI intent. */
class DiscoveryController(
    private val context: Context,
    private val adapter: () -> BluetoothAdapter?,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasPermissions: (List<String>) -> Boolean = { required ->
        PermissionGrantChecker.hasAll(context, required)
    },
) {
    private val discoveredDevices = CopyOnWriteArrayList<BluetoothDevice>()

    fun startDiscovery(): Boolean {
        val currentAdapter = adapter()
        if (currentAdapter == null) return failStart("Bluetooth adapter is unavailable")
        if (!hasPermissions(PermissionPolicy.requiredForScan(sdkInt))) return failStart("Scan permission not granted")

        if (isDiscovering(currentAdapter)) {
            DebugLog.log(TAG, "cancelDiscovery (was discovering)")
            try {
                currentAdapter.cancelDiscovery()
            } catch (se: SecurityException) {
                StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                return failStart("Scan permission was revoked: ${se.message}")
            }
        }

        discoveredDevices.clear()
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(emptyList()))
        DebugLog.log(TAG, "startDiscovery")
        val started =
            try {
                currentAdapter.startDiscovery()
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "startDiscovery SecurityException: ${se.message}")
                StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                return failStart("Scan permission was revoked")
            }

        return if (started) {
            StoreProvider.dispatch(Action.UpdateIsScanning(true))
            StoreProvider.dispatch(Action.UpdateMessage("Scanning for devices…"))
            true
        } else {
            failStart("Failed to start scan")
        }
    }

    fun stopDiscovery(): Boolean {
        DebugLog.log(TAG, "stopDiscovery")
        val currentAdapter = adapter()
        if (currentAdapter == null) {
            StoreProvider.dispatch(Action.UpdateIsScanning(false))
            StoreProvider.dispatch(Action.UpdateMessage("Bluetooth adapter is unavailable"))
            return false
        }
        if (!hasPermissions(PermissionPolicy.requiredForScan(sdkInt))) {
            StoreProvider.dispatch(Action.UpdateIsScanning(false))
            StoreProvider.dispatch(Action.UpdateMessage("Scan permission not granted"))
            return false
        }
        return try {
            currentAdapter.cancelDiscovery()
            StoreProvider.dispatch(Action.UpdateIsScanning(false))
            true
        } catch (se: SecurityException) {
            DebugLog.e(TAG, "cancelDiscovery SecurityException: ${se.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            StoreProvider.dispatch(Action.UpdateIsScanning(false))
            StoreProvider.dispatch(Action.UpdateMessage("Scan permission was revoked"))
            false
        }
    }

    fun getDiscoveredDevices(): List<BluetoothDevice> = discoveredDevices.toList()

    fun getPairedDevices(): List<BluetoothDevice> {
        val required = PermissionPolicy.requiredForClassicStartup(sdkInt)
        if (!hasPermissions(required)) {
            DebugLog.e(TAG, "Bluetooth connect permission not granted; paired list unavailable")
            return emptyList()
        }
        return try {
            adapter()?.bondedDevices?.toList() ?: emptyList()
        } catch (se: SecurityException) {
            DebugLog.e(TAG, "getPairedDevices SecurityException: ${se.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            emptyList()
        }
    }

    fun onDeviceFound(device: BluetoothDevice?) {
        device ?: return
        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            DebugLog.log(TAG, "Bluetooth device discovered")
        }
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(discoveredDevices.toList()))
    }

    fun onDiscoveryFinished() {
        DebugLog.log(TAG, "discovery finished")
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    private fun isDiscovering(adapter: BluetoothAdapter): Boolean =
        try {
            adapter.isDiscovering
        } catch (se: SecurityException) {
            DebugLog.e(TAG, "isDiscovering SecurityException: ${se.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            false
        }

    private fun failStart(message: String): Boolean {
        DebugLog.e(TAG, message)
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
        StoreProvider.dispatch(Action.UpdateMessage(message))
        return false
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
