package com.rick.workoutapp.model

data class WorkoutDevice(
    val address: String,
    val name: String,
    val isSimulated: Boolean = false,
)

val demoStepMachines = listOf(
    WorkoutDevice(address = "demo-1", name = "Step Machine A", isSimulated = true),
    WorkoutDevice(address = "demo-2", name = "Step Machine B", isSimulated = true),
    WorkoutDevice(address = "demo-3", name = "Step Machine C", isSimulated = true),
)
