package com.liftly.app.data

import android.content.Context
import androidx.room.withTransaction
import com.liftly.app.domain.EffectiveScheduleResolver
import com.liftly.app.domain.ParsedSetType
import com.liftly.app.domain.ParsedWorkout
import com.liftly.app.domain.TrainingMomentum
import com.liftly.app.domain.TrainingMomentumCalculator
import com.liftly.app.domain.WorkoutRewardMetrics
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.text.Normalizer
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class LiftlyRepository(
    private val database: LiftlyDatabase,
    private val context: Context
) {
    private val dao = database.dao()
    private val rewardStore = LiftlyRewardsStore(database)

    val exercises: Flow<List<ExerciseEntity>> = dao.observeExercises()
    val workouts: Flow<List<WorkoutEntity>> = dao.observeWorkouts()
    val workoutExercises: Flow<List<WorkoutExerciseEntity>> = dao.observeWorkoutExercises()
    val schedule: Flow<List<ScheduleEntity>> = dao.observeSchedule()
    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val sessionSets: Flow<List<SessionSetEntity>> = dao.observeSessionSets()
    val weights: Flow<List<BodyWeightEntryEntity>> = dao.observeWeights()
    val bodyPhotos: Flow<List<BodyPhotoEntity>> = dao.observeBodyPhotos()
    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val rewards: Flow<RewardSnapshot> = rewardStore.snapshot

    suspend fun initialize(useDemo: Boolean) {
        val existing = dao.allExercises().associateBy { it.id }
        dao.upsertExercises(ExerciseCatalog.exercises.map { seed ->
            seed.copy(
                name = existing[seed.id]?.name ?: seed.name,
                isFavorite = existing[seed.id]?.isFavorite ?: false
            )
        })
        if (useDemo && dao.allWorkouts().isEmpty()) seedDemoWorkout()
        database.withTransaction {
            normalizeAllWorkoutExerciseOrders()
            rewardStore.initializeInTransaction(System.currentTimeMillis())
        }
    }

    suspend fun saveExercise(exercise: ExerciseEntity) = dao.upsertExercise(exercise)

    suspend fun renameExercise(id: String, newName: String) = database.withTransaction {
        renameExerciseInTransaction(id, newName)
    }

    private suspend fun renameExerciseInTransaction(id: String, newName: String) {
        val exercise = requireNotNull(dao.exercise(id)) { "Exercício não encontrado." }
        require(!exercise.archived) { "Não é possível renomear um exercício arquivado." }
        val name = newName.trim().replace(Regex("\\s+"), " ")
        require(name.length in 1..80) { "Informe um nome entre 1 e 80 caracteres." }
        val normalized = normalizeExerciseName(name)
        require(dao.allExercises().none { it.id != id && normalizeExerciseName(it.name) == normalized }) {
            "Já existe um exercício com esse nome."
        }
        check(dao.renameExercise(id, name) == 1) { "Não foi possível renomear o exercício." }
        dao.renameExerciseInActiveSessions(id, name)
    }

    suspend fun toggleFavorite(exercise: ExerciseEntity) {
        dao.upsertExercise(exercise.copy(isFavorite = !exercise.isFavorite))
    }

    suspend fun deleteCustomExercise(exercise: ExerciseEntity) {
        if (!exercise.isCustom) return
        if (dao.deleteUnusedCustomExercise(exercise.id) == 0) dao.archiveCustomExercise(exercise.id)
    }

    suspend fun saveWorkout(workout: WorkoutEntity) = dao.upsertWorkout(workout)

    suspend fun createWorkout(name: String, description: String, weekDays: Set<DayOfWeek>): String = database.withTransaction {
        val id = UUID.randomUUID().toString()
        dao.upsertWorkout(
            WorkoutEntity(
                id = id,
                name = name.trim(),
                description = description.trim(),
                weekDays = weekDays.joinToString(",") { it.value.toString() },
                createdAt = nextWorkoutCreatedAt(),
            )
        )
        id
    }

    suspend fun duplicateWorkout(workout: WorkoutEntity): String = database.withTransaction {
        val newId = UUID.randomUUID().toString()
        dao.upsertWorkout(workout.copy(id = newId, name = "${workout.name} (cópia)", createdAt = nextWorkoutCreatedAt()))
        val sourceItems = normalizedWorkoutExercises(workout.id)
        dao.upsertWorkoutExercises(
            sourceItems.map {
                it.copy(id = UUID.randomUUID().toString(), workoutId = newId)
            }
        )
        newId
    }

    suspend fun exportWorkout(workoutId: String): String = database.withTransaction {
        val workout = requireNotNull(dao.workout(workoutId)) { "Treino não encontrado." }
        require(!workout.archived) { "Não é possível compartilhar um treino arquivado." }
        val items = normalizedWorkoutExercises(workoutId)
        require(items.isNotEmpty()) { "Adicione exercícios antes de compartilhar." }
        WorkoutShareCodec.encode(
            WorkoutShareCodec.fromEntities(
                workout = workout,
                items = items,
                exercises = dao.allExercises().associateBy(ExerciseEntity::id),
            )
        )
    }

    suspend fun importWorkout(payload: String): String = database.withTransaction {
        val shared = WorkoutShareCodec.decode(payload)
        val localExercises = dao.allExercises().associateBy(ExerciseEntity::id)
        val exerciseIdMap = mutableMapOf<String, String>()
        shared.exercises.forEach { source ->
            val localNative = localExercises[source.referenceId]
                ?.takeIf { !source.isCustom && !it.isCustom && !it.archived }
            if (localNative != null) {
                exerciseIdMap[source.referenceId] = localNative.id
            } else {
                val newId = "custom-${UUID.randomUUID()}"
                dao.upsertExercise(
                    ExerciseEntity(
                        id = newId,
                        name = source.name.trim(),
                        muscleGroup = source.muscleGroup,
                        secondaryMuscles = source.secondaryMuscles,
                        equipment = source.equipment,
                        difficulty = source.difficulty,
                        movementType = source.movementType,
                        category = source.category,
                        instructions = source.instructions,
                        cautions = source.cautions,
                        trackingUnit = source.trackingUnit,
                        isCustom = true,
                        isFavorite = false,
                        imageUri = null,
                        archived = false,
                    )
                )
                exerciseIdMap[source.referenceId] = newId
            }
        }
        val newWorkoutId = UUID.randomUUID().toString()
        dao.upsertWorkout(
            WorkoutEntity(
                id = newWorkoutId,
                name = shared.workout.name.trim(),
                description = shared.workout.description.trim(),
                color = shared.workout.color,
                icon = shared.workout.icon,
                weekDays = shared.workout.weekDays,
                archived = false,
                createdAt = nextWorkoutCreatedAt(),
            )
        )
        dao.upsertWorkoutExercises(
            shared.items.sortedBy(SharedWorkoutItem::orderIndex).mapIndexed { index, source ->
                WorkoutExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    workoutId = newWorkoutId,
                    exerciseId = requireNotNull(exerciseIdMap[source.exerciseReferenceId]) {
                        "Exercício compartilhado não encontrado."
                    },
                    orderIndex = index,
                    sets = source.sets,
                    repMin = source.repMin,
                    repMax = source.repMax,
                    targetLoadKg = source.targetLoadKg,
                    restSeconds = source.restSeconds,
                    notes = source.notes,
                    setType = source.setType,
                    trackingMode = source.trackingMode,
                )
            }
        )
        newWorkoutId
    }

    /**
     * Persists the reviewed result of a pasted-text import.
     *
     * The parser never writes to Room directly. This transaction guarantees that either every
     * reviewed workout is appended in its original order, or none of them is created.
     */
    suspend fun importTextWorkouts(workouts: List<ParsedWorkout>): List<String> = database.withTransaction {
        require(workouts.isNotEmpty()) { "Nenhum treino foi encontrado no texto." }
        require(workouts.size <= 20) { "Importe no máximo 20 treinos por vez." }
        require(workouts.sumOf { it.exercises.size } <= 300) {
            "O texto contém exercícios demais para uma única importação."
        }

        val availableExercises = dao.allExercises().filterNot(ExerciseEntity::archived).toMutableList()
        val exerciseByNormalizedName = availableExercises
            .associateByTo(mutableMapOf()) { normalizeExerciseName(it.name).trim() }
        val importedWorkoutIds = ArrayList<String>(workouts.size)
        var createdAt = nextWorkoutCreatedAt()

        workouts.forEach { parsedWorkout ->
            val workoutName = parsedWorkout.name.trim().replace(Regex("\\s+"), " ")
            require(workoutName.length in 1..80) { "Revise o nome de um dos treinos." }
            require(parsedWorkout.description.length <= 600) { "A descrição de $workoutName está muito longa." }
            require(parsedWorkout.exercises.isNotEmpty()) { "$workoutName não possui exercícios." }
            require(parsedWorkout.exercises.size <= 100) { "$workoutName possui exercícios demais." }

            val workoutId = UUID.randomUUID().toString()
            dao.upsertWorkout(
                WorkoutEntity(
                    id = workoutId,
                    name = workoutName,
                    description = parsedWorkout.description.trim(),
                    weekDays = parsedWorkout.weekDays
                        .sortedBy { it.value }
                        .joinToString(",") { it.value.toString() },
                    createdAt = createdAt,
                )
            )

            val items = parsedWorkout.exercises.mapIndexed { index, parsedExercise ->
                val exerciseName = parsedExercise.name.trim().replace(Regex("\\s+"), " ")
                require(exerciseName.length in 1..80) { "Revise o exercício ${index + 1} de $workoutName." }
                val normalizedName = normalizeExerciseName(exerciseName).trim()
                val exercise = exerciseByNormalizedName[normalizedName]
                    ?: findUniqueCompatibleExercise(exerciseName, availableExercises)
                    ?: ExerciseEntity(
                        id = "custom-${UUID.randomUUID()}",
                        name = exerciseName,
                        muscleGroup = "Outro",
                        equipment = "Não informado",
                        difficulty = "Não informado",
                        movementType = "Não informado",
                        category = "Musculação",
                        instructions = "",
                        cautions = "",
                        trackingUnit = "kg",
                        isCustom = true,
                    ).also { created ->
                        dao.upsertExercise(created)
                        availableExercises += created
                        exerciseByNormalizedName[normalizedName] = created
                    }

                val repMin = (parsedExercise.repMin ?: 8).coerceIn(1, 10_000)
                val repMax = (parsedExercise.repMax ?: parsedExercise.repMin ?: 12)
                    .coerceIn(repMin, 10_000)
                val notes = buildList {
                    parsedExercise.notes.trim().takeIf(String::isNotEmpty)?.let(::add)
                    parsedExercise.rir?.let { rir ->
                        if (none { it.contains("RIR", ignoreCase = true) }) add("RIR alvo: $rir")
                    }
                }.joinToString(" • ").take(1_000)

                WorkoutExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    exerciseId = exercise.id,
                    orderIndex = index,
                    sets = (parsedExercise.sets ?: 3).coerceIn(1, 20),
                    repMin = repMin,
                    repMax = repMax,
                    targetLoadKg = (parsedExercise.loadKg ?: 0.0).coerceIn(0.0, 2_000.0),
                    restSeconds = (parsedExercise.restSeconds ?: 60).coerceIn(0, 3_600),
                    notes = notes,
                    setType = when (parsedExercise.setType) {
                        ParsedSetType.WARM_UP -> "Aquecimento"
                        ParsedSetType.DROP_SET -> "Dropset"
                        ParsedSetType.SUPER_SET -> "Supersérie"
                        ParsedSetType.FAILURE -> "Falha"
                        ParsedSetType.NORMAL, ParsedSetType.UNKNOWN, null -> "Normal"
                    },
                    trackingMode = when {
                        exercise.trackingUnit.contains("tempo", ignoreCase = true) -> "Tempo"
                        exercise.trackingUnit.contains("dist", ignoreCase = true) -> "Distância"
                        else -> "Repetições"
                    },
                )
            }
            dao.upsertWorkoutExercises(items)
            importedWorkoutIds += workoutId
            if (createdAt < Long.MAX_VALUE) createdAt += 1L
        }
        importedWorkoutIds
    }

    suspend fun archiveWorkout(workoutId: String) = dao.archiveWorkout(workoutId)
    suspend fun deleteWorkout(workoutId: String) = database.withTransaction {
        dao.deleteScheduleForWorkout(workoutId)
        dao.deleteWorkout(workoutId)
    }

    suspend fun addExerciseToWorkout(
        workoutId: String,
        exerciseId: String,
        sets: Int = 3,
        repMin: Int = 8,
        repMax: Int = 12,
        load: Double = 0.0,
        restSeconds: Int = 60,
        setType: String = "Normal",
        notes: String = "",
        exerciseName: String? = null
    ) = database.withTransaction {
        val orderedItems = normalizedWorkoutExercises(workoutId)
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercício não encontrado." }
        if (exerciseName != null && exerciseName.trim() != exercise.name) renameExerciseInTransaction(exerciseId, exerciseName)
        val trackingMode = when {
            exercise.trackingUnit.contains("tempo", ignoreCase = true) -> "Tempo"
            exercise.trackingUnit.contains("dist", ignoreCase = true) -> "Distância"
            else -> "Repetições"
        }
        val newItem = WorkoutExerciseEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = orderedItems.size,
                sets = sets.coerceIn(1, 20),
                repMin = repMin.coerceAtLeast(1),
                repMax = repMax.coerceAtLeast(repMin),
                targetLoadKg = load.coerceAtLeast(0.0),
                restSeconds = restSeconds.coerceIn(0, 900),
                setType = setType,
                notes = notes.trim(),
                trackingMode = trackingMode
            )
        dao.upsertWorkoutExercises(WorkoutExerciseOrder.append(orderedItems, newItem))
    }

    suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity, exerciseName: String? = null) = database.withTransaction {
        val existing = dao.workoutExercise(item.id)
        val workoutId = existing?.workoutId ?: item.workoutId
        val exercise = requireNotNull(dao.exercise(item.exerciseId)) { "Exercício não encontrado." }
        val orderedItems = normalizedWorkoutExercises(workoutId)
        val orderIndex = orderedItems.firstOrNull { it.id == item.id }?.orderIndex ?: orderedItems.size
        if (exerciseName != null && exerciseName.trim() != exercise.name) {
            renameExerciseInTransaction(item.exerciseId, exerciseName)
        }
        dao.upsertWorkoutExercise(item.copy(workoutId = workoutId, orderIndex = orderIndex))
        normalizedWorkoutExercises(workoutId)
    }
    suspend fun removeWorkoutExercise(id: String) = database.withTransaction {
        val existing = dao.workoutExercise(id)
        dao.deleteWorkoutExercise(id)
        existing?.let { normalizedWorkoutExercises(it.workoutId) }
    }

    suspend fun moveWorkoutExercise(workoutId: String, id: String, direction: Int) = database.withTransaction {
        persistWorkoutExerciseOrder(WorkoutExerciseOrder.move(dao.workoutExercises(workoutId), id, direction))
    }

    suspend fun moveWorkoutExerciseBefore(workoutId: String, id: String, beforeId: String) = database.withTransaction {
        persistWorkoutExerciseOrder(WorkoutExerciseOrder.moveBefore(dao.workoutExercises(workoutId), id, beforeId))
    }

    suspend fun scheduleWorkout(date: LocalDate, workoutId: String) = database.withTransaction {
        requireNotNull(dao.workout(workoutId)) { "Treino não encontrado." }
        dao.deleteRestForDate(date.toString())
        dao.upsertSchedule(ScheduleEntity("schedule-$date-$workoutId", date.toString(), workoutId))
    }

    suspend fun setRestDay(date: LocalDate) = database.withTransaction {
        dao.deleteScheduleForDate(date.toString())
        dao.upsertSchedule(ScheduleEntity("rest-$date", date.toString(), "", status = "Descanso", isRestDay = true))
    }

    suspend fun removeSchedule(id: String) = dao.deleteSchedule(id)
    suspend fun setScheduleStatus(item: ScheduleEntity, status: String) {
        require(status in setOf("Planejado", "Concluído", "Parcial", "Não realizado")) { "Status inválido." }
        val persistedId = if (EffectiveScheduleResolver.isRecurringPlaceholder(item)) {
            "schedule-${item.date}-${item.workoutId}"
        } else item.id
        dao.upsertSchedule(item.copy(id = persistedId, status = status))
    }

    suspend fun copyWeek(sourceDate: LocalDate, targetDate: LocalDate) = database.withTransaction {
        val sourceMonday = sourceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetMonday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val allSchedule = dao.allSchedule()
        val allWorkouts = dao.allWorkouts()
        val sourceItems = (0L..6L).flatMap { offset ->
            EffectiveScheduleResolver.forDate(sourceMonday.plusDays(offset), allWorkouts, allSchedule)
        }
        (0L..6L).forEach { offset -> dao.deleteScheduleForDate(targetMonday.plusDays(offset).toString()) }
        dao.upsertSchedules(sourceItems.map { item ->
            val offset = java.time.temporal.ChronoUnit.DAYS.between(sourceMonday, LocalDate.parse(item.date))
            val newDate = targetMonday.plusDays(offset)
            item.copy(
                id = if (item.isRestDay) "rest-$newDate" else "schedule-$newDate-${item.workoutId}",
                date = newDate.toString(),
                status = if (item.isRestDay) "Descanso" else "Planejado"
            )
        })
    }

    suspend fun startSession(workoutId: String, isTestMode: Boolean = false): String = database.withTransaction {
        dao.activeSession()?.let { return@withTransaction it.id }
        val workout = requireNotNull(dao.workout(workoutId))
        val items = normalizedWorkoutExercises(workoutId)
        require(items.isNotEmpty()) { "Adicione exercícios antes de iniciar." }
        val exerciseMap = dao.allExercises().associateBy { it.id }
        val sessionId = UUID.randomUUID().toString()
        dao.upsertSession(
            SessionEntity(
                id = sessionId,
                workoutId = workout.id,
                workoutName = workout.name,
                startedAt = System.currentTimeMillis(),
                isTestMode = isTestMode
            )
        )
        dao.upsertSessionSets(items.flatMap { item ->
            val exercise = exerciseMap[item.exerciseId]
            (1..item.sets).map { number ->
                SessionSetEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    workoutExerciseId = item.id,
                    exerciseId = item.exerciseId,
                    exerciseName = exercise?.name ?: "Exercício",
                    setNumber = number,
                    reps = item.repMin,
                    loadKg = item.targetLoadKg,
                    durationSeconds = if (item.trackingMode == "Tempo") item.repMin else 0,
                    distanceMeters = if (item.trackingMode == "Distância") item.repMin.toDouble() else 0.0,
                    trackingMode = item.trackingMode,
                    exerciseOrder = item.orderIndex,
                    plannedReps = item.repMin,
                    plannedLoadKg = item.targetLoadKg
                )
            }
        })
        sessionId
    }

    suspend fun saveSet(
        item: SessionSetEntity,
        reps: Int,
        load: Double,
        toggleCompletion: Boolean = false,
        rir: Int? = item.rir,
        painLevel: Int = item.painLevel
    ) = database.withTransaction {
        // Merge with the latest row so a nearby text edit cannot overwrite the
        // completion state written by a checkbox tap from an older UI snapshot.
        val current = dao.sessionSet(item.id) ?: item
        val value = reps.coerceAtLeast(0)
        val completed = if (toggleCompletion) !current.completed else current.completed
        dao.upsertSessionSet(current.copy(
            reps = value,
            loadKg = load.coerceAtLeast(0.0),
            completed = completed,
            completedAt = when {
                !toggleCompletion -> current.completedAt
                completed -> current.completedAt ?: System.currentTimeMillis()
                else -> null
            },
            durationSeconds = if (current.trackingMode == "Tempo") value else current.durationSeconds,
            distanceMeters = if (current.trackingMode == "Distância") value.toDouble() else current.distanceMeters,
            rir = rir?.coerceIn(0, 10),
            painLevel = painLevel.coerceIn(0, 10)
        ))
    }

    suspend fun substituteSessionExercise(
        sessionId: String,
        workoutExerciseId: String,
        replacementExerciseId: String,
    ): Int = database.withTransaction {
        val session = requireNotNull(dao.session(sessionId)) { "Sessão não encontrada." }
        require(session.status == "Em andamento") { "Este treino já foi encerrado." }
        val replacement = requireNotNull(dao.exercise(replacementExerciseId)) {
            "Exercício substituto não encontrado."
        }
        require(!replacement.archived) { "Este exercício não está mais disponível." }
        val targetSets = dao.sessionSets(sessionId).filter { it.workoutExerciseId == workoutExerciseId }
        require(targetSets.isNotEmpty()) { "Exercício da sessão não encontrado." }
        val pendingSets = targetSets.filterNot { it.completed }
        require(pendingSets.isNotEmpty()) { "Todas as séries deste exercício já foram concluídas." }
        val plannedItem = dao.workoutExercise(workoutExerciseId)
        val returningToOriginal = plannedItem?.exerciseId == replacement.id
        val replacementTrackingMode = when {
            replacement.trackingUnit.contains("tempo", ignoreCase = true) -> "Tempo"
            replacement.trackingUnit.contains("dist", ignoreCase = true) -> "Distância"
            else -> "Repetições"
        }
        val updated = pendingSets.map { current ->
            val sameTrackingMode = current.trackingMode == replacementTrackingMode
            current.copy(
                exerciseId = replacement.id,
                exerciseName = replacement.name,
                reps = when {
                    returningToOriginal -> plannedItem?.repMin ?: current.reps
                    sameTrackingMode -> current.reps
                    else -> 0
                },
                loadKg = if (returningToOriginal) plannedItem?.targetLoadKg ?: 0.0 else 0.0,
                durationSeconds = if (returningToOriginal && replacementTrackingMode == "Tempo") plannedItem?.repMin ?: 0 else 0,
                distanceMeters = if (returningToOriginal && replacementTrackingMode == "Distância") (plannedItem?.repMin ?: 0).toDouble() else 0.0,
                trackingMode = replacementTrackingMode,
                plannedReps = when {
                    returningToOriginal -> plannedItem?.repMin ?: current.plannedReps
                    sameTrackingMode -> current.plannedReps
                    else -> 0
                },
                plannedLoadKg = if (returningToOriginal) plannedItem?.targetLoadKg else null,
                rir = null,
                painLevel = 0,
            )
        }
        dao.upsertSessionSets(updated)
        updated.size
    }

    suspend fun finishSession(sessionId: String): SessionSummary = database.withTransaction {
        val session = requireNotNull(dao.session(sessionId)) { "Sessão não encontrada." }
        val sets = dao.sessionSets(sessionId)
        val done = sets.count { it.completed }
        val status = if (done == sets.size) "Concluído" else "Parcial"
        val now = System.currentTimeMillis()
        var summary = SessionSummary(
            session.id,
            session.workoutName,
            session.startedAt,
            now,
            done,
            sets.size,
            sets.filter { it.completed }.sumOf { it.loadKg * it.reps },
            isTestMode = session.isTestMode
        )
        if (session.isTestMode) {
            // The foreign key removes every test set in the same transaction.
            check(dao.deleteSession(sessionId) == 1) { "Não foi possível descartar a sessão de teste." }
        } else {
            dao.upsertSession(session.copy(finishedAt = now, status = status))
            val sessionDate = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val date = sessionDate.toString()
            dao.deleteRestForDate(date)
            val existingSchedule = dao.allSchedule().firstOrNull {
                it.date == date && it.workoutId == session.workoutId
            }
            dao.upsertSchedule(
                existingSchedule?.copy(status = status)
                    ?: ScheduleEntity(
                        id = "schedule-$date-${session.workoutId}",
                        date = date,
                        workoutId = session.workoutId,
                        status = status,
                    )
            )
            val completedSets = sets.filter(SessionSetEntity::completed)
            val personalRecords = completedSets
                .groupBy(SessionSetEntity::exerciseId)
                .count { (exerciseId, exerciseSets) ->
                    val previous = dao.personalRecordExcludingSession(exerciseId, sessionId)
                    previous != null && exerciseSets.maxOf(SessionSetEntity::loadKg) > previous + 0.0001
                }
            val grant = rewardStore.awardWorkoutCompletionInTransaction(
                sessionId = sessionId,
                metrics = WorkoutRewardMetrics(
                    completedSets = done,
                    totalSets = sets.size,
                    rirRecordedSets = completedSets.count { it.rir != null },
                    personalRecords = personalRecords,
                ),
                occurredAt = now,
            )
            summary = summary.copy(
                rewardXp = grant.xp,
                rewardCoins = grant.coins,
                completedRewardMissions = grant.completedMissionIds,
            )
        }
        summary
    }

    /** Removes one completed history entry and its sets; active sessions are protected. */
    suspend fun deleteHistoricalSession(sessionId: String) = database.withTransaction {
        require(dao.deleteHistoricalSession(sessionId) == 1) {
            "Treino concluído não encontrado ou ainda em andamento."
        }
    }

    /** Removes completed sessions started on [date], matching the history/progress grouping. */
    suspend fun deleteHistoryForDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Int =
        database.withTransaction {
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            dao.deleteHistoricalSessionsBetween(start, end)
        }

    suspend fun lastLoad(exerciseId: String): Double = dao.lastLoad(exerciseId) ?: 0.0
    suspend fun personalRecord(exerciseId: String): Double = dao.personalRecord(exerciseId) ?: 0.0

    suspend fun ensureRewardsCurrent() = rewardStore.initialize()
    suspend fun purchaseRewardItem(itemId: String) = rewardStore.purchase(itemId)
    suspend fun equipRewardItem(itemId: String) = rewardStore.equip(itemId)
    suspend fun unequipRewardSlot(slot: String) = rewardStore.unequip(slot)
    suspend fun adminGrantRewards(xp: Long, coins: Long) = rewardStore.adminGrant(xp, coins)
    suspend fun adminUnlockAllRewards(): Int = rewardStore.adminUnlockAll()
    suspend fun adminSimulateWorkout(metrics: WorkoutRewardMetrics): RewardGrant =
        rewardStore.adminSimulateWorkout(metrics)
    suspend fun adminCompleteRewardMission(missionId: String) = rewardStore.adminCompleteMission(missionId)
    suspend fun adminResetCurrentRewardMissions(): Int = rewardStore.adminResetCurrentMissions()
    suspend fun adminResetRewardEconomy() = rewardStore.adminResetEconomy()

    suspend fun saveWeight(weightKg: Double, notes: String = "") = database.withTransaction {
        require(weightKg in 20.0..500.0) { "Informe um peso entre 20 e 500 kg." }
        dao.upsertWeight(BodyWeightEntryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), weightKg, notes))
        val profile = dao.profile() ?: UserProfileEntity()
        dao.upsertProfile(profile.copy(currentWeightKg = weightKg))
    }

    suspend fun updateWeight(id: String, weightKg: Double, notes: String = "") = database.withTransaction {
        require(weightKg in 20.0..500.0) { "Informe um peso entre 20 e 500 kg." }
        val entry = requireNotNull(dao.allWeights().firstOrNull { it.id == id }) {
            "Registro de peso não encontrado."
        }
        dao.upsertWeight(entry.copy(weightKg = weightKg, notes = notes))
        syncProfileWeightToLatestEntry()
    }

    suspend fun deleteWeight(id: String) = database.withTransaction {
        require(dao.allWeights().any { it.id == id }) { "Registro de peso não encontrado." }
        dao.deleteWeight(id)
        syncProfileWeightToLatestEntry()
    }

    private suspend fun syncProfileWeightToLatestEntry() {
        val latestWeight = dao.allWeights()
            .maxWithOrNull(compareBy<BodyWeightEntryEntity> { it.measuredAt }.thenBy { it.id })
            ?.weightKg
        val profile = dao.profile() ?: UserProfileEntity()
        dao.upsertProfile(profile.copy(currentWeightKg = latestWeight))
    }
    suspend fun saveBodyPhoto(imageUri: String, notes: String = "") {
        require(imageUri.startsWith("content://")) { "Selecione uma foto válida pelo Android." }
        dao.upsertBodyPhoto(
            BodyPhotoEntity(
                id = UUID.randomUUID().toString(),
                imageUri = imageUri,
                addedAt = System.currentTimeMillis(),
                notes = notes.trim().take(240)
            )
        )
    }
    suspend fun allBodyPhotos(): List<BodyPhotoEntity> = dao.allBodyPhotos()
    suspend fun deleteBodyPhoto(id: String) {
        require(dao.deleteBodyPhoto(id) == 1) { "Foto não encontrada." }
    }
    suspend fun saveProfile(profile: UserProfileEntity) = dao.upsertProfile(profile)

    suspend fun resetProgress() = dao.clearTrainingProgress()

    suspend fun trainingMomentum(weeklyGoal: Int): TrainingMomentum {
        val completedTimes = dao.allSessions()
            .asSequence()
            .filter { it.finishedAt != null && !it.isTestMode }
            .map { it.startedAt }
            .toList()
        return TrainingMomentumCalculator.calculate(completedTimes, weeklyGoal)
    }

    suspend fun backupJson(): String = database.withTransaction {
        normalizeAllWorkoutExerciseOrders()
        val root = JSONObject()
            .put("schemaVersion", 5)
            .put("exportedAt", System.currentTimeMillis())
            .put("app", "Liftly")
        val catalogNames = ExerciseCatalog.exercises.associate { it.id to it.name }
        root.put("exercises", JSONArray().also { array ->
            dao.allExercises()
                .filter { it.isCustom || it.isFavorite || catalogNames[it.id] != it.name }
                .forEach { array.put(it.toJson()) }
        })
        root.put("workouts", JSONArray().also { array -> dao.allWorkouts().forEach { array.put(it.toJson()) } })
        root.put("workoutExercises", JSONArray().also { array -> dao.allWorkoutExercises().forEach { array.put(it.toJson()) } })
        root.put("schedule", JSONArray().also { array -> dao.allSchedule().forEach { array.put(it.toJson()) } })
        // Test sessions are intentionally device-local and never exported.
        root.put("sessions", JSONArray().also { array -> dao.allExportableSessions().forEach { array.put(it.toJson()) } })
        root.put("sessionSets", JSONArray().also { array -> dao.allExportableSessionSets().forEach { array.put(it.toJson()) } })
        root.put("weights", JSONArray().also { array -> dao.allWeights().forEach { array.put(it.toJson()) } })
        // O arquivo binário não é duplicado: somente a URI persistente e sua data são exportadas.
        root.put("bodyPhotos", JSONArray().also { array -> dao.allBodyPhotos().forEach { array.put(it.toJson()) } })
        root.put("profile", dao.profile()?.toJson() ?: JSONObject.NULL)
        root.put("rewardWallet", dao.rewardWallet()?.toJson() ?: JSONObject.NULL)
        root.put("rewardCatalog", JSONArray().also { array -> dao.allRewardCatalog().forEach { array.put(it.toJson()) } })
        root.put("rewardInventory", JSONArray().also { array -> dao.allRewardInventory().forEach { array.put(it.toJson()) } })
        root.put("rewardMissions", JSONArray().also { array -> dao.allRewardMissions().forEach { array.put(it.toJson()) } })
        root.put("rewardLedger", JSONArray().also { array -> dao.allRewardLedger().forEach { array.put(it.toJson()) } })
        root.toString(2)
    }

    suspend fun importJson(json: String) = database.withTransaction {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") in 1..5) { "Versão de backup não compatível." }
        validateBackup(root)
        dao.clearUserData()
        dao.upsertExercises(ExerciseCatalog.exercises)
        val builtIns = ExerciseCatalog.exercises.associateBy { it.id }
        root.optJSONArray("exercises")?.forEachObject {
            val imported = it.toExercise()
            if (imported.isCustom) {
                dao.upsertExercise(imported)
            } else {
                val seed = requireNotNull(builtIns[imported.id]) { "Exercício nativo desconhecido no backup." }
                dao.upsertExercise(seed.copy(name = imported.name, isFavorite = imported.isFavorite))
            }
        }
        root.optJSONArray("workouts")?.forEachObject { dao.upsertWorkout(it.toWorkout()) }
        root.optJSONArray("workoutExercises")?.forEachObject { dao.upsertWorkoutExercise(it.toWorkoutExercise()) }
        normalizeAllWorkoutExerciseOrders()
        root.optJSONArray("schedule")?.forEachObject { dao.upsertSchedule(it.toSchedule()) }
        root.optJSONArray("sessions")?.forEachObject { dao.upsertSession(it.toSession()) }
        val fallbackOrders = dao.allWorkoutExercises().associate { it.id to it.orderIndex }
        root.optJSONArray("sessionSets")?.forEachObject {
            dao.upsertSessionSet(it.toSessionSet(fallbackOrders[it.optString("workoutExerciseId")] ?: 0))
        }
        root.optJSONArray("weights")?.forEachObject { dao.upsertWeight(it.toWeight()) }
        root.optJSONArray("bodyPhotos")?.forEachObject { dao.upsertBodyPhoto(it.toBodyPhoto()) }
        root.optJSONObject("profile")?.let { dao.upsertProfile(it.toProfile()) }
        root.optJSONObject("rewardWallet")?.let { dao.upsertRewardWallet(it.toRewardWallet()) }
        dao.upsertRewardCatalog(LiftlyRewardCatalog.items)
        root.optJSONArray("rewardCatalog")?.forEachObject {
            dao.upsertRewardCatalog(listOf(it.toRewardCatalogItem()))
        }
        root.optJSONArray("rewardMissions")?.forEachObject { dao.upsertRewardMission(it.toRewardMission()) }
        root.optJSONArray("rewardLedger")?.forEachObject { dao.insertRewardLedger(it.toRewardLedger()) }
        root.optJSONArray("rewardInventory")?.forEachObject { dao.insertRewardInventory(it.toRewardInventory()) }
        rewardStore.initializeInTransaction(System.currentTimeMillis())
    }

    suspend fun deleteAllData() = database.withTransaction {
        dao.clearUserData()
        dao.upsertExercises(ExerciseCatalog.exercises)
        rewardStore.initializeInTransaction(System.currentTimeMillis())
    }

    private suspend fun seedDemoWorkout() = database.withTransaction {
        val id = "demo-full-body"
        dao.upsertWorkout(WorkoutEntity(id, "Full body essencial", "Treino de demonstração • edite como quiser", weekDays = "1,3,5"))
        val picks = listOf("builtin.agachamento_livre", "builtin.supino_reto_barra", "builtin.remada_baixa_cabo", "builtin.terra_romeno", "builtin.prancha_frontal")
        val exercises = dao.allExercises()
        val resolved = picks.mapNotNull { key -> exercises.firstOrNull { it.id == key } }.ifEmpty { exercises.take(5) }
        dao.upsertWorkoutExercises(resolved.mapIndexed { index, exercise ->
            WorkoutExerciseEntity(
                id = "demo-item-$index",
                workoutId = id,
                exerciseId = exercise.id,
                orderIndex = index,
                sets = if (index == resolved.lastIndex) 3 else 4,
                repMin = if (exercise.trackingUnit == "tempo") 30 else 8,
                repMax = if (exercise.trackingUnit == "tempo") 45 else 12,
                targetLoadKg = 0.0,
                restSeconds = 75
            )
        })
    }

    private suspend fun normalizeAllWorkoutExerciseOrders() {
        dao.allWorkoutExercises()
            .groupBy(WorkoutExerciseEntity::workoutId)
            .values
            .forEach { persistWorkoutExerciseOrder(WorkoutExerciseOrder.normalize(it)) }
    }

    private suspend fun normalizedWorkoutExercises(workoutId: String): List<WorkoutExerciseEntity> {
        val normalized = WorkoutExerciseOrder.normalize(dao.workoutExercises(workoutId))
        persistWorkoutExerciseOrder(normalized)
        return normalized
    }

    private suspend fun persistWorkoutExerciseOrder(items: List<WorkoutExerciseEntity>) {
        items.forEachIndexed { index, item -> dao.updateOrder(item.id, index) }
    }

    private suspend fun nextWorkoutCreatedAt(): Long {
        val latest = dao.allWorkouts().maxOfOrNull(WorkoutEntity::createdAt) ?: Long.MIN_VALUE
        val afterLatest = if (latest == Long.MAX_VALUE) latest else latest + 1L
        return maxOf(System.currentTimeMillis(), afterLatest)
    }
}

