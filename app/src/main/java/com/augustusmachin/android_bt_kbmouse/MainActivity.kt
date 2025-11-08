package com.augustusmachin.android_bt_kbmouse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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

class MainActivity : ComponentActivity() {

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
        val serviceIntent = Intent(this, BluetoothService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // Also start BLE HOGP peripheral service for Windows compatibility
        val bleIntent = Intent(this, BleHogpService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(bleIntent) else startService(bleIntent)
        serviceBound = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) { unbindService(connection); serviceBound = false }
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
                    val isEnabled = connected != null || screen == Screen.Pairing || screen == Screen.Settings
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
        androidx.compose.runtime.LaunchedEffect(connected, route) {
            if (connected == null && (route == Screen.Keyboard.route || route == Screen.Mouse.route)) {
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
        NavHost(navController, startDestination = Screen.Pairing.route, Modifier.padding(innerPadding)) {
            composable(Screen.Pairing.route) { PairingScreen(viewModel) }
            composable(Screen.Keyboard.route) { KeyboardScreen(viewModel) }
            composable(Screen.Mouse.route) { MouseScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(onOpenLogs = { navController.navigate("logs") }) }
            composable("logs") { LogsScreen() }
        }
    }
}

@Composable
fun PairingScreen(viewModel: PairingViewModel) {
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
        modifier = Modifier.fillMaxSize(),
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
fun KeyboardScreen(viewModel: PairingViewModel) {
    val context = LocalContext.current
    val connected by viewModel.connectedDevice.collectAsState()
    val settingsFlow = remember(connected) { SettingsManager.flowForDevice(context, connected?.address) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())
    var shiftOn by remember { mutableStateOf(false) }
    var ctrlOn by remember { mutableStateOf(false) }
    var altOn by remember { mutableStateOf(false) }
    var guiOn by remember { mutableStateOf(false) }
    val capsOn by viewModel.capsLock.collectAsState()
    val numOn by viewModel.numLock.collectAsState()
    val scrollOn by viewModel.scrollLock.collectAsState()

    val rows = listOf(
        listOf("F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"),
        listOf("ESC", "`", "1","2","3","4","5","6","7","8","9","0","-","=","BKSP"),
        listOf("TAB", "Q","W","E","R","T","Y","U","I","O","P","[", "]", "\\"),
        listOf("CAPS", "A","S","D","F","G","H","J","K","L",";","'","ENTER"),
        listOf("SHIFT","Z","X","C","V","B","N","M",",",".","/","SPACE"),
        listOf("CTRL","ALT","GUI","NUM","SCROLL","←","↑","↓","→"),
        listOf("HOME","END","PGUP","PGDN","DEL")
    )

    fun labelToHid(label: String): Byte? {
        val numMap = mapOf(
            "1" to 0x1E, "2" to 0x1F, "3" to 0x20, "4" to 0x21, "5" to 0x22,
            "6" to 0x23, "7" to 0x24, "8" to 0x25, "9" to 0x26, "0" to 0x27
        )
        val punctMap = mapOf(
            "-" to 0x2D, "=" to 0x2E, "[" to 0x2F, "]" to 0x30, "\\" to 0x31,
            "`" to 0x35, ";" to 0x33, "'" to 0x34, "," to 0x36, "." to 0x37, "/" to 0x38
        )
        val functionMap = mapOf(
            "F1" to 0x3A, "F2" to 0x3B, "F3" to 0x3C, "F4" to 0x3D, "F5" to 0x3E, "F6" to 0x3F,
            "F7" to 0x40, "F8" to 0x41, "F9" to 0x42, "F10" to 0x43, "F11" to 0x44, "F12" to 0x45
        )
        val arrowsMap = mapOf(
            "←" to 0x50, "→" to 0x4F, "↑" to 0x52, "↓" to 0x51
        )
        val navMap = mapOf(
            "HOME" to 0x4A, "END" to 0x4D, "PGUP" to 0x4B, "PGDN" to 0x4E, "DEL" to 0x4C
        )
        return when (label) {
            "SPACE" -> 0x2C.toByte()
            "ENTER" -> 0x28.toByte()
            "BKSP" -> 0x2A.toByte()
            "TAB" -> 0x2B.toByte()
            "ESC" -> 0x29.toByte()
            in numMap.keys -> numMap.getValue(label).toByte()
            in punctMap.keys -> punctMap.getValue(label).toByte()
            in functionMap.keys -> functionMap.getValue(label).toByte()
            in arrowsMap.keys -> arrowsMap.getValue(label).toByte()
            in navMap.keys -> navMap.getValue(label).toByte()
            else -> {
                val ch = label.first()
                if (ch in 'A'..'Z') {
                    val code = 0x04 + (ch - 'A')
                    code.toByte()
                } else null
            }
        }
    }

    fun currentModifiers(): Int {
        var mods = 0
        if (ctrlOn) mods = mods or 0x01
        if (shiftOn) mods = mods or 0x02
        if (altOn) mods = mods or 0x04
        if (guiOn) mods = mods or 0x08
        return mods
    }

    val view = LocalView.current
    fun keyWeight(label: String): Float = when (label) {
        "SPACE" -> 8f
        "ENTER" -> 2.25f
        "BKSP" -> 2.0f
        "SHIFT" -> 2.25f
        "CAPS" -> 1.75f
        "TAB" -> 1.5f
        "CTRL", "ALT", "GUI" -> 1.25f
        "HOME", "END", "PGUP", "PGDN", "DEL" -> 1.2f
        else -> 1f
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    when (label) {
                        "SHIFT" -> KeyButton(label = "SHIFT", toggled = shiftOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            shiftOn = !shiftOn
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "CTRL" -> KeyButton(label = "CTRL", toggled = ctrlOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            ctrlOn = !ctrlOn
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "ALT" -> KeyButton(label = "ALT", toggled = altOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            altOn = !altOn
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "GUI" -> KeyButton(label = "GUI", toggled = guiOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            guiOn = !guiOn
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "CAPS" -> KeyButton(label = "CAPS", toggled = capsOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            viewModel.toggleCapsLock()
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "NUM" -> KeyButton(label = "NUM", toggled = numOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            viewModel.toggleNumLock()
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        "SCROLL" -> KeyButton(label = "SCROLL", toggled = scrollOn, modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp)) {
                            viewModel.toggleScrollLock()
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        else -> {
                            val baseCode = labelToHid(label) ?: return@Row
                            val mapped = settings.keyMap[baseCode.toInt()]?.toByte() ?: baseCode
                            KeyButton(
                                label = label,
                                modifier = Modifier.weight(keyWeight(label)).padding(4.dp).height(48.dp),
                                repeatable = true,
                                repeatDelayMs = settings.keyRepeatDelayMs,
                                onPress = {
                                    viewModel.keyDown(mapped, currentModifiers())
                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                },
                                onRelease = {
                                    viewModel.keyUp(mapped)
                                    if (shiftOn) shiftOn = false
                                }
                            ) { }
                        }
                    }
                }
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
    val colors = if (pressed || toggled) {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        ButtonDefaults.buttonColors()
    }
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
fun MouseScreen(viewModel: PairingViewModel) {
    val context = LocalContext.current
    val connected by viewModel.connectedDevice.collectAsState()
    val settingsFlow = remember(connected) { SettingsManager.flowForDevice(context, connected?.address) }
    val settings by settingsFlow.collectAsState(initial = com.augustusmachin.android_bt_kbmouse.Settings())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
fun LogsScreen() {
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
