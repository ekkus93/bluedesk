package com.augustusmachin.android_bt_kbmouse

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

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
     * Promote the Classic HID service to the foreground. Failure is authoritative: publish
     * failed startup state, surface a user-visible notification/message, stop the service,
     * and return false so the caller cannot continue initialization.
     */
    fun startInForeground(): Boolean {
        val notif = ServiceNotifications.buildForeground(service, connectAction, disconnectAction, forgetAction)
        return try {
            service.startForeground(1, notif)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            val message = "Classic HID could not enter foreground service state: ${e.message ?: e.javaClass.simpleName}"
            DebugLog.e(TAG, message)
            Log.e(TAG, message, e)
            ClassicHidStartupRegistry.publish(ClassicHidStartupState.Failed(message))
            StoreProvider.dispatch(Action.UpdateMessage(message))
            postRuntimeFailureSafely(message)
            stopSelfSafely("foreground-start failure")
            false
        }
    }

    fun refreshQsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(service, ComponentName(service, HidQuickTileService::class.java))
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // The tile is an optional observer; failure must not alter authoritative HID state.
                Log.w(TAG, "Quick Settings tile refresh failed", e)
            }
        }
    }

    fun reportMissingBluetoothConnect() {
        val msg = "App requires BLUETOOTH_CONNECT permission. Please grant it in Settings."
        DebugLog.e(TAG, msg)
        Log.e(TAG, msg)

        try {
            ServiceNotifications.postMissingPermission(service, msg)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Activity broadcast remains an independent user-visible path.
            Log.e(TAG, "Failed to post missing-permission notification", e)
        }

        try {
            val b = Intent(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT).setPackage(service.packageName)
            service.sendBroadcast(b)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Notification above remains an independent user-visible path.
            Log.e(TAG, "Failed to send missing-permission broadcast", e)
        }

        stopSelfSafely("missing Bluetooth permission")
    }

    private fun postRuntimeFailureSafely(message: String) {
        try {
            ServiceNotifications.postRuntimeFailure(
                service,
                title = "BlueDeck could not start",
                message = message,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Store state and Android system log already contain the authoritative failure.
            Log.e(TAG, "Failed to post foreground-start failure notification", e)
        }
    }

    private fun stopSelfSafely(reason: String) {
        try {
            service.stopSelf()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Teardown failure is diagnostic only; failed startup/permission state is already durable.
            Log.e(TAG, "stopSelf failed during $reason", e)
        }
    }

    private companion object {
        const val TAG = "BluetoothService"
    }
}
