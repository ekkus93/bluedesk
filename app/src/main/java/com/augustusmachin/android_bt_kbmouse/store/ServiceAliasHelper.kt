package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.IBluetoothService

/**
 * Small helper to expose alias read/write helpers from the bound Bluetooth service.
 * MainActivity sets the service instance when binding; tests can set a fake service too.
 */
object ServiceAliasHelper {
    @Volatile
    private var svc: IBluetoothService? = null

    fun setService(service: IBluetoothService?) {
        svc = service
    }

    fun getAlias(device: BluetoothDevice): String? = svc?.getAlias(device)

    fun setAlias(
        device: BluetoothDevice,
        alias: String,
    ) {
        svc?.setAlias(device, alias)
    }
}
