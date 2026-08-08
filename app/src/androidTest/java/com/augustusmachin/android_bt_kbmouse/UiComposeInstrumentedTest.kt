package com.augustusmachin.android_bt_kbmouse

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.CommandResult
import com.augustusmachin.android_bt_kbmouse.store.KeyCommand
import com.augustusmachin.android_bt_kbmouse.store.KeySender
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class UiComposeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetBefore() = resetStore()

    @After
    fun resetAfter() = resetStore()

    @Test
    fun disconnectedMainScreenBlocksKeyboardNavigationWithVisibleReason() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithText("Keyboard").performClick()
        composeRule.onNodeWithText("Connect a device first").assertExists()
    }

    @Test
    fun classicPairingKeepsClassicDeviceManagementControls() {
        StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.CLASSIC_HID))

        composeRule.setContent { BackendAwarePairingScreen() }

        composeRule.onNodeWithText("Scan for devices").assertExists()
        composeRule.onNodeWithText("Paired Devices").assertExists()
    }

    @Test
    fun blePairingUsesHostInitiatedWorkflowWithoutClassicActions() {
        StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.BLE_HOGP))
        StoreProvider.dispatch(
            Action.UpdateBackendRuntime(
                BackendRuntimeState.Ready(BackendMode.BLE_HOGP, BackendCapabilitySets.bleHogp),
            ),
        )

        composeRule.setContent { BackendAwarePairingScreen() }

        composeRule.onNodeWithText("BLE HOGP").assertExists()
        composeRule.onNodeWithText("host-initiated", substring = true).assertExists()
        composeRule.onNodeWithText("Scan for devices").assertDoesNotExist()
        composeRule.onNodeWithText("Paired Devices").assertDoesNotExist()
    }

    @Test
    fun bleStartupFailureIsVisibleOnPairingScreen() {
        StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.BLE_HOGP))
        StoreProvider.dispatch(
            Action.UpdateBackendRuntime(
                BackendRuntimeState.Failed(
                    BackendMode.BLE_HOGP,
                    BackendFailure(BackendFailureCode.BACKEND_INIT_FAILED, "BLE test startup failure"),
                ),
            ),
        )

        composeRule.setContent { BackendAwarePairingScreen() }

        composeRule.onNodeWithText("BLE test startup failure").assertExists()
        composeRule.onNodeWithText("Remediation:", substring = true).assertExists()
    }

    @Test
    fun failedScanMessageIsVisibleOnRealMainScreen() {
        composeRule.setContent { MainScreen() }
        composeRule.runOnIdle {
            StoreProvider.dispatch(Action.UpdateIsScanning(false))
            StoreProvider.dispatch(Action.UpdateMessage("Failed to start scan"))
        }

        composeRule.onNodeWithText("Failed to start scan").assertExists()
        composeRule.runOnIdle {
            assertTrue(!StoreProvider.asStateFlow().value.connection.isScanning)
        }
    }

    @Test
    fun bleMouseScreenExplainsUnsupportedScrolling() {
        StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.BLE_HOGP))
        StoreProvider.dispatch(
            Action.UpdateBackendRuntime(
                BackendRuntimeState.Ready(BackendMode.BLE_HOGP, BackendCapabilitySets.bleHogp),
            ),
        )

        composeRule.setContent { MouseScreen() }

        composeRule.onNodeWithText("Scrolling is unavailable", substring = true).assertExists()
    }

    @Test
    fun dragLockDisposalReleasesMouseButtonOnRealMouseScreen() {
        val sender = RecordingSender()
        StoreProvider.setKeySender(sender)
        StoreProvider.dispatch(
            Action.UpdateBackendRuntime(
                BackendRuntimeState.Ready(BackendMode.CLASSIC_HID, BackendCapabilitySets.classic),
            ),
        )

        composeRule.setContent { MouseScreen() }
        composeRule.onNodeWithText("Drag").performClick()
        composeRule.runOnIdle {
            assertTrue(sender.commands.any { it is KeyCommand.MouseButtonDown })
        }

        composeRule.setContent { Text("disposed") }
        composeRule.runOnIdle {
            assertTrue(sender.commands.any { it is KeyCommand.MouseButtonUp })
        }
    }

    private class RecordingSender : KeySender {
        override val backend = BackendMode.CLASSIC_HID
        override val capabilities = BackendCapabilitySets.classic
        val commands = mutableListOf<KeyCommand>()

        override fun execute(command: KeyCommand): CommandResult {
            commands += command
            return CommandResult.Success
        }
    }

    private fun resetStore() {
        StoreProvider.setKeySender(null)
        StoreProvider.dispatch(Action.UpdateSelectedBackend(BackendMode.CLASSIC_HID))
        StoreProvider.dispatch(Action.UpdateBackendRuntime(BackendRuntimeState.Stopped))
        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
        StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(null))
        StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(null))
        StoreProvider.dispatch(Action.UpdateMessage(null))
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
        StoreProvider.dispatch(Action.UpdateLocks(false, false))
    }
}
