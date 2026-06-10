package com.augustusmachin.android_bt_kbmouse.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReducerTest {
    @Test
    fun `add preview keys appends and trims oldest entries`() {
        var state = AppState()
        repeat(30) { index ->
            state = appReducer(state, Action.AddPreviewKey(index.toLong(), "K$index", decorate = true))
        }
        assertEquals(24, state.ui.previewKeys.size)
        val expectedIds = (6L..29L).toList()
        assertEquals(expectedIds, state.ui.previewKeys.map { it.id })
        assertEquals(expectedIds.map { "K$it" }, state.ui.previewKeys.map { it.label })
        assertTrue(state.ui.previewKeys.all { it.decorate })
    }

    @Test
    fun `remove preview key deletes matching entry`() {
        var state = AppState()
        state = appReducer(state, Action.AddPreviewKey(1L, "First", decorate = true))
        state = appReducer(state, Action.AddPreviewKey(2L, "Second", decorate = false))
        assertEquals(listOf(1L, 2L), state.ui.previewKeys.map { it.id })
        assertEquals(listOf(true, false), state.ui.previewKeys.map { it.decorate })

        val updated = appReducer(state, Action.RemovePreviewKey(1L))
        assertEquals(listOf(2L), updated.ui.previewKeys.map { it.id })
    }

    @Test
    fun `remove preview key no-op when id missing`() {
        val state = appReducer(AppState(), Action.AddPreviewKey(10L, "Only", decorate = true))
        val next = appReducer(state, Action.RemovePreviewKey(42L))
        assertSame(state, next)
        assertTrue(next.ui.previewKeys.isNotEmpty())
    }
}
