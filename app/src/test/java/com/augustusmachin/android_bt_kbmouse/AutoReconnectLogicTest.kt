package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoReconnectLogicTest {
    @Test
    fun persists_lastDevice_and_reads_on_start() {
        val store = mutableMapOf<String, String>()
        assertNull(ReconnectLogic.readLastDevice(store))
        ReconnectLogic.saveLastDevice(store, "AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", ReconnectLogic.readLastDevice(store))
    }

    @Test
    fun schedules_retry_with_exponential_backoff() {
        var attempt = 0
        val base = 2000L
        val delays = mutableListOf<Long>()
        repeat(5) {
            val d =
                ReconnectLogic.nextDelay(
                    manualDisconnect = false,
                    success = false,
                    btEnabled = true,
                    base = base,
                    currentAttempt = attempt,
                )
            assertNotNull(d)
            delays += d!!
            attempt++
        }
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 30000L), delays)
    }

    @Test
    fun stops_retry_on_success_or_manual_disconnect() {
        // Manual disconnect stops
        val d1 =
            ReconnectLogic.nextDelay(
                manualDisconnect = true,
                success = false,
                btEnabled = true,
                base = 2000L,
                currentAttempt = 0,
            )
        assertNull(d1)
        // Success stops
        val d2 =
            ReconnectLogic.nextDelay(
                manualDisconnect = false,
                success = true,
                btEnabled = true,
                base = 2000L,
                currentAttempt = 2,
            )
        assertNull(d2)
        // BT off pauses
        val d3 =
            ReconnectLogic.nextDelay(
                manualDisconnect = false,
                success = false,
                btEnabled = false,
                base = 2000L,
                currentAttempt = 2,
            )
        assertNull(d3)
    }
}