private fun validateBackup(root: JSONObject) {
    val workoutIds = buildSet {
        root.optJSONArray("workouts")?.forEachObject {
            require(it.optString("id").isNotBlank() && it.optString("name").isNotBlank()) { "Treino inválido no backup." }
            add(it.getString("id"))
        }
    }
    val exerciseIds = ExerciseCatalog.exercises.mapTo(mutableSetOf()) { it.id }
    root.optJSONArray("exercises")?.forEachObject {
        val exercise = it.toExercise()
        require(exercise.id.isNotBlank() && exercise.name.isNotBlank()) { "Exercício inválido no backup." }
        require(!exercise.isCustom || !exercise.id.startsWith("builtin.")) { "Identificador de exercício personalizado reservado." }
        require(exercise.isCustom || exercise.id in exerciseIds) { "Exercício nativo desconhecido no backup." }
        exerciseIds += exercise.id
    }
    root.optJSONArray("workoutExercises")?.forEachObject {
        require(it.optString("workoutId") in workoutIds && it.optString("exerciseId") in exerciseIds) { "Referência inválida na montagem de treino." }
        require(it.optInt("sets") in 1..20 && it.optInt("repMin") in 0..10_000 && it.optInt("repMax") in it.optInt("repMin")..10_000) { "Séries ou metas inválidas no backup." }
        require(it.optDouble("targetLoadKg") in 0.0..2_000.0 && it.optInt("restSeconds") in 0..3_600) { "Carga ou descanso inválido no backup." }
    }
    root.optJSONArray("schedule")?.forEachObject {
        runCatching { LocalDate.parse(it.getString("date")) }.getOrElse { throw IllegalArgumentException("Data inválida na programação.") }
        val rest = it.optBoolean("isRestDay")
        require((rest && it.optString("workoutId").isBlank()) || (!rest && it.optString("workoutId") in workoutIds)) { "Programação inválida no backup." }
        require(it.optString("status") in setOf("Planejado", "Concluído", "Parcial", "Não realizado", "Descanso")) { "Status de programação inválido." }
    }
    root.optJSONArray("sessionSets")?.forEachObject {
        require(it.optInt("setNumber") > 0 && it.optInt("reps") >= 0 && it.optDouble("loadKg") >= 0.0) { "Série realizada inválida no backup." }
        require(it.optInt("durationSeconds") >= 0 && it.optDouble("distanceMeters") >= 0.0) { "Duração ou distância inválida no backup." }
        require(!it.has("exerciseOrder") || it.optInt("exerciseOrder") >= 0) { "Ordem de exercício inválida no backup." }
        require(it.isNull("plannedReps") || it.optInt("plannedReps") >= 0) { "Repetições planejadas inválidas no backup." }
        require(it.isNull("plannedLoadKg") || it.optDouble("plannedLoadKg") >= 0.0) { "Carga planejada inválida no backup." }
        require(it.isNull("rir") || it.optInt("rir") in 0..10) { "RIR inválido no backup." }
        require(!it.has("painLevel") || it.optInt("painLevel") in 0..10) { "Nível de dor inválido no backup." }
    }
    root.optJSONArray("weights")?.forEachObject { require(it.optDouble("weightKg") in 20.0..500.0) { "Peso corporal inválido no backup." } }
    root.optJSONArray("bodyPhotos")?.forEachObject {
        require(it.optString("id").isNotBlank() && it.optString("imageUri").startsWith("content://")) { "Foto corporal inválida no backup." }
        require(it.optLong("addedAt") > 0L && it.optString("notes").length <= 240) { "Metadados de foto inválidos no backup." }
    }
    root.optJSONObject("profile")?.let {
        it.nullableDouble("heightCm")?.let { value -> require(value in 80.0..250.0) { "Altura inválida no backup." } }
        it.nullableDouble("currentWeightKg")?.let { value -> require(value in 20.0..500.0) { "Peso atual inválido no backup." } }
    }
    root.optJSONObject("rewardWallet")?.let {
        require(it.optLong("lifetimeXp") >= 0 && it.optLong("coinBalance") >= 0) { "Saldo de recompensas inválido." }
        require(it.optLong("lifetimeCoinsEarned") >= it.optLong("coinBalance")) { "Histórico de moedas inválido." }
    }
    val rewardItemIds = LiftlyRewardCatalog.items.mapTo(mutableSetOf()) { it.id }
    root.optJSONArray("rewardCatalog")?.forEachObject {
        require(it.optString("id").isNotBlank() && it.optLong("priceCoins") >= 0) { "Item de recompensa inválido." }
        require(it.optInt("requiredLevel") >= 1 && it.optString("slot").isNotBlank()) { "Requisitos de item inválidos." }
        rewardItemIds += it.getString("id")
    }
    root.optJSONArray("rewardInventory")?.forEachObject {
        require(it.optString("itemId") in rewardItemIds) { "Inventário de recompensas inválido." }
    }
    root.optJSONArray("rewardMissions")?.forEachObject {
        require(it.optString("id").isNotBlank() && it.optInt("target") > 0) { "Missão inválida no backup." }
        require(it.optInt("progress") in 0..it.optInt("target")) { "Progresso de missão inválido." }
        require(it.optLong("xpReward") >= 0 && it.optLong("coinReward") >= 0) { "Prêmio de missão inválido." }
        require(it.optLong("periodEnd") > it.optLong("periodStart")) { "Período de missão inválido." }
    }
}

