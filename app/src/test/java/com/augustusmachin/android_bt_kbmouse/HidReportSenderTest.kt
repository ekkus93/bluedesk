package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHidReportTransport(
    private val result: HidDeliveryResult = HidDeliveryResult.Sent,
) : HidReportTransport {
    val reportIds = mutableListOf<Int>()
    val reports = mutableListOf<ByteArray>()

    override fun send(
        reportId: Int,
        report: ByteArray,
    ): HidDeliveryResult {
        reportIds.add(reportId)
        reports.add(report.copyOf())
        return result
    }

    val lastReport: ByteArray get() = reports.last()
    val lastReportId: Int get() = reportIds.last()
}

private val KEYBOARD_ID = HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt()
private val MOUSE_ID = HidDescriptorVariants.REPORT_ID_MOUSE.toInt()

class HidReportSenderTest {
    @Test
    fun `setModifiers emits a keyboard report with the new modifier byte`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        assertEquals(HidDeliveryResult.Sent, s.setModifiers(0x02))
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
        listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A).forEach { s.pressKey(it, 0x00) }
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A), t.lastReport)
    }

    @Test
    fun `sendKeyPress emits key-down then key-up without blocking sleep`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        assertEquals(HidDeliveryResult.Sent, s.sendKeyPress(0x04, 0x02))
        assertEquals(2, t.reports.size)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), t.reports[0])
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), t.reports[1])
    }

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
        assertEquals(HidDeliveryResult.Sent, s.sendScroll(3))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 3, 0x00), t.lastReport)
        assertEquals(HidDeliveryResult.Sent, s.sendScrollH(-4))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, -4), t.lastReport)
    }

    @Test
    fun `scroll is explicit unsupported under SIMPLE descriptor`() {
        val t = FakeHidReportTransport()
        val s = HidReportSender(isSimplified = { true }, transport = t)
        assertTrue(s.sendScroll(3) is HidDeliveryResult.Unsupported)
        assertTrue(s.sendScrollH(3) is HidDeliveryResult.Unsupported)
        assertEquals(0, t.reports.size)
    }

    @Test
    fun `transport failure is returned to caller`() {
        val failure = HidDeliveryResult.Failure(HidDeliveryFailureCode.REPORT_REJECTED, "rejected")
        val s = HidReportSender(isSimplified = { true }, transport = FakeHidReportTransport(failure))

        assertEquals(failure, s.sendMouseMove(1, 1))
    }
}
