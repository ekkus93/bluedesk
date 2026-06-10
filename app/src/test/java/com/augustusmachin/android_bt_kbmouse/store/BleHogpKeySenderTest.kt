package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.HogpNotifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Records the report bytes emitted to the BLE HOGP notifier so we can assert exact output. */
private class FakeHogpNotifier : HogpNotifier {
    val keyboardReports = mutableListOf<ByteArray>()
    val mouseReports = mutableListOf<ByteArray>()

    override fun notifyKeyboard(report: ByteArray) {
        keyboardReports.add(report.copyOf())
    }

    override fun notifyMouse(report: ByteArray) {
        mouseReports.add(report.copyOf())
    }

    val lastKeyboard: ByteArray get() = keyboardReports.last()
    val lastMouse: ByteArray get() = mouseReports.last()
}

class BleHogpKeySenderTest {
    // ── Phase 1 / UT-02: keyboard report logic ──────────────────────────────

    @Test
    fun `sendKeyDown sets modifier byte and adds the key`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.sendKeyDown(0x04, 0x02)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `sendKeyDown is idempotent on the same code`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.sendKeyDown(0x04, 0x02)
        s.sendKeyDown(0x04, 0x02)
        // No duplicate key: still a single 0x04 in the array.
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `keys accumulate up to the 6-key rollover cap`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        // 7 distinct codes; the 7th must be dropped by the rollover cap.
        listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A).forEach { s.sendKeyDown(it, 0x00) }
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09), fake.lastKeyboard)
        assertFalse("7th key must not appear", fake.lastKeyboard.contains(0x0A))
    }

    @Test
    fun `sendKeyUp removes the key and re-emits the report`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.sendKeyDown(0x04, 0x00)
        s.sendKeyDown(0x05, 0x00)
        s.sendKeyUp(0x04)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `setModifiers updates modifier byte and keeps pressed keys`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.sendKeyDown(0x04, 0x00)
        s.setModifiers(0x01)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
        assertEquals("only key-down + setModifiers reports emitted", 2, fake.keyboardReports.size)
    }

    // ── Phase 1 / UT-03: mouse report logic ─────────────────────────────────

    @Test
    fun `moveMouse emits a 3-byte report with current button mask`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.moveMouse(10, -5)
        assertArrayEquals(byteArrayOf(0x00, 10, -5), fake.lastMouse)
    }

    @Test
    fun `moveMouse clamps deltas to the signed-byte range`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.moveMouse(200, -200)
        assertArrayEquals(byteArrayOf(0x00, 127, -127), fake.lastMouse)
    }

    @Test
    fun `mouseButtonDown sets the mask and mouseButtonUp clears it`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.mouseButtonDown(0x01)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), fake.lastMouse)
        s.mouseButtonUp()
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), fake.lastMouse)
    }

    @Test
    fun `leftClick emits press then release`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.leftClick()
        assertEquals(2, fake.mouseReports.size)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), fake.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), fake.mouseReports[1])
    }

    @Test
    fun `rightClick emits press then release`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.rightClick()
        assertEquals(2, fake.mouseReports.size)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00), fake.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), fake.mouseReports[1])
    }

    @Test
    fun `middleClick emits press then release with mask 0x04`() {
        val fake = FakeHogpNotifier()
        val s = BleHogpKeySender(fake)
        s.middleClick()
        assertEquals(2, fake.mouseReports.size)
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x00), fake.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), fake.mouseReports[1])
    }
}
