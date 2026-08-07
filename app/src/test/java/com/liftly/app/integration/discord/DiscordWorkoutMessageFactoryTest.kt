package com.liftly.app.integration.discord

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWorkoutMessageFactoryTest {
    @Test
    fun `includes metrics calories and changes made during workout`() {
        val session = session(finishedAt = 3_700_000L)
        val set = SessionSetEntity(
            id = "set-1",
            sessionId = session.id,
            workoutExerciseId = "item-1",
            exerciseId = "supino",
            exerciseName = "Supino \"reto\"",
            setNumber = 1,
            reps = 12,
            loadKg = 52.5,
            completed = true,
            exerciseOrder = 0,
            plannedReps = 10,
            plannedLoadKg = 50.0,
            rir = 2,
            painLevel = 1,
        )

        val result = DiscordWorkoutMessageFactory.create(
            session,
            listOf(set),
            listOf(exercise("supino", "Supino reto")),
            UserProfileEntity(nickname = "Ana", currentWeightKg = 70.0),
        )

        assertNotNull(result)
        result!!
        assertEquals(1, result.completedSets)
        assertEquals(630.0, result.volumeKg, 0.001)
        assertTrue(result.estimatedKilocalories!! > 0)
        assertTrue(result.json.contains("Calorias estimadas"))
        assertTrue(result.json.contains("630,0 kg"))
        assertTrue(result.json.contains("+2 reps"))
        assertTrue(result.json.contains("+2,5 kg"))
        assertTrue(result.json.contains("Supino \\\"reto\\\""))
        assertTrue(result.json.contains("Ana"))
        assertTrue(result.json.contains("RIR 2"))
        assertTrue(result.json.contains("dor 1/10"))
    }

    @Test
    fun `does not export test unfinished or empty workouts`() {
        val finished = session(finishedAt = 60_000L)
        assertNull(DiscordWorkoutMessageFactory.create(finished.copy(isTestMode = true), listOf(completedSet(finished.id)), emptyList(), null))
        assertNull(DiscordWorkoutMessageFactory.create(finished.copy(finishedAt = null), listOf(completedSet(finished.id)), emptyList(), null))
        assertNull(DiscordWorkoutMessageFactory.create(finished, emptyList(), emptyList(), null))
    }

    @Test
    fun `escapes user controlled json text`() {
        val session = session(finishedAt = 60_000L).copy(workoutName = "A\nB\\C")
        val result = DiscordWorkoutMessageFactory.create(
            session,
            listOf(completedSet(session.id)),
            emptyList(),
            UserProfileEntity(nickname = "João\tSilva", currentWeightKg = 80.0),
        )!!

        assertTrue(result.json.contains("A\\nB\\\\C"))
        assertTrue(result.json.contains("João\\tSilva"))
    }

    private fun session(finishedAt: Long?) = SessionEntity(
        id = "session-1",
        workoutId = "workout-1",
        workoutName = "Treino A",
        startedAt = 100_000L,
        finishedAt = finishedAt,
        status = "Concluído",
    )

    private fun completedSet(sessionId: String) = SessionSetEntity(
        id = "set-1",
        sessionId = sessionId,
        workoutExerciseId = "item-1",
        exerciseId = "exercise-1",
        exerciseName = "Agachamento",
        setNumber = 1,
        reps = 10,
        loadKg = 60.0,
        completed = true,
    )

    private fun exercise(id: String, name: String) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Peitoral",
        equipment = "Barra",
        difficulty = "Intermediário",
        movementType = "Composto",
        category = "Musculação",
        instructions = "",
        cautions = "",
    )
}
