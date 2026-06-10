package com.augustusmachin.android_bt_kbmouse

/** What the Quick Settings tile should do when tapped, given current state. */
enum class TileAction { DISCONNECT, CONNECT, OPEN_APP, UNAVAILABLE }

/**
 * Pure policy for the Quick Settings tile. The tile controls the Classic HID backend only,
 * so in BLE mode it is UNAVAILABLE and must not send Classic connect/disconnect broadcasts
 * (which would do nothing). Tile "active" state is reflected from known prefs, never set
 * optimistically just because a connect broadcast was sent.
 */
object QuickTilePolicy {
    fun action(
        bleMode: Boolean,
        connectedName: String?,
        hasLastDevice: Boolean,
    ): TileAction =
        when {
            bleMode -> TileAction.UNAVAILABLE
            connectedName != null -> TileAction.DISCONNECT
            hasLastDevice -> TileAction.CONNECT
            else -> TileAction.OPEN_APP
        }

    /** Whether the tile should render as active (connected). Never optimistic. */
    fun isActive(
        bleMode: Boolean,
        connectedName: String?,
    ): Boolean = !bleMode && connectedName != null
}
