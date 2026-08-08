package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.HidDeliveryFailureCode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryResult
import com.augustusmachin.android_bt_kbmouse.IBluetoothService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class BluetoothKeySenderTest {
    @Test
    fun `keyboard commands forward successful delivery`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.pressKey(0x04.toByte(), 0x02)).thenReturn(HidDeliveryResult.Sent)
        Mockito.`when`(svc.releaseKey(0x04.toByte())).thenReturn(HidDeliveryResult.Sent)
        val sender = BluetoothKeySender(svc)

        assertEquals(CommandResult.Success, sender.execute(KeyCommand.KeyDown(0x04, 0x02)))
        assertEquals(CommandResult.Success, sender.execute(KeyCommand.KeyUp(0x04)))
    }

    @Test
    fun `mouse button commands forward successful delivery`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.mouseButtonDown(0x01)).thenReturn(HidDeliveryResult.Sent)
        Mockito.`when`(svc.mouseButtonUp()).thenReturn(HidDeliveryResult.Sent)
        val sender = BluetoothKeySender(svc)

        assertEquals(CommandResult.Success, sender.execute(KeyCommand.MouseButtonDown(0x01)))
        assertEquals(CommandResult.Success, sender.execute(KeyCommand.MouseButtonUp))
    }

    @Test
    fun `report rejection becomes command rejection failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.sendMouseMove(5, -3)).thenReturn(
            HidDeliveryResult.Failure(HidDeliveryFailureCode.REPORT_REJECTED, "rejected"),
        )
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.MoveMouse(5, -3))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.REPORT_REJECTED, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `simplified scroll result remains explicit unsupported`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.sendScroll(7)).thenReturn(HidDeliveryResult.Unsupported("full descriptor required"))
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.ScrollVertical(7))

        assertTrue(result is CommandResult.Unsupported)
    }

    @Test
    fun `discovery result is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.startDiscovery()).thenReturn(false)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.StartDiscovery)

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.DISCOVERY_FAILED, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `SecurityException becomes permission failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.doThrow(SecurityException("denied")).`when`(svc).pressKey(0x07.toByte(), 0x01)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.KeyDown(0x07, 0x01))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.PERMISSION_DENIED, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `typed permission delivery failure becomes permission failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.`when`(svc.sendMouseMove(10, 20)).thenReturn(
            HidDeliveryResult.Failure(HidDeliveryFailureCode.PERMISSION_DENIED, "revoked"),
        )
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.MoveMouse(10, 20))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.PERMISSION_DENIED, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `IllegalArgumentException becomes invalid-state failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.doThrow(IllegalArgumentException("pair-fail")).`when`(svc).pairDevice(device)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.PairDevice(device))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.INVALID_STATE, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `missing mocked delivery result is never treated as success`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.KeyDown(0x04, 0))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.SERVICE_UNAVAILABLE, (result as CommandResult.Failure).error.code)
    }
}
