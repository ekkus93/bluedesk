package com.augustusmachin.android_bt_kbmouse

private const val MODIFIER_BYTE_MASK = 0xFF
private const val MAX_ROLLOVER_KEYS = 6
private const val KEY_PRESS_HOLD_MS = 10L

/** Delivers a built HID input report (with its report ID) to the connected device. */
fun interface HidReportTransport {
    fun send(
        reportId: Int,
        report: ByteArray,
    )
}

/**
 * Owns the HID output state — the 6-key rollover chord set, current modifiers,
 * and held mouse buttons — and builds keyboard/mouse reports, handing each to a
 * [HidReportTransport] for delivery. Extracted from [BluetoothService] so that
 * class stays focused on connection/lifecycle, and so the report-building state
 * machine can be unit-tested without Android types.
 *
 * The descriptor variant is read live via [isSimplified] so the sender always
 * matches the service's current descriptor.
 */
class HidReportSender(
    private val isSimplified: () -> Boolean,
    private val transport: HidReportTransport,
) {
    // Multi-key chord state
    private val pressedKeys = LinkedHashSet<Byte>() // preserve insertion order
    private var currentModifiers: Int = 0

    @Volatile private var heldMouseButtons = 0

    fun setModifiers(mods: Int) {
        synchronized(this) { currentModifiers = mods and MODIFIER_BYTE_MASK }
        sendCurrentKeyboardReport()
    }

    fun sendKeyPress(
        keyCode: Byte,
        modifiers: Int,
    ) {
        pressKey(keyCode, modifiers)
        try {
            Thread.sleep(KEY_PRESS_HOLD_MS)
        } catch (_: InterruptedException) {
        }
        releaseKey(keyCode)
    }

    fun pressKey(
        keyCode: Byte,
        modifiers: Int,
    ) {
        synchronized(this) {
            currentModifiers = modifiers and MODIFIER_BYTE_MASK
            if (pressedKeys.size < MAX_ROLLOVER_KEYS) {
                pressedKeys.add(keyCode)
            } else {
                // replace the oldest to ensure a report still goes out
                if (pressedKeys.isNotEmpty()) {
                    pressedKeys.remove(pressedKeys.first())
                    pressedKeys.add(keyCode)
                }
            }
        }
        sendCurrentKeyboardReport()
    }

    fun releaseKey(keyCode: Byte) {
        synchronized(this) { pressedKeys.remove(keyCode) }
        sendCurrentKeyboardReport()
    }

    fun sendMouseMove(
        dx: Int,
        dy: Int,
    ) {
        sendMouseReport(heldMouseButtons, dx, dy, 0, 0)
    }

    fun mouseButtonDown(button: Int) {
        heldMouseButtons = heldMouseButtons or button
        sendMouseReport(heldMouseButtons, 0, 0, 0, 0)
    }

    fun mouseButtonUp() {
        heldMouseButtons = 0
        sendMouseReport(0, 0, 0, 0, 0)
    }

    fun sendLeftClick() = clickMouse(buttonMask = 0x01)

    fun sendRightClick() = clickMouse(buttonMask = 0x02)

    fun sendMiddleClick() = clickMouse(buttonMask = 0x04)

    // Scroll is only available in the FULL descriptor; no-op when SIMPLE is active.
    fun sendScroll(delta: Int) {
        if (!isSimplified()) sendMouseReport(0, 0, 0, delta, 0)
    }

    fun sendScrollH(delta: Int) {
        if (!isSimplified()) sendMouseReport(0, 0, 0, 0, delta)
    }

    private fun sendCurrentKeyboardReport() {
        val keys = synchronized(this) { pressedKeys.toList().take(MAX_ROLLOVER_KEYS) }
        val report = HidReportBuilder.keyboardReport(currentModifiers, keys)
        DebugLog.log(TAG, "kbd mods=" + currentModifiers + " keys=" + keys)
        transport.send(HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), report)
    }

    private fun clickMouse(buttonMask: Int) {
        DebugLog.log(TAG, "mouse click mask=" + buttonMask)
        sendMouseReport(buttonMask, 0, 0, 0, 0)
        try {
            Thread.sleep(KEY_PRESS_HOLD_MS)
        } catch (_: InterruptedException) {
        }
        sendMouseReport(0, 0, 0, 0, 0)
    }

    private fun sendMouseReport(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int,
        hWheel: Int,
    ) {
        val simplified = isSimplified()
        // SIMPLE: 3 bytes [buttons][dx][dy]; FULL: 5 bytes [buttons][dx][dy][wheelV][wheelH]
        val report =
            if (simplified) {
                HidReportBuilder.mouseReportSimple(buttons, dx, dy)
            } else {
                HidReportBuilder.mouseReport(buttons, dx, dy, wheel, hWheel)
            }
        DebugLog.log(TAG, "mouse btn=$buttons dx=$dx dy=$dy wheel=$wheel hwheel=$hWheel simplified=$simplified")
        transport.send(HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), report)
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
