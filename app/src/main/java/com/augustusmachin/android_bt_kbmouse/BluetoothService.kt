package com.augustusmachin.android_bt_kbmouse

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val DEFAULT_RECONNECT_BASE_MS = 2000L
private const val HID_PROFILE_UUID = "00001124-0000-1000-8000-00805f9b34fb"

class BluetoothService : Service(), IBluetoothService {
    companion object {
        private const val ACTION_CONNECT = "com.augustusmachin.android_bt_kbmouse.ACTION_CONNECT"
        private const val ACTION_DISCONNECT = "com.augustusmachin.android_bt_kbmouse.ACTION_DISCONNECT"
        private const val ACTION_FORGET = "com.augustusmachin.android_bt_kbmouse.ACTION_FORGET"
        const val ACTION_MISSING_BLUETOOTH_CONNECT =
            "com.augustusmachin.android_bt_kbmouse.ACTION_MISSING_BLUETOOTH_CONNECT"
    }

    private val devicePrefs by lazy { BtDevicePrefs(this) }
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val discovery by lazy { DiscoveryController(this) { bluetoothAdapter } }
    private val foreground by lazy {
        ServiceForegroundController(this, ACTION_CONNECT, ACTION_DISCONNECT, ACTION_FORGET)
    }
    private var bluetoothHidProfile: BluetoothProfile? = null
    private var bluetoothHidModule: BluetoothHidModule? = null
    private var hid: BluetoothHidDevice? = null
    private var lastDeviceAddress: String? = null
    private var reconnectAttempt: Int = 0
    private var btEnabled: Boolean = true
    private var reconnectPending: Boolean = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var connectedDevice: BluetoothDevice? = null
    private var lastTargetDevice: BluetoothDevice? = null
    private var manualDisconnect = false

