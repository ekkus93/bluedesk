package com.augustusmachin.android_bt_kbmouse

/** Pure helpers to unit test permission UX decision branches without Android framework. */
object PermissionUxLogic {
    enum class DeniedFlow { ShowRationale, ShowSettings }

    /** Return true when all required permissions are already granted. */
    fun shouldStartDiscovery(missing: Collection<String>): Boolean = missing.isEmpty()

    /** Determine denied flow: if any permission is permanently denied (rationale = false) go to Settings else show rationale dialog. */
    fun deniedFlow(rationaleFlags: Map<String, Boolean>): DeniedFlow =
        if (rationaleFlags.values.any { it == false }) DeniedFlow.ShowSettings else DeniedFlow.ShowRationale

    /** Gate actions (e.g., scan button enable) based on permission state and connection. */
    fun canInitiateScan(
        allGranted: Boolean,
        connected: Boolean,
    ): Boolean = allGranted || !connected
}
