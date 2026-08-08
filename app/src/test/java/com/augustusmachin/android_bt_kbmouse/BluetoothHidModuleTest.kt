package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
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
        assertTrue(module.currentStartupState() is ClassicHidStartupState.Failed)
    }

    @Test
    fun `immediate registerApp true remains pending until callback success`() {
        val proxy = acceptedProxy()
        val callback = ArgumentCaptor.forClass(BluetoothHidDevice.Callback::class.java)
        val module = BluetoothHidModule()

        assertEquals(HidRegistrationRequestResult.Accepted, module.registerApp(proxy, simplified = true))
        assertEquals(ClassicHidStartupState.WaitingForRegistrationCallback, module.currentStartupState())
        Mockito.verify(proxy).registerApp(
            ArgumentMatchers.any(BluetoothHidDeviceAppSdpSettings::class.java),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.any(Executor::class.java),
            callback.capture(),
        )

        callback.value.onAppStatusChanged(null, true)

        assertEquals(ClassicHidStartupState.Ready, module.currentStartupState())
    }

    @Test
    fun `accepted request followed by callback failure becomes failed`() {
        val proxy = acceptedProxy()
        val callback = ArgumentCaptor.forClass(BluetoothHidDevice.Callback::class.java)
        val errors = mutableListOf<String>()
        val module = BluetoothHidModule()
        module.listener = listener(errors)

        assertEquals(HidRegistrationRequestResult.Accepted, module.registerApp(proxy, simplified = false))
        Mockito.verify(proxy).registerApp(
            ArgumentMatchers.any(BluetoothHidDeviceAppSdpSettings::class.java),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.any(Executor::class.java),
            callback.capture(),
        )

        callback.value.onAppStatusChanged(null, false)

        assertTrue(module.currentStartupState() is ClassicHidStartupState.Failed)
    }

    private fun acceptedProxy(): BluetoothHidDevice =
        Mockito.mock(BluetoothHidDevice::class.java).also { proxy ->
            Mockito.`when`(
                proxy.registerApp(
                    ArgumentMatchers.any(BluetoothHidDeviceAppSdpSettings::class.java),
                    ArgumentMatchers.isNull(),
                    ArgumentMatchers.isNull(),
                    ArgumentMatchers.any(Executor::class.java),
                    ArgumentMatchers.any(BluetoothHidDevice.Callback::class.java),
                ),
            ).thenReturn(true)
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
