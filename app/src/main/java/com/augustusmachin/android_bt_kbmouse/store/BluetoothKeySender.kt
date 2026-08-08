package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendCapabilities
import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.IBluetoothService

/** Explicit Classic HID command bridge. */
class BluetoothKeySender(private val svc: IBluetoothService) : KeySender {
    override val backend: BackendMode = BackendMode.CLASSIC_HID
    override val capabilities: BackendCapabilities = BackendCapabilitySets.classic

    override fun execute(command: KeyCommand): CommandResult =
        try {
            when (command) {
                is KeyCommand.KeyDown -> svc.pressKey(command.code, command.mods)
                is KeyCommand.KeyUp -> svc.releaseKey(command.code)
                is KeyCommand.MoveMouse -> svc.sendMouseMove(command.dx, command.dy)
                is KeyCommand.MouseButtonDown -> svc.mouseButtonDown(command.button)
                KeyCommand.MouseButtonUp -> svc.mouseButtonUp()
                is KeyCommand.ScrollVertical -> svc.sendScroll(command.delta)
                is KeyCommand.ScrollHorizontal -> svc.sendScrollH(command.delta)
                is KeyCommand.SetModifiers -> svc.setModifiers(command.mods)
                KeyCommand.StartDiscovery -> svc.startDiscovery()
                KeyCommand.StopDiscovery -> svc.stopDiscovery()
                is KeyCommand.PairDevice -> svc.pairDevice(command.device)
                is KeyCommand.ConnectDevice -> svc.connectDevice(command.device)
                KeyCommand.DisconnectDevice -> svc.disconnectDevice()
                is KeyCommand.ForgetDevice -> svc.forgetDevice(command.device, command.unpair)
                is KeyCommand.SetDefaultDevice -> svc.setDefaultDevice(command.device)
                is KeyCommand.RenameDevice -> svc.setAlias(command.device, command.alias)
            }
            CommandResult.Success
        } catch (e: SecurityException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.PERMISSION_DENIED, e.message ?: "Bluetooth permission denied"),
            )
        } catch (e: IllegalStateException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.SERVICE_UNAVAILABLE, e.message ?: "Bluetooth service is unavailable"),
            )
        } catch (e: IllegalArgumentException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.INVALID_STATE, e.message ?: "Bluetooth command was rejected"),
            )
        }

    fun sendKeyDown(
        code: Byte,
        mods: Int,
    ): CommandResult = execute(KeyCommand.KeyDown(code, mods))

    fun sendKeyUp(code: Byte): CommandResult = execute(KeyCommand.KeyUp(code))

    fun moveMouse(
        dx: Int,
        dy: Int,
    ): CommandResult = execute(KeyCommand.MoveMouse(dx, dy))

    fun leftClick(): CommandResult = click(0x01)

    fun rightClick(): CommandResult = click(0x02)

    fun middleClick(): CommandResult = click(0x04)

    fun scrollVertical(delta: Int): CommandResult = execute(KeyCommand.ScrollVertical(delta))

    fun scrollHorizontal(delta: Int): CommandResult = execute(KeyCommand.ScrollHorizontal(delta))

    fun toggleCapsLock(): CommandResult = keyPress(0x39.toByte())

    fun toggleScrollLock(): CommandResult = keyPress(0x47.toByte())

    fun mouseButtonDown(button: Int): CommandResult = execute(KeyCommand.MouseButtonDown(button))

    fun mouseButtonUp(): CommandResult = execute(KeyCommand.MouseButtonUp)

    fun setModifiers(mods: Int): CommandResult = execute(KeyCommand.SetModifiers(mods))

    fun startDiscovery(): CommandResult = execute(KeyCommand.StartDiscovery)

    fun stopDiscovery(): CommandResult = execute(KeyCommand.StopDiscovery)

    fun pairDevice(device: BluetoothDevice): CommandResult = execute(KeyCommand.PairDevice(device))

    fun connectDevice(device: BluetoothDevice): CommandResult = execute(KeyCommand.ConnectDevice(device))

    fun disconnectDevice(): CommandResult = execute(KeyCommand.DisconnectDevice)

    fun forgetDevice(
        device: BluetoothDevice,
        unpair: Boolean,
    ): CommandResult = execute(KeyCommand.ForgetDevice(device, unpair))

    fun setDefaultDevice(device: BluetoothDevice): CommandResult = execute(KeyCommand.SetDefaultDevice(device))

    fun renameDevice(
        device: BluetoothDevice,
        alias: String,
    ): CommandResult = execute(KeyCommand.RenameDevice(device, alias))

    private fun click(button: Int): CommandResult {
        val down = execute(KeyCommand.MouseButtonDown(button))
        if (down != CommandResult.Success) return down
        return execute(KeyCommand.MouseButtonUp)
    }

    private fun keyPress(code: Byte): CommandResult {
        val down = execute(KeyCommand.KeyDown(code, 0))
        if (down != CommandResult.Success) return down
        return execute(KeyCommand.KeyUp(code))
    }
}
