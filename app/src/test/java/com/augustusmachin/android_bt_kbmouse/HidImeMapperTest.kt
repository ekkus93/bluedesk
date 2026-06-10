package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HidImeMapperTest {
    @Test
    fun letters_lower_and_upper() {
        val a = charToHid('a')
        assertNotNull(a)
        assertEquals(0x04.toByte(), a!!.first)
        assertEquals(0, a.second.toInt())

        val upperA = charToHid('A')
        assertNotNull(upperA)
        assertEquals(0x04.toByte(), upperA!!.first)
        assertEquals(0x02, upperA.second)
    }

    @Test
    fun digits_and_space_and_tab() {
        val one = charToHid('1')
        assertNotNull(one)
        assertEquals(0x1E.toByte(), one!!.first)

        val space = charToHid(' ')
        assertNotNull(space)
        assertEquals(0x2C.toByte(), space!!.first)

        val tab = charToHid('\t')
        assertNotNull(tab)
        assertEquals(0x2B.toByte(), tab!!.first)
    }

    @Test
    fun punctuation_and_shifted() {
        val dash = charToHid('-')
        assertNotNull(dash)
        assertEquals(0x2D.toByte(), dash!!.first)
        assertEquals(0, dash.second)

        val ex = charToHid('!')
        assertNotNull(ex)
        assertEquals(0x1E.toByte(), ex!!.first)
        assertEquals(0x02, ex.second)

        val question = charToHid('?')
        assertNotNull(question)
        assertEquals(0x38.toByte(), question!!.first)
        assertEquals(0x02, question.second)
    }

    @Test
    fun newline_and_carriage_return() {
        val nl = charToHid('\n')
        assertNotNull(nl)
        assertEquals(0x28.toByte(), nl!!.first)

        val cr = charToHid('\r')
        assertNotNull(cr)
        assertEquals(0x28.toByte(), cr!!.first)
    }

    @Test
    fun unmapped_character_returns_null() {
        val greek = charToHid('α')
        assertNull(greek)
    }

    // ── imeAppendedText: the repeated-key fix ──────────────────────────────

    @Test
    fun imeAppended_firstCharacter() {
        assertEquals("a", imeAppendedText("", "a"))
    }

    @Test
    fun imeAppended_repeatedSameCharacter() {
        // The bug: "a" -> "aa" must report the appended "a" (not be dropped as a dup).
        assertEquals("a", imeAppendedText("a", "aa"))
        assertEquals("a", imeAppendedText("aa", "aaa"))
    }

    @Test
    fun imeAppended_distinctCharacter() {
        assertEquals("b", imeAppendedText("aa", "aab"))
    }

    @Test
    fun imeAppended_multiCharBatch() {
        assertEquals("xy", imeAppendedText("ab", "abxy"))
    }

    @Test
    fun imeAppended_noChangeIsNull() {
        assertNull(imeAppendedText("abc", "abc"))
    }

    @Test
    fun imeAppended_backspaceShrinkIsNull() {
        assertNull(imeAppendedText("abc", "ab"))
    }

    @Test
    fun imeAppended_nonPrefixReplacementIsNull() {
        // e.g. autocorrect replacing the text, not a clean append.
        assertNull(imeAppendedText("teh", "the"))
    }

    @Test
    fun imeAppended_largeJumpRejectedAsDesync() {
        // Guards against replaying the whole buffer after a reset desync.
        assertNull(imeAppendedText("", "abcdefghij", maxBatch = 8))
    }
}
