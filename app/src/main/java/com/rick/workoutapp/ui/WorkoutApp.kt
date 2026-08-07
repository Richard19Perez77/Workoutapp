package com.rick.workoutapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rick.workoutapp.model.fakeStepMachines
import com.rick.workoutapp.ui.theme.WorkoutappTheme

@Composable
fun WorkoutApp() {
    WorkoutappTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            var connectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
            val connectedDevice = fakeStepMachines.find { it.id == connectedDeviceId }

            if (connectedDevice != null) {
                WorkoutScreen(
                    device = connectedDevice,
                    onDisconnect = { connectedDeviceId = null }
                )
            } else {
                DevicesScreen(
                    onDeviceConnected = { connectedDeviceId = it.id }
                )
            }
        }
    }
}
