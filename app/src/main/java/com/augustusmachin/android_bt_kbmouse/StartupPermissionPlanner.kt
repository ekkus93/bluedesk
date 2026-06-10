package com.augustusmachin.android_bt_kbmouse

/** The backend chosen for startup and the runtime permissions that backend requires. */
data class StartupPermissionPlan(
    val backend: BackendMode,
    val requiredPermissions: List<String>,
)

/**
 * Pure planner that selects the startup backend from persisted settings and the permissions
 * that backend needs: BLE HOGP needs connect + advertise; Classic HID needs connect only.
 *
 * Kept Android-free so startup permission selection is unit-testable without a device, and so
 * the "always request Classic permissions" startup bug cannot regress silently.
 */
object StartupPermissionPlanner {
    fun plan(
        settings: Settings,
        sdkInt: Int,
    ): StartupPermissionPlan =
        if (settings.useBleHogp) {
            StartupPermissionPlan(BackendMode.BLE_HOGP, PermissionPolicy.requiredForBleStartup(sdkInt))
        } else {
            StartupPermissionPlan(BackendMode.CLASSIC_HID, PermissionPolicy.requiredForClassicStartup(sdkInt))
        }
}
