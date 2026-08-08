package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.BackendCapabilitySets
import com.augustusmachin.android_bt_kbmouse.BackendMode

/** Test-only sender that records the explicit command contract without production no-op defaults. */
class RecordingKeySender(
    override val backend: BackendMode = BackendMode.CLASSIC_HID,
    private val resultFor: (KeyCommand) -> CommandResult = { CommandResult.Success },
) : KeySender {
    override val capabilities = BackendCapabilitySets.forMode(backend)
    val commands = mutableListOf<KeyCommand>()

    override fun execute(command: KeyCommand): CommandResult {
        commands += command
        return resultFor(command)
    }
}
