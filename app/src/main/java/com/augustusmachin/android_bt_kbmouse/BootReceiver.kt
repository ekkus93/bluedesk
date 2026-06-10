package com.augustusmachin.android_bt_kbmouse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val SDK_INT_OREO = 26

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
                        DebugLog.e("BootReceiver", "startOnBoot read failed: ${e.message}")
                        false
                    }
                }
            if (start) {
                val svc = Intent(context, BluetoothService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= SDK_INT_OREO) {
                        context.startForegroundService(
                            svc,
                        )
                    } else {
                        context.startService(svc)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}
