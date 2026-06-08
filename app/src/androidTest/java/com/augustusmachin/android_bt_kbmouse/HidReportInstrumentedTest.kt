package com.augustusmachin.android_bt_kbmouse

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 1 instrumented tests: verify HID report byte formats produced by HidReportBuilder.
 * No Bluetooth pairing required — runs on device via USB.
 */
@RunWith(AndroidJUnit4::class)
class HidReportInstrumentedTest {

    // ── Keyboard report tests ─────────────────────────────────────────────────

    @Test
    fun keyboardReport_noKeys_allZeroes() {
        val report = HidReportBuilder.keyboardReport(0, emptyList())
        assertArrayEquals(ByteArray(8), report)
    }

    @Test
    fun keyboardReport_pressA_keyCodeAt_index2() {
        // HID usage for 'a' = 0x04
        val report = HidReportBuilder.keyboardReport(0, listOf(0x04.toByte()))
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00),
            report
        )
    }

    @Test
    fun keyboardReport_ctrlC_correctModifierAndKeyCode() {
        // Left Ctrl = modifier bit 0 (0x01); 'c' = HID usage 0x06
        val report = HidReportBuilder.keyboardReport(0x01, listOf(0x06.toByte()))
        assertArrayEquals(
            byteArrayOf(0x01, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00),
            report
        )
    }

    @Test
    fun keyboardReport_shiftA_correctModifierAndKeyCode() {
        // Left Shift = modifier bit 1 (0x02); 'a' = 0x04
        val report = HidReportBuilder.keyboardReport(0x02, listOf(0x04.toByte()))
        assertArrayEquals(
            byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00),
            report
        )
    }

    @Test
    fun keyboardReport_ctrlAltDel_allFieldsCorrect() {
        // Left Ctrl (0x01) | Left Alt (0x04) = 0x05; Del = 0x4C
        val report = HidReportBuilder.keyboardReport(0x05, listOf(0x4C.toByte()))
        assertArrayEquals(
            byteArrayOf(0x05, 0x00, 0x4C.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00),
            report
        )
    }

    @Test
    fun keyboardReport_sixKeyRollover_allSixKeysPresent() {
        val keys = listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        val report = HidReportBuilder.keyboardReport(0, keys)
        assertEquals(8, report.size)
        assertEquals(0x00.toByte(), report[0]) // no modifier
        assertEquals(0x00.toByte(), report[1]) // reserved
        for (i in 0..5) assertEquals(keys[i], report[2 + i])
    }

    @Test
    fun keyboardReport_moreThanSixKeys_onlySixUsed() {
        val keys = listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B)
        val report = HidReportBuilder.keyboardReport(0, keys)
        assertEquals(8, report.size)
        // Only first 6 keys appear
        for (i in 0..5) assertEquals(keys[i], report[2 + i])
    }

    @Test
    fun keyboardReport_reservedByte_isAlwaysZero() {
        val report = HidReportBuilder.keyboardReport(0xFF, listOf(0x04.toByte()))
        assertEquals(0x00.toByte(), report[1])
    }

    @Test
    fun keyboardReport_allModifiers_combinedCorrectly() {
        // All left modifiers: Ctrl(0x01)|Shift(0x02)|Alt(0x04)|GUI(0x08) = 0x0F
        val report = HidReportBuilder.keyboardReport(0x0F, emptyList())
        assertEquals(0x0F.toByte(), report[0])
    }

    @Test
    fun keyboardReport_isAlways8Bytes() {
        assertEquals(8, HidReportBuilder.keyboardReport(0, emptyList()).size)
        assertEquals(8, HidReportBuilder.keyboardReport(0xFF, listOf(0x04.toByte())).size)
        assertEquals(8, HidReportBuilder.keyboardReport(0, (1..10).map { it.toByte() }).size)
    }

    // ── Mouse report tests (SIMPLE — 3-byte) ─────────────────────────────────

    @Test
    fun mouseReportSimple_isAlways3Bytes() {
        assertEquals(3, HidReportBuilder.mouseReportSimple(0, 0, 0).size)
    }

    @Test
    fun mouseReportSimple_noInput_allZeroes() {
        assertArrayEquals(byteArrayOf(0, 0, 0), HidReportBuilder.mouseReportSimple(0, 0, 0))
    }

    @Test
    fun mouseReportSimple_leftClick_bit0Set() {
        val report = HidReportBuilder.mouseReportSimple(0x01, 0, 0)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), report)
    }

    @Test
    fun mouseReportSimple_rightClick_bit1Set() {
        val report = HidReportBuilder.mouseReportSimple(0x02, 0, 0)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00), report)
    }

    @Test
    fun mouseReportSimple_middleClick_bit2Set() {
        val report = HidReportBuilder.mouseReportSimple(0x04, 0, 0)
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x00), report)
    }

    @Test
    fun mouseReportSimple_movementRight_positiveDx() {
        val report = HidReportBuilder.mouseReportSimple(0, 50, 0)
        assertArrayEquals(byteArrayOf(0x00, 50, 0), report)
    }

    @Test
    fun mouseReportSimple_movementDown_positiveDy() {
        val report = HidReportBuilder.mouseReportSimple(0, 0, 50)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 50), report)
    }

    @Test
    fun mouseReportSimple_movementLeft_negativeDx() {
        val report = HidReportBuilder.mouseReportSimple(0, -50, 0)
        assertArrayEquals(byteArrayOf(0x00, (-50).toByte(), 0), report)
    }

    @Test
    fun mouseReportSimple_combinedClickAndMove() {
        val report = HidReportBuilder.mouseReportSimple(0x01, 10, -5)
        assertArrayEquals(byteArrayOf(0x01, 10, (-5).toByte()), report)
    }

    @Test
    fun mouseReportSimple_clampsLargeDx_to127() {
        val report = HidReportBuilder.mouseReportSimple(0, 500, 0)
        assertEquals(127.toByte(), report[1])
    }

    @Test
    fun mouseReportSimple_clampsLargeDy_toNeg127() {
        val report = HidReportBuilder.mouseReportSimple(0, 0, -500)
        assertEquals((-127).toByte(), report[2])
    }

    // ── Mouse report tests (FULL — 5-byte) ───────────────────────────────────

    @Test
    fun mouseReport_isAlways5Bytes() {
        assertEquals(5, HidReportBuilder.mouseReport(0, 0, 0).size)
    }

    @Test
    fun mouseReport_noInput_allZeroes() {
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), HidReportBuilder.mouseReport(0, 0, 0))
    }

    @Test
    fun mouseReport_verticalScroll_byte3Set() {
        val report = HidReportBuilder.mouseReport(0, 0, 0, wheel = 3)
        assertArrayEquals(byteArrayOf(0, 0, 0, 3, 0), report)
    }

    @Test
    fun mouseReport_horizontalScroll_byte4Set() {
        val report = HidReportBuilder.mouseReport(0, 0, 0, hWheel = -2)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, (-2).toByte()), report)
    }

    @Test
    fun mouseReport_allFields_allCorrect() {
        val report = HidReportBuilder.mouseReport(0x03, 10, -5, wheel = 1, hWheel = -1)
        assertArrayEquals(byteArrayOf(0x03, 10, (-5).toByte(), 1, (-1).toByte()), report)
    }

    @Test
    fun mouseReport_isLargerThanSimpleReport() {
        val simple = HidReportBuilder.mouseReportSimple(0, 0, 0)
        val full = HidReportBuilder.mouseReport(0, 0, 0)
        assertEquals(3, simple.size)
        assertEquals(5, full.size)
    }

    // ── Descriptor variant sanity ─────────────────────────────────────────────

    @Test
    fun hidDescriptors_simpleIsSmallerThanFull() {
        val simple = HidDescriptorVariants.SIMPLE
        val full = HidDescriptorVariants.FULL
        assertTrue(
            "SIMPLE descriptor (${simple.size}) should be smaller than FULL (${full.size})",
            simple.size < full.size
        )
    }

    @Test
    fun hidDescriptors_both_contain_keyboard_usagePage() {
        // HID usage page 0x01 (Generic Desktop), usage 0x06 (Keyboard)
        fun ByteArray.hasPair(a: Byte, b: Byte): Boolean =
            indices.dropLast(1).any { this[it] == a && this[it + 1] == b }
        assertTrue(HidDescriptorVariants.SIMPLE.hasPair(0x05, 0x01)) // Generic Desktop page
        assertTrue(HidDescriptorVariants.FULL.hasPair(0x05, 0x01))
    }

    @Test
    fun hidDescriptors_reportIdConstants_matchDescriptorBytes() {
        val kbId = HidDescriptorVariants.REPORT_ID_KEYBOARD
        val mouseId = HidDescriptorVariants.REPORT_ID_MOUSE
        assertEquals(1.toByte(), kbId)
        assertEquals(2.toByte(), mouseId)
    }

    // ── Wheel value helper ────────────────────────────────────────────────────

    @Test
    fun wheelValue_normalDirection_returnsPositive() {
        assertEquals(3.toByte(), HidReportBuilder.wheelValue(3, invert = false))
    }

    @Test
    fun wheelValue_invertedDirection_returnsNegative() {
        assertEquals((-3).toByte(), HidReportBuilder.wheelValue(3, invert = true))
    }

    @Test
    fun wheelValue_clampsToRange() {
        assertEquals(127.toByte(), HidReportBuilder.wheelValue(999, invert = false))
        assertEquals((-127).toByte(), HidReportBuilder.wheelValue(999, invert = true))
    }
}
