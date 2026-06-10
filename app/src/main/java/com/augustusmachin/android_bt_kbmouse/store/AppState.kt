package com.augustusmachin.android_bt_kbmouse.store

import android.bluetooth.BluetoothDevice

data class KeyboardState(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val gui: Boolean = false,
    val ctrlPersist: Boolean = false,
    val shiftPersist: Boolean = false,
    val altPersist: Boolean = false,
    val guiPersist: Boolean = false,
)

data class PreviewKeyEntry(
    val id: Long,
    val label: String,
    val decorate: Boolean,
)

data class UiState(
    val offlinePreview: Boolean = false,
    val showExtended: Boolean = false,
    val extendedPage: Int = 0,
    val previewKeys: List<PreviewKeyEntry> = emptyList(),
)

data class ConnectionState(
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val connectedDevice: BluetoothDevice? = null,
    val message: String? = null,
    val defaultDeviceAddress: String? = null,
    val capsLock: Boolean = false,
    val scrollLock: Boolean = false,
    val isScanning: Boolean = false,
)

data class AppState(
    val keyboard: KeyboardState = KeyboardState(),
    val ui: UiState = UiState(),
    val connection: ConnectionState = ConnectionState(),
)
