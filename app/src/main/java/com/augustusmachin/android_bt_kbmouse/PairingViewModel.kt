package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PairingViewModel : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _capsLock = MutableStateFlow(false)
    val capsLock: StateFlow<Boolean> = _capsLock
    private val _numLock = MutableStateFlow(false)
    val numLock: StateFlow<Boolean> = _numLock
    private val _scrollLock = MutableStateFlow(false)
    val scrollLock: StateFlow<Boolean> = _scrollLock

    private var bluetoothService: IBluetoothService? = null
    private var discoveryJob: Job? = null
    private val _defaultDeviceAddress = MutableStateFlow<String?>(null)
    val defaultDeviceAddress: StateFlow<String?> = _defaultDeviceAddress

    fun setBluetoothService(service: IBluetoothService) {
        DebugLog.log("PairingViewModel", "setBluetoothService")
        bluetoothService = service
        _defaultDeviceAddress.value = service.getLastDeviceAddress()
        service.setEventListener(object : BluetoothService.ServiceEventListener {
            override fun onConnected(device: BluetoothDevice) {
                DebugLog.log("PairingViewModel", "onConnected ${device.address}")
                _connectedDevice.value = device
                _message.value = "Connected to ${device.name ?: device.address}"
            }
            override fun onDisconnected(device: BluetoothDevice?) {
                DebugLog.log("PairingViewModel", "onDisconnected")
                _connectedDevice.value = null
                _message.value = "Disconnected"
            }
            override fun onInfo(message: String) { DebugLog.log("PairingViewModel", "info: " + message); _message.value = message }
            override fun onError(message: String) { DebugLog.e("PairingViewModel", message); _message.value = message }
            override fun onLeds(leds: Int) {
                _numLock.value = (leds and 0x01) != 0
                _capsLock.value = (leds and 0x02) != 0
                _scrollLock.value = (leds and 0x04) != 0
            }
        })
    }

    fun startDiscovery() {
        DebugLog.log("PairingViewModel", "startDiscovery")
        _message.value = "Scanning for devices..."
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            bluetoothService?.startDiscovery()
            // Immediate refresh so user sees lists update
            updateDiscoveredDevices()
            getPairedDevices()
            while (true) {
                updateDiscoveredDevices()
                getPairedDevices()
                // Auto-restart discovery if it finished before host became discoverable
                // Always re-trigger discover to surface results reliably during debugging
                bluetoothService?.startDiscovery()
                delay(1500)
            }
        }
    }

    fun stopDiscovery() {
        DebugLog.log("PairingViewModel", "stopDiscovery")
        discoveryJob?.cancel()
        bluetoothService?.stopDiscovery()
    }

    fun updateDiscoveredDevices() {
        _discoveredDevices.value = bluetoothService?.getDiscoveredDevices() ?: emptyList()
    }

    fun pairDevice(device: BluetoothDevice) {
        DebugLog.log("PairingViewModel", "pairDevice ${device.address}")
        bluetoothService?.pairDevice(device)
    }

    fun connectDevice(device: BluetoothDevice) {
        DebugLog.log("PairingViewModel", "connectDevice ${device.address}")
        bluetoothService?.connectDevice(device)
        _connectedDevice.value = device
    }

    fun disconnectDevice() {
        DebugLog.log("PairingViewModel", "disconnectDevice")
        bluetoothService?.disconnectDevice()
        _connectedDevice.value = null
        _message.value = "Disconnected"
    }

    // Device picker helpers
    fun setDefaultDevice(device: BluetoothDevice) {
        bluetoothService?.setDefaultDevice(device)
        _defaultDeviceAddress.value = device.address
    }
    fun getAlias(device: BluetoothDevice): String? = bluetoothService?.getAlias(device)
    fun setAlias(device: BluetoothDevice, alias: String) { bluetoothService?.setAlias(device, alias) }
    fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {
        bluetoothService?.forgetDevice(device, unpair)
        if (_connectedDevice.value?.address == device.address) _connectedDevice.value = null
        if (_defaultDeviceAddress.value == device.address) _defaultDeviceAddress.value = null
        getPairedDevices()
    }

    fun getPairedDevices() {
        _pairedDevices.value = bluetoothService?.getPairedDevices() ?: emptyList()
    }

    fun consumeMessage() { _message.value = null }

    // Input actions
    fun sendKey(keyCode: Byte, modifiers: Int = 0) {
        bluetoothService?.sendKeyPress(keyCode, modifiers)
    }

    fun moveMouse(dx: Int, dy: Int) {
        bluetoothService?.sendMouseMove(dx, dy)
    }

    fun leftClick() {
        bluetoothService?.sendLeftClick()
    }

    fun rightClick() {
        bluetoothService?.sendRightClick()
    }

    fun middleClick() {
        bluetoothService?.sendMiddleClick()
    }

    fun scroll(delta: Int) {
        bluetoothService?.sendScroll(delta)
    }
    fun scrollH(delta: Int) {
        bluetoothService?.sendScrollH(delta)
    }

    fun keyDown(code: Byte, modifiers: Int) { bluetoothService?.pressKey(code, modifiers) }
    fun keyUp(code: Byte) { bluetoothService?.releaseKey(code) }

    fun toggleCapsLock() { bluetoothService?.sendKeyPress(0x39.toByte(), 0) }
    fun toggleNumLock() { bluetoothService?.sendKeyPress(0x53.toByte(), 0) }
    fun toggleScrollLock() { bluetoothService?.sendKeyPress(0x47.toByte(), 0) }
}
