package com.augustusmachin.android_bt_kbmouse.store

import org.reduxkotlin.Reducer

private const val MAX_PREVIEW_KEYS = 24

val appReducer: Reducer<AppState> = { state, action ->
    when (action) {
        is Action.ToggleCtrl -> state.copy(keyboard = state.keyboard.copy(ctrl = !state.keyboard.ctrl))
        is Action.ToggleShift -> state.copy(keyboard = state.keyboard.copy(shift = !state.keyboard.shift))
        is Action.ToggleAlt -> state.copy(keyboard = state.keyboard.copy(alt = !state.keyboard.alt))
        is Action.ToggleGui -> state.copy(keyboard = state.keyboard.copy(gui = !state.keyboard.gui))
        Action.ReleaseLockedModifiers -> {
            val kb = state.keyboard
            state.copy(
                keyboard =
                    kb.copy(
                        ctrl = if (kb.ctrlPersist) kb.ctrl else false,
                        shift = if (kb.shiftPersist) kb.shift else false,
                        alt = if (kb.altPersist) kb.alt else false,
                        gui = if (kb.guiPersist) kb.gui else false,
                    ),
            )
        }
        is Action.SetCtrlPersist -> state.copy(keyboard = state.keyboard.copy(ctrlPersist = action.persist))
        is Action.SetShiftPersist -> state.copy(keyboard = state.keyboard.copy(shiftPersist = action.persist))
        is Action.SetAltPersist -> state.copy(keyboard = state.keyboard.copy(altPersist = action.persist))
        is Action.SetGuiPersist -> state.copy(keyboard = state.keyboard.copy(guiPersist = action.persist))
        is Action.SetOfflinePreview -> state.copy(ui = state.ui.copy(offlinePreview = action.enabled))
        is Action.SetShowExtended -> state.copy(ui = state.ui.copy(showExtended = action.show))
        is Action.SetExtendedPage -> state.copy(ui = state.ui.copy(extendedPage = action.page))
        is Action.TrackPreviewKey -> state
        is Action.AddPreviewKey -> {
            val updated =
                (state.ui.previewKeys + PreviewKeyEntry(action.id, action.label, action.decorate)).takeLast(
                    MAX_PREVIEW_KEYS,
                )
            state.copy(ui = state.ui.copy(previewKeys = updated))
        }
        is Action.RemovePreviewKey -> {
            val updated = state.ui.previewKeys.filterNot { it.id == action.id }
            if (updated.size == state.ui.previewKeys.size) {
                state
            } else {
                state.copy(ui = state.ui.copy(previewKeys = updated))
            }
        }
        is Action.SendKey,
        is Action.KeyDown,
        is Action.KeyUp,
        is Action.MoveMouse,
        Action.LeftClick,
        Action.RightClick,
        Action.MiddleClick,
        is Action.ScrollVertical,
        is Action.ScrollHorizontal,
        Action.ToggleCapsLock,
        Action.ToggleScrollLock,
        -> state

        is Action.UpdateDiscoveredDevices ->
            state.copy(connection = state.connection.copy(discoveredDevices = action.devices))
        is Action.UpdatePairedDevices ->
            state.copy(connection = state.connection.copy(pairedDevices = action.devices))
        is Action.UpdateConnectedDevice ->
            state.copy(connection = state.connection.copy(connectedDevice = action.device))
        is Action.UpdateMessage ->
            state.copy(connection = state.connection.copy(message = action.message))
        is Action.UpdateDefaultDevice ->
            state.copy(connection = state.connection.copy(defaultDeviceAddress = action.address))
        is Action.UpdateLocks ->
            state.copy(
                connection =
                    state.connection.copy(
                        capsLock = action.caps,
                        scrollLock = action.scroll,
                    ),
            )
        is Action.UpdateIsScanning ->
            state.copy(connection = state.connection.copy(isScanning = action.scanning))
        is Action.UpdateSelectedBackend ->
            state.copy(backend = state.backend.copy(selectedBackend = action.backend))
        is Action.UpdateBackendRuntime ->
            state.copy(backend = state.backend.copy(runtime = action.runtime))
        is Action.UpdateSenderAvailable ->
            state.copy(backend = state.backend.copy(senderAvailable = action.available))
        is Action.ReportCommandResult ->
            state.copy(backend = state.backend.copy(lastCommandResult = action.result))
        Action.ClearCommandResult ->
            state.copy(backend = state.backend.copy(lastCommandResult = null))
        Action.StartDiscovery,
        Action.StopDiscovery,
        is Action.PairDevice,
        is Action.ConnectDevice,
        Action.DisconnectDevice,
        is Action.ForgetDevice,
        is Action.SetDefaultDevice,
        is Action.RenameDevice,
        is Action.MouseButtonDown,
        Action.MouseButtonUp,
        -> state
        else -> state
    }
}