private fun ExerciseEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("muscleGroup", muscleGroup).put("secondaryMuscles", secondaryMuscles)
    .put("equipment", equipment).put("difficulty", difficulty).put("movementType", movementType).put("category", category)
    .put("instructions", instructions).put("cautions", cautions).put("trackingUnit", trackingUnit).put("isCustom", isCustom)
    .put("isFavorite", isFavorite).put("imageUri", imageUri).put("archived", archived)

private fun WorkoutEntity.toJson() = JSONObject().put("id", id).put("name", name).put("description", description)
    .put("color", color).put("icon", icon).put("weekDays", weekDays).put("archived", archived).put("createdAt", createdAt)

private fun WorkoutExerciseEntity.toJson() = JSONObject().put("id", id).put("workoutId", workoutId).put("exerciseId", exerciseId)
    .put("orderIndex", orderIndex).put("sets", sets).put("repMin", repMin).put("repMax", repMax).put("targetLoadKg", targetLoadKg)
    .put("restSeconds", restSeconds).put("notes", notes).put("setType", setType).put("trackingMode", trackingMode)

private fun ScheduleEntity.toJson() = JSONObject().put("id", id).put("date", date).put("workoutId", workoutId).put("status", status).put("isRestDay", isRestDay)
private fun SessionEntity.toJson() = JSONObject().put("id", id).put("workoutId", workoutId).put("workoutName", workoutName).put("startedAt", startedAt).put("finishedAt", finishedAt).put("status", status).put("notes", notes)
private fun SessionSetEntity.toJson() = JSONObject().put("id", id).put("sessionId", sessionId).put("workoutExerciseId", workoutExerciseId).put("exerciseId", exerciseId).put("exerciseName", exerciseName).put("setNumber", setNumber).put("reps", reps).put("loadKg", loadKg).put("completed", completed).put("completedAt", completedAt).put("notes", notes).put("durationSeconds", durationSeconds).put("distanceMeters", distanceMeters).put("trackingMode", trackingMode)
    .put("exerciseOrder", exerciseOrder).put("plannedReps", plannedReps ?: JSONObject.NULL).put("plannedLoadKg", plannedLoadKg ?: JSONObject.NULL)
    .put("rir", rir ?: JSONObject.NULL).put("painLevel", painLevel)
