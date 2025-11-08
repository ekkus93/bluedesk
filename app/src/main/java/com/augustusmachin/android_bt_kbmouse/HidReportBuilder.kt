package com.augustusmachin.android_bt_kbmouse

/** Pure helpers to build HID reports for unit testing. Not used directly by BluetoothService. */
object HidReportBuilder {
    /** Build 8-byte keyboard report: [mods][reserved=0][key1..key6] */
    fun keyboardReport(modifiers: Int, keys: Collection<Byte>): ByteArray {
        val report = ByteArray(8)
        report[0] = (modifiers and 0xFF).toByte()
        // report[1] reserved = 0
        val arr = keys.take(6).toList()
        for ((i, k) in arr.withIndex()) report[2 + i] = k
        return report
    }

    /** Clamp helper to signed 8-bit (-127..127) as HID expects for relative axes. */
    private fun clamp127(v: Int) = v.coerceIn(-127, 127).toByte()

    /** Build 5-byte mouse report: [buttons][dx][dy][wheelV][wheelH] */
    fun mouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, hWheel: Int = 0): ByteArray = byteArrayOf(
        (buttons and 0xFF).toByte(), clamp127(dx), clamp127(dy), clamp127(wheel), clamp127(hWheel)
    )

    /** Apply inversion for wheels. */
    fun wheelValue(delta: Int, invert: Boolean): Byte = clamp127(if (invert) -delta else delta)

    /** Simple Consumer Control report builder: encodes first usage ID (e.g., 0x00CD play/pause) in 2 bytes LE. */
    fun consumerControlReport(vararg usages: Int): ByteArray {
        val usage = usages.firstOrNull() ?: 0
        val lo = (usage and 0xFF).toByte()
        val hi = ((usage ushr 8) and 0xFF).toByte()
        return byteArrayOf(lo, hi)
    }
}
