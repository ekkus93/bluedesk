package com.augustusmachin.android_bt_kbmouse

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DataStore round-trip tests for SettingsManager.
 * Runs on device via USB. Clears the DataStore before and after each test
 * so tests don't interfere with each other or the live app prefs.
 */
@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {

    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun clearDataStore() {
        runBlocking { context.settingsDataStore.edit { it.clear() } }
    }

    @After
    fun clearDataStoreAfter() {
        runBlocking { context.settingsDataStore.edit { it.clear() } }
    }

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    fun defaults_touchpadSensitivity_is_1_5() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertEquals(1.5f, settings.touchpadSensitivity)
    }

    @Test
    fun defaults_hidSimplified_isTrue() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.hidSimplified)
    }

    @Test
    fun defaults_useBleHogp_isFalse() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.useBleHogp)
    }

    @Test
    fun defaults_invertScroll_isFalse() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.invertScroll)
    }

    @Test
    fun defaults_enableHorizontalScroll_isTrue() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.enableHorizontalScroll)
    }

    @Test
    fun defaults_keyRepeatDelayMs_is350() = runBlocking {
        val settings = SettingsManager.flow(context).first()
        assertEquals(350, settings.keyRepeatDelayMs)
    }

    // ── Round-trip writes ─────────────────────────────────────────────────────

    @Test
    fun setUseBleHogp_true_roundTrip() = runBlocking {
        SettingsManager.setUseBleHogp(context, true)
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.useBleHogp)
    }

    @Test
    fun setUseBleHogp_false_roundTrip() = runBlocking {
        SettingsManager.setUseBleHogp(context, true)
        SettingsManager.setUseBleHogp(context, false)
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.useBleHogp)
    }

    @Test
    fun setHidSimplified_false_roundTrip() = runBlocking {
        SettingsManager.setHidSimplified(context, false)
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.hidSimplified)
    }

    @Test
    fun setTouchpadSensitivity_roundTrip() = runBlocking {
        SettingsManager.setTouchpadSensitivity(context, 2.5f)
        val settings = SettingsManager.flow(context).first()
        assertEquals(2.5f, settings.touchpadSensitivity)
    }

    @Test
    fun setScrollSpeed_roundTrip() = runBlocking {
        SettingsManager.setScrollSpeed(context, 3.0f)
        val settings = SettingsManager.flow(context).first()
        assertEquals(3.0f, settings.scrollSpeed)
    }

    @Test
    fun setInvertScroll_true_roundTrip() = runBlocking {
        SettingsManager.setInvertScroll(context, true)
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.invertScroll)
    }

    @Test
    fun setKeyRepeatDelay_roundTrip() = runBlocking {
        SettingsManager.setKeyRepeatDelay(context, 500)
        val settings = SettingsManager.flow(context).first()
        assertEquals(500, settings.keyRepeatDelayMs)
    }

    @Test
    fun setEnableHorizontalScroll_false_roundTrip() = runBlocking {
        SettingsManager.setEnableHorizontalScroll(context, false)
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.enableHorizontalScroll)
    }

    @Test
    fun setDebugLogging_true_roundTrip() = runBlocking {
        SettingsManager.setDebugLogging(context, true)
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.debugLogging)
    }

    @Test
    fun setLogLevel_roundTrip() = runBlocking {
        SettingsManager.setLogLevel(context, 2)
        val settings = SettingsManager.flow(context).first()
        assertEquals(2, settings.logLevel)
    }

    @Test
    fun setLogLevel_clampedToMax2() = runBlocking {
        SettingsManager.setLogLevel(context, 99)
        val settings = SettingsManager.flow(context).first()
        assertEquals(2, settings.logLevel)
    }

    @Test
    fun setLogLevel_clampedToMin0() = runBlocking {
        SettingsManager.setLogLevel(context, -5)
        val settings = SettingsManager.flow(context).first()
        assertEquals(0, settings.logLevel)
    }

    @Test
    fun setOfflinePreview_true_roundTrip() = runBlocking {
        SettingsManager.setOfflinePreview(context, true)
        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.offlinePreview)
    }

    @Test
    fun setClickSound_false_roundTrip() = runBlocking {
        SettingsManager.setClickSound(context, false)
        val settings = SettingsManager.flow(context).first()
        assertFalse(settings.clickSound)
    }

    // ── Multiple writes preserve unrelated settings ───────────────────────────

    @Test
    fun multipleWrites_independentSettings_allPersist() = runBlocking {
        SettingsManager.setUseBleHogp(context, true)
        SettingsManager.setHidSimplified(context, false)
        SettingsManager.setTouchpadSensitivity(context, 3.0f)
        SettingsManager.setInvertScroll(context, true)

        val settings = SettingsManager.flow(context).first()
        assertTrue(settings.useBleHogp)
        assertFalse(settings.hidSimplified)
        assertEquals(3.0f, settings.touchpadSensitivity)
        assertTrue(settings.invertScroll)
    }

    // ── IME override round-trip ───────────────────────────────────────────────

    @Test
    fun imeOverride_set_and_get_roundTrip() = runBlocking {
        val pkg = "com.example.ime"
        SettingsManager.setImeOverride(context, pkg, true)
        val result = SettingsManager.getImeOverride(context, pkg)
        assertEquals(true, result)
    }

    @Test
    fun imeOverride_denied_roundTrip() = runBlocking {
        val pkg = "com.example.ime2"
        SettingsManager.setImeOverride(context, pkg, false)
        val result = SettingsManager.getImeOverride(context, pkg)
        assertEquals(false, result)
    }

    @Test
    fun imeOverride_unset_returnsNull() = runBlocking {
        val result = SettingsManager.getImeOverride(context, "com.not.set")
        assertNull(result)
    }

    @Test
    fun imeOverride_remove_returnsNull() = runBlocking {
        val pkg = "com.example.ime3"
        SettingsManager.setImeOverride(context, pkg, true)
        SettingsManager.removeImeOverride(context, pkg)
        val result = SettingsManager.getImeOverride(context, pkg)
        assertNull(result)
    }

    @Test
    fun getAllImeOverrides_returnsAllSet() = runBlocking {
        SettingsManager.setImeOverride(context, "pkg.a", true)
        SettingsManager.setImeOverride(context, "pkg.b", false)
        val all = SettingsManager.getAllImeOverrides(context)
        assertEquals(true, all["pkg.a"])
        assertEquals(false, all["pkg.b"])
    }

    // ── Per-device profile round-trip ─────────────────────────────────────────

    @Test
    fun deviceProfile_sensitivity_roundTrip() = runBlocking {
        val addr = "AA:BB:CC:DD:EE:FF"
        SettingsManager.setDeviceTouchpadSensitivity(context, addr, 4.0f)
        val settings = SettingsManager.flowForDevice(context, addr).first()
        assertEquals(4.0f, settings.touchpadSensitivity)
    }

    @Test
    fun deviceProfile_clearProfile_revertsToGlobal() = runBlocking {
        val addr = "AA:BB:CC:DD:EE:FF"
        SettingsManager.setTouchpadSensitivity(context, 2.0f)
        SettingsManager.setDeviceTouchpadSensitivity(context, addr, 5.0f)
        SettingsManager.clearDeviceProfile(context, addr)
        val settings = SettingsManager.flowForDevice(context, addr).first()
        assertEquals(2.0f, settings.touchpadSensitivity)
    }

    @Test
    fun deviceProfile_keyRemap_addAndRemove() = runBlocking {
        val addr = "11:22:33:44:55:66"
        SettingsManager.addDeviceKeyRemap(context, addr, fromUsage = 0x04, toUsage = 0x05)
        var settings = SettingsManager.flowForDevice(context, addr).first()
        assertEquals(0x05, settings.keyMap[0x04])

        SettingsManager.removeDeviceKeyRemap(context, addr, fromUsage = 0x04)
        settings = SettingsManager.flowForDevice(context, addr).first()
        assertNull(settings.keyMap[0x04])
    }
}
