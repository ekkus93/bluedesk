package com.augustusmachin.android_bt_kbmouse

/** Capabilities that are safe to expose for the currently active HID backend. */
data class BackendCapabilities(
    val discovery: Boolean,
    val explicitConnect: Boolean,
    val explicitDisconnect: Boolean,
    val classicPairing: Boolean,
    val defaultDevice: Boolean,
    val deviceRename: Boolean,
    val verticalScroll: Boolean,
    val horizontalScroll: Boolean,
    val middleClick: Boolean,
    val hostLedReports: Boolean,
)

object BackendCapabilitySets {
    val classic =
        BackendCapabilities(
            discovery = true,
            explicitConnect = true,
            explicitDisconnect = true,
            classicPairing = true,
            defaultDevice = true,
            deviceRename = true,
            verticalScroll = true,
            horizontalScroll = true,
            middleClick = true,
            hostLedReports = true,
        )

    val bleHogp =
        BackendCapabilities(
            discovery = false,
            explicitConnect = false,
            explicitDisconnect = false,
            classicPairing = false,
            defaultDevice = false,
            deviceRename = false,
            verticalScroll = false,
            horizontalScroll = false,
            middleClick = true,
            hostLedReports = true,
        )

    fun forMode(mode: BackendMode): BackendCapabilities =
        when (mode) {
            BackendMode.CLASSIC_HID -> classic
            BackendMode.BLE_HOGP -> bleHogp
        }
}

enum class BackendStartupStage {
    PERMISSION_VALIDATION,
    STARTING_SERVICE,
    BINDING_SERVICE,
    INSTALLING_LISTENER,
    INSTALLING_SENDER,
    BACKEND_INITIALIZATION,
}

enum class BackendFailureCode {
    PERMISSION_DENIED,
    UNSUPPORTED_PLATFORM,
    SERVICE_START_FAILED,
    SERVICE_BIND_FAILED,
    BACKEND_INIT_FAILED,
    SERVICE_LOST,
    SWITCH_FAILED,
}

data class BackendFailure(
    val code: BackendFailureCode,
    val message: String,
)

sealed interface BackendRuntimeState {
    data object Stopped : BackendRuntimeState

    data class Starting(
        val backend: BackendMode,
        val stage: BackendStartupStage,
    ) : BackendRuntimeState

    data class Ready(
        val backend: BackendMode,
        val capabilities: BackendCapabilities,
    ) : BackendRuntimeState

    data class Stopping(val backend: BackendMode) : BackendRuntimeState

    data class Failed(
        val backend: BackendMode?,
        val failure: BackendFailure,
    ) : BackendRuntimeState
}

sealed interface BackendTransitionResult {
    data class Applied(val state: BackendRuntimeState) : BackendTransitionResult

    data class Rejected(val reason: String) : BackendTransitionResult
}

/**
 * Single lifecycle truth for a backend activation transaction.
 *
 * Activity bind booleans may still exist as low-level bookkeeping, but they are not allowed to
 * answer the product question "which backend is active?". This coordinator does that and rejects
 * conflicting starts so a second backend cannot become authoritative while the first is live.
 */
class BackendRuntimeCoordinator(initial: BackendRuntimeState = BackendRuntimeState.Stopped) {
    @Volatile
    var state: BackendRuntimeState = initial
        private set

    val currentLiveBackend: BackendMode?
        get() =
            when (val current = state) {
                is BackendRuntimeState.Starting -> current.backend
                is BackendRuntimeState.Ready -> current.backend
                is BackendRuntimeState.Stopping -> current.backend
                is BackendRuntimeState.Failed,
                BackendRuntimeState.Stopped,
                -> null
            }

    @Synchronized
    fun beginStart(backend: BackendMode): BackendTransitionResult =
        when (state) {
            BackendRuntimeState.Stopped,
            is BackendRuntimeState.Failed,
            -> apply(BackendRuntimeState.Starting(backend, BackendStartupStage.PERMISSION_VALIDATION))

            else -> BackendTransitionResult.Rejected("A backend lifecycle transaction is already active")
        }

    @Synchronized
    fun advanceStart(
        backend: BackendMode,
        stage: BackendStartupStage,
    ): BackendTransitionResult {
        val current = state
        return if (current is BackendRuntimeState.Starting && current.backend == backend) {
            apply(BackendRuntimeState.Starting(backend, stage))
        } else {
            BackendTransitionResult.Rejected("Cannot advance a backend that is not starting")
        }
    }

    @Synchronized
    fun markReady(backend: BackendMode): BackendTransitionResult {
        val current = state
        return if (current is BackendRuntimeState.Starting && current.backend == backend) {
            apply(BackendRuntimeState.Ready(backend, BackendCapabilitySets.forMode(backend)))
        } else {
            BackendTransitionResult.Rejected("Cannot mark a backend ready outside its start transaction")
        }
    }

    @Synchronized
    fun beginStop(): BackendTransitionResult =
        when (val current = state) {
            is BackendRuntimeState.Starting -> apply(BackendRuntimeState.Stopping(current.backend))
            is BackendRuntimeState.Ready -> apply(BackendRuntimeState.Stopping(current.backend))
            is BackendRuntimeState.Stopping -> BackendTransitionResult.Rejected("Backend is already stopping")
            is BackendRuntimeState.Failed,
            BackendRuntimeState.Stopped,
            -> BackendTransitionResult.Rejected("No live backend to stop")
        }

    @Synchronized
    fun markStopped(): BackendTransitionResult = apply(BackendRuntimeState.Stopped)

    @Synchronized
    fun fail(
        backend: BackendMode?,
        failure: BackendFailure,
    ): BackendTransitionResult = apply(BackendRuntimeState.Failed(backend, failure))

    @Synchronized
    fun serviceLost(backend: BackendMode): BackendTransitionResult {
        val current = state
        val ownsRuntime =
            (current is BackendRuntimeState.Starting && current.backend == backend) ||
                (current is BackendRuntimeState.Ready && current.backend == backend)
        return if (ownsRuntime) {
            fail(
                backend,
                BackendFailure(BackendFailureCode.SERVICE_LOST, "$backend service disconnected unexpectedly"),
            )
        } else {
            BackendTransitionResult.Rejected("Lost service does not own the active runtime")
        }
    }

    private fun apply(next: BackendRuntimeState): BackendTransitionResult {
        state = next
        return BackendTransitionResult.Applied(next)
    }
}
