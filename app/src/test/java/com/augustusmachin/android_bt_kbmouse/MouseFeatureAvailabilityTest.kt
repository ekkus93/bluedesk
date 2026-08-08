package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseFeatureAvailabilityTest {
    @Test
    fun `classic full descriptor exposes configured scrolling`() {
        val settings = Settings(hidSimplified = false, enableMiddleClick = true)

        val features = mouseFeatureAvailability(settings, BackendCapabilitySets.classic)

        assertTrue(features.middleClick)
        assertTrue(features.verticalScroll)
        assertTrue(features.horizontalScroll)
    }

    @Test
    fun `BLE never exposes unsupported scrolling`() {
        val settings = Settings(hidSimplified = false, enableMiddleClick = true)

        val features = mouseFeatureAvailability(settings, BackendCapabilitySets.bleHogp)

        assertTrue(features.middleClick)
        assertFalse(features.verticalScroll)
        assertFalse(features.horizontalScroll)
    }

    @Test
    fun `simplified descriptor disables scrolling even on classic`() {
        val settings = Settings(hidSimplified = true, enableMiddleClick = true)

        val features = mouseFeatureAvailability(settings, BackendCapabilitySets.classic)

        assertFalse(features.verticalScroll)
        assertFalse(features.horizontalScroll)
    }
}