private fun BodyWeightEntryEntity.toJson() = JSONObject().put("id", id).put("measuredAt", measuredAt).put("weightKg", weightKg).put("notes", notes)
private fun BodyPhotoEntity.toJson() = JSONObject().put("id", id).put("imageUri", imageUri).put("addedAt", addedAt).put("notes", notes)
private fun UserProfileEntity.toJson() = JSONObject().put("id", id).put("nickname", nickname).put("birthYear", birthYear).put("heightCm", heightCm).put("currentWeightKg", currentWeightKg).put("objective", objective).put("preferredUnit", preferredUnit)

private fun RewardWalletEntity.toJson() = JSONObject().put("id", id).put("lifetimeXp", lifetimeXp)
    .put("coinBalance", coinBalance).put("lifetimeCoinsEarned", lifetimeCoinsEarned).put("updatedAt", updatedAt)
private fun RewardCatalogItemEntity.toJson() = JSONObject().put("id", id).put("title", title)
    .put("description", description).put("category", category).put("slot", slot).put("rarity", rarity)
    .put("priceCoins", priceCoins).put("requiredLevel", requiredLevel).put("assetKey", assetKey)
    .put("enabled", enabled).put("sortOrder", sortOrder)
private fun RewardInventoryEntity.toJson() = JSONObject().put("itemId", itemId).put("acquiredAt", acquiredAt)
    .put("equippedSlot", equippedSlot ?: JSONObject.NULL)
