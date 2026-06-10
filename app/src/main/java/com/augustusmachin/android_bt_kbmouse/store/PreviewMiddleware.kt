package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.reduxkotlin.middleware
import java.util.concurrent.atomic.AtomicLong

/**
 * Middleware that tracks preview key presses so the UI can display their names briefly.
 * Each tracked key generates an AddPreviewKey action immediately and a RemovePreviewKey
 * action after the configured TTL.
 */
class PreviewMiddleware(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val idGenerator = AtomicLong(0L)

    fun create() =
        middleware<AppState> { store, next, action ->
            if (action is Action.TrackPreviewKey) {
                val rawLabel = action.label
                val label = if (action.decorate) rawLabel.trim() else rawLabel
                if (label.isNotEmpty()) {
                    val entryId = idGenerator.incrementAndGet()
                    store.dispatch(Action.AddPreviewKey(entryId, label, action.decorate))
                    val ttl = action.ttlMillis.coerceAtLeast(0L)
                    scope.launch {
                        if (ttl > 0L) {
                            delay(ttl)
                        }
                        store.dispatch(Action.RemovePreviewKey(entryId))
                    }
                }
            }
            next(action)
        }
}
