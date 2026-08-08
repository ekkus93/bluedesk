package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothHidDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class BluetoothHidModuleTest {
    @Test
    fun `immediate registerApp false is surfaced as rejection`() {
        val proxy = Mockito.mock(BluetoothHidDevice::class.java)
        Mockito.`when`(
            proxy.registerApp(
                ArgumentMatchers.any(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
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
                ArgumentMatchers.any(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
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
