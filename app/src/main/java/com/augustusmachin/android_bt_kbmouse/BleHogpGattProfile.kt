package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.util.UUID

internal data class BleHogpGattProfile(
    val services: List<BluetoothGattService>,
    val keyboardBootInput: BluetoothGattCharacteristic,
    val mouseBootInput: BluetoothGattCharacteristic,
    val keyboardInputReport: BluetoothGattCharacteristic,
    val mouseInputReport: BluetoothGattCharacteristic,
    val batteryLevel: BluetoothGattCharacteristic,
)

internal object BleHogpGattProfileBuilder {
    private val hidServiceUuid = uuid("1812")
    private val batteryServiceUuid = uuid("180F")
    private val deviceInfoServiceUuid = uuid("180A")
    private val hidInformationUuid = uuid("2A4A")
    private val reportMapUuid = uuid("2A4B")
    private val hidControlPointUuid = uuid("2A4C")
    private val protocolModeUuid = uuid("2A4E")
    private val bootKeyboardInputUuid = uuid("2A22")
    private val bootKeyboardOutputUuid = uuid("2A32")
    private val bootMouseInputUuid = uuid("2A33")
    private val reportUuid = uuid("2A4D")
    private val cccUuid = uuid("2902")
    private val reportReferenceUuid = uuid("2908")
    private val batteryLevelUuid = uuid("2A19")

    fun build(protocolMode: Byte): BleHogpGattProfile {
        val hidService = BluetoothGattService(hidServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        addMetadata(hidService, protocolMode)
        val keyboard = addKeyboard(hidService)
        val mouse = addMouse(hidService)
        val battery = buildBattery()
        val deviceInfo = BluetoothGattService(deviceInfoServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        return BleHogpGattProfile(
            services = listOf(hidService, battery.first, deviceInfo),
            keyboardBootInput = keyboard.first,
            keyboardInputReport = keyboard.second,
            mouseBootInput = mouse.first,
            mouseInputReport = mouse.second,
            batteryLevel = battery.second,
        )
    }

    private fun addMetadata(
        service: BluetoothGattService,
        protocolMode: Byte,
    ) {
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                protocolModeUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ).also { it.setValueCompat(byteArrayOf(protocolMode)) },
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                hidInformationUuid,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.setValueCompat(byteArrayOf(0x11, 0x01, 0x00, 0x02)) },
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                hidControlPointUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                reportMapUuid,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.setValueCompat(HidDescriptorVariants.SIMPLE) },
        )
    }

    private fun addKeyboard(service: BluetoothGattService): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val bootInput =
            BluetoothGattCharacteristic(
                bootKeyboardInputUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.addDescriptor(cccDescriptor()) }
        service.addCharacteristic(bootInput)

        val inputReport =
            BluetoothGattCharacteristic(
                reportUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(reportReferenceDescriptor(0x01))
                it.addDescriptor(cccDescriptor())
            }
        service.addCharacteristic(inputReport)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                bootKeyboardOutputUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        return bootInput to inputReport
    }

    private fun addMouse(service: BluetoothGattService): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic> {
        val bootInput =
            BluetoothGattCharacteristic(
                bootMouseInputUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also { it.addDescriptor(cccDescriptor()) }
        service.addCharacteristic(bootInput)

        val inputReport =
            BluetoothGattCharacteristic(
                reportUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.addDescriptor(reportReferenceDescriptor(0x02))
                it.addDescriptor(cccDescriptor())
            }
        service.addCharacteristic(inputReport)
        return bootInput to inputReport
    }

    private fun buildBattery(): Pair<BluetoothGattService, BluetoothGattCharacteristic> {
        val service = BluetoothGattService(batteryServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val level =
            BluetoothGattCharacteristic(
                batteryLevelUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).also {
                it.setValueCompat(byteArrayOf(100.toByte()))
                it.addDescriptor(cccDescriptor())
            }
        service.addCharacteristic(level)
        return service to level
    }

    private fun cccDescriptor(): BluetoothGattDescriptor =
        BluetoothGattDescriptor(
            cccUuid,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
        )

    private fun reportReferenceDescriptor(reportId: Byte): BluetoothGattDescriptor =
        BluetoothGattDescriptor(reportReferenceUuid, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED).also {
            it.setValueCompat(byteArrayOf(reportId, 0x01))
        }

    private fun uuid(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
}

internal fun safeBleDeviceAddress(device: BluetoothDevice): String? =
    try {
        device.address
    } catch (e: SecurityException) {
        DebugLog.e("BleHogpService", "Connected device address unavailable: ${e.message}")
        null
    }

internal fun safeBleDeviceLabel(
    device: BluetoothDevice,
    address: String?,
): String =
    try {
        device.name?.takeIf { it.isNotBlank() } ?: address ?: "Bluetooth host"
    } catch (e: SecurityException) {
        DebugLog.e("BleHogpService", "Connected device name unavailable: ${e.message}")
        address ?: "Bluetooth host"
    }

@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.setValueCompat(value: ByteArray) {
    setValue(value)
}

@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.setValueCompat(value: ByteArray) {
    setValue(value)
}
