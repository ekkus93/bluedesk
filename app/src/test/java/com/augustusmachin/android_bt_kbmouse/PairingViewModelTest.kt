package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.CommandResult
import com.augustusmachin.android_bt_kbmouse.store.KeyCommand
import com.augustusmachin.android_bt_kbmouse.store.RecordingKeySender
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/** Regression coverage for the canonical pairing state now consumed by the UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        StoreProvider.setKeySender(null)
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(emptyList()))
        StoreProvider.dispatch(Action.UpdatePairedDevices(emptyList()))
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    @After
    fun tearDown() {
        StoreProvider.setKeySender(null)
        Dispatchers.resetMain()
    }

    @Test
    fun scanRequestDoesNotClaimScanStarted() {
        val sender = RecordingKeySender()
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.StartDiscovery)

        assertTrue(sender.commands.contains(KeyCommand.StartDiscovery))
        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
    }

    @Test
    fun failedScanResultRemainsVisible() {
        val sender =
            RecordingKeySender {
                if (it == KeyCommand.StartDiscovery) {
                    CommandResult.Failure(
                        com.augustusmachin.android_bt_kbmouse.store.CommandError(
                            com.augustusmachin.android_bt_kbmouse.store.CommandErrorCode.DISCOVERY_FAILED,
                            "Failed to start scan",
                        ),
                    )
                } else {
                    CommandResult.Success
                }
            }
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.StartDiscovery)

        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
        assertEquals("Failed to start scan", StoreProvider.asStateFlow().value.connection.message)
    }

    @Test
    fun connectionStateChangesOnlyFromBackendUpdate() {
        val sender = RecordingKeySender()
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(device.address).thenReturn("01:23:45:67:89:AB")
        StoreProvider.setKeySender(sender)

        StoreProvider.dispatch(Action.ConnectDevice(device))
        assertNull(StoreProvider.asStateFlow().value.connection.connectedDevice)

        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
        assertEquals(device, StoreProvider.asStateFlow().value.connection.connectedDevice)
    }

    @Test
    fun serviceDisconnectClearsConnectionWhenPublished() {
        val device = Mockito.mock(BluetoothDevice::class.java)
        Mockito.`when`(device.address).thenReturn("DE:AD:BE:EF:00:01")
        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))

        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateMessage("Disconnected"))

        assertNull(StoreProvider.asStateFlow().value.connection.connectedDevice)
        assertEquals("Disconnected", StoreProvider.asStateFlow().value.connection.message)
    }
}
