package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.IBluetoothService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class BluetoothKeySenderTest {
    @Test
    fun `keyboard commands forward to service`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val sender = BluetoothKeySender(svc)

        assertEquals(CommandResult.Success, sender.execute(KeyCommand.KeyDown(0x04, 0x02)))
        assertEquals(CommandResult.Success, sender.execute(KeyCommand.KeyUp(0x04)))

        Mockito.verify(svc).pressKey(0x04.toByte(), 0x02)
        Mockito.verify(svc).releaseKey(0x04.toByte())
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `mouse button commands use explicit down and up`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val sender = BluetoothKeySender(svc)

        assertEquals(CommandResult.Success, sender.execute(KeyCommand.MouseButtonDown(0x01)))
        assertEquals(CommandResult.Success, sender.execute(KeyCommand.MouseButtonUp))

        Mockito.verify(svc).mouseButtonDown(0x01)
        Mockito.verify(svc).mouseButtonUp()
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `scroll and move commands forward`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val sender = BluetoothKeySender(svc)

        sender.execute(KeyCommand.MoveMouse(5, -3))
        sender.execute(KeyCommand.ScrollVertical(7))
        sender.execute(KeyCommand.ScrollHorizontal(9))

        Mockito.verify(svc).sendMouseMove(5, -3)
        Mockito.verify(svc).sendScroll(7)
        Mockito.verify(svc).sendScrollH(9)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `discovery pairing and connection commands forward`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val sender = BluetoothKeySender(svc)
        val device = Mockito.mock(BluetoothDevice::class.java)

        sender.execute(KeyCommand.StartDiscovery)
        sender.execute(KeyCommand.StopDiscovery)
        sender.execute(KeyCommand.PairDevice(device))
        sender.execute(KeyCommand.ConnectDevice(device))
        sender.execute(KeyCommand.DisconnectDevice)
        sender.execute(KeyCommand.ForgetDevice(device, true))
        sender.execute(KeyCommand.SetDefaultDevice(device))
        sender.execute(KeyCommand.RenameDevice(device, "alias"))

        Mockito.verify(svc).startDiscovery()
        Mockito.verify(svc).stopDiscovery()
        Mockito.verify(svc).pairDevice(device)
        Mockito.verify(svc).connectDevice(device)
        Mockito.verify(svc).disconnectDevice()
        Mockito.verify(svc).forgetDevice(device, true)
        Mockito.verify(svc).setDefaultDevice(device)
        Mockito.verify(svc).setAlias(device, "alias")
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `SecurityException becomes permission failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.doThrow(SecurityException("denied"))
            .`when`(svc).pressKey(0x07.toByte(), 0x01)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.KeyDown(0x07, 0x01))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.PERMISSION_DENIED, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `IllegalStateException becomes service unavailable failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        Mockito.doThrow(IllegalStateException("mouse-fail"))
            .`when`(svc).sendMouseMove(10, 20)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.MoveMouse(10, 20))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.SERVICE_UNAVAILABLE, (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `IllegalArgumentException becomes invalid-state failure`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.doThrow(IllegalArgumentException("pair-fail"))
            .`when`(svc).pairDevice(device)
        val sender = BluetoothKeySender(svc)

        val result = sender.execute(KeyCommand.PairDevice(device))

        assertTrue(result is CommandResult.Failure)
        assertEquals(CommandErrorCode.INVALID_STATE, (result as CommandResult.Failure).error.code)
    }
}
