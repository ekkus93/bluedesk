package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.concurrent.Executor

class BluetoothHidModuleTest {
    @Test
    fun `immediate registerApp false is surfaced as rejection`() {
        val proxy = Mockito.mock(BluetoothHidDevice::class.java)
        Mockito.`when`(
            proxy.registerApp(
                ArgumentMatchers.any(BluetoothHidDeviceAppSdpSettings::class.java),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(Executor::class.java),
                ArgumentMatchers.any(BluetoothHidDevice.Callback::class.java),
            ),
        ).thenReturn(false)
        val errors = mutableListOf<String>()
        val module = BluetoothHidModule()
        module.listener = listener(errors)

        val result = module.registerApp(proxy, simplified = false)

        assertEquals(HidRegistrationRequestResult.Rejected, result)
        assertTrue(errors.single().contains("rejected immediately"))
    }

    @Test
    fun `immediate registerApp true is accepted pending async callback`() {
        val proxy = Mockito.mock(BluetoothHidDevice::class.java)
        Mockito.`when`(
            proxy.registerApp(
                ArgumentMatchers.any(BluetoothHidDeviceAppSdpSettings::class.java),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(Executor::class.java),
                ArgumentMatchers.any(BluetoothHidDevice.Callback::class.java),
            ),
        ).thenReturn(true)
        val module = BluetoothHidModule()

        assertEquals(HidRegistrationRequestResult.Accepted, module.registerApp(proxy, simplified = true))
    }

    private fun listener(errors: MutableList<String>) =
        object : BluetoothHidModule.HidEventListener {
            override fun onAppStatus(registered: Boolean) = Unit

            override fun onConnectionStateChanged(
                device: android.bluetooth.BluetoothDevice,
                state: Int,
            ) = Unit

            override fun onError(message: String) {
                errors += message
            }
        }
}
