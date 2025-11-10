package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.Settings

sealed class Action {
    // Keyboard modifier toggles
    object ToggleCtrl : Action()
    object ToggleShift : Action()
    object ToggleAlt : Action()
    object ToggleGui : Action()
    data class SetCtrlPersist(val persist: Boolean) : Action()
    data class SetShiftPersist(val persist: Boolean) : Action()
    data class SetAltPersist(val persist: Boolean) : Action()
    data class SetGuiPersist(val persist: Boolean) : Action()

    // UI actions
    data class SetOfflinePreview(val enabled: Boolean) : Action()
    data class SetShowExtended(val show: Boolean) : Action()
    data class SetExtendedPage(val page: Int) : Action()

    // HID key/mouse actions handled by middleware
    data class SendKey(val code: Byte, val mods: Int = 0) : Action()
    data class KeyDown(val code: Byte, val mods: Int = 0) : Action()
    data class KeyUp(val code: Byte) : Action()
    data class MoveMouse(val dx: Int, val dy: Int) : Action()
    object LeftClick : Action()
    object RightClick : Action()
    object MiddleClick : Action()
    data class ScrollVertical(val delta: Int) : Action()
    data class ScrollHorizontal(val delta: Int) : Action()
    object ToggleCapsLock : Action()
    object ToggleScrollLock : Action()

    // Connection updates (dispatched by middleware/service listeners)
    data class UpdateDiscoveredDevices(val devices: List<BluetoothDevice>) : Action()
    data class UpdatePairedDevices(val devices: List<BluetoothDevice>) : Action()
    data class UpdateConnectedDevice(val device: BluetoothDevice?) : Action()
    data class UpdateMessage(val message: String?) : Action()
    data class UpdateDefaultDevice(val address: String?) : Action()
    data class UpdateLocks(val caps: Boolean, val scroll: Boolean) : Action()
    data class UpdateIsScanning(val scanning: Boolean) : Action()

    // Commands that trigger side-effects via middleware
    object StartDiscovery : Action()
    object StopDiscovery : Action()
    data class PairDevice(val device: BluetoothDevice) : Action()
    data class ConnectDevice(val device: BluetoothDevice) : Action()
    object DisconnectDevice : Action()
    data class ForgetDevice(val device: BluetoothDevice, val unpair: Boolean) : Action()
    data class SetDefaultDevice(val device: BluetoothDevice) : Action()
    data class RenameDevice(val device: BluetoothDevice, val alias: String) : Action()

    // Settings
    data class UpdateSettings(val settings: Settings) : Action()
    data class UpdateImeOverrides(val overrides: Map<String, Boolean>) : Action()
}
