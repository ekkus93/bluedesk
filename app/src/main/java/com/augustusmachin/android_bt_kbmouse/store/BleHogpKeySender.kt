package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.HidReportBuilder
import com.augustusmachin.android_bt_kbmouse.HogpNotifier

private const val MAX_ROLLOVER_KEYS = 6
private const val MOUSE_BUTTON_MIDDLE = 0x04
private const val CLICK_HOLD_MS = 10L
private const val KEY_PRESS_HOLD_MS = 40L

/**
 * KeySender bridge for BLE HOGP. Builds HID reports and delivers them via
 * BleHogpService GATT notifications.
 *
 * Keyboard: 8 bytes [mods, reserved=0, key1..key6] — no report-ID prefix
 *           (the Report Reference descriptor on the characteristic identifies it as report ID 1).
 * Mouse:    3 bytes [buttons, dx, dy] — SIMPLE variant, no scroll wheels.
 *
 * Discovery/connection commands are no-ops: BLE HOGP is advertising-based;
 * the host initiates the connection, not us.
 */
class BleHogpKeySender(private val notifier: HogpNotifier) : KeySender {
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

    override fun sendKeyDown(
        code: Byte,
        mods: Int,
    ) {
        modifierByte = mods
        synchronized(pressedKeys) { if (!pressedKeys.contains(code)) pressedKeys.add(code) }
        notifier.notifyKeyboard(buildKeyReport())
    }

    override fun sendKeyUp(code: Byte) {
        synchronized(pressedKeys) { pressedKeys.remove(code) }
        notifier.notifyKeyboard(buildKeyReport())
    }

    override fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        notifier.notifyMouse(buildMouseReport(dx, dy))
    }

    override fun leftClick() {
        click(0x01)
    }

    override fun rightClick() {
        click(0x02)
    }

    override fun middleClick() {
        click(MOUSE_BUTTON_MIDDLE)
    }

    private fun click(mask: Int) {
        buttonsMask = buttonsMask or mask
        notifier.notifyMouse(buildMouseReport())
        try {
            Thread.sleep(CLICK_HOLD_MS)
        } catch (_: InterruptedException) {
        }
        buttonsMask = buttonsMask and mask.inv()
        notifier.notifyMouse(buildMouseReport())
    }

    override fun mouseButtonDown(button: Int) {
        buttonsMask = buttonsMask or button
        notifier.notifyMouse(buildMouseReport())
    }

    override fun mouseButtonUp() {
        buttonsMask = 0
        notifier.notifyMouse(buildMouseReport())
    }

    override fun toggleCapsLock() {
        sendKeyDown(0x39.toByte(), modifierByte)
        try {
            Thread.sleep(KEY_PRESS_HOLD_MS)
        } catch (_: InterruptedException) {
        }
        sendKeyUp(0x39.toByte())
    }

    override fun toggleScrollLock() {
        sendKeyDown(0x47.toByte(), modifierByte)
        try {
            Thread.sleep(KEY_PRESS_HOLD_MS)
        } catch (_: InterruptedException) {
        }
        sendKeyUp(0x47.toByte())
    }

    override fun setModifiers(mods: Int) {
        modifierByte = mods
        notifier.notifyKeyboard(buildKeyReport())
    }
}
