package com.augustusmachin.android_bt_kbmouse

/** Pure helpers to unit test BLE HOGP decisions. */
object BleHogpLogic {
    /** Return selected report map variant bytes length and flags summary. */
    fun selectReportMap(simplified: Boolean): Pair<Int, String> {
        val simpleLen = SIMPLE_REPORT_MAP.size
        val fullLen = FULL_REPORT_MAP.size
        return if (simplified) simpleLen to "variant=simple len=$simpleLen" else fullLen to "variant=full len=$fullLen"
    }

    /** Decide if CCCD notify should be enabled given descriptor value (0x0001 little-endian for notifications). */
    fun cccdEnabled(cccValue: ByteArray?): Boolean {
        if (cccValue == null || cccValue.size < 2) return false
        val flag = ((cccValue[1].toInt() and 0xFF) shl 8) or (cccValue[0].toInt() and 0xFF)
        return (flag and 0x0001) != 0
    }

    // Minimal placeholder maps (lengths only matter for tests) – real map lives in service.
    private val SIMPLE_REPORT_MAP = byteArrayOf(0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0xC0.toByte())
    private val FULL_REPORT_MAP =
        byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x05, 0x07, 0x19.toByte(),
            0xE0.toByte(), 0x29.toByte(), 0xE7.toByte(), 0xC0.toByte(),
        )
}
