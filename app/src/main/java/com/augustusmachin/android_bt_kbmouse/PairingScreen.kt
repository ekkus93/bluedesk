package com.augustusmachin.android_bt_kbmouse

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

private data class PairedDeviceCallbacks(
    val onRename: (BluetoothDevice, String) -> Unit,
    val onForget: (BluetoothDevice) -> Unit,
)

private data class DeviceRow(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val isDefault: Boolean,
)

private data class PairingDeviceState(
    val discovered: List<DeviceRow>,
    val paired: List<DeviceRow>,
    val isConnected: Boolean,
)

private data class PairedDeviceRowActions(
    val onRename: () -> Unit,
    val onForget: () -> Unit,
)

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
            // Callback maps can be partial. Re-read actual OS state before starting discovery.
            val required = PermissionPolicy.requiredForScan(Build.VERSION.SDK_INT)
            val missingScan = PermissionGrantChecker.missing(context, required)
            if (missingScan.isEmpty()) {
                StoreProvider.dispatch(Action.StartDiscovery)
            } else {
                val denied = missingScan.toTypedArray()
                ui.pendingPermissions.value = denied
                val permanentlyDenied =
                    activity != null &&
                        denied.any {
                            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                        }
                if (permanentlyDenied) ui.showSettings.value = true else ui.showRationale.value = true
            }
        }

    PermissionDialogs(ui, context, permissionLauncher)
    DeviceDialogs(ui)

    PairingContent(
        contentPadding = contentPadding,
        devices = resolveDeviceRows(context, appState.connection),
        view = view,
        onScan = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val missing =
                requiredScanPermissions().filter {
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

private typealias PermissionLauncher = androidx.activity.result.ActivityResultLauncher<Array<String>>

// One permission-safe mapper intentionally handles both discovered and paired device variants.
@Suppress("CyclomaticComplexMethod")
private fun resolveDeviceRows(
    context: android.content.Context,
    connection: com.augustusmachin.android_bt_kbmouse.store.ConnectionState,
): PairingDeviceState {
    val mayReadDeviceMetadata =
        PermissionGrantChecker.hasAll(context, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))
    val connectedAddress = connection.connectedDeviceAddress

    fun addressOf(device: BluetoothDevice): String? =
        if (!mayReadDeviceMetadata) {
            null
        } else {
            try {
                device.address
            } catch (se: SecurityException) {
                DebugLog.e("PairingScreen", "Bluetooth address permission failure: ${se.message}")
                null
            }
        }

    fun nameOf(device: BluetoothDevice): String? =
        if (!mayReadDeviceMetadata) {
            null
        } else {
            try {
                device.name
            } catch (se: SecurityException) {
                DebugLog.e("PairingScreen", "Bluetooth name permission failure: ${se.message}")
                null
            }
        }

    val discovered =
        connection.discoveredDevices.map { device ->
            val address = addressOf(device)
            DeviceRow(
                device = device,
                name = nameOf(device) ?: address ?: "Unknown Device",
                address = address ?: "Address unavailable",
                isConnected = address != null && address == connectedAddress,
                isDefault = false,
            )
        }
    val paired =
        connection.pairedDevices.map { device ->
            val address = addressOf(device)
            val alias =
                try {
                    ServiceAliasHelper.getAlias(device)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    DebugLog.e("PairingScreen", "Alias lookup failed: ${e.message}")
                    null
                }
            DeviceRow(
                device = device,
                name = alias ?: nameOf(device) ?: address ?: "Bluetooth device",
                address = address ?: "Address unavailable",
                isConnected = address != null && address == connectedAddress,
                isDefault = address != null && connection.defaultDeviceAddress == address,
            )
        }
    return PairingDeviceState(discovered, paired, connection.connectedDevice != null)
}

private fun requiredScanPermissions(): Array<String> =
    PermissionPolicy.requiredForScan(
        Build.VERSION.SDK_INT,
    ).toTypedArray()

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
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onScan) { Text(text = "Scan for devices") }
            if (devices.isConnected) {
                Button(onClick = { StoreProvider.dispatch(Action.DisconnectDevice) }) { Text(text = "Disconnect") }
            }
        }

        if (devices.discovered.isNotEmpty()) DiscoveredDeviceList(devices.discovered, view)
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
                    launcher.launch(if (pending.isNotEmpty()) pending else requiredScanPermissions())
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
                    ui.renaming.value?.let { device ->
                        StoreProvider.dispatch(Action.RenameDevice(device, ui.renameText.value.trim()))
                    }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 0.dp, max = 200.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            PairedDeviceActions(row, actions)
        }
    }
}

@Composable
private fun PairedDeviceActions(
    row: DeviceRow,
    actions: PairedDeviceRowActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
