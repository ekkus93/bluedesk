package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class BleHogpLogicTest {

    @Test
    fun reportMap_selection_and_flags() {
        val (lenSimple, infoSimple) = BleHogpLogic.selectReportMap(true)
        val (lenFull, infoFull) = BleHogpLogic.selectReportMap(false)
        assertTrue(lenFull > lenSimple)
        assertTrue(infoSimple.contains("variant=simple"))
        assertTrue(infoFull.contains("variant=full"))
    }

    @Test
    fun cccd_notify_flag_decisions() {
        // Enabled (0x01 0x00 little endian) -> flag = 0x0001
        assertTrue(BleHogpLogic.cccdEnabled(byteArrayOf(0x01, 0x00)))
        // Disabled (0x00 0x00)
        assertFalse(BleHogpLogic.cccdEnabled(byteArrayOf(0x00, 0x00)))
        // Short array
        assertFalse(BleHogpLogic.cccdEnabled(byteArrayOf(0x01)))
        // Different flag (0x02 0x00 = indications)
        assertFalse(BleHogpLogic.cccdEnabled(byteArrayOf(0x02, 0x00)))
    }
}
