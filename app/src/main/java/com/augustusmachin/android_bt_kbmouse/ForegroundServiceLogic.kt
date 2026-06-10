package com.augustusmachin.android_bt_kbmouse

/** Pure helpers for foreground service lifecycle logic to unit test without Android Service instance. */
object ForegroundServiceLogic {
    /** Returns true if foreground start exceeded the 5s guard window (5000ms). */
    fun foregroundStartExceeded(
        startTimestampMs: Long,
        nowMs: Long,
    ): Boolean = (nowMs - startTimestampMs) > 5000L

    /** The service start mode used (START_STICKY). */
    fun startMode(): Int = android.app.Service.START_STICKY

    /** Build notification content text (currently constant in BluetoothService). */
    fun buildNotificationText(connectedName: String?): String = "Tap to manage connection"
}
