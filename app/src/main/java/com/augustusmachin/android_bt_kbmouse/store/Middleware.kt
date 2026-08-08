package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.reduxkotlin.Store
import org.reduxkotlin.middleware

private const val MODIFIER_CTRL = 0x01
private const val MODIFIER_SHIFT = 0x02
private const val MODIFIER_ALT = 0x04
private const val MODIFIER_GUI = 0x08
private const val MOUSE_BUTTON_LEFT = 0x01
private const val MOUSE_BUTTON_RIGHT = 0x02
private const val MOUSE_BUTTON_MIDDLE = 0x04
private const val CAPS_LOCK_KEY = 0x39
private const val SCROLL_LOCK_KEY = 0x47
private const val MOUSE_CLICK_HOLD_MS = 10L
private const val KEY_PRESS_HOLD_MS = 40L

private val MODIFIER_TOGGLE_ACTIONS =
    setOf(Action.ToggleCtrl, Action.ToggleShift, Action.ToggleAlt, Action.ToggleGui)

/**
 * Middleware that forwards HID and connection intent actions to an explicit [KeySender].
 * Missing senders and unsupported operations are state-visible failures, never nullable no-ops.
 */
class KeySenderMiddleware(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    @Volatile
    private var sender: KeySender? = null

    // Serializes press/release sequences so rapid identical keys and clicks cannot interleave.
    private val inputSequenceMutex = Mutex()

    fun installSender(sender: KeySender?) {
        this.sender = sender
    }

    fun currentSender(): KeySender? = sender

    fun create() =
        middleware<AppState> { store: Store<AppState>, next, action ->
            if (action in MODIFIER_TOGGLE_ACTIONS) {
                val result = next(action)
                executeCurrent(store, KeyCommand.SetModifiers8modifierMask(store.state.keyboard)))
                return@middleware result
            }

            when (action) {
                is Action.SendKey -> return@middleware handleSendKey(store, next, action)
                Action.ReleaseLockedModifiers -> {
                    val result = next(action)
                    executeCurrent(store, KeyCommand.SetModifiers(modifierMask(store.state.keyboard)))
                    return@middleware result
              }
                is Action.KeyDown -> executeCurrent(store, KeyCommand.KeyDown(action.code, action.mods))
                is Action.KeyUp -> executeCurrent(store, KeyCommand.KeyUp(action.code))
                is Action.MoveMouse -> executeCurrent(store, KeyCommand.MoveMouse(action.dx, action.dy))
                Action.LeftClick -> launchMouseClick(store, MOUSE_BUTTON_LEFT)
                Action.RightClick -> launchMouseClick(store, MOUSE_BUTTON_RIGHT)
                Action.MiddleClick -> launchMouseClick(store, MOUSE_BUTTON_MIDDLE)
                is Action.ScrollVertical -> executeCurrent(store, KeyCommand.ScrollVertical(action.delta))
                is Action.ScrollHorizontal -> executeCurrent(store, KeyCommand.ScrollHorizontal(action.delta))
                Action.ToggleCapsLock -> launchLockKey(store, CAPS_LOCK_KEY.toByte())
                Action.ToggleScrollLock -> launchLockKey(store, SCROLL_LOCK_KEY.toByte())
                Action.StartDiscovery -> executeCurrent(store, KeyCommand.StartDiscovery)
                Action.StopDiscovery -> executeCurrent(store, KeyCommand.StopDiscovery)
                is Action.PairDevice -> executeCurrent(store, KeyCommand.PairDevice(action.device))
                is Action.ConnectDevice -> executeCurrent(store, KeyCommand.ConnectDevice(action.device))
                Action.DisconnectDevice -> executeCurrent(store, KeyCommand.DisconnectDevice)
                is Action.ForgetDevice -> executeCurrent(store, KeyCommand.ForgetDevice(action.device, action.unpair))
                is Action.SetDefaultDevice -> executeCurrent(store, KeyCommand.SetDefaultDevice(action.device))
                is Action.RenameDevice -> executeCurrent(store, KeyCommand.RenameDevice(action.device, action.alias))
                is Action.MouseButtonDown -> executeCurrent(store, KeyCommand.MouseButtonDown(action.button))
                Action.MouseButtonUp -> executeCurrent(store, KeyCommand.MouseButtonUp)
            }
            next(action)
        }

    private fun modifierMask(k: KeyboardState): Int {
        var mods = 0
        if (k.ctrl) mods = mods or MODIFIER_CTRL
        if (k.shift) mods = mods or MODIFIER_SHIFT
        if (k.alt) mods = mods or MODIFIER_ALT
        if (k.gui) mods = mods or MODIFIER_GUI
        return mods
    }

    private fun handleSendKey(
        store: Store<AppState>,
        next: (Any) -> Any,
        action: Action.SendKey,
    ): Any {
        val mask = modifierMask(store.state.keyboard)
        val mods = action.mods or mask
        val current = sender
        if (current == null) {
            report(store, missingSender())
        } else {
            scope.launch {
                inputSequenceMutex.withLock {
                    try {
                        report(store, current.execute(KeyCommand.KeyDown(action.code, mods)))
                        delay(KEY_PRESS_HOLD_MS)
                    } finally {
                        report(store, current.execute(KeyCommand.KeyUp(action.code)))
                    }
                }
            }
        }
        val result = next(action.copy(mods = mods))
        if (mask != 0) store.dispatch(Action.ReleaseLockedModifiers)
        return result
    }

    private fun launchMouseClick(
        store: Store<AppState>,
        button: Int,
    ) {
        val current = sender
        if (current == null) {
            report(store, missingSender())
            return
        }
        scope.launch {
            inputSequenceMutex.withLock {
                try {
                    report(store, current.execute(KeyCommand.MouseButtonDown(button)))
                    delay(MOUSE_CLICK_HOLD_MS)
                } finally {
                    report(store, current.execute(KeyCommand.MouseButtonUp))
                }
            }
        }
    }

    private fun launchLockKey(
        store: Store<AppState>,
        code: Byte,
    ) {
        val current = sender
        if (current == null) {
            report(store, missingSender())
            return
        }
        val mods = modifierMask(store.state.keyboard)
        scope.launch {
            inputSequenceMutex.withLock {
                try {
                    report(store, current.execute(KeyCommand.KeyDown(code, mods)))
                    delay(KEY_PRESS_HOLD_MS)
                } finally {
                    report(store, current.execute(KeyCommand.KeyUp(code))
                }
            }
        }
    }

    private fun executeCurrent(
        store: Store<AppState>,
        command: KeyCommand,
    ): CommandResult {
        val current = sender
        val result = current?.execute(command) ?: missingSender()
        report(store, result)
        return result
    }

    private fun report(
        store: Store<AppState>,
        result: CommandResult,
    ) {
        when (result) {
            CommandResult.Success -> {
                if (store.state.backend.lastCommandResult != null) store.dispatch(Action.ClearCommandResult)
            }
            is CommandResult.Unsupported -> {
                store.dispatch(Action.ReportCommandResult(result))
                store.dispatch(Action.UpdateMessage(result.message))
            }
            is CommandResult.Failure -> {
                store.dispatch(Action.ReportCommandResult(result))
                store.dispatch(Action.UpdateMessage(result.error.message))
                if (result.error.code == CommandErrorCode.SENDER_UNAVAILABLE) {
                    store.dispatch(Action.UpdateSenderAvailable(false))
                }
            }
        }
    }

    private fun missingSender(): CommandResult.Failure =
        CommandResult.Failure(
            CommandError(
                CommandErrorCode.SENDER_UNAVAILABLE,
                "Bluetooth input backend is not ready; command was not sent.",
            ),
        )
}