    @Volatile private var hidSimplified: Boolean = true
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> discovery.onDeviceFound(deviceFromIntent(intent))
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> discovery.onDiscoveryFinished()
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> handleBondStateChanged(intent)
                    BluetoothAdapter.ACTION_STATE_CHANGED -> handleAdapterStateChanged(intent)
                }
            }
        }

    private fun deviceFromIntent(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        }

    private fun handleBondStateChanged(intent: Intent) {
        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
        val dev = deviceFromIntent(intent)
        DebugLog.log("BluetoothService", "BOND_STATE_CHANGED=$state")
        when (state) {
            BluetoothDevice.BOND_BONDED -> if (dev != null) onBondBonded(dev)
            BluetoothDevice.BOND_NONE -> if (dev != null) onBondRemoved(dev)
        }
    }

    private fun onBondBonded(dev: BluetoothDevice) {
        val address = safeDeviceAddress(dev) ?: return
        lastTargetDevice = dev
        lastDeviceAddress = address
        devicePrefs.setLastDevice(address)
        scheduleReconnect(0)
        StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
    }

    private fun onBondRemoved(dev: BluetoothDevice) {
        val address = safeDeviceAddress(dev) ?: return
        DebugLog.log("BluetoothService", "BOND_NONE for $address")
        StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
        if (safeDeviceAddress(connectedDevice) == address) {
            connectedDevice = null
            publishConnectedDevice(null)
            StoreProvider.dispatch(Action.UpdateMessage("Unpaired: $address"))
        }
        if (lastDeviceAddress == address) {
            devicePrefs.setLastDevice(null)
            lastDeviceAddress = null
            lastTargetDevice = null
            StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
        }
    }

    private fun handleAdapterStateChanged(intent: Intent) {
        when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
            BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                btEnabled = false
                DebugLog.log("BluetoothService", "Bluetooth OFF - pausing reconnect and clearing HID")
                reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                reconnectRunnable = null
                reconnectPending = true
                hid = null
                connectedDevice = null
                publishConnectedDevice(null)
            }
            BluetoothAdapter.STATE_ON -> {
                btEnabled = true
                DebugLog.log("BluetoothService", "Bluetooth ON - reacquiring HID proxy")
                val requested =
                    bluetoothAdapter?.getProfileProxy(
                        this,
                        profileListener,
                        BluetoothProfile.HID_DEVICE,
                    ) == true
                if (!requested) {
                    failClassicStartup("Android rejected the Classic HID profile-proxy request")
                    return
                }
                if (reconnectPending || connectedDevice == null) {
                    reconnectPending = false
                    scheduleReconnect(0)
                }
            }
        }
    }

    interface ServiceEventListener {
        fun onConnected(device: BluetoothDevice)

        fun onDisconnected(device: BluetoothDevice?)

        fun onInfo(message: String)

        fun onError(message: String)

        fun onLeds(leds: Int)
    }

    private var eventListener: ServiceEventListener? = null

    override fun setEventListener(l: ServiceEventListener?) {
        eventListener = l
    }

    override fun getConnectedDevice(): BluetoothDevice? = connectedDevice

    override fun getConnectedDeviceLabel(): String? = connectedDevice?.let(::connectedLabel)

    override fun getStartupState(): ClassicHidStartupState =
        bluetoothHidModule?.currentStartupState() ?: ClassicHidStartupRegistry.state

    private val profileListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    failClassicStartup("Classic HID requires Android 9 (API 28) or newer")
                    return
                }
                if (!hasClassicPermission()) {
                    StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                    failClassicStartup("Bluetooth connect permission is unavailable; Classic HID cannot initialize")
                    reportMissingBluetoothConnect()
                    return
                }

                bluetoothHidProfile = proxy
                hid = proxy as BluetoothHidDevice
                DebugLog.log("BluetoothService", "HID service connected")
                eventListener?.onInfo("HID profile proxy connected; registering app")
                val module = BluetoothHidModule()
                bluetoothHidModule = module
                module.listener = hidEventListener()
                val simplified = hidSimplified
                eventListener?.onInfo("Registering HID app (simplified=$simplified)")
                when (val result = module.registerApp(proxy, simplified)) {
                    HidRegistrationRequestResult.Accepted -> Unit
                    HidRegistrationRequestResult.Rejected ->
                        failClassicStartup("Classic HID app registration was rejected immediately")
                    is HidRegistrationRequestResult.PermissionDenied -> {
                        StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                        failClassicStartup(result.message)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidProfile = null
                    hid = null
                    ClassicHidStartupRegistry.publish(
                        ClassicHidStartupState.Failed("Classic HID profile proxy disconnected"),
                    )
                    eventListener?.onError("Classic HID profile proxy disconnected")
                }
            }
        }

    private fun hidEventListener(): BluetoothHidModule.HidEventListenerExt =
        object : BluetoothHidModule.HidEventListenerExt {
            override fun onAppStatus(registered: Boolean) {
                if (registered) {
                    onHidAppRegistered()
                } else {
                    failClassicStartup("HID app registration failed")
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> onHidConnected(device)
                    BluetoothProfile.STATE_DISCONNECTED -> onHidDisconnected(device)
                }
            }

            override fun onError(message: String) {
                eventListener?.onError(message)
            }

            override fun onLeds(leds: Int) {
                eventListener?.onLeds(leds)
            }
        }

    private fun onHidConnected(device: BluetoothDevice) {
        val address = safeDeviceAddress(device)
        eventListener?.onInfo("HID state CONNECTED ${address ?: "Bluetooth host"}")
        connectedDevice = device
        reconnectAttempt = 0
        if (hasScanPermission()) {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "cancelDiscovery permission failure: ${se.message}")
                StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            }
        }
        devicePrefs.setConnectedName(connectedLabel(device))
        publishConnectedDevice(device)
        refreshQsTile()
        eventListener?.onConnected(device)
    }

    private fun onHidDisconnected(device: BluetoothDevice) {
        val address = safeDeviceAddress(device)
        eventListener?.onInfo("HID state DISCONNECTED ${address ?: "Bluetooth host"}")
        connectedDevice = null
        devicePrefs.setConnectedName(null)
        publishConnectedDevice(null)
        refreshQsTile()
        eventListener?.onDisconnected(device)
        if (!manualDisconnect) scheduleReconnect()
    }

    // Fail-fast startup intentionally exits as soon as a required Android primitive is unavailable.
    @Suppress("ReturnCount")
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        // This is the authoritative boundary for a NEW Classic service instance. Activity
        // recreation/rebind must not reset a healthy service's durable registration state.
        ClassicHidStartupRegistry.beginActivation()
        if (!startInForeground()) return

        val btMgr = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter =
            btMgr?.adapter ?: run {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
        if (bluetoothAdapter == null) {
            failClassicStartup("Bluetooth adapter is unavailable")
            return
        }

        val filter =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val notifFilter =
            IntentFilter().apply {
                addAction(ACTION_CONNECT)
                addAction(ACTION_DISCONNECT)
                addAction(ACTION_FORGET)
            }
        ContextCompat.registerReceiver(this, notifActionReceiver, notifFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshQsTile()
        lastDeviceAddress = devicePrefs.getLastDevice()
        if (lastDeviceAddress != null) {
            DebugLog.log("BluetoothService", "remembered last_device=$lastDeviceAddress")
        }
        serviceScope.launch {
            SettingsManager.flow(this@BluetoothService).collect { settings ->
                hidSimplified = settings.hidSimplified
            }
        }
        val requested = bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE) == true
        if (!requested) {
            failClassicStartup("Android rejected the Classic HID profile-proxy request")
            return
        }
        StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiverSafely(receiver, "Bluetooth receiver")
        unregisterReceiverSafely(notifActionReceiver, "notification action receiver")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasClassicPermission()) {
            try {
                val unregistered = hid?.unregisterApp()
                if (unregistered == false) {
                    DebugLog.e("BluetoothService", "HID unregisterApp was rejected during teardown")
                }
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "unregisterApp permission failure: ${se.message}")
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("BluetoothService", "unregisterApp failed during teardown: ${e.message}")
            }
        }
        bluetoothHidProfile?.let { profile ->
            try {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, profile)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("BluetoothService", "closeProfileProxy failed during teardown: ${e.message}")
            }
        }
        connectedDevice = null
        publishConnectedDevice(null)
        refreshQsTile()
        super.onDestroy()
    }

    private fun unregisterReceiverSafely(
        receiver: BroadcastReceiver,
        label: String,
    ) {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Teardown is idempotent: an already-unregistered receiver requires no recovery,
            // but the condition remains observable in diagnostics.
            DebugLog.e("BluetoothService", "$label was already unregistered: ${e.message}")
        }
    }

    override fun startDiscovery(): Boolean = discovery.startDiscovery()

    override fun stopDiscovery(): Boolean = discovery.stopDiscovery()

    override fun getDiscoveredDevices(): List<BluetoothDevice> = discovery.getDiscoveredDevices()

    override fun getPairedDevices(): List<BluetoothDevice> = discovery.getPairedDevices()

    override fun pairDevice(device: BluetoothDevice) {
        if (!hasClassicPermission()) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Bluetooth connect permission is unavailable; pairing was not started")
            reportMissingBluetoothConnect()
            return
        }
        try {
            if (!device.createBond()) {
                eventListener?.onError("Android rejected the Bluetooth pairing request")
            }
        } catch (se: SecurityException) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Pairing failed because Bluetooth permission was revoked")
            DebugLog.e("BluetoothService", "createBond permission failure: ${se.message}")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            val message = "Pairing failed: ${e.message ?: e.javaClass.simpleName}"
            DebugLog.e("BluetoothService", message)
            eventListener?.onError(message)
        }
    }

    private fun scheduleReconnect(delayMs: Long = 0) {
        if (manualDisconnect) return
        if (!btEnabled) {
            DebugLog.log("BluetoothService", "Bluetooth OFF; pausing reconnect")
            reconnectPending = true
            return
        }
        val target =
            lastTargetDevice ?: run {
                val addr = lastDeviceAddress ?: return
                try {
                    bluetoothAdapter?.getRemoteDevice(addr)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    DebugLog.e("BluetoothService", "Remembered Bluetooth address is invalid: ${e.message}")
                    null
                }
            } ?: return
        val attempt = (++reconnectAttempt).coerceAtLeast(1)
        val base = if (delayMs > 0) delayMs else DEFAULT_RECONNECT_BASE_MS
        val computed = ReconnectLogic.computeReconnectDelay(base, attempt)
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { attemptHidConnect(target, attempt) }
        reconnectRunnable = r
        DebugLog.log("BluetoothService", "scheduleReconnect attempt=$attempt in ${computed}ms")
        eventListener?.onInfo("Reconnect attempt #$attempt in ${computed}ms")
        mainHandler.postDelayed(r, computed)
    }

    private fun attemptHidConnect(
        target: BluetoothDevice,
        attempt: Int,
    ) {
        eventListener?.onInfo("Auto-reconnect attempt #$attempt")
        eventListener?.onInfo(linuxHostInitiatedGuidance())
        if (!requestHidConnect(target, "auto reconnect")) {
            scheduleReconnect()
        }
    }

    private fun onHidAppRegistered() {
        eventListener?.onInfo("HID app registered")
        val addr = lastDeviceAddress
        if (connectedDevice != null || addr == null) return
        val dev =
            try {
                bluetoothAdapter?.getRemoteDevice(addr)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val detail = e.message ?: e.javaClass.simpleName
                val message = "Remembered Bluetooth device could not be resolved: $detail"
                DebugLog.e("BluetoothService", message)
                eventListener?.onError(message)
                null
            } ?: return
        lastTargetDevice = dev
        reconnectAttempt = 0
        eventListener?.onInfo("Initiating connection request to remembered host")
        eventListener?.onInfo(linuxHostInitiatedGuidance())
        if (!requestHidConnect(dev, "immediate reconnect")) scheduleReconnect()
    }

    private fun requestHidConnect(
        target: BluetoothDevice,
        operation: String,
    ): Boolean {
        if (!hasClassicPermission()) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Bluetooth connect permission is unavailable; $operation aborted")
            reportMissingBluetoothConnect()
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            eventListener?.onError("Classic HID connect is unsupported below API 28")
            return false
        }
        val activeHid = hid
        if (activeHid == null) {
            eventListener?.onError("Classic HID profile is unavailable; $operation was not sent")
            return false
        }
        return try {
            if (activeHid.connect(target)) {
                true
            } else {
                eventListener?.onError("Android rejected the Classic HID $operation request")
                false
            }
        } catch (se: SecurityException) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Classic HID $operation failed because Bluetooth permission was revoked")
            DebugLog.e("BluetoothService", "hid.connect permission failure: ${se.message}")
            false
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            val message = "Classic HID $operation failed: ${e.message ?: e.javaClass.simpleName}"
            DebugLog.e("BluetoothService", message)
            eventListener?.onError(message)
            false
        }
    }

    override fun connectDevice(device: BluetoothDevice) {
        manualDisconnect = false
        reconnectAttempt = 0
        lastTargetDevice = device
        safeDeviceAddress(device)?.let { address ->
            devicePrefs.setLastDevice(address)
            lastDeviceAddress = address
        }
        eventListener?.onInfo("Requested HID connection")
        eventListener?.onInfo(linuxHostInitiatedGuidance())
        if (!requestHidConnect(device, "manual connect")) scheduleReconnect()
    }

    private fun disconnectHidFrom(target: BluetoothDevice): Boolean {
        if (!hasClassicPermission()) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Bluetooth connect permission is unavailable; disconnect was not sent")
            return false
        }
        val activeHid = hid
        if (activeHid == null) {
            eventListener?.onError("Classic HID profile is unavailable; disconnect was not sent")
            return false
        }
        return try {
            if (activeHid.disconnect(target)) {
                true
            } else {
                eventListener?.onError("Android rejected the Classic HID disconnect request")
                false
            }
        } catch (se: SecurityException) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            eventListener?.onError("Classic HID disconnect failed because Bluetooth permission was revoked")
            DebugLog.e("BluetoothService", "hid.disconnect permission failure: ${se.message}")
            false
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            val message = "Classic HID disconnect failed: ${e.message ?: e.javaClass.simpleName}"
            DebugLog.e("BluetoothService", message)
            eventListener?.onError(message)
            false
        }
    }

    override fun disconnectDevice() {
        manualDisconnect = true
        val target = connectedDevice ?: lastTargetDevice
        if (target != null) disconnectHidFrom(target)
        connectedDevice = null
        devicePrefs.setConnectedName(null)
        publishConnectedDevice(null)
        refreshQsTile()
    }

    @SuppressLint("NewApi")
    private fun hidDevice(): BluetoothHidDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hid ?: bluetoothHidProfile as? BluetoothHidDevice
        } else {
            null
        }

    private fun connectLast(): Boolean {
        val addr = lastDeviceAddress ?: return false
        val dev =
            try {
                bluetoothAdapter?.getRemoteDevice(addr)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                DebugLog.e("BluetoothService", "Could not resolve remembered host: ${e.message}")
                eventListener?.onError("Remembered Bluetooth host could not be resolved")
                null
            } ?: return false
        connectDevice(dev)
        return true
    }

    private val notifActionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_CONNECT ->
                        if (!connectLast()) {
                            eventListener?.onError(
                                "No remembered Bluetooth host is available",
                            )
                        }
                    ACTION_DISCONNECT -> disconnectDevice()
                    ACTION_FORGET -> {
                        devicePrefs.clearLastAndConnected()
                        lastDeviceAddress = null
                        lastTargetDevice = null
                        refreshQsTile()
                        eventListener?.onInfo("Forgot the remembered Bluetooth host")
                    }
                }
            }
        }

    private fun refreshQsTile() = foreground.refreshQsTile()

    private fun startInForeground(): Boolean = foreground.startInForeground()

    private fun connectedLabel(device: BluetoothDevice): String {
        val address = safeDeviceAddress(device)
        if (!hasClassicPermission()) return address ?: "Bluetooth host"
        val name =
            try {
                device.name
            } catch (se: SecurityException) {
                StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                DebugLog.e("BluetoothService", "device.name permission failure: ${se.message}")
                null
            }
        return name?.takeIf { it.isNotBlank() } ?: address ?: "Bluetooth host"
    }

    private fun safeDeviceAddress(device: BluetoothDevice?): String? {
        if (device == null) return null
        return try {
            device.address
        } catch (se: SecurityException) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            DebugLog.e("BluetoothService", "device.address permission failure: ${se.message}")
            null
        }
    }

    private fun publishConnectedDevice(device: BluetoothDevice?) {
        StoreProvider.dispatch(Action.UpdateConnectedDevice(device))
        val address = safeDeviceAddress(device)
        StoreProvider.dispatch(Action.UpdateConnectedDeviceAddress(address))
        StoreProvider.dispatch(Action.UpdateConnectedDeviceLabel(device?.let(::connectedLabel)))
    }

    private fun reportMissingBluetoothConnect() = foreground.reportMissingBluetoothConnect()

    private fun hasClassicPermission(): Boolean =
        PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))

    private fun hasScanPermission(): Boolean =
        PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForScan(Build.VERSION.SDK_INT))

    private fun linuxHostInitiatedGuidance(): String =
        "Linux/BlueZ hosts may need to initiate HID from the host. Use the documented D-Bus " +
            "ConnectProfile(HID, $HID_PROFILE_UUID) procedure first; " +
            "use bluetoothctl connect only as a diagnostic fallback."

    private fun failClassicStartup(message: String) {
        DebugLog.e("BluetoothService", message)
        ClassicHidStartupRegistry.publish(ClassicHidStartupState.Failed(message))
        StoreProvider.dispatch(Action.UpdateMessage(message))
        eventListener?.onError(message)
        stopSelf()
    }

    override fun getLastDeviceAddress(): String? = lastDeviceAddress

    override fun setDefaultDevice(device: BluetoothDevice) {
        val address = safeDeviceAddress(device)
        if (address == null) {
            eventListener?.onError("Default device could not be saved because its Bluetooth address is unavailable")
            return
        }
        devicePrefs.setLastDevice(address)
        lastDeviceAddress = address
        lastTargetDevice = device
        StoreProvider.dispatch(Action.UpdateDefaultDevice(address))
        eventListener?.onInfo("Default device: $address")
        refreshQsTile()
    }

    override fun getAlias(device: BluetoothDevice): String? = safeDeviceAddress(device)?.let(devicePrefs::getAlias)

    override fun setAlias(
        device: BluetoothDevice,
        alias: String,
    ) {
        val address = safeDeviceAddress(device)
        if (address == null) {
            eventListener?.onError("Device alias could not be saved because its Bluetooth address is unavailable")
            return
        }
        devicePrefs.setAlias(address, alias)
        eventListener?.onInfo("Renamed: $alias")
    }

    override fun forgetDevice(
        device: BluetoothDevice,
        unpair: Boolean,
    ) {
        val address = safeDeviceAddress(device)
        if (address == null) {
            eventListener?.onError("Device could not be forgotten because its Bluetooth address is unavailable")
            return
        }
        if (safeDeviceAddress(connectedDevice) == address) {
            manualDisconnect = true
            disconnectHidFrom(device)
            connectedDevice = null
            devicePrefs.setConnectedName(null)
            publishConnectedDevice(null)
            refreshQsTile()
            eventListener?.onDisconnected(device)
        }
        if (lastDeviceAddress == address) {
            devicePrefs.setLastDevice(null)
            lastDeviceAddress = null
            lastTargetDevice = null
            StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
        }
        devicePrefs.removeAlias(address)
        var unpairFailed = false
        if (unpair) {
            try {
                val method = device.javaClass.getMethod("removeBond")
                method.isAccessible = true
                val accepted = method.invoke(device) as? Boolean
                if (accepted == false) {
                    unpairFailed = true
                    eventListener?.onError("Device was forgotten locally, but Android rejected the unpair request")
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                unpairFailed = true
                val detail = e.message ?: e.javaClass.simpleName
                val message = "Device was forgotten locally, but system unpair failed: $detail"
                DebugLog.e("BluetoothService", message)
                eventListener?.onError(message)
            }
        }
        StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
        if (!unpairFailed) eventListener?.onInfo("Forgot $address")
    }

    private val reportSender by lazy {
        HidReportSender(
            isSimplified = { hidSimplified },
            transport =
                BluetoothHidTransport(
                    context = this,
                    currentDevice = { connectedDevice },
                    currentHid = { hidDevice() },
                    onError = { msg -> eventListener?.onError(msg) },
                ),
        )
    }

    override fun setModifiers(mods: Int) = reportSender.setModifiers(mods)

    override fun sendKeyPress(
        keyCode: Byte,
        modifiers: Int,
    ) = reportSender.sendKeyPress(keyCode, modifiers)

    override fun pressKey(
        keyCode: Byte,
        modifiers: Int,
    ) = reportSender.pressKey(keyCode, modifiers)

    override fun releaseKey(keyCode: Byte) = reportSender.releaseKey(keyCode)

    override fun sendMouseMove(
        dx: Int,
        dy: Int,
    ) = reportSender.sendMouseMove(dx, dy)

    override fun mouseButtonDown(button: Int) = reportSender.mouseButtonDown(button)

    override fun mouseButtonUp() = reportSender.mouseButtonUp()

    override fun sendLeftClick() = reportSender.sendLeftClick()

    override fun sendRightClick() = reportSender.sendRightClick()

    override fun sendMiddleClick() = reportSender.sendMiddleClick()

    override fun sendScroll(delta: Int) = reportSender.sendScroll(delta)

    override fun sendScrollH(delta: Int) = reportSender.sendScrollH(delta)

    override fun onBind(intent: Intent): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}
