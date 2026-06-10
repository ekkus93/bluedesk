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

private const val SDK_INT_TIRAMISU = 33
private const val SDK_INT_S = 31

// Per-row callbacks for a paired device entry, bundled to keep composable parameter
// lists small.
private data class PairedDeviceCallbacks(
    val onRename: (BluetoothDevice, String) -> Unit,
    val onForget: (BluetoothDevice) -> Unit,
)

// A pre-resolved device row. name/address are read once in PairingScreen (which performs
// a permission check) so child composables render plain strings without touching the
// permission-guarded BluetoothDevice properties.
private data class DeviceRow(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val isDefault: Boolean,
)

// The device collections rendered by the pairing content, bundled to keep the content
// composable's parameter list small.
private data class PairingDeviceState(
    val discovered: List<DeviceRow>,
    val paired: List<DeviceRow>,
    val isConnected: Boolean,
)

// Bound (no-arg) rename/forget callbacks for a single paired-device card.
private data class PairedDeviceRowActions(
    val onRename: () -> Unit,
    val onForget: () -> Unit,
)

// Hoisted UI state for the pairing screen's dialogs, held as MutableState so child
// composables can both read and update it without a long callback list.
private class PairingUiState {
    val pendingPermissions = mutableStateOf(emptyArray<String>())
    val showRationale = mutableStateOf(false)
    val showSettings = mutableStateOf(false)
    val renaming = mutableStateOf<BluetoothDevice?>(null)
    val renameText = mutableStateOf("")
    val toForget = mutableStateOf<BluetoothDevice?>(null)
    val unpair = mutableStateOf(true)
}

@Composable
fun PairingScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = LocalView.current
    val ui = remember { PairingUiState() }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                StoreProvider.dispatch(Action.StartDiscovery)
            } else {
                val denied = granted.filterValues { !it }.keys.toTypedArray()
                ui.pendingPermissions.value = denied
                val permanentlyDenied =
                    activity != null &&
                        denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                if (permanentlyDenied) ui.showSettings.value = true else ui.showRationale.value = true
            }
        }

    PermissionDialogs(ui, context, permissionLauncher)
    DeviceDialogs(ui)

    // Resolve device names/addresses once; child composables receive plain strings.
    PairingContent(
        contentPadding = contentPadding,
        devices = resolveDeviceRows(context, appState.connection),
        view = view,
        onScan = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val missing =
                requiredBluetoothPermissions().filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
            if (missing.isEmpty()) {
                StoreProvider.dispatch(Action.StartDiscovery)
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        },
        callbacks =
            PairedDeviceCallbacks(
                onRename = { dev, display ->
                    ui.renaming.value = dev
                    ui.renameText.value = display
                },
                onForget = { dev -> ui.toForget.value = dev },
            ),
    )
}

private typealias PermissionLauncher =
    androidx.activity.result.ActivityResultLauncher<Array<String>>

// Reads device names/addresses once and produces plain-string rows. Performs an explicit
// BLUETOOTH_CONNECT permission check so the BluetoothDevice property reads are guarded.
private fun resolveDeviceRows(
    context: android.content.Context,
    connection: com.augustusmachin.android_bt_kbmouse.store.ConnectionState,
): PairingDeviceState {
    val hasConnect =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    val nameOf: (BluetoothDevice) -> String? = { dev -> if (hasConnect) dev.name else null }
    val connectedAddr = connection.connectedDevice?.address
    val discovered =
        connection.discoveredDevices.map { dev ->
            DeviceRow(dev, nameOf(dev) ?: "Unknown Device", dev.address, dev.address == connectedAddr, false)
        }
    val paired =
        connection.pairedDevices.map { dev ->
            DeviceRow(
                device = dev,
                name = ServiceAliasHelper.getAlias(dev) ?: (nameOf(dev) ?: dev.address),
                address = dev.address,
                isConnected = dev.address == connectedAddr,
                isDefault = connection.defaultDeviceAddress == dev.address,
            )
        }
    return PairingDeviceState(discovered, paired, connection.connectedDevice != null)
}

