package com.augustusmachin.android_bt_kbmouse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared signal that the startup Bluetooth-permission flow has resolved — settings loaded,
 * the selected backend's permissions requested/checked, and any denial/fallback handled.
 *
 * The optional notification-permission prompt waits on this instead of a fixed timer, so it
 * can never fire while the startup permission launcher is still active.
 */
object StartupState {
    private val resolved = MutableStateFlow(false)

    val permissionFlowResolved: StateFlow<Boolean> = resolved.asStateFlow()

    fun markPermissionFlowResolved() {
        resolved.value = true
    }
}
