package com.rick.workoutapp.bluetooth

/**
 * UI-facing connection lifecycle for a single GATT attempt.
 *
 * Typical happy path:
 *   Idle → Connecting(address) → Connected(address)
 *
 * Failures (permission issues, timeout, GATT error, drop while connecting):
 *   Idle → Connecting(address) → Failed(address, reason)
 *
 * Disconnect from the workout screen returns to Idle.
 */
sealed interface BleConnectionState {
    /** No active GATT session and no in-flight connect. */
    data object Idle : BleConnectionState

    /** connectGatt() was called; waiting for onConnectionStateChange. */
    data class Connecting(val address: String) : BleConnectionState

    /** GATT link is up for [address]. Safe to move into the workout UI. */
    data class Connected(val address: String) : BleConnectionState

    /** Connect did not succeed; [reason] is shown in the devices UI. */
    data class Failed(val address: String, val reason: String) : BleConnectionState
}
