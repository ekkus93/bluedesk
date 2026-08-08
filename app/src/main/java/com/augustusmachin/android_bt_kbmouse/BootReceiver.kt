package com.augustusmachin.android_bt_kbmouse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SDK_INT_OREO = 26
private const val BOOT_TIMEOUT_MS = 3_000L

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return
        // Read settings off the main thread without blocking the receiver indefinitely.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(BOOT_TIMEOUT_MS) {
                    val settings = readSettings(context) ?: return@withTimeoutOrNull
                    val sdk = Build.VERSION.SDK_INT
                    val decision =
                        BootStartPlanner.plan(
                            startOnBoot = settings.startOnBoot,
                            useBleHogp = settings.useBleHogp,
                            hasClassicPermissions =
                                hasAll(context, PermissionPolicy.requiredForClassicStartup(sdk)),
                            hasBlePermissions = hasAll(context, PermissionPolicy.requiredForBleStartup(sdk)),
                        )
                    applyDecision(context, decision)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun readSettings(context: Context): Settings? =
        try {
            SettingsManager.flow(context).first()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            reportBootFailure(context, "Could not read settings during start-on-boot: ${e.message}")
            null
        }

    private fun applyDecision(
        context: Context,
        decision: BootStartDecision,
    ) {
        when (decision) {
            BootStartDecision.StartNothing -> Unit
            BootStartDecision.StartClassic -> startBackend(context, BluetoothService::class.java)
            BootStartDecision.StartBle -> startBackend(context, BleHogpService::class.java)
            is BootStartDecision.Skip -> reportBootFailure(context, decision.reason)
        }
    }

    private fun hasAll(
        context: Context,
        permissions: List<String>,
    ): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startBackend(
        context: Context,
        cls: Class<*>,
    ) {
        val svc = Intent(context, cls)
        try {
            if (Build.VERSION.SDK_INT >= SDK_INT_OREO) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            reportBootFailure(context, "Could not start ${cls.simpleName} during start-on-boot: ${e.message}")
        }
    }

    private fun reportBootFailure(
        context: Context,
        message: String,
    ) {
        BtDevicePrefs(context).setLastRuntimeFailure(message)
        DebugLog.e("BootReceiver", message)
        Log.e("BootReceiver", message)
        try {
            ServiceNotifications.postRuntimeFailure(
                context,
                title = "BlueDeck could not start",
                message = message,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Notification permission/framework failure must not erase the durable record or system log.
            Log.e("BootReceiver", "Could not post startup-failure notification", e)
        }
    }
}
