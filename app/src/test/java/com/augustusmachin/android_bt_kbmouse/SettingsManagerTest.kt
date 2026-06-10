package com.augustusmachin.android_bt_kbmouse

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerTest {
    @Test
    fun defaults_loaded_correctly() {
        val defaults = SettingsManager.fromPreferences(emptyPreferences())
        assertEquals(1.5f, defaults.touchpadSensitivity, 0.0001f)
        assertEquals(1.0f, defaults.scrollSpeed, 0.0001f)
        assertFalse(defaults.invertScroll)
        assertTrue(defaults.enableHorizontalScroll)
        assertFalse(defaults.invertHorizontalScroll)
        assertTrue(defaults.enableMiddleClick)
        assertEquals(350, defaults.keyRepeatDelayMs)
        assertTrue(defaults.clickSound)
        assertFalse(defaults.debugLogging)
        assertEquals(0, defaults.logLevel)
        assertFalse(defaults.startOnBoot)
        assertTrue(defaults.keyMap.isEmpty())
    }

    @Test
    fun persist_and_restore_all_settings() {
        val prefs: Preferences =
            preferencesOf(
                floatPreferencesKey("touchpad_sensitivity") to 2.2f,
                floatPreferencesKey("scroll_speed") to 0.75f,
                booleanPreferencesKey("invert_scroll") to true,
                booleanPreferencesKey("enable_hscroll") to false,
                booleanPreferencesKey("invert_hscroll") to true,
                booleanPreferencesKey("enable_middle_click") to false,
                intPreferencesKey("key_repeat_delay_ms") to 500,
                booleanPreferencesKey("click_sound") to false,
                booleanPreferencesKey("debug_logging") to true,
                intPreferencesKey("log_level") to 2,
                booleanPreferencesKey("start_on_boot") to true,
            )
        val restored = SettingsManager.fromPreferences(prefs)
        assertEquals(2.2f, restored.touchpadSensitivity, 0.0001f)
        assertEquals(0.75f, restored.scrollSpeed, 0.0001f)
        assertTrue(restored.invertScroll)
        assertFalse(restored.enableHorizontalScroll)
        assertTrue(restored.invertHorizontalScroll)
        assertFalse(restored.enableMiddleClick)
        assertEquals(500, restored.keyRepeatDelayMs)
        assertFalse(restored.clickSound)
        assertTrue(restored.debugLogging)
        assertEquals(2, restored.logLevel)
        assertTrue(restored.startOnBoot)
    }

    @Test
    fun migration_safe_when_missing_or_old_keys() {
        val prefs: Preferences =
            preferencesOf(
                floatPreferencesKey("touchpad_sensitivity") to 1.8f,
                booleanPreferencesKey("invert_scroll") to true,
            )
        val mapped = SettingsManager.fromPreferences(prefs)
        assertEquals(1.8f, mapped.touchpadSensitivity, 0.0001f)
        assertTrue(mapped.invertScroll)
        assertEquals(1.0f, mapped.scrollSpeed, 0.0001f) // default
        assertTrue(mapped.enableHorizontalScroll) // default
        assertFalse(mapped.invertHorizontalScroll) // default
        assertTrue(mapped.enableMiddleClick) // default
        assertEquals(350, mapped.keyRepeatDelayMs) // default
        assertTrue(mapped.clickSound) // default
        assertFalse(mapped.debugLogging) // default
        assertEquals(0, mapped.logLevel) // default
        assertFalse(mapped.startOnBoot) // default
    }
}
