from pathlib import Path


def replace(path_str: str, old: str, new: str, expected: int = 1) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path_str}: expected {expected} matches, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


service = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt"
replace(
    service,
    "                val requested = bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE) == true",
    """                val requested =
                    bluetoothAdapter?.getProfileProxy(
                        this,
                        profileListener,
                        BluetoothProfile.HID_DEVICE,
                    ) == true""",
)
replace(
    service,
    '                val message = "Remembered Bluetooth device could not be resolved: ${e.message ?: e.javaClass.simpleName}"',
    (
        "                val detail = e.message ?: e.javaClass.simpleName\n"
        '                val message = "Remembered Bluetooth device could not be resolved: $detail"'
    ),
)
replace(
    service,
    '            "ConnectProfile(HID, $HID_PROFILE_UUID) procedure first; use bluetoothctl connect only as a diagnostic fallback."',
    (
        '            "ConnectProfile(HID, $HID_PROFILE_UUID) procedure first; " +\n'
        '            "use bluetoothctl connect only as a diagnostic fallback."'
    ),
)
replace(
    service,
    '                val message = "Device was forgotten locally, but system unpair failed: ${e.message ?: e.javaClass.simpleName}"',
    (
        "                val detail = e.message ?: e.javaClass.simpleName\n"
        '                val message = "Device was forgotten locally, but system unpair failed: $detail"'
    ),
)
replace(
    service,
    '    @SuppressLint("UnspecifiedRegisterReceiverFlag")\n    override fun onCreate()',
    """    // Fail-fast startup intentionally exits as soon as a required Android primitive is unavailable.
    @Suppress("ReturnCount")
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate()""",
)

pairing = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/PairingScreen.kt"
replace(
    pairing,
    "                    activity != null && denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }",
    """                    activity != null &&
                        denied.any {
                            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                        }""",
)
replace(
    pairing,
    "private fun resolveDeviceRows(",
    """// One permission-safe mapper intentionally handles both discovered and paired device variants.
@Suppress("CyclomaticComplexMethod")
private fun resolveDeviceRows(""",
)

settings = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/SettingsScreen.kt"
replace(
    settings,
    "@Composable\nprivate fun PointerSettingsSection(",
    """// This Compose section keeps tightly related pointer controls and transient slider state together.
@Suppress("LongMethod")
@Composable
private fun PointerSettingsSection(""",
)
replace(
    settings,
    "@Composable\nprivate fun InputBehaviorSection(",
    """// This Compose section is declarative UI wiring; splitting it would separate coupled settings controls.
@Suppress("LongMethod")
@Composable
private fun InputBehaviorSection(""",
)
replace(
    settings,
    "@Composable\nprivate fun ImeOverridesSection(",
    """// Compose state, Android services, and the update callback are distinct UI dependencies here.
@Suppress("LongParameterList")
@Composable
private fun ImeOverridesSection(""",
)

main_screen = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/MainScreen.kt"
replace(
    main_screen,
    "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun MainScreen()",
    """// Root Compose orchestration keeps navigation, snackbar, permission, and live backend state in one owner.
@Suppress("LongMethod")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen()""",
)
replace(
    main_screen,
    "@Composable\nprivate fun MainTopBar(",
    """// The top bar receives independent navigation and backend-status inputs from the root screen.
@Suppress("LongParameterList")
@Composable
private fun MainTopBar(""",
)
replace(
    main_screen,
    "@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nprivate fun StatusTopBar(",
    """// StatusTopBar is a thin Compose adapter over explicit runtime and navigation state.
@Suppress("LongParameterList")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StatusTopBar(""",
)

ble = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BleHogpService.kt"
replace(
    ble,
    '    @SuppressLint("MissingPermission")\n    override fun onCreate()',
    """    // Each startup prerequisite fails closed immediately; early exits are the safety contract.
    @Suppress("ReturnCount")
    @SuppressLint("MissingPermission")
    override fun onCreate()""",
)
replace(
    ble,
    '    @SuppressLint("MissingPermission")\n    private fun registerNextGattService()',
    """    // Registration is a fail-fast state-machine step; each invalid prerequisite terminates startup.
    @Suppress("ReturnCount")
    @SuppressLint("MissingPermission")
    private fun registerNextGattService()""",
)
replace(
    ble,
    '    @SuppressLint("MissingPermission")\n    private fun notifyReport(',
    """    // Explicit guard exits keep every delivery failure typed and prevent fall-through success.
    @Suppress("LongMethod", "NestedBlockDepth", "ReturnCount")
    @SuppressLint("MissingPermission")
    private fun notifyReport(""",
)

