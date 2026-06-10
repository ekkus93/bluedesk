package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class ForegroundServiceLogicTest {
    @Test
    fun startForeground_guard_within_5s_logic() {
        val start = 1000L
        assertFalse(ForegroundServiceLogic.foregroundStartExceeded(start, 5000L)) // 4s elapsed
        assertTrue(ForegroundServiceLogic.foregroundStartExceeded(start, 7001L)) // >6s elapsed
    }

    @Test
    fun startSticky_intent_handling() {
        assertEquals(android.app.Service.START_STICKY, ForegroundServiceLogic.startMode())
    }

    @Test
    fun notification_text_builder_content() {
        assertEquals("Tap to manage connection", ForegroundServiceLogic.buildNotificationText(null))
        assertEquals("Tap to manage connection", ForegroundServiceLogic.buildNotificationText("DeviceName"))
    }
}
