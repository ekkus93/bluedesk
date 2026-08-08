from pathlib import Path


def replace(path_str: str, old: str, new: str, expected: int = 1) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path_str}: expected {expected} matches, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


discovery = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/DiscoveryController.kt"
replace(
    discovery,
    '''    fun getPairedDevices(): List<BluetoothDevice> {
        val required = PermissionPolicy.requiredForClassicStartup(sdkInt)
        if (!hasPermissions(required)) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            return failPairedList("Bluetooth connect permission is unavailable; paired devices cannot be read")
        }
        val currentAdapter = adapter()
            ?: return failPairedList("Bluetooth adapter is unavailable; paired devices cannot be read")
        return try {
            currentAdapter.bondedDevices.toList()
        } catch (se: SecurityException) {
            DebugLog.e(TAG, "getPairedDevices SecurityException: ${se.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            failPairedList("Bluetooth connect permission was revoked; paired devices cannot be read")
        }
    }
''',
    '''    fun getPairedDevices(): List<BluetoothDevice> {
        val required = PermissionPolicy.requiredForClassicStartup(sdkInt)
        return if (!hasPermissions(required)) {
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            failPairedList("Bluetooth connect permission is unavailable; paired devices cannot be read")
        } else {
            adapter()?.let { currentAdapter ->
                try {
                    currentAdapter.bondedDevices.toList()
                } catch (se: SecurityException) {
                    DebugLog.e(TAG, "getPairedDevices SecurityException: ${se.message}")
                    StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
                    failPairedList("Bluetooth connect permission was revoked; paired devices cannot be read")
                }
            } ?: failPairedList("Bluetooth adapter is unavailable; paired devices cannot be read")
        }
    }
''',
)

classic = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BluetoothService.kt"
replace(
    classic,
    '''            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val detail = e.message ?: e.javaClass.simpleName
                val message = "Remembered Bluetooth device could not be resolved: $detail"
                DebugLog.e("BluetoothService", message)
                eventListener?.onError(message)
                null
            } ?: return''',
    '''            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val detail = e.message ?: e.javaClass.simpleName
                val message = "Remembered Bluetooth device could not be resolved: $detail"
                DebugLog.e("BluetoothService", message)
                devicePrefs.setLastRuntimeFailure(message)
                devicePrefs.setLastDevice(null)
                lastDeviceAddress = null
                lastTargetDevice = null
                StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
                StoreProvider.dispatch(Action.UpdateMessage(message))
                eventListener?.onError(message)
                null
            } ?: return''',
)
replace(
    classic,
    '''                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    DebugLog.e("BluetoothService", "Remembered Bluetooth address is invalid: ${e.message}")
                    null
                }
            } ?: return''',
    '''                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    val message = "Remembered Bluetooth address is invalid: ${e.message ?: e.javaClass.simpleName}"
                    DebugLog.e("BluetoothService", message)
                    devicePrefs.setLastRuntimeFailure(message)
                    devicePrefs.setLastDevice(null)
                    lastDeviceAddress = null
                    lastTargetDevice = null
                    StoreProvider.dispatch(Action.UpdateDefaultDevice(null))
                    StoreProvider.dispatch(Action.UpdateMessage(message))
                    eventListener?.onError(message)
                    null
                }
            } ?: return''',
)

ble = "app/src/main/java/com/augustusmachin/android_bt_kbmouse/BleHogpService.kt"
replace(
    ble,
    "import android.os.ParcelUuid\n",
    "import android.os.ParcelUuid\nimport android.util.Log\n",
)
replace(
    ble,
    '''        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            DebugLog.e(TAG, "Could not change BLE adapter name: ${e.message}")
        }
''',
    '''        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Device-name customization is cosmetic; continue advertising under the existing name.
            DebugLog.e(TAG, "Could not change BLE adapter name: ${e.message}")
            Log.w(TAG, "BLE adapter-name customization failed; continuing with existing name", e)
        }
''',
)
replace(
    ble,
    '''        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))) {
            DebugLog.e(TAG, "Bluetooth connect permission unavailable; cannot send GATT response")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            return
        }
        val server = gattServer
        if (server == null) {
            DebugLog.e(TAG, "Cannot send GATT response: GATT server is unavailable")
            return
        }
''',
    '''        if (!PermissionGrantChecker.hasAll(this, PermissionPolicy.requiredForClassicStartup(Build.VERSION.SDK_INT))) {
            val message = "Bluetooth connect permission unavailable; cannot send GATT response"
            DebugLog.e(TAG, message)
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            StoreProvider.dispatch(Action.UpdateMessage(message))
            eventListener?.onError(message)
            return
        }
        val server = gattServer
        if (server == null) {
            val message = "Cannot send GATT response: GATT server is unavailable"
            DebugLog.e(TAG, message)
            StoreProvider.dispatch(Action.UpdateMessage(message))
            eventListener?.onError(message)
            return
        }
''',
)
replace(
    ble,
    '''        } catch (e: SecurityException) {
            DebugLog.e(TAG, "GATT response permission failure: ${e.message}")
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
        }
''',
    '''        } catch (e: SecurityException) {
            val message = "GATT response permission failure: ${e.message}"
            DebugLog.e(TAG, message)
            StoreProvider.dispatch(Action.UpdatePermissionsValid(false))
            StoreProvider.dispatch(Action.UpdateMessage(message))
            eventListener?.onError(message)
        }
''',
)
replace(
    ble,
    '''    private fun failStartup(message: String) {
        readiness.fail(message)
        val persisted = (readiness.state as? BleHogpStartupState.Failed)?.message ?: message
        DebugLog.e(TAG, persisted)
        eventListener?.onError(persisted)
        cleanupBleResources()
        stopSelf()
    }
''',
    '''    private fun failStartup(message: String) {
        readiness.fail(message)
        val persisted = (readiness.state as? BleHogpStartupState.Failed)?.message ?: message
        DebugLog.e(TAG, persisted)
        Log.e(TAG, persisted)
        BtDevicePrefs(this).setLastRuntimeFailure(persisted)
        StoreProvider.dispatch(Action.UpdateMessage(persisted))
        eventListener?.onError(persisted)
        postStartupFailureNotification(persisted)
        cleanupBleResources()
        stopSelf()
    }

    private fun postStartupFailureNotification(message: String) {
        try {
            ServiceNotifications.postRuntimeFailure(
                this,
                title = "BlueDeck BLE could not start",
                message = message,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Durable preferences, store state, and system log already retain the failure.
            Log.e(TAG, "Could not post BLE startup-failure notification", e)
        }
    }
''',
)
