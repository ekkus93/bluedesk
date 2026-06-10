package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTilePolicyTest {
    @Test
    fun classicMode_connected_disconnects() {
        assertEquals(
            TileAction.DISCONNECT,
            QuickTilePolicy.action(bleMode = false, connectedName = "Host", hasLastDevice = true),
        )
    }

    @Test
    fun classicMode_notConnectedWithLast_connects() {
        assertEquals(
            TileAction.CONNECT,
            QuickTilePolicy.action(bleMode = false, connectedName = null, hasLastDevice = true),
        )
    }

    @Test
    fun classicMode_noLastDevice_opensApp() {
        assertEquals(
            TileAction.OPEN_APP,
            QuickTilePolicy.action(bleMode = false, connectedName = null, hasLastDevice = false),
        )
    }

    @Test
    fun bleMode_isAlwaysUnavailable() {
        // In BLE mode the tile must not emit any Classic action, regardless of other state.
        assertEquals(TileAction.UNAVAILABLE, QuickTilePolicy.action(true, "Host", true))
        assertEquals(TileAction.UNAVAILABLE, QuickTilePolicy.action(true, null, true))
        assertEquals(TileAction.UNAVAILABLE, QuickTilePolicy.action(true, null, false))
    }

    @Test
    fun isActive_reflectsKnownConnectionNotOptimism() {
        assertTrue(QuickTilePolicy.isActive(bleMode = false, connectedName = "Host"))
        assertFalse(QuickTilePolicy.isActive(bleMode = false, connectedName = null))
        // BLE mode is never shown active by the (Classic) tile.
        assertFalse(QuickTilePolicy.isActive(bleMode = true, connectedName = "Host"))
    }
}
