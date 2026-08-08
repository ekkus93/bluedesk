package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendCapabilities
import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryFailureCode
import com.augustusmachin.android_bt_kbmouse.HidDeliveryResult
import com.augustusmachin.android_bt_kbmouse.HidReportBuilder
import com.augustusmachin.android_bt_kbmouse.HogpNotifier

private const val MAX_ROLLOVER_KEYS = 6

/** Explicit BLE HOGP command bridge with no Classic-operation no-ops. */
class BleHogpKeySender(private val notifier: HogpNotifier) : KeySender {
    override val backend: BackendMode = BackendMode.BLE_HOGP
    override val capabilities: BackendCapabilities = BackendCapabilitySets.bleHogp

    @Volatile
    private var modifierByte: Int = 0

    private val pressedKeys = mutableListOf<Byte>()

    @Volatile
    private var buttonsMask: Int = 0

    private fun buildKeyReport(): ByteArray =
        HidReportBuilder.keyboardReport(
            modifierByte,
            synchronized(pressedKeys) { pressedKeys.take(MAX_ROLLOVER_KEYS).toList() },
        )

    private fun buildMouseReport(
        dx: Int = 0,
        dy: Int = 0,
    ): ByteArray = HidReportBuilder.mouseReportSimple(buttonsMask, dx, dy)

    override fun execute(command: KeyCommand): CommandResult =
        try {
            when (command) {
                is KeyCommand.KeyDown -> mapDelivery(sendKeyDownInternal(command.code, command.mods))
                is KeyCommand.KeyUp -> mapDelivery(sendKeyUpInternal(command.code))
                is KeyCommand.MoveMouse -> mapDelivery(notifier.notifyMouse(buildMouseReport(command.dx, command.dy)))
                is KeyCommand.MouseButtonDown -> {
                    buttonsMask = buttonsMask or command.button
                    mapDelivery(notifier.notifyMouse(buildMouseReport()))
                }
                KeyCommand.MouseButtonUp -> {
                    buttonsMask = 0
                    mapDelivery(notifier.notifyMouse(buildMouseReport()))
                }
                is KeyCommand.SetModifiers -> {
                    modifierByte = command.mods
                    mapDelivery(notifier.notifyKeyboard(buildKeyReport()))
                }
                is KeyCommand.ScrollVertical -> unsupported("vertical scroll")
                is KeyCommand.ScrollHorizontal -> unsupported("horizontal scroll")
                KeyCommand.StartDiscovery,
                KeyCommand.StopDiscovery,
                -> unsupported("device discovery")
                is KeyCommand.PairDevice -> unsupported("Classic pairing")
                is KeyCommand.ConnectDevice -> unsupported("explicit connect")
                KeyCommand.DisconnectDevice -> unsupported("explicit disconnect")
                is KeyCommand.ForgetDevice -> unsupported("forget/unpair")
                is KeyCommand.SetDefaultDevice -> unsupported("default device")
                is KeyCommand.RenameDevice -> unsupported("device rename")
            }
        } catch (e: SecurityException) {
            failure(CommandErrorCode.PERMISSION_DENIED, e.message ?: "BLE permission denied")
        } catch (e: IllegalStateException) {
            failure(CommandErrorCode.SERVICE_UNAVAILABLE, e.message ?: "BLE HOGP is unavailable")
        } catch (e: IllegalArgumentException) {
            failure(CommandErrorCode.INVALID_STATE, e.message ?: "BLE HOGP command was rejected")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            failure(CommandErrorCode.TRANSPORT_FAILURE, e.message ?: "BLE HOGP command failed")
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

    fun mouseButtonDown(button: Int): CommandResult = execute(KeyCommand.MouseButtonDown(button))

    fun mouseButtonUp(): CommandResult = execute(KeyCommand.MouseButtonUp)

    fun scrollVertical(delta: Int): CommandResult = execute(KeyCommand.ScrollVertical(delta))

    fun scrollHorizontal(delta: Int): CommandResult = execute(KeyCommand.ScrollHorizontal(delta))

    fun toggleCapsLock(): CommandResult = keyPress(0x39.toByte())

    fun toggleScrollLock(): CommandResult = keyPress(0x47.toByte())

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

    private fun sendKeyDownInternal(
        code: Byte,
        mods: Int,
    ): HidDeliveryResult {
        modifierByte = mods
        synchronized(pressedKeys) {
            if (!pressedKeys.contains(code)) pressedKeys.add(code)
        }
        return notifier.notifyKeyboard(buildKeyReport())
    }

    private fun sendKeyUpInternal(code: Byte): HidDeliveryResult {
        synchronized(pressedKeys) { pressedKeys.remove(code) }
        return notifier.notifyKeyboard(buildKeyReport())
    }

    private fun click(button: Int): CommandResult {
        val down = execute(KeyCommand.MouseButtonDown(button))
        if (down != CommandResult.Success) return down
        return execute(KeyCommand.MouseButtonUp)
    }

    private fun keyPress(code: Byte): CommandResult {
        val down = execute(KeyCommand.KeyDown(code, modifierByte))
        if (down != CommandResult.Success) return down
        return execute(KeyCommand.KeyUp(code))
    }

    private fun mapDelivery(result: HidDeliveryResult): CommandResult =
        when (result) {
            HidDeliveryResult.Sent -> CommandResult.Success
            is HidDeliveryResult.Unsupported -> CommandResult.Unsupported("BLE HOGP report", result.reason)
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
        }

    private fun unsupported(operation: String): CommandResult.Unsupported =
        CommandResult.Unsupported(operation, "BLE HOGP does not support $operation; connect and pair from the host.")

    private fun failure(
        code: CommandErrorCode,
        message: String,
    ) = CommandResult.Failure(CommandError(code, message))
}
