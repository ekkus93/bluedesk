package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore

object StoreProvider {
    private val keySenderMiddleware = KeySenderMiddleware()
    private val previewMiddleware = PreviewMiddleware()
    private val store: Store<AppState> =
        createStore(
            appReducer,
            AppState(),
            applyMiddleware(previewMiddleware.create(), keySenderMiddleware.create()),
        )
    private val _stateFlow = MutableStateFlow(store.state)

    init {
        store.subscribe { _stateFlow.value = store.state }
    }

    fun dispatch(action: Any) {
        store.dispatch(action)
    }

    fun asStateFlow(): StateFlow<AppState> = _stateFlow

    /**
     * Sender installation occurs only after startup permission validation. Mirror both sender and
     * initial permission validity into canonical state; later SecurityException results explicitly
     * invalidate permissions again. Clearing the sender clears both facts.
     */
    fun setKeySender(sender: KeySender?) {
        keySenderMiddleware.installSender(sender)
        if (sender != null) dispatch(Action.UpdateSelectedBackend(sender.backend))
        dispatch(Action.UpdateSenderAvailable(sender != null))
        dispatch(Action.UpdatePermissionsValid(sender != null))
    }

    fun currentKeySender(): KeySender? = keySenderMiddleware.currentSender()
}
