package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice

interface IBluetoothService {
    fun getLastDeviceAddress(): String?
    fun setEventListener(l: BluetoothService.ServiceEventListener)
    fun startDiscovery()
    fun stopDiscovery()
    fun getDiscoveredDevices(): List<BluetoothDevice>
    fun getPairedDevices(): List<BluetoothDevice>
    fun pairDevice(device: BluetoothDevice)
    fun connectDevice(device: BluetoothDevice)
    fun disconnectDevice()
    fun setDefaultDevice(device: BluetoothDevice)
    fun getAlias(device: BluetoothDevice): String?
    fun setAlias(device: BluetoothDevice, alias: String)
    fun forgetDevice(device: BluetoothDevice, unpair: Boolean)
    fun sendKeyPress(keyCode: Byte, modifiers: Int = 0)
    fun sendMouseMove(dx: Int, dy: Int)
    fun sendLeftClick()
    fun sendRightClick()
    fun sendMiddleClick()
    fun sendScroll(delta: Int)
    fun sendScrollH(delta: Int)
    fun pressKey(keyCode: Byte, modifiers: Int = 0)
    fun releaseKey(keyCode: Byte)
}