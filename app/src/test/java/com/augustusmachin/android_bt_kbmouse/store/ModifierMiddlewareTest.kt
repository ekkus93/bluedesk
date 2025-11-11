package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore

@OptIn(ExperimentalCoroutinesApi::class)
class ModifierMiddlewareTest {
    @Test
    fun sendKeyCombinesLockedModifiersAndReleases() = runTest {
        val middleware = KeySenderMiddleware(this)
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
        val sender = RecordingSender()
        middleware.sender = sender

        store.dispatch(Action.ToggleShift)
        assertTrue(store.state.keyboard.shift)
        assertEquals(0x02, sender.lastSetModifiers)

        store.dispatch(Action.SendKey(0x04.toByte()))
        advanceUntilIdle()

        assertEquals(0x02, sender.lastSendKeyDownMods)
        assertFalse(store.state.keyboard.shift)
        assertEquals(0x00, sender.lastSetModifiers)
    }

    private class RecordingSender : KeySender {
        var lastSetModifiers: Int = -1
        var lastSendKeyDownMods: Int = -1

        override fun setModifiers(mods: Int) {
            lastSetModifiers = mods
        }

        override fun sendKeyDown(code: Byte, mods: Int) {
            lastSendKeyDownMods = mods
        }
    }
}
