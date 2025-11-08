package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.*
import org.junit.Test

class DebugLogTest {

    @Test
    fun level_filtering_and_formatting() {
        DebugLog.clear()
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.ALL)
        DebugLog.log("Tag","info message")
        DebugLog.e("Tag","error message")
        val linesAll = DebugLog.lines.value
        assertEquals(2, linesAll.size)
        assertTrue(linesAll[0].contains("[Tag] info message"))
        assertTrue(linesAll[1].contains("E [Tag] error message"))

        DebugLog.clear()
        DebugLog.setLevel(DebugLog.Level.INFO)
        DebugLog.log("Tag","again info")
        DebugLog.e("Tag","should skip error")
        val linesInfo = DebugLog.lines.value
        assertEquals(1, linesInfo.size)
        assertTrue(linesInfo[0].contains("[Tag] again info"))

        DebugLog.clear()
        DebugLog.setLevel(DebugLog.Level.ERROR)
        DebugLog.log("Tag","should skip info")
        DebugLog.e("Tag","only error")
        val linesErr = DebugLog.lines.value
        assertEquals(1, linesErr.size)
        assertTrue(linesErr[0].contains("E [Tag] only error"))
    }

    @Test
    fun bounded_buffer_rotation() {
        DebugLog.clear()
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.ALL)
        // Add more than MAX_LINES (500)
        for (i in 0 until 550) {
            DebugLog.log("T","line $i")
        }
        val lines = DebugLog.lines.value
        assertEquals(500, lines.size)
        // Oldest should be line 50 (0..49 dropped)
        assertTrue(lines.first().contains("line 50"))
        assertTrue(lines.last().contains("line 549"))
    }

    @Test
    fun export_payload_contains_visible_entries_only() {
        DebugLog.clear()
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.INFO)
        DebugLog.log("T","info1")
        DebugLog.e("T","err1 skipped") // skipped under INFO
        val dumpInfo = DebugLog.dump()
        assertTrue(dumpInfo.contains("info1"))
        assertFalse(dumpInfo.contains("err1 skipped"))

        DebugLog.setLevel(DebugLog.Level.ERROR)
        DebugLog.log("T","info2 skipped") // skipped under ERROR
        DebugLog.e("T","err2")
        val dumpErr = DebugLog.dump()
        assertFalse(dumpErr.contains("info2 skipped"))
        assertTrue(dumpErr.contains("err2"))
    }
}
