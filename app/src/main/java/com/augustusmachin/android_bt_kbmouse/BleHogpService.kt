package com.augustusmachin.android_bt_kbmouse

import android.Manifest
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
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import java.util.Collections
import java.util.UUID

private const val HID_INFO_BCD_HID_LSB: Byte = 0x11
private const val SDK_INT_OREO = 26
private const val SDK_INT_MARSHMALLOW = 23

class BleHogpService : Service(), HogpNotifier {
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

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var kbInputChar: BluetoothGattCharacteristic? = null
    private var mouseInputChar: BluetoothGattCharacteristic? = null
    private var batteryLevelChar: BluetoothGattCharacteristic? = null
    private var kbInputReportChar: BluetoothGattCharacteristic? = null
    private var mouseInputReportChar: BluetoothGattCharacteristic? = null
    private var previousName: String? = null

    private val connected = Collections.synchronizedSet(mutableSetOf<BluetoothDevice>())
    private var protocolMode: Byte = 0x01 // 0=Boot, 1=Report (prefer report mode)

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        val hasConnect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (!hasConnect) {
            DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; deferring BLE init")
            return
        }
        startInForeground()
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter
        advertiser = adapter.bluetoothLeAdvertiser
        try {
            previousName = adapter.name
            adapter.name = "Bluetooth Keyboard"
        } catch (_: Exception) {
        }
        gattServer = mgr.openGattServer(this, gattCb)
        setupGattServices()
        startAdvertising()
    }

    override fun onDestroy() {
        super.onDestroy()
        val hasPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (hasPermissions) {
            try {
                advertiser?.stopAdvertising(adCb)
            } catch (e: SecurityException) {
                DebugLog.e("BleHogpService", "stopAdvertising SecurityException: ${e.message}")
            }
        } else {
            DebugLog.e("BleHogpService", "Missing permissions on destroy; skipping stopAdvertising")
        }
        try {
            gattServer?.close()
        } catch (_: Exception) {
        }
        try {
            val ad = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            previousName?.let { ad.name = it }
        } catch (_: Exception) {
        }
    }

    private fun setupGattServices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; skipping GATT setup")
            return
        }

        val hidService = BluetoothGattService(UUID_HID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val proto =
            BluetoothGattCharacteristic(
                UUID_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ).also { it.setValueCompat(byteArrayOf(protocolMode)) }
        hidService.addCharacteristic(proto)

        val hidInfo =
            BluetoothGattCharacteristic(
                UUID_HID_INFORMATION,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.setValueCompat(byteArrayOf(HID_INFO_BCD_HID_LSB, 0x01, 0x00, 0x02)) }
        hidService.addCharacteristic(hidInfo)

        val ctrlPt =
            BluetoothGattCharacteristic(
                UUID_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        hidService.addCharacteristic(ctrlPt)

        val reportMap =
            BluetoothGattCharacteristic(
                UUID_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.setValueCompat(HidDescriptorVariants.SIMPLE) }
        hidService.addCharacteristic(reportMap)

        // Boot keyboard input (notify) — report ID 1 in boot protocol
        kbInputChar =
            BluetoothGattCharacteristic(
                UUID_BOOT_KB_INPUT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_CCC,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
                    ),
                )
            }
        hidService.addCharacteristic(kbInputChar)

        // Report-mode keyboard input (report ID 1)
        kbInputReportChar =
            BluetoothGattCharacteristic(
                UUID_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_REPORT_REF,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED,
                    ).also { d -> d.setValueCompat(byteArrayOf(0x01, 0x01)) },
                )
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_CCC,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
                    ),
                )
            }
        hidService.addCharacteristic(kbInputReportChar)

        // Boot keyboard output (LED state from host — bit1=CapsLk, bit2=ScrollLk)
        hidService.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID_BOOT_KB_OUTPUT,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )

        // Boot mouse input (notify) — report ID 2 in boot protocol
        mouseInputChar =
            BluetoothGattCharacteristic(
                UUID_BOOT_MOUSE_INPUT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_CCC,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
                    ),
                )
            }
        hidService.addCharacteristic(mouseInputChar)

        // Report-mode mouse input (report ID 2)
        mouseInputReportChar =
            BluetoothGattCharacteristic(
                UUID_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_REPORT_REF,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED,
                    ).also { d -> d.setValueCompat(byteArrayOf(0x02, 0x01)) },
                )
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_CCC,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
                    ),
                )
            }
        hidService.addCharacteristic(mouseInputReportChar)

        // Battery service
        val battService = BluetoothGattService(UUID_BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        batteryLevelChar =
            BluetoothGattCharacteristic(
                UUID_BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.setValueCompat(byteArrayOf(100.toByte()))
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        UUID_CCC,
                        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
                    ),
                )
            }
        battService.addCharacteristic(batteryLevelChar)

        gattServer?.addService(hidService)
        gattServer?.addService(battService)
        gattServer?.addService(BluetoothGattService(UUID_DEVINFO_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY))
    }

    private fun startAdvertising() {
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
        val hasPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (hasPermissions) {
            try {
                advertiser?.startAdvertising(settings, data, adCb)
            } catch (e: SecurityException) {
                DebugLog.e("BleHogpService", "startAdvertising SecurityException: ${e.message}")
            }
        } else {
            DebugLog.e("BleHogpService", "Missing permissions; not starting BLE advertising")
        }
    }

    private val adCb =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                DebugLog.log("BleHogpService", "BLE advertise started")
                eventListener?.onInfo("BLE HOGP advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                DebugLog.e("BleHogpService", "BLE advertise failed: $errorCode")
                eventListener?.onError("BLE advertising failed (code $errorCode)")
            }
        }

    private val gattCb =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                DebugLog.log("BleHogpService", "GATT state=$newState status=$status dev=${device.address}")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected.add(device)
                    eventListener?.onConnected(device)
                    eventListener?.onInfo("BLE HOGP connected ${device.address}")
                } else {
                    connected.remove(device)
                    eventListener?.onDisconnected(device)
                    eventListener?.onInfo("BLE HOGP disconnected ${device.address}")
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                char: BluetoothGattCharacteristic,
            ) {
                val v = char.valueCompat() ?: byteArrayOf()
                val slice = v.copyOfRange(offset.coerceAtMost(v.size), v.size)
                gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                char: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                when (char.uuid) {
                    UUID_PROTOCOL_MODE -> {
                        protocolMode = value.firstOrNull() ?: 0
                        char.setValueCompat(byteArrayOf(protocolMode))
                    }
                    UUID_HID_CONTROL_POINT -> { /* 0=Suspend, 1=Exit Suspend — no-op */ }
                    UUID_BOOT_KB_OUTPUT -> {
                        // LED report: bit0=NumLk, bit1=CapsLk, bit2=ScrollLk
                        val leds = value.firstOrNull()?.toInt() ?: 0
                        DebugLog.log("BleHogpService", "LED output leds=$leds")
                        eventListener?.onLeds(leds)
                    }
                }
                if (responseNeeded) gattSendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor,
            ) {
                val v = descriptor.valueCompat() ?: byteArrayOf()
                val slice = v.copyOfRange(offset.coerceAtMost(v.size), v.size)
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

    private fun gattSendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        val hasConnect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (!hasConnect) {
            DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; cannot sendResponse")
            return
        }
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            DebugLog.e("BleHogpService", "sendResponse SecurityException: ${e.message}")
        }
    }

    override fun notifyKeyboard(report: ByteArray) {
        val hasConnect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (!hasConnect) {
            DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; cannot notify keyboard")
            return
        }
        val snapshot = synchronized(connected) { connected.toList() }
        listOfNotNull(kbInputChar, kbInputReportChar).forEach { ch ->
            ch.setValueCompat(report)
            snapshot.forEach { dev -> notifyOne(dev, ch, report) }
        }
    }

    override fun notifyMouse(report: ByteArray) {
        val hasConnect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (!hasConnect) {
            DebugLog.e("BleHogpService", "BLUETOOTH_CONNECT not granted; cannot notify mouse")
            return
        }
        val snapshot = synchronized(connected) { connected.toList() }
        listOfNotNull(mouseInputChar, mouseInputReportChar).forEach { ch ->
            ch.setValueCompat(report)
            snapshot.forEach { dev -> notifyOne(dev, ch, report) }
        }
    }

    private fun notifyOne(
        dev: BluetoothDevice,
        ch: BluetoothGattCharacteristic,
        report: ByteArray,
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(dev, ch, false, report)
            } else {
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(dev, ch, false)
            }
        } catch (e: SecurityException) {
            DebugLog.e("BleHogpService", "notify SecurityException: ${e.message}")
        }
    }

    private fun startInForeground() {
        val channelId = "ble_hogp_service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= SDK_INT_OREO && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "BLE HID Service", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val pi =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= SDK_INT_MARSHMALLOW) PendingIntent.FLAG_IMMUTABLE else 0),
            )
        val notif: Notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentTitle("BlueDeck (BLE) active")
                .setContentText("Advertising as BLE keyboard/mouse")
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        try {
            startForeground(2, notif)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // defensive: falls back to plain notify
            DebugLog.e("BleHogpService", "startForeground failed: ${e.message}")
            nm.notify(2, notif)
        }
    }
}

// ── Deprecation compat shims ──────────────────────────────────────────────
// BluetoothGattCharacteristic/Descriptor.setValue() and .value were deprecated
// in API 33, but remain the supported way to seed values on a GATT *server*'s
// local attributes (and the only option below API 33). Each shim contains the
// single deprecated call so the suppression is tightly scoped and documented.
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
