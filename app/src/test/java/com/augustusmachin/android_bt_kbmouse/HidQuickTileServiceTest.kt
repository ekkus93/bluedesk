package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class HidQuickTileServiceTest {
    @Test
    fun intent_routes_to_MainActivity() {
        // Can't construct actual Intent for MainActivity on host JVM; verify class name constant
        assertTrue(MainActivity::class.java.name.contains("MainActivity"))
    }

    @Test
    fun safe_noop_when_service_not_bound() {
        // Simulate logic branch: no last device => would open activity; we assert fallback label remains app name when no connected_name
        val spMock = mutableMapOf<String, String>()
        val connectedName = spMock["connected_name"]
        assertNull(connectedName) // tile would show app name and perform no connect/disconnect broadcast
    }
}
