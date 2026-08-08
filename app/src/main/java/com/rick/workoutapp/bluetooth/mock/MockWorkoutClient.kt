package com.rick.workoutapp.bluetooth.mock

import com.rick.workoutapp.bluetooth.BleConnectionState
import com.rick.workoutapp.model.StepSample
import com.rick.workoutapp.model.WorkoutDevice
import com.rick.workoutapp.model.mockStepMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Mock GATT **client** (the phone).
 * Performs a realistic connect sequence against [MockStepMachineServer] over [MockBlePipe]:
 * connect → discover services → enable notifications → receive measurement bytes.
 */
class MockWorkoutClient {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val pipe = MockBlePipe()
    private val server = MockStepMachineServer(pipe, scope).also { it.attach() }

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _samples = MutableSharedFlow<StepSample>(extraBufferCapacity = 64)
    val samples: SharedFlow<StepSample> = _samples.asSharedFlow()

    private var notifyJob: Job? = null
    private var connectJob: Job? = null

    /** Services “discovered” after connect — for learning/debug parity with real GATT. */
    var discoveredServiceUuid: String? = null
        private set
    var notificationsEnabled: Boolean = false
        private set

    fun connect(device: WorkoutDevice = mockStepMachine) {
        if (!device.isSimulated) return
        connectJob?.cancel()
        disconnectInternal(keepState = false)
        _connectionState.value = BleConnectionState.Connecting(device.address)

        connectJob = scope.launch {
            // Simulate radio + stack latency.
            delay(450.milliseconds)
            server.onClientConnected()

            delay(350.milliseconds)
            discoveredServiceUuid = server.serviceUuid.toString()

            delay(250.milliseconds)
            notificationsEnabled = true
            notifyJob = pipe.notifications
                .onEach { notification ->
                    if (notification.characteristicUuid != server.measurementUuid) return@onEach
                    val sample = MockStepGattProfile.decodeMeasurement(notification.value)
                        ?: return@onEach
                    _samples.emit(sample)
                }
                .launchIn(scope)

            _connectionState.value = BleConnectionState.Connected(device.address)
        }
    }

    /**
     * Phone writes session-control characteristic (start).
     * Machine server receives the write and begins notifying measurements.
     */
    fun startWorkout() {
        if (_connectionState.value !is BleConnectionState.Connected) return
        scope.launch {
            pipe.write(
                characteristicUuid = server.controlUuid,
                value = byteArrayOf(MockStepGattProfile.CONTROL_START),
            )
        }
    }

    /** Phone writes stop; machine stops notifying. Link stays up. */
    fun stopWorkout() {
        if (_connectionState.value !is BleConnectionState.Connected) return
        scope.launch {
            pipe.write(
                characteristicUuid = server.controlUuid,
                value = byteArrayOf(MockStepGattProfile.CONTROL_STOP),
            )
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        disconnectInternal(keepState = true)
        _connectionState.value = BleConnectionState.Idle
    }

    fun release() {
        disconnect()
        server.release()
        job.cancel()
    }

    private fun disconnectInternal(keepState: Boolean) {
        notifyJob?.cancel()
        notifyJob = null
        notificationsEnabled = false
        discoveredServiceUuid = null
        server.onClientDisconnected()
        if (!keepState) {
            // no-op; caller sets Connecting/Idle
        }
    }
}
