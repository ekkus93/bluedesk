package com.augustusmachin.android_bt_kbmouse

/**
 * Builds raw HID input report byte arrays used by BluetoothService and BleHogpKeySender.
 *
 * Reports contain NO report-ID prefix — the ID is supplied separately
 * (Classic BT: hid.sendReport reportId param; BLE HOGP: GATT Report Reference descriptor).
 *
 * HID modifier byte bit layout (USB HID spec §10):
 *   bit 0 = Left Ctrl (0x01), bit 1 = Left Shift (0x02), bit 2 = Left Alt (0x04),
 *   bit 3 = Left GUI  (0x08), bit 4 = Right Ctrl (0x10), bit 5 = Right Shift (0x20),
 *   bit 6 = Right Alt (0x40), bit 7 = Right GUI  (0x80)
 *
 * Mouse button byte bit layout: bit 0 = Left (0x01), bit 1 = Right (0x02), bit 2 = Middle (0x04)
 */
object HidReportBuilder {
    // ── Keyboard ─────────────────────────────────────────────────────────────

    /** 8-byte keyboard input report: [mods, reserved=0, key1..key6] */
    fun keyboardReport(
        modifiers: Int,
        keys: Collection<Byte>,
    ): ByteArray {
        val report = ByteArray(8)
        report[0] = (modifiers and 0xFF).toByte()
        val arr = keys.take(6).toList()
        for ((i, k) in arr.withIndex()) report[2 + i] = k
        return report
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    private fun clamp127(v: Int) = v.coerceIn(-127, 127).toByte()

    /**
     * 3-byte mouse report for the SIMPLE descriptor (no scroll wheels):
     * [buttons, dx, dy]
     */
    fun mouseReportSimple(
        buttons: Int,
        dx: Int,
        dy: Int,
    ): ByteArray =
        byteArrayOf(
            (buttons and 0xFF).toByte(),
            clamp127(dx),
            clamp127(dy),
        )

    /**
     * 5-byte mouse report for the FULL descriptor (with vertical + horizontal scroll):
     * [buttons, dx, dy, wheelV, wheelH]
     */
    fun mouseReport(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int = 0,
        hWheel: Int = 0,
    ): ByteArray =
        byteArrayOf(
            (buttons and 0xFF).toByte(),
            clamp127(dx),
            clamp127(dy),
            clamp127(wheel),
            clamp127(hWheel),
        )

    /** Apply scroll inversion. */
    fun wheelValue(
        delta: Int,
        invert: Boolean,
    ): Byte = clamp127(if (invert) -delta else delta)

    /** Consumer Control 2-byte report (little-endian usage ID). */
    fun consumerControlReport(vararg usages: Int): ByteArray {
        val usage = usages.firstOrNull() ?: 0
        return byteArrayOf((usage and 0xFF).toByte(), ((usage ushr 8) and 0xFF).toByte())
    }
}
