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
    fun sendKeyCombinesLockedModifiersAndReleases() =
        runTest {
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

    @Test
    fun repeatedSameKeyEmitsKeyUpBetweenPresses() =
        runTest {
            val middleware = KeySenderMiddleware(this)
            val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
            val sender = RecordingSender()
            middleware.sender = sender

            // Type the same key twice in a row (e.g. "aa").
            store.dispatch(Action.SendKey(0x04.toByte()))
            store.dispatch(Action.SendKey(0x04.toByte()))
            advanceUntilIdle()

            // Each press must be a full down->up before the next starts. Otherwise the two
            // identical key-down reports have no key-up between them and the host coalesces
            // them into a single keystroke (the "aa" -> "a" bug).
            assertEquals(listOf("down:4", "up:4", "down:4", "up:4"), sender.events)
        }

    private class RecordingSender : KeySender {
        var lastSetModifiers: Int = -1
        var lastSendKeyDownMods: Int = -1
        val events = mutableListOf<String>()

        override fun setModifiers(mods: Int) {
            lastSetModifiers = mods
        }

        override fun sendKeyDown(
            code: Byte,
            mods: Int,
        ) {
            lastSendKeyDownMods = mods
            events.add("down:$code")
        }

        override fun sendKeyUp(code: Byte) {
            events.add("up:$code")
        }
    }
}
