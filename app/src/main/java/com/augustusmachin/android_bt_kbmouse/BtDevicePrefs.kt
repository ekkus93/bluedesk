package com.augustusmachin.android_bt_kbmouse

import android.content.Context

/**
 * Typed wrapper around the "bt_hid" SharedPreferences used by [BluetoothService]
 * to persist the last/default device, the connected device name (for the Quick
 * Settings tile), per-device aliases, and the most recent runtime failure.
 * Centralizes storage keys that would otherwise be duplicated as string literals.
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

    fun getConnectedName(): String? = prefs.getString(KEY_CONNECTED_NAME, null)

    /** Clear both the last device and the cached connected name in one edit. */
    fun clearLastAndConnected() {
        prefs.edit().remove(KEY_LAST_DEVICE).remove(KEY_CONNECTED_NAME).apply()
    }

    /** Mirror of the useBleHogp setting so the QS tile (no DataStore access) can read the backend. */
    fun getUseBle(): Boolean = prefs.getBoolean(KEY_USE_BLE, false)

    fun setUseBle(useBle: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BLE, useBle).apply()
    }

    /** Durable diagnostic for a correctness-significant runtime/startup failure. */
    fun setLastRuntimeFailure(message: String) {
        prefs.edit().putString(KEY_LAST_RUNTIME_FAILURE, message).apply()
    }

    fun getLastRuntimeFailure(): String? = prefs.getString(KEY_LAST_RUNTIME_FAILURE, null)

    fun clearLastRuntimeFailure() {
        prefs.edit().remove(KEY_LAST_RUNTIME_FAILURE).apply()
    }

    fun getAlias(address: String): String? = prefs.getString(aliasKey(address), null)

    fun setAlias(
        address: String,
        alias: String,
    ) {
        prefs.edit().putString(aliasKey(address), alias).apply()
    }

    fun removeAlias(address: String) {
        prefs.edit().remove(aliasKey(address)).apply()
    }

    private fun aliasKey(address: String) = "$KEY_ALIAS_PREFIX$address"

    private companion object {
        const val KEY_LAST_DEVICE = "last_device"
        const val KEY_CONNECTED_NAME = "connected_name"
        const val KEY_USE_BLE = "use_ble"
        const val KEY_LAST_RUNTIME_FAILURE = "last_runtime_failure"
        const val KEY_ALIAS_PREFIX = "alias_"
    }
}
