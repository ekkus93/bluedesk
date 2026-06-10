package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies Redux-level scroll behavior.
 *
 * ScrollVertical / ScrollHorizontal are pass-through actions handled entirely by
 * middleware (which reads hidSimplified from the service at dispatch time).
 * The reducer leaves state unchanged for these actions — that is the intended contract:
 * scroll does not affect store state, only the HID report path.
 *
 * The scroll no-op in SIMPLE mode is enforced in BluetoothService.sendScroll /
 * sendScrollH (if !hidSimplified). These tests confirm the reducer contract so that
 * a future refactor cannot accidentally add state changes for scroll actions.
 */
class DescriptorScrollPolicyTest {
    @Test
    fun scrollVertical_doesNotMutateState() {
        val initial = AppState()
        val after = appReducer(initial, Action.ScrollVertical(3))
        // Connection, keyboard, and ui state must all be identical (same object references)
        assertFalse(after !== initial) // should be the exact same state object (else statement)
    }

    @Test
    fun scrollHorizontal_doesNotMutateState() {
        val initial = AppState()
        val after = appReducer(initial, Action.ScrollHorizontal(2))
        assertFalse(after !== initial)
    }

    @Test
    fun scrollVertical_doesNotChangeConnectedDevice() {
        val initial = AppState()
        val after = appReducer(initial, Action.ScrollVertical(1))
        assertNull(after.connection.connectedDevice)
    }

    @Test
    fun scrollHorizontal_doesNotChangeConnectedDevice() {
        val initial = AppState()
        val after = appReducer(initial, Action.ScrollHorizontal(1))
        assertNull(after.connection.connectedDevice)
    }
}
