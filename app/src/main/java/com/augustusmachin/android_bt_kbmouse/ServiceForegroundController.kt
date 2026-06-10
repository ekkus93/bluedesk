package com.augustusmachin.android_bt_kbmouse

import android.app.Service
import android.content.ComponentName
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
    /**
     * Promote the service to the foreground. Returns true on success. On failure we do NOT
     * fall back to a plain notification (that is not a foreground service and can be killed
     * unpredictably) — we log, stop the service, and return false so the caller aborts.
     */
    fun startInForeground(): Boolean {
        val notif = ServiceNotifications.buildForeground(service, connectAction, disconnectAction, forgetAction)
        return try {
            service.startForeground(1, notif)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e(TAG, "startForeground failed: ${e.message}")
            service.stopSelf()
            false
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
