package com.augustusmachin.android_bt_kbmouse

enum class BackendMode { CLASSIC_HID, BLE_HOGP }

object BackendSelector {
    fun fromSettings(useBleHogp: Boolean): BackendMode =
        if (useBleHogp) BackendMode.BLE_HOGP else BackendMode.CLASSIC_HID
}

/** One step in a backend transition. */
sealed interface BackendStep {
    data class Stop(val mode: BackendMode) : BackendStep

    data class Start(val mode: BackendMode) : BackendStep
}

/**
 * Pure planner for switching HID backends. Produces the ordered steps to move from the
 * [current] backend to [target]: stop the current backend (if any) before starting the
 * target, so both services are never running at once. An unchanged target is a no-op.
 *
 * Kept Android-free so the transition logic can be unit-tested without service mocks.
 */
object BackendTransitionPlanner {
    fun plan(
        current: BackendMode?,
        target: BackendMode,
    ): List<BackendStep> =
        if (current == target) {
            emptyList()
        } else {
            buildList {
                if (current != null) add(BackendStep.Stop(current))
                add(BackendStep.Start(target))
            }
        }
}
