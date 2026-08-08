package com.rick.workoutapp.model

data class WorkoutDevice(
    val address: String,
    val name: String,
    val isSimulated: Boolean = false,
)

/**
 * The single in-app mock peripheral used when hardware BLE is unavailable
 * or when practicing the client/server data path.
 */
val mockStepMachine = WorkoutDevice(
    address = "MOCK:STEP:01",
    name = "GymStep Pro (Mock)",
    isSimulated = true,
)
