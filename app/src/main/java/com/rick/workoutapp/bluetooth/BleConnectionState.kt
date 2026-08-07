package com.rick.workoutapp.bluetooth

sealed interface BleConnectionState {
    data object Idle : BleConnectionState
    data class Connecting(val address: String) : BleConnectionState
    data class Connected(val address: String) : BleConnectionState
    data class Failed(val address: String, val reason: String) : BleConnectionState
}
