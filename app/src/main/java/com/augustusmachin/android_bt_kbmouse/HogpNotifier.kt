package com.augustusmachin.android_bt_kbmouse

/**
 * Minimal sink for BLE HOGP input reports.
 *
 * Lets [com.augustusmachin.android_bt_kbmouse.store.BleHogpKeySender] be unit-tested
 * without a real [BleHogpService] / GATT server. [BleHogpService] is the production
 * implementation; tests provide a fake that records the emitted report bytes.
 */
interface HogpNotifier {
    fun notifyKeyboard(report: ByteArray)

    fun notifyMouse(report: ByteArray)
}