private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= SDK_INT_TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else if (Build.VERSION.SDK_INT >= SDK_INT_S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun PairingContent(
    contentPadding: PaddingValues,
    devices: PairingDeviceState,
    view: android.view.View,
    onScan: () -> Unit,
    callbacks: PairedDeviceCallbacks,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Action row: Scan always available; Disconnect shown when connected
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onScan) { Text(text = "Scan for devices") }
            if (devices.isConnected) {
                Button(onClick = { StoreProvider.dispatch(Action.DisconnectDevice) }) {
                    Text(text = "Disconnect")
                }
            }
        }

        // Discovered devices — collapses to nothing when empty (TASK-10)
        if (devices.discovered.isNotEmpty()) {
            DiscoveredDeviceList(devices.discovered, view)
        }

        // Paired devices — always visible (TASK-13)
        PairedDeviceList(
            pairedRows = devices.paired,
            callbacks = callbacks,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun PairedDeviceList(
    pairedRows: List<DeviceRow>,
    callbacks: PairedDeviceCallbacks,
    modifier: Modifier,
) {
    Text(
        text = "Paired Devices",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(pairedRows) { row ->
            PairedDeviceCard(
                row = row,
                actions =
                    PairedDeviceRowActions(
                        onRename = { callbacks.onRename(row.device, row.name) },
                        onForget = { callbacks.onForget(row.device) },
                    ),
            )
        }
    }
}

@Composable
private fun PermissionDialogs(
    ui: PairingUiState,
    context: android.content.Context,
    launcher: PermissionLauncher,
) {
    if (ui.showRationale.value) {
        AlertDialog(
            onDismissRequest = { ui.showRationale.value = false },
            title = { Text("Permission required") },
            text = { Text("Bluetooth permissions are needed to discover and connect to your host device.") },
            confirmButton = {
                Button(onClick = {
                    ui.showRationale.value = false
                    val pending = ui.pendingPermissions.value
                    launcher.launch(if (pending.isNotEmpty()) pending else requiredBluetoothPermissions())
                }) { Text("Try again") }
            },
            dismissButton = { Button(onClick = { ui.showRationale.value = false }) { Text("Cancel") } },
        )
    }
    if (ui.showSettings.value) {
        AlertDialog(
            onDismissRequest = { ui.showSettings.value = false },
            title = { Text("Permission permanently denied") },
            text = { Text("You have permanently denied required permissions. Open app settings to grant them.") },
            confirmButton = {
                Button(onClick = {
                    ui.showSettings.value = false
                    val intent =
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        )
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = { Button(onClick = { ui.showSettings.value = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeviceDialogs(ui: PairingUiState) {
    if (ui.renaming.value != null) {
        AlertDialog(
            onDismissRequest = { ui.renaming.value = null },
            title = { Text("Rename device") },
            text = { TextField(value = ui.renameText.value, onValueChange = { ui.renameText.value = it }) },
            confirmButton = {
                Button(onClick = {
                    ui.renaming.value?.let { ServiceAliasHelper.setAlias(it, ui.renameText.value.trim()) }
                    ui.renaming.value = null
                }) { Text("Save") }
            },
            dismissButton = { Button(onClick = { ui.renaming.value = null }) { Text("Cancel") } },
        )
    }
    if (ui.toForget.value != null) {
        AlertDialog(
            onDismissRequest = { ui.toForget.value = null },
            title = { Text("Forget device") },
            text = {
                Column {
                    Text("Remove this device from defaults and aliases.")
                    Row(Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = ui.unpair.value, onCheckedChange = { ui.unpair.value = it })
                        Text("Also unpair from system")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    ui.toForget.value?.let { StoreProvider.dispatch(Action.ForgetDevice(it, ui.unpair.value)) }
                    ui.toForget.value = null
                }) { Text("Forget") }
            },
            dismissButton = { Button(onClick = { ui.toForget.value = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DiscoveredDeviceList(
    discoveredRows: List<DeviceRow>,
    view: android.view.View,
) {
    Text(
        text = "Discovered Devices",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = 200.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(discoveredRows) { row ->
            ListItem(
                leadingContent = {
                    androidx.compose.foundation.layout.Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bluetooth),
                            contentDescription = row.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                headlineContent = { Text(row.name) },
                supportingContent = { Text(row.address) },
                modifier =
                    Modifier
                        .semantics { this[SemanticsProperties.Role] = Role.Button }
                        .clickable {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            StoreProvider.dispatch(Action.PairDevice(row.device))
                        },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PairedDeviceCard(
    row: DeviceRow,
    actions: PairedDeviceRowActions,
) {
    val cardDesc =
        "Paired device: ${row.name}. " +
            "${if (row.isConnected) "Connected." else "Disconnected."} " +
            "${if (row.isDefault) "Default device." else ""}"
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics {
                this[SemanticsProperties.ContentDescription] = listOf(cardDesc)
            },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Row 1: icon + name + MAC address
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bluetooth),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = row.name + if (row.isDefault) " ★" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Row 2: action icons
            PairedDeviceActions(row, actions)
        }
    }
}

@Composable
private fun PairedDeviceActions(
    row: DeviceRow,
    actions: PairedDeviceRowActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        val iconTint = MaterialTheme.colorScheme.onSurface
        if (row.isConnected) {
            IconButton(
                onClick = { StoreProvider.dispatch(Action.DisconnectDevice) },
                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
            ) {
                Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Disconnect", tint = iconTint)
            }
        } else {
            IconButton(
                onClick = { StoreProvider.dispatch(Action.ConnectDevice(row.device)) },
                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
            ) {
                Icon(painterResource(id = R.drawable.ic_bluetooth), contentDescription = "Connect", tint = iconTint)
            }
        }
        IconButton(
            onClick = { StoreProvider.dispatch(Action.SetDefaultDevice(row.device)) },
            enabled = !row.isDefault,
            modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
        ) {
            val starRes = if (row.isDefault) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            Icon(
                painterResource(id = starRes),
                contentDescription = if (row.isDefault) "Default device" else "Set default device",
                tint = if (row.isDefault) MaterialTheme.colorScheme.primary else iconTint,
            )
        }
        IconButton(
            onClick = actions.onRename,
            modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
        ) {
            Icon(painterResource(id = R.drawable.ic_edit), contentDescription = "Rename", tint = iconTint)
        }
        IconButton(
            onClick = actions.onForget,
            modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
        ) {
            Icon(
                painterResource(id = R.drawable.ic_delete),
                contentDescription = "Forget",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
