package com.rick.workoutapp.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rick.workoutapp.R
import com.rick.workoutapp.model.FakeStepMachine
import com.rick.workoutapp.model.fakeStepMachines
import com.rick.workoutapp.ui.theme.WorkoutappTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val ConnectedGreen = Color(0xFF2E7D32)

@Composable
fun DevicesScreen(
    onDeviceConnected: (FakeStepMachine) -> Unit,
    modifier: Modifier = Modifier,
) {
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }
    var connectedDeviceId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(fakeStepMachines, key = { it.id }) { device ->
                val isConnecting = connectingDeviceId == device.id
                val isConnected = connectedDeviceId == device.id
                val isBusy = connectingDeviceId != null

                DeviceRow(
                    device = device,
                    isConnecting = isConnecting,
                    isConnected = isConnected,
                    enabled = !isBusy || isConnecting,
                    onClick = {
                        if (connectingDeviceId != null) return@DeviceRow
                        connectingDeviceId = device.id
                        scope.launch {
                            delay(1_500.milliseconds)
                            connectingDeviceId = null
                            connectedDeviceId = device.id
                            delay(400.milliseconds)
                            onDeviceConnected(device)
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
    device: FakeStepMachine,
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
                    else -> stringResource(R.string.status_available)
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
        DevicesScreen(onDeviceConnected = {})
    }
}
