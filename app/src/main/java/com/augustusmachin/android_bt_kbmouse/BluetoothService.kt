package com.augustusmachin.android_bt_kbmouse

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.bluetooth.BluetoothHidDevice
import android.Manifest
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.graphics.Color
import androidx.core.app.NotificationCompat
import android.service.quicksettings.TileService
import android.content.ComponentName

class BluetoothService : Service(), IBluetoothService {

    companion object {
        private const val ACTION_CONNECT = "com.augustusmachin.android_bt_kbmouse.ACTION_CONNECT"
        private const val ACTION_DISCONNECT = "com.augustusmachin.android_bt_kbmouse.ACTION_DISCONNECT"
        private const val ACTION_FORGET = "com.augustusmachin.android_bt_kbmouse.ACTION_FORGET"
        const val ACTION_MISSING_BLUETOOTH_CONNECT = "com.augustusmachin.android_bt_kbmouse.ACTION_MISSING_BLUETOOTH_CONNECT"
    }

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var bluetoothHidProfile: BluetoothProfile? = null
    private var bluetoothHidModule: BluetoothHidModule? = null
    private var hid: BluetoothHidDevice? = null
    private var lastDeviceAddress: String? = null
    private var reconnectAttempt: Int = 0
    private var btEnabled: Boolean = true
    private var reconnectPending: Boolean = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                    }
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            // Avoid accessing device.name which requires BLUETOOTH_CONNECT on newer Android
                            DebugLog.log("BluetoothService", "FOUND ${it.address}")
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val dev: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                    }
                    DebugLog.log("BluetoothService", "BOND_STATE_CHANGED=$state dev=${dev?.address}")
                    if (state == BluetoothDevice.BOND_BONDED && dev != null) {
                        // Remember and attempt HID connect automatically after pairing
                        lastTargetDevice = dev
                        lastDeviceAddress = dev.address
                        getSharedPreferences("bt_hid", MODE_PRIVATE).edit().putString("last_device", dev.address).apply()
                        scheduleReconnect(0)
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
                            DebugLog.log("BluetoothService", "Bluetooth ON - (re)acquiring HID proxy and resuming reconnect")
                            bluetoothAdapter?.getProfileProxy(this@BluetoothService, profileListener, BluetoothProfile.HID_DEVICE)
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
    override fun setEventListener(l: ServiceEventListener) { eventListener = l }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                // HID APIs require API level 28+. If running on older platforms, skip HID initialization.
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                    DebugLog.e("BluetoothService", "HID profile requires API 28+; skipping HID initialization on this OS")
                    eventListener?.onError("HID not supported on this Android version")
                    return
                }
                // Require BLUETOOTH_CONNECT permission before using HID APIs
                val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasBtConnect) {
                    DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; reporting and skipping HID initialization to avoid SecurityException")
                    eventListener?.onError("Missing BLUETOOTH_CONNECT permission - HID disabled")
                    reportMissingBluetoothConnect()
                    return
                }

                bluetoothHidProfile = proxy
                hid = proxy as BluetoothHidDevice
                DebugLog.log("BluetoothService", "HID service connected")
                eventListener?.onInfo("HID profile proxy connected; registering app")
                bluetoothHidModule = BluetoothHidModule(bluetoothAdapter!!).also { module ->
                    module.listener = object : BluetoothHidModule.HidEventListenerExt {
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
                                            eventListener?.onInfo("Immediate auto-connect to $addr")
                                            eventListener?.onInfo("hid.connect($addr) immediate")
                                            // Ensure BLUETOOTH_CONNECT is available before attempting connect
                                            val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasBtConnect) {
                                                try {
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                        hid?.connect(dev)
                                                    } else {
                                                        DebugLog.e("BluetoothService", "HID connect not supported on API < 28; skipping")
                                                    }
                                                } catch (se: SecurityException) {
                                                    DebugLog.e("BluetoothService", "hid.connect SecurityException: ${se.message}")
                                                    eventListener?.onError("HID connect failed due to missing permission")
                                                } catch (e: Exception) {
                                                    DebugLog.e("BluetoothService", "immediate reconnect error: ${e.message}")
                                                    scheduleReconnect()
                                                }
                                            } else {
                                                DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping immediate auto-connect")
                                                reportMissingBluetoothConnect()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        DebugLog.e("BluetoothService", "immediate reconnect error: ${e.message}")
                                        scheduleReconnect()
                                    }
                                }
                            }
                        }
                        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                            if (state == BluetoothProfile.STATE_CONNECTED) {
                                eventListener?.onInfo("HID state CONNECTED ${device.address}")
                                connectedDevice = device
                                reconnectAttempt = 0
                                // Cancel discovery only if BLUETOOTH_SCAN is granted to avoid SecurityException
                                val hasBtScanCancel = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasBtScanCancel) {
                                    try { bluetoothAdapter?.cancelDiscovery() } catch (se: SecurityException) { DebugLog.e("BluetoothService", "cancelDiscovery SecurityException: ${se.message}") }
                                } else {
                                    DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
                                }
                                // persist connected address for QS tile (avoid device.name which requires BLUETOOTH_CONNECT)
                                getSharedPreferences("bt_hid", MODE_PRIVATE).edit().putString("connected_name", device.address).apply()
                                // notify tile to refresh
                                refreshQsTile()
                                eventListener?.onConnected(device)
                            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                eventListener?.onInfo("HID state DISCONNECTED ${device.address}")
                                connectedDevice = null
                                // clear connected name
                                getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("connected_name").apply()
                                refreshQsTile()
                                eventListener?.onDisconnected(device)
                                if (!manualDisconnect) scheduleReconnect()
                            }
                        }
                        override fun onError(message: String) { eventListener?.onError(message) }
                        override fun onLeds(leds: Int) { eventListener?.onLeds(leds) }
                    }
                }
                // Resolve descriptor variant from settings.
                // runBlocking is safe here only as a fallback; TASK-04 will replace this
                // with a pre-cached field read in onCreate.
                val simplified = try {
                    kotlinx.coroutines.runBlocking {
                        SettingsManager.flow(this@BluetoothService).first().hidSimplified
                    }
                } catch (e: Exception) { true }
                hidSimplified = simplified
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
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        // Robust dynamic receiver registration for newer preview SDKs: always specify flag
        try {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (se: SecurityException) {
            // Some preview builds may reject NOT_EXPORTED; fall back to exported
            try { registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) } catch (_: Exception) {}
        }
        val notifFilter = IntentFilter().apply {
            addAction(ACTION_CONNECT)
            addAction(ACTION_DISCONNECT)
            addAction(ACTION_FORGET)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
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
        lastDeviceAddress = getSharedPreferences("bt_hid", MODE_PRIVATE).getString("last_device", null)
        if (lastDeviceAddress != null) {
            DebugLog.log("BluetoothService", "remembered last_device=$lastDeviceAddress")
        }
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { unregisterReceiver(notifActionReceiver) } catch (_: Exception) {}
        // Unregister HID app only on supported platforms (API 28+) and if BLUETOOTH_CONNECT is available; guard against SecurityException
        val hasBtConnectDestroy = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && hasBtConnectDestroy) {
            try { hid?.unregisterApp() } catch (se: SecurityException) { DebugLog.e("BluetoothService", "unregisterApp SecurityException: ${se.message}") } catch (_: Exception) {}
        } else {
            DebugLog.e("BluetoothService", "Skipping hid.unregisterApp: either BLUETOOTH_CONNECT not granted or API < 28")
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidProfile)
        refreshQsTile()
    }

    override fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            DebugLog.log("BluetoothService", "cancelDiscovery (was discovering)")
                // Cancel discovery only if BLUETOOTH_SCAN is granted to avoid SecurityException
                val hasBtScan = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasBtScan) {
                    try { bluetoothAdapter?.cancelDiscovery() } catch (se: SecurityException) { DebugLog.e("BluetoothService", "cancelDiscovery SecurityException: ${se.message}") }
                } else {
                    DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
                }
        }
        // Clear previous results so UI refreshes immediately
        discoveredDevices.clear()
        DebugLog.log("BluetoothService", "startDiscovery")
        android.util.Log.d("BTKB", "BluetoothService.startDiscovery invoked")
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        DebugLog.log("BluetoothService", "stopDiscovery")
        // Cancel discovery only if BLUETOOTH_SCAN is granted to avoid SecurityException
        val hasBtScan2 = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtScan2) {
            try { bluetoothAdapter?.cancelDiscovery() } catch (se: SecurityException) { DebugLog.e("BluetoothService", "cancelDiscovery SecurityException: ${se.message}") }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_SCAN not granted; skipping cancelDiscovery")
        }
    }

    override fun getDiscoveredDevices(): List<BluetoothDevice> {
        // Return a snapshot to trigger StateFlow emissions in UI
        return discoveredDevices.toList()
    }

    override fun getPairedDevices(): List<BluetoothDevice> {
        // Access to bondedDevices requires BLUETOOTH_CONNECT on newer Android; check permission
        val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
        val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
    private var reconnecting = false
    // Tracks which descriptor variant was registered so send methods build correct-length reports.
    @Volatile private var hidSimplified: Boolean = true

    private fun scheduleReconnect(delayMs: Long = 0) {
        if (manualDisconnect) return
        if (!btEnabled) {
            DebugLog.log("BluetoothService", "Bluetooth OFF; pausing reconnect")
            reconnectPending = true
            return
        }
        val target = lastTargetDevice ?: run {
            val addr = lastDeviceAddress ?: return
            bluetoothAdapter?.getRemoteDevice(addr)
        } ?: return
        val attempt = (++reconnectAttempt).coerceAtLeast(1)
        val base = if (delayMs > 0) delayMs else 2000L
        val computed = ReconnectLogic.computeReconnectDelay(base, attempt)
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            try {
                DebugLog.log("BluetoothService", "auto reconnect attempt #${attempt} to ${target.address}")
                eventListener?.onInfo("hid.connect(${target.address}) attempt #${attempt}")
        // Ensure BLUETOOTH_CONNECT is available before attempting connect
        val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            hid?.connect(target)
                        } else {
                            DebugLog.e("BluetoothService", "HID connect not supported on API < 28; skipping auto reconnect")
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
        DebugLog.log("BluetoothService", "scheduleReconnect attempt=${attempt} in ${computed}ms to ${target.address}")
        eventListener?.onInfo("Reconnect attempt #${attempt} in ${computed}ms to ${target.address}")
        mainHandler.postDelayed(r, computed)
    }

    override fun connectDevice(device: BluetoothDevice) {
        manualDisconnect = false
        reconnectAttempt = 0
        lastTargetDevice = device
        DebugLog.log("BluetoothService", "connectDevice ${device.address}")
        // Avoid accessing device.name which requires BLUETOOTH_CONNECT; show address instead
        eventListener?.onInfo("Connecting to ${device.address} (manual)")
        getSharedPreferences("bt_hid", MODE_PRIVATE).edit().putString("last_device", device.address).apply()
        lastDeviceAddress = device.address
        // Ensure BLUETOOTH_CONNECT is available before attempting connect
        val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            try {
                DebugLog.log("BluetoothService", "hid.connect immediate manual ${device.address}")
                eventListener?.onInfo("hid.connect(${device.address})")
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
                e.printStackTrace()
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
                val hasBtConnectDisc = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasBtConnectDisc) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            hid?.disconnect(target)
                        } else {
                            DebugLog.e("BluetoothService", "HID disconnect not supported on API < 28; skipping")
                        }
                    } catch (se: SecurityException) { DebugLog.e("BluetoothService", "hid.disconnect SecurityException: ${se.message}") }
                } else {
                    DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping disconnect")
                }
            }
        } catch (e: Exception) {
            DebugLog.e("BluetoothService", "disconnect error: ${e.message}")
            e.printStackTrace()
        } finally {
            connectedDevice = null
            getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("connected_name").apply()
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
        val dev = try { bluetoothAdapter?.getRemoteDevice(addr) } catch (_: Exception) { null } ?: return false
        connectDevice(dev)
        return true
    }

    private val notifActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
                    getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("last_device").remove("connected_name").apply()
                    lastDeviceAddress = null
                    refreshQsTile()
                }
            }
        }
    }

    private fun refreshQsTile() {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                TileService.requestListeningState(this, ComponentName(this, HidQuickTileService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun startInForeground() {
        val channelId = "bt_hid_service"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            if (mgr.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(channelId, "Bluetooth HID Service", NotificationManager.IMPORTANCE_LOW)
                ch.enableLights(false)
                ch.enableVibration(false)
                mgr.createNotificationChannel(ch)
            }
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val actionFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val connectPi = PendingIntent.getBroadcast(this, 1, Intent(ACTION_CONNECT).setPackage(packageName), actionFlags)
        val disconnectPi = PendingIntent.getBroadcast(this, 2, Intent(ACTION_DISCONNECT).setPackage(packageName), actionFlags)
        val forgetPi = PendingIntent.getBroadcast(this, 3, Intent(ACTION_FORGET).setPackage(packageName), actionFlags)
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth HID running")
            .setContentText("Tap to manage connection")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_bluetooth, "Connect", connectPi)
            .addAction(R.drawable.ic_bluetooth, "Disconnect", disconnectPi)
            .addAction(R.drawable.ic_settings, "Forget", forgetPi)
            .setOngoing(true)
            .build()
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
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "bt_hid_error"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                if (nm.getNotificationChannel(channelId) == null) {
                    val ch = NotificationChannel(channelId, "Bluetooth HID errors", NotificationManager.IMPORTANCE_HIGH)
                    ch.enableLights(true)
                    ch.lightColor = Color.RED
                    nm.createNotificationChannel(ch)
                }
            }
            val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null))
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            val notif = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Bluetooth permission required")
                .setContentText(msg)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(2, notif)
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
        try { stopSelf() } catch (_: Exception) {}
    }

    // Bonded device manager helpers
    override fun getLastDeviceAddress(): String? = lastDeviceAddress

    override fun setDefaultDevice(device: BluetoothDevice) {
        DebugLog.log("BluetoothService", "setDefaultDevice ${device.address}")
        getSharedPreferences("bt_hid", MODE_PRIVATE).edit().putString("last_device", device.address).apply()
    lastDeviceAddress = device.address
    lastTargetDevice = device
    // Avoid accessing device.name (requires BLUETOOTH_CONNECT on newer Android) — use address in UI messages
    eventListener?.onInfo("Default device: ${device.address}")
        refreshQsTile()
    }

    override fun getAlias(device: BluetoothDevice): String? {
        return getSharedPreferences("bt_hid", MODE_PRIVATE).getString("alias_${device.address}", null)
    }

    override fun setAlias(device: BluetoothDevice, alias: String) {
        DebugLog.log("BluetoothService", "setAlias ${device.address} -> $alias")
        getSharedPreferences("bt_hid", MODE_PRIVATE).edit().putString("alias_${device.address}", alias).apply()
        eventListener?.onInfo("Renamed: $alias")
    }

    override fun forgetDevice(device: BluetoothDevice, unpair: Boolean) {
        DebugLog.log("BluetoothService", "forgetDevice ${device.address} unpair=$unpair")
        try {
            if (connectedDevice?.address == device.address) {
                manualDisconnect = true
                // Guard hid.disconnect with BLUETOOTH_CONNECT check
                val hasBtConnectForget = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasBtConnectForget) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            hid?.disconnect(device)
                        } else {
                            DebugLog.e("BluetoothService", "HID disconnect not supported on API < 28; skipping forget-device disconnect")
                        }
                    } catch (se: SecurityException) { DebugLog.e("BluetoothService", "hid.disconnect SecurityException: ${se.message}") } catch (_: Exception) {}
                } else {
                    DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; skipping disconnect in forgetDevice")
                }
                connectedDevice = null
                getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("connected_name").apply()
                refreshQsTile()
                eventListener?.onDisconnected(device)
            }
            if (lastDeviceAddress == device.address) {
                getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("last_device").apply()
                lastDeviceAddress = null
                lastTargetDevice = null
            }
            getSharedPreferences("bt_hid", MODE_PRIVATE).edit().remove("alias_${device.address}").apply()
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
            // refresh paired list on UI by emitting info (avoid device.name access)
            eventListener?.onInfo("Forgot ${device.address}")
        }
    }

    // Multi-key chord state
    private val pressedKeys = LinkedHashSet<Byte>() // preserve insertion order
    private var currentModifiers: Int = 0

    override fun setModifiers(mods: Int) {
        synchronized(this) { currentModifiers = mods and 0xFF }
        sendCurrentKeyboardReport()
    }

    override fun sendKeyPress(keyCode: Byte, modifiers: Int) {
        pressKey(keyCode, modifiers)
        try { Thread.sleep(10) } catch (_: InterruptedException) {}
        releaseKey(keyCode)
    }

    override fun pressKey(keyCode: Byte, modifiers: Int) {
        synchronized(this) {
            currentModifiers = modifiers and 0xFF
            if (pressedKeys.size < 6) pressedKeys.add(keyCode) else {
                // replace the oldest to ensure a report still goes out
                if (pressedKeys.isNotEmpty()) {
                    pressedKeys.remove(pressedKeys.first())
                    pressedKeys.add(keyCode)
                }
            }
        }
        sendCurrentKeyboardReport()
    }

    override fun releaseKey(keyCode: Byte) {
        synchronized(this) { pressedKeys.remove(keyCode) }
        sendCurrentKeyboardReport()
    }

    private fun sendCurrentKeyboardReport() {
        val device = connectedDevice ?: return
        val hid = hidDevice() ?: return
        val keys = synchronized(this) { pressedKeys.toList().take(6) }
        val report = ByteArray(8)
        report[0] = currentModifiers.toByte()
        // report[1] reserved = 0
        for (i in keys.indices) {
            report[2 + i] = keys[i]
        }
        DebugLog.log("BluetoothService", "kbd mods=" + currentModifiers + " keys=" + keys)
        // Ensure BLUETOOTH_CONNECT before sending HID report
        val hasBtConnectReport = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnectReport) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_KEYBOARD.toInt(), report)
                } else {
                    DebugLog.e("BluetoothService", "HID sendReport not supported on API < 28; skipping keyboard report")
                }
            } catch (se: SecurityException) { DebugLog.e("BluetoothService", "sendReport SecurityException: ${se.message}"); eventListener?.onError("HID report failed due to missing permission") } catch (e: Exception) { DebugLog.e("BluetoothService", "kbd report error: ${e.message}"); e.printStackTrace(); eventListener?.onError("HID report failed: ${e.message}") }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; cannot send keyboard report")
        }
    }

    override fun sendMouseMove(dx: Int, dy: Int) {
        sendMouseReport(0, dx, dy, 0, 0) // wheel/hwheel removed in simplified descriptor
    }

    override fun sendLeftClick() {
        clickMouse(buttonMask = 0x01)
    }

    override fun sendRightClick() {
        clickMouse(buttonMask = 0x02)
    }

    override fun sendMiddleClick() {
        clickMouse(buttonMask = 0x04)
    }

    // Scroll is only available in the FULL descriptor; no-op when SIMPLE is active.
    override fun sendScroll(delta: Int) {
        if (!hidSimplified) sendMouseReport(0, 0, 0, delta, 0)
    }
    override fun sendScrollH(delta: Int) {
        if (!hidSimplified) sendMouseReport(0, 0, 0, 0, delta)
    }

    private fun clickMouse(buttonMask: Int) {
        DebugLog.log("BluetoothService", "mouse click mask=" + buttonMask)
        sendMouseReport(buttonMask, 0, 0, 0, 0)
        try { Thread.sleep(10) } catch (_: InterruptedException) {}
        sendMouseReport(0, 0, 0, 0, 0)
    }

    private fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int, hWheel: Int) {
        val device = connectedDevice ?: return
        val hid = hidDevice() ?: return
        // SIMPLE: 3 bytes [buttons][dx][dy]; FULL: 5 bytes [buttons][dx][dy][wheelV][wheelH]
        val report = if (hidSimplified) {
            byteArrayOf(
                buttons.toByte(),
                dx.coerceIn(-127, 127).toByte(),
                dy.coerceIn(-127, 127).toByte()
            )
        } else {
            byteArrayOf(
                buttons.toByte(),
                dx.coerceIn(-127, 127).toByte(),
                dy.coerceIn(-127, 127).toByte(),
                wheel.coerceIn(-127, 127).toByte(),
                hWheel.coerceIn(-127, 127).toByte()
            )
        }
        DebugLog.log("BluetoothService", "mouse btn=$buttons dx=$dx dy=$dy wheel=$wheel hwheel=$hWheel simplified=$hidSimplified")
        val hasBtConnect = ContextCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    hid.sendReport(device, HidDescriptorVariants.REPORT_ID_MOUSE.toInt(), report)
                } else {
                    DebugLog.e("BluetoothService", "HID sendReport not supported on API < 28; skipping mouse report")
                }
            } catch (se: SecurityException) {
                DebugLog.e("BluetoothService", "mouse sendReport SecurityException: ${se.message}")
                eventListener?.onError("Mouse click failed due to missing permission")
            } catch (e: Exception) {
                DebugLog.e("BluetoothService", "mouse report error: ${e.message}")
                e.printStackTrace()
            }
        } else {
            DebugLog.e("BluetoothService", "BLUETOOTH_CONNECT not granted; cannot send mouse report")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}
