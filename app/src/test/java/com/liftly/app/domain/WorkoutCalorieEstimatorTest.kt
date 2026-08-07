package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCalorieEstimatorTest {
    @Test
    fun `standard strength session follows met body weight and time formula`() {
        val estimate = WorkoutCalorieEstimator.estimate(
            session = session(durationMinutes = 60),
            sets = listOf(set(exerciseId = "strength")),
            exercises = listOf(exercise("strength", "Musculação", "Supino reto")),
            bodyWeightKg = 70.0,
        )

        assertEquals(257, estimate?.kilocalories)
        assertEquals(3.5, estimate?.averageMet ?: 0.0, 0.001)
        assertFalse(estimate?.durationWasCapped ?: true)
    }

    @Test
    fun `mixed workout combines met values from completed exercises`() {
        val estimate = WorkoutCalorieEstimator.estimate(
            session = session(durationMinutes = 60),
            sets = listOf(
                set(id = "s1", exerciseId = "strength"),
                set(id = "s2", exerciseId = "run"),
            ),
            exercises = listOf(
                exercise("strength", "Musculação", "Supino reto"),
                exercise("run", "Cardio", "Corrida na esteira"),
            ),
            bodyWeightKg = 70.0,
        )

        assertEquals(5.5, estimate?.averageMet ?: 0.0, 0.001)
        assertEquals(404, estimate?.kilocalories)
    }

    @Test
    fun `recorded cardio duration has proportional influence in a mixed session`() {
        val estimate = WorkoutCalorieEstimator.estimate(
            session = session(durationMinutes = 30),
            sets = listOf(
                set(id = "s1", exerciseId = "strength"),
                set(id = "s2", exerciseId = "run", trackingMode = "Tempo", durationSeconds = 600),
            ),
            exercises = listOf(
                exercise("strength", "Musculação", "Supino reto"),
                exercise("run", "Cardio", "Corrida na esteira"),
            ),
            bodyWeightKg = 70.0,
        )

        assertTrue((estimate?.averageMet ?: 0.0) > 7.0)
    }

    @Test
    fun `estimate requires weight and completed work`() {
        val validSession = session(durationMinutes = 30)
        val completedSet = set(exerciseId = "strength")
        val exercise = exercise("strength", "Musculação", "Supino reto")

        assertNull(WorkoutCalorieEstimator.estimate(validSession, listOf(completedSet), listOf(exercise), null))
        assertNull(
            WorkoutCalorieEstimator.estimate(
                validSession,
                listOf(completedSet.copy(completed = false)),
                listOf(exercise),
                70.0,
            ),
        )
    }

    @Test
    fun `implausibly long open session is capped at four hours`() {
        val estimate = WorkoutCalorieEstimator.estimate(
            session = session(durationMinutes = 10 * 60),
            sets = listOf(set(exerciseId = "strength")),
            exercises = listOf(exercise("strength", "Musculação", "Supino reto")),
            bodyWeightKg = 70.0,
        )

        assertEquals(240.0, estimate?.durationMinutes ?: 0.0, 0.001)
        assertTrue(estimate?.durationWasCapped == true)
    }

    private fun session(durationMinutes: Int) = SessionEntity(
        id = "session",
        workoutId = "workout",
        workoutName = "Treino",
        startedAt = 1_000L,
        finishedAt = 1_000L + durationMinutes * 60_000L,
    )

    private fun set(
        id: String = "set",
        exerciseId: String,
        trackingMode: String = "Repetições",
        durationSeconds: Int = 0,
    ) = SessionSetEntity(
        id = id,
        sessionId = "session",
        workoutExerciseId = exerciseId,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = 1,
        reps = 10,
        loadKg = 20.0,
        completed = true,
        trackingMode = trackingMode,
        durationSeconds = durationSeconds,
    )

    private fun exercise(id: String, category: String, name: String) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Corpo inteiro",
        equipment = "Equipamento",
        difficulty = "Intermediário",
        movementType = "Movimento",
        category = category,
        instructions = "",
        cautions = "",
    )
}
