package com.rick.workoutapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rick.workoutapp.bluetooth.BleWorkoutConnector
import com.rick.workoutapp.model.WorkoutDevice
import com.rick.workoutapp.ui.theme.WorkoutappTheme

@Composable
fun WorkoutApp() {
    val context = LocalContext.current
    val connector = remember { BleWorkoutConnector(context.applicationContext) }

    DisposableEffect(connector) {
        onDispose { connector.release() }
    }

    WorkoutappTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            var connectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
            var connectedName by rememberSaveable { mutableStateOf<String?>(null) }
            var connectedIsSimulated by rememberSaveable { mutableStateOf(false) }

            val connectedDevice = connectedAddress?.let { address ->
                WorkoutDevice(
                    address = address,
                    name = connectedName ?: address,
                    isSimulated = connectedIsSimulated
                )
            }

            if (connectedDevice != null) {
                WorkoutScreen(
                    device = connectedDevice,
                    onDisconnect = {
                        connector.disconnect()
                        connectedAddress = null
                        connectedName = null
                        connectedIsSimulated = false
                    }
                )
            } else {
                DevicesScreen(
                    connector = connector,
                    onDeviceConnected = { device ->
                        connectedAddress = device.address
                        connectedName = device.name
                        connectedIsSimulated = device.isSimulated
                    }
                )
            }
        }
    }
}
