package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Layer 3A instrumented tests: send real HID reports to a paired host and assert
 * that BluetoothHidDevice.sendReport() returns true (stack accepted delivery).
 *
 * ⚠️ Physical hardware test — run as single class with opt-in arguments:
 *
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest \
 *     -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
 *     -Pandroid.testInstrumentationRunnerArguments.hidHostAddress=<LAPTOP_BT_ADDRESS>
 *
 * Then, during the test wait window (after HID registration logs), from the laptop run:
 *
 *   bluetoothctl connect <PHONE_BT_ADDRESS>
 *
 * Prerequisites (one-time manual setup):
 *  1. Enable Bluetooth on the Android device and laptop.
 *  2. Pair the Android phone with the laptop (Bluetooth settings).
 *  3. Kill the main app (swipe away from recents) so BluetoothService is not
 *     running — Android allows only one HID app registration at a time.
 *
 * If any prerequisite is not met, all tests in this class are skipped (not failed).
 * Skip reasons explain which requirement is missing.
 *
 * What sendReport(true) guarantees: the Android BT stack successfully enqueued
 * the HID report for delivery over the air. The host will receive and process it
 * within ~10 ms. (Full verification that the host processed the keystroke is
 * Layer 3C, which requires a host-side listener script.)
 */
@SuppressLint("NewApi", "MissingPermission")
@RunWith(AndroidJUnit4::class)
class BluetoothHidSendReportTest {

    companion object {
        private var hidProxy: BluetoothHidDevice? = null
        private var connectedDevice: BluetoothDevice? = null
        private var btAdapter: BluetoothAdapter? = null
        private var expectedHostAddress: String? = null
        private var expectedPhoneAddress: String? = null

        // Host connection wait timeout (seconds)
        private const val HOST_CONNECT_TIMEOUT_SECONDS = 90L

        // isReady=true only after @BeforeClass fully succeeds.
        @Volatile private var isReady = false
        @Volatile private var notReadyReason = "Setup not yet attempted"

        @BeforeClass @JvmStatic
        fun setupClass() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext

            // Check opt-in flag for physical hardware test
            val runPhysical = InstrumentationRegistry.getArguments()
                .getString("runPhysicalHidTests") == "true"
            if (!runPhysical) {
                notReadyReason = "Physical HID tests require runPhysicalHidTests=true and host-side bluetoothctl connect"
                return
            }

            // Require expected host address
            expectedHostAddress = InstrumentationRegistry.getArguments()
                .getString("hidHostAddress")
                ?.uppercase(Locale.US)
            if (expectedHostAddress.isNullOrBlank()) {
                notReadyReason = "Physical HID test requires hidHostAddress=<laptop/controller Bluetooth MAC>. Find it with bluetoothctl show."
                return
            }

            // Require expected phone address
            expectedPhoneAddress = InstrumentationRegistry.getArguments()
                .getString("hidPhoneAddress")
                ?.uppercase(Locale.US)
            if (expectedPhoneAddress.isNullOrBlank()) {
                notReadyReason = "Physical HID test requires hidPhoneAddress=<Android phone Bluetooth MAC>. Find it from the laptop with bluetoothctl devices."
                return
            }

            // Grant runtime permissions (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val auto = instrumentation.uiAutomation
                val pkg = context.packageName
                auto.executeShellCommand("pm grant $pkg ${Manifest.permission.BLUETOOTH_CONNECT}").close()
                auto.executeShellCommand("pm grant $pkg ${Manifest.permission.BLUETOOTH_SCAN}").close()
            }

            if (Build.VERSION.SDK_INT < 28) {
                notReadyReason = "API 28+ required for BluetoothProfile.HID_DEVICE"
                return
            }

            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = mgr?.adapter
            if (adapter == null) { notReadyReason = "No Bluetooth adapter on this device"; return }
            if (!adapter.isEnabled) { notReadyReason = "Bluetooth is not enabled"; return }
            btAdapter = adapter

