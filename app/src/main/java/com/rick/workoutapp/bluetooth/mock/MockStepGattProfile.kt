package com.rick.workoutapp.bluetooth.mock

import com.rick.workoutapp.model.StepSample
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Fake GATT profile for the mock step machine.
 * Mirrors how a real peripheral exposes a service + notify characteristic.
 *
 * Wire format for step measurements (little-endian):
 * ```
 * byte 0     : flags (bit0 = steps present, bit1 = elapsed present)
 * bytes 1..4 : cumulative steps (uint32)
 * bytes 5..6 : elapsed seconds (uint16)
 * ```
 */
object MockStepGattProfile {
    val SERVICE_UUID: UUID =
        UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    val STEP_MEASUREMENT_UUID: UUID =
        UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
    /** Client would write 0x01 start / 0x00 stop on a real control char. */
    val SESSION_CONTROL_UUID: UUID =
        UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")

    private const val FLAG_STEPS: Int = 0x01
    private const val FLAG_ELAPSED: Int = 0x02

    const val CONTROL_START: Byte = 0x01
    const val CONTROL_STOP: Byte = 0x00

    fun encodeMeasurement(sample: StepSample): ByteArray {
        val buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put((FLAG_STEPS or FLAG_ELAPSED).toByte())
        buffer.putInt(sample.cumulativeSteps)
        buffer.putShort(sample.elapsedSeconds.coerceIn(0, 0xFFFF).toShort())
        return buffer.array()
    }

    fun decodeMeasurement(payload: ByteArray): StepSample? {
        if (payload.size < 7) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        if (flags and FLAG_STEPS == 0) return null
        val steps = buffer.int
        val elapsed = if (flags and FLAG_ELAPSED != 0) {
            buffer.short.toInt() and 0xFFFF
        } else {
            0
        }
        return StepSample(cumulativeSteps = steps, elapsedSeconds = elapsed)
    }
}
