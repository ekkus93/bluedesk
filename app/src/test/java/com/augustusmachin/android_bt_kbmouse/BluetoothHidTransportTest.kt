package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class BluetoothHidTransportTest {
    private val context = Mockito.mock(Context::class.java)
    private val device = Mockito.mock(BluetoothDevice::class.java)
    private val hid = Mockito.mock(BluetoothHidDevice::class.java)
    private val report = byteArrayOf(1, 2, 3)

    @Test
    fun `missing device is explicit failure`() {
        val result = transport(device = null).send(1, report)
        assertFailure(result, HidDeliveryFailureCode.DEVICE_MISSING)
    }

    @Test
    fun `missing HID proxy is explicit failure`() {
        val result = transport(hid = null).send(1, report)
        assertFailure(result, HidDeliveryFailureCode.HID_PROXY_MISSING)
    }

    @Test
    fun `permission denial is explicit failure`() {
        val result = transport(granted = false).send(1, report)
        assertFailure(result, HidDeliveryFailureCode.PERMISSION_DENIED)
    }

    @Test
    fun `unsupported api is explicit failure`() {
        val result = transport(sdkInt = 27).send(1, report)
        assertFailure(result, HidDeliveryFailureCode.UNSUPPORTED_API)
    }

    @Test
    fun `sendReport false is report rejection`() {
        Mockito.`when`(hid.sendReport(device, 1, report)).thenReturn(false)
        val errors = mutableListOf<String>()

        val result = transport(errors = errors).send(1, report)

        assertFailure(result, HidDeliveryFailureCode.REPORT_REJECTED)
        assertTrue(errors.single().contains("rejected"))
    }

    @Test
    fun `keyboard SecurityException is explicit permission failure`() {
        Mockito.`when`(hid.sendReport(device, 1, report)).thenThrow(SecurityException("revoked"))
        val result = transport().send(1, report)
        assertFailure(result, HidDeliveryFailureCode.PERMISSION_DENIED)
    }

    @Test
    fun `mouse exception uses same explicit failure pipeline`() {
        val mouseId = HidDescriptorVariants.REPORT_ID_MOUSE.toInt()
        Mockito.`when`(hid.sendReport(device, mouseId, report)).thenThrow(IllegalStateException("gone"))
        val errors = mutableListOf<String>()

        val result = transport(errors = errors).send(mouseId, report)

        assertFailure(result, HidDeliveryFailureCode.TRANSPORT_EXCEPTION)
        assertTrue(errors.single().contains("mouse HID report failed"))
    }

    @Test
    fun `accepted report returns sent`() {
        Mockito.`when`(hid.sendReport(device, 1, report)).thenReturn(true)
        assertEquals(HidDeliveryResult.Sent, transport().send(1, report))
    }

    private fun transport(
        device: BluetoothDevice? = this.device,
        hid: BluetoothHidDevice? = this.hid,
        sdkInt: Int = 34,
        granted: Boolean = true,
        errors: MutableList<String> = mutableListOf(),
    ) = BluetoothHidTransport(
        context = context,
        currentDevice = { device },
        currentHid = { hid },
        onError = { errors += it },
        sdkInt = sdkInt,
        hasPermissions = { granted },
    )

    private fun assertFailure(
        result: HidDeliveryResult,
        expected: HidDeliveryFailureCode,
    ) {
        assertTrue(result is HidDeliveryResult.Failure)
        assertEquals(expected, (result as HidDeliveryResult.Failure).code)
    }
}
