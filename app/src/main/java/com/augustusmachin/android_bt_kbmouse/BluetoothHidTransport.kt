package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Production [HidReportTransport]: resolves the current device + HID proxy, checks
 * BLUETOOTH_CONNECT, and sends the report via the platform. Holds the
 * keyboard-vs-mouse error handling that previously lived inside [HidReportSender]
 * (extracted so the sender's report-building state machine is host-testable).
 */
class BluetoothHidTransport(
    private val context: Context,
    private val currentDevice: () -> BluetoothDevice?,
    private val currentHid: () -> BluetoothHidDevice?,
    private val onError: (String) -> Unit,
) : HidReportTransport {
    override fun send(
        reportId: Int,
        report: ByteArray,
    ) {
        val device = currentDevice() ?: return
        val hid = currentHid() ?: return
        val keyboard = reportId == HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt()
        val kind = if (keyboard) "keyboard" else "mouse"
        val hasBtConnect =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasBtConnect) {
            DebugLog.e(TAG, "BLUETOOTH_CONNECT not granted; cannot send $kind report")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            DebugLog.e(TAG, "HID sendReport not supported on API < 28; skipping $kind report")
            return
        }
        try {
            hid.sendReport(device, reportId, report)
        } catch (se: SecurityException) {
            if (keyboard) {
                DebugLog.e(TAG, "sendReport SecurityException: ${se.message}")
                onError("HID report failed due to missing permission")
            } else {
                DebugLog.e(TAG, "mouse sendReport SecurityException: ${se.message}")
                onError("Mouse click failed due to missing permission")
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (keyboard) {
                DebugLog.e(TAG, "kbd report error: ${e.message}")
                onError("HID report failed: ${e.message}")
            } else {
                // defensive: mouse path logs only (no onError), matching prior behavior
                DebugLog.e(TAG, "mouse report error: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
