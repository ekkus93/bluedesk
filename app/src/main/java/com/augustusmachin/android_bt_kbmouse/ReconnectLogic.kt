package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import kotlin.math.min

private const val DEFAULT_RECONNECT_BASE_MS = 2000L
private const val MAX_RECONNECT_DELAY_MS = 30_000L

/** Pure helpers for reconnect/backoff logic to enable host JVM unit testing without Android Handler. */
object ReconnectLogic {
    /** Exponential backoff doubling up to 30s cap. Base defaults to 2000ms if <=0. attempt starts at 1. */
    fun computeReconnectDelay(
        base: Long,
        attempt: Int,
    ): Long {
        val effectiveBase = if (base > 0) base else DEFAULT_RECONNECT_BASE_MS
        val shift = (attempt - 1).coerceAtLeast(0)
        return min(MAX_RECONNECT_DELAY_MS, effectiveBase shl shift)
    }

    /** Whether reconnect should proceed given manualDisconnect and bluetooth adapter enabled state. */
    fun shouldScheduleReconnect(
        manualDisconnect: Boolean,
        btEnabled: Boolean,
    ): Boolean = !manualDisconnect && btEnabled

    /** Bond state action: schedule immediate reconnect when bonded. */
    fun bondStateTriggersReconnect(state: Int): Boolean = state == BluetoothDevice.BOND_BONDED

    /** Minimal preference-like store helpers for last_device to enable pure unit tests. */
    fun saveLastDevice(
        store: MutableMap<String, String>,
        address: String,
    ) {
        store["last_device"] = address
    }

    fun readLastDevice(store: Map<String, String>): String? = store["last_device"]

    /** Compute next delay or null when retries should stop (on success or manual disconnect or BT off). */
    fun nextDelay(
        manualDisconnect: Boolean,
        success: Boolean,
        btEnabled: Boolean,
        base: Long,
        currentAttempt: Int,
    ): Long? {
        if (manualDisconnect || success || !btEnabled) return null
        val nextAttempt = (currentAttempt + 1).coerceAtLeast(1)
        return computeReconnectDelay(base, nextAttempt)
    }
}
