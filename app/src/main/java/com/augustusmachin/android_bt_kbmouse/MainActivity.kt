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
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            // defensive: if the dialog can't show, just finish
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

    // The backend + permissions chosen at startup once settings have loaded.
    private var startupPlan: StartupPermissionPlan? = null

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
                } catch (
                    @Suppress("TooGenericExceptionCaught") t: Throwable,
                ) {
                    // defensive: catches Throwable to keep service binding alive
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
                } catch (
                    @Suppress("TooGenericExceptionCaught") t: Throwable,
                ) {
                    // defensive: catches Throwable to keep service binding alive
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
        // DebugLog enable/level is settings-driven: SettingsViewModel applies the persisted
        // debugLogging preference after settings load. Do not force-enable logging at startup.
        installComposeUi()
        // Defer starting/binding services until the *selected backend's* required runtime
        // permissions are resolved. Settings load first so the request is backend-aware
        // (Classic needs connect; BLE needs connect+advertise).
        val permissionLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                onStartupPermissionResult(result)
            }
        lifecycleScope.launch {
            settingsViewModel.isLoaded.first { it }
            val plan =
                StartupPermissionPlanner.plan(settingsViewModel.settings.value, android.os.Build.VERSION.SDK_INT)
            startupPlan = plan
            val missing =
                plan.requiredPermissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
            if (missing.isEmpty()) {
                startPlannedBackend(plan)
                StartupState.markPermissionFlowResolved()
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }

        // Observe backend-mode changes at runtime and switch services cleanly.
        observeBackendChanges()
    }

    private fun installComposeUi() {
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
    }

    private fun observeBackendChanges() {
        lifecycleScope.launch {
            var prevUseBle: Boolean? = null
            settingsViewModel.settings.collect { s ->
                val useBle = s.useBleHogp
                // Mirror the backend to prefs so the Quick Settings tile (no DataStore access)
                // knows whether Classic HID is active.
                BtDevicePrefs(this@MainActivity).setUseBle(useBle)
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

    private fun startPlannedBackend(plan: StartupPermissionPlan) {
        DebugLog.log("MainActivity", "startPlannedBackend backend=${plan.backend}")
        if (plan.backend == BackendMode.BLE_HOGP) startAndBindBleBackend() else startAndBindClassicBackend()
    }

    /** Result of the startup permission request for the planned backend. */
    private fun onStartupPermissionResult(result: Map<String, Boolean>) {
        val plan = startupPlan
        if (plan == null) {
            StartupState.markPermissionFlowResolved()
            return
        }
        when {
            PermissionPolicy.missingRequired(result, plan.requiredPermissions).isEmpty() -> {
                startPlannedBackend(plan)
                StartupState.markPermissionFlowResolved()
            }
            plan.backend == BackendMode.BLE_HOGP -> handleBleStartupDenied()
            else -> {
                showPermissionNeededDialog()
                StartupState.markPermissionFlowResolved()
            }
        }
    }

    /**
     * Interactive BLE startup denial: persist useBleHogp=false and fall back to Classic if its
     * permission is present, otherwise prompt. (Boot does NOT persist this — see BootReceiver.)
     */
    private fun handleBleStartupDenied() {
        DebugLog.e("MainActivity", "BLE startup permissions denied; falling back to Classic")
        StoreProvider.dispatch(
            Action.UpdateMessage("BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic."),
        )
        lifecycleScope.launch {
            SettingsManager.setUseBleHogp(this@MainActivity, false)
            val classicMissing =
                PermissionPolicy.requiredForClassicStartup(android.os.Build.VERSION.SDK_INT).filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
            if (classicMissing.isEmpty()) startAndBindClassicBackend() else showPermissionNeededDialog()
            StartupState.markPermissionFlowResolved()
        }
    }

    private fun startAndBindClassicBackend() {
        if (serviceBound) return
        val intent = Intent(this, BluetoothService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_OREO) startForegroundService(intent) else startService(intent)
        runCatchingLogged("MainActivity", "bind BluetoothService failed") {
            serviceBound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startAndBindBleBackend() {
        if (bleHogpBound) return
        val intent = Intent(this, BleHogpService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_OREO) startForegroundService(intent) else startService(intent)
        runCatchingLogged("MainActivity", "bind BleHogpService failed") {
            bleHogpBound = bindService(intent, bleHogpConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun currentBackend(): BackendMode? =
        when {
            serviceBound -> BackendMode.CLASSIC_HID
            bleHogpBound -> BackendMode.BLE_HOGP
            else -> null
        }

    /** Stop + unbind the Classic backend so its started/foreground service does not linger. */
    private fun stopClassicBackend() {
        if (serviceBound) {
            try {
                StoreProvider.setKeySender(null)
            } catch (_: Exception) {
            }
            try {
                unbindService(connection)
            } catch (e: IllegalArgumentException) {
                DebugLog.e("MainActivity", "Classic unbind failed: ${e.message}")
            }
            serviceBound = false
        }
        stopService(Intent(this, BluetoothService::class.java))
    }

    /** Stop + unbind the BLE backend so its started/foreground service does not linger. */
    private fun stopBleBackend() {
        if (bleHogpBound) {
            try {
                StoreProvider.setKeySender(null)
            } catch (_: Exception) {
            }
            try {
                unbindService(bleHogpConnection)
            } catch (e: IllegalArgumentException) {
                DebugLog.e("MainActivity", "BLE unbind failed: ${e.message}")
            }
            bleHogpBound = false
        }
        stopService(Intent(this, BleHogpService::class.java))
    }

    private fun switchBackend(useBle: Boolean) {
        val target = BackendSelector.fromSettings(useBle)
        val steps = BackendTransitionPlanner.plan(currentBackend(), target)
        if (steps.isEmpty()) return
        DebugLog.log("MainActivity", "switchBackend → $target")
        // Stop the inactive backend's service before starting the target — never both at once.
        steps.filterIsInstance<BackendStep.Stop>().forEach {
            if (it.mode == BackendMode.CLASSIC_HID) stopClassicBackend() else stopBleBackend()
        }
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage("Switching HID backend…"))
        steps.filterIsInstance<BackendStep.Start>().forEach {
            if (it.mode == BackendMode.BLE_HOGP) startAndBindBleBackend() else startAndBindClassicBackend()
        }
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
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // defensive: finishes activity as fallback
                DebugLog.e("MainActivity", "permission dialog failed: ${e.message}")
                finish()
            }
        }
    }
}
