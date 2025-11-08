package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class HidDescriptorVariantsTest {

    @Test
    fun builds_simplified_descriptor_correctly() {
        val simple = HidDescriptorVariants.SIMPLE
        val full = HidDescriptorVariants.FULL
        assertTrue(simple.size < full.size)
        // Contains keyboard usage (0x09 0x06) and mouse usage (0x09 0x02)
        assertTrue(simple.toList().windowed(2).any { it[0] == 0x09.toByte() && it[1] == 0x06.toByte() })
        assertTrue(simple.toList().windowed(2).any { it[0] == 0x09.toByte() && it[1] == 0x02.toByte() })
        // No extra media consumer page 0x0C markers in simplified (we didn't add them)
        assertFalse(simple.contains(0x0C.toByte()))
    }

    @Test
    fun builds_full_descriptor_correctly() {
        val full = HidDescriptorVariants.FULL
        // Has keyboard & mouse usages
        assertTrue(full.toList().windowed(2).any { it[0] == 0x09.toByte() && it[1] == 0x06.toByte() })
        assertTrue(full.toList().windowed(2).any { it[0] == 0x09.toByte() && it[1] == 0x02.toByte() })
        // Length reasonable (> simplified)
        assertTrue(full.size > HidDescriptorVariants.SIMPLE.size)
    }

    @Test
    fun variant_flag_selects_expected_bytes() {
        val selSimple = HidDescriptorVariants.select(true)
        val selFull = HidDescriptorVariants.select(false)
        assertArrayEquals(HidDescriptorVariants.SIMPLE, selSimple)
        assertArrayEquals(HidDescriptorVariants.FULL, selFull)
    }
}
