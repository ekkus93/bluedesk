package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.KeyCommand
import com.augustusmachin.android_bt_kbmouse.store.RecordingKeySender
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class PairingStoreTest {
    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        StoreProvider.setKeySender(null)
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(emptyList()))
        StoreProvider.dispatch(Action.UpdatePairedDevices(emptyList()))
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
        StoreProvider.dispatch(Action.UpdateLocks(false, false))
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    @After
    fun tearDown() {
        StoreProvider.setKeySender(null)
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isEmpty() {
        val state = StoreProvider.asStateFlow().value
        assertEquals(0, state.connection.discoveredDevices.size)
        assertEquals(0, state.connection.pairedDevices.size)
        assertNull(state.connection.connectedDevice)
        assertNull(state.connection.message)
    }

    @Test
    fun startDiscovery_forwardsIntent_withoutOptimisticScanningState() {
        val sender = RecordingKeySender()
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.StartDiscovery)

        assertEquals(listOf(KeyCommand.StartDiscovery), sender.commands)
        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
        assertNull(StoreProvider.asStateFlow().value.connection.message)
    }

    @Test
    fun backendScanState_isCanonical() {
        StoreProvider.dispatch(Action.UpdateIsScanning(true))
        assertEquals(true, StoreProvider.asStateFlow().value.connection.isScanning)
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
    }

    @Test
    fun keyDownUp_invokesExplicitCommands() {
        val sender = RecordingKeySender()
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.KeyDown(0x04.toByte(), 0x02))
        StoreProvider.dispatch(Action.KeyUp(0x04.toByte()))

        assertEquals(
            listOf(KeyCommand.KeyDown(0x04.toByte(), 0x02), KeyCommand.KeyUp(0x04.toByte())),
            sender.commands,
        )
    }

    @Test
    fun mouseMove_forwardsExplicitCommand() {
        val sender = RecordingKeySender()
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.MoveMouse(5, -3))

        assertEquals(listOf(KeyCommand.MoveMouse(5, -3)), sender.commands)
    }

    @Test
    fun updatesDiscovered_andPairedLists_fromServiceCallbacks() {
        val d1 = Mockito.mock(BluetoothDevice::class.java)
        val d2 = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(d1.address).thenReturn("00:11:22:33:44:55")
        Mockito.`when`(d2.address).thenReturn("66:77:88:99:AA:BB")

        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(listOf(d1)))
        StoreProvider.dispatch(Action.UpdatePairedDevices(listOf(d2)))

        assertEquals(listOf(d1), StoreProvider.asStateFlow().value.connection.discoveredDevices)
        assertEquals(listOf(d2), StoreProvider.asStateFlow().value.connection.pairedDevices)
    }

    @Test
    fun connectAndDisconnect_areCommands_notOptimisticConnectionState() {
        val sender = RecordingKeySender()
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(device.address).thenReturn("01:23:45:67:89:AB")
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.ConnectDevice(device))
        StoreProvider.dispatch(Action.DisconnectDevice)

        assertEquals(
            listOf(KeyCommand.ConnectDevice(device), KeyCommand.DisconnectDevice),
            sender.commands,
        )
        assertNull(StoreProvider.asStateFlow().value.connection.connectedDevice)
    }

    @Test
    fun serviceCallback_updatesConnectionState() {
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(device.address).thenReturn("DE:AD:BE:EF:00:01")

        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
        assertEquals(device, StoreProvider.asStateFlow().value.connection.connectedDevice)
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage("Disconnected"))

        assertNull(StoreProvider.asStateFlow().value.connection.connectedDevice)
        assertEquals("Disconnected", StoreProvider.asStateFlow().value.connection.message)
    }
}
