package com.augustusmachin.android_bt_kbmouse

import android.bluetooth.BluetoothDevice
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class UiComposeTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeService: IBluetoothService {
        var startCalls = 0
        override fun getLastDeviceAddress(): String? = null
        override fun setEventListener(l: BluetoothService.ServiceEventListener) {}
        override fun startDiscovery() { startCalls++ }
        override fun stopDiscovery() {}
        override fun getDiscoveredDevices(): List<BluetoothDevice> = emptyList()
        override fun getPairedDevices(): List<BluetoothDevice> = emptyList()
        override fun pairDevice(device: BluetoothDevice) {}
        override fun connectDevice(device: BluetoothDevice) {}
        override fun disconnectDevice() {}
        override fun setDefaultDevice(device: BluetoothDevice) {}
        override fun getAlias(device: BluetoothDevice): String? = null
        override fun setAlias(device: BluetoothDevice, alias: String) {}
        override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {}
        override fun sendKeyPress(keyCode: Byte, modifiers: Int) {}
        override fun sendMouseMove(dx: Int, dy: Int) {}
        override fun sendLeftClick() {}
        override fun sendRightClick() {}
        override fun sendMiddleClick() {}
        override fun sendScroll(delta: Int) {}
        override fun sendScrollH(delta: Int) {}
        override fun pressKey(keyCode: Byte, modifiers: Int) {}
        override fun releaseKey(keyCode: Byte) {}
        override fun setModifiers(mods: Int) {}
    }

    @Composable
    private fun TestScan() {
        Button(onClick = { com.augustusmachin.android_bt_kbmouse.store.StoreProvider.dispatch(com.augustusmachin.android_bt_kbmouse.store.Action.StartDiscovery) }) { Text("Scan for devices") }
        val appState by com.augustusmachin.android_bt_kbmouse.store.StoreProvider.asStateFlow().collectAsState()
        val msg = appState.connection.message
        if (msg != null) Text(msg)
    }

    @Test
    fun scanButtonShowsMessage() {
        // install a fake KeySender so StartDiscovery actually calls our fake service
        com.augustusmachin.android_bt_kbmouse.store.StoreProvider.setKeySender(object : com.augustusmachin.android_bt_kbmouse.store.KeySender {
            override fun startDiscovery() { FakeService().startDiscovery() }
        })
        composeRule.setContent { TestScan() }
        composeRule.onNodeWithText("Scan for devices").performClick()
        composeRule.onNodeWithText("Scanning for devices...").assertExists()
    }

    @Test
    fun sanityRuns() {
        org.junit.Assert.assertTrue(true)
    }
}
