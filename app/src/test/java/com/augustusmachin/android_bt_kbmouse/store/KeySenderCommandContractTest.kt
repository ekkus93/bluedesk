package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.HogpNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeySenderCommandContractTest {
    @Test
    fun `BLE discovery is explicit unsupported`() {
        val sender = BleHogpKeySender(NoopNotifier)

        val result = sender.execute(KeyCommand.StartDiscovery)

        assertTrue(result is CommandResult.Unsupported)
        assertEquals(false, sender.capabilities.discovery)
    }

    @Test
    fun `BLE scroll is explicit unsupported`() {
        val sender = BleHogpKeySender(NoopNotifier)

        val result = sender.execute(KeyCommand.ScrollVertical(1))

        assertTrue(result is CommandResult.Unsupported)
        assertEquals(false, sender.capabilities.verticalScroll)
    }

    @Test
    fun `supported BLE mouse movement succeeds`() {
        var calls = 0
        val notifier =
            object : HogpNotifier {
                override fun notifyKeyboard(report: ByteArray) = Unit

                override fun notifyMouse(report: ByteArray) {
                    calls += 1
                }
            }
        val sender = BleHogpKeySender(notifier)

        val result = sender.execute(KeyCommand.MoveMouse(2, 3))

        assertEquals(CommandResult.Success, result)
        assertEquals(1, calls)
    }

    private object NoopNotifier : HogpNotifier {
        override fun notifyKeyboard(report: ByteArray) = Unit

        override fun notifyMouse(report: ByteArray) = Unit
    }
}
