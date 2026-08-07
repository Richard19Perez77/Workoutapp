package com.rick.workoutapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rick.workoutapp.model.FakeStepMachine
import com.rick.workoutapp.model.fakeStepMachines
import com.rick.workoutapp.ui.DevicesScreen
import com.rick.workoutapp.ui.WorkoutScreen
import com.rick.workoutapp.ui.theme.WorkoutappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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
                            onDeviceConnected = { device: FakeStepMachine ->
                                connectedDeviceId = device.id
                            }
                        )
                    }
                }
            }
        }
    }
}
