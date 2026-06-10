package com.augustusmachin.android_bt_kbmouse

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

private const val SDK_INT_MARSHMALLOW = 23

class HidQuickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = BtDevicePrefs(this)
        when (QuickTilePolicy.action(prefs.getUseBle(), prefs.getConnectedName(), prefs.getLastDevice() != null)) {
            TileAction.DISCONNECT -> sendHidBroadcast("com.augustusmachin.android_bt_kbmouse.ACTION_DISCONNECT")
            TileAction.CONNECT -> sendHidBroadcast("com.augustusmachin.android_bt_kbmouse.ACTION_CONNECT")
            TileAction.OPEN_APP -> openApp()
            // BLE mode: tile controls Classic HID only, so it does nothing (and renders unavailable).
            TileAction.UNAVAILABLE -> Unit
        }
        // Reflect known state only — do NOT optimistically mark the tile active.
        updateTile()
    }

    private fun sendHidBroadcast(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= SDK_INT_MARSHMALLOW) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 0, i, flags)
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = BtDevicePrefs(this)
        val bleMode = prefs.getUseBle()
        val connectedName = prefs.getConnectedName()
        when {
            bleMode -> {
                // Tile controls Classic HID only; show it disabled in BLE mode.
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.app_name)
            }
            QuickTilePolicy.isActive(bleMode, connectedName) -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = connectedName
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.app_name)
            }
        }
        tile.updateTile()
    }
}