private fun RewardMissionEntity.toJson() = JSONObject().put("id", id).put("period", period).put("metric", metric)
    .put("title", title).put("description", description).put("target", target).put("progress", progress)
    .put("xpReward", xpReward).put("coinReward", coinReward).put("periodStart", periodStart)
    .put("periodEnd", periodEnd).put("completedAt", completedAt ?: JSONObject.NULL).put("sortOrder", sortOrder)
private fun RewardLedgerEntity.toJson() = JSONObject().put("id", id).put("sourceType", sourceType)
    .put("sourceId", sourceId).put("deltaXp", deltaXp).put("deltaCoins", deltaCoins)
    .put("createdAt", createdAt).put("description", description)

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) { for (i in 0 until length()) block(getJSONObject(i)) }
private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }
private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
private fun JSONObject.nullableInt(name: String): Int? = if (isNull(name)) null else optInt(name)
private fun JSONObject.nullableDouble(name: String): Double? = if (isNull(name)) null else optDouble(name)

private fun JSONObject.toExercise() = ExerciseEntity(getString("id"), getString("name"), getString("muscleGroup"), optString("secondaryMuscles"), getString("equipment"), getString("difficulty"), getString("movementType"), getString("category"), getString("instructions"), getString("cautions"), optString("trackingUnit", "kg"), optBoolean("isCustom"), optBoolean("isFavorite"), nullableString("imageUri"), optBoolean("archived"))
private fun JSONObject.toWorkout() = WorkoutEntity(getString("id"), getString("name"), optString("description"), optLong("color", 0xFF22E5EA), optString("icon", "fitness"), optString("weekDays"), optBoolean("archived"), optLong("createdAt"))
private fun JSONObject.toWorkoutExercise() = WorkoutExerciseEntity(getString("id"), getString("workoutId"), getString("exerciseId"), getInt("orderIndex"), getInt("sets"), getInt("repMin"), getInt("repMax"), getDouble("targetLoadKg"), getInt("restSeconds"), optString("notes"), optString("setType", "Normal"), optString("trackingMode", "Repetições"))
private fun JSONObject.toSchedule() = ScheduleEntity(getString("id"), getString("date"), getString("workoutId"), getString("status"), getBoolean("isRestDay"))
private fun JSONObject.toSession() = SessionEntity(getString("id"), getString("workoutId"), getString("workoutName"), getLong("startedAt"), nullableLong("finishedAt"), getString("status"), optString("notes"))
private fun JSONObject.toSessionSet(fallbackOrder: Int = 0) = SessionSetEntity(
    id = getString("id"),
    sessionId = getString("sessionId"),
    workoutExerciseId = getString("workoutExerciseId"),
    exerciseId = getString("exerciseId"),
    exerciseName = getString("exerciseName"),
    setNumber = getInt("setNumber"),
    reps = getInt("reps"),
    loadKg = getDouble("loadKg"),
    completed = getBoolean("completed"),
    completedAt = nullableLong("completedAt"),
    notes = optString("notes"),
    durationSeconds = optInt("durationSeconds"),
    distanceMeters = optDouble("distanceMeters"),
    trackingMode = optString("trackingMode", "Repetições"),
    exerciseOrder = if (has("exerciseOrder")) optInt("exerciseOrder") else fallbackOrder,
    plannedReps = nullableInt("plannedReps"),
    plannedLoadKg = nullableDouble("plannedLoadKg"),
    rir = nullableInt("rir"),
    painLevel = optInt("painLevel", 0).coerceIn(0, 10)
)
private fun JSONObject.toWeight() = BodyWeightEntryEntity(getString("id"), getLong("measuredAt"), getDouble("weightKg"), optString("notes"))
private fun JSONObject.toBodyPhoto() = BodyPhotoEntity(getString("id"), getString("imageUri"), getLong("addedAt"), optString("notes"))
private fun JSONObject.toProfile() = UserProfileEntity(optInt("id", 1), optString("nickname"), nullableInt("birthYear"), nullableDouble("heightCm"), nullableDouble("currentWeightKg"), optString("objective", "Saúde e bem-estar"), optString("preferredUnit", "Métrico"))
private fun JSONObject.toRewardWallet() = RewardWalletEntity(
    id = optInt("id", 1), lifetimeXp = getLong("lifetimeXp"), coinBalance = getLong("coinBalance"),
    lifetimeCoinsEarned = getLong("lifetimeCoinsEarned"), updatedAt = getLong("updatedAt"),
)
private fun JSONObject.toRewardCatalogItem() = RewardCatalogItemEntity(
    id = getString("id"), title = getString("title"), description = getString("description"),
    category = getString("category"), slot = getString("slot"), rarity = getString("rarity"),
    priceCoins = getLong("priceCoins"), requiredLevel = getInt("requiredLevel"),
    assetKey = getString("assetKey"), enabled = optBoolean("enabled", true), sortOrder = optInt("sortOrder"),
)
private fun JSONObject.toRewardInventory() = RewardInventoryEntity(
    itemId = getString("itemId"), acquiredAt = getLong("acquiredAt"), equippedSlot = nullableString("equippedSlot"),
)
private fun JSONObject.toRewardMission() = RewardMissionEntity(
    id = getString("id"), period = getString("period"), metric = getString("metric"),
    title = getString("title"), description = getString("description"), target = getInt("target"),
    progress = getInt("progress"), xpReward = getLong("xpReward"), coinReward = getLong("coinReward"),
    periodStart = getLong("periodStart"), periodEnd = getLong("periodEnd"),
    completedAt = nullableLong("completedAt"), sortOrder = optInt("sortOrder"),
)
private fun JSONObject.toRewardLedger() = RewardLedgerEntity(
    id = getString("id"), sourceType = getString("sourceType"), sourceId = getString("sourceId"),
    deltaXp = getLong("deltaXp"), deltaCoins = getLong("deltaCoins"),
    createdAt = getLong("createdAt"), description = getString("description"),
)

private fun normalizeExerciseName(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")

private fun findUniqueCompatibleExercise(
    importedName: String,
    exercises: List<ExerciseEntity>,
): ExerciseEntity? {
    val importedTokens = normalizeExerciseName(importedName)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length > 1 }
        .toSet()
    if (importedTokens.size < 2) return null
    val compatible = exercises.filter { candidate ->
        val candidateTokens = normalizeExerciseName(candidate.name)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 }
            .toSet()
        candidateTokens.containsAll(importedTokens) || importedTokens.containsAll(candidateTokens)
    }
    return compatible.singleOrNull()
}
