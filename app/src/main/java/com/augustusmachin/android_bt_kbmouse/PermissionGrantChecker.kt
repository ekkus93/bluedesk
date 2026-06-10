package com.augustusmachin.android_bt_kbmouse

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Thin Android-facing wrapper that reads the *actual* OS permission state.
 *
 * Use this after any permission launcher callback instead of trusting the callback result map:
 * `RequestMultiplePermissions` maps are partial (only the permissions requested that round), so
 * treating them as full state can falsely report an already-granted permission as denied. Pure
 * decision logic lives in [StartupPermissionDecision]; this object only does the framework read.
 */
object PermissionGrantChecker {
    fun hasAll(
        context: Context,
        permissions: List<String>,
    ): Boolean = permissions.all { isGranted(context, it) }

    fun missing(
        context: Context,
        permissions: List<String>,
    ): List<String> = permissions.filterNot { isGranted(context, it) }

    /** The subset of [permissions] currently granted, as a set for [StartupPermissionDecision]. */
    fun grantedSet(
        context: Context,
        permissions: List<String>,
    ): Set<String> = permissions.filterTo(mutableSetOf()) { isGranted(context, it) }

    private fun isGranted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
