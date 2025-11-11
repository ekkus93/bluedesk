package com.augustusmachin.android_bt_kbmouse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import java.util.*

class BleHogpService : Service() {
    companion object {
        private val UUID_HID_SERVICE = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        private val UUID_BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val UUID_DEVINFO_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")

        private val UUID_HID_INFORMATION = UUID.fromString("00002A4A-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT_MAP = UUID.fromString("00002A4B-0000-1000-8000-00805f9b34fb")
        private val UUID_HID_CONTROL_POINT = UUID.fromString("00002A4C-0000-1000-8000-00805f9b34fb")
        private val UUID_PROTOCOL_MODE = UUID.fromString("00002A4E-0000-1000-8000-00805f9b34fb")
        private val UUID_BOOT_KB_INPUT = UUID.fromString("00002A22-0000-1000-8000-00805f9b34fb")
        private val UUID_BOOT_KB_OUTPUT = UUID.fromString("00002A32-0000-1000-8000-00805f9b34fb")
        private val UUID_BOOT_MOUSE_INPUT = UUID.fromString("00002A33-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT = UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb")

        private val UUID_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT_REF = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb")
        private val UUID_BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    inner class LocalBinder : Binder() { fun getService(): BleHogpService = this@BleHogpService }

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var kbInputChar: BluetoothGattCharacteristic? = null
    private var mouseInputChar: BluetoothGattCharacteristic? = null
    private var batteryLevelChar: BluetoothGattCharacteristic? = null
    private var kbInputReportChar: BluetoothGattCharacteristic? = null
    private var mouseInputReportChar: BluetoothGattCharacteristic? = null
    private var previousName: String? = null

    private val connected = Collections.synchronizedSet(mutableSetOf<BluetoothDevice>())
    private var protocolMode: Byte = 0x00 // 0=Boot, 1=Report

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        // Require BLUETOOTH_CONNECT permission to interact with GATT/advertising.
        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasConnectPermission) {
            // Can't proceed with GATT server/advertising until permission is granted.
            android.util.Log.w("BleHogpService", "BLUETOOTH_CONNECT permission not granted; deferring BLE initialization")
            return
        }
        startInForeground()
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter
        advertiser = adapter.bluetoothLeAdvertiser
        // Temporarily set device name to help host identify as a keyboard
        try { previousName = adapter.name; adapter.name = "Bluetooth Keyboard" } catch (_: Exception) {}
        gattServer = mgr.openGattServer(this, gattCb)
        setupGattServices()
        startAdvertising(adapter)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop advertising; perform explicit permission checks so lint can verify we handled runtime permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasAdvertise = checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val hasConnect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (hasAdvertise && hasConnect) {
                try { advertiser?.stopAdvertising(adCb) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "stopAdvertising SecurityException: ${e.message}") }
            } else {
                android.util.Log.w("BleHogpService", "Missing BLUETOOTH_ADVERTISE/CONNECT permission on destroy; skipping stopAdvertising")
            }
        } else {
            try { advertiser?.stopAdvertising(adCb) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "stopAdvertising SecurityException: ${e.message}") }
        }
        try { gattServer?.close() } catch (_: Exception) {}
        // Restore device name
        try { val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager; val ad = mgr.adapter; previousName?.let { ad.name = it } } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun setupGattServices() {
        // Ensure we have BLUETOOTH_CONNECT permission before interacting with GATT server.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("BleHogpService", "BLUETOOTH_CONNECT not granted; skipping GATT service setup")
                return
            }
        }
        val hidService = BluetoothGattService(UUID_HID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Protocol Mode
        val proto = BluetoothGattCharacteristic(
            UUID_PROTOCOL_MODE,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
    proto.setValue(byteArrayOf(protocolMode))
        hidService.addCharacteristic(proto)

        // HID Information (bcdHID=0x0111, bCountryCode=0, flags=0x02)
        val hidInfo = BluetoothGattCharacteristic(
            UUID_HID_INFORMATION,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
    hidInfo.setValue(byteArrayOf(0x11, 0x01, 0x00, 0x02))
        hidService.addCharacteristic(hidInfo)

        // HID Control Point (write without response)
        val ctrlPt = BluetoothGattCharacteristic(
            UUID_HID_CONTROL_POINT,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        hidService.addCharacteristic(ctrlPt)

        // Report Map (simple boot kbd + mouse)
        val reportMap = BluetoothGattCharacteristic(
            UUID_REPORT_MAP,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
    reportMap.setValue(HID_REPORT_MAP)
        hidService.addCharacteristic(reportMap)

        // Boot Keyboard Input (notify)
        kbInputChar = BluetoothGattCharacteristic(
            UUID_BOOT_KB_INPUT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val kbCcc = BluetoothGattDescriptor(UUID_CCC, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
        kbInputChar!!.addDescriptor(kbCcc)
        hidService.addCharacteristic(kbInputChar)

        // Input Report (Keyboard) with Report Reference (ID=1, type=Input)
        kbInputReportChar = BluetoothGattCharacteristic(
            UUID_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val kbRepRef = BluetoothGattDescriptor(UUID_REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED)
    kbRepRef.setValue(byteArrayOf(0x01, 0x01)) // reportId=1, type=Input(1)
        val kbRepCcc = BluetoothGattDescriptor(UUID_CCC, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
        kbInputReportChar!!.addDescriptor(kbRepRef)
        kbInputReportChar!!.addDescriptor(kbRepCcc)
        hidService.addCharacteristic(kbInputReportChar)

        // Boot Keyboard Output (write)
        val kbOut = BluetoothGattCharacteristic(
            UUID_BOOT_KB_OUTPUT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        hidService.addCharacteristic(kbOut)

        // Boot Mouse Input (notify)
        mouseInputChar = BluetoothGattCharacteristic(
            UUID_BOOT_MOUSE_INPUT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val mouseCcc = BluetoothGattDescriptor(UUID_CCC, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
        mouseInputChar!!.addDescriptor(mouseCcc)
        hidService.addCharacteristic(mouseInputChar)

        // Input Report (Mouse) with Report Reference (ID=2, type=Input)
        mouseInputReportChar = BluetoothGattCharacteristic(
            UUID_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val mouseRepRef = BluetoothGattDescriptor(UUID_REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED)
    mouseRepRef.setValue(byteArrayOf(0x02, 0x01)) // reportId=2, type=Input
        val mouseRepCcc = BluetoothGattDescriptor(UUID_CCC, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
        mouseInputReportChar!!.addDescriptor(mouseRepRef)
        mouseInputReportChar!!.addDescriptor(mouseRepCcc)
        hidService.addCharacteristic(mouseInputReportChar)

        // Battery Service
        val battService = BluetoothGattService(UUID_BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        batteryLevelChar = BluetoothGattCharacteristic(
            UUID_BATTERY_LEVEL,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val battCcc = BluetoothGattDescriptor(UUID_CCC, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
    batteryLevelChar!!.setValue(byteArrayOf(100.toByte()))
        batteryLevelChar!!.addDescriptor(battCcc)
        battService.addCharacteristic(batteryLevelChar)

        // Device Info (optional minimal)
        val devInfo = BluetoothGattService(UUID_DEVINFO_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        gattServer?.addService(hidService)
        gattServer?.addService(battService)
        gattServer?.addService(devInfo)
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID_HID_SERVICE))
            .setIncludeTxPowerLevel(false)
            .build()
        val scanResp = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID_HID_SERVICE))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasAdvertise = checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val hasConnect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (hasAdvertise && hasConnect) {
                advertiser?.startAdvertising(settings, data, adCb)
                // Attempt to also set scan response (appearance cannot be set via API)
                try { advertiser?.startAdvertising(settings, scanResp, adCb) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "startAdvertising SecurityException: ${e.message}") }
                advertiser?.startAdvertising(settings, data, adCb)
            } else {
                android.util.Log.w("BleHogpService", "Missing BLUETOOTH_ADVERTISE/CONNECT permission; not starting BLE advertising")
            }
        } else {
            advertiser?.startAdvertising(settings, data, adCb)
            try { advertiser?.startAdvertising(settings, scanResp, adCb) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "startAdvertising SecurityException: ${e.message}") }
            advertiser?.startAdvertising(settings, data, adCb)
        }
    }

    private fun hasAdvertisePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+ both BLUETOOTH_ADVERTISE and BLUETOOTH_CONNECT are required for advertising control
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private val adCb = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            android.util.Log.d("BTKB", "BLE advertise started")
        }
        override fun onStartFailure(errorCode: Int) {
            android.util.Log.e("BTKB", "BLE advertise failed: $errorCode")
        }
    }

    @Suppress("DEPRECATION")
    private val gattCb = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            android.util.Log.d("BTKB", "GATT conn state=$newState status=$status dev=${device.address}")
            if (newState == BluetoothProfile.STATE_CONNECTED) connected.add(device) else connected.remove(device)
        }
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val v = characteristic.value ?: byteArrayOf()
            val start = offset.coerceAtMost(v.size)
            val slice = v.copyOfRange(start, v.size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                } else {
                    android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot sendResponse")
                }
            } else {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
            }
        }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
                if (characteristic.uuid == UUID_PROTOCOL_MODE) {
                protocolMode = value.firstOrNull() ?: 0
                characteristic.setValue(byteArrayOf(protocolMode))
            } else if (characteristic.uuid == UUID_HID_CONTROL_POINT) {
                // 0=Suspend, 1=Exit Suspend
            }
            if (responseNeeded) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot sendResponse")
                    }
                } else {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                }
            }
        }
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val v = descriptor.value ?: byteArrayOf()
            val start = offset.coerceAtMost(v.size)
            val slice = v.copyOfRange(start, v.size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                } else {
                    android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot sendResponse")
                }
            } else {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
            }
        }
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            descriptor.setValue(value)
            if (responseNeeded) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot sendResponse")
                    }
                } else {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "sendResponse SecurityException: ${e.message}") }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun notifyKeyboard(report8: ByteArray) {
        val snapshot = synchronized(connected) { connected.toList() }
        kbInputChar?.setValue(report8)
        kbInputReportChar?.setValue(report8)
        snapshot.forEach {
            kbInputChar?.let { ch ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gattServer?.notifyCharacteristicChanged(it, ch, false, report8)
                            } else {
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(it, ch, false)
                            }
                        } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot notifyCharacteristicChanged")
                    }
                } else {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gattServer?.notifyCharacteristicChanged(it, ch, false, report8)
                        } else {
                            @Suppress("DEPRECATION")
                            gattServer?.notifyCharacteristicChanged(it, ch, false)
                        }
                    } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                }
            }
            kbInputReportChar?.let { ch ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gattServer?.notifyCharacteristicChanged(it, ch, false, report8)
                            } else {
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(it, ch, false)
                            }
                        } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot notifyCharacteristicChanged")
                    }
                } else {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gattServer?.notifyCharacteristicChanged(it, ch, false, report8)
                        } else {
                            @Suppress("DEPRECATION")
                            gattServer?.notifyCharacteristicChanged(it, ch, false)
                        }
                    } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    fun notifyMouse(report5: ByteArray) {
        val snapshot = synchronized(connected) { connected.toList() }
        mouseInputChar?.setValue(report5)
        mouseInputReportChar?.setValue(report5)
        snapshot.forEach {
            mouseInputChar?.let { ch ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gattServer?.notifyCharacteristicChanged(it, ch, false, report5)
                            } else {
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(it, ch, false)
                            }
                        } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot notifyCharacteristicChanged")
                    }
                } else {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gattServer?.notifyCharacteristicChanged(it, ch, false, report5)
                        } else {
                            @Suppress("DEPRECATION")
                            gattServer?.notifyCharacteristicChanged(it, ch, false)
                        }
                    } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                }
            }
            mouseInputReportChar?.let { ch ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gattServer?.notifyCharacteristicChanged(it, ch, false, report5)
                            } else {
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(it, ch, false)
                            }
                        } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                    } else {
                        android.util.Log.w("BleHogpService", "Missing BLUETOOTH_CONNECT permission; cannot notifyCharacteristicChanged")
                    }
                } else {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gattServer?.notifyCharacteristicChanged(it, ch, false, report5)
                        } else {
                            @Suppress("DEPRECATION")
                            gattServer?.notifyCharacteristicChanged(it, ch, false)
                        }
                    } catch (e: SecurityException) { android.util.Log.w("BleHogpService", "notifyCharacteristicChanged SecurityException: ${e.message}") }
                }
            }
        }
    }

    private fun startInForeground() {
        val channelId = "ble_hogp_service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "BLE HID Service", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("BLE HID active")
            .setContentText("Advertising as BLE keyboard/mouse")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        // Try to start as a foreground service but handle restrictive
        // platform behavior gracefully. If startForeground throws an
        // exception (MissingForegroundServiceTypeException or
        // SecurityException), fall back to just posting the notification
        // so the process won't be killed at startup.
        try {
            startForeground(2, notif)
        } catch (e: Exception) {
            android.util.Log.e("BleHogpService", "startForeground failed: ${e.message}")
            nm.notify(2, notif)
        }
    }

    // Simplified HID Report Map: Boot keyboard only (remove mouse for Windows experiment)
    private val HID_REPORT_MAP = byteArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x05, 0x07,       // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),  // Usage Min (224)
        0x29.toByte(), 0xE7.toByte(),  // Usage Max (231)
        0x15, 0x00,       // Logical Min (0)
        0x25, 0x01,       // Logical Max (1)
        0x75, 0x01,       // Report Size (1)
        0x95.toByte(), 0x08, // Report Count (8)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs) mod
        0x95.toByte(), 0x01, // Report Count (1)
        0x75, 0x08,       // Report Size (8)
        0x81.toByte(), 0x01, // Input (Const)
        0x95.toByte(), 0x05, // Report Count (5)
        0x75, 0x01,       // Report Size (1)
        0x05, 0x08,       // Usage Page (LEDs)
        0x19.toByte(), 0x01, // Usage Min (1)
        0x29.toByte(), 0x05, // Usage Max (5)
        0x91.toByte(), 0x02, // Output (Data,Var,Abs)
        0x95.toByte(), 0x01, // Report Count (1)
        0x75, 0x03,       // Report Size (3)
        0x91.toByte(), 0x01, // Output (Const)
        0x95.toByte(), 0x06, // Report Count (6)
        0x75, 0x08,       // Report Size (8)
        0x15, 0x00,       // Logical Min (0)
        0x25, 0x65,       // Logical Max (101)
        0x05, 0x07,       // Usage Page (Key Codes)
        0x19.toByte(), 0x00, // Usage Min (0)
        0x29.toByte(), 0x65, // Usage Max (101)
        0x81.toByte(), 0x00, // Input (Data,Array,Abs)
        0xC0.toByte()     // End Collection
    )
}
