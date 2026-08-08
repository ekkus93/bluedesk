package com.augustusmachin.android_bt_kbmouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.os.Build

/** Production Classic HID transport. Every attempted report has an explicit delivery result. */
class BluetoothHidTransport(
    private val context: Context,
    private val currentDevice: () -> BluetoothDevice?,
    private val currentHid: () -> BluetoothHidDevice?,
    private val onError: (String) -> Unit,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasPermissions: (List<String>) -> Boolean = { required ->
        PermissionGrantChecker.hasAll(context, required)
    },
) : HidReportTransport {
    @SuppressLint("MissingPermission", "NewApi")
    override fun send(
        reportId: Int,
        report: ByteArray,
    ): HidDeliveryResult {
        val kind = if (reportId == HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt()) "keyboard" else "mouse"
        val device =
            currentDevice()
                ?: return failure(
                    HidDeliveryFailureCode.DEVICE_MISSING,
                    "Cannot send $kind report: no connected HID device",
                )
        val hid =
            currentHid()
                ?: return failure(
                    HidDeliveryFailureCode.HID_PROXY_MISSING,
                    "Cannot send $kind report: HID profile is unavailable",
                )
        if (sdkInt < Build.VERSION_CODES.P) {
            return failure(
                HidDeliveryFailureCode.UNSUPPORTED_API,
                "Cannot send $kind report: Classic HID requires Android 9 (API 28) or newer",
            )
        }
        if (!hasPermissions(PermissionPolicy.requiredForClassicStartup(sdkInt))) {
            return failure(
                HidDeliveryFailureCode.PERMISSION_DENIED,
                "Cannot send $kind report: Bluetooth connect permission is not granted",
            )
        }

        return try {
            if (hid.sendReport(device, reportId, report)) {
                HidDeliveryResult.Sent
            } else {
                failure(
                    HidDeliveryFailureCode.REPORT_REJECTED,
                    "$kind HID report was rejected by the platform",
                )
            }
        } catch (se: SecurityException) {
            failure(
                HidDeliveryFailureCode.PERMISSION_DENIED,
                "$kind HID report failed because Bluetooth permission was revoked: ${se.message}",
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            failure(
                HidDeliveryFailureCode.TRANSPORT_EXCEPTION,
                "$kind HID report failed: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private fun failure(
        code: HidDeliveryFailureCode,
        message: String,
    ): HidDeliveryResult.Failure {
        DebugLog.e("BluetoothService", message)
        onError(message)
        return HidDeliveryResult.Failure(code, message)
    }
}
