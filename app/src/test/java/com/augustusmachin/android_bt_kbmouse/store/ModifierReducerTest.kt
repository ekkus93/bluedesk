package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierReducerTest {
    @Test
    fun releaseClearsNonPersistentLocks() {
        val initial = AppState(
            keyboard = KeyboardState(shift = true, alt = true, gui = true)
        )
        val updated = appReducer(initial, Action.ReleaseLockedModifiers)
        assertFalse(updated.keyboard.shift)
        assertFalse(updated.keyboard.alt)
        assertFalse(updated.keyboard.gui)
    }

    @Test
    fun releaseRespectsPersistFlags() {
        val initial = AppState(
            keyboard = KeyboardState(shift = true, alt = true, gui = true, shiftPersist = true, altPersist = true)
        )
        val updated = appReducer(initial, Action.ReleaseLockedModifiers)
        assertTrue(updated.keyboard.shift)
        assertTrue(updated.keyboard.alt)
        assertFalse(updated.keyboard.gui)
    }
}
