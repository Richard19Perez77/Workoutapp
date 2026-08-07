package com.rick.workoutapp.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
 * Real BLE scan + GATT connect.
 * On emulator this usually finds nothing and connect attempts fail/timeout —
 * that is expected (no host Bluetooth passthrough).
 */
class BleWorkoutConnector(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _devices = MutableStateFlow<List<WorkoutDevice>>(emptyList())
    val devices: StateFlow<List<WorkoutDevice>> = _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanning = false

    val isBluetoothAvailable: Boolean
        get() = adapter != null

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

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

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions() || adapter == null || !adapter.isEnabled) {
            _isScanning.value = false
            return
        }
        if (scanning) return

        _devices.value = emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        if (hasRequiredPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        scanning = false
        _isScanning.value = false
    }

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

        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            _connectionState.value = BleConnectionState.Failed(
                address = device.address,
                reason = "Invalid device address"
            )
            return
        }

        gatt = remote.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (hasRequiredPermissions()) {
            gatt?.disconnect()
        }
        closeGatt()
        _connectionState.value = BleConnectionState.Idle
    }

    fun clearFailure() {
        if (_connectionState.value is BleConnectionState.Failed) {
            _connectionState.value = BleConnectionState.Idle
        }
    }

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

    fun release() {
        stopScan()
        disconnect()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            @SuppressLint("MissingPermission")
            val rawName = if (hasRequiredPermissions()) device.name else null
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

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    closeGatt()
                    _connectionState.value = BleConnectionState.Failed(
                        address = address,
                        reason = "GATT status $status"
                    )
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Connected(address)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
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

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        if (hasRequiredPermissions()) {
            gatt?.close()
        }
        gatt = null
    }
}
