package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollLockReducerTest {

    @Test
    fun toggleCapsLockIsPassthroughInReducer() {
        // ToggleCapsLock must NOT speculatively flip capsLock in the reducer.
        // The authoritative state comes from UpdateLocks (host LED report).
        val after = appReducer(AppState(), Action.ToggleCapsLock)
        assertFalse(after.connection.capsLock)
    }

    @Test
    fun toggleScrollLockIsPassthroughInReducer() {
        val after = appReducer(AppState(), Action.ToggleScrollLock)
        assertFalse(after.connection.scrollLock)
    }

    @Test
    fun updateLocksSetsCapsAndScrollLock() {
        val updated = appReducer(AppState(), Action.UpdateLocks(caps = true, scroll = true))
        assertTrue(updated.connection.capsLock)
        assertTrue(updated.connection.scrollLock)
    }

    @Test
    fun updateLocksOverridesAnyPriorState() {
        val withLocks = appReducer(AppState(), Action.UpdateLocks(caps = true, scroll = true))
        val cleared = appReducer(withLocks, Action.UpdateLocks(caps = false, scroll = false))
        assertFalse(cleared.connection.capsLock)
        assertFalse(cleared.connection.scrollLock)
    }
}
