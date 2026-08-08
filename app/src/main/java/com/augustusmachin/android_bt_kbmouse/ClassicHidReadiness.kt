package com.augustusmachin.android_bt_kbmouse

sealed interface ClassicHidStartupState {
    data object WaitingForRegisterRequest : ClassicHidStartupState

    data object WaitingForRegistrationCallback : ClassicHidStartupState

    data object Ready : ClassicHidStartupState

    data class Failed(val message: String) : ClassicHidStartupState
}

/** Process-local durable registration state for Activity rebind/reconciliation. */
object ClassicHidStartupRegistry {
    @Volatile
    var state: ClassicHidStartupState = ClassicHidStartupState.WaitingForRegisterRequest
        private set

    internal fun publish(next: ClassicHidStartupState) {
        state = next
    }

    /** Begin a new Classic service activation without inheriting readiness from a prior instance. */
    fun beginActivation() {
        publish(ClassicHidStartupState.WaitingForRegisterRequest)
    }
}

/** Pure Classic HID registration state machine. */
class ClassicHidReadinessTracker(
    private val publish: (ClassicHidStartupState) -> Unit = ClassicHidStartupRegistry::publish,
) {
    @Volatile
    var state: ClassicHidStartupState = ClassicHidStartupState.WaitingForRegisterRequest
        private set

    init {
        publish(state)
    }

    @Synchronized
    fun registrationRequestAccepted() {
        if (state == ClassicHidStartupState.WaitingForRegisterRequest) {
            setState(ClassicHidStartupState.WaitingForRegistrationCallback)
        }
    }

    @Synchronized
    fun registrationRequestFailed(message: String) {
        setState(ClassicHidStartupState.Failed(message))
    }

    @Synchronized
    fun registrationCallback(registered: Boolean) {
        if (registered) {
            if (state == ClassicHidStartupState.WaitingForRegistrationCallback) {
                setState(ClassicHidStartupState.Ready)
            } else {
                setState(ClassicHidStartupState.Failed("Classic HID registration succeeded in invalid state: $state"))
            }
        } else {
            setState(ClassicHidStartupState.Failed("Classic HID app registration failed"))
        }
    }

    private fun setState(next: ClassicHidStartupState) {
        state = next
        publish(next)
    }
}
