// Char→HID keycode mapping table; the numeric literals are HID usage codes.
@file:Suppress("MagicNumber")

package com.augustusmachin.android_bt_kbmouse

private const val MAX_IME_BATCH = 8
private const val MAX_IME_EDIT_OPERATIONS = 16

sealed interface ImeEditPlan {
    data object NoChange : ImeEditPlan

    /** Delete [deleteCount] characters from the remote suffix, then type [appendText]. */
    data class Apply(
        val deleteCount: Int,
        val appendText: String,
    ) : ImeEditPlan

    /** The IME buffer changed too far to replay safely as a bounded deterministic edit. */
    data class ResetRequired(val reason: String) : ImeEditPlan
}

/**
 * Plans a bounded suffix edit between two controlled IME-buffer values.
 *
 * The longest common prefix is retained. Everything after it in [previous] is deleted and
 * everything after it in [current] is typed. This covers append, delete, equal-length
 * replacement, suffix replacement, and ordinary composing-text rewrites without silently
 * dropping an update. Large/desynchronized changes are rejected rather than replaying an
 * unbounded buffer into the remote host.
 */
fun planImeEdit(
    previous: String,
    current: String,
    maxOperations: Int = MAX_IME_EDIT_OPERATIONS,
): ImeEditPlan {
    if (previous == current) return ImeEditPlan.NoChange

    var commonPrefix = 0
    val commonLimit = minOf(previous.length, current.length)
    while (commonPrefix < commonLimit && previous[commonPrefix] == current[commonPrefix]) {
        commonPrefix++
    }

    val deleteCount = previous.length - commonPrefix
    val appendText = current.substring(commonPrefix)
    val operationCount = deleteCount + appendText.length
    if (operationCount > maxOperations) {
        return ImeEditPlan.ResetRequired(
            "IME edit requires $operationCount operations; bounded limit is $maxOperations",
        )
    }
    return ImeEditPlan.Apply(deleteCount = deleteCount, appendText = appendText)
}

/**
 * Compatibility helper retained for the repeated-key regression tests and callers that only
 * want to detect a small pure append. New forwarding logic should use [planImeEdit].
 */
fun imeAppendedText(
    previous: String,
    current: String,
    maxBatch: Int = MAX_IME_BATCH,
): String? {
    val plan = planImeEdit(previous, current, maxOperations = maxBatch)
    return if (plan is ImeEditPlan.Apply && plan.deleteCount == 0 && plan.appendText.isNotEmpty()) {
        plan.appendText
    } else {
        null
    }
}

/**
 * Map an input character from the system IME to a HID usage code (byte) and shift modifier bits (Int).
 * Returns null if the character can't be mapped.
 */
fun charToHid(ch: Char): Pair<Byte, Int>? {
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
    return when {
        ch in 'a'..'z' || ch in 'A'..'Z' -> {
            val upper = ch.uppercaseChar()
            Pair((0x04 + (upper - 'A')).toByte(), if (ch.isUpperCase()) 0x02 else 0x00)
        }
        numMap.containsKey(ch) -> Pair(numMap.getValue(ch).toByte(), 0)
        punctBase.containsKey(ch) -> Pair(punctBase.getValue(ch).toByte(), 0)
        ch == ' ' -> Pair(0x2C.toByte(), 0)
        ch == '\t' -> Pair(0x2B.toByte(), 0)
        ch == '\n' || ch == '\r' -> Pair(0x28.toByte(), 0)
        shifted.containsKey(ch) -> {
            val p = shifted.getValue(ch)
            Pair(p.first.toByte(), p.second)
        }
        else -> null
    }
}
