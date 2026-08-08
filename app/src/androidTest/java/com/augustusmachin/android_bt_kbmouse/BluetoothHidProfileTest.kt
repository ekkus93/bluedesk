package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Layer 2 instrumented tests: verify the BluetoothHidDevice profile can be obtained
 * and that our HID descriptors are accepted by the Android Bluetooth stack.
 *
 * These are hardware/stack-dependent tests. Emulators without a Bluetooth adapter skip
 * the relevant cases rather than failing the non-physical UI matrix.
 */
@SuppressLint("NewApi", "MissingPermission")
@RunWith(AndroidJUnit4::class)
class BluetoothHidProfileTest {
    companion object {
        @BeforeClass @JvmStatic
        fun clearStaleRegistration() {
            if (Build.VERSION.SDK_INT < 28) return
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val adapter =
                (
                    context.getSystemService(Context.BLUETOOTH_SERVICE)
                        as? BluetoothManager
                )?.adapter ?: return
            if (!adapter.isEnabled) return
            val latch = CountDownLatch(1)
            var proxy: BluetoothHidDevice? = null
            adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profile: Int,
                        p: BluetoothProfile,
                    ) {
                        proxy = p as BluetoothHidDevice
                        latch.countDown()
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                },
                BluetoothProfile.HID_DEVICE,
            )
            if (latch.await(3, TimeUnit.SECONDS) && proxy != null) {
                runCatching { proxy!!.unregisterApp() }
                Thread.sleep(1500)
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy!!)
            }
            Thread.sleep(500)
        }
    }

    @Before
    fun grantBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            val auto = InstrumentationRegistry.getInstrumentation().uiAutomation
            auto.executeShellCommand("pm grant $pkg ${Manifest.permission.BLUETOOTH_CONNECT}").close()
            auto.executeShellCommand("pm grant $pkg ${Manifest.permission.BLUETOOTH_SCAN}").close()
        }
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val btAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Test
    fun bluetooth_adapterIsAvailableAndEnabled() {
        val adapter = btAdapter
        assumeTrue("No Bluetooth adapter on this device", adapter != null)
        assumeTrue("Bluetooth is disabled on this device", adapter!!.isEnabled)
        assertTrue(adapter.isEnabled)
    }

    @Test
    fun bluetooth_hidDeviceProfileProxyCanBeObtained() {
        assumeApi28()
        val adapter = requireEnabledAdapter() ?: return

        val latch = CountDownLatch(1)
        var obtained = false

        adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile,
                ) {
                    obtained = true
                    latch.countDown()
                    adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.HID_DEVICE,
        )

        assertTrue("HID_DEVICE proxy not returned within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertTrue("HID_DEVICE profile not supported on this device", obtained)
    }

    @Test
    fun registerApp_simpleDescriptor_stackAcceptsIt() {
        assertRegisters(simplified = true)
    }

    @Test
    fun registerApp_fullDescriptor_stackAcceptsIt() {
        assertRegisters(simplified = false)
    }

    @Test
    fun unregisterApp_receivesRegisteredFalseCallback() {
        assumeApi28()
        val adapter = requireEnabledAdapter() ?: return
        val hid = obtainHidProxy(adapter) ?: return
        val module = BluetoothHidModule()

        val registerLatch = CountDownLatch(1)
        module.listener = makeListener(onStatus = { registerLatch.countDown() })
        module.registerApp(hid, simplified = true)
        assertTrue("register callback not received within 8 s", registerLatch.await(8, TimeUnit.SECONDS))

        val unregisterLatch = CountDownLatch(1)
        var unregisteredValue: Boolean? = null
        module.listener =
            makeListener(onStatus = { registered ->
                unregisteredValue = registered
                unregisterLatch.countDown()
            }, onError = { unregisterLatch.countDown() })

        hid.unregisterApp()

        assertTrue("unregister callback not received within 5 s", unregisterLatch.await(5, TimeUnit.SECONDS))
        assertTrue("expected registered=false after unregisterApp()", unregisteredValue == false)

        adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
    }

    @Test
    fun reregisterApp_switchDescriptorVariant_stackAcceptsBoth() {
        assumeApi28()
        val adapter = requireEnabledAdapter() ?: return
        val hid = obtainHidProxy(adapter) ?: return
        val module = BluetoothHidModule()

        try {
            var registered = false
            val latch1 = CountDownLatch(1)
            module.listener =
                makeListener(onStatus = { r ->
                    registered = r
                    latch1.countDown()
                })
            module.registerApp(hid, simplified = true)
            assertTrue("SIMPLE register callback not received", latch1.await(8, TimeUnit.SECONDS))
            assertTrue("SIMPLE registration rejected by stack", registered)

            val unregLatch = CountDownLatch(1)
            module.listener =
                makeListener(
                    onStatus = { unregLatch.countDown() },
                    onError = { unregLatch.countDown() },
                )
            hid.unregisterApp()
            unregLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(1000)

            registered = false
            val latch2 = CountDownLatch(1)
            module.listener =
                makeListener(onStatus = { r ->
                    registered = r
                    latch2.countDown()
                })
            module.registerApp(hid, simplified = false)
            assertTrue("FULL register callback not received", latch2.await(8, TimeUnit.SECONDS))
            assertTrue("FULL registration rejected by stack", registered)
        } finally {
            runCatching { hid.unregisterApp() }
            Thread.sleep(1500)
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
    }

    private fun assumeApi28() =
        assumeTrue("API 28+ required for BluetoothProfile.HID_DEVICE", Build.VERSION.SDK_INT >= 28)

    private fun requireEnabledAdapter(): BluetoothAdapter? {
        val adapter = btAdapter
        assumeTrue("No Bluetooth adapter on this device", adapter != null)
        assumeTrue("Bluetooth is not enabled", adapter!!.isEnabled)
        return adapter
    }

    private fun obtainHidProxy(adapter: BluetoothAdapter): BluetoothHidDevice? {
        val latch = CountDownLatch(1)
        var result: BluetoothProfile? = null
        adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile,
                ) {
                    result = proxy
                    latch.countDown()
                }

                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.HID_DEVICE,
        )
        assertTrue("HID_DEVICE proxy not returned within 5 s", latch.await(5, TimeUnit.SECONDS))
        return result as? BluetoothHidDevice
    }

    private fun assertRegisters(simplified: Boolean) {
        assumeApi28()
        val adapter = requireEnabledAdapter() ?: return
        val hid = obtainHidProxy(adapter) ?: return
        val module = BluetoothHidModule()

        val preLatch = CountDownLatch(1)
        module.listener =
            makeListener(
                onStatus = { preLatch.countDown() },
                onError = { preLatch.countDown() },
            )
        runCatching { hid.unregisterApp() }
        preLatch.await(1, TimeUnit.SECONDS)
        Thread.sleep(500)

        val latch = CountDownLatch(1)
        var registered = false
        module.listener =
            makeListener(
                onStatus = { r ->
                    registered = r
                    latch.countDown()
                },
                onError = { latch.countDown() },
            )

        module.registerApp(hid, simplified)

        try {
            assertTrue(
                "onAppStatusChanged not received within 8 s (simplified=$simplified)",
                latch.await(8, TimeUnit.SECONDS),
            )
            assertTrue(
                "Android BT stack rejected descriptor (simplified=$simplified)",
                registered,
            )
        } finally {
            val cleanupLatch = CountDownLatch(1)
            module.listener =
                makeListener(
                    onStatus = { cleanupLatch.countDown() },
                    onError = { cleanupLatch.countDown() },
                )
            runCatching { hid.unregisterApp() }
            cleanupLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(1500)
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
    }

    private fun makeListener(
        onStatus: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
    ) = object : BluetoothHidModule.HidEventListener {
        override fun onAppStatus(registered: Boolean) = onStatus(registered)

        override fun onConnectionStateChanged(
            device: BluetoothDevice,
            state: Int,
        ) {}

        override fun onError(message: String) = onError(message)
    }
}
