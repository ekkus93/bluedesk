package com.augustusmachin.android_bt_kbmouse

/** What BootReceiver should do on ACTION_BOOT_COMPLETED, given settings + permission state. */
sealed interface BootStartDecision {
    data object StartNothing : BootStartDecision

    data object StartClassic : BootStartDecision

    data object StartBle : BootStartDecision

    /** Wanted to start a backend but cannot; [reason] is logged. */
    data class Skip(val reason: String) : BootStartDecision
}

/**
 * Pure, backend-aware boot planner. Boot respects the selected backend and required
 * permissions: it never silently starts Classic when BLE HOGP is selected, and never starts a
 * backend whose permissions are missing. Boot must NOT change the user's setting (that only
 * happens on interactive startup), so a permission-missing case is a [Skip], not a fallback.
 *
 * Kept Android-free so boot decisions are unit-testable without a device.
 */
object BootStartPlanner {
    fun plan(
        startOnBoot: Boolean,
        useBleHogp: Boolean,
        hasClassicPermissions: Boolean,
        hasBlePermissions: Boolean,
    ): BootStartDecision =
        when {
            !startOnBoot -> BootStartDecision.StartNothing
            useBleHogp && hasBlePermissions -> BootStartDecision.StartBle
            useBleHogp ->
                BootStartDecision.Skip(
                    "Start on boot skipped: BLE HOGP selected but required Bluetooth " +
                        "connect/advertise permissions are missing.",
                )
            hasClassicPermissions -> BootStartDecision.StartClassic
            else ->
                BootStartDecision.Skip(
                    "Start on boot skipped: Classic HID selected but required Bluetooth " +
                        "connect permission is missing.",
                )
        }
}
