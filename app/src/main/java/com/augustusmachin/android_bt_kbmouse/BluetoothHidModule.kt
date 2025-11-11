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

    private val keyboardReportDescriptor = byteArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1.toByte(), 0x01,       // Collection (Application)
        0x05, 0x07,       //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (231)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95.toByte(), 0x08,       //   Report Count (8) (modifiers)
        0x81.toByte(), 0x02,       //   Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01,       //   Report Count (1)
        0x75, 0x08,       //   Report Size (8)
        0x81.toByte(), 0x01,       //   Input (Constant) Reserved
        0x95.toByte(), 0x05,       //   Report Count (5) (LEDs)
        0x75, 0x01,       //   Report Size (1)
        0x05, 0x08,       //   Usage Page (LEDs)
        0x19.toByte(), 0x01,       //   Usage Minimum (1)
        0x29.toByte(), 0x05,       //   Usage Maximum (5)
        0x91.toByte(), 0x02,       //   Output (Data, Variable, Absolute)
        0x95.toByte(), 0x01,       //   Report Count (1)
        0x75, 0x03,       //   Report Size (3)
        0x91.toByte(), 0x01,       //   Output (Constant) padding
        0x95.toByte(), 0x06,       //   Report Count (6) (keys)
        0x75, 0x08,       //   Report Size (8)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x65,       //   Logical Maximum (101)
        0x05, 0x07,       //   Usage Page (Key Codes)
        0x19.toByte(), 0x00,       //   Usage Minimum (0)
        0x29.toByte(), 0x65,       //   Usage Maximum (101)
        0x81.toByte(), 0x00,       //   Input (Data, Array, Absolute)
        0xC0.toByte()            // End Collection
    )

    private val mouseReportDescriptor = byteArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1.toByte(), 0x01,       // Collection (Application)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1.toByte(), 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Buttons)
        0x19.toByte(), 0x01,       //     Usage Minimum (Button 1)
        0x29.toByte(), 0x03,       //     Usage Maximum (Button 3)
        0x15, 0x00,       //     Logical Minimum (0)
        0x25, 0x01,       //     Logical Maximum (1)
        0x75, 0x01,       //     Report Size (1)
        0x95.toByte(), 0x03,       //     Report Count (3)
        0x81.toByte(), 0x02,       //     Input (Data, Variable, Absolute)
        0x75, 0x05,       //     Report Size (5)
        0x95.toByte(), 0x01,       //     Report Count (1)
        0x81.toByte(), 0x01,       //     Input (Constant)
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x15, 0x81.toByte(),       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95.toByte(), 0x02,       //     Report Count (2)
        0x81.toByte(), 0x06,       //     Input (Data, Variable, Relative)
        0xC0.toByte(),             //   End Collection
        0xC0.toByte()              // End Collection
    )

    @SuppressLint("NewApi")
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            DebugLog.log("BluetoothHidModule", "onAppStatusChanged registered=" + registered)
             android.util.Log.d("BTKB", "onAppStatusChanged registered="+registered+" plugged="+(pluggedDevice?.address))
            listener?.onAppStatus(registered)
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            // Avoid accessing device.name which requires BLUETOOTH_CONNECT on newer Android; use address for logs.
            DebugLog.log("BluetoothHidModule", "onConnectionStateChanged state=" + state + " dev=" + device.address)
             android.util.Log.d("BTKB", "onConnectionStateChanged state="+state+" dev="+(device.address))
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
    fun registerApp(proxy: BluetoothProfile) {
        val subclass: Byte = 0x40.toByte() // Keyboard only (boot)
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth Keyboard",
            "Android Bluetooth Keyboard (boot)",
            "Gemini",
            subclass,
            keyboardReportDescriptor
        )

        DebugLog.log("BluetoothHidModule", "registerApp")
        try {
            (proxy as BluetoothHidDevice).registerApp(sdpSettings, null, null, { it.run() }, callback)
        } catch (e: SecurityException) {
            android.util.Log.w("BluetoothHidModule", "registerApp SecurityException: ${e.message}")
            listener?.onError("registerApp failed: ${e.message}")
        }
    }
}
