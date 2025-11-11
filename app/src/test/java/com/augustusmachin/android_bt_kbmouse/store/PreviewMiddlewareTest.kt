package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewMiddlewareTest {
    @Test
    fun `track preview key adds entry then removes after ttl`() = runTest {
        val middleware = PreviewMiddleware(this)
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))

        store.dispatch(Action.TrackPreviewKey("TAB", ttlMillis = 1_000L))
        runCurrent()
        assertEquals(listOf("TAB"), store.state.ui.previewKeys.map { it.label })
        assertEquals(listOf(true), store.state.ui.previewKeys.map { it.decorate })

        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(store.state.ui.previewKeys.isEmpty())
    }

    @Test
    fun `blank labels are ignored`() = runTest {
        val middleware = PreviewMiddleware(this)
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))

        store.dispatch(Action.TrackPreviewKey("   ", ttlMillis = 1_000L))
        runCurrent()
        assertTrue(store.state.ui.previewKeys.isEmpty())
    }

    @Test
    fun `undecorated entries preserve whitespace`() = runTest {
        val middleware = PreviewMiddleware(this)
        val store = createStore(appReducer, AppState(), applyMiddleware(middleware.create()))

        store.dispatch(Action.TrackPreviewKey(" ", ttlMillis = 500L, decorate = false))
        runCurrent()
        assertEquals(1, store.state.ui.previewKeys.size)
        assertEquals(" ", store.state.ui.previewKeys.first().label)
        assertEquals(false, store.state.ui.previewKeys.first().decorate)
    }
}
