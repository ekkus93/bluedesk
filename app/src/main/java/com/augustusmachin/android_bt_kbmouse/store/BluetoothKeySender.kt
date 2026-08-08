package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendCapabilities
import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryFailureCode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryResult
import com.augustusmachin.android_bt_kbmouse.IBluetoothService

/** Explicit Classic HID command bridge. */
class BluetoothKeySender(private val svc: IBluetoothService) : KeySender {
    override val backend: BackendMode = BackendMode.CLASSIC_HID
    override val capabilities: BackendCapabilities = BackendCapabilitySets.classic

    override fun execute(command: KeyCommand): CommandResult =
        try {
            when (command) {
                is KeyCommand.KeyDown -> mapDelivery(svc.pressKey(command.code, command.mods))
                is KeyCommand.KeyUp -> mapDelivery(svc.releaseKey(command.code))
                is KeyCommand.MoveMouse -> mapDelivery(svc.sendMouseMove(command.dx, command.dy))
                is KeyCommand.MouseButtonDown -> mapDelivery(svc.mouseButtonDown(command.button))
                KeyCommand.MouseButtonUp -> mapDelivery(svc.mouseButtonUp())
                is KeyCommand.ScrollVertical -> mapDelivery(svc.sendScroll(command.delta))
                is KeyCommand.ScrollHorizontal -> mapDelivery(svc.sendScrollH(command.delta))
                is KeyCommand.SetModifiers -> mapDelivery(svc.setModifiers(command.mods))
                KeyCommand.StartDiscovery ->
                    if (svc.startDiscovery()) CommandResult.Success else failure(CommandErrorCode.DISCOVERY_FAILED, "Failed to start Bluetooth discovery")
                KeyCommand.StopDiscovery ->
                    if (svc.stopDiscovery()) CommandResult.Success else failure(CommandErrorCode.DISCOVERY_FAILED, "Failed to stop Bluetooth discovery")
                is KeyCommand.PairDevice -> successAfter { svc.pairDevice(command.device) }
                is KeyCommand.ConnectDevice -> successAfter { svc.connectDevice(command.device) }
                KeyCommand.DisconnectDevice -> successAfter { svc.disconnectDevice() }
                is KeyCommand.ForgetDevice -> successAfter { svc.forgetDevice(command.device, command.unpair) }
                is KeyCommand.SetDefaultDevice -> successAfter { svc.setDefaultDevice(command.device) }
                is KeyCommand.RenameDevice -> successAfter { svc.setAlias(command.device, command.alias) }
            }
        } catch (e: SecurityException) {
            failure(CommandErrorCode.PERMISSION_DENIED, e.message ?: "Bluetooth permission denied")
        } catch (e: IllegalStateException) {
            failure(CommandErrorCode.SERVICE_UNAVAILABLE, e.message ?: "Bluetooth service is unavailable")
        } catch (e: IllegalArgumentException) {
            failure(CommandErrorCode.INVALID_STATE, e.message ?: "Bluetooth command was rejected")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            failure(CommandErrorCode.TRANSPORT_FAILURE, e.message ?: "Bluetooth command failed")
        }

    fun sendKeyDown(code: Byte, mods: Int): CommandResult = execute(KeyCommand.KeyDown(code, mods))
    fun sendKeyUp(code: Byte): CommandResult = execute(KeyCommand.KeyUp(code))
    fun moveMouse(dx: Int, dy: Int): CommandResult = execute(KeyCommand.MoveMouse(dx, dy))
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
    fun forgetDevice(device: BluetoothDevice, unpair: Boolean): CommandResult = execute(KeyCommand.ForgetDevice(device, unpair))
    fun setDefaultDevice(device: BluetoothDevice): CommandResult = execute(KeyCommand.SetDefaultDevice(device))
    fun renameDevice(device: BluetoothDevice, alias: String): CommandResult = execute(KeyCommand.RenameDevice(device, alias))

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

    private fun mapDelivery(result: HidDeliveryResult?): CommandResult =
        when (result) {
            HidDeliveryResult.Sent -> CommandResult.Success
            is HidDeliveryResult.Unsupported -> CommandResult.Unsupported("Classic HID report", result.reason)
            is HidDeliveryResult.Failure ->
                failure(
                    when (result.code) {
                        HidDeliveryFailureCode.PERMISSION_DENIED -> CommandErrorCode.PERMISSION_DENIED
                        HidDeliveryFailureCode.REPORT_REJECTED -> CommandErrorCode.REPORT_REJECTED
                        HidDeliveryFailureCode.DEVICE_MISSING,
                        HidDeliveryFailureCode.HID_PROXY_MISSING,
                        HidDeliveryFailureCode.UNSUPPORTED_API,
                        -> CommandErrorCode.SERVICE_UNAVAILABLE
                        HidDeliveryFailureCode.TRANSPORT_EXCEPTION -> CommandErrorCode.TRANSPORT_FAILURE
                    },
                    result.message,
                )
            null -> failure(CommandErrorCode.SERVICE_UNAVAILABLE, "Classic HID service returned no delivery result")
        }

    private inline fun successAfter(operation: () -> Unit): CommandResult {
        operation()
        return CommandResult.Success
    }

    private fun failure(code: CommandErrorCode, message: String) = CommandResult.Failure(CommandError(code, message))
}