            // ── Pre-cleanup: unregister any stale HID app from a previous test run ──
            val cleanupLatch = CountDownLatch(1)
            var cleanupProxy: BluetoothHidDevice? = null
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                    cleanupProxy = p as BluetoothHidDevice
                    cleanupLatch.countDown()
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HID_DEVICE)
            if (cleanupLatch.await(3, TimeUnit.SECONDS) && cleanupProxy != null) {
                runCatching { cleanupProxy!!.unregisterApp() }
                Thread.sleep(1500)
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, cleanupProxy!!)
            }
            Thread.sleep(500)

            // ── Step 1: obtain the HID device profile proxy ──────────────────
            val proxyLatch = CountDownLatch(1)
            var proxy: BluetoothHidDevice? = null
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                    proxy = p as BluetoothHidDevice
                    proxyLatch.countDown()
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HID_DEVICE)

            if (!proxyLatch.await(5, TimeUnit.SECONDS) || proxy == null) {
                notReadyReason = "HID_DEVICE profile proxy not obtained within 5 s"
                return
            }
            hidProxy = proxy

            // ── Step 2: register the HID app ─────────────────────────────────
            val hid = proxy!!
            val connectionLatch = CountDownLatch(1)
            val regLatch = CountDownLatch(1)
            var registered = false

            val module = BluetoothHidModule(adapter)
            module.listener = object : BluetoothHidModule.HidEventListenerExt {
                override fun onAppStatus(r: Boolean) {
                    registered = r
                    regLatch.countDown()
                }
                override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        val address = device.address.uppercase(Locale.US)
                        if (address == expectedHostAddress) {
                            connectedDevice = device
                            connectionLatch.countDown()
                        } else {
                            DebugLog.log("BluetoothHidSendReportTest", "Ignoring HID connection from unexpected host $address")
                        }
                    }
                }
                override fun onLeds(leds: Int) {}
                override fun onError(msg: String) { regLatch.countDown() }
            }
            module.registerApp(hid, simplified = true)

            if (!regLatch.await(5, TimeUnit.SECONDS) || !registered) {
                notReadyReason = "HID app registration failed — is BluetoothService still running?"
                return
            }

            // ── Step 3: select target host by hidHostAddress ───────────────────
            // Do not call hid.connect(target). On the Linux/BlueZ host setup used for physical testing,
            // the host must initiate the HID L2CAP connection after Android registers the HID app.
            val bonded = adapter.bondedDevices?.toList() ?: emptyList()
            if (bonded.isEmpty()) {
                notReadyReason = "No paired devices found — pair the phone with the laptop first"
                return
            }
            val target = bonded.firstOrNull { device ->
                device.address.uppercase(Locale.US) == expectedHostAddress
            }
            if (target == null) {
                notReadyReason = "Expected HID host $expectedHostAddress is not bonded. Pair the phone and laptop first"
                return
            }

            // Log instructions for host-initiated connection
            DebugLog.log("BluetoothHidSendReportTest", "HID profile registered.\n\nExpected host/laptop address:\n$expectedHostAddress\n\nFrom the laptop, connect to this Android phone:\nbluetoothctl connect $expectedPhoneAddress")

            if (!connectionLatch.await(HOST_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                notReadyReason = "Host did not initiate HID connection within $HOST_CONNECT_TIMEOUT_SECONDS seconds. After Android logs HID registration, run bluetoothctl connect $expectedPhoneAddress from the laptop."
                return
            }

            isReady = true
        }

        @AfterClass @JvmStatic
        fun teardownClass() {
            val hid = hidProxy ?: return
            val device = connectedDevice
            if (device != null) {
                // Release all keys and buttons before disconnecting
                runCatching {
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), ByteArray(8))
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), ByteArray(3))
                }
                Thread.sleep(100)
            }
            runCatching { hid.unregisterApp() }
            Thread.sleep(300)
            btAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
    }

    // ── Per-test guard ────────────────────────────────────────────────────────

    @Before
    fun requireConnected() = assumeTrue(notReadyReason, isReady)

    @After
    fun releaseAllKeys() {
        // Always send a clean "no keys / no buttons" report between tests
        val device = connectedDevice ?: return
        val hid = hidProxy ?: return
        runCatching {
            hid.sendReport(device, HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), ByteArray(8))
            hid.sendReport(device, HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), ByteArray(3))
        }
        Thread.sleep(50)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sendKey(reportId: Byte, data: ByteArray): Boolean =
        hidProxy!!.sendReport(connectedDevice!!, reportId.toInt(), data)

    private fun key(report: ByteArray) = sendKey(HidDescriptorVariants.REPORT_ID_KEYBOARD, report)
    private fun mouse(report: ByteArray) = sendKey(HidDescriptorVariants.REPORT_ID_MOUSE, report)

    // ── Keyboard tests ────────────────────────────────────────────────────────

    @Test
    fun keyboard_pressAndRelease_A() {
        // HID usage 0x04 = 'a'
        assertTrue("key-down A", key(HidReportBuilder.keyboardReport(0, listOf(0x04))))
        Thread.sleep(50)
        assertTrue("key-up A", key(HidReportBuilder.keyboardReport(0, emptyList())))
    }

    @Test
    fun keyboard_pressAndRelease_CtrlC() {
        // Left Ctrl modifier (0x01) + 'c' (0x06)
        assertTrue("Ctrl+C down", key(HidReportBuilder.keyboardReport(0x01, listOf(0x06))))
        Thread.sleep(50)
        assertTrue("Ctrl+C up", key(HidReportBuilder.keyboardReport(0, emptyList())))
    }

    @Test
    fun keyboard_sixKeyRollover_allSixKeysAccepted() {
        // a b c d e f (0x04–0x09) held simultaneously
        val keys = listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        assertTrue("6KRO down", key(HidReportBuilder.keyboardReport(0, keys)))
        Thread.sleep(50)
        assertTrue("6KRO up", key(HidReportBuilder.keyboardReport(0, emptyList())))
    }

    @Test
    fun keyboard_allModifiers_accepted() {
        // All left+right modifiers simultaneously (0xFF)
        assertTrue("all mods down", key(HidReportBuilder.keyboardReport(0xFF, emptyList())))
        Thread.sleep(50)
        assertTrue("all mods up", key(HidReportBuilder.keyboardReport(0, emptyList())))
    }

    @Test
    fun keyboard_shiftLetter_accepted() {
        // Left Shift (0x02) + 'a' (0x04) → 'A'
        assertTrue("Shift+A down", key(HidReportBuilder.keyboardReport(0x02, listOf(0x04))))
        Thread.sleep(50)
        assertTrue("Shift+A up", key(HidReportBuilder.keyboardReport(0, emptyList())))
    }

    // ── Mouse tests ───────────────────────────────────────────────────────────

    @Test
    fun mouse_leftClick_accepted() {
        assertTrue("left btn down", mouse(HidReportBuilder.mouseReportSimple(0x01, 0, 0)))
        Thread.sleep(50)
        assertTrue("left btn up", mouse(HidReportBuilder.mouseReportSimple(0, 0, 0)))
    }

    @Test
    fun mouse_rightClick_accepted() {
        assertTrue("right btn down", mouse(HidReportBuilder.mouseReportSimple(0x02, 0, 0)))
        Thread.sleep(50)
        assertTrue("right btn up", mouse(HidReportBuilder.mouseReportSimple(0, 0, 0)))
    }

    @Test
    fun mouse_middleClick_accepted() {
        assertTrue("middle btn down", mouse(HidReportBuilder.mouseReportSimple(0x04, 0, 0)))
        Thread.sleep(50)
        assertTrue("middle btn up", mouse(HidReportBuilder.mouseReportSimple(0, 0, 0)))
    }

    @Test
    fun mouse_moveRight_accepted() {
        for (i in 1..5) {
            assertTrue("move right step $i", mouse(HidReportBuilder.mouseReportSimple(0, 20, 0)))
            Thread.sleep(16) // ~60 Hz
        }
    }

    @Test
    fun mouse_moveDown_accepted() {
        for (i in 1..5) {
            assertTrue("move down step $i", mouse(HidReportBuilder.mouseReportSimple(0, 0, 20)))
            Thread.sleep(16)
        }
    }

    @Test
    fun mouse_moveAndClick_accepted() {
        // Move diagonally then left-click — a combined gesture
        assertTrue("move diagonal", mouse(HidReportBuilder.mouseReportSimple(0, 15, 15)))
        Thread.sleep(30)
        assertTrue("click down", mouse(HidReportBuilder.mouseReportSimple(0x01, 0, 0)))
        Thread.sleep(50)
        assertTrue("click up", mouse(HidReportBuilder.mouseReportSimple(0, 0, 0)))
    }

    @Test
    fun mouse_maxPositiveMovement_clamped_accepted() {
        // dx=127, dy=127 — boundary values
        assertTrue(mouse(HidReportBuilder.mouseReportSimple(0, 127, 127)))
    }

    @Test
    fun mouse_maxNegativeMovement_clamped_accepted() {
        // dx=-127, dy=-127 — boundary values
        assertTrue(mouse(HidReportBuilder.mouseReportSimple(0, -127, -127)))
    }
}
