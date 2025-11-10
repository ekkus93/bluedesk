package com.augustusmachin.android_bt_kbmouse.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import org.reduxkotlin.applyMiddleware

object StoreProvider {
    private val keySenderMiddleware = KeySenderMiddleware()
    private val store: Store<AppState> = createStore(appReducer, AppState(), applyMiddleware(keySenderMiddleware.create()))
    private val _stateFlow = MutableStateFlow(store.state)

    init {
        // Subscribe to store updates and push to StateFlow for Compose
        store.subscribe {
            _stateFlow.value = store.state
        }
    }

    fun dispatch(action: Any) {
        store.dispatch(action)
    }

    fun asStateFlow(): StateFlow<AppState> = _stateFlow

    fun setKeySender(sender: KeySender?) {
        keySenderMiddleware.sender = sender
    }
}
