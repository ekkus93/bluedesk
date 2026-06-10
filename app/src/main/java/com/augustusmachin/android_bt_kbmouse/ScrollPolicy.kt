package com.augustusmachin.android_bt_kbmouse

/**
 * Whether mouse scroll is available given the current HID descriptor mode.
 *
 * The SIMPLE descriptor has no scroll wheels, so scroll is unavailable. The FULL descriptor
 * supports vertical scroll always, and horizontal scroll only when the user enabled it.
 *
 * Centralized so MouseScreen's UI copy and gesture dispatch agree. (HidReportSender also
 * no-ops scroll reports in SIMPLE mode as defense in depth.)
 */
object ScrollPolicy {
    fun verticalAvailable(settings: Settings): Boolean = !settings.hidSimplified

    fun horizontalAvailable(settings: Settings): Boolean = !settings.hidSimplified && settings.enableHorizontalScroll
}
