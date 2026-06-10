package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceStateReducerTest {
    // --- UpdateDefaultDevice ---

    @Test
    fun updateDefaultDevice_setsAddress() {
        val after = appReducer(AppState(), Action.UpdateDefaultDevice("AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", after.connection.defaultDeviceAddress)
    }

    @Test
    fun updateDefaultDevice_clearsAddress() {
        val initial = AppState(connection = ConnectionState(defaultDeviceAddress = "AA:BB:CC:DD:EE:FF"))
        val after = appReducer(initial, Action.UpdateDefaultDevice(null))
        assertNull(after.connection.defaultDeviceAddress)
    }

    @Test
    fun updateDefaultDevice_doesNotAffectConnectedDevice() {
        val initial = AppState(connection = ConnectionState(defaultDeviceAddress = "AA:BB:CC:DD:EE:FF"))
        val after = appReducer(initial, Action.UpdateDefaultDevice(null))
        assertNull(after.connection.connectedDevice) // was already null
    }

    // --- UpdatePairedDevices ---

    @Test
    fun updatePairedDevices_replacesExistingList() {
        val initial = AppState(connection = ConnectionState(pairedDevices = emptyList()))
        val after = appReducer(initial, Action.UpdatePairedDevices(emptyList()))
        assertEquals(0, after.connection.pairedDevices.size)
    }

    @Test
    fun updatePairedDevices_doesNotAffectDefaultDevice() {
        val initial = AppState(connection = ConnectionState(defaultDeviceAddress = "AA:BB:CC:DD:EE:FF"))
        val after = appReducer(initial, Action.UpdatePairedDevices(emptyList()))
        assertEquals("AA:BB:CC:DD:EE:FF", after.connection.defaultDeviceAddress)
    }

    // --- UpdateConnectedDevice ---

    @Test
    fun updateConnectedDevice_nullClearsConnectedDevice() {
        val initial = AppState(connection = ConnectionState(connectedDevice = null))
        val after = appReducer(initial, Action.UpdateConnectedDevice(null))
        assertNull(after.connection.connectedDevice)
    }

    @Test
    fun updateConnectedDevice_doesNotAffectDefaultDevice() {
        val initial = AppState(connection = ConnectionState(defaultDeviceAddress = "AA:BB:CC:DD:EE:FF"))
        val after = appReducer(initial, Action.UpdateConnectedDevice(null))
        assertEquals("AA:BB:CC:DD:EE:FF", after.connection.defaultDeviceAddress)
    }
}
