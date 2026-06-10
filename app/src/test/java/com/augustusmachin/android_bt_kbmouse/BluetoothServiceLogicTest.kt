package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class BluetoothServiceLogicTest {
    @Test
    fun maps_onConnectionStateChanged_to_VM_state() {
        // This test drives the store directly (as MainActivity does in production);
        // a shared FakeBluetoothService is available if a service stand-in is needed.
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
