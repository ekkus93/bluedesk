package com.augustusmachin.android_bt_kbmouse

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class Settings(
    val touchpadSensitivity: Float = 1.5f, // tuned default
    val scrollSpeed: Float = 1.0f,        // tuned default (per step multiplier)
    val invertScroll: Boolean = false,
    val enableHorizontalScroll: Boolean = true,
    val invertHorizontalScroll: Boolean = false,
    val enableMiddleClick: Boolean = true,
    val keyRepeatDelayMs: Int = 350,
    val clickSound: Boolean = true,
    val debugLogging: Boolean = false,
    val logLevel: Int = 0, // 0=All,1=Info,2=Error
    val startOnBoot: Boolean = false,
    // Per-device: key remapping table (HID usage -> HID usage)
    val keyMap: Map<Int, Int> = emptyMap(),
)

object SettingsManager {
    private val KEY_SENS = floatPreferencesKey("touchpad_sensitivity")
    private val KEY_SCROLL = floatPreferencesKey("scroll_speed")
    private val KEY_INVERT = booleanPreferencesKey("invert_scroll")
    private val KEY_HSCROLL = booleanPreferencesKey("enable_hscroll")
    private val KEY_HSCROLL_INV = booleanPreferencesKey("invert_hscroll")
    private val KEY_MIDDLE = booleanPreferencesKey("enable_middle_click")
    private val KEY_REPEAT = intPreferencesKey("key_repeat_delay_ms")
    private val KEY_CLICK = booleanPreferencesKey("click_sound")
    private val KEY_DEBUG = booleanPreferencesKey("debug_logging")
    private val KEY_LOGLEVEL = intPreferencesKey("log_level")
    private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")

    fun fromPreferences(p: androidx.datastore.preferences.core.Preferences): Settings = Settings(
        touchpadSensitivity = p[KEY_SENS] ?: 1.5f,
        scrollSpeed = p[KEY_SCROLL] ?: 1.0f,
        invertScroll = p[KEY_INVERT] ?: false,
        enableHorizontalScroll = p[KEY_HSCROLL] ?: true,
        invertHorizontalScroll = p[KEY_HSCROLL_INV] ?: false,
        enableMiddleClick = p[KEY_MIDDLE] ?: true,
        keyRepeatDelayMs = p[KEY_REPEAT] ?: 350,
        clickSound = p[KEY_CLICK] ?: true,
        debugLogging = p[KEY_DEBUG] ?: false,
        logLevel = p[KEY_LOGLEVEL] ?: 0,
        startOnBoot = p[KEY_START_ON_BOOT] ?: false,
        keyMap = emptyMap(),
    )

    fun flow(context: Context): Flow<Settings> = context.settingsDataStore.data.map { p ->
        fromPreferences(p)
    }

    // Per-device overlay: returns global settings merged with per-device overrides
    fun fromPreferencesForDevice(p: androidx.datastore.preferences.core.Preferences, deviceAddress: String?): Settings {
        val base = fromPreferences(p)
        if (deviceAddress.isNullOrEmpty()) return base
        val pref = { key: String -> "device_${deviceAddress}_" + key }
        val sens = p[floatPreferencesKey(pref("touchpad_sensitivity"))] ?: base.touchpadSensitivity
        val scroll = p[floatPreferencesKey(pref("scroll_speed"))] ?: base.scrollSpeed
        val inv = p[booleanPreferencesKey(pref("invert_scroll"))] ?: base.invertScroll
        val hscroll = p[booleanPreferencesKey(pref("enable_hscroll"))] ?: base.enableHorizontalScroll
        val hscrollInv = p[booleanPreferencesKey(pref("invert_hscroll"))] ?: base.invertHorizontalScroll
        val middle = p[booleanPreferencesKey(pref("enable_middle_click"))] ?: base.enableMiddleClick
        val repeat = p[intPreferencesKey(pref("key_repeat_delay_ms"))] ?: base.keyRepeatDelayMs
        val click = p[booleanPreferencesKey(pref("click_sound"))] ?: base.clickSound
        val mapStr = p[androidx.datastore.preferences.core.stringPreferencesKey(pref("keymap"))] ?: ""
        return base.copy(
            touchpadSensitivity = sens,
            scrollSpeed = scroll,
            invertScroll = inv,
            enableHorizontalScroll = hscroll,
            invertHorizontalScroll = hscrollInv,
            enableMiddleClick = middle,
            keyRepeatDelayMs = repeat,
            clickSound = click,
            keyMap = parseKeyMap(mapStr)
        )
    }

    fun flowForDevice(context: Context, deviceAddress: String?): Flow<Settings> = context.settingsDataStore.data.map { p ->
        fromPreferencesForDevice(p, deviceAddress)
    }

    private fun parseKeyMap(s: String): Map<Int, Int> {
        if (s.isBlank()) return emptyMap()
        return s.split(',').mapNotNull { part ->
            val kv = part.split('=')
            if (kv.size == 2) {
                val k = kv[0].trim().toIntOrNull()
                val v = kv[1].trim().toIntOrNull()
                if (k != null && v != null) k to v else null
            } else null
        }.toMap()
    }
    private fun formatKeyMap(m: Map<Int, Int>): String = m.entries.joinToString(",") { it.key.toString() + "=" + it.value.toString() }

