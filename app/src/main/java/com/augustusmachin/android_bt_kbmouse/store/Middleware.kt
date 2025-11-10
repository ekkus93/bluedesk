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
        when (action) {
            is Action.SendKey -> {
                val s = sender
                if (s != null) {
                    scope.launch {
                        try {
                            s.sendKeyDown(action.code, action.mods)
                            delay(40)
                        } finally {
                            s.sendKeyUp(action.code)
                        }
                    }
                }
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
            Action.ToggleNumLock -> sender?.toggleNumLock()
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
    fun scrollVertical(delta: Int) {}
    fun scrollHorizontal(delta: Int) {}
    fun toggleCapsLock() {}
    fun toggleNumLock() {}
    fun toggleScrollLock() {}

    fun startDiscovery() {}
    fun stopDiscovery() {}
    fun pairDevice(device: android.bluetooth.BluetoothDevice) {}
    fun connectDevice(device: android.bluetooth.BluetoothDevice) {}
    fun disconnectDevice() {}
    fun forgetDevice(device: android.bluetooth.BluetoothDevice, unpair: Boolean) {}
    fun setDefaultDevice(device: android.bluetooth.BluetoothDevice) {}
    fun renameDevice(device: android.bluetooth.BluetoothDevice, alias: String) {}
}
