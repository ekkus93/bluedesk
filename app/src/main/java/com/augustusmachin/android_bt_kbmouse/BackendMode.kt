package com.augustusmachin.android_bt_kbmouse

enum class BackendMode { CLASSIC_HID, BLE_HOGP }

object BackendSelector {
    fun fromSettings(useBleHogp: Boolean): BackendMode =
        if (useBleHogp) BackendMode.BLE_HOGP else BackendMode.CLASSIC_HID
}
