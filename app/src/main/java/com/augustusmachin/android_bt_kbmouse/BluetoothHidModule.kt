package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.annotation.SuppressLint
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile

class BluetoothHidModule(private val bluetoothAdapter: BluetoothAdapter) {

    interface HidEventListener {
        fun onAppStatus(registered: Boolean)
        fun onConnectionStateChanged(device: BluetoothDevice, state: Int)
        fun onError(message: String)
    }
    interface HidEventListenerExt : HidEventListener {
        fun onLeds(leds: Int)
    }

    var listener: HidEventListener? = null

    @SuppressLint("NewApi")
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            DebugLog.log("BluetoothHidModule", "onAppStatusChanged registered=" + registered)
            listener?.onAppStatus(registered)
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            DebugLog.log("BluetoothHidModule", "onConnectionStateChanged state=" + state + " dev=" + device.address)
            listener?.onConnectionStateChanged(device, state)
        }
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT.toByte()) {
                val leds = data.firstOrNull()?.toInt() ?: 0
                DebugLog.log("BluetoothHidModule", "onSetReport OUTPUT leds=" + leds)
                (listener as? HidEventListenerExt)?.onLeds(leds)
            }
        }
    }

    @SuppressLint("NewApi")
    fun registerApp(proxy: BluetoothProfile, simplified: Boolean) {
        // 0xC0 = keyboard + mouse combo subclass
        val subclass: Byte = 0xC0.toByte()
        val descriptor = HidDescriptorVariants.select(simplified)
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth Keyboard/Mouse",
            "Android Bluetooth HID",
            "Gemini",
            subclass,
            descriptor
        )

        DebugLog.log("BluetoothHidModule", "registerApp simplified=$simplified descriptor.size=${descriptor.size}")
        try {
            (proxy as BluetoothHidDevice).registerApp(sdpSettings, null, null, { it.run() }, callback)
        } catch (e: SecurityException) {
            DebugLog.e("BluetoothHidModule", "registerApp SecurityException: ${e.message}")
            listener?.onError("registerApp failed: ${e.message}")
        }
    }
}
