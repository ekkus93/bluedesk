package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.BackendRuntimeState

sealed class Action {
    object ToggleCtrl : Action()
    object ToggleShift : Action()
    object ToggleAlt : Action()
    object ToggleGui : Action()

    data class SetCtrlPersist(val persist: Boolean) : Action()
    data class SetShiftPersist(val persist: Boolean) : Action()
    data class SetAltPersist(val persist: Boolean) : Action()
    data class SetGuiPersist(val persist: Boolean) : Action()

    data class SetOfflinePreview(val enabled: Boolean) : Action()
    data class SetShowExtended(val show: Boolean) : Action()
    data class SetExtendedPage(val page: Int) : Action()
    data class TrackPreviewKey(val label: String, val ttlMillis: Long = 5_000L, val decorate: Boolean = true) : Action()
    data class AddPreviewKey(val id: Long, val label: String, val decorate: Boolean) : Action()
    data class RemovePreviewKey(val id: Long) : Action()

    data class SendKey(val code: Byte, val mods: Int = 0) : Action()
    data class KeyDown(val code: Byte, val mods: Int = 0) : Action()
    data class KeyUp(val code: Byte) : Action()
    data class MoveMouse(val dx: Int, val dy: Int) : Action()
    object LeftClick : Action()
    object RightClick : Action()
    object MiddleClick : Action()
    data class MouseButtonDown(val button: Int) : Action()
    object MouseButtonUp : Action()
    data class ScrollVertical(val delta: Int) : Action()
    data class ScrollHorizontal(val delta: Int) : Action()
    object ToggleCapsLock : Action()
    object ToggleScrollLock : Action()
    object ReleaseLockedModifiers : Action()

    data class UpdateDiscoveredDevices(val devices: List<BluetoothDevice>) : Action()
    data class UpdatePairedDevices(val devices: List<BluetoothDevice>) : Action()
    data class UpdateConnectedDevice(val device: BluetoothDevice?) : Action()
    data class UpdateConnectedDeviceLabel(val label: String?) : Action()
    data class UpdateConnectedDeviceAddress(val address: String?) : Action()
    data class UpdateMessage(val message: String?) : Action()
    data class UpdateDefaultDevice(val address: String?) : Action()
    data class UpdateLocks(val caps: Boolean, val scroll: Boolean) : Action()
    data class UpdateIsScanning(val scanning: Boolean) : Action()

    data class UpdateSelectedBackend(val backend: BackendMode) : Action()
    data class UpdateBackendRuntime(val runtime: BackendRuntimeState) : Action()
    data class UpdateSenderAvailable(val available: Boolean) : Action()
    data class UpdatePermissionsValid(val valid: Boolean) : Action()
    data class ReportCommandResult(val result: CommandResult) : Action()
    object ClearCommandResult : Action()

    object StartDiscovery : Action()
    object StopDiscovery : Action()
    data class PairDevice(val device: BluetoothDevice) : Action()
    data class ConnectDevice(val device: BluetoothDevice) : Action()
    object DisconnectDevice : Action()
    data class ForgetDevice(val device: BluetoothDevice, val unpair: Boolean) : Action()
    data class SetDefaultDevice(val device: BluetoothDevice) : Action()
    data class RenameDevice(val device: BluetoothDevice, val alias: String) : Action()
}
