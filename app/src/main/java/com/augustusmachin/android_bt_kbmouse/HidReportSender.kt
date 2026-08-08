package com.augustusmachin.android_bt_kbmouse

private const val MODIFIER_BYTE_MASK = 0xFF
private const val MAX_ROLLOVER_KEYS = 6

enum class HidDeliveryFailureCode {
    DEVICE_MISSING,
    HID_PROXY_MISSING,
    PERMISSION_DENIED,
    UNSUPPORTED_API,
    REPORT_REJECTED,
    TRANSPORT_EXCEPTION,
}

sealed interface HidDeliveryResult {
    data object Sent : HidDeliveryResult

    data class Unsupported(val reason: String) : HidDeliveryResult

    data class Failure(
        val code: HidDeliveryFailureCode,
        val message: String,
    ) : HidDeliveryResult
}

/** Delivers a built HID input report (with its report ID) to the connected device. */
fun interface HidReportTransport {
    fun send(
        reportId: Int,
        report: ByteArray,
    ): HidDeliveryResult
}

/** Owns keyboard/mouse report state and returns the transport result for every send attempt. */
class HidReportSender(
    private val isSimplified: () -> Boolean,
    private val transport: HidReportTransport,
) {
    private val pressedKeys = LinkedHashSet<Byte>()
    private var currentModifiers: Int = 0

    @Volatile private var heldMouseButtons = 0

    fun setModifiers(mods: Int): HidDeliveryResult {
        synchronized(this) { currentModifiers = mods and MODIFIER_BYTE_MASK }
        return sendCurrentKeyboardReport()
    }

    /** Convenience synchronous sequence; production middleware normally sends down/up separately. */
    fun sendKeyPress(
        keyCode: Byte,
        modifiers: Int,
    ): HidDeliveryResult {
        val down = pressKey(keyCode, modifiers)
        val up = releaseKey(keyCode)
        return firstFailure(down, up)
    }

    fun pressKey(
        keyCode: Byte,
        modifiers: Int,
    ): HidDeliveryResult {
        synchronized(this) {
            currentModifiers = modifiers and MODIFIER_BYTE_MASK
            if (pressedKeys.size < MAX_ROLLOVER_KEYS) {
                pressedKeys.add(keyCode)
            } else if (pressedKeys.isNotEmpty()) {
                pressedKeys.remove(pressedKeys.first())
                pressedKeys.add(keyCode)
            }
        }
        return sendCurrentKeyboardReport()
    }

    fun releaseKey(keyCode: Byte): HidDeliveryResult {
        synchronized(this) { pressedKeys.remove(keyCode) }
        return sendCurrentKeyboardReport()
    }

    fun sendMouseMove(
        dx: Int,
        dy: Int,
    ): HidDeliveryResult = sendMouseReport(heldMouseButtons, dx, dy, 0, 0)

    fun mouseButtonDown(button: Int): HidDeliveryResult {
        heldMouseButtons = heldMouseButtons or button
        return sendMouseReport(heldMouseButtons, 0, 0, 0, 0)
    }

    fun mouseButtonUp(): HidDeliveryResult {
        heldMouseButtons = 0
        return sendMouseReport(0, 0, 0, 0, 0)
    }

    fun sendLeftClick(): HidDeliveryResult = clickMouse(buttonMask = 0x01)

    fun sendRightClick(): HidDeliveryResult = clickMouse(buttonMask = 0x02)

    fun sendMiddleClick(): HidDeliveryResult = clickMouse(buttonMask = 0x04)

    fun sendScroll(delta: Int): HidDeliveryResult =
        if (isSimplified()) {
            HidDeliveryResult.Unsupported("Vertical scroll requires the full HID descriptor")
        } else {
            sendMouseReport(0, 0, 0, delta, 0)
        }

    fun sendScrollH(delta: Int): HidDeliveryResult =
        if (isSimplified()) {
            HidDeliveryResult.Unsupported("Horizontal scroll requires the full HID descriptor")
        } else {
            sendMouseReport(0, 0, 0, 0, delta)
        }

    private fun sendCurrentKeyboardReport(): HidDeliveryResult {
        val keys = synchronized(this) { pressedKeys.toList().take(MAX_ROLLOVER_KEYS) }
        val report = HidReportBuilder.keyboardReport(currentModifiers, keys)
        DebugLog.log(TAG, "kbd mods=$currentModifiers keys=$keys")
        return transport.send(HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), report)
    }

    private fun clickMouse(buttonMask: Int): HidDeliveryResult {
        DebugLog.log(TAG, "mouse click mask=$buttonMask")
        val down = sendMouseReport(buttonMask, 0, 0, 0, 0)
        val up = sendMouseReport(0, 0, 0, 0, 0)
        return firstFailure(down, up)
    }

    private fun sendMouseReport(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int,
        hWheel: Int,
    ): HidDeliveryResult {
        val simplified = isSimplified()
        val report =
            if (simplified) {
                HidReportBuilder.mouseReportSimple(buttons, dx, dy)
            } else {
                HidReportBuilder.mouseReport(buttons, dx, dy, wheel, hWheel)
            }
        DebugLog.log(TAG, "mouse btn=$buttons dx=$dx dy=$dy wheel=$wheel hwheel=$hWheel simplified=$simplified")
        return transport.send(HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), report)
    }

    private fun firstFailure(
        first: HidDeliveryResult,
        second: HidDeliveryResult,
    ): HidDeliveryResult =
        when {
            first !is HidDeliveryResult.Sent -> first
            second !is HidDeliveryResult.Sent -> second
            else -> HidDeliveryResult.Sent
        }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
