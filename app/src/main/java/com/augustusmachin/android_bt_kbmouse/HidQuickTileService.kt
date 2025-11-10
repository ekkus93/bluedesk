package com.augustusmachin.android_bt_kbmouse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HidQuickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val sp = getSharedPreferences("bt_hid", Context.MODE_PRIVATE)
        val connectedName = sp.getString("connected_name", null)
        if (connectedName != null) {
            // disconnect
            sendBroadcast(Intent("com.augustusmachin.android_bt_kbmouse.ACTION_DISCONNECT").setPackage(packageName))
        } else {
            // connect last or open app
            val hasLast = sp.getString("last_device", null) != null
            if (hasLast) {
                sendBroadcast(Intent("com.augustusmachin.android_bt_kbmouse.ACTION_CONNECT").setPackage(packageName))
            } else {
                // no last device; open app
                val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // startActivityAndCollapse is deprecated on newer platforms; keep behavior but suppress warning
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        }
        // optimistic UI update
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val sp = getSharedPreferences("bt_hid", Context.MODE_PRIVATE)
        val connectedName = sp.getString("connected_name", null)
        if (connectedName != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = connectedName
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
        }
        tile.updateTile()
    }
}
