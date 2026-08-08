package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

/** Routes Pairing to a workflow that matches the selected backend's actual capabilities. */
@Composable
fun BackendAwarePairingScreen(contentPadding: PaddingValues = PaddingValues()) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    if (appState.backend.selectedBackend == BackendMode.BLE_HOGP) {
        BleHostInitiatedPairingScreen(contentPadding)
    } else {
        PairingScreen(contentPadding)
    }
}

@Composable
private fun BleHostInitiatedPairingScreen(contentPadding: PaddingValues) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val runtime = appState.backend.runtime
    val connectedLabel = appState.connection.connectedDeviceLabel ?: appState.connection.connectedDeviceAddress

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .padding(20.dp),
    ) {
        Text("BLE HOGP", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = bleRuntimeStatus(runtime, connectedLabel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
            color =
                if (runtime is BackendRuntimeState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        Text(
            "BLE HOGP pairing is host-initiated. BlueDeck advertises as a Bluetooth keyboard/mouse; " +
                "there is no in-app Scan, Pair, Connect, Rename, default-device, or Forget workflow in BLE mode.",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "On the computer or other host, open Bluetooth settings, find BlueDeck, and choose Pair/Connect. " +
                "Keep this screen open while the backend is starting so any initialization failure remains visible.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (runtime is BackendRuntimeState.Failed) {
            Text(
                "Remediation: verify Bluetooth is enabled and BlueDeck has the required Nearby devices " +
                    "connect/advertise permissions, then switch to Classic and back to BLE to retry startup.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun bleRuntimeStatus(
    runtime: BackendRuntimeState,
    connectedLabel: String?,
): String =
    when (runtime) {
        is BackendRuntimeState.Ready ->
            if (runtime.backend == BackendMode.BLE_HOGP) {
                if (connectedLabel != null) "Connected to $connectedLabel" else "Advertising and ready for the host"
            } else {
                "Waiting for BLE backend activation"
            }

        is BackendRuntimeState.Starting ->
            if (runtime.backend == BackendMode.BLE_HOGP) {
                "Starting BLE backend: ${runtime.stage.name.lowercase().replace('_', ' ')}"
            } else {
                "Waiting for BLE backend activation"
            }

        is BackendRuntimeState.Stopping -> "Stopping BLE backend"
        is BackendRuntimeState.Failed -> runtime.failure.message
        BackendRuntimeState.Stopped -> "BLE backend is stopped"
    }
