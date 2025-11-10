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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
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
import androidx.core.content.ContextCompat
import android.view.SoundEffectConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown


import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import kotlin.math.abs
import android.os.PowerManager
import android.bluetooth.BluetoothDevice
import android.provider.Settings
import android.net.Uri
import android.app.ActivityManager
import android.view.inputmethod.InputMethodManager
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

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

    private val viewModel: PairingViewModel by viewModels()

    private var serviceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.LocalBinder
            viewModel.setBluetoothService(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // This is called when the connection with the service has been unexpectedly disconnected
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register receiver for permission-error reports from services
        try {
            registerReceiver(permReceiver, IntentFilter(BluetoothService.ACTION_MISSING_BLUETOOTH_CONNECT))
        } catch (_: Exception) {}
        // Force enable debug logging for diagnostics, then apply persisted settings
        DebugLog.setEnabled(true)
        DebugLog.setLevel(DebugLog.Level.ALL)
        val settingsFlow = SettingsManager.flow(this)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            settingsFlow.collect {
                DebugLog.setEnabled(true) // keep enabled regardless of persisted flag during troubleshooting
                val lvl = when (it.logLevel) { 1 -> DebugLog.Level.INFO; 2 -> DebugLog.Level.ERROR; else -> DebugLog.Level.ALL }
                DebugLog.setLevel(lvl)
            }
        }
        setContent {
            AndroidbtkbmouseTheme {
                MainScreen(viewModel)
            }
        }
        // Defer starting/binding services until required runtime permissions are granted.
        val permissionLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val granted = result.values.all { it }
                if (granted) {
                    startServicesAndBind()
                } else {
                    showPermissionNeededDialog()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(permReceiver) } catch (_: Exception) {}
        if (serviceBound) { unbindService(connection); serviceBound = false }
    }

    private fun requiredStartupPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) else if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
        ) else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startServicesAndBind() {
        val serviceIntent = Intent(this, BluetoothService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        val bleIntent = Intent(this, BleHogpService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(bleIntent) else startService(bleIntent)
        try { serviceBound = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE) } catch (e: Exception) { DebugLog.e("MainActivity", "bind service failed: ${e.message}") }
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
fun MainScreen(viewModel: PairingViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()
    val scope = rememberCoroutineScope()
    val connected by viewModel.connectedDevice.collectAsState()
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
    // Battery optimization / Doze prompt
    var showBatteryDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            val sp = context.getSharedPreferences("perm", Context.MODE_PRIVATE)
            val asked = sp.getBoolean("battery_asked", false)
            if (!ignoring && !asked) {
                showBatteryDialog = true
            }
        }
    }
    // read settings to show logging chip
    val settingsFlow = remember { SettingsManager.flow(context) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    // set DebugLog level reactively inside Compose too (safety if activity restarted)
    androidx.compose.runtime.LaunchedEffect(settings.debugLogging, settings.logLevel) {
        DebugLog.setEnabled(settings.debugLogging)
        val lvl = when (settings.logLevel) { 1 -> DebugLog.Level.INFO; 2 -> DebugLog.Level.ERROR; else -> DebugLog.Level.ALL }
        DebugLog.setLevel(lvl)
    }
    if (showBatteryDialog) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val restricted = if (Build.VERSION.SDK_INT >= 28) am.isBackgroundRestricted else false
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBatteryDialog = false
                context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("battery_asked", true).apply()
            },
            title = { Text("Disable battery optimizations") },
            text = { Text("To keep the Bluetooth HID service running reliably, allow the app to be excluded from battery optimizations." + (if (restricted) " Your device also restricts background activity; consider allowing background activity for best reliability." else "")) },
            confirmButton = {
                Button(onClick = {
                    showBatteryDialog = false
                    context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("battery_asked", true).apply()
                    if (Build.VERSION.SDK_INT >= 23) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    }
                }) { Text("Allow") }
            },
            dismissButton = { Button(onClick = {
                showBatteryDialog = false
                context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("battery_asked", true).apply()
            }) { Text("Later") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { 
                val chip = if (settings.debugLogging) {
                    val t = when (settings.logLevel) { 1 -> "Info"; 2 -> "Error"; else -> "All" }
                    " • Log:" + t
                } else ""
                Column {
                    Text(stringResource(id = R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text("Status: " + (connected?.name ?: "Disconnected") + chip, style = MaterialTheme.typography.bodySmall)
                }
            })
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                Screen.values().forEach { screen ->
                    // Allow access to keyboard/mouse screens when connected OR when in Pairing/Settings
                    // Also allow access when debug logging is enabled so developers can use local preview
                    // on emulator/no-BT without changing production behavior.
                    // Allow offline access to keyboard/mouse when user enables Offline preview
                    val isEnabled = connected != null || screen == Screen.Pairing || screen == Screen.Settings || settings.debugLogging || settings.offlinePreview
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = screen.icon), contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        enabled = isEnabled,
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
        }
    ) { innerPadding ->
        val message by viewModel.message.collectAsState()
        val connected by viewModel.connectedDevice.collectAsState()
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
                viewModel.consumeMessage()
            }
        }
        NavHost(navController, startDestination = Screen.Pairing.route, Modifier) {
            composable(Screen.Pairing.route) { PairingScreen(viewModel, contentPadding = innerPadding) }
            composable(Screen.Keyboard.route) { KeyboardScreen(viewModel, contentPadding = innerPadding) }
            composable(Screen.Mouse.route) { MouseScreen(viewModel, contentPadding = innerPadding) }
            composable(Screen.Settings.route) { SettingsScreen(contentPadding = innerPadding, onOpenLogs = { navController.navigate("logs") }) }
            composable("logs") { LogsScreen(contentPadding = innerPadding) }
        }
    }
}

