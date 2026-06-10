package com.augustusmachin.android_bt_kbmouse

import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Owns the foreground-service notification, the quick-settings tile refresh, and the
 * missing-permission alert/teardown for [BluetoothService]. These all wrap Service/Context
 * APIs (startForeground, stopSelf, sendBroadcast, TileService), so the controller holds the
 * [service] reference. Extracted to keep BluetoothService focused on HID logic.
 */
class ServiceForegroundController(
    private val service: Service,
    private val connectAction: String,
    private val disconnectAction: String,
    private val forgetAction: String,
) {
    fun startInForeground() {
        val mgr = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = ServiceNotifications.buildForeground(service, connectAction, disconnectAction, forgetAction)
        // Use the two-argument startForeground where possible. On some platform builds the
        // system may still enforce foreground-service types; guard against that so the
        // service doesn't crash the app.
        try {
            service.startForeground(1, notif)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // defensive: fall back to a plain notification so the process isn't killed during
            // startup on restrictive platform builds (accepting it may not be a true FGS).
            DebugLog.e(TAG, "startForeground failed: ${e.message}")
            mgr.notify(1, notif)
        }
    }

    fun refreshQsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(service, ComponentName(service, HidQuickTileService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    fun reportMissingBluetoothConnect() {
        val msg = "App requires BLUETOOTH_CONNECT permission. Please grant it in Settings."
        DebugLog.e(TAG, msg)
        // Post a user-visible notification with a shortcut to app settings
        runCatchingLogged(TAG, "failed to post settings notification") {
            ServiceNotifications.postMissingPermission(service, msg)
        }
        // Broadcast so the Activity can show UI and exit gracefully
        runCatchingLogged(TAG, "failed to send missing-perm broadcast") {
            val b = Intent(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT).setPackage(service.packageName)
            service.sendBroadcast(b)
        }
        // Stop the service gracefully
        try {
            service.stopSelf()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
