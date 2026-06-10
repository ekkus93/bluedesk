package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns Bluetooth Classic device discovery (scanning) plus the discovered/paired device
 * lists, dispatching results to the store. Extracted from [BluetoothService] so that
 * class stays focused on the HID connection. [adapter] is read live because the service
 * resolves the adapter asynchronously. Permission checks are kept inline with the
 * guarded adapter calls so Android lint's MissingPermission stays satisfied.
 */
class DiscoveryController(
    private val context: Context,
    private val adapter: () -> BluetoothAdapter?,
) {
    private val discoveredDevices = CopyOnWriteArrayList<BluetoothDevice>()

    fun startDiscovery() {
        if (adapter()?.isDiscovering == true) {
            DebugLog.log(TAG, "cancelDiscovery (was discovering)")
            // Cancel discovery only if BLUETOOTH_SCAN is granted to avoid SecurityException
            val hasBtScan =
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
            if (hasBtScan) {
                try {
                    adapter()?.cancelDiscovery()
                } catch (se: SecurityException) {
                    DebugLog.e(TAG, "cancelDiscovery SecurityException: ${se.message}")
                }
            } else {
                DebugLog.e(TAG, "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
            }
        }
        // Clear previous results so UI refreshes immediately
        discoveredDevices.clear()
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(emptyList()))
        DebugLog.log(TAG, "startDiscovery")
        val hasBtScanStart =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasBtScanStart) {
            DebugLog.e(TAG, "BLUETOOTH_SCAN not granted; cannot start discovery")
            StoreProvider.dispatch(Action.UpdateMessage("Scan permission not granted"))
            return
        }
        val started =
            try {
                adapter()?.startDiscovery() ?: false
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "startDiscovery SecurityException: ${se.message}")
                false
            }
        if (started) {
            StoreProvider.dispatch(Action.UpdateIsScanning(true))
        } else {
            DebugLog.e(TAG, "startDiscovery returned false")
            StoreProvider.dispatch(Action.UpdateMessage("Failed to start scan"))
        }
    }

    fun stopDiscovery() {
        DebugLog.log(TAG, "stopDiscovery")
        val hasBtScan =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        if (hasBtScan) {
            try {
                adapter()?.cancelDiscovery()
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "cancelDiscovery SecurityException: ${se.message}")
            }
        } else {
            DebugLog.e(TAG, "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
        }
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    fun getDiscoveredDevices(): List<BluetoothDevice> = discoveredDevices.toList()

    fun getPairedDevices(): List<BluetoothDevice> {
        // Access to bondedDevices requires BLUETOOTH_CONNECT on newer Android; check permission
        val hasBtConnect =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        return if (hasBtConnect) {
            try {
                adapter()?.bondedDevices?.toList() ?: emptyList()
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "getPairedDevices SecurityException: ${se.message}")
                emptyList()
            }
        } else {
            DebugLog.e(TAG, "BLUETOOTH_CONNECT not granted; returning empty paired list")
            emptyList()
        }
    }

    /** Record a device from an ACTION_FOUND broadcast and publish the updated list. */
    fun onDeviceFound(device: BluetoothDevice?) {
        device ?: return
        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            DebugLog.log(TAG, "FOUND ${device.address}")
        }
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(discoveredDevices.toList()))
    }

    /** Handle an ACTION_DISCOVERY_FINISHED broadcast. */
    fun onDiscoveryFinished() {
        DebugLog.log(TAG, "discovery finished")
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
