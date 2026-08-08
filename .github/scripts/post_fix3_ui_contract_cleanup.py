from pathlib import Path


def replace(path_str: str, old: str, new: str, expected: int = 1) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path_str}: expected {expected} matches, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


keyboard = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/KeyboardScreen.kt"
replace(keyboard, "private const val KEY_CELL_HEIGHT_DP = 48", "internal const val KEY_CELL_HEIGHT_DP = 48")

for screen in [
    "app/src/main/java/com/augustusmachin/android_bt_kbmouse/FunctionKeysScreen.kt",
    "app/src/main/java/com/augustusmachin/android_bt_kbmouse/ExtendedKeysScreen.kt",
    "app/src/main/java/com/augustusmachin/android_bt_kbmouse/NavigationKeysScreen.kt",
]:
    replace(
        screen,
        "import com.augustusmachin.android_bt_kbmouse.store.StoreProvider\n",
        "import com.augustusmachin.android_bt_kbmouse.store.StoreProvider\n"
        "import com.augustusmachin.android_bt_kbmouse.store.isInputUsable\n",
    )
    replace(
        screen,
        "    val connected = appState.connection.connectedDevice != null",
        "    val connected = appState.isInputUsable()",
    )

function_keys = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/FunctionKeysScreen.kt"
replace(
    function_keys,
    "        Spacer(Modifier.height(48.dp))",
    "        Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp))",
)

extended = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/ExtendedKeysScreen.kt"
replace(
    extended,
    "        Spacer(Modifier.height(48.dp)) // match button height",
    "        Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp)) // match shared key-cell height",
)

navigation = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/NavigationKeysScreen.kt"
replace(
    navigation,
    "        Spacer(Modifier.height(48.dp))",
    "        Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp))",
)
replace(
    navigation,
    "        is NavCell.Empty -> Spacer(Modifier.height(48.dp)) // match button height",
    "        is NavCell.Empty -> Spacer(Modifier.height(KEY_CELL_HEIGHT_DP.dp)) // shared key-cell height",
)
replace(
    navigation,
    "                modifier = Modifier.fillMaxWidth(),\n                colors = if (handlers.scrollLockActive)",
    "                modifier = Modifier.fillMaxWidth().height(KEY_CELL_HEIGHT_DP.dp),\n"
    "                colors = if (handlers.scrollLockActive)",
)

settings = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsScreen.kt"
replace(
    settings,
    '''            DebugLog.e(
                "SettingsScreen",
                "$message (app-specific request also failed: ${first.message ?: first.javaClass.simpleName})",
            )
            message''',
    '''            val diagnostic =
                "$message (app-specific request also failed: ${first.message ?: first.javaClass.simpleName})"
            DebugLog.e("SettingsScreen", diagnostic)
            android.util.Log.e("SettingsScreen", diagnostic, second)
            message''',
)

source_contract = "app/src/test/java/com/augustusmachin/android_bt_kbmouse/NoSilentFailureSourceContractTest.kt"
replace(
    source_contract,
    '''            "com/augustusmachin/android_bt_kbmouse/ServiceForegroundController.kt",
            "com/augustusmachin/android_bt_kbmouse/BluetoothService.kt",''',
    '''            "com/augustusmachin/android_bt_kbmouse/ServiceForegroundController.kt",
            "com/augustusmachin/android_bt_kbmouse/DiscoveryController.kt",
            "com/augustusmachin/android_bt_kbmouse/BluetoothService.kt",''',
)
replace(
    source_contract,
    '''    @Test
    fun `production input runtime contains no blocking sleeps`() {''',
    '''    @Test
    fun `BLE startup failure remains durable and user visible`() {
        val ble = source("com/augustusmachin/android_bt_kbmouse/BleHogpService.kt")
        assertTrue(ble.contains("BtDevicePrefs(this).setLastRuntimeFailure(persisted)"))
        assertTrue(ble.contains("StoreProvider.dispatch(Action.UpdateMessage(persisted))"))
        assertTrue(ble.contains("ServiceNotifications.postRuntimeFailure("))
    }

    @Test
    fun `production input runtime contains no blocking sleeps`() {''',
)

ui_test = "app/src/androidTest/java/com/augustusmachin/android_bt_kbmouse/UiComposeInstrumentedTest.kt"
replace(
    ui_test,
    "import androidx.compose.material3.Text\nimport androidx.compose.runtime.mutableStateOf\n",
    "import androidx.compose.material3.Text\n"
    "import androidx.compose.runtime.CompositionLocalProvider\n"
    "import androidx.compose.runtime.mutableStateOf\n",
)
replace(
    ui_test,
    "import androidx.compose.ui.test.junit4.createAndroidComposeRule\n",
    "import androidx.compose.ui.platform.LocalDensity\n"
    "import androidx.compose.ui.test.fetchSemanticsNode\n"
    "import androidx.compose.ui.test.junit4.createAndroidComposeRule\n",
)
replace(
    ui_test,
    "import androidx.compose.ui.test.performClick\n",
    "import androidx.compose.ui.test.performClick\n"
    "import androidx.compose.ui.unit.Density\n",
)
replace(
    ui_test,
    "import org.junit.After\nimport org.junit.Assert.assertTrue\n",
    "import org.junit.After\n"
    "import org.junit.Assert.assertEquals\n"
    "import org.junit.Assert.assertTrue\n",
)
replace(
    ui_test,
    '''    @Test
    fun dragLockDisposalReleasesMouseButtonOnRealMouseScreen() {''',
    '''    @Test
    fun navigationGridUsesConsistentCellHeightAtNormalFontScale() {
        assertNavigationGridCellHeights(fontScale = 1.0f)
    }

    @Test
    fun navigationGridUsesConsistentCellHeightAtLargeFontScale() {
        assertNavigationGridCellHeights(fontScale = 1.6f)
    }

    @Test
    fun dragLockDisposalReleasesMouseButtonOnRealMouseScreen() {''',
)
replace(
    ui_test,
    '''    private fun installUsableClassicState(label: String?): RecordingSender {''',
    '''    private fun assertNavigationGridCellHeights(fontScale: Float) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale),
            ) {
                NavigationKeysScreen()
            }
        }

        val ctrlHeight = composeRule.onNodeWithText("Ctrl").fetchSemanticsNode().boundsInRoot.height
        val upHeight = composeRule.onNodeWithText("↑").fetchSemanticsNode().boundsInRoot.height
        assertEquals(ctrlHeight, upHeight, 1.0f)

        composeRule.onNodeWithText("❯").performClick()
        composeRule.waitForIdle()
        val scrollLockHeight = composeRule.onNodeWithText("Scrl Lk").fetchSemanticsNode().boundsInRoot.height
        assertEquals(ctrlHeight, scrollLockHeight, 1.0f)
    }

    private fun installUsableClassicState(label: String?): RecordingSender {''',
)
