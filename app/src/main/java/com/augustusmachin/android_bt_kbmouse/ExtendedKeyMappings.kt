package com.augustusmachin.android_bt_kbmouse

private const val FUNCTION_KEY_MIN = 1
private const val FUNCTION_KEY_MAX = 12

// HID usage for F1 is 0x3A; (0x39 + n) yields F1..F12 for n in 1..12.
private const val FUNCTION_KEY_USAGE_BASE = 0x39

/**
 * Small helper to map extended key labels to HID usage bytes.
 * Extracted from ExtendedKeysScreen for unit testing.
 */
private val LABEL_TO_HID: Map<String, Byte> =
    mapOf(
        "ESC" to 0x29.toByte(),
        "TAB" to 0x2B.toByte(),
        "CAPS" to 0x39.toByte(),
        "ENTER" to 0x28.toByte(),
        "PRTSC" to 0x46.toByte(),
        "PAUSE" to 0x48.toByte(),
        "INS" to 0x49.toByte(),
        "HOME" to 0x4A.toByte(),
        "END" to 0x4D.toByte(),
        "PGUP" to 0x4B.toByte(),
        "PGDN" to 0x4E.toByte(),
        "DEL" to 0x4C.toByte(),
        "\u2190" to 0x50.toByte(),
        "\u2192" to 0x4F.toByte(),
        "\u2191" to 0x52.toByte(),
        "\u2193" to 0x51.toByte(),
    )

fun labelToHid(label: String): Byte? = LABEL_TO_HID[label] ?: functionKeyToHid(label)

private fun functionKeyToHid(label: String): Byte? {
    if (!label.startsWith("F")) return null
    val n = label.removePrefix("F").toIntOrNull() ?: return null
    return if (n in FUNCTION_KEY_MIN..FUNCTION_KEY_MAX) (FUNCTION_KEY_USAGE_BASE + n).toByte() else null
}
