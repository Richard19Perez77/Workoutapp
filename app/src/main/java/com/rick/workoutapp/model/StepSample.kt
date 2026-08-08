package com.rick.workoutapp.model

/**
 * App-layer step measurement after parsing a GATT notification payload.
 * Over the radio this is bytes; in the UI this is what we display.
 */
data class StepSample(
    val cumulativeSteps: Int,
    val elapsedSeconds: Int,
)
