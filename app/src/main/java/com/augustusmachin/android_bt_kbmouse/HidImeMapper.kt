// Char→HID keycode mapping table; the numeric literals are HID usage codes.
@file:Suppress("MagicNumber")

package com.augustusmachin.android_bt_kbmouse

/**
 * Map an input character from the system IME to a HID usage code (byte) and shift modifier bits (Int).
 * Returns null if the character can't be mapped.
 */
fun charToHid(ch: Char): Pair<Byte, Int>? {
    // letters
    if (ch in 'a'..'z' || ch in 'A'..'Z') {
        val upper = ch.uppercaseChar()
        val code = (0x04 + (upper - 'A')).toByte()
        val mods = if (ch.isUpperCase()) 0x02 else 0x00
        return Pair(code, mods)
    }
    // numbers
    val numMap =
        mapOf(
            '1' to 0x1E, '2' to 0x1F, '3' to 0x20, '4' to 0x21, '5' to 0x22,
            '6' to 0x23, '7' to 0x24, '8' to 0x25, '9' to 0x26, '0' to 0x27,
        )
    val punctBase =
        mapOf(
            '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30, '\\' to 0x31,
            '`' to 0x35, ';' to 0x33, '\'' to 0x34, ',' to 0x36, '.' to 0x37, '/' to 0x38,
        )
    if (numMap.containsKey(ch)) return Pair(numMap.getValue(ch).toByte(), 0)
    if (punctBase.containsKey(ch)) return Pair(punctBase.getValue(ch).toByte(), 0)
    if (ch == ' ') return Pair(0x2C.toByte(), 0)
    if (ch == '\t') return Pair(0x2B.toByte(), 0)
    if (ch == '\n' || ch == '\r') return Pair(0x28.toByte(), 0)
    val shifted =
        mapOf(
            '!' to Pair(0x1E, 0x02), '@' to Pair(0x1F, 0x02), '#' to Pair(0x20, 0x02),
            '$' to Pair(0x21, 0x02), '%' to Pair(0x22, 0x02), '^' to Pair(0x23, 0x02),
            '&' to Pair(0x24, 0x02), '*' to Pair(0x25, 0x02), '(' to Pair(0x26, 0x02),
            ')' to Pair(0x27, 0x02), '_' to Pair(0x2D, 0x02), '+' to Pair(0x2E, 0x02),
            '{' to Pair(0x2F, 0x02), '}' to Pair(0x30, 0x02), '|' to Pair(0x31, 0x02),
            '~' to Pair(0x35, 0x02), ':' to Pair(0x33, 0x02), '"' to Pair(0x34, 0x02),
            '<' to Pair(0x36, 0x02), '>' to Pair(0x37, 0x02), '?' to Pair(0x38, 0x02),
        )
    if (shifted.containsKey(ch)) {
        val p = shifted.getValue(ch)
        return Pair(p.first.toByte(), p.second)
    }
    return null
}
