package com.augustusmachin.android_bt_kbmouse

import android.content.Context

/**
 * Typed wrapper around the "bt_hid" SharedPreferences used by [BluetoothService]
 * to persist the last/default device, the connected device name (for the Quick
 * Settings tile), and per-device aliases. Centralizes the storage keys that were
 * previously duplicated as string literals throughout the service.
 */
class BtDevicePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("bt_hid", Context.MODE_PRIVATE)

    /** Address of the last/default device, or null if none. Null clears it. */
    fun getLastDevice(): String? = prefs.getString(KEY_LAST_DEVICE, null)
    fun setLastDevice(address: String?) {
        prefs.edit().apply {
            if (address == null) remove(KEY_LAST_DEVICE) else putString(KEY_LAST_DEVICE, address)
        }.apply()
    }

    /** Connected device address cached for the QS tile. Null clears it. */
    fun setConnectedName(address: String?) {
        prefs.edit().apply {
            if (address == null) remove(KEY_CONNECTED_NAME) else putString(KEY_CONNECTED_NAME, address)
        }.apply()
    }

    /** Clear both the last device and the cached connected name in one edit. */
    fun clearLastAndConnected() {
        prefs.edit().remove(KEY_LAST_DEVICE).remove(KEY_CONNECTED_NAME).apply()
    }

    fun getAlias(address: String): String? = prefs.getString(aliasKey(address), null)
    fun setAlias(address: String, alias: String) {
        prefs.edit().putString(aliasKey(address), alias).apply()
    }
    fun removeAlias(address: String) {
        prefs.edit().remove(aliasKey(address)).apply()
    }

    private fun aliasKey(address: String) = "$KEY_ALIAS_PREFIX$address"

    private companion object {
        const val KEY_LAST_DEVICE = "last_device"
        const val KEY_CONNECTED_NAME = "connected_name"
        const val KEY_ALIAS_PREFIX = "alias_"
    }
}
