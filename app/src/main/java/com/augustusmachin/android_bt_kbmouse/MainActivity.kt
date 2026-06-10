package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.ServiceAliasHelper
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.ui.theme.AndroidbtkbmouseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val LED_CAPS_LOCK = 0x02
private const val LED_SCROLL_LOCK = 0x04
private const val SDK_INT_OREO = 26

class MainActivity : ComponentActivity() {
    private companion object {
        // How long the branded BlueDeck launch screen stays visible, in ms. Tune to taste.
        const val SPLASH_DISPLAY_MS = 1800L
    }

    private val permReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action ==
                    com.augustusmachin.android_bt_kbmouse.BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT
                ) {
                    // Show a dialog on UI thread and finish gracefully
                    runOnUiThread {
                        try {
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Missing permission")
                                .setMessage(
                                    "This app requires the BLUETOOTH_CONNECT permission. " +
                                        "Please grant it in Settings. The app will now exit.",
                                )
                                .setPositiveButton("Open Settings") { _, _ ->
                                    val i =
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        )
                                    startActivity(i)
                                    finish()
                                }
                                .setNegativeButton("Close") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        } catch (e: Exception) {
                            // As a fallback, just finish
                            DebugLog.e("MainActivity", "missing-perm dialog failed: ${e.message}")
                            finish()
                        }
                    }
                }
            }
        }

    // Pairing view model removed in production: UI reads/writes canonical state via StoreProvider

    private var serviceBound = false
    private var bleHogpBound = false

    // Settings are collected in SettingsViewModel which performs global side-effects (DebugLog)
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                val binder = service as BluetoothService.LocalBinder
                val svc = binder.getService()
                // Expose alias helpers to UI/tests via a small helper; MainActivity remains the
                // owner of service event wiring and of installing the KeySender into StoreProvider.
                ServiceAliasHelper.setService(svc)
                try {
                    StoreProvider.setKeySender(com.augustusmachin.android_bt_kbmouse.store.BluetoothKeySender(svc))
                } catch (t: Throwable) {
                    DebugLog.e("MainActivity", "setKeySender failed: ${t.message}")
                }

                // Initialize default device address in store from persisted service state
                try {
                    val last = svc.getLastDeviceAddress()
                    StoreProvider.dispatch(Action.UpdateDefaultDevice(last))
                } catch (_: Exception) {
                }

                // Install service event listener here and dispatch canonical store updates
                try {
                    svc.setEventListener(
                        object : BluetoothService.ServiceEventListener {
                            override fun onConnected(device: BluetoothDevice) {
                                DebugLog.log("MainActivity", "onConnected ${device.address}")
                                StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                                // Avoid reading device.name here to prevent BLUETOOTH_CONNECT permission lint
                                // in non-UI contexts; use address for message
                                StoreProvider.dispatch(Action.UpdateMessage("Connected to ${device.address}"))
                            }

                            override fun onDisconnected(device: BluetoothDevice?) {
                                DebugLog.log("MainActivity", "onDisconnected")
                                StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                                StoreProvider.dispatch(Action.UpdateMessage("Disconnected"))
                            }

                            override fun onInfo(message: String) {
                                DebugLog.log("MainActivity", "info: $message")
                                StoreProvider.dispatch(Action.UpdateMessage(message))
                            }

                            override fun onError(message: String) {
                                DebugLog.e("MainActivity", message)
                                StoreProvider.dispatch(Action.UpdateMessage(message))
                            }

                            override fun onLeds(leds: Int) {
                                val caps = (leds and LED_CAPS_LOCK) != 0
                                val scroll = (leds and LED_SCROLL_LOCK) != 0
                                StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
                            }
                        },
                    )
                } catch (_: Exception) {
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // Unregister the KeySender since the service is gone
                try {
                    StoreProvider.setKeySender(null)
                } catch (_: Exception) {
                }
            }
        }

    private val bleHogpConnection =
        object : android.content.ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                val svc = (service as BleHogpService.LocalBinder).getService()
                if (!settingsViewModel.settings.value.useBleHogp) return
                // useBleHogp is enabled — install BleHogpKeySender and wire store events
                try {
                    StoreProvider.setKeySender(com.augustusmachin.android_bt_kbmouse.store.BleHogpKeySender(svc))
                } catch (t: Throwable) {
                    DebugLog.e("MainActivity", "BleHogp setKeySender failed: ${t.message}")
                }
                svc.eventListener =
                    object : BleHogpService.ServiceEventListener {
                        override fun onConnected(device: BluetoothDevice) {
                            DebugLog.log("MainActivity", "BleHogp onConnected ${device.address}")
                            StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                            StoreProvider.dispatch(Action.UpdateMessage("BLE connected to ${device.address}"))
                        }

                        override fun onDisconnected(device: BluetoothDevice?) {
                            DebugLog.log("MainActivity", "BleHogp onDisconnected")
                            StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                            StoreProvider.dispatch(Action.UpdateMessage("BLE disconnected"))
                        }

                        override fun onInfo(message: String) {
                            DebugLog.log("MainActivity", "BleHogp info: $message")
                            StoreProvider.dispatch(Action.UpdateMessage(message))
                        }

                        override fun onError(message: String) {
                            DebugLog.e("MainActivity", "BleHogp error: $message")
                            StoreProvider.dispatch(Action.UpdateMessage(message))
                        }

                        override fun onLeds(leds: Int) {
                            val caps = (leds and LED_CAPS_LOCK) != 0
                            val scroll = (leds and LED_SCROLL_LOCK) != 0
                            StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
                        }
                    }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (settingsViewModel.settings.value.useBleHogp) {
                    try {
                        StoreProvider.setKeySender(null)
                    } catch (_: Exception) {
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the system splash (a circular icon) for the cold-start window,
        // then let it hand off immediately to our branded Compose launch screen
        // (BlueDeckSplash), which is what shows the name + tagline.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Register receiver for permission-error reports from services
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                permReceiver,
                IntentFilter(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
        }
        // SettingsViewModel drives DebugLog enable/level after persisted settings load.
        // Keep a minimal startup log enabled until settings arrive.
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.ERROR)
        setContent {
            AndroidbtkbmouseTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_DISPLAY_MS)
                    showSplash = false
                }
                Box(Modifier.fillMaxSize()) {
                    MainScreen()
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(durationMillis = 450)),
                    ) {
                        BlueDeckSplash()
                    }
                }
            }
        }
        // Defer starting/binding services until required runtime permissions are granted.
        val permissionLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                // Only block startup if a REQUIRED Classic HID permission was denied.
                // POST_NOTIFICATIONS and BLUETOOTH_ADVERTISE denials are non-fatal.
                if (PermissionPolicy.isClassicStartupBlocked(result, android.os.Build.VERSION.SDK_INT)) {
                    showPermissionNeededDialog()
                } else {
                    startServicesAndBind()
                }
            }

        val missing =
            requiredStartupPermissions().filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            startServicesAndBind()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }

        // Observe backend-mode changes at runtime and switch services cleanly.
        lifecycleScope.launch {
            var prevUseBle: Boolean? = null
            settingsViewModel.settings.collect { s ->
                val useBle = s.useBleHogp
                if (prevUseBle != null && useBle != prevUseBle) {
                    switchBackend(useBle)
                }
                prevUseBle = useBle
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(permReceiver)
        } catch (_: Exception) {
        }
        if (serviceBound) {
            try {
                StoreProvider.setKeySender(null)
            } catch (_: Exception) {
            }
            unbindService(connection)
            serviceBound = false
        }
        if (bleHogpBound) {
            try {
                unbindService(bleHogpConnection)
            } catch (_: Exception) {
            }
            bleHogpBound = false
        }
    }

    private fun requiredStartupPermissions(): Array<String> =
        PermissionPolicy.startupPermissions(android.os.Build.VERSION.SDK_INT).toTypedArray()

    private fun startServicesAndBind() {
        lifecycleScope.launch {
            // Wait for persisted settings before choosing the HID backend (Phase 3).
            settingsViewModel.isLoaded.first { it }
            val useBle = settingsViewModel.settings.value.useBleHogp
            DebugLog.log("MainActivity", "startServicesAndBind backend=${BackendSelector.fromSettings(useBle)}")
            if (useBle) startAndBindBleBackend() else startAndBindClassicBackend()
        }
    }

    private fun startAndBindClassicBackend() {
        val intent = Intent(this, BluetoothService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_OREO) startForegroundService(intent) else startService(intent)
        try {
            serviceBound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            DebugLog.e("MainActivity", "bind BluetoothService failed: ${e.message}")
        }
    }

    private fun startAndBindBleBackend() {
        val intent = Intent(this, BleHogpService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_OREO) startForegroundService(intent) else startService(intent)
        try {
            bleHogpBound = bindService(intent, bleHogpConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            DebugLog.e("MainActivity", "bind BleHogpService failed: ${e.message}")
        }
    }

    private fun switchBackend(useBle: Boolean) {
        DebugLog.log("MainActivity", "switchBackend → ${BackendSelector.fromSettings(useBle)}")
        if (serviceBound) {
            try {
                StoreProvider.setKeySender(null)
            } catch (_: Exception) {
            }
            try {
                unbindService(connection)
            } catch (_: Exception) {
            }
            serviceBound = false
        }
        if (bleHogpBound) {
            try {
                StoreProvider.setKeySender(null)
            } catch (_: Exception) {
            }
            try {
                unbindService(bleHogpConnection)
            } catch (_: Exception) {
            }
            bleHogpBound = false
        }
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage("Switching HID backend…"))
        if (useBle) startAndBindBleBackend() else startAndBindClassicBackend()
    }

    private fun showPermissionNeededDialog() {
        runOnUiThread {
            try {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Permissions required")
                    .setMessage(
                        "This app needs Bluetooth permissions to operate. " +
                            "Open App Settings to grant permissions or close the app.",
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        val i =
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            )
                        startActivity(i)
                        finish()
                    }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                DebugLog.e("MainActivity", "permission dialog failed: ${e.message}")
                finish()
            }
        }
    }
}
