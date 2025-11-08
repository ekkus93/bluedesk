package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private class FakeService: IBluetoothService {
        var startCalls = 0
        var stopCalls = 0
        var pressKeyCalls = 0
        var releaseKeyCalls = 0
        var lastPressed: Byte? = null
        var lastReleased: Byte? = null
        var lastModifiers: Int? = null
        var mouseMoves = mutableListOf<Pair<Int,Int>>()
        var leftClicks = 0
        var rightClicks = 0
        var middleClicks = 0
        private val discovered = mutableListOf<BluetoothDevice>()
        private val paired = mutableListOf<BluetoothDevice>()
        override fun getLastDeviceAddress(): String? = null
        override fun setEventListener(l: BluetoothService.ServiceEventListener) {}
        override fun startDiscovery() { startCalls++ }
        override fun stopDiscovery() { stopCalls++ }
        override fun getDiscoveredDevices(): List<BluetoothDevice> = discovered
        override fun getPairedDevices(): List<BluetoothDevice> = paired
        override fun pairDevice(device: BluetoothDevice) { }
        override fun connectDevice(device: BluetoothDevice) { }
        override fun disconnectDevice() { }
        override fun setDefaultDevice(device: BluetoothDevice) { }
        override fun getAlias(device: BluetoothDevice): String? = null
        override fun setAlias(device: BluetoothDevice, alias: String) {}
        override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
        override fun sendKeyPress(keyCode: Byte, modifiers: Int) { pressKey(keyCode, modifiers); releaseKey(keyCode) }
        override fun sendMouseMove(dx: Int, dy: Int) { mouseMoves += dx to dy }
        override fun sendLeftClick() { leftClicks++ }
        override fun sendRightClick() { rightClicks++ }
        override fun sendMiddleClick() { middleClicks++ }
        override fun sendScroll(delta: Int) {}
        override fun sendScrollH(delta: Int) {}
        override fun pressKey(keyCode: Byte, modifiers: Int) { pressKeyCalls++; lastPressed = keyCode; lastModifiers = modifiers }
        override fun releaseKey(keyCode: Byte) { releaseKeyCalls++; lastReleased = keyCode }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_empty() {
        val vm = PairingViewModel()
        assertEquals(0, vm.discoveredDevices.value.size)
        assertEquals(0, vm.pairedDevices.value.size)
        assertNull(vm.connectedDevice.value)
        assertNull(vm.message.value)
    }

    @Test
    fun startDiscovery_callsService() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.startDiscovery()
        runCurrent()
        vm.stopDiscovery()
        assert(fake.startCalls >= 1)
        assertEquals("Scanning for devices...", vm.message.value)
        Dispatchers.resetMain()
    }

    @Test
    fun keyDown_up_invokesService() {
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.keyDown(0x04, 0x02) // 'A' with Shift
        vm.keyUp(0x04)
        assertEquals(1, fake.pressKeyCalls)
        assertEquals(1, fake.releaseKeyCalls)
        assertEquals(0x04.toByte(), fake.lastPressed)
        assertEquals(0x02, fake.lastModifiers)
        assertEquals(0x04.toByte(), fake.lastReleased)
    }

    @Test
    fun mouseMove_and_clicks_invoked() {
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.moveMouse(5, -3)
        vm.leftClick(); vm.rightClick(); vm.middleClick()
        assertEquals(listOf(5 to -3), fake.mouseMoves)
        assertEquals(1, fake.leftClicks)
        assertEquals(1, fake.rightClicks)
        assertEquals(1, fake.middleClicks)
    }
}
