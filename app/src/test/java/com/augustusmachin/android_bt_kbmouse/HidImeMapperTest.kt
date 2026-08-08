package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(charToHid('α'))
    }

    @Test
    fun imeAppended_firstCharacter() {
        assertEquals("a", imeAppendedText("", "a"))
    }

    @Test
    fun imeAppended_repeatedSameCharacter() {
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
        assertNull(imeAppendedText("teh", "the"))
    }

    @Test
    fun imeAppended_largeJumpRejectedAsDesync() {
        assertNull(imeAppendedText("", "abcdefghij", maxBatch = 8))
    }

    @Test
    fun imePlan_simpleAppend() {
        assertEquals(ImeEditPlan.Apply(0, "c"), planImeEdit("ab", "abc"))
    }

    @Test
    fun imePlan_multiCharacterAppend() {
        assertEquals(ImeEditPlan.Apply(0, "xyz"), planImeEdit("ab", "abxyz"))
    }

    @Test
    fun imePlan_backspaceDelete() {
        assertEquals(ImeEditPlan.Apply(1, ""), planImeEdit("abc", "ab"))
    }

    @Test
    fun imePlan_equalLengthReplacement() {
        assertEquals(ImeEditPlan.Apply(2, "he"), planImeEdit("teh", "the"))
    }

    @Test
    fun imePlan_suffixReplacement() {
        assertEquals(ImeEditPlan.Apply(3, "xyz"), planImeEdit("prefixabc", "prefixxyz"))
    }

    @Test
    fun imePlan_compositionLikeReplacement() {
        assertEquals(ImeEditPlan.Apply(2, "llo"), planImeEdit("hel", "hello"))
    }

    @Test
    fun imePlan_noChange() {
        assertEquals(ImeEditPlan.NoChange, planImeEdit("same", "same"))
    }

    @Test
    fun imePlan_largeDesyncRequiresReset() {
        val plan = planImeEdit("abcdefghijklmnop", "qrstuvwxyzabcdef", maxOperations = 8)
        assertTrue(plan is ImeEditPlan.ResetRequired)
    }
}
