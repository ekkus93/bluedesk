package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class ExtendedKeyMappingsTest {
    @Test
    fun `labelToHid maps basic keys`() {
        assertEquals(0x29.toByte(), labelToHid("ESC"))
        assertEquals(0x46.toByte(), labelToHid("PRTSC"))
        assertEquals(0x48.toByte(), labelToHid("PAUSE"))
        assertEquals(0x49.toByte(), labelToHid("INS"))
        // F1 should be 0x39 + 1 per existing mapping
        assertEquals((0x39 + 1).toByte(), labelToHid("F1"))
    }

    @Test
    fun `labelToHid negative and boundary cases`() {
        // unknown label should return null
        assertNull(labelToHid("BADKEY"))

        // case-sensitivity: the implementation expects uppercase labels
        assertNull(labelToHid("prtsc"))

        // whitespace is not trimmed by the helper
        assertNull(labelToHid(" F1 "))

        // valid boundary: F12 should map correctly
        assertEquals((0x39 + 12).toByte(), labelToHid("F12"))

        // out-of-range function keys should be null
        assertNull(labelToHid("F13"))
        assertNull(labelToHid("F0"))

        // empty string -> null
        assertNull(labelToHid(""))
    }
}