transport = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothHidTransport.kt"
replace(
    transport,
    '    @SuppressLint("MissingPermission", "NewApi")\n    override fun send(',
    """    // Report delivery deliberately fails at the first missing prerequisite with a typed result.
    @Suppress("ReturnCount")
    @SuppressLint("MissingPermission", "NewApi")
    override fun send(""",
)

lifecycle = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BackendLifecycleController.kt"
replace(
    lifecycle,
    "    @Synchronized\n    fun start(mode: BackendMode): Boolean {",
    """    // Transactional startup exits immediately on any failed stage so partial state is rolled back.
    @Suppress("ReturnCount")
    @Synchronized
    fun start(mode: BackendMode): Boolean {""",
)
replace(
    lifecycle,
    "    @Synchronized\n    fun switchTo(target: BackendMode): Boolean {",
    """    // Switching is intentionally linear: preserve the active backend unless stop succeeds.
    @Suppress("ReturnCount")
    @Synchronized
    fun switchTo(target: BackendMode): Boolean {""",
)

discovery = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/DiscoveryController.kt"
replace(
    discovery,
    "    fun startDiscovery(): Boolean {",
    """    // Discovery rejects each unavailable prerequisite explicitly instead of publishing fake scan state.
    @Suppress("ReturnCount")
    fun startDiscovery(): Boolean {""",
)
replace(
    discovery,
    "    fun stopDiscovery(): Boolean {",
    """    // Stop reports adapter/permission failures explicitly and always reconciles scanning state.
    @Suppress("ReturnCount")
    fun stopDiscovery(): Boolean {""",
)

main_activity = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/MainActivity.kt"
replace(
    main_activity,
    "            override fun onServiceConnected(\n",
    """            // Binding is a fail-closed startup transaction; each rejected stage stops immediately.
            @Suppress("LongMethod", "ReturnCount")
            override fun onServiceConnected(
""",
    expected=2,
)

gatt_profile = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BleHogpGattProfile.kt"
replace(
    gatt_profile,
    "internal object BleHogpGattProfileBuilder {\n",
    """internal object BleHogpGattProfileBuilder {
    private const val HID_VERSION_LSB: Byte = 0x11
    private const val HID_VERSION_MSB: Byte = 0x01
    private const val HID_COUNTRY_CODE: Byte = 0x00
    private const val HID_INFORMATION_FLAGS: Byte = 0x02
""",
)
replace(
    gatt_profile,
    "byteArrayOf(0x11, 0x01, 0x00, 0x02)",
    "byteArrayOf(HID_VERSION_LSB, HID_VERSION_MSB, HID_COUNTRY_CODE, HID_INFORMATION_FLAGS)",
)

for sender in [
    "app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/BluetoothKeySender.kt",
    "app/src/main/java/com/augustusmachin/android_bt_kbmouse/store/BleHogpKeySender.kt",
]:
    path = Path(sender)
    text = path.read_text()
    anchor = (
        "/** Explicit Classic HID command bridge. */"
        if sender.endswith("BluetoothKeySender.kt")
        else "/** Explicit BLE HOGP command bridge with no Classic-operation no-ops. */"
    )
    if text.count(anchor) != 1:
        raise SystemExit(f"{sender}: expected one sender anchor, found {text.count(anchor)}")
    constants = (
        "private const val MOUSE_BUTTON_LEFT = 0x01\n"
        "private const val MOUSE_BUTTON_RIGHT = 0x02\n"
        "private const val MOUSE_BUTTON_MIDDLE = 0x04\n\n"
    )
    text = text.replace(anchor, constants + anchor, 1)
    for old, new in [
        ("click(0x01)", "click(MOUSE_BUTTON_LEFT)"),
        ("click(0x02)", "click(MOUSE_BUTTON_RIGHT)"),
        ("click(0x04)", "click(MOUSE_BUTTON_MIDDLE)"),
    ]:
        if text.count(old) != 1:
            raise SystemExit(f"{sender}: expected one {old}, found {text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text)
