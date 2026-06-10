package com.augustusmachin.android_bt_kbmouse

private const val FOREGROUND_START_GUARD_MS = 5000L

/** Pure helpers for foreground service lifecycle logic to unit test without Android Service instance. */
object ForegroundServiceLogic {
    /** Returns true if foreground start exceeded the 5s guard window (5000ms). */
    fun foregroundStartExceeded(
        startTimestampMs: Long,
        nowMs: Long,
    ): Boolean = (nowMs - startTimestampMs) > FOREGROUND_START_GUARD_MS

    /** The service start mode used (START_STICKY). */
    fun startMode(): Int = android.app.Service.START_STICKY

    /** Notification content text: shows the connected device when present. */
    fun buildNotificationText(connectedName: String?): String =
        if (connectedName.isNullOrBlank()) {
            "Tap to manage connection"
        } else {
            "Connected to $connectedName"
        }
}
