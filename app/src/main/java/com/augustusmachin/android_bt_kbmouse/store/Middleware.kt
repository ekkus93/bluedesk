package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.reduxkotlin.middleware
import org.reduxkotlin.Store

/**
 * Middleware that forwards HID and connection intent actions to platform services.
 * The [KeySender] abstraction allows the activity/service layer to bridge actual HID calls
 * without leaking platform dependencies into reducers.
 */
class KeySenderMiddleware(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    @Volatile
    var sender: KeySender? = null

    fun create() = middleware<AppState> { store: Store<AppState>, next, action ->
        // Modifier toggles need to update store state first, then push modifier mask to device
        if (action == Action.ToggleCtrl || action == Action.ToggleShift || action == Action.ToggleAlt || action == Action.ToggleGui) {
            val res = next(action)
            val k = store.state.keyboard
            var mods = 0
            if (k.ctrl) mods = mods or 0x01
            if (k.shift) mods = mods or 0x02
            if (k.alt) mods = mods or 0x04
            if (k.gui) mods = mods or 0x08
            sender?.setModifiers(mods)
            return@middleware res
        }

        when (action) {
            is Action.SendKey -> {
                val k = store.state.keyboard
                var mods = action.mods
                if (k.ctrl) mods = mods or 0x01
                if (k.shift) mods = mods or 0x02
                if (k.alt) mods = mods or 0x04
                if (k.gui) mods = mods or 0x08
                val s = sender
                if (s != null) {
                    scope.launch {
                        try {
                            s.sendKeyDown(action.code, mods)
                            delay(40)
                        } finally {
                            s.sendKeyUp(action.code)
                        }
                    }
                }
                val result = next(action.copy(mods = mods))
                if (k.ctrl || k.shift || k.alt || k.gui) {
                    store.dispatch(Action.ReleaseLockedModifiers)
                }
                return@middleware result
            }
            Action.ReleaseLockedModifiers -> {
                val res = next(action)
                val k = store.state.keyboard
                var mods = 0
                if (k.ctrl) mods = mods or 0x01
                if (k.shift) mods = mods or 0x02
                if (k.alt) mods = mods or 0x04
                if (k.gui) mods = mods or 0x08
                sender?.setModifiers(mods)
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

            Action.StartDiscovery -> {
                // Update UI state in the store so UIs/tests see the scanning message
                try {
                    store.dispatch(Action.UpdateMessage("Scanning for devices..."))
                    store.dispatch(Action.UpdateIsScanning(true))
                } catch (_: Exception) {}
                sender?.startDiscovery()
            }
            Action.StopDiscovery -> {
                try {
                    store.dispatch(Action.UpdateMessage(null))
                    store.dispatch(Action.UpdateIsScanning(false))
                } catch (_: Exception) {}
                sender?.stopDiscovery()
            }
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
}

interface KeySender {
    fun sendKeyDown(code: Byte, mods: Int) {}
    fun sendKeyUp(code: Byte) {}
    fun moveMouse(dx: Int, dy: Int) {}
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
    fun forgetDevice(device: android.bluetooth.BluetoothDevice, unpair: Boolean) {}
    fun setDefaultDevice(device: android.bluetooth.BluetoothDevice) {}
    fun renameDevice(device: android.bluetooth.BluetoothDevice, alias: String) {}
    fun setModifiers(mods: Int) {}
}