    suspend fun setTouchpadSensitivity(context: Context, v: Float) {
        context.settingsDataStore.edit { it[KEY_SENS] = v }
    }
    suspend fun setScrollSpeed(context: Context, v: Float) {
        context.settingsDataStore.edit { it[KEY_SCROLL] = v }
    }
    suspend fun setInvertScroll(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_INVERT] = v }
    }
    suspend fun setKeyRepeatDelay(context: Context, v: Int) {
        context.settingsDataStore.edit { it[KEY_REPEAT] = v }
    }
    suspend fun setClickSound(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_CLICK] = v }
    }
    suspend fun setEnableHorizontalScroll(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_HSCROLL] = v }
    }
    suspend fun setInvertHorizontalScroll(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_HSCROLL_INV] = v }
    }
    suspend fun setEnableMiddleClick(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_MIDDLE] = v }
    }
    suspend fun setDebugLogging(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_DEBUG] = v }
    }
    suspend fun setLogLevel(context: Context, v: Int) {
        context.settingsDataStore.edit { it[KEY_LOGLEVEL] = v.coerceIn(0, 2) }
    }
    suspend fun setStartOnBoot(context: Context, v: Boolean) {
        context.settingsDataStore.edit { it[KEY_START_ON_BOOT] = v }
    }

    // Per-device setters
    private fun pk(addr: String, name: String) = "device_${addr}_$name"
    suspend fun setDeviceTouchpadSensitivity(context: Context, addr: String, v: Float) {
        context.settingsDataStore.edit { it[floatPreferencesKey(pk(addr, "touchpad_sensitivity"))] = v }
    }
    suspend fun setDeviceScrollSpeed(context: Context, addr: String, v: Float) {
        context.settingsDataStore.edit { it[floatPreferencesKey(pk(addr, "scroll_speed"))] = v }
    }
    suspend fun setDeviceInvertScroll(context: Context, addr: String, v: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(pk(addr, "invert_scroll"))] = v }
    }
    suspend fun setDeviceEnableHorizontalScroll(context: Context, addr: String, v: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(pk(addr, "enable_hscroll"))] = v }
    }
    suspend fun setDeviceInvertHorizontalScroll(context: Context, addr: String, v: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(pk(addr, "invert_hscroll"))] = v }
    }
    suspend fun setDeviceEnableMiddleClick(context: Context, addr: String, v: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(pk(addr, "enable_middle_click"))] = v }
    }
    suspend fun setDeviceKeyRepeatDelay(context: Context, addr: String, v: Int) {
        context.settingsDataStore.edit { it[intPreferencesKey(pk(addr, "key_repeat_delay_ms"))] = v }
    }
    suspend fun setDeviceClickSound(context: Context, addr: String, v: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(pk(addr, "click_sound"))] = v }
    }
    suspend fun setDeviceKeyMap(context: Context, addr: String, map: Map<Int, Int>) {
        context.settingsDataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey(pk(addr, "keymap"))] = formatKeyMap(map) }
    }
    suspend fun addDeviceKeyRemap(context: Context, addr: String, fromUsage: Int, toUsage: Int) {
        context.settingsDataStore.edit { prefs ->
            val key = androidx.datastore.preferences.core.stringPreferencesKey(pk(addr, "keymap"))
            val cur = parseKeyMap(prefs[key] ?: "").toMutableMap()
            cur[fromUsage] = toUsage
            prefs[key] = formatKeyMap(cur)
        }
    }
    suspend fun removeDeviceKeyRemap(context: Context, addr: String, fromUsage: Int) {
        context.settingsDataStore.edit { prefs ->
            val key = androidx.datastore.preferences.core.stringPreferencesKey(pk(addr, "keymap"))
            val cur = parseKeyMap(prefs[key] ?: "").toMutableMap()
            cur.remove(fromUsage)
            prefs[key] = formatKeyMap(cur)
        }
    }
    suspend fun clearDeviceProfile(context: Context, addr: String) {
        context.settingsDataStore.edit { prefs ->
            listOf(
                floatPreferencesKey(pk(addr, "touchpad_sensitivity")),
                floatPreferencesKey(pk(addr, "scroll_speed")),
                booleanPreferencesKey(pk(addr, "invert_scroll")),
                booleanPreferencesKey(pk(addr, "enable_hscroll")),
                booleanPreferencesKey(pk(addr, "invert_hscroll")),
                booleanPreferencesKey(pk(addr, "enable_middle_click")),
                intPreferencesKey(pk(addr, "key_repeat_delay_ms")),
                booleanPreferencesKey(pk(addr, "click_sound")),
                androidx.datastore.preferences.core.stringPreferencesKey(pk(addr, "keymap"))
            ).forEach { prefs.remove(it) }
        }
    }
}

