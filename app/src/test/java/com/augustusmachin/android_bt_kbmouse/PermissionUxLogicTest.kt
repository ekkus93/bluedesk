package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class PermissionUxLogicTest {
    @Test
    fun rationale_vs_permanentlyDenied_branches() {
        // All need rationale => show rationale
        val flow1 = PermissionUxLogic.deniedFlow(mapOf("BLUETOOTH_SCAN" to true, "BLUETOOTH_CONNECT" to true))
        assertEquals(PermissionUxLogic.DeniedFlow.ShowRationale, flow1)
        // One permanently denied => show settings
        val flow2 = PermissionUxLogic.deniedFlow(mapOf("BLUETOOTH_SCAN" to true, "BLUETOOTH_CONNECT" to false))
        assertEquals(PermissionUxLogic.DeniedFlow.ShowSettings, flow2)
        // All permanently denied still settings
        val flow3 = PermissionUxLogic.deniedFlow(mapOf("A" to false, "B" to false))
        assertEquals(PermissionUxLogic.DeniedFlow.ShowSettings, flow3)
    }

    @Test
    fun gating_actions_when_permissions_missing() {
        // Missing permissions (not all granted) but not connected => can still initiate scan flow (will request perms)
        assertTrue(PermissionUxLogic.canInitiateScan(allGranted = false, connected = false))
        // Missing permissions and already connected => gating disallows initiating new scan (no need)
        assertFalse(PermissionUxLogic.canInitiateScan(allGranted = false, connected = true))
        // All granted => allowed regardless of connection
        assertTrue(PermissionUxLogic.canInitiateScan(allGranted = true, connected = true))
        assertTrue(PermissionUxLogic.canInitiateScan(allGranted = true, connected = false))
    }
}
