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
private val MOUSE_ID = HidDescriptorVariants.REPORT_ID_MOUSE.toInt()

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

    // ── UT-08: mouse state ──────────────────────────────────────────────────

    @Test
    fun `sendMouseMove emits a SIMPLE 3-byte report with current mask`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.sendMouseMove(10, -5)
        assertEquals(MOUSE_ID, t.lastReportId)
        assertArrayEquals(byteArrayOf(0x00, 10, -5), t.lastReport)
    }

    @Test
    fun `sendMouseMove clamps deltas to the signed-byte range`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.sendMouseMove(200, -200)
        assertArrayEquals(byteArrayOf(0x00, 127, -127), t.lastReport)
    }

    @Test
    fun `mouseButtonDown sets the mask and mouseButtonUp clears it`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.mouseButtonDown(0x01)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), t.lastReport)
        s.mouseButtonUp()
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), t.lastReport)
    }

    @Test
    fun `sendLeftClick emits press then release`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.sendLeftClick()
        assertEquals(2, t.reports.size)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), t.reports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), t.reports[1])
    }

    @Test
    fun `right and middle clicks use masks 0x02 and 0x04`() {
        val tr = FakeHidReportTransport()
        HidReportSender(isSimplified = { true }, transport = tr).sendRightClick()
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00), tr.reports[0])
        val tm = FakeHidReportTransport()
        HidReportSender(isSimplified = { true }, transport = tm).sendMiddleClick()
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x00), tm.reports[0])
    }

    @Test
    fun `FULL descriptor emits 5-byte reports and enables scroll`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { false }, transport = t)
        s.sendMouseMove(1, 2)
        assertArrayEquals(byteArrayOf(0x00, 1, 2, 0x00, 0x00), t.lastReport)
        s.sendScroll(3)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 3, 0x00), t.lastReport)
        s.sendScrollH(-4)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, -4), t.lastReport)
    }

    @Test
    fun `scroll is a no-op under the SIMPLE descriptor`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        s.sendScroll(3)
        s.sendScrollH(3)
        assertEquals(0, t.reports.size)
    }
}
