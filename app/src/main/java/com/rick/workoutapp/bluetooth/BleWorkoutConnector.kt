package com.rick.workoutapp.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rick.workoutapp.model.WorkoutDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the two BLE phases used by this app:
 *
 * 1) **Scan** — [android.bluetooth.le.BluetoothLeScanner] discovers nearby advertisers and publishes
 *    them on [devices]. This is discovery only; nothing is connected yet.
 * 2) **Connect** — [BluetoothGatt] opens a link to one device's GATT server and
 *    publishes progress on [connectionState].
 *
 * Emulator note: the official emulator does not pass through PC Bluetooth, so
 * scans are usually empty and real connects time out / fail. Demo devices in
 * the UI cover that case.
 *
 * Flow overview:
 *   startScan() → onScanResult (list grows)
 *   connect(device) → Connecting → onConnectionStateChange → Connected | Failed
 *   disconnect() / release() → close GATT → Idle
 */
class BleWorkoutConnector(context: Context) {

    // applicationContext avoids leaking an Activity if this lives across screens.
    private val appContext = context.applicationContext

    // System entry point for Bluetooth; adapter may be null on devices/emulators
    // without a radio.
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    // Nearby BLE advertisers found during scan (MAC address is the stable id).
    private val _devices = MutableStateFlow<List<WorkoutDevice>>(emptyList())
    val devices: StateFlow<List<WorkoutDevice>> = _devices.asStateFlow()

    // Current GATT attempt for one address at a time.
    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Active GATT client session. Non-null while connecting or connected.
     * Always pair disconnect() with close() (see [closeGatt]) to free the radio.
     */
    private var gatt: BluetoothGatt? = null

    // Local flag so we don't call stopScan when we never started.
    private var scanning = false

    /** False when the device/emulator has no Bluetooth adapter. */
    val isBluetoothAvailable: Boolean
        get() = adapter != null

