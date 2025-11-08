package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import kotlin.math.min

/** Pure helpers for reconnect/backoff logic to enable host JVM unit testing without Android Handler. */
object ReconnectLogic {
    /** Exponential backoff doubling up to 30s cap. Base defaults to 2000ms if <=0. attempt starts at 1. */
    fun computeReconnectDelay(base: Long, attempt: Int): Long {
        val effectiveBase = if (base > 0) base else 2000L
        val shift = (attempt - 1).coerceAtLeast(0)
        return min(30_000L, effectiveBase shl shift)
    }
    /** Whether reconnect should proceed given manualDisconnect and bluetooth adapter enabled state. */
    fun shouldScheduleReconnect(manualDisconnect: Boolean, btEnabled: Boolean): Boolean = !manualDisconnect && btEnabled
    /** Bond state action: schedule immediate reconnect when bonded. */
    fun bondStateTriggersReconnect(state: Int): Boolean = state == BluetoothDevice.BOND_BONDED
}
