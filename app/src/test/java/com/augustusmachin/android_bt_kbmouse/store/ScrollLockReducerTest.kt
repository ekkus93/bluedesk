package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollLockReducerTest {
    @Test
    fun toggleScrollLockFlipsFlag() {
        val initial = AppState()
        val first = appReducer(initial, Action.ToggleScrollLock)
        assertTrue(first.connection.scrollLock)

        val second = appReducer(first, Action.ToggleScrollLock)
        assertFalse(second.connection.scrollLock)
    }

    @Test
    fun updateLocksOverridesToggle() {
        val toggled = appReducer(AppState(), Action.ToggleScrollLock)
        val updated = appReducer(toggled, Action.UpdateLocks(caps = false, scroll = false))
        assertFalse(updated.connection.scrollLock)
    }
}
