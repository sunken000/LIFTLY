package com.liftly.app.integration.healthconnect

import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutHealthExportMapperTest {
    @Test
    fun `finished workout maps to stable idempotent payload`() {
        val session = session(finishedAt = 1_700_003_600_000L)
        val sets = listOf(
            set(id = "s1", exerciseId = "squat", reps = 10, loadKg = 60.0, completed = true),
            set(id = "s2", exerciseId = "squat", reps = 8, loadKg = 65.0, completed = true),
            set(id = "s3", exerciseId = "bench", reps = 10, loadKg = 40.0, completed = false),
        )

        val result = WorkoutHealthExportMapper.prepare(session, sets)

        assertTrue(result is WorkoutHealthExportPreparation.Ready)
        val payload = (result as WorkoutHealthExportPreparation.Ready).payload
        assertEquals("liftly-workout-session-1", payload.clientRecordId)
        assertEquals("Treino A", payload.title)
        assertEquals(1_700_003_600_000L, payload.clientRecordVersion)
        assertTrue(payload.notes.contains("2 séries concluídas"))
        assertTrue(payload.notes.contains("1 exercício"))
        assertTrue(payload.notes.contains("volume 1120 kg·reps"))
    }

    @Test
    fun `test workout is never exported`() {
        val result = WorkoutHealthExportMapper.prepare(
            session = session(
                finishedAt = 1_700_003_600_000L,
                isTestMode = true,
            ),
            sessionSets = emptyList(),
        )

        assertEquals(
            WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.TEST_SESSION,
            ),
            result,
        )
    }

    @Test
    fun `unfinished and invalid sessions are rejected before Android API call`() {
        assertEquals(
            WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.SESSION_NOT_FINISHED,
            ),
            WorkoutHealthExportMapper.prepare(
                session = session(finishedAt = null),
                sessionSets = emptyList(),
            ),
        )
        assertEquals(
            WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.INVALID_TIME_RANGE,
            ),
            WorkoutHealthExportMapper.prepare(
                session = session(finishedAt = 1_700_000_000_000L),
                sessionSets = emptyList(),
            ),
        )
    }

    @Test
    fun `empty workout name gets a human fallback and notes stay bounded`() {
        val result = WorkoutHealthExportMapper.prepare(
            session = session(
                workoutName = " ",
                finishedAt = 1_700_003_600_000L,
                notes = "x".repeat(2_000),
            ),
            sessionSets = emptyList(),
        ) as WorkoutHealthExportPreparation.Ready

        assertEquals("Treino Liftly", result.payload.title)
        assertTrue(result.payload.notes.length <= 1_000)
    }

    private fun session(
        workoutName: String = "Treino A",
        finishedAt: Long?,
        isTestMode: Boolean = false,
        notes: String = "",
    ) = SessionEntity(
        id = "session-1",
        workoutId = "workout-1",
        workoutName = workoutName,
        startedAt = 1_700_000_000_000L,
        finishedAt = finishedAt,
        status = if (finishedAt == null) "Em andamento" else "Concluído",
        notes = notes,
        isTestMode = isTestMode,
    )

    private fun set(
        id: String,
        exerciseId: String,
        reps: Int,
        loadKg: Double,
        completed: Boolean,
    ) = SessionSetEntity(
        id = id,
        sessionId = "session-1",
        workoutExerciseId = "workout-exercise-$exerciseId",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = 1,
        reps = reps,
        loadKg = loadKg,
        completed = completed,
    )
}
