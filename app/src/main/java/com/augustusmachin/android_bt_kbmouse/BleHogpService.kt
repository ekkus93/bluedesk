package com.augustusmachin.android_bt_kbmouse

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import java.util.Collections
import java.util.UUID

private const val SDK_INT_OREO = 26
private const val SDK_INT_MARSHMALLOW = 23

class BleHogpService : Service(), HogpNotifier {
    companion object {
        private const val TAG = "BleHogpService"
        private val UUID_HID_SERVICE = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        private val UUID_HID_CONTROL_POINT = UUID.fromString("00002A4C-0000-1000-8000-00805f9b34fb")
        private val UUID_PROTOCOL_MODE = UUID.fromString("00002A4E-0000-1000-8000-00805f9b34fb")
        private val UUID_BOOT_KB_OUTPUT = UUID.fromString("00002A32-0000-1000-8000-00805f9b34fb")
        const val READY_MESSAGE = "BLE advertising started"
    }

    interface ServiceEventListener {
        fun onConnected(device: BluetoothDevice)

        fun onDisconnected(device: BluetoothDevice?)

        fun onInfo(message: String)

        fun onError(message: String)

        fun onLeds(leds: Int)
    }

    var eventListener: ServiceEventListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): BleHogpService = this@BleHogpService
    }

    private val readiness = BleHogpReadinessTracker()
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var kbInputChar: BluetoothGattCharacteristic? = null
    private var mouseInputChar: BluetoothGattCharacteristic? = null
    private var batteryLevelChar: BluetoothGattCharacteristic? = null
    private var kbInputReportChar: BluetoothGattCharacteristic? = null
    private var mouseInputReportChar: BluetoothGattCharacteristic? = null
    private var previousName: String? = null
    private val connected = Collections.synchronizedSet(mutableSetOf<BluetoothDevice>())
    private var protocolMode: Byte = 0x01
    private var mandatoryServices: Map<String, BluetoothGattService> = emptyMap()
    private var advertisingRequested = false
    private var cleanupComplete = false

    fun currentStartupState(): BleHogpStartupState = readiness.state

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        readiness.advance(BleHogpStartupStage.VALIDATING_PERMISSIONS)
        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForBleStartup(Build.VERSION.SDK_INT))) {
            failStartup("BLE HOGP requires Bluetooth connect/advertise permissions")
            return
        }

        readiness.advance(BleHogpStartupStage.STARTING_FOREGROUND)
        if (!startInForeground()) {
            failStartup("BLE HOGP could not enter foreground service state")
            return
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (manager == null || adapter == null) {
            failStartup("Bluetooth adapter is unavailable")
            return
        }

        readiness.advance(BleHogpStartupStage.RESOLVING_ADVERTISER)
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            failStartup("BLE advertising is unavailable on this device")
            return
        }

        try {
            previousName = adapter.name
            adapter.name = "BlueDeck"
        } catch (e: SecurityException) {
            failStartup("Could not configure BLE adapter name: ${e.message}")
            return
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e(TAG, "Could not change BLE adapter name: ${e.message}")
        }

        readiness.advance(BleHogpStartupStage.OPENING_GATT_SERVER)
        gattServer =
            try {
                manager.openGattServer(this, gattCb)
            } catch (e: SecurityException) {
                failStartup("Opening BLE GATT server failed due to permission: ${e.message}")
                return
            }
        if (gattServer == null) {
            failStartup("Bluetooth manager returned no GATT server")
            return
        }

        beginGattRegistration()
    }

    override fun onDestroy() {
        cleanupBleResources()
        super.onDestroy()
    }

    private fun beginGattRegistration() {
        val profile = BleHogpGattProfileBuilder.build(protocolMode)
        kbInputChar = profile.keyboardBootInput
        mouseInputChar = profile.mouseBootInput
        kbInputReportChar = profile.keyboardInputReport
        mouseInputReportChar = profile.mouseInputReport
        batteryLevelChar = profile.batteryLevel
        mandatoryServices = profile.services.associateBy { it.uuid.toString() }
        readiness.beginGattRegistration(profile.services.map { it.uuid.toString() })
        registerNextGattService()
    }

    @SuppressLint("MissingPermission")
    private fun registerNextGattService() {
        val serviceId = readiness.nextServiceToRegister()
        if (serviceId == null) {
            if (readiness.state is BleHogpStartupState.Failed) {
                failStartup((readiness.state as BleHogpStartupState.Failed).message)
            }
            return
        }
        val service = mandatoryServices[serviceId]
        if (service == null) {
            failStartup("Mandatory GATT service $serviceId is missing from startup plan")
            return
        }
        val server = gattServer
        if (server == null) {
            failStartup("GATT server disappeared during service registration")
            return
        }
        val accepted =
            try {
                server.addService(service)
            } catch (e: SecurityException) {
                failStartup("GATT addService permission failure for $serviceId: ${e.message}")
                return
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failStartup("GATT addService failed for $serviceId: ${e.message}")
                return
            }
        if (!readiness.onAddServiceImmediate(serviceId, accepted)) {
            failStartup((readiness.state as BleHogpStartupState.Failed).message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForBleStartup(Build.VERSION.SDK_INT))) {
            failStartup("BLE permissions were revoked before advertising")
            return
        }
        val activeAdvertiser = advertiser
        if (activeAdvertiser == null) {
            failStartup("BLE advertiser disappeared before advertising start")
            return
        }
        readiness.beginAdvertising()
        if (readiness.state is BleHogpStartupState.Failed) {
            failStartup((readiness.state as BleHogpStartupState.Failed).message)
            return
        }
        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
        val data =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID_HID_SERVICE))
                .setIncludeTxPowerLevel(false)
                .build()
        try {
            advertisingRequested = true
            activeAdvertiser.startAdvertising(settings, data, adCb)
        } catch (e: SecurityException) {
            failStartup("BLE advertising permission failure: ${e.message}")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            failStartup("BLE advertising start failed: ${e.message}")
        }
    }

    private val adCb =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                readiness.advertisingSucceeded()
                if (readiness.state is BleHogpStartupState.Ready) {
                    DebugLog.log(TAG, READY_MESSAGE)
                    eventListener?.onInfo(READY_MESSAGE)
                } else {
                    failStartup((readiness.state as BleHogpStartupState.Failed).message)
                }
            }

            override fun onStartFailure(errorCode: Int) {
                failStartup("BLE advertising failed (code $errorCode)")
            }
        }

    private val gattCb =
        object : BluetoothGattServerCallback() {
            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService,
            ) {
                val allRegistered =
                    readiness.onServiceAdded(
                        service.uuid.toString(),
                        status == BluetoothGatt.GATT_SUCCESS,
                    )
                if (readiness.state is BleHogpStartupState.Failed) {
                    failStartup((readiness.state as BleHogpStartupState.Failed).message)
                } else if (allRegistered) {
                    startAdvertising()
                } else {
                    registerNextGattService()
                }
            }

            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                val address = safeBleDeviceAddress(device)
                DebugLog.log(TAG, "GATT state=$newState status=$status")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected.add(device)
                    StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(address))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(safeBleDeviceLabel(device, address)))
                    eventListener?.onConnected(device)
                    eventListener?.onInfo("BLE HOGP host connected")
                } else {
                    connected.remove(device)
                    StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(null))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(null))
                    eventListener?.onDisconnected(device)
                    eventListener?.onInfo("BLE HOGP host disconnected")
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val value = characteristic.valueCompat() ?: byteArrayOf()
                val slice = value.copyOfRange(offset.coerceAtMost(value.size), value.size)
                gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                when (characteristic.uuid) {
                    UUID_PROTOCOL_MODE -> {
                        protocolMode = value.firstOrNull() ?: 0
                        characteristic.setValueCompat(byteArrayOf(protocolMode))
                    }
                    UUID_HID_CONTROL_POINT -> Unit
                    UUID_BOOT_KB_OUTPUT -> eventListener?.onLeds(value.firstOrNull()?.toInt() ?: 0)
                }
                if (responseNeeded) gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor,
            ) {
                val value = descriptor.valueCompat() ?: byteArrayOf()
                val slice = value.copyOfRange(offset.coerceAtMost(value.size), value.size)
                gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                descriptor.setValueCompat(value)
                if (responseNeeded) gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

    @SuppressLint("MissingPermission")
    private fun gattSendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))) {
            DebugLog.e(TAG, "Bluetooth connect permission unavailable; cannot send GATT response")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            return
        }
        val server = gattServer
        if (server == null) {
            DebugLog.e(TAG, "Cannot send GATT response: GATT server is unavailable")
            return
        }
        try {
            server.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            DebugLog.e(TAG, "GATT response permission failure: ${e.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
        }
    }

    override fun notifyKeyboard(report: ByteArray): HidDeliveryResult =
        notifyReport(report, listOfNotNull(kbInputChar, kbInputReportChar), "keyboard")

    override fun notifyMouse(report: ByteArray): HidDeliveryResult =
        notifyReport(report, listOfNotNull(mouseInputChar, mouseInputReportChar), "mouse")

    @SuppressLint("MissingPermission")
    private fun notifyReport(
        report: ByteArray,
        characteristics: List<BluetoothGattCharacteristic>,
        kind: String,
    ): HidDeliveryResult {
        if (readiness.state !is BleHogpStartupState.Ready) {
            return deliveryFailure(
                HidDeliveryFailureCode.HID_PROXY_MISSING,
                "BLE $kind report rejected: backend is not ready",
            )
        }
        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            return deliveryFailure(
                HidDeliveryFailureCode.PERMISSION_DENIED,
                "BLE $kind report rejected: connect permission is unavailable",
            )
        }
        val server =
            gattServer
                ?: return deliveryFailure(
                    HidDeliveryFailureCode.HID_PROXY_MISSING,
                    "BLE $kind report rejected: GATT server is unavailable",
                )
        val devices = synchronized(connected) { connected.toList() }
        if (devices.isEmpty()) {
            return deliveryFailure(
                HidDeliveryFailureCode.DEVICE_MISSING,
                "BLE $kind report rejected: no host is connected",
            )
        }
        if (characteristics.isEmpty()) {
            return deliveryFailure(
                HidDeliveryFailureCode.HID_PROXY_MISSING,
                "BLE $kind report rejected: GATT input characteristic is unavailable",
            )
        }

        return try {
            for (characteristic in characteristics) {
                characteristic.setValueCompat(report)
                for (device in devices) {
                    val accepted =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            server.notifyCharacteristicChanged(device, characteristic, false, report) ==
                                android.bluetooth.BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            server.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    if (!accepted) {
                        return deliveryFailure(
                            HidDeliveryFailureCode.REPORT_REJECTED,
                            "BLE $kind notification was rejected",
                        )
                    }
                }
            }
            HidDeliveryResult.Sent
        } catch (e: SecurityException) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            deliveryFailure(
                HidDeliveryFailureCode.PERMISSION_DENIED,
                "BLE $kind notification permission failure: ${e.message}",
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            deliveryFailure(
                HidDeliveryFailureCode.TRANSPORT_EXCEPTION,
                "BLE $kind notification failed: ${e.message}",
            )
        }
    }

    private fun deliveryFailure(
        code: HidDeliveryFailureCode,
        message: String,
    ): HidDeliveryResult.Failure {
        DebugLog.e(TAG, message)
        eventListener?.onError(message)
        return HidDeliveryResult.Failure(code, message)
    }

    private fun failStartup(message: String) {
        readiness.fail(message)
        val persisted = (readiness.state as? BleHogpStartupState.Failed)?.message ?: message
        DebugLog.e(TAG, persisted)
        eventListener?.onError(persisted)
        cleanupBleResources()
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun cleanupBleResources() {
        if (cleanupComplete) return
        cleanupComplete = true
        val hasBlePermissions =
            PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForBleStartup(Build.VERSION.SDK_INT))
        if (advertisingRequested && hasBlePermissions) {
            try {
                advertiser?.stopAdvertising(adCb)
            } catch (e: SecurityException) {
                DebugLog.e(TAG, "stopAdvertising permission failure during cleanup: ${e.message}")
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e(TAG, "stopAdvertising failed during cleanup: ${e.message}")
            }
        }
        advertisingRequested = false
        try {
            gattServer?.close()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e(TAG, "GATT close failed during cleanup: ${e.message}")
        }
        if (hasBlePermissions && previousName != null) {
            try {
                val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                manager?.adapter?.name = previousName
            } catch (e: SecurityException) {
                DebugLog.e(TAG, "Adapter-name restore permission failure: ${e.message}")
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e(TAG, "Adapter-name restore failed: ${e.message}")
            }
        }
        connected.clear()
        gattServer = null
        advertiser = null
        kbInputChar = null
        mouseInputChar = null
        kbInputReportChar = null
        mouseInputReportChar = null
        batteryLevelChar = null
        mandatoryServices = emptyMap()
    }

    private fun startInForeground(): Boolean {
        val channelId = "ble_hogp_service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= SDK_INT_OREO && notificationManager.getNotificationChannel(channelId) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "BLE HID Service", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= SDK_INT_MARSHMALLOW) PendingIntent.FLAG_IMMUTABLE else 0),
            )
        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentTitle("BlueDeck (BLE) active")
                .setContentText("Starting BLE keyboard/mouse")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        return try {
            startForeground(2, notification)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e(TAG, "startForeground failed: ${e.message}")
            false
        }
    }
}

@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.setValueCompat(value: ByteArray) {
    setValue(value)
}

@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.valueCompat(): ByteArray? = value

@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.setValueCompat(value: ByteArray) {
    setValue(value)
}

@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.valueCompat(): ByteArray? = value
