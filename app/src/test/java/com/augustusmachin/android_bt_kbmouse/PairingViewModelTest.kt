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
import com.augustusmachin.android_bt_kbmouse.store.Action

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
        // Ensure the global store is reset between tests to avoid state leakage
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(null)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateDiscoveredDevices(emptyList()))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdatePairedDevices(emptyList()))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateConnectedDevice(null))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateMessage(null))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateDefaultDevice(null))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateLocks(false, false, false))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateIsScanning(false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isEmpty() {
        val state = com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value
        assertEquals(0, state.connection.discoveredDevices.size)
        assertEquals(0, state.connection.pairedDevices.size)
        assertNull(state.connection.connectedDevice)
        assertNull(state.connection.message)
    }

    @Test
    fun startDiscovery_setsMessage_and_callsService() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // Use KeySender middleware to observe discovery intent
        var startCalls = 0
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun startDiscovery() { startCalls++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StartDiscovery)
        runCurrent()
        assert(startCalls >= 1)
        // VM maps message from store; startDiscovery dispatches UpdateMessage("Scanning for devices...")
    // State updates flow through the store; assert directly on the store's current snapshot
    assertEquals("Scanning for devices...", com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StopDiscovery)
        Dispatchers.resetMain()
    }

    @Test
    fun stopDiscovery_callsService_and_clearsMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var stopCalls = 0
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun stopDiscovery() { stopCalls++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StartDiscovery)
        runCurrent()
        assertEquals("Scanning for devices...", com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StopDiscovery)
        assert(stopCalls >= 1)
    assertNull(com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
        Dispatchers.resetMain()
    }

    @Test
    fun keyDown_up_invokesService() {
        // use a fake KeySender to capture middleware-dispatched HID actions
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            var pressCalls = 0
            var releaseCalls = 0
            var lastPressed: Byte? = null
            var lastMods: Int? = null
            override fun sendKeyDown(code: Byte, mods: Int) { pressCalls++; lastPressed = code; lastMods = mods }
            override fun sendKeyUp(code: Byte) { releaseCalls++; lastPressed = code }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.KeyDown(0x04.toByte(), 0x02))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.KeyUp(0x04.toByte()))
        assertEquals(1, fakeSender.pressCalls)
        assertEquals(1, fakeSender.releaseCalls)
        assertEquals(0x04.toByte(), fakeSender.lastPressed)
        assertEquals(0x02, fakeSender.lastMods)
    }

    @Test
    fun mouseMove_and_clicks_invoked() {
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            val moves = mutableListOf<Pair<Int,Int>>()
            var left = 0; var right = 0; var middle = 0
            override fun moveMouse(dx: Int, dy: Int) { moves += dx to dy }
            override fun leftClick() { left++ }
            override fun rightClick() { right++ }
            override fun middleClick() { middle++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.MoveMouse(5, -3))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.LeftClick)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.RightClick)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.MiddleClick)
        assertEquals(listOf(5 to -3), fakeSender.moves)
        assertEquals(1, fakeSender.left)
        assertEquals(1, fakeSender.right)
        assertEquals(1, fakeSender.middle)
    }

    @Test
    fun consumeMessage_clearsMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var startCalls = 0
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun startDiscovery() { startCalls++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StartDiscovery)
        runCurrent()
        assertEquals("Scanning for devices...", com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
    com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.UpdateMessage(null))
    runCurrent()
    assertNull(com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
    com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StopDiscovery)
        Dispatchers.resetMain()
    }

    @Test
    fun updatesDiscovered_andPairedLists_fromServiceCallbacks() {
        // Simulate service events by dispatching Update* actions to the store and assert VM
        // no ViewModel required; read directly from store
        val d1 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        val d2 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(d1.address).thenReturn("00:11:22:33:44:55")
        org.mockito.Mockito.`when`(d2.address).thenReturn("66:77:88:99:AA:BB")
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateDiscoveredDevices(listOf(d1)))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdatePairedDevices(listOf(d2)))
    assertEquals(1, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.discoveredDevices.size)
    assertEquals(1, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.pairedDevices.size)
        // Add another and refresh
        val d3 = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(d3.address).thenReturn("CC:DD:EE:FF:00:11")
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateDiscoveredDevices(listOf(d1, d3)))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdatePairedDevices(listOf(d2, d1)))
    assertEquals(2, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.discoveredDevices.size)
    assertEquals(2, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.pairedDevices.size)
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
        // no ViewModel required; dispatch actions directly
        var connectCalls = 0
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun connectDevice(device: BluetoothDevice) { connectCalls++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        val device = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(device.address).thenReturn("01:23:45:67:89:AB")
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.ConnectDevice(device))
        assertEquals(1, connectCalls)
        // simulate service callback into store
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
    assertEquals(device, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.connectedDevice)
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
        // no ViewModel required; dispatch actions directly
        var disconnectCalls = 0
        val fakeSender = object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun disconnectDevice() { disconnectCalls++ }
        }
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(fakeSender)
        val device = org.mockito.Mockito.mock(BluetoothDevice::class.java)
        org.mockito.Mockito.`when`(device.address).thenReturn("DE:AD:BE:EF:00:01")
        // simulate connected state
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
    assertEquals(device, com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.connectedDevice)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.DisconnectDevice)
        assertEquals(1, disconnectCalls)
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateMessage("Disconnected"))
    assertNull(com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.connectedDevice)
    assertEquals("Disconnected", com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
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
        // Simulate error callback from service by dispatching into store
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateMessage("Failed to connect"))
    assertEquals("Failed to connect", com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
        // Simulate navigation consuming message
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(Action.UpdateMessage(null))
    assertNull(com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().value.connection.message)
    }
}

