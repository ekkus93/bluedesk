package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.BackendRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            middleware.installSender(sender)

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
            middleware.installSender(sender)

            store.dispatch(Action.SendKey(0x04.toByte()))
            store.dispatch(Action.SendKey(0x04.toByte()))
            advanceUntilIdle()

            assertEquals(listOf("down:4", "up:4", "down:4", "up:4"), sender.events)
        }

    @Test
    fun rapidMouseClicksRemainDiscreteDownUpPairs() =
        runTest {
            val middleware = KeySenderMiddleware(this)
            val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
            val sender = RecordingSender()
            middleware.installSender(sender)

            store.dispatch(Action.LeftClick)
            store.dispatch(Action.LeftClick)
            store.dispatch(Action.RightClick)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "mouse-down:1",
                    "mouse-up",
                    "mouse-down:1",
                    "mouse-up",
                    "mouse-down:2",
                    "mouse-up",
                ),
                sender.events,
            )
        }

    @Test
    fun lockTogglesPreserveSerializedPressReleaseOrder() =
        runTest {
            val middleware = KeySenderMiddleware(this)
            val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
            val sender = RecordingSender()
            middleware.installSender(sender)

            store.dispatch(Action.ToggleCapsLock)
            store.dispatch(Action.ToggleScrollLock)
            store.dispatch(Action.ToggleCapsLock)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "down:57",
                    "up:57",
                    "down:71",
                    "up:71",
                    "down:57",
                    "up:57",
                ),
                sender.events,
            )
        }

    @Test
    fun cancellingCommandScopeStillReleasesHeldKey() =
        runTest {
            val commandJob = Job()
            val commandScope = CoroutineScope(StandardTestDispatcher(testScheduler) + commandJob)
            val middleware = KeySenderMiddleware(commandScope)
            val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
            val sender = RecordingSender()
            middleware.installSender(sender)

            store.dispatch(Action.SendKey(0x04.toByte()))
            runCurrent()
            assertEquals(listOf("down:4"), sender.events)

            commandJob.cancel()
            runCurrent()

            assertEquals(listOf("down:4", "up:4"), sender.events)
        }

    @Test
    fun missingSenderProducesVisibleFailureInsteadOfNoOp() {
        val middleware = KeySenderMiddleware()
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))

        store.dispatch(Action.MoveMouse(4, 5))

        val failure = store.state.backend.lastCommandResult as CommandResult.Failure
        assertEquals(CommandErrorCode.SENDER_UNAVAILABLE, failure.error.code)
        assertEquals(false, store.state.backend.senderAvailable)
        assertTrue(store.state.connection.message!!.contains("not ready"))
    }

    @Test
    fun unsupportedOperationIsNotTreatedAsSuccess() {
        val middleware = KeySenderMiddleware()
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))
        val sender =
            object : KeySender {
                override val backend = BackendMode.BLE_HOGP
                override val capabilities = BackendCapabilitySets.bleHogp

                override fun execute(command: KeyCommand): CommandResult =
                    CommandResult.Unsupported("device discovery", "BLE HOGP discovery is host-initiated")
            }
        middleware.installSender(sender)

        store.dispatch(Action.StartDiscovery)

        assertTrue(store.state.backend.lastCommandResult is CommandResult.Unsupported)
        assertEquals("BLE HOGP discovery is host-initiated", store.state.connection.message)
    }

    @Test
    fun inputUsabilityRequiresReadySenderPermissionsAndSafeHostAddress() {
        val base = AppState()
        assertFalse(base.isInputUsable())

        val readyWithoutAddress =
            base.copy(
                backend =
                    BackendState(
                        selectedBackend = BackendMode.CLASSIC_HID,
                        runtime = BackendRuntimeState.Ready(BackendMode.CLASSIC_HID, BackendCapabilitySets.classic),
                        senderAvailable = true,
                        permissionsValid = true,
                    ),
            )
        assertFalse(readyWithoutAddress.isInputUsable())

        val usable =
            readyWithoutAddress.copy(
                connection = readyWithoutAddress.connection.copy(connectedDeviceAddress = "AA:BB:CC:DD:EE:FF"),
            )
        assertTrue(usable.isInputUsable())

        assertFalse(usable.copy(backend = usable.backend.copy(senderAvailable = false)).isInputUsable())
        assertFalse(usable.copy(backend = usable.backend.copy(permissionsValid = false)).isInputUsable())
        assertFalse(usable.copy(backend = usable.backend.copy(runtime = BackendRuntimeState.Stopped)).isInputUsable())
    }

    private class RecordingSender : KeySender {
        override val backend = BackendMode.CLASSIC_HID
        override val capabilities = BackendCapabilitySets.classic
        var lastSetModifiers: Int = -1
        var lastSendKeyDownMods: Int = -1
        val events = mutableListOf<String>()

        override fun execute(command: KeyCommand): CommandResult {
            when (command) {
                is KeyCommand.SetModifiers -> lastSetModifiers = command.mods
                is KeyCommand.KeyDown -> {
                    lastSendKeyDownMods = command.mods
                    events.add("down:${command.code}")
                }
                is KeyCommand.KeyUp -> events.add("up:${command.code}")
                is KeyCommand.MouseButtonDown -> events.add("mouse-down:${command.button}")
                KeyCommand.MouseButtonUp -> events.add("mouse-up")
                else -> Unit
            }
            return CommandResult.Success
        }
    }
}
