package com.augustusmachin.android_bt_kbmouse

/** What the startup flow should do, given the plan and the *actual* granted permission set. */
sealed interface StartupDecision {
    /** All required permissions for the planned backend are granted; start it. */
    data object StartPlannedBackend : StartupDecision

    /** Planned BLE backend is missing permissions; persist Classic + fall back. */
    data object FallbackBleToClassic : StartupDecision

    /** Planned Classic backend is missing permissions; prompt the user. */
    data object ShowClassicPermissionDenied : StartupDecision
}

/**
 * Pure decision helper for the post-permission-callback startup flow.
 *
 * `RequestMultiplePermissions` callback maps are *partial* — they report only the permissions
 * requested in that launcher call, not the full state. So the Android layer must re-read actual
 * OS permission state (see [PermissionGrantChecker]) and pass the granted set here; this object
 * never sees the callback map. Kept Android-free so the partial-callback bug is unit-testable.
 */
object StartupPermissionDecision {
    fun decide(
        plan: StartupPermissionPlan,
        grantedFullState: Set<String>,
    ): StartupDecision =
        when {
            allGranted(plan.requiredPermissions, grantedFullState) -> StartupDecision.StartPlannedBackend
            plan.backend == BackendMode.BLE_HOGP -> StartupDecision.FallbackBleToClassic
            else -> StartupDecision.ShowClassicPermissionDenied
        }

    /** True when every entry of [required] is present in the actual [grantedFullState] set. */
    fun allGranted(
        required: List<String>,
        grantedFullState: Set<String>,
    ): Boolean = grantedFullState.containsAll(required)
}
