package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.reduxkotlin.Store
import org.reduxkotlin.middleware

private const val MODIFIER_CTRL = 0x01
private const val MODIFIER_SHIFT = 0x02
private const val MODIFIER_ALT = 0x04
private const val MODIFIER_GUI = 0x08
private const val KEY_PRESS_HOLD_MS = 40L

private val MODIFIER_TOGGLE_ACTIONS =
    setOf(Action.ToggleCtrl, Action.ToggleShift, Action.ToggleAlt, Action.ToggleGui)

/**
 * Middleware that forwards HID and connection intent actions to platform services.
 * The [KeySender] abstraction allows the activity/service layer to bridge actual HID calls
 * without leaking platform dependencies into reducers.
 */
class KeySenderMiddleware(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    @Volatile
    var sender: KeySender? = null

    fun create() =
        middleware<AppState> { store: Store<AppState>, next, action ->
            // Modifier toggles update store state first, then push the mask to the device.
            if (action in MODIFIER_TOGGLE_ACTIONS) {
                val res = next(action)
                sender?.setModifiers(modifierMask(store.state.keyboard))
                return@middleware res
            }

            when (action) {
                is Action.SendKey -> return@middleware handleSendKey(store, next, action)
                Action.ReleaseLockedModifiers -> {
                    val res = next(action)
                    sender?.setModifiers(modifierMask(store.state.keyboard))
                    return@middleware res
                }
                is Action.KeyDown -> sender?.sendKeyDown(action.code, action.mods)
                is Action.KeyUp -> sender?.sendKeyUp(action.code)
                is Action.MoveMouse -> sender?.moveMouse(action.dx, action.dy)
                is Action.LeftClick -> sender?.leftClick()
                is Action.RightClick -> sender?.rightClick()
                is Action.MiddleClick -> sender?.middleClick()
                is Action.ScrollVertical -> sender?.scrollVertical(action.delta)
                is Action.ScrollHorizontal -> sender?.scrollHorizontal(action.delta)
                Action.ToggleCapsLock -> sender?.toggleCapsLock()
                Action.ToggleScrollLock -> sender?.toggleScrollLock()
                Action.StartDiscovery -> handleStartDiscovery(store)
                Action.StopDiscovery -> handleStopDiscovery(store)
                is Action.PairDevice -> sender?.pairDevice(action.device)
                is Action.ConnectDevice -> sender?.connectDevice(action.device)
                Action.DisconnectDevice -> sender?.disconnectDevice()
                is Action.ForgetDevice -> sender?.forgetDevice(action.device, action.unpair)
                is Action.SetDefaultDevice -> sender?.setDefaultDevice(action.device)
                is Action.RenameDevice -> sender?.renameDevice(action.device, action.alias)
                is Action.MouseButtonDown -> sender?.mouseButtonDown(action.button)
                Action.MouseButtonUp -> sender?.mouseButtonUp()
            }
            next(action)
        }

    /** HID modifier mask from the current sticky-modifier keyboard state. */
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
        val k = store.state.keyboard
        val mask = modifierMask(k)
        val mods = action.mods or mask
        val s = sender
        if (s != null) {
            scope.launch {
                try {
                    s.sendKeyDown(action.code, mods)
                    delay(KEY_PRESS_HOLD_MS)
                } finally {
                    s.sendKeyUp(action.code)
                }
            }
        }
        val result = next(action.copy(mods = mods))
        if (mask != 0) {
            store.dispatch(Action.ReleaseLockedModifiers)
        }
        return result
    }

    private fun handleStartDiscovery(store: Store<AppState>) {
        // Update UI state in the store so UIs/tests see the scanning message
        try {
            store.dispatch(Action.UpdateMessage("Scanning for devices..."))
            store.dispatch(Action.UpdateIsScanning(true))
        } catch (_: Exception) {
        }
        sender?.startDiscovery()
    }

    private fun handleStopDiscovery(store: Store<AppState>) {
        try {
            store.dispatch(Action.UpdateMessage(null))
            store.dispatch(Action.UpdateIsScanning(false))
        } catch (_: Exception) {
        }
        sender?.stopDiscovery()
    }
}

interface KeySender {
    fun sendKeyDown(
        code: Byte,
        mods: Int,
    ) {}

    fun sendKeyUp(code: Byte) {}

    fun moveMouse(
        dx: Int,
        dy: Int,
    ) {}

    fun leftClick() {}

    fun rightClick() {}

    fun middleClick() {}

    fun mouseButtonDown(button: Int) {}

    fun mouseButtonUp() {}

    fun scrollVertical(delta: Int) {}

    fun scrollHorizontal(delta: Int) {}

    fun toggleCapsLock() {}

    fun toggleScrollLock() {}

    fun startDiscovery() {}

    fun stopDiscovery() {}

    fun pairDevice(device: android.bluetooth.BluetoothDevice) {}

    fun connectDevice(device: android.bluetooth.BluetoothDevice) {}

    fun disconnectDevice() {}

    fun forgetDevice(
        device: android.bluetooth.BluetoothDevice,
        unpair: Boolean,
    ) {}

    fun setDefaultDevice(device: android.bluetooth.BluetoothDevice) {}

    fun renameDevice(
        device: android.bluetooth.BluetoothDevice,
        alias: String,
    ) {}

    fun setModifiers(mods: Int) {}
}
