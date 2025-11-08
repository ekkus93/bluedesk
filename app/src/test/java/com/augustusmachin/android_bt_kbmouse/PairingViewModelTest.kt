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
    fun initialState_isEmpty() {
        val vm = PairingViewModel()
        assertEquals(0, vm.discoveredDevices.value.size)
        assertEquals(0, vm.pairedDevices.value.size)
        assertNull(vm.connectedDevice.value)
        assertNull(vm.message.value)
    }

    @Test
    fun startDiscovery_setsMessage_and_callsService() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.startDiscovery()
        runCurrent()
        assert(fake.startCalls >= 1)
        assertEquals("Scanning for devices...", vm.message.value)
        vm.stopDiscovery()
        Dispatchers.resetMain()
    }

    @Test
    fun stopDiscovery_callsService_and_clearsMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.startDiscovery()
        runCurrent()
        assertEquals("Scanning for devices...", vm.message.value)
        vm.stopDiscovery()
        assert(fake.stopCalls >= 1)
        assertNull(vm.message.value)
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

    @Test
    fun consumeMessage_clearsMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = PairingViewModel()
        val fake = FakeService()
        vm.setBluetoothService(fake)
        vm.startDiscovery()
        runCurrent()
        assertEquals("Scanning for devices...", vm.message.value)
        vm.consumeMessage()
        assertNull(vm.message.value)
        vm.stopDiscovery() // cancel looping job to avoid test leak
        Dispatchers.resetMain()
    }

    @Test
    fun updatesDiscovered_andPairedLists_fromServiceCallbacks() {
        // Service with mutable lists we can manipulate
        class ListService: IBluetoothService {
            val discoveredList = mutableListOf<BluetoothDevice>()
            val pairedList = mutableListOf<BluetoothDevice>()
            override fun getLastDeviceAddress(): String? = null
            override fun setEventListener(l: BluetoothService.ServiceEventListener) {}
            override fun startDiscovery() {}
            override fun stopDiscovery() {}
            override fun getDiscoveredDevices(): List<BluetoothDevice> = discoveredList
            override fun getPairedDevices(): List<BluetoothDevice> = pairedList
            override fun pairDevice(device: BluetoothDevice) {}
            override fun connectDevice(device: BluetoothDevice) {}
            override fun disconnectDevice() {}
            override fun setDefaultDevice(device: BluetoothDevice) {}
            override fun getAlias(device: BluetoothDevice): String? = null
            override fun setAlias(device: BluetoothDevice, alias: String) {}
            override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
            override fun sendKeyPress(keyCode: Byte, modifiers: Int) {}
            override fun sendMouseMove(dx: Int, dy: Int) {}
            override fun sendLeftClick() {}
            override fun sendRightClick() {}
            override fun sendMiddleClick() {}
            override fun sendScroll(delta: Int) {}
            override fun sendScrollH(delta: Int) {}
            override fun pressKey(keyCode: Byte, modifiers: Int) {}
            override fun releaseKey(keyCode: Byte) {}
        }
        val service = ListService()
        val vm = PairingViewModel()
        vm.setBluetoothService(service)
        // Mock BluetoothDevice objects (final) using Mockito inline
        val d1 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        val d2 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(d1.address).thenReturn("00:11:22:33:44:55")
        org.mockito.Mockito.`when`(d2.address).thenReturn("66:77:88:99:AA:BB")
        service.discoveredList += d1
        service.pairedList += d2
        vm.updateDiscoveredDevices()
        vm.getPairedDevices()
        assertEquals(1, vm.discoveredDevices.value.size)
        assertEquals(1, vm.pairedDevices.value.size)
        // Add another and refresh
        val d3 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(d3.address).thenReturn("CC:DD:EE:FF:00:11")
        service.discoveredList += d3
        service.pairedList += d1
        vm.updateDiscoveredDevices()
        vm.getPairedDevices()
        assertEquals(2, vm.discoveredDevices.value.size)
        assertEquals(2, vm.pairedDevices.value.size)
    }

    @Test
    fun connect_onSelection_invokesService_and_updatesState() {
        class CaptureService: IBluetoothService {
            var connectCalls = 0
            var disconnectCalls = 0
            var last: BluetoothDevice? = null
            override fun getLastDeviceAddress(): String? = null
            override fun setEventListener(l: BluetoothService.ServiceEventListener) {}
            override fun startDiscovery() {}
            override fun stopDiscovery() {}
            override fun getDiscoveredDevices(): List<BluetoothDevice> = emptyList()
            override fun getPairedDevices(): List<BluetoothDevice> = emptyList()
            override fun pairDevice(device: BluetoothDevice) {}
            override fun connectDevice(device: BluetoothDevice) { connectCalls++; last = device }
            override fun disconnectDevice() { disconnectCalls++ }
            override fun setDefaultDevice(device: BluetoothDevice) {}
            override fun getAlias(device: BluetoothDevice): String? = null
            override fun setAlias(device: BluetoothDevice, alias: String) {}
            override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
            override fun sendKeyPress(keyCode: Byte, modifiers: Int) {}
            override fun sendMouseMove(dx: Int, dy: Int) {}
            override fun sendLeftClick() {}
            override fun sendRightClick() {}
            override fun sendMiddleClick() {}
            override fun sendScroll(delta: Int) {}
            override fun sendScrollH(delta: Int) {}
            override fun pressKey(keyCode: Byte, modifiers: Int) {}
            override fun releaseKey(keyCode: Byte) {}
        }
        val service = CaptureService()
        val vm = PairingViewModel()
        vm.setBluetoothService(service)
        val device = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(device.address).thenReturn("01:23:45:67:89:AB")
        vm.connectDevice(device)
        assertEquals(1, service.connectCalls)
        assertEquals(device, vm.connectedDevice.value)
    }

    @Test
    fun disconnect_updatesState_and_message() {
        class DiscService: IBluetoothService {
            var disconnectCalls = 0
            override fun getLastDeviceAddress(): String? = null
            override fun setEventListener(l: BluetoothService.ServiceEventListener) {}
            override fun startDiscovery() {}
            override fun stopDiscovery() {}
            override fun getDiscoveredDevices(): List<BluetoothDevice> = emptyList()
            override fun getPairedDevices(): List<BluetoothDevice> = emptyList()
            override fun pairDevice(device: BluetoothDevice) {}
            override fun connectDevice(device: BluetoothDevice) {}
            override fun disconnectDevice() { disconnectCalls++ }
            override fun setDefaultDevice(device: BluetoothDevice) {}
            override fun getAlias(device: BluetoothDevice): String? = null
            override fun setAlias(device: BluetoothDevice, alias: String) {}
            override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
            override fun sendKeyPress(keyCode: Byte, modifiers: Int) {}
            override fun sendMouseMove(dx: Int, dy: Int) {}
            override fun sendLeftClick() {}
            override fun sendRightClick() {}
            override fun sendMiddleClick() {}
            override fun sendScroll(delta: Int) {}
            override fun sendScrollH(delta: Int) {}
            override fun pressKey(keyCode: Byte, modifiers: Int) {}
            override fun releaseKey(keyCode: Byte) {}
        }
        val service = DiscService()
        val vm = PairingViewModel()
        vm.setBluetoothService(service)
        val device = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(device.address).thenReturn("DE:AD:BE:EF:00:01")
        // Force as connected by calling connectDevice
        vm.connectDevice(device)
        assertEquals(device, vm.connectedDevice.value)
        vm.disconnectDevice()
        assertEquals(1, service.disconnectCalls)
        assertNull(vm.connectedDevice.value)
        assertEquals("Disconnected", vm.message.value)
    }

    @Test
    fun surfacesErrorMessages_and_clearsOnNavigate() {
        class ErrorService: IBluetoothService {
            var listener: BluetoothService.ServiceEventListener? = null
            override fun getLastDeviceAddress(): String? = null
            override fun setEventListener(l: BluetoothService.ServiceEventListener) { listener = l }
            override fun startDiscovery() {}
            override fun stopDiscovery() {}
            override fun getDiscoveredDevices(): List<BluetoothDevice> = emptyList()
            override fun getPairedDevices(): List<BluetoothDevice> = emptyList()
            override fun pairDevice(device: BluetoothDevice) {}
            override fun connectDevice(device: BluetoothDevice) {}
            override fun disconnectDevice() {}
            override fun setDefaultDevice(device: BluetoothDevice) {}
            override fun getAlias(device: BluetoothDevice): String? = null
            override fun setAlias(device: BluetoothDevice, alias: String) {}
            override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
            override fun sendKeyPress(keyCode: Byte, modifiers: Int) {}
            override fun sendMouseMove(dx: Int, dy: Int) {}
            override fun sendLeftClick() {}
            override fun sendRightClick() {}
            override fun sendMiddleClick() {}
            override fun sendScroll(delta: Int) {}
            override fun sendScrollH(delta: Int) {}
            override fun pressKey(keyCode: Byte, modifiers: Int) {}
            override fun releaseKey(keyCode: Byte) {}
        }
        val service = ErrorService()
        val vm = PairingViewModel()
        vm.setBluetoothService(service)
        // Simulate error callback from service
        service.listener?.onError("Failed to connect")
        assertEquals("Failed to connect", vm.message.value)
        // Simulate navigation consuming message
        vm.consumeMessage()
        assertNull(vm.message.value)
    }
}

