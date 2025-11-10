package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.Action

class PairingViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) : ViewModel() {

    // Expose read-only views of the canonical Redux store slices so UI/tests can keep using
    // the PairingViewModel API while Redux remains the single source of truth.
    // Use Unconfined so test dispatchers (Main test dispatcher) or simple JVM test threads
    // observe updates synchronously; production will usually run on Main/Default as needed.
    private val _localScope = CoroutineScope(dispatcher + SupervisorJob())

    val discoveredDevices: StateFlow<List<BluetoothDevice>> = StoreProvider.asStateFlow()
        .map { it.connection.discoveredDevices }
        .stateIn(_localScope, SharingStarted.Eagerly, emptyList())

    val pairedDevices: StateFlow<List<BluetoothDevice>> = StoreProvider.asStateFlow()
        .map { it.connection.pairedDevices }
        .stateIn(_localScope, SharingStarted.Eagerly, emptyList())

    val connectedDevice: StateFlow<BluetoothDevice?> = StoreProvider.asStateFlow()
        .map { it.connection.connectedDevice }
        .stateIn(_localScope, SharingStarted.Eagerly, null)

    val message: StateFlow<String?> = StoreProvider.asStateFlow()
        .map { it.connection.message }
        .stateIn(_localScope, SharingStarted.Eagerly, null)

    val capsLock: StateFlow<Boolean> = StoreProvider.asStateFlow()
        .map { it.connection.capsLock }
        .stateIn(_localScope, SharingStarted.Eagerly, false)

    val numLock: StateFlow<Boolean> = StoreProvider.asStateFlow()
        .map { it.connection.numLock }
        .stateIn(_localScope, SharingStarted.Eagerly, false)

    val scrollLock: StateFlow<Boolean> = StoreProvider.asStateFlow()
        .map { it.connection.scrollLock }
        .stateIn(_localScope, SharingStarted.Eagerly, false)

    // Keep a reference for alias helpers; MainActivity will install service listeners and dispatch
    // canonical store updates. PairingViewModel no longer installs listeners or manages discovery.
    private var bluetoothService: IBluetoothService? = null

    // Expose default device from the store as well when available
    val defaultDeviceAddress: StateFlow<String?> = StoreProvider.asStateFlow()
        .map { it.connection.defaultDeviceAddress }
        .stateIn(_localScope, SharingStarted.Eagerly, null)

    fun setBluetoothService(service: IBluetoothService) {
        DebugLog.log("PairingViewModel", "setBluetoothService (shim)")
        bluetoothService = service
        // Event listeners are owned/installed by MainActivity (production) or tests can
        // dispatch store actions directly. No listener is installed here to keep the
        // ViewModel a pure shim over the Redux store.
    }

    override fun onCleared() {
        super.onCleared()
        _localScope.cancel()
    }


    fun startDiscovery() {
        DebugLog.log("PairingViewModel", "startDiscovery -> dispatch StartDiscovery")
        StoreProvider.dispatch(Action.UpdateMessage("Scanning for devices..."))
        StoreProvider.dispatch(Action.UpdateIsScanning(true))
        StoreProvider.dispatch(Action.StartDiscovery)
    }

    fun stopDiscovery() {
        DebugLog.log("PairingViewModel", "stopDiscovery -> dispatch StopDiscovery")
        StoreProvider.dispatch(Action.StopDiscovery)
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    fun pairDevice(device: BluetoothDevice) {
        DebugLog.log("PairingViewModel", "pairDevice -> dispatch PairDevice ${device.address}")
        StoreProvider.dispatch(Action.PairDevice(device))
    }

    fun connectDevice(device: BluetoothDevice) {
        DebugLog.log("PairingViewModel", "connectDevice -> dispatch ConnectDevice ${device.address}")
        StoreProvider.dispatch(Action.ConnectDevice(device))
    }

    fun disconnectDevice() {
        DebugLog.log("PairingViewModel", "disconnectDevice -> dispatch DisconnectDevice")
        StoreProvider.dispatch(Action.DisconnectDevice)
    }

    // Device picker helpers
    fun setDefaultDevice(device: BluetoothDevice) {
        StoreProvider.dispatch(Action.SetDefaultDevice(device))
    }

    fun getAlias(device: BluetoothDevice): String? = bluetoothService?.getAlias(device)
    fun setAlias(device: BluetoothDevice, alias: String) { bluetoothService?.setAlias(device, alias) }

    fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {
        StoreProvider.dispatch(Action.ForgetDevice(device, unpair))
    }

    fun consumeMessage() { StoreProvider.dispatch(Action.UpdateMessage(null)) }

    // Input actions
    fun sendKey(keyCode: Byte, modifiers: Int = 0) {
        StoreProvider.dispatch(Action.SendKey(keyCode, modifiers))
    }

    fun moveMouse(dx: Int, dy: Int) {
        StoreProvider.dispatch(Action.MoveMouse(dx, dy))
    }

    fun leftClick() {
        StoreProvider.dispatch(Action.LeftClick)
    }

    fun rightClick() {
        StoreProvider.dispatch(Action.RightClick)
    }

    fun middleClick() {
        StoreProvider.dispatch(Action.MiddleClick)
    }

    fun scroll(delta: Int) {
        StoreProvider.dispatch(Action.ScrollVertical(delta))
    }
    fun scrollH(delta: Int) {
        StoreProvider.dispatch(Action.ScrollHorizontal(delta))
    }

    fun keyDown(code: Byte, modifiers: Int) { StoreProvider.dispatch(Action.KeyDown(code, modifiers)) }
    fun keyUp(code: Byte) { StoreProvider.dispatch(Action.KeyUp(code)) }

    fun toggleCapsLock() { StoreProvider.dispatch(Action.ToggleCapsLock) }
    fun toggleNumLock() { StoreProvider.dispatch(Action.ToggleNumLock) }
    fun toggleScrollLock() { StoreProvider.dispatch(Action.ToggleScrollLock) }
}
