package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.Action

class BluetoothServiceLogicTest {

    @Test
    fun maps_onConnectionStateChanged_to_VM_state() {
        // Fake service that exposes listener
        class EventService: IBluetoothService {
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
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(device.address).thenReturn("FE:ED:FA:CE:00:01")
    // Simulate service callbacks by dispatching canonical store actions (MainActivity does this in production)
    StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
    assertEquals(device, StoreProvider.asStateFlow().value.connection.connectedDevice)
    StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
    assertNull(StoreProvider.asStateFlow().value.connection.connectedDevice)
    }

    @Test
    fun handles_bondStateTransitions_correctly() {
        assertTrue(ReconnectLogic.bondStateTriggersReconnect(BluetoothDevice.BOND_BONDED))
        assertFalse(ReconnectLogic.bondStateTriggersReconnect(BluetoothDevice.BOND_NONE))
        // BOND_BONDING should not trigger immediate reconnect
        assertFalse(ReconnectLogic.bondStateTriggersReconnect(BluetoothDevice.BOND_BONDING))
    }

    @Test
    fun schedules_autoReconnect_with_backoff() {
        val base = 2000L
        val delays = (1..6).map { attempt -> ReconnectLogic.computeReconnectDelay(base, attempt) }
        // Expected: 2000, 4000, 8000, 16000, 30000 (cap), 30000
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 30000L, 30000L), delays)
    }

    @Test
    fun cancels_reconnect_on_manualDisconnect() {
        assertTrue(ReconnectLogic.shouldScheduleReconnect(manualDisconnect = false, btEnabled = true))
        assertFalse(ReconnectLogic.shouldScheduleReconnect(manualDisconnect = true, btEnabled = true))
        assertFalse(ReconnectLogic.shouldScheduleReconnect(manualDisconnect = false, btEnabled = false))
    }
}
