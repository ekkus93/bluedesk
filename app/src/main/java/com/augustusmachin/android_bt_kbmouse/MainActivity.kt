package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.BleHogpKeySender
import com.augustusmachin.android_bt_kbmouse.store.BluetoothKeySender
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
        const val SPLASH_DISPLAY_MS = 1800L
    }

    private val permReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT) {
                    runOnUiThread {
                        try {
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Missing permission")
                                .setMessage(
                                    "This app requires Bluetooth connection permission. " +
                                        "Please grant it in Settings. The app will now exit.",
                                )
                                .setPositiveButton("Open Settings") { _, _ ->
                                    val settingsIntent =
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        )
                                    startActivity(settingsIntent)
                                    finish()
                                }
                                .setNegativeButton("Close") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            DebugLog.e("MainActivity", "missing-permission dialog failed: ${e.message}")
                            finish()
                        }
                    }
                }
            }
        }

    private var serviceBound = false
    private var bleHogpBound = false
    private var classicService: BluetoothService? = null
    private var bleService: BleHogpService? = null
    private var startupPlan: StartupPermissionPlan? = null

    private val runtimeCoordinator = BackendRuntimeCoordinator()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val backendLifecycle: BackendLifecycleController by lazy { createBackendLifecycleController() }

    private val connection =
        object : ServiceConnection {
            // Binding is a fail-closed startup transaction; each rejected stage stops immediately.
            @Suppress("LongMethod", "ReturnCount")
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                val svc = (service as BluetoothService.LocalBinder).getService()
                classicService = svc
                if (runtimeCoordinator.currentLiveBackend != BackendMode.CLASSIC_HID) {
                    backendLifecycle.failInitialization(
                        BackendMode.CLASSIC_HID,
                        "Classic service bound outside the active startup transaction",
                    )
                    return
                }
                if (!backendLifecycle.beginListenerInstallation(BackendMode.CLASSIC_HID)) {
                    backendLifecycle.failInitialization(BackendMode.CLASSIC_HID, "Classic listener stage was rejected")
                    return
                }

                ServiceAliasHelper.setService(svc)
                val lastAddress =
                    try {
                        svc.getLastDeviceAddress()
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        DebugLog.e("MainActivity", "Reading default Classic device failed: ${e.message}")
                        StoreProvider.dispatch(Action.UpdateMessage("Could not read the remembered Bluetooth device."))
                        null
                    }
                StoreProvider.dispatch(Action.UpdateDefaultDevice(lastAddress))

                try {
                    svc.setEventListener(classicEventListener())
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    backendLifecycle.failInitialization(
                        BackendMode.CLASSIC_HID,
                        "Installing Classic service listener failed: ${e.message}",
                    )
                    return
                }
                if (!backendLifecycle.listenerInstalled(BackendMode.CLASSIC_HID)) {
                    backendLifecycle.failInitialization(BackendMode.CLASSIC_HID, "Classic sender stage was rejected")
                    return
                }
                try {
                    StoreProvider.setKeySender(BluetoothKeySender(svc))
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    backendLifecycle.failInitialization(
                        BackendMode.CLASSIC_HID,
                        "Installing Classic command sender failed: ${e.message}",
                    )
                    return
                }
                if (!backendLifecycle.senderInstalled(BackendMode.CLASSIC_HID)) {
                    backendLifecycle.failInitialization(
                        BackendMode.CLASSIC_HID,
                        "Classic backend-init stage was rejected",
                    )
                    return
                }
                restoreClassicHostSnapshot(svc)
                reconcileClassicReadiness(svc.getStartupState())
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceBound = false
                classicService = null
                ServiceAliasHelper.setService(null)
                backendLifecycle.unexpectedServiceLoss(BackendMode.CLASSIC_HID)
            }

            override fun onBindingDied(name: ComponentName) {
                serviceBound = false
                classicService = null
                ServiceAliasHelper.setService(null)
                backendLifecycle.unexpectedServiceLoss(BackendMode.CLASSIC_HID)
            }

            override fun onNullBinding(name: ComponentName) {
                serviceBound = false
                backendLifecycle.failInitialization(BackendMode.CLASSIC_HID, "Classic service returned a null binding")
            }
        }

    private val bleHogpConnection =
        object : ServiceConnection {
            // Binding is a fail-closed startup transaction; each rejected stage stops immediately.
            @Suppress("LongMethod", "ReturnCount")
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                val svc = (service as BleHogpService.LocalBinder).getService()
                bleService = svc
                if (runtimeCoordinator.currentLiveBackend != BackendMode.BLE_HOGP) {
                    backendLifecycle.failInitialization(
                        BackendMode.BLE_HOGP,
                        "BLE service bound outside the active startup transaction",
                    )
                    return
                }
                if (!backendLifecycle.beginListenerInstallation(BackendMode.BLE_HOGP)) {
                    backendLifecycle.failInitialization(BackendMode.BLE_HOGP, "BLE listener stage was rejected")
                    return
                }

                svc.eventListener = bleEventListener()
                if (!backendLifecycle.listenerInstalled(BackendMode.BLE_HOGP)) {
                    backendLifecycle.failInitialization(BackendMode.BLE_HOGP, "BLE sender stage was rejected")
                    return
                }
                try {
                    StoreProvider.setKeySender(BleHogpKeySender(svc))
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    backendLifecycle.failInitialization(
                        BackendMode.BLE_HOGP,
                        "Installing BLE command sender failed: ${e.message}",
                    )
                    return
                }
                if (!backendLifecycle.senderInstalled(BackendMode.BLE_HOGP)) {
                    backendLifecycle.failInitialization(BackendMode.BLE_HOGP, "BLE backend-init stage was rejected")
                    return
                }
                reconcileBleReadiness(svc.currentStartupState())
            }

            override fun onServiceDisconnected(name: ComponentName) {
                bleHogpBound = false
                bleService = null
                backendLifecycle.unexpectedServiceLoss(BackendMode.BLE_HOGP)
            }

            override fun onBindingDied(name: ComponentName) {
                bleHogpBound = false
                bleService = null
                backendLifecycle.unexpectedServiceLoss(BackendMode.BLE_HOGP)
            }

            override fun onNullBinding(name: ComponentName) {
                bleHogpBound = false
                backendLifecycle.failInitialization(BackendMode.BLE_HOGP, "BLE service returned a null binding")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                permReceiver,
                IntentFilter(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e("MainActivity", "Permission receiver registration failed: ${e.message}")
            StoreProvider.dispatch(Action.UpdateMessage("Permission error reporting could not be initialized."))
        }

        installComposeUi()
        val permissionLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                onStartupPermissionResult()
            }
        lifecycleScope.launch {
            settingsViewModel.isLoaded.first { it }
            val plan = StartupPermissionPlanner.plan(settingsViewModel.settings.value, android.os.Build.VERSION.SDK_INT)
            startupPlan = plan
            val missing = PermissionGrantChecker.missing(this@MainActivity, plan.requiredPermissions)
            if (missing.isEmpty()) {
                startPlannedBackend(plan)
                StartupState.markPermissionFlowResolved()
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }

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
            settingsViewModel.settings.collect { settings ->
                val useBle = settings.useBleHogp
                BtDevicePrefs(this@MainActivity).setUseBle(useBle)
                if (prevUseBle != null && useBle != prevUseBle) switchBackend(useBle)
                prevUseBle = useBle
            }
        }
    }

    override fun onDestroy() {
        detachActivityFromBackend()
        try {
            unregisterReceiver(permReceiver)
        } catch (e: IllegalArgumentException) {
            DebugLog.e("MainActivity", "Permission receiver was already unregistered: ${e.message}")
        }
        super.onDestroy()
    }

    private fun detachActivityFromBackend() {
        StoreProvider.currentKeySender()?.let {
            StoreProvider.dispatch(Action.MouseButtonUp)
            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
        }
        StoreProvider.setKeySender(null)
        try {
            classicService?.setEventListener(null)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e("MainActivity", "Detaching Classic listener failed: ${e.message}")
        }
        bleService?.eventListener = null
        ServiceAliasHelper.setService(null)

        if (serviceBound) {
            try {
                unbindService(connection)
            } catch (e: IllegalArgumentException) {
                DebugLog.e("MainActivity", "Classic Activity binding was already detached: ${e.message}")
            }
            serviceBound = false
        }
        if (bleHogpBound) {
            try {
                unbindService(bleHogpConnection)
            } catch (e: IllegalArgumentException) {
                DebugLog.e("MainActivity", "BLE Activity binding was already detached: ${e.message}")
            }
            bleHogpBound = false
        }
        classicService = null
        bleService = null
        // Intentionally do not stop the foreground service and do not clear connected-host state.
        // A recreated Activity starts a fresh transaction, rebinds, and reconciles durable readiness.
    }

    private fun startPlannedBackend(plan: StartupPermissionPlan) {
        DebugLog.log("MainActivity", "startPlannedBackend backend=${plan.backend}")
        StoreProvider.dispatch(Action.UpdateSelectedBackend(plan.backend))
        backendLifecycle.start(plan.backend)
    }

    private fun onStartupPermissionResult() {
        val plan = startupPlan
        if (plan == null) {
            StartupState.markPermissionFlowResolved()
            return
        }
        val granted = PermissionGrantChecker.grantedSet(this, plan.requiredPermissions)
        when (StartupPermissionDecision.decide(plan, granted)) {
            StartupDecision.StartPlannedBackend -> {
                startPlannedBackend(plan)
                StartupState.markPermissionFlowResolved()
            }
            StartupDecision.FallbackBleToClassic -> handleBleStartupDenied()
            StartupDecision.ShowClassicPermissionDenied -> {
                StoreProvider.dispatch(
                    Action.UpdateMessage("Bluetooth permission is required before the HID backend can start."),
                )
                showPermissionNeededDialog()
                StartupState.markPermissionFlowResolved()
            }
        }
    }

    private fun handleBleStartupDenied() {
        DebugLog.e("MainActivity", "BLE startup permissions denied; staying on Classic")
        StoreProvider.dispatch(
            Action.UpdateMessage("BLE HOGP needs Bluetooth connect/advertise permissions; staying on Classic."),
        )
        lifecycleScope.launch {
            SettingsManager.setUseBleHogp(this@MainActivity, false)
            val classicPerms = PermissionPolicy.requiredForClassicStartup(android.os.Build.VERSION.SDK_INT)
            if (PermissionGrantChecker.hasAll(this@MainActivity, classicPerms)) {
                StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.CLASSIC_HID))
                backendLifecycle.start(BackendMode.CLASSIC_HID)
            } else {
                showPermissionNeededDialog()
            }
            StartupState.markPermissionFlowResolved()
        }
    }

    private fun currentBackend(): BackendMode? = runtimeCoordinator.currentLiveBackend

    private fun switchBackend(useBle: Boolean) {
        val target = BackendSelector.fromSettings(useBle)
        if (currentBackend() == target && runtimeCoordinator.state is BackendRuntimeState.Ready) return
        val required =
            when (target) {
                BackendMode.CLASSIC_HID -> PermissionPolicy.requiredForClassicStartup(android.os.Build.VERSION.SDK_INT)
                BackendMode.BLE_HOGP -> PermissionPolicy.requiredForBleStartup(android.os.Build.VERSION.SDK_INT)
            }
        val missing = PermissionGrantChecker.missing(this, required)
        if (missing.isNotEmpty()) {
            val source = currentBackend()
            val message = "Cannot switch to $target because required Bluetooth permission is unavailable"
            DebugLog.e("MainActivity", message)
            StoreProvider.dispatch(Action.UpdateMessage(message))
            if (source != null) {
                lifecycleScope.launch {
                    SettingsManager.setUseBleHogp(this@MainActivity, source == BackendMode.BLE_HOGP)
                }
            }
            return
        }

        DebugLog.log("MainActivity", "switchBackend → $target")
        StoreProvider.dispatch(Action.UpdateMessage("Switching HID backend…"))
        StoreProvider.dispatch(Action.UpdateSelectedBackend(target))
        backendLifecycle.switchTo(target)
    }

    private fun createBackendLifecycleController(): BackendLifecycleController =
        BackendLifecycleController(
            runtimeCoordinator,
            object : BackendLifecycleOperations {
                override fun startService(mode: BackendMode): LifecycleOperationResult {
                    val intent = serviceIntent(mode)
                    return try {
                        val component =
                            if (android.os.Build.VERSION.SDK_INT >= SDK_INT_OREO) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        if (component != null) {
                            LifecycleOperationResult.Success
                        } else {
                            LifecycleOperationResult.Failure("Android did not start the $mode service")
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        LifecycleOperationResult.Failure("Starting $mode service failed: ${e.message}")
                    }
                }

                override fun bindService(mode: BackendMode): LifecycleOperationResult {
                    return try {
                        val bound =
                            when (mode) {
                                BackendMode.CLASSIC_HID ->
                                    bindService(serviceIntent(mode), connection, Context.BIND_AUTO_CREATE).also {
                                        serviceBound = it
                                    }
                                BackendMode.BLE_HOGP ->
                                    bindService(serviceIntent(mode), bleHogpConnection, Context.BIND_AUTO_CREATE).also {
                                        bleHogpBound = it
                                    }
                            }
                        if (bound) {
                            LifecycleOperationResult.Success
                        } else {
                            LifecycleOperationResult.Failure(
                                "Binding $mode returned false",
                            )
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        when (mode) {
                            BackendMode.CLASSIC_HID -> serviceBound = false
                            BackendMode.BLE_HOGP -> bleHogpBound = false
                        }
                        LifecycleOperationResult.Failure("Binding $mode failed: ${e.message}")
                    }
                }

                override fun releaseHeldInput(mode: BackendMode) {
                    if (StoreProvider.currentKeySender()?.backend == mode) {
                        StoreProvider.dispatch(Action.MouseButtonUp)
                        StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                    }
                }

                override fun clearSenderAndListener(mode: BackendMode) {
                    if (StoreProvider.currentKeySender()?.backend == mode) StoreProvider.setKeySender(null)
                    when (mode) {
                        BackendMode.CLASSIC_HID -> {
                            try {
                                classicService?.setEventListener(null)
                            } catch (
                                @Suppress("TooGenericExceptionCaught") e: Exception,
                            ) {
                                DebugLog.e("MainActivity", "Clearing Classic listener failed: ${e.message}")
                            }
                            ServiceAliasHelper.setService(null)
                            classicService = null
                        }
                        BackendMode.BLE_HOGP -> {
                            bleService?.eventListener = null
                            bleService = null
                        }
                    }
                }

                override fun unbindService(mode: BackendMode): LifecycleOperationResult {
                    val isBound = if (mode == BackendMode.CLASSIC_HID) serviceBound else bleHogpBound
                    if (!isBound) return LifecycleOperationResult.Success
                    return try {
                        if (mode == BackendMode.CLASSIC_HID) {
                            unbindService(connection)
                            serviceBound = false
                        } else {
                            unbindService(bleHogpConnection)
                            bleHogpBound = false
                        }
                        LifecycleOperationResult.Success
                    } catch (e: IllegalArgumentException) {
                        LifecycleOperationResult.Failure("Unbinding $mode failed: ${e.message}")
                    }
                }

                override fun stopService(mode: BackendMode): LifecycleOperationResult =
                    try {
                        if (stopService(serviceIntent(mode))) {
                            LifecycleOperationResult.Success
                        } else {
                            LifecycleOperationResult.Failure("Android reported that the $mode service was not running")
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        LifecycleOperationResult.Failure("Stopping $mode failed: ${e.message}")
                    }

                override fun resetLocalState() {
                    StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(null))
                    StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(null))
                    StoreProvider.dispatch(Action.UpdateIsScanning(false))
                    StoreProvider.dispatch(Action.UpdateLocks(false, false))
                }
            },
            publish = { StoreProvider.dispatch(Action.UpdateBackendRuntime(it)) },
            surfaceFailure = { message ->
                DebugLog.e("MainActivity", message)
                StoreProvider.dispatch(Action.UpdateMessage(message))
            },
        )

    private fun serviceIntent(mode: BackendMode): Intent =
        when (mode) {
            BackendMode.CLASSIC_HID -> Intent(this, BluetoothService::class.java)
            BackendMode.BLE_HOGP -> Intent(this, BleHogpService::class.java)
        }

    private fun restoreClassicHostSnapshot(service: BluetoothService) {
        val device =
            try {
                service.getConnectedDevice()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("MainActivity", "Reading Classic host snapshot failed: ${e.message}")
                null
            }
        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
        val label =
            try {
                service.getConnectedDeviceLabel()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("MainActivity", "Reading Classic host label failed: ${e.message}")
                null
            }
        StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(label))
    }

    private fun reconcileClassicReadiness(state: ClassicHidStartupState) {
        when (state) {
            ClassicHidStartupState.Ready -> backendLifecycle.markReady(BackendMode.CLASSIC_HID)
            is ClassicHidStartupState.Failed ->
                backendLifecycle.failInitialization(
                    BackendMode.CLASSIC_HID,
                    state.message,
                )
            ClassicHidStartupState.WaitingForRegisterRequest,
            ClassicHidStartupState.WaitingForRegistrationCallback,
            -> Unit
        }
    }

    private fun reconcileBleReadiness(state: BleHogpStartupState) {
        when (state) {
            BleHogpStartupState.Ready -> backendLifecycle.markReady(BackendMode.BLE_HOGP)
            is BleHogpStartupState.Failed -> backendLifecycle.failInitialization(BackendMode.BLE_HOGP, state.message)
            is BleHogpStartupState.Starting -> Unit
        }
    }

    private fun classicEventListener(): BluetoothService.ServiceEventListener =
        object : BluetoothService.ServiceEventListener {
            override fun onConnected(device: BluetoothDevice) {
                DebugLog.log("MainActivity", "Classic host connected")
                StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                StoreProvider.dispatch(Action.UpdateMessage("Classic Bluetooth host connected"))
            }

            override fun onDisconnected(device: BluetoothDevice?) {
                DebugLog.log("MainActivity", "Classic host disconnected")
                StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(null))
                StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(null))
                StoreProvider.dispatch(Action.UpdateMessage("Disconnected"))
            }

            override fun onInfo(message: String) {
                DebugLog.log("MainActivity", "info: $message")
                if (message == "HID app registered" &&
                    runtimeCoordinator.state is BackendRuntimeState.Starting &&
                    runtimeCoordinator.currentLiveBackend == BackendMode.CLASSIC_HID
                ) {
                    val state = classicService?.getStartupState() ?: ClassicHidStartupRegistry.state
                    reconcileClassicReadiness(state)
                }
                StoreProvider.dispatch(Action.UpdateMessage(message))
            }

            override fun onError(message: String) {
                DebugLog.e("MainActivity", message)
                handleBackendError(BackendMode.CLASSIC_HID, message)
            }

            override fun onLeds(leds: Int) {
                val caps = (leds and LED_CAPS_LOCK) != 0
                val scroll = (leds and LED_SCROLL_LOCK) != 0
                StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
            }
        }

    private fun bleEventListener(): BleHogpService.ServiceEventListener =
        object : BleHogpService.ServiceEventListener {
            override fun onConnected(device: BluetoothDevice) {
                DebugLog.log("MainActivity", "BLE host connected")
                StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                StoreProvider.dispatch(Action.UpdateMessage("BLE HOGP host connected"))
            }

            override fun onDisconnected(device: BluetoothDevice?) {
                DebugLog.log("MainActivity", "BLE host disconnected")
                StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(null))
                StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(null))
                StoreProvider.dispatch(Action.UpdateMessage("BLE disconnected"))
            }

            override fun onInfo(message: String) {
                DebugLog.log("MainActivity", "BLE HOGP info: $message")
                if (message == BleHogpService.READY_MESSAGE &&
                    runtimeCoordinator.state is BackendRuntimeState.Starting &&
                    runtimeCoordinator.currentLiveBackend == BackendMode.BLE_HOGP
                ) {
                    reconcileBleReadiness(bleService?.currentStartupState() ?: BleHogpStartupRegistry.state)
                }
                StoreProvider.dispatch(Action.UpdateMessage(message))
            }

            override fun onError(message: String) {
                DebugLog.e("MainActivity", "BLE HOGP error: $message")
                handleBackendError(BackendMode.BLE_HOGP, message)
            }

            override fun onLeds(leds: Int) {
                val caps = (leds and LED_CAPS_LOCK) != 0
                val scroll = (leds and LED_SCROLL_LOCK) != 0
                StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
            }
        }

    private fun handleBackendError(
        mode: BackendMode,
        message: String,
    ) {
        val state = runtimeCoordinator.state
        if (state is BackendRuntimeState.Starting && state.backend == mode) {
            backendLifecycle.failInitialization(mode, message)
        } else {
            StoreProvider.dispatch(Action.UpdateMessage(message))
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
                        val settingsIntent =
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            )
                        startActivity(settingsIntent)
                        finish()
                    }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("MainActivity", "permission dialog failed: ${e.message}")
                finish()
            }
        }
    }
}
