package com.rick.workoutapp.model

data class FakeStepMachine(
    val id: String,
    val name: String,
)

val fakeStepMachines = listOf(
    FakeStepMachine(id = "step-1", name = "Step Machine A"),
    FakeStepMachine(id = "step-2", name = "Step Machine B"),
    FakeStepMachine(id = "step-3", name = "Step Machine C"),
)