    /** Adapter exists but user (or system) has Bluetooth turned off. */
    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Android 12+ (S) split Bluetooth into SCAN vs CONNECT runtime permissions.
     * Older APIs used the legacy BLUETOOTH* permissions plus location for scans.
     */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }
    }

    /**
     * Drop all scanned devices from the list without starting a scan.
     * Used so the UI can empty the list the moment Scan is tapped.
     */
    fun clearDevices() {
        _devices.value = emptyList()
    }

    /**
     * Begin a BLE advertisement scan.
     * [scanCallback] is invoked asynchronously as packets arrive.
     * Pass null filters = accept all advertisers (fine for learning / demo).
     *
     * Always clears [devices] first so each Scan click starts from an empty list.
     * If a scan is already running, it is stopped and restarted.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        // Drop previous results immediately so the UI shows an empty list.
        _devices.value = emptyList()

        if (!hasRequiredPermissions() || adapter == null || !adapter.isEnabled) {
            _isScanning.value = false
            return
        }

        // Restarting: stop the current session before starting a new one.
        if (scanning) {
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
            _isScanning.value = false
        }

        val scanner = adapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            // LOW_LATENCY finds devices faster; uses more power — OK for short UI scans.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        _isScanning.value = true
    }

    /** Stop listening for advertisements (call before connect, and on screen exit). */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        if (hasRequiredPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        scanning = false
        _isScanning.value = false
    }

    /**
     * Open a GATT connection to a previously scanned (real) device.
     *
     * Steps:
     * 1. Stop scanning so the radio can focus on connecting.
     * 2. Close any previous GATT client.
     * 3. Resolve a [BluetoothDevice] from the MAC address.
     * 4. call connectGatt(...) — result arrives later in [gattCallback],
     *    not as a return value from this function.
     *
     * Simulated/demo devices are ignored here; the UI fakes that path.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: WorkoutDevice) {
        if (device.isSimulated) {
            // Simulated path is handled by the UI layer.
            return
        }
        if (!hasRequiredPermissions() || adapter == null) {
            _connectionState.value = BleConnectionState.Failed(
                address = device.address,
                reason = "Bluetooth permission or adapter unavailable"
            )
            return
        }

        stopScan()
        closeGatt()
        _connectionState.value = BleConnectionState.Connecting(device.address)

        // getRemoteDevice does not contact the peripheral yet — it only builds
        // a local BluetoothDevice handle for this MAC. Invalid MAC → exception.
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            _connectionState.value = BleConnectionState.Failed(
                address = device.address,
                reason = "Invalid device address"
            )
            return
        }

        // API 37+ (CINNAMON_BUN) prefers BluetoothGattConnectionSettings + Executor;
        // older APIs keep the Context-based overload (deprecated on 37).
        gatt = openGatt(remote)
    }

    /**
     * Opens a GATT client for [remote].
     * Uses the API 37+ ([Build.VERSION_CODES.CINNAMON_BUN]) settings/executor overload
     * when available. Note: [Build.VERSION_CODES.BAKLAVA] is API 36 — too low for this API.
     */
    @SuppressLint("MissingPermission")
    private fun openGatt(remote: BluetoothDevice): BluetoothGatt? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            val settings = BluetoothGattConnectionSettings.Builder()
                .setTransport(BluetoothDevice.TRANSPORT_LE)
                .setAutoConnectEnabled(false)
                .build()
            remote.connectGatt(
                settings,
                ContextCompat.getMainExecutor(appContext),
                gattCallback
            )
        } else {
            @Suppress("DEPRECATION")
            remote.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }
    }

    /** Drop the link from our side and reset state (e.g. user taps Disconnect). */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (hasRequiredPermissions()) {
            gatt?.disconnect()
        }
        closeGatt()
        _connectionState.value = BleConnectionState.Idle
    }

    /** Clears a Failed banner so the user can try another device. */
    fun clearFailure() {
        if (_connectionState.value is BleConnectionState.Failed) {
            _connectionState.value = BleConnectionState.Idle
        }
    }

    /**
     * Used by the UI timeout: if we are still Connecting after N seconds,
     * treat it as Failed. Common on emulator / out-of-range devices.
     */
    fun failIfStillConnecting(reason: String) {
        val current = _connectionState.value
        if (current is BleConnectionState.Connecting) {
            closeGatt()
            _connectionState.value = BleConnectionState.Failed(
                address = current.address,
                reason = reason
            )
        }
    }

    /** Full teardown when the app composable leaves composition. */
    fun release() {
        stopScan()
        disconnect()
    }

    /**
     * Scan results can fire many times for the same peripheral as advertisements
     * repeat. We upsert by MAC address so the list stays unique.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            @SuppressLint("MissingPermission")
            val rawName = if (hasRequiredPermissions()) device.name else null
            // Prefer the bonded/system name; fall back to the name inside the
            // advertisement payload; last resort a generic label.
            val name = rawName?.takeIf { it.isNotBlank() }
                ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                ?: "BLE Device"

            _devices.update { current ->
                val existing = current.indexOfFirst { it.address == address }
                val mapped = WorkoutDevice(address = address, name = name, isSimulated = false)
                if (existing >= 0) {
                    current.toMutableList().apply { this[existing] = mapped }
                } else {
                    current + mapped
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _isScanning.value = false
        }
    }

    /**
     * GATT callbacks run on a binder thread — keep work light and only publish
     * state. @status is the GATT operation result; @newState is connected/disconnected.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            when {
                // Operation itself failed (auth, remote reject, stack error, etc.).
                status != BluetoothGatt.GATT_SUCCESS -> {
                    closeGatt()
                    _connectionState.value = BleConnectionState.Failed(
                        address = address,
                        reason = "GATT status $status"
                    )
                }
                // Link is up. Next real-world step would be discoverServices() and
                // subscribe to characteristics (steps, heart rate, …). Not yet.
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Connected(address)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    // If we were still trying to connect, surface it as a failure.
                    // If we were already connected, just return to Idle.
                    if (_connectionState.value is BleConnectionState.Connecting) {
                        _connectionState.value = BleConnectionState.Failed(
                            address = address,
                            reason = "Connection dropped"
                        )
                    } else {
                        _connectionState.value = BleConnectionState.Idle
                    }
                }
            }
        }
    }

    /**
     * disconnect() asks the remote to drop the link; close() releases local
     * native resources. Always close when done or you can leak GATT clients.
     */
    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        if (hasRequiredPermissions()) {
            gatt?.close()
        }
        gatt = null
    }
}
