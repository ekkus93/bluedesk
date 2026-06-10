package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class HidReportBuilderTest {
    @Test
    fun keyboardReport_basic_keys_and_modifiers() {
        val report = HidReportBuilder.keyboardReport(0x05, listOf(0x04, 0x05).map { it.toByte() })
        assertEquals(8, report.size)
        assertEquals(0x05.toByte(), report[0]) // modifiers
        assertEquals(0x00.toByte(), report[1]) // reserved
        assertEquals(0x04.toByte(), report[2])
        assertEquals(0x05.toByte(), report[3])
        // remaining keys empty
        for (i in 4..7) assertEquals(0x00.toByte(), report[i])
    }

    @Test
    fun keyboardReport_sixKeyRollover_noOverflow() {
        val keys = listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A).map { it.toByte() }
        val report = HidReportBuilder.keyboardReport(0x00, keys)
        assertEquals(8, report.size)
        // only first 6 keys fit
        val expected = byteArrayOf(0, 0, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09).map { it.toByte() }
        for (i in 0..7) assertEquals(expected[i], report[i])
    }

    @Test
    fun mediaKeys_consumerControl_usages_correctBytes() {
        // Example: 0x00CD (Play/Pause)
        val rpt = HidReportBuilder.consumerControlReport(0x00CD)
        assertArrayEquals(byteArrayOf(0xCD.toByte(), 0x00.toByte()), rpt)
        // Next usage: 0x00B5 (Scan Next Track)
        val rpt2 = HidReportBuilder.consumerControlReport(0x00B5)
        assertArrayEquals(byteArrayOf(0xB5.toByte(), 0x00.toByte()), rpt2)
    }

    @Test
    fun mouse_move_buttons_report_bytes() {
        val rpt = HidReportBuilder.mouseReport(buttons = 0x05, dx = 10, dy = -20)
        assertArrayEquals(byteArrayOf(0x05, 10, (-20).toByte(), 0x00, 0x00), rpt)
        // clamp values
        val rpt2 = HidReportBuilder.mouseReport(0x00, 500, -999)
        assertEquals(5, rpt2.size)
        assertEquals(127.toByte(), rpt2[1])
        assertEquals((-127).toByte(), rpt2[2])
    }

    @Test
    fun scroll_vertical_and_horizontal_with_invert() {
        val v = HidReportBuilder.wheelValue(3, invert = false)
        val vInv = HidReportBuilder.wheelValue(3, invert = true)
        assertEquals(3.toByte(), v)
        assertEquals((-3).toByte(), vInv)
        val rpt =
            HidReportBuilder.mouseReport(
                0,
                0,
                0,
                wheel = v.toInt(),
                hWheel = HidReportBuilder.wheelValue(-2, invert = true).toInt(),
            )
        assertEquals(0x03.toByte(), rpt[3])
        assertEquals(0x02.toByte(), rpt[4])
    }
}
