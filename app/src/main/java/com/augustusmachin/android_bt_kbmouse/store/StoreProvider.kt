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
        store.subscribe {
            _stateFlow.value = store.state
        }
    }

    fun dispatch(action: Any) {
        store.dispatch(action)
    }

    fun asStateFlow(): StateFlow<AppState> = _stateFlow

    /**
     * Install or clear the only command sender. Sender availability is mirrored into canonical
     * store state so Compose never has to guess whether a remembered connection can accept input.
     */
    fun setKeySender(sender: KeySender?) {
        keySenderMiddleware.installSender(sender)
        if (sender != null) dispatch(Action.UpdateSelectedBackend(sender.backend))
        dispatch(Action.UpdateSenderAvailable(sender != null))
    }

    fun currentKeySender(): KeySender? = keySenderMiddleware.currentSender()
}
