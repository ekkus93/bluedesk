package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.SoundEffectConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.ServiceAliasHelper
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

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
            if (activity != null && denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
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
        AlertDialog(
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
        AlertDialog(
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
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Rename device") },
                text = {
                    TextField(value = renameText, onValueChange = { renameText = it })
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
            AlertDialog(
                onDismissRequest = { toForget = null },
                title = { Text("Forget device") },
                text = {
                    Column {
                        Text("Remove this device from defaults and aliases." )
                        Row(Modifier.padding(top = 8.dp)) {
                            Checkbox(checked = unpair, onCheckedChange = { unpair = it })
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
                    ListItem(
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
                    HorizontalDivider()
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { this[SemanticsProperties.ContentDescription] = listOf(cardDesc) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        // Row 1: icon + name + MAC address
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_bluetooth), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val iconTint = MaterialTheme.colorScheme.onSurface
                            if (isConn) {
                                IconButton(
                                    onClick = { StoreProvider.dispatch(Action.DisconnectDevice) },
                                    modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                                ) { Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Disconnect", tint = iconTint) }
                            } else {
                                IconButton(
                                    onClick = { StoreProvider.dispatch(Action.ConnectDevice(dev)) },
                                    modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                                ) { Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Connect", tint = iconTint) }
                            }
                            IconButton(
                                onClick = { StoreProvider.dispatch(Action.SetDefaultDevice(dev)) },
                                enabled = !isDef,
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                            ) {
                                val starRes = if (isDef) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                                Icon(painterResource(id = starRes), contentDescription = if (isDef) "Default device" else "Set default device", tint = if (isDef) MaterialTheme.colorScheme.primary else iconTint)
                            }
                            IconButton(
                                onClick = { renaming = dev; renameText = display },
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button }
                            ) { Icon(painterResource(id = R.drawable.ic_edit), contentDescription = "Rename", tint = iconTint) }
                            IconButton(
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
