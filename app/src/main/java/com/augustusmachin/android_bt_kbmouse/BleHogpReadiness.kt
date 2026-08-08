package com.augustusmachin.android_bt_kbmouse

import java.util.ArrayDeque

enum class BleHogpStartupStage {
    VALIDATING_PERMISSIONS,
    STARTING_FOREGROUND,
    RESOLVING_ADVERTISER,
    OPENING_GATT_SERVER,
    REGISTERING_GATT_SERVICES,
    STARTING_ADVERTISING,
}

sealed interface BleHogpStartupState {
    data class Starting(val stage: BleHogpStartupStage) : BleHogpStartupState

    data object Ready : BleHogpStartupState

    data class Failed(val message: String) : BleHogpStartupState
}

/** Process-local durable snapshot so late binders do not depend on transient service callbacks. */
object BleHogpStartupRegistry {
    @Volatile
    var state: BleHogpStartupState = BleHogpStartupState.Starting(BleHogpStartupStage.VALIDATING_PERMISSIONS)
        private set

    internal fun publish(next: BleHogpStartupState) {
        state = next
    }
}

/** Pure state machine used by [BleHogpService] and JVM tests. */
class BleHogpReadinessTracker(
    private val publish: (BleHogpStartupState) -> Unit = BleHogpStartupRegistry::publish,
) {
    @Volatile
    var state: BleHogpStartupState = BleHogpStartupState.Starting(BleHogpStartupStage.VALIDATING_PERMISSIONS)
        private set

    private val pendingServiceIds = ArrayDeque<String>()
    private var currentServiceId: String? = null

    init {
        publish(state)
    }

    @Synchronized
    fun advance(stage: BleHogpStartupStage) {
        if (state !is BleHogpStartupState.Failed && state !is BleHogpStartupState.Ready) {
            setState(BleHogpStartupState.Starting(stage))
        }
    }

    @Synchronized
    fun beginGattRegistration(serviceIds: List<String>) {
        require(serviceIds.isNotEmpty()) { "At least one mandatory GATT service is required" }
        pendingServiceIds.clear()
        pendingServiceIds.addAll(serviceIds)
        currentServiceId = null
        setState(BleHogpStartupState.Starting(BleHogpStartupStage.REGISTERING_GATT_SERVICES))
    }

    @Synchronized
    fun nextServiceToRegister(): String? {
        if (state is BleHogpStartupState.Failed) return null
        if (currentServiceId != null) return null
        val next = pendingServiceIds.peekFirst() ?: return null
        currentServiceId = next
        return next
    }

    @Synchronized
    fun onAddServiceImmediate(
        serviceId: String,
        accepted: Boolean,
    ): Boolean {
        if (currentServiceId != serviceId) {
            fail("Unexpected GATT addService result for $serviceId; expected $currentServiceId")
            return false
        }
        if (!accepted) {
            fail("GATT addService rejected $serviceId")
            return false
        }
        return true
    }

    /** Returns true when all mandatory services have completed successfully. */
    @Synchronized
    fun onServiceAdded(
        serviceId: String,
        success: Boolean,
    ): Boolean {
        if (currentServiceId != serviceId) {
            fail("Unexpected GATT service callback for $serviceId; expected $currentServiceId")
            return false
        }
        if (!success) {
            fail("GATT service registration failed for $serviceId")
            return false
        }
        pendingServiceIds.removeFirst()
        currentServiceId = null
        return pendingServiceIds.isEmpty()
    }

    @Synchronized
    fun beginAdvertising() {
        if (state is BleHogpStartupState.Failed) return
        if (pendingServiceIds.isNotEmpty() || currentServiceId != null) {
            fail("Advertising requested before mandatory GATT registration completed")
            return
        }
        setState(BleHogpStartupState.Starting(BleHogpStartupStage.STARTING_ADVERTISING))
    }

    @Synchronized
    fun advertisingSucceeded() {
        if (state == BleHogpStartupState.Starting(BleHogpStartupStage.STARTING_ADVERTISING)) {
            setState(BleHogpStartupState.Ready)
        } else {
            fail("Advertising success arrived in invalid startup state: $state")
        }
    }

    @Synchronized
    fun fail(message: String) {
        if (state !is BleHogpStartupState.Failed) setState(BleHogpStartupState.Failed(message))
    }

    private fun setState(next: BleHogpStartupState) {
        state = next
        publish(next)
    }
}
