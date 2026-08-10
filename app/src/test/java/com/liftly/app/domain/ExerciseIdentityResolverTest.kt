package com.liftly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdentityResolverTest {
    @Test
    fun `classifies compound name with all relevant tags`() {
        val identity = ExerciseIdentityResolver.resolve(
            name = "Agachamento Búlgaro no Hack",
            movementType = "Agachar unilateral",
            muscleGroup = "Quadríceps",
            secondaryMuscles = "Glúteos",
            equipment = "Hack",
            category = "Musculação",
        )

        assertEquals("squat", identity.movementPattern)
        assertTrue(setOf("agachamento", "unilateral", "bulgaro", "hack", "quadriceps", "gluteos").all { it in identity.tags })
    }

    @Test
    fun `incline chest press variations share the same family`() {
        val dumbbells = ExerciseIdentityResolver.resolve("Supino inclinado com halteres", muscleGroup = "Peitoral", equipment = "Halteres")
        val smith = ExerciseIdentityResolver.resolve("Supino inclinado Smith", muscleGroup = "Peitoral", equipment = "Smith")
        val machine = ExerciseIdentityResolver.resolve("Supino inclinado máquina", muscleGroup = "Peitoral", equipment = "Máquina")

        assertEquals("chest_press", dumbbells.family)
        assertEquals(dumbbells.family, smith.family)
        assertEquals(dumbbells.family, machine.family)
        assertEquals("horizontal_push", machine.movementPattern)
    }
}
