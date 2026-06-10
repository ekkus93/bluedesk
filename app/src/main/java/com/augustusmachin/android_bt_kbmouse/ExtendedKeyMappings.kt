package com.augustusmachin.android_bt_kbmouse

private const val FUNCTION_KEY_MIN = 1
private const val FUNCTION_KEY_MAX = 12

// HID usage for F1 is 0x3A; (0x39 + n) yields F1..F12 for n in 1..12.
private const val FUNCTION_KEY_USAGE_BASE = 0x39

/**
 * Small helper to map extended key labels to HID usage bytes.
 * Extracted from ExtendedKeysScreen for unit testing.
 */
fun labelToHid(label: String): Byte? =
    when (label) {
        "ESC" -> 0x29.toByte()
        "TAB" -> 0x2B.toByte()
        "CAPS" -> 0x39.toByte()
        "ENTER" -> 0x28.toByte()
        "PRTSC" -> 0x46.toByte()
        "PAUSE" -> 0x48.toByte()
        "INS" -> 0x49.toByte()
        "HOME" -> 0x4A.toByte()
        "END" -> 0x4D.toByte()
        "PGUP" -> 0x4B.toByte()
        "PGDN" -> 0x4E.toByte()
        "DEL" -> 0x4C.toByte()
        "\u2190" -> 0x50.toByte()
        "\u2192" -> 0x4F.toByte()
        "\u2191" -> 0x52.toByte()
        "\u2193" -> 0x51.toByte()
        else ->
            if (label.startsWith("F")) {
                val n = label.removePrefix("F").toIntOrNull()
                if (n != null && n in FUNCTION_KEY_MIN..FUNCTION_KEY_MAX) {
                    (FUNCTION_KEY_USAGE_BASE + n).toByte()
                } else {
                    null
                }
            } else {
                null
            }
    }
