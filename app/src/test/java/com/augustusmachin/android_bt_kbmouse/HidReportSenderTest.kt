package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records the (reportId, bytes) handed to the transport so we can assert exact output. */
private class FakeHidReportTransport : HidReportTransport {
    val reportIds = mutableListOf<Int>()
    val reports = mutableListOf<ByteArray>()

    override fun send(
        reportId: Int,
        report: ByteArray,
    ) {
        reportIds.add(reportId)
        reports.add(report.copyOf())
    }

    val lastReport: ByteArray get() = reports.last()
    val lastReportId: Int get() = reportIds.last()
}

private val KEYBOARD_ID = HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt()

class HidReportSenderTest {
    // ── UT-07: keyboard + modifier state ────────────────────────────────────

    @Test
    fun `setModifiers emits a keyboard report with the new modifier byte`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.setModifiers(0x02)
        assertEquals(KEYBOARD_ID, t.lastReportId)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), t.lastReport)
    }

    @Test
    fun `pressKey adds the key with the given modifiers`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.pressKey(0x04, 0x02)
        assertEquals(KEYBOARD_ID, t.lastReportId)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), t.lastReport)
    }

    @Test
    fun `releaseKey removes the key and re-emits`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.pressKey(0x04, 0x00)
        s.pressKey(0x05, 0x00)
        s.releaseKey(0x04)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00), t.lastReport)
    }

    @Test
    fun `pressKey past the cap rolls off the oldest key`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        // 0x04..0x09 fill the 6 slots; 0x0A evicts the oldest (0x04).
        listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A).forEach { s.pressKey(it, 0x00) }
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A), t.lastReport)
    }

    @Test
    fun `sendKeyPress emits key-down then key-up`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.sendKeyPress(0x04, 0x02)
        assertEquals(2, t.reports.size)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), t.reports[0])
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), t.reports[1])
    }
}
