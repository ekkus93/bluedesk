package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.IBluetoothService
import org.junit.Test
import org.mockito.Mockito

class BluetoothKeySenderTest {
    @Test
    fun `sendKeyDown forwards to pressKey`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.sendKeyDown(0x04, 0x02)
        Mockito.verify(svc).pressKey(0x04.toByte(), 0x02)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `sendKeyUp forwards to releaseKey`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.sendKeyUp(0x04)
        Mockito.verify(svc).releaseKey(0x04.toByte())
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `moveMouse forwards to sendMouseMove`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.moveMouse(5, -3)
        Mockito.verify(svc).sendMouseMove(5, -3)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `clicks forward to respective service calls`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.leftClick()
        Mockito.verify(svc).sendLeftClick()
        s.rightClick()
        Mockito.verify(svc).sendRightClick()
        s.middleClick()
        Mockito.verify(svc).sendMiddleClick()
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `scroll forwards to sendScroll and sendScrollH`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.scrollVertical(7)
        Mockito.verify(svc).sendScroll(7)
        s.scrollHorizontal(9)
        Mockito.verify(svc).sendScrollH(9)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `toggle locks sendKeyPress with correct codes`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.toggleCapsLock()
        Mockito.verify(svc).sendKeyPress(0x39.toByte(), 0)
        s.toggleScrollLock()
        Mockito.verify(svc).sendKeyPress(0x47.toByte(), 0)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `discovery_and_pairing_and_connection_calls forwarded`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        val d = Mockito.mock(BluetoothDevice::class.java)

        s.startDiscovery()
        Mockito.verify(svc).startDiscovery()
        s.stopDiscovery()
        Mockito.verify(svc).stopDiscovery()
        s.pairDevice(d)
        Mockito.verify(svc).pairDevice(d)
        s.connectDevice(d)
        Mockito.verify(svc).connectDevice(d)
        s.disconnectDevice()
        Mockito.verify(svc).disconnectDevice()
        s.forgetDevice(d, true)
        Mockito.verify(svc).forgetDevice(d, true)
        s.setDefaultDevice(d)
        Mockito.verify(svc).setDefaultDevice(d)
        s.renameDevice(d, "alias")
        Mockito.verify(svc).setAlias(d, "alias")
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `no extra interactions when only sendKeyDown called`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.sendKeyDown(0x05, 0)
        // expected: only pressKey called
        Mockito.verify(svc).pressKey(0x05.toByte(), 0)
        // ensure releaseKey was not called
        Mockito.verify(svc, Mockito.never()).releaseKey(0x05.toByte())
        // and no other interactions occurred
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `argument capture for pressKey in sendKeyDown`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        s.sendKeyDown(0x06, 0x03)

        // verify exact values were passed to the service
        Mockito.verify(svc).pressKey(0x06.toByte(), 0x03)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `pressKey exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        Mockito.doThrow(RuntimeException("boom"))
            .`when`(svc).pressKey(0x07.toByte(), 0x01)

        try {
            s.sendKeyDown(0x07, 0x01)
            org.junit.Assert.fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            org.junit.Assert.assertEquals("boom", e.message)
        }

        Mockito.verify(svc).pressKey(0x07.toByte(), 0x01)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `moveMouse exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        Mockito.doThrow(IllegalStateException("mouse-fail"))
            .`when`(svc).sendMouseMove(10, 20)

        try {
            s.moveMouse(10, 20)
            org.junit.Assert.fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            org.junit.Assert.assertEquals("mouse-fail", e.message)
        }

        Mockito.verify(svc).sendMouseMove(10, 20)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `pairDevice exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        val d = Mockito.mock(BluetoothDevice::class.java)
        Mockito.doThrow(IllegalArgumentException("pair-fail"))
            .`when`(svc).pairDevice(d)

        try {
            s.pairDevice(d)
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            org.junit.Assert.assertEquals("pair-fail", e.message)
        }

        Mockito.verify(svc).pairDevice(d)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `disconnectDevice exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        Mockito.doThrow(IllegalStateException("disconnect-io"))
            .`when`(svc).disconnectDevice()

        try {
            s.disconnectDevice()
            org.junit.Assert.fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            org.junit.Assert.assertEquals("disconnect-io", e.message)
        }

        Mockito.verify(svc).disconnectDevice()
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `toggleCapsLock exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        Mockito.doThrow(RuntimeException("caps-fail"))
            .`when`(svc).sendKeyPress(0x39.toByte(), 0)

        try {
            s.toggleCapsLock()
            org.junit.Assert.fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            org.junit.Assert.assertEquals("caps-fail", e.message)
        }

        Mockito.verify(svc).sendKeyPress(0x39.toByte(), 0)
        Mockito.verifyNoMoreInteractions(svc)
    }

    @Test
    fun `leftClick exception is propagated`() {
        val svc = Mockito.mock(IBluetoothService::class.java)
        val s = BluetoothKeySender(svc)
        Mockito.doThrow(RuntimeException("left-fail"))
            .`when`(svc).sendLeftClick()

        try {
            s.leftClick()
            org.junit.Assert.fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            org.junit.Assert.assertEquals("left-fail", e.message)
        }

        Mockito.verify(svc).sendLeftClick()
        Mockito.verifyNoMoreInteractions(svc)
    }
}
