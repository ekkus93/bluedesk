package com.augustusmachin.android_bt_kbmouse

/** Minimal result-bearing sink for BLE HOGP input reports. */
interface HogpNotifier {
    fun notifyKeyboard(report: ByteArray): HidDeliveryResult

    fun notifyMouse(report: ByteArray): HidDeliveryResult
}
