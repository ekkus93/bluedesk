package com.augustusmachin.android_bt_kbmouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile

sealed interface HidRegistrationRequestResult {
    data object Accepted : HidRegistrationRequestResult

    data object Rejected : HidRegistrationRequestResult

    data class PermissionDenied(val message: String) : HidRegistrationRequestResult
}

class BluetoothHidModule {
    interface HidEventListener {
        fun onAppStatus(registered: Boolean)

        fun onConnectionStateChanged(
            device: BluetoothDevice,
            state: Int,
        )

        fun onError(message: String)
    }

    interface HidEventListenerExt : HidEventListener {
        fun onLeds(leds: Int)
    }

    var listener: HidEventListener? = null

    @SuppressLint("NewApi")
    private val callback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                DebugLog.log("BluetoothHidModule", "onAppStatusChanged registered=$registered")
                listener?.onAppStatus(registered)
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                DebugLog.log("BluetoothHidModule", "onConnectionStateChanged state=$state")
                listener?.onConnectionStateChanged(device, state)
            }

            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT.toByte()) {
                    val leds = data.firstOrNull()?.toInt() ?: 0
                    DebugLog.log("BluetoothHidModule", "onSetReport OUTPUT leds=$leds")
                    (listener as? HidEventListenerExt)?.onLeds(leds)
                }
            }
        }

    @SuppressLint("NewApi")
    fun registerApp(
        proxy: BluetoothProfile,
        simplified: Boolean,
    ): HidRegistrationRequestResult {
        val subclass: Byte = 0xC0.toByte()
        val descriptor = HidDescriptorVariants.select(simplified)
        val sdpSettings =
            BluetoothHidDeviceAppSdpSettings(
                "BlueDeck Keyboard/Mouse",
                "BlueDeck Android HID",
                "BlueDeck",
                subclass,
                descriptor,
            )

        DebugLog.log("BluetoothHidModule", "registerApp simplified=$simplified descriptor.size=${descriptor.size}")
        return try {
            val accepted =
                (proxy as BluetoothHidDevice).registerApp(
                    sdpSettings,
                    null,
                    null,
                    { it.run() },
                    callback,
                )
            if (accepted) {
                HidRegistrationRequestResult.Accepted
            } else {
                val message = "Classic HID registerApp request was rejected immediately"
                DebugLog.e("BluetoothHidModule", message)
                listener?.onError(message)
                HidRegistrationRequestResult.Rejected
            }
        } catch (e: SecurityException) {
            val message = "registerApp failed: ${e.message}"
            DebugLog.e("BluetoothHidModule", message)
            listener?.onError(message)
            HidRegistrationRequestResult.PermissionDenied(message)
        }
    }
}
