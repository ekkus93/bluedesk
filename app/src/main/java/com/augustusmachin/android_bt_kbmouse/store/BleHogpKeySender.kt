package com.augustusmachin.android_bt_kbmouse.store

import com.augustusmachin.android_bt_kbmouse.BleHogpService
import com.augustusmachin.android_bt_kbmouse.HidReportBuilder

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
class BleHogpKeySender(private val svc: BleHogpService) : KeySender {
    @Volatile private var modifierByte: Int = 0
    private val pressedKeys = mutableListOf<Byte>()

    @Volatile private var buttonsMask: Int = 0

    private fun buildKeyReport(): ByteArray =
        HidReportBuilder.keyboardReport(modifierByte, synchronized(pressedKeys) { pressedKeys.take(6).toList() })

    private fun buildMouseReport(
        dx: Int = 0,
        dy: Int = 0,
    ): ByteArray =
        HidReportBuilder.mouseReportSimple(buttonsMask, dx, dy)

    override fun sendKeyDown(
        code: Byte,
        mods: Int,
    ) {
        modifierByte = mods
        synchronized(pressedKeys) { if (!pressedKeys.contains(code)) pressedKeys.add(code) }
        svc.notifyKeyboard(buildKeyReport())
    }

    override fun sendKeyUp(code: Byte) {
        synchronized(pressedKeys) { pressedKeys.remove(code) }
        svc.notifyKeyboard(buildKeyReport())
    }

    override fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        svc.notifyMouse(buildMouseReport(dx, dy))
    }

    override fun leftClick() {
        click(0x01)
    }

    override fun rightClick() {
        click(0x02)
    }

    override fun middleClick() {
        click(0x04)
    }

    private fun click(mask: Int) {
        buttonsMask = buttonsMask or mask
        svc.notifyMouse(buildMouseReport())
        try {
            Thread.sleep(10)
        } catch (_: InterruptedException) {
        }
        buttonsMask = buttonsMask and mask.inv()
        svc.notifyMouse(buildMouseReport())
    }

    override fun mouseButtonDown(button: Int) {
        buttonsMask = buttonsMask or button
        svc.notifyMouse(buildMouseReport())
    }

    override fun mouseButtonUp() {
        buttonsMask = 0
        svc.notifyMouse(buildMouseReport())
    }

    override fun toggleCapsLock() {
        sendKeyDown(0x39.toByte(), modifierByte)
        try {
            Thread.sleep(40)
        } catch (_: InterruptedException) {
        }
        sendKeyUp(0x39.toByte())
    }

    override fun toggleScrollLock() {
        sendKeyDown(0x47.toByte(), modifierByte)
        try {
            Thread.sleep(40)
        } catch (_: InterruptedException) {
        }
        sendKeyUp(0x47.toByte())
    }

    override fun setModifiers(mods: Int) {
        modifierByte = mods
        svc.notifyKeyboard(buildKeyReport())
    }
}
