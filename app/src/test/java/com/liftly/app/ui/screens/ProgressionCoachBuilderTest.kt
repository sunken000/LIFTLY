package com.liftly.app.ui.screens

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.WorkoutExerciseEntity
import com.liftly.app.domain.ProgressionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionCoachBuilderTest {
    @Test
    fun `current sixty kilograms overrides previous one hundred kilograms`() {
        val current = listOf(
            set(id = "current-1", sessionId = CURRENT_SESSION, setNumber = 1, load = 60.0, rir = 4),
            set(id = "current-2", sessionId = CURRENT_SESSION, setNumber = 2, load = 60.0, rir = 4),
        )
        val previous = listOf(
            set(id = "previous-1", sessionId = PREVIOUS_SESSION, setNumber = 1, load = 100.0, rir = 2),
            set(id = "previous-2", sessionId = PREVIOUS_SESSION, setNumber = 2, load = 100.0, rir = 2),
        )

        val result = recommendation(current + previous)

        requireNotNull(result)
        assertEquals(ProgressionStatus.INCREASE, result.status)
        assertEquals(61.5, result.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `first completed set with rir four immediately suggests load for next sets`() {
        val current = listOf(
            set(
                id = "current-1",
                sessionId = CURRENT_SESSION,
                setNumber = 1,
                load = 60.0,
                rir = 4,
            ),
            set(
                id = "current-2",
                sessionId = CURRENT_SESSION,
                setNumber = 2,
                load = 60.0,
                rir = null,
                completed = false,
            ),
        )
        val previous = listOf(
            set(id = "previous-1", sessionId = PREVIOUS_SESSION, setNumber = 1, load = 100.0, rir = 2),
        )

        val result = recommendation(current + previous)

        requireNotNull(result)
        assertEquals(ProgressionStatus.INCREASE, result.status)
        assertEquals(61.5, result.suggestedLoadKg ?: 0.0, 0.001)
        assertEquals(true, result.reasons.any { it.contains("1 série") })
    }

    @Test
    fun `untouched current exercise never displays previous workout as recommendation`() {
        val current = listOf(
            set("current-1", CURRENT_SESSION, 1, 60.0, null, completed = false),
            set("current-2", CURRENT_SESSION, 2, 60.0, null, completed = false),
        )
        val previous = listOf(
            set("previous-1", PREVIOUS_SESSION, 1, 100.0, 4),
        )

        assertNull(recommendation(current + previous))
    }

    @Test
    fun `first completed set with moderate pain immediately reduces next set load`() {
        val current = listOf(
            set("current-1", CURRENT_SESSION, 1, 60.0, 4, pain = 4),
            set("current-2", CURRENT_SESSION, 2, 60.0, null, completed = false),
        )

        val result = recommendation(current)

        requireNotNull(result)
        assertEquals(ProgressionStatus.REDUCE, result.status)
        assertEquals(54.0, result.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `legacy repetition label still participates in coach calculation`() {
        val current = listOf(
            set(
                id = "current-1",
                sessionId = CURRENT_SESSION,
                setNumber = 1,
                load = 60.0,
                rir = 4,
                trackingMode = "Carga e reps",
            ),
            set(
                id = "current-2",
                sessionId = CURRENT_SESSION,
                setNumber = 2,
                load = 60.0,
                rir = 4,
                trackingMode = "Reps",
            ),
        )

        val result = recommendation(current)

        requireNotNull(result)
        assertEquals(ProgressionStatus.INCREASE, result.status)
        assertEquals(61.5, result.suggestedLoadKg ?: 0.0, 0.001)
    }

    private fun recommendation(allSets: List<SessionSetEntity>) = buildCoachRecommendation(
        currentSessionId = CURRENT_SESSION,
        exerciseSets = allSets.filter { it.sessionId == CURRENT_SESSION },
        allSets = allSets,
        sessions = listOf(
            SessionEntity(
                id = CURRENT_SESSION,
                workoutId = WORKOUT_ID,
                workoutName = "Treino B",
                startedAt = 2_000L,
            ),
            SessionEntity(
                id = PREVIOUS_SESSION,
                workoutId = WORKOUT_ID,
                workoutName = "Treino B",
                startedAt = 1_000L,
                finishedAt = 1_500L,
                status = "Concluído",
            ),
        ),
        workoutItem = WorkoutExerciseEntity(
            id = WORKOUT_EXERCISE_ID,
            workoutId = WORKOUT_ID,
            exerciseId = EXERCISE_ID,
            orderIndex = 0,
            sets = 2,
            repMin = 12,
            repMax = 20,
            targetLoadKg = 100.0,
        ),
        exercise = ExerciseEntity(
            id = EXERCISE_ID,
            name = "Panturrilha sentada",
            muscleGroup = "Panturrilhas",
            equipment = "Máquina",
            difficulty = "Iniciante",
            movementType = "Flexão plantar",
            category = "Musculação",
            instructions = "",
            cautions = "",
        ),
    )

    private fun set(
        id: String,
        sessionId: String,
        setNumber: Int,
        load: Double,
        rir: Int?,
        completed: Boolean = true,
        trackingMode: String = "Repetições",
        reps: Int = 12,
        pain: Int = 0,
    ) = SessionSetEntity(
        id = id,
        sessionId = sessionId,
        workoutExerciseId = WORKOUT_EXERCISE_ID,
        exerciseId = EXERCISE_ID,
        exerciseName = "Panturrilha sentada",
        setNumber = setNumber,
        reps = reps,
        loadKg = load,
        completed = completed,
        completedAt = if (completed) 3_000L + setNumber else null,
        trackingMode = trackingMode,
        plannedReps = 12,
        plannedLoadKg = 100.0,
        rir = rir,
        painLevel = pain,
    )

    private companion object {
        const val CURRENT_SESSION = "session-current"
        const val PREVIOUS_SESSION = "session-previous"
        const val WORKOUT_ID = "workout"
        const val WORKOUT_EXERCISE_ID = "workout-exercise"
        const val EXERCISE_ID = "exercise"
    }
}
