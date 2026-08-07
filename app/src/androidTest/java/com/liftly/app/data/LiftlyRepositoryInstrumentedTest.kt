package com.liftly.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiftlyRepositoryInstrumentedTest {
    private lateinit var database: LiftlyDatabase
    private lateinit var repository: LiftlyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LiftlyRepository(database, context)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun workoutSessionAndLoadsRemainInRoom() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Treino teste", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(workoutId, exerciseId, sets = 2, repMin = 8, repMax = 10, load = 20.0)

        val sessionId = repository.startSession(workoutId)
        val firstSet = database.dao().sessionSets(sessionId).first()
        repository.saveSet(firstSet, reps = 10, load = 22.5, toggleCompletion = true)

        val stored = database.dao().sessionSets(sessionId).first()
        assertTrue(stored.completed)
        assertEquals(10, stored.reps)
        assertEquals(22.5, stored.loadKg, 0.0)
        assertEquals(8, stored.plannedReps)
        assertEquals(20.0, stored.plannedLoadKg ?: -1.0, 0.0)
        assertNotNull(database.dao().activeSession())
    }

    @Test
    fun staleValueEditDoesNotUndoCompletionOrPlannedSnapshot() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Histórico", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(
            workoutId = workoutId,
            exerciseId = exerciseId,
            sets = 1,
            repMin = 8,
            repMax = 12,
            load = 20.0
        )

        val sessionId = repository.startSession(workoutId)
        val staleSnapshot = database.dao().sessionSets(sessionId).single()
        repository.saveSet(staleSnapshot, reps = 10, load = 22.5, toggleCompletion = true)
        repository.saveSet(staleSnapshot, reps = 11, load = 25.0, toggleCompletion = false)

        val stored = database.dao().sessionSets(sessionId).single()
        assertTrue(stored.completed)
        assertNotNull(stored.completedAt)
        assertEquals(11, stored.reps)
        assertEquals(25.0, stored.loadKg, 0.0)
        assertEquals(8, stored.plannedReps)
        assertEquals(20.0, stored.plannedLoadKg ?: -1.0, 0.0)
    }

    @Test
    fun restDayCannotCoexistWithScheduledWorkout() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("A", "", emptySet())
        val date = LocalDate.of(2026, 7, 20)
        repository.scheduleWorkout(date, workoutId)
        repository.setRestDay(date)
        val entries = database.dao().allSchedule().filter { it.date == date.toString() }
        assertEquals(1, entries.size)
        assertTrue(entries.single().isRestDay)
    }

    @Test
    fun backupRoundTripRestoresPrimaryData() = runBlocking {
        repository.initialize(useDemo = false)
        repository.createWorkout("Backup", "Treino salvo", emptySet())
        repository.saveWeight(75.2)
        val json = repository.backupJson()

        repository.deleteAllData()
        repository.importJson(json)

        assertEquals("Backup", database.dao().allWorkouts().single().name)
        assertEquals(75.2, database.dao().allWeights().single().weightKg, 0.0)
        assertEquals(ExerciseCatalog.exercises.size, database.dao().allExercises().count { !it.isCustom })
    }

    @Test
    fun versionFourBackupPreservesPlannedActualAndEffortValues() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Backup com histórico", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(
            workoutId = workoutId,
            exerciseId = exerciseId,
            sets = 1,
            repMin = 8,
            repMax = 12,
            load = 20.0
        )
        val sessionId = repository.startSession(workoutId)
        val original = database.dao().sessionSets(sessionId).single()
        repository.saveSet(original, reps = 10, load = 22.5, toggleCompletion = true, rir = 2, painLevel = 1)
        repository.finishSession(sessionId)

        val backup = repository.backupJson()
        assertEquals(4, JSONObject(backup).getInt("schemaVersion"))
        repository.deleteAllData()
        repository.importJson(backup)

        val restored = database.dao().sessionSets(sessionId).single()
        assertTrue(restored.completed)
        assertEquals(10, restored.reps)
        assertEquals(22.5, restored.loadKg, 0.0)
        assertEquals(8, restored.plannedReps)
        assertEquals(20.0, restored.plannedLoadKg ?: -1.0, 0.0)
        assertEquals(2, restored.rir)
        assertEquals(1, restored.painLevel)
    }

    @Test
    fun bodyPhotoMetadataRoundTripKeepsUploadDate() = runBlocking {
        repository.saveBodyPhoto("content://photos/evolution-1", "Frente")
        val original = database.dao().allBodyPhotos().single()

        val backup = repository.backupJson()
        repository.deleteAllData()
        repository.importJson(backup)

        val restored = database.dao().allBodyPhotos().single()
        assertEquals(original.id, restored.id)
        assertEquals(original.addedAt, restored.addedAt)
        assertEquals("content://photos/evolution-1", restored.imageUri)
        assertEquals("Frente", restored.notes)
    }

    @Test
    fun legacyBackupWithoutPlannedValuesImportsThemAsUnavailable() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Backup legado", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(workoutId, exerciseId, sets = 1, repMin = 8, load = 20.0)
        val sessionId = repository.startSession(workoutId)
        val original = database.dao().sessionSets(sessionId).single()
        repository.saveSet(original, reps = 10, load = 22.5, toggleCompletion = true)
        repository.finishSession(sessionId)

        val legacy = JSONObject(repository.backupJson()).put("schemaVersion", 2)
        val sessionSets = legacy.getJSONArray("sessionSets")
        for (index in 0 until sessionSets.length()) {
            sessionSets.getJSONObject(index).remove("plannedReps")
            sessionSets.getJSONObject(index).remove("plannedLoadKg")
        }
        repository.deleteAllData()
        repository.importJson(legacy.toString())

        val restored = database.dao().sessionSets(sessionId).single()
        assertTrue(restored.completed)
        assertEquals(10, restored.reps)
        assertEquals(22.5, restored.loadKg, 0.0)
        assertNull(restored.plannedReps)
        assertNull(restored.plannedLoadKg)
    }

    @Test
    fun sessionKeepsConfiguredExerciseOrder() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Ordem", "", emptySet())
        val exerciseIds = database.dao().allExercises().take(3).map { it.id }
        exerciseIds.forEach { repository.addExerciseToWorkout(workoutId, it, sets = 2) }

        val sessionId = repository.startSession(workoutId)
        val orderedIds = database.dao().sessionSets(sessionId).distinctBy { it.workoutExerciseId }.map { it.exerciseId }

        assertEquals(exerciseIds, orderedIds)
        assertEquals(listOf(0, 1, 2), database.dao().sessionSets(sessionId).distinctBy { it.workoutExerciseId }.map { it.exerciseOrder })
    }

    @Test
    fun nativeRenameSurvivesInitializationAndProgressResetPreservesPlans() = runBlocking {
        repository.initialize(useDemo = false)
        val exercise = database.dao().allExercises().first()
        repository.renameExercise(exercise.id, "Meu nome preferido")
        repository.initialize(useDemo = false)
        assertEquals("Meu nome preferido", database.dao().exercise(exercise.id)?.name)

        val workoutId = repository.createWorkout("Plano", "", emptySet())
        repository.addExerciseToWorkout(workoutId, exercise.id)
        val date = LocalDate.of(2026, 7, 21)
        repository.scheduleWorkout(date, workoutId)
        val planned = database.dao().allSchedule().single()
        repository.setScheduleStatus(planned, "Não realizado")
        repository.startSession(workoutId)

        repository.resetProgress()

        assertNull(database.dao().activeSession())
        assertTrue(database.dao().allSessions().isEmpty())
        assertEquals("Não realizado", database.dao().allSchedule().single().status)
    }

    @Test
    fun testSessionPersistsWhileActiveThenIsDiscardedAndNeverExported() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Simulação", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(workoutId, exerciseId, sets = 1, repMin = 8, load = 30.0)

        val sessionId = repository.startSession(workoutId, isTestMode = true)
        assertTrue(database.dao().session(sessionId)?.isTestMode == true)
        val set = database.dao().sessionSets(sessionId).single()
        repository.saveSet(set, reps = 12, load = 50.0, toggleCompletion = true)

        val backup = JSONObject(repository.backupJson())
        assertEquals(0, backup.getJSONArray("sessions").length())
        assertEquals(0, backup.getJSONArray("sessionSets").length())

        val summary = repository.finishSession(sessionId)
        assertTrue(summary.isTestMode)
        assertNull(database.dao().session(sessionId))
        assertTrue(database.dao().sessionSets(sessionId).isEmpty())
        assertEquals(0.0, repository.personalRecord(exerciseId), 0.0)
    }

    @Test
    fun deletingOneHistoricalSessionCascadesItsSetsOnly() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Histórico removível", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(workoutId, exerciseId, sets = 1)
        val sessionId = repository.startSession(workoutId)
        repository.finishSession(sessionId)
        repository.saveWeight(74.5)

        repository.deleteHistoricalSession(sessionId)

        assertNull(database.dao().session(sessionId))
        assertTrue(database.dao().sessionSets(sessionId).isEmpty())
        assertEquals(74.5, database.dao().allWeights().single().weightKg, 0.0)
    }

    @Test
    fun deletingHistoryForDatePreservesOtherDaysAndBodyWeight() = runBlocking {
        val zone = ZoneId.of("America/Sao_Paulo")
        val target = LocalDate.of(2026, 7, 20)
        val targetTime = target.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val nextDayTime = target.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val overnightStart = target.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val overnightFinish = target.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        database.dao().upsertSession(SessionEntity("day-a", "w", "A", targetTime - 3_600_000, targetTime, "Concluído"))
        database.dao().upsertSession(SessionEntity("day-b", "w", "B", targetTime - 1_800_000, targetTime, "Parcial"))
        database.dao().upsertSession(SessionEntity("overnight", "w", "Virada", overnightStart, overnightFinish, "Concluído"))
        database.dao().upsertSession(SessionEntity("next-day", "w", "C", nextDayTime - 1_800_000, nextDayTime, "Concluído"))
        repository.saveWeight(80.0)

        assertEquals(3, repository.deleteHistoryForDate(target, zone))

        assertEquals(listOf("next-day"), database.dao().allSessions().map { it.id })
        assertEquals(80.0, database.dao().allWeights().single().weightKg, 0.0)
    }

    @Test
    fun importingOneWorkoutAppendsWithoutReplacingLocalData() = runBlocking {
        repository.initialize(useDemo = false)
        val localId = repository.createWorkout("Local", "Preservar", emptySet())
        val sharedId = repository.createWorkout("Compartilhado", "Importar", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(sharedId, exerciseId, sets = 2, repMin = 6, repMax = 10)
        val payload = repository.exportWorkout(sharedId)

        val importedId = repository.importWorkout(payload)
        val workouts = database.dao().allWorkouts()

        assertEquals(3, workouts.size)
        assertTrue(workouts.any { it.id == localId })
        assertTrue(importedId != sharedId)
        assertEquals(importedId, workouts.last().id)
        assertEquals(listOf(0), database.dao().workoutExercises(importedId).map { it.orderIndex })
    }

    @Test
    fun finishingRecurringWorkoutCreatesDatedCalendarEntryFromSessionStart() = runBlocking {
        repository.initialize(useDemo = false)
        val workoutId = repository.createWorkout("Calendário", "", emptySet())
        val exerciseId = database.dao().allExercises().first().id
        repository.addExerciseToWorkout(workoutId, exerciseId, sets = 1)
        val sessionId = repository.startSession(workoutId)
        val startDate = LocalDate.of(2026, 7, 19)
        val startedAt = startDate.atTime(23, 55).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val session = requireNotNull(database.dao().session(sessionId))
        database.dao().upsertSession(session.copy(startedAt = startedAt))

        repository.finishSession(sessionId)

        val entry = database.dao().allSchedule().single()
        assertEquals(startDate.toString(), entry.date)
        assertEquals(workoutId, entry.workoutId)
        assertEquals("Parcial", entry.status)
    }
}