@Composable
fun PairingScreen(viewModel: PairingViewModel, contentPadding: PaddingValues = PaddingValues()) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()

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
            viewModel.startDiscovery()
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
                        viewModel.setAlias(dev, renameText.trim())
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
                        viewModel.forgetDevice(dev, unpair)
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
        if (connectedDevice == null) {
            Text(text = "Status: Disconnected", modifier = Modifier.padding(16.dp))
            Button(onClick = {
                android.util.Log.d("BTKB", "ScanButton: playSoundEffect CLICK")
                view.playSoundEffect(SoundEffectConstants.CLICK)
                val hapticDone = view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                android.util.Log.d("BTKB", "ScanButton: hapticPerformed="+hapticDone)
                // Removed MediaActionSound for reliability in tests
                val req = requiredPermissions()
                val missing = req.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) {
                    viewModel.startDiscovery()
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }) {
                Text(text = "Scan for devices")
            }
            Text(text = "Discovered Devices", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(discoveredDevices) { dev: BluetoothDevice ->
                    val name = dev.name ?: "Unknown Device"
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(dev.address) },
                        modifier = Modifier.clickable { view.playSoundEffect(SoundEffectConstants.CLICK); viewModel.pairDevice(dev) }
                    )
                    androidx.compose.material3.HorizontalDivider()
                }
            }
            Text(text = "Paired Devices", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            val defaultAddr by viewModel.defaultDeviceAddress.collectAsState()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(pairedDevices) { dev: BluetoothDevice ->
                    val display = viewModel.getAlias(dev) ?: (dev.name ?: dev.address)
                    val isConn = connectedDevice?.address == dev.address
                    val isDef = defaultAddr == dev.address
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = display + if (isDef) " ★" else "", modifier = Modifier.weight(1f))
                        if (isConn) {
                            Button(onClick = { viewModel.disconnectDevice() }, modifier = Modifier.padding(end = 6.dp)) { Text("Disconnect") }
                        } else {
                            Button(onClick = { viewModel.connectDevice(dev) }, modifier = Modifier.padding(end = 6.dp)) { Text("Connect") }
                        }
                        Button(onClick = { viewModel.setDefaultDevice(dev) }, modifier = Modifier.padding(end = 6.dp), enabled = !isDef) { Text(if (isDef) "Default" else "Make default") }
                        Button(onClick = { renaming = dev; renameText = display }, modifier = Modifier.padding(end = 6.dp)) { Text("Rename") }
                        Button(onClick = { toForget = dev }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Forget") }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        } else {
            Text(text = "Status: Connected to ${connectedDevice?.name}", modifier = Modifier.padding(16.dp))
            Button(onClick = { viewModel.disconnectDevice() }) {
                Text(text = "Disconnect")
            }
        }
    }
}

