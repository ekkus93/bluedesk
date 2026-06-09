package com.augustusmachin.android_bt_kbmouse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
// NOTE: Do not import the 'weight' symbol explicitly to avoid internal visibility issues; use Modifier.weight as available from layout package
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.TextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.augustusmachin.android_bt_kbmouse.ui.theme.AndroidbtkbmouseTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import android.view.SoundEffectConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown


import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import kotlin.math.abs
import android.bluetooth.BluetoothDevice
import android.provider.Settings
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.ServiceAliasHelper
import com.augustusmachin.android_bt_kbmouse.store.PreviewKeyEntry

private const val KEY_FONT_SCALE = 4200
val LocalKeyFontSize = staticCompositionLocalOf { 16.sp }

class MainActivity : ComponentActivity() {

    private val permReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == com.augustusmachin.android_bt_kbmouse.BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT) {
                // Show a dialog on UI thread and finish gracefully
                runOnUiThread {
                    try {
                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Missing permission")
                            .setMessage("This app requires the BLUETOOTH_CONNECT permission. Please grant it in Settings. The app will now exit.")
                            .setPositiveButton("Open Settings") { _, _ ->
                                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                                startActivity(i)
                                finish()
                            }
                            .setNegativeButton("Close") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } catch (e: Exception) {
                        // As a fallback, just finish
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

        private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
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
            } catch (_: Exception) {}

            // Install service event listener here and dispatch canonical store updates
            try {
                svc.setEventListener(object : BluetoothService.ServiceEventListener {
                    override fun onConnected(device: BluetoothDevice) {
                        DebugLog.log("MainActivity", "onConnected ${device.address}")
                        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
                        // Avoid reading device.name here to prevent BLUETOOTH_CONNECT permission lint in non-UI contexts; use address for message
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
                        val caps = (leds and 0x02) != 0
                        val scroll = (leds and 0x04) != 0
                        StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
                    }
                })
            } catch (_: Exception) {}
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Unregister the KeySender since the service is gone
            try { StoreProvider.setKeySender(null) } catch (_: Exception) {}
        }
    }

    private val bleHogpConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val svc = (service as BleHogpService.LocalBinder).getService()
            if (!settingsViewModel.settings.value.useBleHogp) return
            // useBleHogp is enabled — install BleHogpKeySender and wire store events
            try {
                StoreProvider.setKeySender(com.augustusmachin.android_bt_kbmouse.store.BleHogpKeySender(svc))
            } catch (t: Throwable) {
                DebugLog.e("MainActivity", "BleHogp setKeySender failed: ${t.message}")
            }
            svc.eventListener = object : BleHogpService.ServiceEventListener {
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
                    val caps = (leds and 0x02) != 0
                    val scroll = (leds and 0x04) != 0
                    StoreProvider.dispatch(Action.UpdateLocks(caps, scroll))
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            if (settingsViewModel.settings.value.useBleHogp) {
                try { StoreProvider.setKeySender(null) } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register receiver for permission-error reports from services
        try {
            androidx.core.content.ContextCompat.registerReceiver(this, permReceiver, IntentFilter(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {}
        // SettingsViewModel drives DebugLog enable/level after persisted settings load.
        // Keep a minimal startup log enabled until settings arrive.
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.ERROR)
        setContent {
            AndroidbtkbmouseTheme {
                MainScreen()
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

        val missing = requiredStartupPermissions().filter {
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
        try { unregisterReceiver(permReceiver) } catch (_: Exception) {}
        if (serviceBound) {
            try { StoreProvider.setKeySender(null) } catch (_: Exception) {}
            unbindService(connection)
            serviceBound = false
        }
        if (bleHogpBound) {
            try { unbindService(bleHogpConnection) } catch (_: Exception) {}
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
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        try { serviceBound = bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        catch (e: Exception) { DebugLog.e("MainActivity", "bind BluetoothService failed: ${e.message}") }
    }

    private fun startAndBindBleBackend() {
        val intent = Intent(this, BleHogpService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        try { bleHogpBound = bindService(intent, bleHogpConnection, Context.BIND_AUTO_CREATE) }
        catch (e: Exception) { DebugLog.e("MainActivity", "bind BleHogpService failed: ${e.message}") }
    }

    private fun switchBackend(useBle: Boolean) {
        DebugLog.log("MainActivity", "switchBackend → ${BackendSelector.fromSettings(useBle)}")
        if (serviceBound) {
            try { StoreProvider.setKeySender(null) } catch (_: Exception) {}
            try { unbindService(connection) } catch (_: Exception) {}
            serviceBound = false
        }
        if (bleHogpBound) {
            try { StoreProvider.setKeySender(null) } catch (_: Exception) {}
            try { unbindService(bleHogpConnection) } catch (_: Exception) {}
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
                    .setMessage("This app needs Bluetooth permissions to operate. Open App Settings to grant permissions or close the app.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        startActivity(i)
                        finish()
                    }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                finish()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice
    // Proactively request notifications on 33+
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
        DebugLog.log("Main", "POST_NOTIFICATIONS granted=$granted")
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val sp = context.getSharedPreferences("perm", Context.MODE_PRIVATE)
            if (!sp.getBoolean("notif_asked", false)) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    sp.edit().putBoolean("notif_asked", true).apply()
                }
            }
        }
    }
    // Battery-optimization exemption is now offered on demand from the Settings screen
    // (Background reliability), instead of as a prompt on launch.
    // read settings from SettingsViewModel to avoid duplicate collectors
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    Scaffold(
        topBar = {
            Column {
                // Move the navigation bar to the top so it's accessible even when the IME is visible.
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    Screen.values().forEach { screen ->
                        val isEnabled = connected != null || screen == Screen.Pairing || screen == Screen.Settings || settings.debugLogging || settings.offlinePreview
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = screen.icon), contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            // Always enabled so onClick fires; disabled appearance applied via colors.
                            enabled = true,
                            colors = if (!isEnabled) NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                selectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                indicatorColor = Color.Transparent,
                            ) else NavigationBarItemDefaults.colors(),
                            onClick = {
                                if (!isEnabled) {
                                    scope.launch { snackbarHostState.showSnackbar("Connect a device first") }
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }

                // Compact status row — hidden on Keyboard screen to save space.
                val statusNavEntry by navController.currentBackStackEntryAsState()
                if (statusNavEntry?.destination?.route != Screen.Keyboard.route) CenterAlignedTopAppBar(
                    title = {
                        val chip = if (settings.debugLogging) {
                            val t = when (settings.logLevel) { 1 -> "Info"; 2 -> "Error"; else -> "All" }
                            " • Log:" + t
                        } else ""
                        // Compact status: indicator + device name; omit the large app title to save space.
                        val connectedName = connected?.name
                        val statusText = if (connectedName != null) "Connected to ${connectedName}" else "Disconnected"
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics { this[SemanticsProperties.ContentDescription] = listOf(statusText) }
                        ) {
                            val indicatorColor = if (connectedName != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                            Text(text = connectedName ?: "Disconnected", style = MaterialTheme.typography.bodySmall)
                            if (settings.debugLogging && chip.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                                Text(chip, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    actions = {
                        // Scan action
                        androidx.compose.material3.IconButton(
                            onClick = { StoreProvider.dispatch(Action.StartDiscovery) },
                            modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Scan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Settings action
                        androidx.compose.material3.IconButton(
                            onClick = { navController.navigate(Screen.Settings.route) },
                            modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
    val message = appState.connection.message
        val navBack by navController.currentBackStackEntryAsState()
        val route = navBack?.destination?.route
        androidx.compose.runtime.LaunchedEffect(connected, route, settings.offlinePreview, settings.debugLogging) {
                val keyboardAccessible = connected != null || settings.debugLogging || settings.offlinePreview
                if (!keyboardAccessible && (route == Screen.Keyboard.route || route == Screen.Mouse.route)) {
                    snackbarHostState.showSnackbar("Disconnected")
                    navController.navigate(Screen.Pairing.route) { launchSingleTop = true }
                }
            }
        if (message != null) {
            androidx.compose.runtime.LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message!!)
                StoreProvider.dispatch(Action.UpdateMessage(null))
            }
        }
        NavHost(navController, startDestination = Screen.Pairing.route, Modifier) {
            composable(Screen.Pairing.route) { PairingScreen(contentPadding = innerPadding) }
            composable(Screen.Keyboard.route) { KeyboardScreen(navController, contentPadding = innerPadding) }
            composable(Screen.Mouse.route) { MouseScreen(contentPadding = innerPadding) }
            composable(Screen.Settings.route) { SettingsScreen(contentPadding = innerPadding, onOpenLogs = { navController.navigate("logs") }) }
            composable("logs") { LogsScreen(contentPadding = innerPadding) }
        }
    }
}

@Composable
fun PairingScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val discoveredDevices = appState.connection.discoveredDevices
    val pairedDevices = appState.connection.pairedDevices
    val connectedDevice = appState.connection.connectedDevice

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = LocalView.current
    var pendingPermissions by remember { mutableStateOf(emptyArray<String>()) }
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BluetoothDevice?>(null) }
    var renameText by remember { mutableStateOf("") }
    var toForget by remember { mutableStateOf<BluetoothDevice?>(null) }
    var unpair by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            StoreProvider.dispatch(Action.StartDiscovery)
        } else {
            val denied = granted.filterValues { !it }.keys.toTypedArray()
            if (activity != null && denied.any { !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
                pendingPermissions = denied
                showSettings = true
            } else {
                pendingPermissions = denied
                showRationale = true
            }
        }
    }
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS
        ) else if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent)
    }

    if (showRationale) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Permission required") },
            text = { Text("Bluetooth permissions are needed to discover and connect to your host device.") },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    val perms = if (pendingPermissions.isNotEmpty()) pendingPermissions else requiredPermissions()
                    permissionLauncher.launch(perms)
                }) { Text("Try again") }
            },
            dismissButton = { Button(onClick = { showRationale = false }) { Text("Cancel") } }
        )
    }
    if (showSettings) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Permission permanently denied") },
            text = { Text("You have permanently denied required permissions. Open app settings to grant them.") },
            confirmButton = {
                Button(onClick = {
                    showSettings = false
                    openAppSettings()
                }) { Text("Open Settings") }
            },
            dismissButton = { Button(onClick = { showSettings = false }) { Text("Cancel") } }
        )
    }

    run {
        val ren = renaming
        if (ren != null) {
            val dev = ren
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Rename device") },
                text = {
                    androidx.compose.material3.TextField(value = renameText, onValueChange = { renameText = it })
                },
                confirmButton = {
                    Button(onClick = {
                        ServiceAliasHelper.setAlias(dev, renameText.trim())
                        renaming = null
                    }) { Text("Save") }
                },
                dismissButton = { Button(onClick = { renaming = null }) { Text("Cancel") } }
            )
        }
    }
    run {
        val tf = toForget
        if (tf != null) {
            val dev = tf
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { toForget = null },
                title = { Text("Forget device") },
                text = {
                    Column { 
                        Text("Remove this device from defaults and aliases." )
                        androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp)) {
                            androidx.compose.material3.Checkbox(checked = unpair, onCheckedChange = { unpair = it })
                            Text("Also unpair from system")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        StoreProvider.dispatch(Action.ForgetDevice(dev, unpair))
                        toForget = null
                    }) { Text("Forget") }
                },
                dismissButton = { Button(onClick = { toForget = null }) { Text("Cancel") } }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Action row: Scan always available; Disconnect shown when connected
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                val req = requiredPermissions()
                val missing = req.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) {
                    StoreProvider.dispatch(Action.StartDiscovery)
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }) {
                Text(text = "Scan for devices")
            }
            if (connectedDevice != null) {
                Button(onClick = { StoreProvider.dispatch(Action.DisconnectDevice) }) {
                    Text(text = "Disconnect")
                }
            }
        }

        // Discovered devices — collapses to nothing when empty (TASK-10)
        if (discoveredDevices.isNotEmpty()) {
            Text(text = "Discovered Devices", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = 200.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(discoveredDevices) { dev: BluetoothDevice ->
                    val name = dev.name ?: "Unknown Device"
                    androidx.compose.material3.ListItem(
                        leadingContent = {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bluetooth),
                                    contentDescription = name,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        headlineContent = { Text(name) },
                        supportingContent = { Text(dev.address) },
                        modifier = Modifier
                            .semantics { this[SemanticsProperties.Role] = Role.Button }
                            .clickable { view.playSoundEffect(SoundEffectConstants.CLICK); StoreProvider.dispatch(Action.PairDevice(dev)) }
                    )
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        }

        // Paired devices — always visible (TASK-13)
        Text(text = "Paired Devices", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
        val defaultAddr = appState.connection.defaultDeviceAddress
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(pairedDevices) { dev: BluetoothDevice ->
                val display = ServiceAliasHelper.getAlias(dev) ?: (dev.name ?: dev.address)
                val isConn = connectedDevice?.address == dev.address
                val isDef = defaultAddr == dev.address
                val cardDesc = "Paired device: $display. ${if (isConn) "Connected." else "Disconnected."} ${if (isDef) "Default device." else ""}"
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { this[SemanticsProperties.ContentDescription] = listOf(cardDesc) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        // Row 1: icon + name + MAC address
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_bluetooth), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = display + if (isDef) " ★" else "",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = dev.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Row 2: action icons
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                        ) {
                            val iconTint = MaterialTheme.colorScheme.onSurface
                            if (isConn) {
                                androidx.compose.material3.IconButton(
                                    onClick = { StoreProvider.dispatch(Action.DisconnectDevice) },
                                    modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                                ) { Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Disconnect", tint = iconTint) }
                            } else {
                                androidx.compose.material3.IconButton(
                                    onClick = { StoreProvider.dispatch(Action.ConnectDevice(dev)) },
                                    modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                                ) { Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Connect", tint = iconTint) }
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { StoreProvider.dispatch(Action.SetDefaultDevice(dev)) },
                                enabled = !isDef,
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                            ) {
                                val starRes = if (isDef) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                                Icon(painterResource(id = starRes), contentDescription = if (isDef) "Default device" else "Set default device", tint = if (isDef) MaterialTheme.colorScheme.primary else iconTint)
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { renaming = dev; renameText = display },
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                            ) { Icon(painterResource(id = R.drawable.ic_edit), contentDescription = "Rename", tint = iconTint) }
                            androidx.compose.material3.IconButton(
                                onClick = { toForget = dev },
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                            ) { Icon(painterResource(id = R.drawable.ic_delete), contentDescription = "Forget", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboardScreen(navController: NavHostController, contentPadding: PaddingValues = PaddingValues()) {
    // Replace the tiny demo with the full Extended Keys UI by default.
    // The ExtendedKeysScreen already handles modifier toggles and connected-state gating.
    val tabs = listOf("Extended", "Function", "Navigation")
    var selectedTab by remember { mutableStateOf(0) }
    val appState by StoreProvider.asStateFlow().collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var previewText by remember { mutableStateOf("") }
    val previewKeys = appState.ui.previewKeys
    val previewSuffix = remember(previewKeys) {
        if (previewKeys.isEmpty()) {
            ""
        } else buildString {
            previewKeys.forEachIndexed { index, entry ->
                val decoratedText = if (entry.decorate) "[${entry.label}]" else entry.label
                val previous = previewKeys.getOrNull(index - 1)
                val currentRequiresSpace = entry.decorate || entry.label.length > 1
                val previousRequiresSpace = previous?.decorate == true || ((previous?.label?.length ?: 0) > 1)
                val currentIsSpace = !entry.decorate && entry.label == " "
                val previousIsSpace = previous?.let { !it.decorate && it.label == " " } == true
                val needsSeparator = when {
                    isEmpty() -> false
                    currentIsSpace || previousIsSpace -> false
                    currentRequiresSpace || previousRequiresSpace -> true
                    else -> false
                }
                if (needsSeparator) append(' ')
                append(decoratedText)
            }
        }
    }
    val previewTransformation = remember(previewSuffix) {
        if (previewSuffix.isEmpty()) VisualTransformation.None else PreviewSuffixVisualTransformation(previewSuffix)
    }
    val connected = appState.connection.connectedDevice != null
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val keyFontSize = remember(screenWidthDp) { (KEY_FONT_SCALE / screenWidthDp).coerceIn(10f, 16f).sp }

    CompositionLocalProvider(LocalKeyFontSize provides keyFontSize) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        // TabRow with keyboard icon (tap to re-show IME if dismissed)
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
                }
            }
            androidx.compose.material3.IconButton(onClick = { keyboardController?.show() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard),
                    contentDescription = "Show system keyboard",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Invisible 1dp-tall text field — always present so the IME has an attach point.
        // graphicsLayer alpha=0 hides it visually while keeping it focusable.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .graphicsLayer { alpha = 0f }
        ) {
            androidx.compose.material3.TextField(
                value = previewText,
                onValueChange = { newValue ->
                    if (newValue.isNotEmpty()) {
                        newValue.forEach { char ->
                            val (label, decorate, mapping) = when (char) {
                                ' ' -> Triple(" ", false, charToHid(char))
                                '\n' -> Triple("ENTER", false, charToHid(char))
                                '\t' -> Triple("TAB", false, charToHid(char))
                                else -> Triple(char.toString(), false, charToHid(char))
                            }
                            StoreProvider.dispatch(Action.TrackPreviewKey(label, decorate = decorate))
                            if (connected && mapping != null) {
                                StoreProvider.dispatch(Action.SendKey(mapping.first, mapping.second))
                            } else {
                                StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                            }
                        }
                    }
                    previewText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_DEL && native.action == android.view.KeyEvent.ACTION_DOWN) {
                            StoreProvider.dispatch(Action.TrackPreviewKey("DEL"))
                            if (connected) {
                                StoreProvider.dispatch(Action.SendKey(0x2A.toByte(), 0))
                            } else {
                                StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                            }
                            true
                        } else {
                            false
                        }
                    },
                visualTransformation = previewTransformation,
                placeholder = { Text("System keyboard input") }
            )
        }
        // Auto-focus the hidden field and show IME whenever the Keyboard screen is visible
        androidx.compose.runtime.LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        // Content area: render the selected tab's screen
        when (selectedTab) {
            0 -> ExtendedKeysScreen()
            1 -> FunctionKeysScreen()
            2 -> NavigationKeysScreen()
        }
        }
    }
}

@Composable
fun ResponsiveText(
    text: String,
    minSize: TextUnit = 10.sp,
    maxSize: TextUnit = 16.sp,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    BoxWithConstraints {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // Convert available width to pixels for measurement
        val maxWidthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }

        // Binary search for largest font size (in sp) that fits within maxWidth and single line
        val minSp = minSize.value
        val maxSp = maxSize.value
        var best = minSp
        var lo = minSp
        var hi = maxSp
        while (lo <= hi) {
            val mid = (lo + hi) / 2f
            val style = TextStyle(fontSize = mid.sp, fontWeight = fontWeight, letterSpacing = letterSpacing)
            val result = measurer.measure(AnnotatedString(text), style = style, constraints = Constraints(maxWidth = maxWidthPx))
            val fits = result.size.width <= maxWidthPx && result.lineCount <= 1
            if (fits) {
                best = mid
                lo = mid + 0.5f
            } else {
                hi = mid - 0.5f
            }
        }

        Text(
            text = text,
            fontSize = best.sp,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private class PreviewSuffixVisualTransformation(
    private val suffix: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (suffix.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder()
        builder.append(text)
        builder.append(suffix)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(text.length)
        }
        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}

@Composable
fun MouseScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice
    val settingsFlow = remember(connected) { SettingsManager.flowForDevice(context, connected?.address) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    var dragLock by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { if (dragLock) StoreProvider.dispatch(Action.MouseButtonUp) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).navigationBarsPadding()) {
        // Touchpad area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .semantics { this[SemanticsProperties.Role] = Role.Button }
                .pointerInput(settings) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var maxPointers = 1
                        var moved = false
                        val startTime = System.currentTimeMillis()
                        var scrollAccumV = 0f
                        var scrollAccumH = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed > maxPointers) maxPointers = pressed
                            if (pressed == 1) {
                                event.changes.filter { it.pressed }.forEach { change ->
                                    val d = change.positionChange()
                                    if (d != Offset.Zero) {
                                        moved = true
                                        val dx = (d.x * settings.touchpadSensitivity).roundToInt().coerceIn(-20, 20)
                                        val dy = (d.y * settings.touchpadSensitivity).roundToInt().coerceIn(-20, 20)
                                        if (dx != 0 || dy != 0) StoreProvider.dispatch(Action.MoveMouse(dx, dy))
                                        change.consume()
                                    }
                                }
                            } else if (pressed == 2) {
                                var dySum = 0f
                                var dxSum = 0f
                                event.changes.filter { it.pressed }.forEach { change ->
                                    val d = change.positionChange()
                                    dySum += d.y
                                    dxSum += d.x
                                }
                                moved = moved || (abs(dySum) > 0.5f || abs(dxSum) > 0.5f)
                                val stepPx = (24f / settings.scrollSpeed.coerceAtLeast(0.1f))
                                scrollAccumV += dySum
                                        while (abs(scrollAccumV) >= stepPx) {
                                    val step = if (scrollAccumV > 0) 1 else -1
                                    val send = if (settings.invertScroll) -step else step
                                    StoreProvider.dispatch(Action.ScrollVertical(send))
                                    scrollAccumV -= stepPx * step
                                }
                                if (settings.enableHorizontalScroll) {
                                    scrollAccumH += dxSum
                                        while (abs(scrollAccumH) >= stepPx) {
                                        val step = if (scrollAccumH > 0) 1 else -1
                                        val send = if (settings.invertHorizontalScroll) -step else step
                                        StoreProvider.dispatch(Action.ScrollHorizontal(send))
                                        scrollAccumH -= stepPx * step
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed > 2) {
                                if (event.changes.any { it.positionChange() != Offset.Zero }) moved = true
                            }
                        } while (event.changes.any { it.pressed })
                        val duration = System.currentTimeMillis() - startTime
                        if (!moved && duration < 220) {
                            if (dragLock) {
                                dragLock = false
                                StoreProvider.dispatch(Action.MouseButtonUp)
                            } else {
                                when (maxPointers) {
                                    1 -> StoreProvider.dispatch(Action.LeftClick)
                                    2 -> StoreProvider.dispatch(Action.RightClick)
                                    3 -> if (settings.enableMiddleClick) StoreProvider.dispatch(Action.MiddleClick)
                                }
                            }
                        }
                    }
                }
        ) {
            Text(
                text = "Use this area as a touchpad\n• 1-finger move/tap\n• 2-finger scroll/tap=right\n• 3-finger tap=middle",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Quick mouse buttons
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.ElevatedButton(modifier = Modifier.weight(1f).height(44.dp), onClick = { StoreProvider.dispatch(Action.LeftClick) }) { Text("L") }
            androidx.compose.material3.ElevatedButton(modifier = Modifier.weight(1f).height(44.dp), enabled = settings.enableMiddleClick, onClick = { if (settings.enableMiddleClick) StoreProvider.dispatch(Action.MiddleClick) }) { Text("M") }
            androidx.compose.material3.ElevatedButton(modifier = Modifier.weight(1f).height(44.dp), onClick = { StoreProvider.dispatch(Action.RightClick) }) { Text("R") }
            androidx.compose.material3.ElevatedButton(
                modifier = Modifier.weight(1f).height(44.dp),
                onClick = {
                    dragLock = !dragLock
                    if (dragLock) StoreProvider.dispatch(Action.MouseButtonDown(0x01))
                    else StoreProvider.dispatch(Action.MouseButtonUp)
                },
                colors = if (dragLock)
                    androidx.compose.material3.ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                else androidx.compose.material3.ButtonDefaults.elevatedButtonColors()
            ) { Text("D") }
        }
    }
}

@Composable
fun LogsScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val lines by DebugLog.lines.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(0) } // 0=All,1=Info,2=Error
    val filtered = remember(lines, filter) {
        when (filter) {
            2 -> lines.filter { it.contains(" E [") }
            1 -> lines.filter { !it.contains(" E [") }
            else -> lines
        }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding().padding(16.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Button(onClick = { DebugLog.clear() }, modifier = Modifier.padding(end = 8.dp)) { Text("Clear") }
            Button(onClick = {
                val meta = "App: ${context.packageName}\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\nFilter: " + when(filter){0->"All";1->"Info";else->"Error"} + "\n\n"
                val body = meta + filtered.joinToString("\n")
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_SUBJECT, "Android BT KB/Mouse Logs")
                intent.putExtra(Intent.EXTRA_TEXT, body)
                context.startActivity(Intent.createChooser(intent, "Share logs"))
            }) { Text("Share") }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { filter = 0 }, modifier = Modifier.padding(end = 4.dp)) { Text("All") }
            Button(onClick = { filter = 1 }, modifier = Modifier.padding(end = 4.dp)) { Text("Info") }
            Button(onClick = { filter = 2 }) { Text("Error") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { line -> Text(line, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

enum class Screen(val route: String, val title: String, val icon: Int) {
    Pairing("pairing", "Pairing", R.drawable.ic_bluetooth),
    Keyboard("keyboard", "Keyboard", R.drawable.ic_keyboard),
    Mouse("mouse", "Mouse", R.drawable.ic_mouse),
    Settings("settings", "Settings", R.drawable.ic_settings)
}
