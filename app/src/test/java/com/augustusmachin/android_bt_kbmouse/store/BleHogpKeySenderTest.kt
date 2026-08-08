package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.HidDeliveryFailureCode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryResult
import com.augustusmachin.android_bt_kbmouse.HogpNotifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHogpNotifier(
    private val keyboardResult: HidDeliveryResult = HidDeliveryResult.Sent,
    private val mouseResult: HidDeliveryResult = HidDeliveryResult.Sent,
) : HogpNotifier {
    val keyboardReports = mutableListOf<ByteArray>()
    val mouseReports = mutableListOf<ByteArray>()

    override fun notifyKeyboard(report: ByteArray): HidDeliveryResult {
        keyboardReports.add(report.copyOf())
        return keyboardResult
    }

    override fun notifyMouse(report: ByteArray): HidDeliveryResult {
        mouseReports.add(report.copyOf())
        return mouseResult
    }

    val lastKeyboard: ByteArray get() = keyboardReports.last()
    val lastMouse: ByteArray get() = mouseReports.last()
}

class BleHogpKeySenderTest {
    @Test
    fun `sendKeyDown sets modifier byte and adds the key`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        assertEquals(CommandResult.Success, sender.sendKeyDown(0x04, 0x02))
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `sendKeyDown is idempotent on the same code`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.sendKeyDown(0x04, 0x02)
        sender.sendKeyDown(0x04, 0x02)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `keys accumulate only to the 6-key rollover cap`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A).forEach { sender.sendKeyDown(it, 0x00) }
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09), fake.lastKeyboard)
        assertFalse(fake.lastKeyboard.contains(0x0A))
    }

    @Test
    fun `sendKeyUp removes the key and re-emits the report`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.sendKeyDown(0x04, 0x00)
        sender.sendKeyDown(0x05, 0x00)
        sender.sendKeyUp(0x04)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
    }

    @Test
    fun `setModifiers updates modifier byte and keeps pressed keys`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.sendKeyDown(0x04, 0x00)
        sender.setModifiers(0x01)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00), fake.lastKeyboard)
        assertEquals(2, fake.keyboardReports.size)
    }

    @Test
    fun `moveMouse emits a simple 3-byte report`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        assertEquals(CommandResult.Success, sender.moveMouse(10, -5))
        assertArrayEquals(byteArrayOf(0x00, 10, -5), fake.lastMouse)
    }

    @Test
    fun `moveMouse clamps deltas to signed-byte range`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.moveMouse(200, -200)
        assertArrayEquals(byteArrayOf(0x00, 127, -127), fake.lastMouse)
    }

    @Test
    fun `mouseButtonDown sets mask and mouseButtonUp clears it`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.mouseButtonDown(0x01)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), fake.lastMouse)
        sender.mouseButtonUp()
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), fake.lastMouse)
    }

    @Test
    fun `click methods emit press then release`() {
        val left = FakeHogpNotifier()
        BleHogpKeySender(left).leftClick()
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00), left.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), left.mouseReports[1])

        val right = FakeHogpNotifier()
        BleHogpKeySender(right).rightClick()
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x00), right.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), right.mouseReports[1])

        val middle = FakeHogpNotifier()
        BleHogpKeySender(middle).middleClick()
        assertArrayEquals(byteArrayOf(0x04, 0x00, 0x00), middle.mouseReports[0])
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00), middle.mouseReports[1])
    }

    @Test
    fun `lock toggle preserves current modifier byte`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)
        sender.setModifiers(0x02)
        sender.toggleCapsLock()
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x39, 0x00, 0x00, 0x00, 0x00, 0x00), fake.keyboardReports[1])
    }

    @Test
    fun `host-initiated operations return unsupported and emit no report`() {
        val fake = FakeHogpNotifier()
        val sender = BleHogpKeySender(fake)

        assertTrue(sender.startDiscovery() is CommandResult.Unsupported)
        assertTrue(sender.stopDiscovery() is CommandResult.Unsupported)
        assertTrue(sender.disconnectDevice() is CommandResult.Unsupported)
        assertTrue(sender.scrollVertical(5) is CommandResult.Unsupported)
        assertTrue(sender.scrollHorizontal(-5) is CommandResult.Unsupported)
        assertEquals(0, fake.keyboardReports.size)
        assertEquals(0, fake.mouseReports.size)
    }

    @Test
    fun `notifier delivery failure propagates to command failure`() {
        val fake =
            FakeHogpNotifier(
                mouseResult =
                    HidDeliveryResult.Failure(
                        HidDeliveryFailureCode.REPORT_REJECTED,
                        "BLE notification rejected",
                    ),
            )
        val result = BleHogpKeySender(fake).moveMouse(1, 1)

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.REPORT_REJECTED, (result as CommandResult.Failure).error.code)
    }
}
