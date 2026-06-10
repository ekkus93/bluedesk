package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollPolicyTest {
    @Test
    fun simpleMode_noScrollAtAll() {
        val s = Settings().copy(hidSimplified = true, enableHorizontalScroll = true)
        assertFalse(ScrollPolicy.verticalAvailable(s))
        assertFalse(ScrollPolicy.horizontalAvailable(s))
    }

    @Test
    fun fullMode_verticalAvailable() {
        assertTrue(ScrollPolicy.verticalAvailable(Settings().copy(hidSimplified = false)))
    }

    @Test
    fun fullMode_horizontalDependsOnSetting() {
        assertTrue(
            ScrollPolicy.horizontalAvailable(Settings().copy(hidSimplified = false, enableHorizontalScroll = true)),
        )
        assertFalse(
            ScrollPolicy.horizontalAvailable(Settings().copy(hidSimplified = false, enableHorizontalScroll = false)),
        )
    }
}
