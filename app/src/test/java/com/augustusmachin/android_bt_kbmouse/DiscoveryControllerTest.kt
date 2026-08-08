package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class DiscoveryControllerTest {
    private val context = Mockito.mock(Context::class.java)
    private val adapter = Mockito.mock(BluetoothAdapter::class.java)

    @Before
    fun setUp() {
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdatePermissionsValid(true))
    }

    @After
    fun tearDown() {
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdatePermissionsValid(true))
    }

    @Test
    fun `failed adapter start leaves scanning false`() {
        Mockito.`when`(adapter.isDiscovering).thenReturn(false)
        Mockito.`when`(adapter.startDiscovery()).thenReturn(false)
        val controller = controller(granted = true)

        assertFalse(controller.startDiscovery())
        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
        assertTrue(StoreProvider.asStateFlow().value.connection.message!!.contains("Failed to start scan"))
    }

    @Test
    fun `successful adapter start is the only path that marks scanning true`() {
        Mockito.`when`(adapter.isDiscovering).thenReturn(false)
        Mockito.`when`(adapter.startDiscovery()).thenReturn(true)
        val controller = controller(granted = true)

        assertTrue(controller.startDiscovery())
        assertTrue(StoreProvider.asStateFlow().value.connection.isScanning)
    }

    @Test
    fun `permission denial never calls adapter start`() {
        val controller = controller(granted = false)

        assertFalse(controller.startDiscovery())
        assertFalse(StoreProvider.asStateFlow().value.connection.isScanning)
        Mockito.verify(adapter, Mockito.never()).startDiscovery()
    }

    @Test
    fun `paired devices permission denial is visible and marks permissions invalid`() {
        val controller = controller(granted = false)

        assertTrue(controller.getPairedDevices().isEmpty())
        assertFalse(StoreProvider.asStateFlow().value.backend.permissionsValid)
        assertTrue(
            StoreProvider.asStateFlow().value.connection.message!!
                .contains("paired devices cannot be read"),
        )
    }

    @Test
    fun `missing adapter is visible instead of looking like an empty paired list`() {
        val controller =
            DiscoveryController(
                context = context,
                adapter = { null },
                sdkInt = 34,
                hasPermissions = { true },
            )

        assertTrue(controller.getPairedDevices().isEmpty())
        assertTrue(StoreProvider.asStateFlow().value.connection.message!!.contains("Bluetooth adapter is unavailable"))
    }

    @Test
    fun `legacy api permission plan is used instead of Android 12 bluetooth permissions`() {
        val required = mutableListOf<List<String>>()
        Mockito.`when`(adapter.isDiscovering).thenReturn(false)
        Mockito.`when`(adapter.startDiscovery()).thenReturn(true)
        val controller =
            DiscoveryController(
                context = context,
                adapter = { adapter },
                sdkInt = 30,
                hasPermissions = {
                    required += it
                    true
                },
            )

        assertTrue(controller.startDiscovery())
        assertTrue(required.single().contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(required.single().contains(android.Manifest.permission.BLUETOOTH_SCAN))
    }

    private fun controller(granted: Boolean): DiscoveryController =
        DiscoveryController(
            context = context,
            adapter = { adapter },
            sdkInt = 34,
            hasPermissions = { granted },
        )
}
