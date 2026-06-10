package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.IBluetoothService

/**
 * Bridge that implements KeySender and forwards calls to the app's IBluetoothService.
 * This lets Redux middleware call into the platform service without importing Android framework types
 * into reducers or middleware logic.
 */
class BluetoothKeySender(private val svc: IBluetoothService) : KeySender {
    override fun sendKeyDown(
        code: Byte,
        mods: Int,
    ) {
        svc.pressKey(code, mods)
    }

    override fun sendKeyUp(code: Byte) {
        svc.releaseKey(code)
    }

    override fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        svc.sendMouseMove(dx, dy)
    }

    override fun leftClick() {
        svc.sendLeftClick()
    }

    override fun rightClick() {
        svc.sendRightClick()
    }

    override fun middleClick() {
        svc.sendMiddleClick()
    }

    override fun scrollVertical(delta: Int) {
        svc.sendScroll(delta)
    }

    override fun scrollHorizontal(delta: Int) {
        svc.sendScrollH(delta)
    }

    override fun toggleCapsLock() {
        svc.sendKeyPress(0x39.toByte(), 0)
    }

    override fun toggleScrollLock() {
        svc.sendKeyPress(0x47.toByte(), 0)
    }

    override fun mouseButtonDown(button: Int) {
        svc.mouseButtonDown(button)
    }

    override fun mouseButtonUp() {
        svc.mouseButtonUp()
    }

    override fun setModifiers(mods: Int) {
        svc.setModifiers(mods)
    }

    override fun startDiscovery() {
        svc.startDiscovery()
    }

    override fun stopDiscovery() {
        svc.stopDiscovery()
    }

    override fun pairDevice(device: BluetoothDevice) {
        svc.pairDevice(device)
    }

    override fun connectDevice(device: BluetoothDevice) {
        svc.connectDevice(device)
    }

    override fun disconnectDevice() {
        svc.disconnectDevice()
    }

    override fun forgetDevice(
        device: BluetoothDevice,
        unpair: Boolean,
    ) {
        svc.forgetDevice(device, unpair)
    }

    override fun setDefaultDevice(device: BluetoothDevice) {
        svc.setDefaultDevice(device)
    }

    override fun renameDevice(
        device: BluetoothDevice,
        alias: String,
    ) {
        svc.setAlias(device, alias)
    }
}
