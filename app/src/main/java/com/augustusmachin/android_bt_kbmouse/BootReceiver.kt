package com.augustusmachin.android_bt_kbmouse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val start =
                runBlocking {
                    try {
                        SettingsManager.flow(context).first().startOnBoot
                    } catch (e: Exception) {
                        false
                    }
                }
            if (start) {
                val svc = Intent(context, BluetoothService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc) else context.startService(svc)
                } catch (_: Exception) {
                }
            }
        }
    }
}
