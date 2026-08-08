package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.BackendCapabilities
import com.augustusmachin.android_bt_kbmouse.BackendMode

enum class CommandErrorCode {
    SENDER_UNAVAILABLE,
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    TRANSPORT_FAILURE,
    REPORT_REJECTED,
    DISCOVERY_FAILED,
    INVALID_STATE,
}

data class CommandError(
    val code: CommandErrorCode,
    val message: String,
)

sealed interface CommandResult {
    data object Success : CommandResult

    data class Unsupported(
        val operation: String,
        val message: String,
    ) : CommandResult

    data class Failure(val error: CommandError) : CommandResult
}

sealed interface KeyCommand {
    data class KeyDown(val code: Byte, val mods: Int) : KeyCommand

    data class KeyUp(val code: Byte) : KeyCommand

    data class MoveMouse(val dx: Int, val dy: Int) : KeyCommand

    data class MouseButtonDown(val button: Int) : KeyCommand

    data object MouseButtonUp : KeyCommand

    data class ScrollVertical(val delta: Int) : KeyCommand

    data class ScrollHorizontal(val delta: Int) : KeyCommand

    data class SetModifiers(val mods: Int) : KeyCommand

    data object StartDiscovery : KeyCommand

    data object StopDiscovery : KeyCommand

    data class PairDevice(val device: BluetoothDevice) : KeyCommand

    data class ConnectDevice(val device: BluetoothDevice) : KeyCommand

    data object DisconnectDevice : KeyCommand

    data class ForgetDevice(
        val device: BluetoothDevice,
        val unpair: Boolean,
    ) : KeyCommand

    data class SetDefaultDevice(val device: BluetoothDevice) : KeyCommand

    data class RenameDevice(
        val device: BluetoothDevice,
        val alias: String,
    ) : KeyCommand
}

/**
 * Explicit backend command boundary.
 *
 * There are intentionally no default operation implementations. A backend must inspect every
 * command and return Success, Unsupported, or Failure; adding another backend cannot silently
 * inherit user-visible no-ops.
 */
interface KeySender {
    val backend: BackendMode
    val capabilities: BackendCapabilities

    fun execute(command: KeyCommand): CommandResult
}
