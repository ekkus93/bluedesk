package com.augustusmachin.android_bt_kbmouse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds the notifications used by [BluetoothService]: the ongoing
 * foreground-service notification and the "missing permission" alert.
 * Kept out of the service class to keep that file focused on HID logic.
 */
object ServiceNotifications {
    private const val FGS_CHANNEL_ID = "bt_hid_service"
    private const val ERROR_CHANNEL_ID = "bt_hid_error"

    /** Ongoing foreground-service notification (creates its channel if needed). */
    fun buildForeground(
        service: Service,
        connectAction: String,
        disconnectAction: String,
        forgetAction: String,
    ): Notification {
        val mgr = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && mgr.getNotificationChannel(FGS_CHANNEL_ID) == null) {
            val ch = NotificationChannel(FGS_CHANNEL_ID, "Bluetooth HID Service", NotificationManager.IMPORTANCE_LOW)
            ch.enableLights(false)
            ch.enableVibration(false)
            mgr.createNotificationChannel(ch)
        }
        val pkg = service.packageName
        val pi = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java), pendingFlags())
        val connectPi = PendingIntent.getBroadcast(service, 1, Intent(connectAction).setPackage(pkg), pendingFlags())
        val disconnectPi = PendingIntent.getBroadcast(service, 2, Intent(disconnectAction).setPackage(pkg), pendingFlags())
        val forgetPi = PendingIntent.getBroadcast(service, 3, Intent(forgetAction).setPackage(pkg), pendingFlags())
        return NotificationCompat.Builder(service, FGS_CHANNEL_ID)
            .setContentTitle("BlueDeck running")
            .setContentText("Tap to manage connection")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_bluetooth, "Connect", connectPi)
            .addAction(R.drawable.ic_bluetooth, "Disconnect", disconnectPi)
            .addAction(R.drawable.ic_settings, "Forget", forgetPi)
            .setOngoing(true)
            .build()
    }

    /** High-priority notification prompting the user to grant BLUETOOTH_CONNECT. */
    fun postMissingPermission(context: Context, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(ERROR_CHANNEL_ID) == null) {
            val ch = NotificationChannel(ERROR_CHANNEL_ID, "Bluetooth HID errors", NotificationManager.IMPORTANCE_HIGH)
            ch.enableLights(true)
            ch.lightColor = Color.RED
            nm.createNotificationChannel(ch)
        }
        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 0, settingsIntent, pendingFlags())
        val notif = NotificationCompat.Builder(context, ERROR_CHANNEL_ID)
            .setContentTitle("Bluetooth permission required")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2, notif)
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
}
