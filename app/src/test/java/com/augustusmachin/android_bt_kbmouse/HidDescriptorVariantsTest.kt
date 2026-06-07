package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class HidDescriptorVariantsTest {

    private fun ByteArray.hasPair(a: Byte, b: Byte) =
        toList().windowed(2).any { it[0] == a && it[1] == b }

    private fun ByteArray.hasTriple(a: Byte, b: Byte, c: Byte) =
        toList().windowed(3).any { it[0] == a && it[1] == b && it[2] == c }

    @Test
    fun simple_has_keyboard_and_mouse_usages() {
        val simple = HidDescriptorVariants.SIMPLE
        // Usage (Keyboard) = 0x09 0x06
        assertTrue(simple.hasPair(0x09, 0x06))
        // Usage (Mouse) = 0x09 0x02
        assertTrue(simple.hasPair(0x09, 0x02))
    }

    @Test
    fun full_has_keyboard_and_mouse_usages() {
        val full = HidDescriptorVariants.FULL
        assertTrue(full.hasPair(0x09, 0x06))
        assertTrue(full.hasPair(0x09, 0x02))
    }

    @Test
    fun both_have_report_id_1_for_keyboard() {
        // 0x85 0x01 = Report ID (1)
        assertTrue(HidDescriptorVariants.SIMPLE.hasPair(0x85.toByte(), 0x01))
        assertTrue(HidDescriptorVariants.FULL.hasPair(0x85.toByte(), 0x01))
    }

    @Test
    fun both_have_report_id_2_for_mouse() {
        // 0x85 0x02 = Report ID (2)
        assertTrue(HidDescriptorVariants.SIMPLE.hasPair(0x85.toByte(), 0x02))
        assertTrue(HidDescriptorVariants.FULL.hasPair(0x85.toByte(), 0x02))
    }

    @Test
    fun both_have_led_output_report() {
        // 0x91 0x02 = Output (Data, Var, Abs) — LED output
        assertTrue(HidDescriptorVariants.SIMPLE.hasPair(0x91.toByte(), 0x02))
        assertTrue(HidDescriptorVariants.FULL.hasPair(0x91.toByte(), 0x02))
    }

    @Test
    fun simple_has_no_scroll_wheel_or_consumer_page() {
        val simple = HidDescriptorVariants.SIMPLE
        // No Consumer page (0x05 0x0C)
        assertFalse(simple.hasPair(0x05, 0x0C))
        // No vertical scroll wheel usage (0x09 0x38)
        assertFalse(simple.hasPair(0x09, 0x38))
    }

    @Test
    fun full_has_scroll_wheel_and_consumer_ac_pan() {
        val full = HidDescriptorVariants.FULL
        // Vertical scroll: Usage (Wheel) = 0x09 0x38
        assertTrue(full.hasPair(0x09, 0x38))
        // Horizontal scroll: Usage Page (Consumer) = 0x05 0x0C
        assertTrue(full.hasPair(0x05, 0x0C))
        // Consumer AC Pan = 0x0A 0x38 0x02
        assertTrue(full.hasTriple(0x0A, 0x38, 0x02))
    }

    @Test
    fun full_is_larger_than_simple() {
        assertTrue(HidDescriptorVariants.SIMPLE.size < HidDescriptorVariants.FULL.size)
    }

    @Test
    fun select_returns_correct_variant() {
        assertArrayEquals(HidDescriptorVariants.SIMPLE, HidDescriptorVariants.select(true))
        assertArrayEquals(HidDescriptorVariants.FULL, HidDescriptorVariants.select(false))
    }

    @Test
    fun report_id_constants_match_descriptor_markers() {
        // The Byte constants used in sendReport must match what's in the descriptor
        assertEquals(0x01.toByte(), HidDescriptorVariants.REPORT_ID_KEYBOARD)
        assertEquals(0x02.toByte(), HidDescriptorVariants.REPORT_ID_MOUSE)
    }
}
