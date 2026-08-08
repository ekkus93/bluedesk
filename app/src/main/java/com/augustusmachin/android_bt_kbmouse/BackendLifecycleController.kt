package com.augustusmachin.android_bt_kbmouse

/** Result returned by the platform-facing service lifecycle adapter. */
sealed interface LifecycleOperationResult {
    data object Success : LifecycleOperationResult

    data class Failure(val message: String) : LifecycleOperationResult
}

/**
 * Platform lifecycle operations are injected so transaction semantics can be proven with JVM tests.
 * Implementations must make cleanup operations idempotent: rollback can run after partial startup.
 */
interface BackendLifecycleOperations {
    fun startService(mode: BackendMode): LifecycleOperationResult

    fun bindService(mode: BackendMode): LifecycleOperationResult

    fun releaseHeldInput(mode: BackendMode)

    fun clearSenderAndListener(mode: BackendMode)

    fun unbindService(mode: BackendMode): LifecycleOperationResult

    fun stopService(mode: BackendMode): LifecycleOperationResult

    fun resetLocalState()
}

/**
 * Serial, fail-closed controller for starting, initializing, stopping, and switching HID backends.
 *
 * A successful service start or bind is not Ready. The owner advances listener/sender/backend init
 * from ServiceConnection callbacks and explicitly calls [markReady] only after backend-specific
 * initialization has completed.
 */
class BackendLifecycleController(
    private val coordinator: BackendRuntimeCoordinator,
    private val operations: BackendLifecycleOperations,
    private val publish: (BackendRuntimeState) -> Unit,
    private val surfaceFailure: (String) -> Unit,
) {
    @Synchronized
    fun start(mode: BackendMode): Boolean {
        val current = coordinator.state
        if (current is BackendRuntimeState.Ready && current.backend == mode) return true
        if (current is BackendRuntimeState.Starting && current.backend == mode) return true
        if (coordinator.currentLiveBackend != null) {
            surfaceFailure("Cannot start $mode while ${coordinator.currentLiveBackend} is still live")
            return false
        }

        if (coordinator.beginStart(mode) is BackendTransitionResult.Rejected) return false
        publishCurrent()
        advance(mode, BackendStartupStage.STARTING_SERVICE)

        when (val started = operations.startService(mode)) {
            LifecycleOperationResult.Success -> Unit
            is LifecycleOperationResult.Failure -> {
                rollbackStartup(mode, BackendFailureCode.SERVICE_START_FAILED, started.message, unbind = false)
                return false
            }
        }

        advance(mode, BackendStartupStage.BINDING_SERVICE)
        when (val bound = operations.bindService(mode)) {
            LifecycleOperationResult.Success -> return true
            is LifecycleOperationResult.Failure -> {
                rollbackStartup(mode, BackendFailureCode.SERVICE_BIND_FAILED, bound.message, unbind = true)
                return false
            }
        }
    }

    @Synchronized
    fun listenerInstalled(mode: BackendMode): Boolean = advance(mode, BackendStartupStage.INSTALLING_SENDER)

    @Synchronized
    fun senderInstalled(mode: BackendMode): Boolean = advance(mode, BackendStartupStage.BACKEND_INITIALIZATION)

    @Synchronized
    fun beginListenerInstallation(mode: BackendMode): Boolean = advance(mode, BackendStartupStage.INSTALLING_LISTENER)

    @Synchronized
    fun markReady(mode: BackendMode): Boolean {
        val result = coordinator.markReady(mode)
        publishCurrent()
        return result is BackendTransitionResult.Applied
    }

    @Synchronized
    fun failInitialization(
        mode: BackendMode,
        message: String,
    ) {
        rollbackStartup(mode, BackendFailureCode.BACKEND_INIT_FAILED, message, unbind = true)
    }

    @Synchronized
    fun stopCurrent(): Boolean {
        val mode = coordinator.currentLiveBackend ?: return false
        return stopInternal(mode)
    }

    @Synchronized
    fun switchTo(target: BackendMode): Boolean {
        val state = coordinator.state
        val live = coordinator.currentLiveBackend
        if (live == target && (state is BackendRuntimeState.Ready || state is BackendRuntimeState.Starting)) return true
        if (live != null && !stopInternal(live)) return false
        return start(target)
    }

    @Synchronized
    fun unexpectedServiceLoss(mode: BackendMode) {
        if (coordinator.state is BackendRuntimeState.Stopping) return
        if (coordinator.currentLiveBackend != mode) return
        operations.clearSenderAndListener(mode)
        operations.resetLocalState()
        coordinator.serviceLost(mode)
        publishCurrent()
        surfaceFailure("$mode service disconnected unexpectedly")
    }

    @Synchronized
    fun forceStoppedAfterOwnerTeardown() {
        val live = coordinator.currentLiveBackend
        if (live != null) {
            stopInternal(live)
        } else {
            operations.resetLocalState()
            coordinator.markStopped()
            publishCurrent()
        }
    }

    private fun stopInternal(mode: BackendMode): Boolean {
        coordinator.beginStop()
        publishCurrent()
        operations.releaseHeldInput(mode)
        operations.clearSenderAndListener(mode)
        val unbind = operations.unbindService(mode)
        val stop = operations.stopService(mode)
        operations.resetLocalState()
        val cleanupFailure = firstFailure(unbind, stop)
        if (cleanupFailure != null) {
            val message = "Failed to fully stop $mode: ${cleanupFailure.message}"
            coordinator.fail(mode, BackendFailure(BackendFailureCode.SWITCH_FAILED, message))
            publishCurrent()
            surfaceFailure(message)
            return false
        }
        coordinator.markStopped()
        publishCurrent()
        return true
    }

    private fun rollbackStartup(
        mode: BackendMode,
        code: BackendFailureCode,
        message: String,
        unbind: Boolean,
    ) {
        operations.clearSenderAndListener(mode)
        val unbindResult =
            if (unbind) operations.unbindService(mode) else LifecycleOperationResult.Success
        val stopResult = operations.stopService(mode)
        operations.resetLocalState()
        val cleanupFailure = firstFailure(unbindResult, stopResult)
        val fullMessage =
            if (cleanupFailure == null) message else "$message; cleanup also failed: ${cleanupFailure.message}"
        coordinator.fail(mode, BackendFailure(code, fullMessage))
        publishCurrent()
        surfaceFailure(fullMessage)
    }

    private fun firstFailure(vararg results: LifecycleOperationResult): LifecycleOperationResult.Failure? =
        results.firstOrNull { it is LifecycleOperationResult.Failure } as? LifecycleOperationResult.Failure

    private fun advance(
        mode: BackendMode,
        stage: BackendStartupStage,
    ): Boolean {
        val result = coordinator.advanceStart(mode, stage)
        publishCurrent()
        return result is BackendTransitionResult.Applied
    }

    private fun publishCurrent() {
        publish(coordinator.state)
    }
}
