package com.rick.workoutapp.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rick.workoutapp.R
import com.rick.workoutapp.bluetooth.BleConnectionState
import com.rick.workoutapp.bluetooth.BleWorkoutConnector
import com.rick.workoutapp.model.WorkoutDevice
import com.rick.workoutapp.model.demoStepMachines
import com.rick.workoutapp.ui.theme.WorkoutappTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val ConnectedGreen = Color(0xFF2E7D32)

@Composable
fun DevicesScreen(
    connector: BleWorkoutConnector,
    onDeviceConnected: (WorkoutDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scannedDevices by connector.devices.collectAsState()
    val connectionState by connector.connectionState.collectAsState()
    val isScanning by connector.isScanning.collectAsState()

    var hasPermissions by remember { mutableStateOf(connector.hasRequiredPermissions()) }
    var connectingDemoAddress by remember { mutableStateOf<String?>(null) }
    var connectedDemoAddress by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
        if (hasPermissions && connector.isBluetoothEnabled) {
            connector.startScan()
            statusMessage = null
        } else if (!hasPermissions) {
            statusMessage = context.getString(R.string.bt_permission_denied)
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (connector.isBluetoothEnabled && hasPermissions) {
            connector.startScan()
            statusMessage = null
        } else {
            statusMessage = context.getString(R.string.bt_disabled)
        }
    }

    fun beginScan() {
        when {
            !connector.isBluetoothAvailable -> {
                statusMessage = context.getString(R.string.bt_unavailable)
            }
            !hasPermissions -> {
                permissionLauncher.launch(connector.requiredPermissions())
            }
            !connector.isBluetoothEnabled -> {
                statusMessage = context.getString(R.string.bt_disabled)
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> {
                statusMessage = null
                connector.startScan()
            }
        }
    }

    LaunchedEffect(Unit) {
        beginScan()
    }

    // Keep scans finite so the UI settles on emulator/hardware.
    LaunchedEffect(isScanning) {
        if (!isScanning) return@LaunchedEffect
        delay(8.seconds)
        connector.stopScan()
    }

    DisposableEffect(connector) {
        onDispose { connector.stopScan() }
    }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is BleConnectionState.Connected -> {
                val device = scannedDevices.find { it.address == state.address }
                    ?: WorkoutDevice(address = state.address, name = "BLE Device")
                onDeviceConnected(device)
            }
            is BleConnectionState.Failed -> {
                statusMessage = context.getString(
                    R.string.bt_connect_failed,
                    state.reason
                )
            }
            else -> Unit
        }
    }

    LaunchedEffect(connectionState) {
        val connecting = connectionState as? BleConnectionState.Connecting ?: return@LaunchedEffect
        delay(10.seconds)
        connector.failIfStillConnecting("Timed out waiting for ${connecting.address}")
    }

    val listDevices = scannedDevices + demoStepMachines

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.connect_device_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.connect_device_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { beginScan() }) {
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.bt_scanning)
                    } else {
                        stringResource(R.string.bt_scan)
                    }
                )
            }
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = stringResource(R.string.bt_demo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(listDevices, key = { it.address }) { device ->
                val isConnecting = if (device.isSimulated) {
                    connectingDemoAddress == device.address
                } else {
                    (connectionState as? BleConnectionState.Connecting)?.address == device.address
                }
                val isConnected = if (device.isSimulated) {
                    connectedDemoAddress == device.address
                } else {
                    (connectionState as? BleConnectionState.Connected)?.address == device.address
                }
                val isBusy = connectingDemoAddress != null ||
                    connectionState is BleConnectionState.Connecting

                DeviceRow(
                    device = device,
                    isConnecting = isConnecting,
                    isConnected = isConnected,
                    enabled = !isBusy || isConnecting,
                    onClick = {
                        if (isBusy) return@DeviceRow
                        connector.clearFailure()
                        statusMessage = null
                        if (device.isSimulated) {
                            connectingDemoAddress = device.address
                            scope.launch {
                                delay(1.seconds)
                                connectingDemoAddress = null
                                connectedDemoAddress = device.address
                                delay(400)
                                onDeviceConnected(device)
                            }
                        } else {
                            connector.connect(device)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: WorkoutDevice,
    isConnecting: Boolean,
    isConnected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    isConnecting -> stringResource(R.string.status_connecting)
                    isConnected -> stringResource(R.string.status_connected)
                    device.isSimulated -> stringResource(R.string.status_demo)
                    else -> device.address
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isConnected -> ConnectedGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        when {
            isConnecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            isConnected -> {
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = CircleShape,
                    color = ConnectedGreen
                ) {}
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicesScreenPreview() {
    WorkoutappTheme {
        val context = LocalContext.current
        DevicesScreen(
            connector = BleWorkoutConnector(context),
            onDeviceConnected = {}
        )
    }
}