@Composable
fun KeyboardScreen(viewModel: PairingViewModel, contentPadding: PaddingValues = PaddingValues()) {
    // Minimal KeyboardScreen wired to Redux store for modifier demo.
    val appState by StoreProvider.asStateFlow().collectAsState()
    val kb = appState.keyboard
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Keyboard (Redux demo)", style = MaterialTheme.typography.titleLarge)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ctrl:" )
            androidx.compose.material3.Switch(checked = kb.ctrl, onCheckedChange = { StoreProvider.dispatch(Action.ToggleCtrl) })
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            Text("Shift:")
            androidx.compose.material3.Switch(checked = kb.shift, onCheckedChange = { StoreProvider.dispatch(Action.ToggleShift) })
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            // dispatch a SendKey action as demonstration (HID 'A' usage 0x04)
            StoreProvider.dispatch(Action.SendKey(0x04.toByte(), mods = if (kb.ctrl) 0x01 else 0))
        }) { Text("Send 'A' (via middleware)") }
    }
}

@Composable
fun ExtendedModButton(label: String, active: Boolean, persist: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, modifier = Modifier.height(40.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
            val color = when {
                persist -> androidx.compose.ui.graphics.Color.Red
                active -> androidx.compose.ui.graphics.Color.Green
                else -> androidx.compose.ui.graphics.Color.LightGray
            }
            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = color)
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    toggled: Boolean = false,
    repeatable: Boolean = false,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    repeatDelayMs: Int = 350,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val containerColor = if (pressed || toggled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (pressed || toggled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    Button(
        modifier = if (repeatable) modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var isDown = false
                var repeatJob: kotlinx.coroutines.Job? = null
                while (true) {
                    val event = awaitPointerEvent()
                    val anyDown = event.changes.any { it.pressed }
                    if (anyDown && !isDown) {
                        isDown = true
                        pressed = true
                        onPress?.invoke()
                        repeatJob = scope.launch {
                            delay(repeatDelayMs.toLong())
                            while (isDown) {
                                onPress?.invoke()
                                delay(70)
                            }
                        }
                    } else if (!anyDown && isDown) {
                        isDown = false
                        pressed = false
                        repeatJob?.cancel()
                        onRelease?.invoke()
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        } else modifier,
        colors = colors,
        onClick = {
            scope.launch {
                pressed = true
                onClick()
                delay(80)
                pressed = false
            }
        }
    ) { Text(label) }
}

@Composable
fun MouseScreen(viewModel: PairingViewModel, contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val connected by viewModel.connectedDevice.collectAsState()
    val settingsFlow = remember(connected) { SettingsManager.flowForDevice(context, connected?.address) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).navigationBarsPadding()) {
        // Touchpad area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
                                        if (dx != 0 || dy != 0) viewModel.moveMouse(dx, dy)
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
                                    viewModel.scroll(send)
                                    scrollAccumV -= stepPx * step
                                }
                                if (settings.enableHorizontalScroll) {
                                    scrollAccumH += dxSum
                                    while (abs(scrollAccumH) >= stepPx) {
                                        val step = if (scrollAccumH > 0) 1 else -1
                                        val send = if (settings.invertHorizontalScroll) -step else step
                                        viewModel.scrollH(send)
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
                            when (maxPointers) {
                                1 -> viewModel.leftClick()
                                2 -> viewModel.rightClick()
                                3 -> if (settings.enableMiddleClick) viewModel.middleClick()
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = { viewModel.leftClick() }) { Text("Left") }
            Button(modifier = Modifier.weight(1f), enabled = settings.enableMiddleClick, onClick = { if (settings.enableMiddleClick) viewModel.middleClick() }) { Text("Middle") }
            Button(modifier = Modifier.weight(1f), onClick = { viewModel.rightClick() }) { Text("Right") }
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
