package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Owns the HID output state — the 6-key rollover chord set, current modifiers,
 * and held mouse buttons — and writes keyboard/mouse reports to the connected
 * device. Extracted from [BluetoothService] so that class stays focused on
 * connection and lifecycle.
 *
 * Device/proxy/descriptor state is read live through the provider lambdas so the
 * sender always targets the service's current connection.
 */
class HidReportSender(
    private val context: Context,
    private val currentDevice: () -> BluetoothDevice?,
    private val currentHid: () -> BluetoothHidDevice?,
    private val isSimplified: () -> Boolean,
    private val onError: (String) -> Unit,
) {
    // Multi-key chord state
    private val pressedKeys = LinkedHashSet<Byte>() // preserve insertion order
    private var currentModifiers: Int = 0

    @Volatile private var heldMouseButtons = 0

    fun setModifiers(mods: Int) {
        synchronized(this) { currentModifiers = mods and 0xFF }
        sendCurrentKeyboardReport()
    }

    fun sendKeyPress(
        keyCode: Byte,
        modifiers: Int,
    ) {
        pressKey(keyCode, modifiers)
        try {
            Thread.sleep(10)
        } catch (_: InterruptedException) {
        }
        releaseKey(keyCode)
    }

    fun pressKey(
        keyCode: Byte,
        modifiers: Int,
    ) {
        synchronized(this) {
            currentModifiers = modifiers and 0xFF
            if (pressedKeys.size < 6) {
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
        val device = currentDevice() ?: return
        val hid = currentHid() ?: return
        val keys = synchronized(this) { pressedKeys.toList().take(6) }
        val report = HidReportBuilder.keyboardReport(currentModifiers, keys)
        DebugLog.log(TAG, "kbd mods=" + currentModifiers + " keys=" + keys)
        // Ensure BLUETOOTH_CONNECT before sending HID report
        val hasBtConnectReport =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnectReport) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), report)
                } else {
                    DebugLog.e(TAG, "HID sendReport not supported on API < 28; skipping keyboard report")
                }
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "sendReport SecurityException: ${se.message}")
                onError("HID report failed due to missing permission")
            } catch (e: Exception) {
                DebugLog.e(TAG, "kbd report error: ${e.message}")
                onError("HID report failed: ${e.message}")
            }
        } else {
            DebugLog.e(TAG, "BLUETOOTH_CONNECT not granted; cannot send keyboard report")
        }
    }

    private fun clickMouse(buttonMask: Int) {
        DebugLog.log(TAG, "mouse click mask=" + buttonMask)
        sendMouseReport(buttonMask, 0, 0, 0, 0)
        try {
            Thread.sleep(10)
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
        val device = currentDevice() ?: return
        val hid = currentHid() ?: return
        val simplified = isSimplified()
        // SIMPLE: 3 bytes [buttons][dx][dy]; FULL: 5 bytes [buttons][dx][dy][wheelV][wheelH]
        val report =
            if (simplified) {
                HidReportBuilder.mouseReportSimple(buttons, dx, dy)
            } else {
                HidReportBuilder.mouseReport(buttons, dx, dy, wheel, hWheel)
            }
        DebugLog.log(TAG, "mouse btn=$buttons dx=$dx dy=$dy wheel=$wheel hwheel=$hWheel simplified=$simplified")
        val hasBtConnect =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), report)
                } else {
                    DebugLog.e(TAG, "HID sendReport not supported on API < 28; skipping mouse report")
                }
            } catch (se: SecurityException) {
                DebugLog.e(TAG, "mouse sendReport SecurityException: ${se.message}")
                onError("Mouse click failed due to missing permission")
            } catch (e: Exception) {
                DebugLog.e(TAG, "mouse report error: ${e.message}")
            }
        } else {
            DebugLog.e(TAG, "BLUETOOTH_CONNECT not granted; cannot send mouse report")
        }
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
