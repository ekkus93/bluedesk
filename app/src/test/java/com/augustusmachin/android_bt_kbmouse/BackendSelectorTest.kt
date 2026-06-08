package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendSelectorTest {
    @Test
    fun classicHidWhenBleDisabled() {
        assertEquals(BackendMode.CLASSIC_HID, BackendSelector.fromSettings(useBleHogp = false))
    }

    @Test
    fun bleHogpWhenBleEnabled() {
        assertEquals(BackendMode.BLE_HOGP, BackendSelector.fromSettings(useBleHogp = true))
    }
}
