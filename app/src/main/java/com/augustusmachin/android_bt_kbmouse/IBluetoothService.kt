package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice

interface IBluetoothService {
    fun getLastDeviceAddress(): String?

    fun getConnectedDevice(): BluetoothDevice?

    fun getConnectedDeviceLabel(): String?

    fun getStartupState(): ClassicHidStartupState

    fun setEventListener(l: BluetoothService.ServiceEventListener?)

    fun startDiscovery(): Boolean

    fun stopDiscovery(): Boolean

    fun getDiscoveredDevices(): List<BluetoothDevice>

    fun getPairedDevices(): List<BluetoothDevice>

    fun pairDevice(device: BluetoothDevice)

    fun connectDevice(device: BluetoothDevice)

    fun disconnectDevice()

    fun setDefaultDevice(device: BluetoothDevice)

    fun getAlias(device: BluetoothDevice): String?

    fun setAlias(
        device: BluetoothDevice,
        alias: String,
    )

    fun forgetDevice(
        device: BluetoothDevice,
        unpair: Boolean,
    )

    fun sendKeyPress(
        keyCode: Byte,
        modifiers: Int = 0,
    ): HidDeliveryResult

    fun sendMouseMove(
        dx: Int,
        dy: Int,
    ): HidDeliveryResult

    fun sendLeftClick(): HidDeliveryResult

    fun sendRightClick(): HidDeliveryResult

    fun sendMiddleClick(): HidDeliveryResult

    fun sendScroll(delta: Int): HidDeliveryResult

    fun sendScrollH(delta: Int): HidDeliveryResult

    fun mouseButtonDown(button: Int): HidDeliveryResult

    fun mouseButtonUp(): HidDeliveryResult

    fun pressKey(
        keyCode: Byte,
        modifiers: Int = 0,
    ): HidDeliveryResult

    fun releaseKey(keyCode: Byte): HidDeliveryResult

    fun setModifiers(mods: Int): HidDeliveryResult
}
