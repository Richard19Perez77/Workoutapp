package com.rick.workoutapp.bluetooth.mock

import com.rick.workoutapp.model.StepSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Mock GATT **server** (the workout machine).
 * Accepts a client link, listens for session start/stop writes, and notifies
 * step measurement bytes — same shape as a real peripheral.
 */
class MockStepMachineServer(
    private val pipe: MockBlePipe,
    private val scope: CoroutineScope,
) {
    private var sessionJob: Job? = null
    private var controlJob: Job? = null
    private var cumulativeSteps: Int = 0
    private var elapsedSeconds: Int = 0
    private var clientConnected: Boolean = false

    val serviceUuid = MockStepGattProfile.SERVICE_UUID
    val measurementUuid = MockStepGattProfile.STEP_MEASUREMENT_UUID
    val controlUuid = MockStepGattProfile.SESSION_CONTROL_UUID

    fun attach() {
        controlJob?.cancel()
        controlJob = pipe.controlWrites
            .onEach { write ->
                if (write.characteristicUuid != controlUuid) return@onEach
                if (write.value.isEmpty()) return@onEach
                when (write.value[0]) {
                    MockStepGattProfile.CONTROL_START -> startSession()
                    MockStepGattProfile.CONTROL_STOP -> stopSession()
                }
            }
            .launchIn(scope)
    }

    fun onClientConnected() {
        clientConnected = true
        cumulativeSteps = 0
        elapsedSeconds = 0
        stopSession()
    }

    fun onClientDisconnected() {
        clientConnected = false
        stopSession()
    }

    private fun startSession() {
        if (!clientConnected) return
        stopSession()
        cumulativeSteps = 0
        elapsedSeconds = 0
        sessionJob = scope.launch {
            while (isActive && clientConnected) {
                delay(1.seconds)
                elapsedSeconds += 1
                // Mock cadence: ~1 step per tick (steady walk on the machine).
                cumulativeSteps += 1
                val sample = StepSample(
                    cumulativeSteps = cumulativeSteps,
                    elapsedSeconds = elapsedSeconds,
                )
                pipe.notify(
                    characteristicUuid = measurementUuid,
                    value = MockStepGattProfile.encodeMeasurement(sample),
                )
            }
        }
    }

    private fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
    }

    fun release() {
        onClientDisconnected()
        controlJob?.cancel()
        controlJob = null
    }
}
