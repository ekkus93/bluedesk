package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendCapabilities
import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode
import com.augustusmachin.android_bt_kbmouse.HidReportBuilder
import com.augustusmachin.android_bt_kbmouse.HogpNotifier

private const val MAX_ROLLOVER_KEYS = 6

/**
 * Explicit BLE HOGP command bridge. BLE is host-initiated, so Classic discovery/pair/connect
 * operations and wheel scrolling return [CommandResult.Unsupported] instead of silently doing
 * nothing.
 */
class BleHogpKeySender(private val notifier: HogpNotifier) : KeySender {
    override val backend: BackendMode = BackendMode.BLE_HOGP
    override val capabilities: BackendCapabilities = BackendCapabilitySets.bleHogp

    @Volatile private var modifierByte: Int = 0
    private val pressedKeys = mutableListOf<Byte>()

    @Volatile private var buttonsMask: Int = 0

    private fun buildKeyReport(): ByteArray =
        HidReportBuilder.keyboardReport(
            modifierByte,
            synchronized(pressedKeys) { pressedKeys.take(MAX_ROLLOVER_KEYS).toList() },
        )

    private fun buildMouseReport(
        dx: Int = 0,
        dy: Int = 0,
    ): ByteArray = HidReportBuilder.mouseReportSimple(buttonsMask, dx, dy)

    override fun execute(command: KeyCommand): CommandResult {
        return try {
            when (command) {
                is KeyCommand.KeyDown -> successAfter { sendKeyDownInternal(command.code, command.mods) }
                is KeyCommand.KeyUp -> successAfter { sendKeyUpInternal(command.code) }
                is KeyCommand.MoveMouse -> successAfter { notifier.notifyMouse(buildMouseReport(command.dx, command.dy)) }
                is KeyCommand.MouseButtonDown ->
                    successAfter {
                        buttonsMask = buttonsMask or command.button
                        notifier.notifyMouse(buildMouseReport())
                    }
                KeyCommand.MouseButtonUp ->
                    successAfter {
                        buttonsMask = 0
                        notifier.notifyMouse(buildMouseReport())
                    }
                is KeyCommand.SetModifiers ->
                    successAfter {
                        modifierByte = command.mods
                        notifier.notifyKeyboard(buildKeyReport())
                    }
                is KeyCommand.ScrollVertical -> unsupported("vertical scroll")
                is KeyCommand.ScrollHorizontal -> unsupported("horizontal scroll")
                KeyCommand.StartDiscovery -> unsupported("device discovery")
                KeyCommand.StopDiscovery -> unsupported("device discovery")
                is KeyCommand.PairDevice -> unsupported("Classic pairing")
                is KeyCommand.ConnectDevice -> unsupported("explicit connect")
                KeyCommand.DisconnectDevice -> unsupported("explicit disconnect")
                is KeyCommand.ForgetDevice -> unsupported("forget/unpair")
                is KeyCommand.SetDefaultDevice -> unsupported("default device")
                is KeyCommand.RenameDevice -> unsupported("device rename")
            }
        } catch (e: SecurityException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.PERMISSION_DENIED, e.message ?: "BLE permission denied"),
            )
        } catch (e: IllegalStateException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.SERVICE_UNAVAILABLE, e.message ?: "BLE HOGP is unavailable"),
            )
        } catch (e: IllegalArgumentException) {
            CommandResult.Failure(
                CommandError(CommandErrorCode.INVALID_STATE, e.message ?: "BLE HOGP command was rejected"),
            )
        }
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
    ) {
        modifierByte = mods
        synchronized(pressedKeys) {
            if (!pressedKeys.contains(code)) pressedKeys.add(code)
        }
        notifier.notifyKeyboard(buildKeyReport())
    }

    private fun sendKeyUpInternal(code: Byte) {
        synchronized(pressedKeys) { pressedKeys.remove(code) }
        notifier.notifyKeyboard(buildKeyReport())
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

    private inline fun successAfter(operation: () -> Unit): CommandResult {
        operation()
        return CommandResult.Success
    }

    private fun unsupported(operation: String): CommandResult.Unsupported =
        CommandResult.Unsupported(
            operation,
            "BLE HOGP does not support $operation; connect and pair from the host.",
        )
}
