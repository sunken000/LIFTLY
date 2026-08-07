package com.liftly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkoutShareCodecTest {
    @Test
    fun `round trip preserves dense exercise order and configuration`() {
        val exercises = listOf(exercise("a"), exercise("b", custom = true)).associateBy { it.id }
        val workout = WorkoutEntity(id = "source", name = "Push", weekDays = "1,4")
        val items = listOf(
            WorkoutExerciseEntity(
                id = "second",
                workoutId = workout.id,
                exerciseId = "b",
                orderIndex = 9,
                sets = 4,
                repMin = 6,
                repMax = 8,
                targetLoadKg = 32.5,
                restSeconds = 120,
            ),
            WorkoutExerciseEntity(
                id = "first",
                workoutId = workout.id,
                exerciseId = "a",
                orderIndex = 2,
            ),
        )

        val encoded = WorkoutShareCodec.encode(
            WorkoutShareCodec.fromEntities(workout, items, exercises, exportedAt = 1L)
        )
        val decoded = WorkoutShareCodec.decode(encoded)

        assertEquals("Push", decoded.workout.name)
        assertEquals(listOf("a", "b"), decoded.items.map { it.exerciseReferenceId })
        assertEquals(listOf(0, 1), decoded.items.map { it.orderIndex })
        assertEquals(32.5, decoded.items.last().targetLoadKg, 0.0)
        assertFalse(encoded.contains("source"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects item pointing to missing exercise`() {
        val valid = WorkoutShareCodec.fromEntities(
            WorkoutEntity(id = "source", name = "A"),
            listOf(WorkoutExerciseEntity("item", "source", "a", 0)),
            mapOf("a" to exercise("a")),
            exportedAt = 1L,
        )
        WorkoutShareCodec.decode(
            WorkoutShareCodec.encode(valid).replace("\"exerciseReferenceId\":\"a\"", "\"exerciseReferenceId\":\"missing\"")
        )
    }

    private fun exercise(id: String, custom: Boolean = false) = ExerciseEntity(
        id = id,
        name = "Exercise $id",
        muscleGroup = "Peito",
        equipment = "Barra",
        difficulty = "Iniciante",
        movementType = "Empurrar",
        category = "Musculação",
        instructions = "Execute com controle.",
        cautions = "Sem dor.",
        isCustom = custom,
    )
}
