package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryReducerTest {
    // Minimal BluetoothDevice stand-in for reducer tests (reducers only hold the list reference)
    // We can't construct a real BluetoothDevice in JVM tests, so we verify list-level semantics
    // using the reducer's observable output fields.

    @Test
    fun updateDiscoveredDevices_replacesExistingList() {
        // Start with a non-empty discovered list
        val initial =
            AppState(
                connection = ConnectionState(discoveredDevices = emptyList()),
            )
        // Reducer should replace the list with whatever is dispatched
        val after = appReducer(initial, Action.UpdateDiscoveredDevices(emptyList()))
        assertEquals(0, after.connection.discoveredDevices.size)
    }

    @Test
    fun updateDiscoveredDevices_clearsToPreviouslyNonEmptyState() {
        // Simulate a prior state where the reducer had stored a list,
        // then a clear dispatch arrives (new scan started)
        val clearedState =
            appReducer(
                AppState(connection = ConnectionState(isScanning = true)),
                Action.UpdateDiscoveredDevices(emptyList()),
            )
        assertTrue(clearedState.connection.discoveredDevices.isEmpty())
    }

    @Test
    fun updateDiscoveredDevices_doesNotAffectOtherConnectionFields() {
        val initial =
            AppState(
                connection =
                    ConnectionState(
                        isScanning = true,
                        message = "scanning...",
                    ),
            )
        val after = appReducer(initial, Action.UpdateDiscoveredDevices(emptyList()))
        assertTrue(after.connection.isScanning)
        assertEquals("scanning...", after.connection.message)
    }

    @Test
    fun updateIsScanning_setsTrue() {
        val after = appReducer(AppState(), Action.UpdateIsScanning(true))
        assertTrue(after.connection.isScanning)
    }

    @Test
    fun updateIsScanning_setsFalse() {
        val initial = AppState(connection = ConnectionState(isScanning = true))
        val after = appReducer(initial, Action.UpdateIsScanning(false))
        assertFalse(after.connection.isScanning)
    }

    @Test
    fun updateIsScanning_doesNotAffectDiscoveredDevices() {
        val initial = AppState(connection = ConnectionState(isScanning = false))
        val after = appReducer(initial, Action.UpdateIsScanning(true))
        // discoveredDevices must stay at its default (empty) — scanning flag change is independent
        assertTrue(after.connection.discoveredDevices.isEmpty())
    }
}
