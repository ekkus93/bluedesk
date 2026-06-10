package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val SDK_INT_TIRAMISU = 33
private const val SDK_INT_NOUGAT = 24
private const val DEFAULT_RECONNECT_BASE_MS = 2000L

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
    private val discoveredDevices = java.util.concurrent.CopyOnWriteArrayList<BluetoothDevice>()
    private var bluetoothHidProfile: BluetoothProfile? = null
    private var bluetoothHidModule: BluetoothHidModule? = null
    private var hid: BluetoothHidDevice? = null
    private var lastDeviceAddress: String? = null
    private var reconnectAttempt: Int = 0
    private var btEnabled: Boolean = true
    private var reconnectPending: Boolean = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                            }
                        device?.let {
                            if (!discoveredDevices.contains(it)) {
                                discoveredDevices.add(it)
                                DebugLog.log("BluetoothService", "FOUND ${it.address}")
                            }
                            StoreProvider.dispatch(Action.UpdateDiscoveredDevices(discoveredDevices.toList()))
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        DebugLog.log("BluetoothService", "discovery finished")
                        StoreProvider.dispatch(Action.UpdateIsScanning(false))
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                        val dev: BluetoothDevice? =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                            }
                        DebugLog.log("BluetoothService", "BOND_STATE_CHANGED=$state dev=${dev?.address}")
                        when (state) {
                            BluetoothDevice.BOND_BONDED ->
                                if (dev != null) {
                                    lastTargetDevice = dev
                                    lastDeviceAddress = dev.address
                                    devicePrefs.setLastDevice(dev.address)
                                    scheduleReconnect(0)
                                    StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
                                }
                            BluetoothDevice.BOND_NONE ->
                                if (dev != null) {
                                    DebugLog.log("BluetoothService", "BOND_NONE for ${dev.address}")
                                    StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
                                    if (connectedDevice?.address == dev.address) {
                                        connectedDevice = null
                                        StoreProvider.dispatch(Action.UpdateConnectedDevice(null))
                                        StoreProvider.dispatch(Action.UpdateMessage("Unpaired: ${dev.address}"))
                                    }
                                    if (lastDeviceAddress == dev.address) {
                                        devicePrefs.setLastDevice(null)
                                        lastDeviceAddress = null
                                        lastTargetDevice = null
                                        StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
                                    }
                                }
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        when (state) {
                            BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                                btEnabled = false
                                DebugLog.log("BluetoothService", "Bluetooth OFF - pausing reconnect and clearing HID")
                                reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                                reconnectRunnable = null
                                reconnectPending = true
                                hid = null
                            }
                            BluetoothAdapter.STATE_ON -> {
                                btEnabled = true
                                DebugLog.log(
                                    "BluetoothService",
                                    "Bluetooth ON - (re)acquiring HID proxy and resuming reconnect",
                                )
                                bluetoothAdapter?.getProfileProxy(
                                    this@BluetoothService,
                                    profileListener,
                                    BluetoothProfile.HID_DEVICE,
                                )
                                if (reconnectPending || connectedDevice == null) {
                                    reconnectPending = false
                                    scheduleReconnect(0)
                                }
                            }
                        }
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

    override fun setEventListener(l: ServiceEventListener) {
        eventListener = l
    }

    private val profileListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    // HID APIs require API level 28+. If running on older platforms, skip HID initialization.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                        DebugLog.e(
                            "BluetoothService",
                            "HID profile requires API 28+; skipping HID initialization on this OS",
                        )
                        eventListener?.onError("HID not supported on this Android version")
                        return
                    }
                    // Require BLUETOOTH_CONNECT permission before using HID APIs
                    val hasBtConnect =
                        ContextCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!hasBtConnect) {
                        DebugLog.e(
                            "BluetoothService",
                            "BLUETOOTH_CONNECT not granted; reporting and skipping HID initialization " +
                                "to avoid SecurityException",
                        )
                        eventListener?.onError("Missing BLUETOOTH_CONNECT permission - HID disabled")
                        reportMissingBluetoothConnect()
                        return
                    }

                    bluetoothHidProfile = proxy
                    hid = proxy as BluetoothHidDevice
                    DebugLog.log("BluetoothService", "HID service connected")
                    eventListener?.onInfo("HID profile proxy connected; registering app")
                    bluetoothHidModule =
                        BluetoothHidModule().also { module ->
                            module.listener =
                                object : BluetoothHidModule.HidEventListenerExt {
                                    override fun onAppStatus(registered: Boolean) {
                                        if (!registered) {
                                            eventListener?.onError("HID app registration failed")
                                        } else {
                                            eventListener?.onInfo("HID app registered")
                                            val addr = lastDeviceAddress
                                            if (connectedDevice == null && addr != null) {
                                                try {
                                                    val dev = bluetoothAdapter?.getRemoteDevice(addr)
                                                    if (dev != null) {
                                                        lastTargetDevice = dev
                                                        reconnectAttempt = 0
                                                        DebugLog.log("BluetoothService", "auto reconnect now to $addr")
                                                        eventListener?.onInfo("Initiating connection request to $addr")
                                                        eventListener?.onInfo(
                                                            "Some Linux/BlueZ hosts still require " +
                                                                "the host to initiate the HID connection " +
                                                                "with bluetoothctl connect " +
                                                                "<this phone's Bluetooth address>.",
                                                        )
                                                        // Ensure BLUETOOTH_CONNECT is available
                                                        // before attempting connect
                                                        val hasBtConnect =
                                                            ContextCompat.checkSelfPermission(
                                                                this@BluetoothService,
                                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                            ) ==
                                                                android.content.pm.PackageManager.PERMISSION_GRANTED
                                                        if (hasBtConnect) {
                                                            try {
                                                                if (android.os.Build.VERSION.SDK_INT >=
                                                                    android.os.Build.VERSION_CODES.P
                                                                ) {
                                                                    hid?.connect(dev)
                                                                } else {
                                                                    DebugLog.e(
                                                                        "BluetoothService",
                                                                        "HID connect not supported on " +
                                                                            "API < 28; skipping",
                                                                    )
                                                                }
                                                            } catch (se: SecurityException) {
                                                                DebugLog.e(
                                                                    "BluetoothService",
                                                                    "hid.connect SecurityException: ${se.message}",
                                                                )
                                                                eventListener?.onError(
                                                                    "HID connect failed due to missing permission",
                                                                )
                                                            } catch (e: Exception) {
                                                                DebugLog.e(
                                                                    "BluetoothService",
                                                                    "immediate reconnect error: ${e.message}",
                                                                )
                                                                scheduleReconnect()
                                                            }
                                                        } else {
                                                            DebugLog.e(
                                                                "BluetoothService",
                                                                "BLUETOOTH_CONNECT not granted; " +
                                                                    "skipping immediate auto-connect",
                                                            )
                                                            reportMissingBluetoothConnect()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    DebugLog.e(
                                                        "BluetoothService",
                                                        "immediate reconnect error: ${e.message}",
                                                    )
                                                    scheduleReconnect()
                                                }
                                            }
                                        }
                                    }

                                    override fun onConnectionStateChanged(
                                        device: BluetoothDevice,
                                        state: Int,
                                    ) {
                                        if (state == BluetoothProfile.STATE_CONNECTED) {
                                            eventListener?.onInfo("HID state CONNECTED ${device.address}")
                                            connectedDevice = device
                                            reconnectAttempt = 0
                                            // Cancel discovery only if BLUETOOTH_SCAN is granted
                                            // to avoid SecurityException
                                            val hasBtScanCancel =
                                                ContextCompat.checkSelfPermission(
                                                    this@BluetoothService,
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                ) ==
                                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasBtScanCancel) {
                                                try {
                                                    bluetoothAdapter?.cancelDiscovery()
                                                } catch (se: SecurityException) {
                                                    DebugLog.e(
                                                        "BluetoothService",
                                                        "cancelDiscovery SecurityException: ${se.message}",
                                                    )
                                                }
                                            } else {
                                                DebugLog.e(
                                                    "BluetoothService",
                                                    "BLUETOOTH_SCAN not granted; skipping cancelDiscovery",
                                                )
                                            }
                                            // persist connected address for QS tile
                                            // (avoid device.name which requires BLUETOOTH_CONNECT)
                                            devicePrefs.setConnectedName(device.address)
                                            // notify tile to refresh
                                            refreshQsTile()
                                            eventListener?.onConnected(device)
                                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                            eventListener?.onInfo("HID state DISCONNECTED ${device.address}")
                                            connectedDevice = null
                                            // clear connected name
                                            devicePrefs.setConnectedName(null)
                                            refreshQsTile()
                                            eventListener?.onDisconnected(device)
                                            if (!manualDisconnect) scheduleReconnect()
                                        }
                                    }

                                    override fun onError(message: String) {
                                        eventListener?.onError(message)
                                    }

                                    override fun onLeds(leds: Int) {
                                        eventListener?.onLeds(leds)
                                    }
                                }
                        }
                    // hidSimplified is pre-cached by the serviceScope coroutine launched in onCreate.
                    val simplified = hidSimplified
                    eventListener?.onInfo("Registering HID app (simplified=$simplified)")
                    bluetoothHidModule?.registerApp(proxy, simplified)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidProfile = null
                    hid = null
                }
            }
        }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        // Prefer BluetoothManager.adapter on newer platform versions. If unavailable, fall back
        // to the older API but suppress the deprecation warning for the single fallback call
        // so the code remains clean on modern toolchains while preserving compatibility.
        val btMgr = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btMgr?.adapter ?: run {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        // Robust dynamic receiver registration for newer preview SDKs: always specify flag
        try {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (se: SecurityException) {
            // Some preview builds may reject NOT_EXPORTED; fall back to exported
            DebugLog.e("BluetoothService", "registerReceiver NOT_EXPORTED rejected: ${se.message}")
            try {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } catch (_: Exception) {
            }
        }
        val notifFilter =
            IntentFilter().apply {
                addAction(ACTION_CONNECT)
                addAction(ACTION_DISCONNECT)
                addAction(ACTION_FORGET)
            }
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_TIRAMISU) {
            try {
                registerReceiver(notifActionReceiver, notifFilter, Context.RECEIVER_NOT_EXPORTED)
            } catch (se: SecurityException) {
                // Fallback: some preview/API levels may require explicit exported flag
                registerReceiver(notifActionReceiver, notifFilter, Context.RECEIVER_EXPORTED)
                DebugLog.e("BluetoothService", "Fallback to RECEIVER_EXPORTED for notifActionReceiver: ${se.message}")
            }
        } else {
            registerReceiver(notifActionReceiver, notifFilter)
        }
        refreshQsTile()
        lastDeviceAddress = devicePrefs.getLastDevice()
        if (lastDeviceAddress != null) {
            DebugLog.log("BluetoothService", "remembered last_device=$lastDeviceAddress")
        }
        // Pre-cache hidSimplified and keep it current so onServiceConnected
        // never has to call runBlocking on the BT callback thread.
        serviceScope.launch {
            SettingsManager.flow(this@BluetoothService).collect { settings ->
                hidSimplified = settings.hidSimplified
            }
        }
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
        startInForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(notifActionReceiver)
        } catch (_: Exception) {
        }
        // Unregister HID app only on supported platforms (API 28+) and if BLUETOOTH_CONNECT is available;
        // guard against SecurityException
        val hasBtConnectDestroy =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && hasBtConnectDestroy) {
            try {
                hid?.unregisterApp()
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "unregisterApp SecurityException: ${se.message}")
            } catch (_: Exception) {
            }
        } else {
            DebugLog.e(
                "BluetoothService",
                "Skipping hid.unregisterApp: either BLUETOOTH_CONNECT not granted or API < 28",
            )
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidProfile)
        refreshQsTile()
    }

    override fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            DebugLog.log("BluetoothService", "cancelDiscovery (was discovering)")
            // Cancel discovery only if BLUETOOTH_SCAN is granted to avoid SecurityException
            val hasBtScan =
                ContextCompat.checkSelfPermission(
                    this@BluetoothService,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasBtScan) {
                try {
                    bluetoothAdapter?.cancelDiscovery()
                } catch (se: SecurityException) {
                    DebugLog.e("BluetoothService", "cancelDiscovery SecurityException: ${se.message}")
                }
            } else {
                DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
            }
        }
        // Clear previous results so UI refreshes immediately
        discoveredDevices.clear()
        StoreProvider.dispatch(Action.UpdateDiscoveredDevices(emptyList()))
        DebugLog.log("BluetoothService", "startDiscovery")
        val hasBtScanStart =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_SCAN,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasBtScanStart) {
            DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; cannot start discovery")
            StoreProvider.dispatch(Action.UpdateMessage("Scan permission not granted"))
            return
        }
        val started =
            try {
                bluetoothAdapter?.startDiscovery() ?: false
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "startDiscovery SecurityException: ${se.message}")
                false
            }
        if (started) {
            StoreProvider.dispatch(Action.UpdateIsScanning(true))
        } else {
            DebugLog.e("BluetoothService", "startDiscovery returned false")
            StoreProvider.dispatch(Action.UpdateMessage("Failed to start scan"))
        }
    }

    override fun stopDiscovery() {
        DebugLog.log("BluetoothService", "stopDiscovery")
        val hasBtScan2 =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_SCAN,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtScan2) {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "cancelDiscovery SecurityException: ${se.message}")
            }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
        }
        StoreProvider.dispatch(Action.UpdateIsScanning(false))
    }

    override fun getDiscoveredDevices(): List<BluetoothDevice> {
        // Return a snapshot to trigger StateFlow emissions in UI
        return discoveredDevices.toList()
    }

    override fun getPairedDevices(): List<BluetoothDevice> {
        // Access to bondedDevices requires BLUETOOTH_CONNECT on newer Android; check permission
        val hasBtConnect =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (hasBtConnect) {
            try {
                bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "getPairedDevices SecurityException: ${se.message}")
                emptyList()
            }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; returning empty paired list")
            emptyList()
        }
    }

    override fun pairDevice(device: BluetoothDevice) {
        // Creating a bond may require BLUETOOTH_CONNECT on newer Android; guard the call
        val hasBtConnect =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            try {
                device.createBond()
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "createBond SecurityException: ${se.message}")
                eventListener?.onError("Pairing failed due to missing permission")
            } catch (e: Exception) {
                DebugLog.e("BluetoothService", "createBond error: ${e.message}")
            }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; cannot create bond")
            eventListener?.onError("Missing BLUETOOTH_CONNECT permission; pairing not allowed")
        }
    }

    private var connectedDevice: BluetoothDevice? = null
    private var lastTargetDevice: BluetoothDevice? = null
    private var manualDisconnect = false

    // Tracks which descriptor variant was registered so send methods build correct-length reports.
    @Volatile private var hidSimplified: Boolean = true
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

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
                bluetoothAdapter?.getRemoteDevice(addr)
            } ?: return
        val attempt = (++reconnectAttempt).coerceAtLeast(1)
        val base = if (delayMs > 0) delayMs else DEFAULT_RECONNECT_BASE_MS
        val computed = ReconnectLogic.computeReconnectDelay(base, attempt)
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        val r =
            Runnable {
                try {
                    DebugLog.log("BluetoothService", "auto reconnect attempt #$attempt to ${target.address}")
                    eventListener?.onInfo("Auto-reconnect attempt #$attempt to ${target.address}")
                    eventListener?.onInfo(
                        "Note: Some hosts (especially Linux/BlueZ) must initiate the HID connection " +
                            "from the host side.",
                    )
                    // Ensure BLUETOOTH_CONNECT is available before attempting connect
                    val hasBtConnect =
                        ContextCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasBtConnect) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                hid?.connect(target)
                            } else {
                                DebugLog.e(
                                    "BluetoothService",
                                    "HID connect not supported on API < 28; skipping auto reconnect",
                                )
                            }
                        } catch (se: SecurityException) {
                            DebugLog.e("BluetoothService", "hid.connect SecurityException: ${se.message}")
                            eventListener?.onError("HID connect failed due to missing permission")
                        } catch (e: Exception) {
                            DebugLog.e("BluetoothService", "reconnect error: ${e.message}")
                            scheduleReconnect()
                        }
                    } else {
                        DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping auto reconnect")
                        reportMissingBluetoothConnect()
                    }
                } catch (e: Exception) {
                    DebugLog.e("BluetoothService", "reconnect error: ${e.message}")
                    scheduleReconnect()
                }
            }
        reconnectRunnable = r
        DebugLog.log("BluetoothService", "scheduleReconnect attempt=$attempt in ${computed}ms to ${target.address}")
        eventListener?.onInfo("Reconnect attempt #$attempt in ${computed}ms to ${target.address}")
        mainHandler.postDelayed(r, computed)
    }

    override fun connectDevice(device: BluetoothDevice) {
        manualDisconnect = false
        reconnectAttempt = 0
        lastTargetDevice = device
        DebugLog.log("BluetoothService", "connectDevice ${device.address}")
        // Avoid accessing device.name which requires BLUETOOTH_CONNECT; show address instead
        eventListener?.onInfo("Connecting to ${device.address} (manual)")
        devicePrefs.setLastDevice(device.address)
        lastDeviceAddress = device.address
        // Ensure BLUETOOTH_CONNECT is available before attempting connect
        val hasBtConnect =
            ContextCompat.checkSelfPermission(
                this@BluetoothService,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            try {
                DebugLog.log("BluetoothService", "hid.connect manual ${device.address}")
                eventListener?.onInfo(
                    "Requested HID connection. Some Linux/BlueZ hosts must initiate the HID connection " +
                        "from the host side. On Linux, run bluetoothctl connect " +
                        "<this phone's Bluetooth address> from the laptop.",
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    hid?.connect(device)
                } else {
                    DebugLog.e("BluetoothService", "HID connect not supported on API < 28; manual connect skipped")
                }
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "hid.connect SecurityException: ${se.message}")
                eventListener?.onError("HID connect failed due to missing permission")
            } catch (e: Exception) {
                DebugLog.e("BluetoothService", "connect error: ${e.message}")
                scheduleReconnect()
            }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; cannot connect")
            eventListener?.onError("Missing BLUETOOTH_CONNECT permission; connect aborted")
            reportMissingBluetoothConnect()
        }
    }

    override fun disconnectDevice() {
        manualDisconnect = true
        try {
            val target = connectedDevice ?: lastTargetDevice
            DebugLog.log("BluetoothService", "disconnectDevice target=${target?.address}")
            if (target != null) {
                val hasBtConnectDisc =
                    ContextCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasBtConnectDisc) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            hid?.disconnect(target)
                        } else {
                            DebugLog.e("BluetoothService", "HID disconnect not supported on API < 28; skipping")
                        }
                    } catch (se: SecurityException) {
                        DebugLog.e("BluetoothService", "hid.disconnect SecurityException: ${se.message}")
                    }
                } else {
                    DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping disconnect")
                }
            }
        } catch (e: Exception) {
            DebugLog.e("BluetoothService", "disconnect error: ${e.message}")
        } finally {
            connectedDevice = null
            devicePrefs.setConnectedName(null)
            refreshQsTile()
        }
    }

    // HID report helpers
    @SuppressLint("NewApi")
    private fun hidDevice(): BluetoothHidDevice? {
        // Only resolve the profile proxy as a BluetoothHidDevice on supported platforms.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return hid ?: bluetoothHidProfile as? BluetoothHidDevice
        }
        return null
    }

    private fun connectLast(): Boolean {
        val addr = lastDeviceAddress ?: return false
        val dev =
            try {
                bluetoothAdapter?.getRemoteDevice(addr)
            } catch (_: Exception) {
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
                    ACTION_CONNECT -> {
                        val ok = connectLast()
                        if (!ok) {
                            DebugLog.e("BluetoothService", "No last_device to connect")
                        }
                    }
                    ACTION_DISCONNECT -> disconnectDevice()
                    ACTION_FORGET -> {
                        DebugLog.log("BluetoothService", "forget last_device")
                        devicePrefs.clearLastAndConnected()
                        lastDeviceAddress = null
                        refreshQsTile()
                    }
                }
            }
        }

    private fun refreshQsTile() {
        if (android.os.Build.VERSION.SDK_INT >= SDK_INT_NOUGAT) {
            try {
                TileService.requestListeningState(this, ComponentName(this, HidQuickTileService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private fun startInForeground() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = ServiceNotifications.buildForeground(this, ACTION_CONNECT, ACTION_DISCONNECT, ACTION_FORGET)
        // Use the two-argument startForeground where possible. On some
        // platform builds the system may still enforce foreground-service
        // types; guard against that so the service doesn't crash the app.
        try {
            startForeground(1, notif)
        } catch (e: Exception) {
            // Fall back to posting the notification without calling
            // startForeground so the process won't be killed during
            // startup on restrictive platform builds. This keeps the
            // user-visible notification but accepts the risk that the
            // service may not be treated as a true FGS by the system.
            DebugLog.e("BluetoothService", "startForeground failed: ${e.message}")
            mgr.notify(1, notif)
        }
    }

    private fun reportMissingBluetoothConnect() {
        val msg = "App requires BLUETOOTH_CONNECT permission. Please grant it in Settings."
        DebugLog.e("BluetoothService", msg)
        // Post a user-visible notification with a shortcut to app settings
        try {
            ServiceNotifications.postMissingPermission(this, msg)
        } catch (e: Exception) {
            DebugLog.e("BluetoothService", "failed to post settings notification: ${e.message}")
        }

        // Broadcast so Activity can show UI and exit gracefully
        try {
            val b = Intent(ACTION_MISSING_BLUETOOTH_CONNECT).setPackage(packageName)
            sendBroadcast(b)
        } catch (e: Exception) {
            DebugLog.e("BluetoothService", "failed to send missing-perm broadcast: ${e.message}")
        }

        // Stop the service gracefully
        try {
            stopSelf()
        } catch (_: Exception) {
        }
    }

    // Bonded device manager helpers
    override fun getLastDeviceAddress(): String? = lastDeviceAddress

    override fun setDefaultDevice(device: BluetoothDevice) {
        DebugLog.log("BluetoothService", "setDefaultDevice ${device.address}")
        devicePrefs.setLastDevice(device.address)
        lastDeviceAddress = device.address
        lastTargetDevice = device
        StoreProvider.dispatch(Action.UpdateDefaultDevice(device.address))
        eventListener?.onInfo("Default device: ${device.address}")
        refreshQsTile()
    }

    override fun getAlias(device: BluetoothDevice): String? {
        return devicePrefs.getAlias(device.address)
    }

    override fun setAlias(
        device: BluetoothDevice,
        alias: String,
    ) {
        DebugLog.log("BluetoothService", "setAlias ${device.address} -> $alias")
        devicePrefs.setAlias(device.address, alias)
        eventListener?.onInfo("Renamed: $alias")
    }

    override fun forgetDevice(
        device: BluetoothDevice,
        unpair: Boolean,
    ) {
        DebugLog.log("BluetoothService", "forgetDevice ${device.address} unpair=$unpair")
        try {
            if (connectedDevice?.address == device.address) {
                manualDisconnect = true
                // Guard hid.disconnect with BLUETOOTH_CONNECT check
                val hasBtConnectForget =
                    ContextCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasBtConnectForget) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            hid?.disconnect(device)
                        } else {
                            DebugLog.e(
                                "BluetoothService",
                                "HID disconnect not supported on API < 28; skipping forget-device disconnect",
                            )
                        }
                    } catch (se: SecurityException) {
                        DebugLog.e("BluetoothService", "hid.disconnect SecurityException: ${se.message}")
                    } catch (_: Exception) {
                    }
                } else {
                    DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping disconnect in forgetDevice")
                }
                connectedDevice = null
                devicePrefs.setConnectedName(null)
                refreshQsTile()
                eventListener?.onDisconnected(device)
            }
            if (lastDeviceAddress == device.address) {
                devicePrefs.setLastDevice(null)
                lastDeviceAddress = null
                lastTargetDevice = null
                StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
            }
            devicePrefs.removeAlias(device.address)
            if (unpair) {
                try {
                    val m = device.javaClass.getMethod("removeBond")
                    m.isAccessible = true
                    m.invoke(device)
                } catch (e: Exception) {
                    DebugLog.e("BluetoothService", "unpair failed: ${e.message}")
                }
            }
        } finally {
            StoreProvider.dispatch(Action.UpdatePairedDevices(getPairedDevices()))
            eventListener?.onInfo("Forgot ${device.address}")
        }
    }

    // HID report output (chord state + report writing) lives in HidReportSender.
    // It reads the current device/proxy/descriptor mode live via these lambdas.
    private val reportSender by lazy {
        HidReportSender(
            context = this,
            currentDevice = { connectedDevice },
            currentHid = { hidDevice() },
            isSimplified = { hidSimplified },
            onError = { msg -> eventListener?.onError(msg) },
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

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}
