package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.WorkoutEntity
import com.liftly.app.data.WorkoutExerciseEntity
import java.text.Normalizer
import java.util.Locale

data class WorkoutAnalysisConfig(
    val maxWorkingSetsPerMuscle: Int = 12,
    val maxWorkingSetsPerWorkout: Int = 30,
    val maxWeeklyExposurePerMuscle: Double = 20.0,
    val maxExercisesPerMovement: Int = 2,
    val minimumRestForCompoundSeconds: Int = 60,
    val minimumRestForIsolationSeconds: Int = 30,
    val minimumSetsForRecoveryCheck: Int = 6,
    val minimumCombinedSetsForBalanceCheck: Int = 8,
    val minimumBalanceRatio: Double = 0.5,
    val maximumBalanceRatio: Double = 2.0,
    val maxIntensifierShare: Double = 0.30,
)

enum class SuggestionSeverity { INFORMATION, SUGGESTION, ATTENTION }

enum class WorkoutSuggestionCode {
    INVALID_CONFIGURATION,
    MISSING_EXERCISE,
    DUPLICATE_EXERCISE,
    REDUNDANT_MOVEMENT,
    ISOLATION_BEFORE_COMPOUND,
    SHORT_REST,
    HIGH_MUSCLE_VOLUME,
    HIGH_TOTAL_VOLUME,
    MANY_INTENSIFIERS,
    SHORT_RECOVERY,
    WEEKLY_MUSCLE_VOLUME,
    PUSH_PULL_IMBALANCE,
    KNEE_HIP_IMBALANCE,
    MISSING_BASIC_PATTERN,
}

sealed interface WorkoutSuggestionAction {
    data class MoveExercise(val workoutExerciseId: String, val beforeWorkoutExerciseId: String) : WorkoutSuggestionAction
    data class RemoveExercise(val workoutExerciseId: String) : WorkoutSuggestionAction
    data class SetRest(val workoutExerciseId: String, val seconds: Int) : WorkoutSuggestionAction
    data class ReviewVolume(val workoutExerciseIds: List<String>) : WorkoutSuggestionAction
    data class ReplaceExercise(
        val workoutExerciseId: String,
        val candidateExerciseIds: List<String>,
    ) : WorkoutSuggestionAction
}

data class WorkoutSuggestion(
    val code: WorkoutSuggestionCode,
    val severity: SuggestionSeverity,
    val title: String,
    val message: String,
    val evidence: List<String> = emptyList(),
    val affectedWorkoutExerciseIds: List<String> = emptyList(),
    val action: WorkoutSuggestionAction? = null,
) {
    /** Identificador estável enquanto os mesmos itens continuarem envolvidos na recomendação. */
    val fingerprint: String = buildString {
        append(code.name)
        append(':')
        append(affectedWorkoutExerciseIds.sorted().joinToString(","))
        append(':')
        append(evidence.sorted().joinToString("|"))
    }
}

/**
 * Analisador local e determinístico. Ele não prescreve nem altera treinos; apenas retorna
 * recomendações gerais que a interface pode aplicar ou ignorar individualmente.
 */
class WorkoutAnalyzer(
    private val config: WorkoutAnalysisConfig = WorkoutAnalysisConfig(),
) {
    fun analyze(
        workout: WorkoutEntity,
        workoutExercises: List<WorkoutExerciseEntity>,
        exercises: List<ExerciseEntity>,
    ): List<WorkoutSuggestion> {
        if (workout.archived) return emptyList()

        val exerciseById = exercises.associateBy(ExerciseEntity::id)
        val ordered = workoutExercises
            .asSequence()
            .filter { it.workoutId == workout.id }
            .sortedWith(compareBy(WorkoutExerciseEntity::orderIndex, WorkoutExerciseEntity::id))
            .toList()

        val resolved = ordered.mapNotNull { item -> exerciseById[item.exerciseId]?.let { item to it } }
        val suggestions = buildList {
            addAll(validateConfiguration(ordered, exerciseById))
            addAll(findDuplicates(resolved))
            addAll(findRedundantMovements(resolved, exercises))
            addAll(findIsolationBeforeCompound(resolved))
            addAll(findShortRest(resolved))
            addAll(findHighVolume(resolved))
            findIntensifierDensity(resolved)?.let(::add)
        }

        return suggestions
            .distinctBy { it.fingerprint }
            .sortedWith(compareByDescending<WorkoutSuggestion> { it.severity.ordinal }.thenBy { it.code.ordinal })
    }

    /**
     * Analisa a grade semanal recorrente descrita por [WorkoutEntity.weekDays]. Dias aceitos
     * incluem nomes completos, abreviações em português e números ISO de 1 (segunda) a 7.
     */
    fun analyzeWeekly(
        workouts: List<WorkoutEntity>,
        workoutExercises: List<WorkoutExerciseEntity>,
        exercises: List<ExerciseEntity>,
    ): List<WorkoutSuggestion> {
        val activeWorkouts = workouts.filterNot(WorkoutEntity::archived)
        val exerciseById = exercises.associateBy(ExerciseEntity::id)
        val itemsByWorkout = workoutExercises.groupBy(WorkoutExerciseEntity::workoutId)
        val scheduled = buildList {
            activeWorkouts.forEach { workout ->
                parseWeekDays(workout.weekDays).forEach { day ->
                    itemsByWorkout[workout.id].orEmpty().forEach { item ->
                        val exercise = exerciseById[item.exerciseId] ?: return@forEach
                        if (!item.isWarmUp() && item.sets > 0 && !exercise.archived) {
                            add(ScheduledExercise(day, workout, item, exercise))
                        }
                    }
                }
            }
        }
        if (scheduled.isEmpty()) return emptyList()

        return buildList {
            addAll(findShortRecovery(scheduled))
            addAll(findWeeklyMuscleVolume(scheduled))
            findPatternImbalance(
                scheduled = scheduled,
                firstName = "empurrar",
                secondName = "puxar",
                code = WorkoutSuggestionCode.PUSH_PULL_IMBALANCE,
                title = "Distribuição de empurrar e puxar",
            )?.let(::add)
            findPatternImbalance(
                scheduled = scheduled,
                firstName = "agachar",
                secondName = "hinge",
                code = WorkoutSuggestionCode.KNEE_HIP_IMBALANCE,
                title = "Distribuição entre joelhos e quadril",
            )?.let(::add)
            findMissingBasicPattern(scheduled)?.let(::add)
        }.distinctBy { it.fingerprint }
            .sortedWith(compareByDescending<WorkoutSuggestion> { it.severity.ordinal }.thenBy { it.code.ordinal })
    }

    private fun validateConfiguration(
        items: List<WorkoutExerciseEntity>,
        exerciseById: Map<String, ExerciseEntity>,
    ): List<WorkoutSuggestion> = buildList {
        items.forEach { item ->
            if (exerciseById[item.exerciseId] == null) {
                add(
                    WorkoutSuggestion(
                        code = WorkoutSuggestionCode.MISSING_EXERCISE,
                        severity = SuggestionSeverity.ATTENTION,
                        title = "Exercício indisponível",
                        message = "Um item do treino não está mais disponível no catálogo. Revise-o antes de iniciar.",
                        evidence = listOf("Identificador: ${item.exerciseId}"),
                        affectedWorkoutExerciseIds = listOf(item.id),
                        action = WorkoutSuggestionAction.RemoveExercise(item.id),
                    ),
                )
            } else if (item.sets <= 0 || item.repMin < 0 || item.repMax < item.repMin || item.restSeconds < 0) {
                add(
                    WorkoutSuggestion(
                        code = WorkoutSuggestionCode.INVALID_CONFIGURATION,
                        severity = SuggestionSeverity.ATTENTION,
                        title = "Configuração incompleta",
                        message = "Revise séries, repetições e descanso deste exercício.",
                        evidence = listOf("${item.sets} série(s), ${item.repMin}–${item.repMax} repetição(ões), ${item.restSeconds} s"),
                        affectedWorkoutExerciseIds = listOf(item.id),
                    ),
                )
            }
        }
    }

    private fun findDuplicates(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
    ): List<WorkoutSuggestion> = resolved
        .groupBy { it.second.id }
        .values
        .filter { it.size > 1 }
        .map { duplicates ->
            val removable = duplicates.drop(1).last().first
            WorkoutSuggestion(
                code = WorkoutSuggestionCode.DUPLICATE_EXERCISE,
                severity = SuggestionSeverity.SUGGESTION,
                title = "Exercício repetido",
                message = "${duplicates.first().second.name} aparece ${duplicates.size} vezes neste treino. Confirme se a repetição é intencional.",
                evidence = listOf("Posições: ${duplicates.joinToString { (it.first.orderIndex + 1).toString() }}"),
                affectedWorkoutExerciseIds = duplicates.map { it.first.id },
                action = WorkoutSuggestionAction.RemoveExercise(removable.id),
            )
        }

    private fun findRedundantMovements(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
        catalog: List<ExerciseEntity>,
    ): List<WorkoutSuggestion> = resolved
        .filterNot { it.first.isWarmUp() }
        .groupBy { (_, exercise) -> exercise.muscleGroup.normalized() to exercise.movementType.normalized() }
        .values
        .filter { it.size > config.maxExercisesPerMovement }
        .map { similar ->
            val last = similar.last()
            val currentIds = resolved.map { it.second.id }.toSet()
            val alternatives = catalog.asSequence()
                .filterNot(ExerciseEntity::archived)
                .filter { it.muscleGroup.normalized() == last.second.muscleGroup.normalized() }
                .filter { it.movementType.normalized() != last.second.movementType.normalized() }
                .filter { it.id !in currentIds }
                .sortedBy(ExerciseEntity::name)
                .take(3)
                .map(ExerciseEntity::id)
                .toList()
            WorkoutSuggestion(
                code = WorkoutSuggestionCode.REDUNDANT_MOVEMENT,
                severity = SuggestionSeverity.SUGGESTION,
                title = "Movimentos muito semelhantes",
                message = "Há ${similar.size} exercícios de ${last.second.movementType.lowercase(Locale.getDefault())} para ${last.second.muscleGroup.lowercase(Locale.getDefault())}.",
                evidence = similar.map { it.second.name },
                affectedWorkoutExerciseIds = similar.map { it.first.id },
                action = if (alternatives.isEmpty()) null else WorkoutSuggestionAction.ReplaceExercise(last.first.id, alternatives),
            )
        }

    private fun findIsolationBeforeCompound(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
    ): List<WorkoutSuggestion> {
        val working = resolved.filterNot { it.first.isWarmUp() }
        val compound = working.firstOrNull { (_, exercise) -> exercise.isCompound() } ?: return emptyList()
        val isolations = working.takeWhile { it.first.id != compound.first.id }
            .filter { (_, exercise) -> !exercise.isCompound() }
            .filter { (_, exercise) ->
                exercise.muscleGroup.normalized() == compound.second.muscleGroup.normalized() ||
                    compound.second.secondaryMuscles.normalized().contains(exercise.muscleGroup.normalized())
            }
        if (isolations.isEmpty()) return emptyList()

        return listOf(
            WorkoutSuggestion(
                code = WorkoutSuggestionCode.ISOLATION_BEFORE_COMPOUND,
                severity = SuggestionSeverity.SUGGESTION,
                title = "Exercício composto mais tarde",
                message = "Considere executar ${compound.second.name} antes dos isoladores, caso ele seja uma prioridade do treino.",
                evidence = isolations.map { "${it.second.name} vem antes" },
                affectedWorkoutExerciseIds = isolations.map { it.first.id } + compound.first.id,
                action = WorkoutSuggestionAction.MoveExercise(compound.first.id, isolations.first().first.id),
            ),
        )
    }

    private fun findShortRest(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
    ): List<WorkoutSuggestion> = resolved.mapNotNull { (item, exercise) ->
        if (item.isWarmUp() || item.restSeconds == 0) return@mapNotNull null
        val minimum = if (exercise.isCompound()) {
            config.minimumRestForCompoundSeconds
        } else {
            config.minimumRestForIsolationSeconds
        }
        if (item.restSeconds >= minimum) return@mapNotNull null

        WorkoutSuggestion(
            code = WorkoutSuggestionCode.SHORT_REST,
            severity = SuggestionSeverity.INFORMATION,
            title = "Descanso curto",
            message = "O descanso de ${item.restSeconds} s em ${exercise.name} pode dificultar a recuperação entre séries.",
            evidence = listOf("Referência geral desta análise: pelo menos $minimum s"),
            affectedWorkoutExerciseIds = listOf(item.id),
            action = WorkoutSuggestionAction.SetRest(item.id, minimum),
        )
    }

    private fun findHighVolume(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
    ): List<WorkoutSuggestion> = buildList {
        val working = resolved.filterNot { it.first.isWarmUp() }.filter { it.first.sets > 0 }
        val total = working.sumOf { it.first.sets }
        if (total > config.maxWorkingSetsPerWorkout) {
            add(
                WorkoutSuggestion(
                    code = WorkoutSuggestionCode.HIGH_TOTAL_VOLUME,
                    severity = SuggestionSeverity.ATTENTION,
                    title = "Treino com muitas séries",
                    message = "Este treino tem $total séries de trabalho. O volume pode estar alto para uma única sessão.",
                    evidence = listOf("Limiar geral da análise: ${config.maxWorkingSetsPerWorkout} séries"),
                    affectedWorkoutExerciseIds = working.map { it.first.id },
                    action = WorkoutSuggestionAction.ReviewVolume(working.map { it.first.id }),
                ),
            )
        }
        working.groupBy { it.second.muscleGroup.normalized() }.values.forEach { group ->
            val directSets = group.sumOf { it.first.sets }
            if (directSets > config.maxWorkingSetsPerMuscle) {
                add(
                    WorkoutSuggestion(
                        code = WorkoutSuggestionCode.HIGH_MUSCLE_VOLUME,
                        severity = SuggestionSeverity.ATTENTION,
                        title = "Volume concentrado em ${group.first().second.muscleGroup}",
                        message = "Há $directSets séries diretas para este grupo na sessão. Considere objetivo, experiência e recuperação.",
                        evidence = group.map { "${it.second.name}: ${it.first.sets} séries" },
                        affectedWorkoutExerciseIds = group.map { it.first.id },
                        action = WorkoutSuggestionAction.ReviewVolume(group.map { it.first.id }),
                    ),
                )
            }
        }
    }

    private fun findIntensifierDensity(
        resolved: List<Pair<WorkoutExerciseEntity, ExerciseEntity>>,
    ): WorkoutSuggestion? {
        val working = resolved.filterNot { it.first.isWarmUp() }.filter { it.first.sets > 0 }
        val totalSets = working.sumOf { it.first.sets }
        if (totalSets == 0) return null
        val intensified = working.filter { it.first.isIntensifier() }
        val intensifiedSets = intensified.sumOf { it.first.sets }
        if (intensifiedSets.toDouble() / totalSets <= config.maxIntensifierShare) return null

        return WorkoutSuggestion(
            code = WorkoutSuggestionCode.MANY_INTENSIFIERS,
            severity = SuggestionSeverity.INFORMATION,
            title = "Muitas técnicas intensificadoras",
            message = "$intensifiedSets de $totalSets séries estão em dropset ou supersérie. Considere se essa densidade é intencional.",
            evidence = intensified.map { "${it.second.name}: ${it.first.setType}" },
            affectedWorkoutExerciseIds = intensified.map { it.first.id },
        )
    }

    private fun findShortRecovery(scheduled: List<ScheduledExercise>): List<WorkoutSuggestion> {
        val directByMuscleAndDay = scheduled.groupBy { it.exercise.muscleGroup.normalized() to it.day }
            .mapValues { (_, entries) -> entries.sumOf { it.item.sets } }
        val itemIdsByMuscleAndDay = scheduled.groupBy { it.exercise.muscleGroup.normalized() to it.day }
            .mapValues { (_, entries) -> entries.map { it.item.id } }

        return directByMuscleAndDay.keys.groupBy(Pair<String, Int>::first).flatMap { (muscle, keys) ->
            val days = keys.map(Pair<String, Int>::second).sorted()
            days.mapNotNull { day ->
                val nextDay = if (day == 7) 1 else day + 1
                val currentSets = directByMuscleAndDay[muscle to day] ?: 0
                val nextSets = directByMuscleAndDay[muscle to nextDay] ?: 0
                if (currentSets < config.minimumSetsForRecoveryCheck || nextSets < config.minimumSetsForRecoveryCheck) {
                    return@mapNotNull null
                }
                val ids = itemIdsByMuscleAndDay[muscle to day].orEmpty() + itemIdsByMuscleAndDay[muscle to nextDay].orEmpty()
                WorkoutSuggestion(
                    code = WorkoutSuggestionCode.SHORT_RECOVERY,
                    severity = SuggestionSeverity.ATTENTION,
                    title = "Pouco intervalo para ${scheduled.first { it.exercise.muscleGroup.normalized() == muscle }.exercise.muscleGroup}",
                    message = "Este grupo aparece em dias consecutivos com $currentSets e $nextSets séries diretas.",
                    evidence = listOf("${dayName(day)} → ${dayName(nextDay)}"),
                    affectedWorkoutExerciseIds = ids,
                )
            }
        }
    }

    private fun findWeeklyMuscleVolume(scheduled: List<ScheduledExercise>): List<WorkoutSuggestion> = scheduled
        .flatMap { scheduledExercise ->
            buildList {
                add(MuscleExposure(scheduledExercise.exercise.muscleGroup, scheduledExercise.item.sets.toDouble(), scheduledExercise.item.id))
                scheduledExercise.exercise.secondaryMuscles.split(',', ';')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach { add(MuscleExposure(it, scheduledExercise.item.sets * 0.5, scheduledExercise.item.id)) }
            }
        }
        .groupBy { it.muscle.normalized() }
        .values
        .mapNotNull { exposures ->
            val total = exposures.sumOf(MuscleExposure::sets)
            if (total <= config.maxWeeklyExposurePerMuscle) return@mapNotNull null
            WorkoutSuggestion(
                code = WorkoutSuggestionCode.WEEKLY_MUSCLE_VOLUME,
                severity = SuggestionSeverity.ATTENTION,
                title = "Volume semanal elevado para ${exposures.first().muscle}",
                message = "A programação soma ${formatNumber(total)} exposições diretas e indiretas para este grupo.",
                evidence = listOf("Limiar geral da análise: ${formatNumber(config.maxWeeklyExposurePerMuscle)} exposições"),
                affectedWorkoutExerciseIds = exposures.map(MuscleExposure::itemId).distinct(),
                action = WorkoutSuggestionAction.ReviewVolume(exposures.map(MuscleExposure::itemId).distinct()),
            )
        }

    private fun findPatternImbalance(
        scheduled: List<ScheduledExercise>,
        firstName: String,
        secondName: String,
        code: WorkoutSuggestionCode,
        title: String,
    ): WorkoutSuggestion? {
        fun setsFor(pattern: String): Int = scheduled
            .filter { it.exercise.movementType.normalized().contains(pattern) }
            .sumOf { it.item.sets }
        val first = setsFor(firstName)
        val second = setsFor(secondName)
        if (first + second < config.minimumCombinedSetsForBalanceCheck) return null
        val ratio = if (second == 0) Double.POSITIVE_INFINITY else first.toDouble() / second
        if (ratio in config.minimumBalanceRatio..config.maximumBalanceRatio) return null
        val affected = scheduled
            .filter { it.exercise.movementType.normalized().contains(firstName) || it.exercise.movementType.normalized().contains(secondName) }
            .map { it.item.id }
        return WorkoutSuggestion(
            code = code,
            severity = SuggestionSeverity.SUGGESTION,
            title = title,
            message = "A semana tem $first séries de $firstName e $second séries de $secondName. Considere se a diferença combina com seu objetivo.",
            evidence = listOf("Faixa de proporção usada pela análise: ${config.minimumBalanceRatio}–${config.maximumBalanceRatio}"),
            affectedWorkoutExerciseIds = affected,
            action = WorkoutSuggestionAction.ReviewVolume(affected),
        )
    }

    private fun findMissingBasicPattern(scheduled: List<ScheduledExercise>): WorkoutSuggestion? {
        val resistanceWorkouts = scheduled
            .filter { it.exercise.category.normalized() in RESISTANCE_CATEGORIES }
            .map { it.workout.id to it.day }
            .distinct()
        if (resistanceWorkouts.size < 3) return null

        val movementNames = scheduled.map { it.exercise.movementType.normalized() }
        val missing = BASIC_PATTERNS.filterValues { aliases -> movementNames.none { movement -> aliases.any(movement::contains) } }.keys
        if (missing.isEmpty()) return null
        val affected = scheduled.map { it.item.id }.distinct()
        return WorkoutSuggestion(
            code = WorkoutSuggestionCode.MISSING_BASIC_PATTERN,
            severity = SuggestionSeverity.INFORMATION,
            title = "Padrões básicos ausentes",
            message = "Não encontramos ${missing.joinToString()} nesta programação. Isso pode ser intencional conforme seu objetivo.",
            affectedWorkoutExerciseIds = affected,
        )
    }

    private data class ScheduledExercise(
        val day: Int,
        val workout: WorkoutEntity,
        val item: WorkoutExerciseEntity,
        val exercise: ExerciseEntity,
    )

    private data class MuscleExposure(val muscle: String, val sets: Double, val itemId: String)

    private fun parseWeekDays(raw: String): Set<Int> = raw
        .split(',', ';', '|')
        .mapNotNull { token ->
            when (token.trim().normalized().removeSuffix("-feira")) {
                "1", "seg", "segunda" -> 1
                "2", "ter", "terca" -> 2
                "3", "qua", "quarta" -> 3
                "4", "qui", "quinta" -> 4
                "5", "sex", "sexta" -> 5
                "6", "sab", "sabado" -> 6
                "7", "dom", "domingo" -> 7
                else -> null
            }
        }
        .toSet()

    private fun dayName(day: Int) = listOf("", "segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")[day]

    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.ROOT, value)

    private fun WorkoutExerciseEntity.isWarmUp() = setType.normalized().contains("aquec")

    private fun WorkoutExerciseEntity.isIntensifier(): Boolean {
        val normalized = setType.normalized()
        return normalized.contains("drop") || normalized.contains("super")
    }

    private fun ExerciseEntity.isCompound(): Boolean {
        val movement = movementType.normalized()
        return COMPOUND_PATTERNS.any(movement::contains)
    }

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()

    private companion object {
        val COMPOUND_PATTERNS = listOf(
            "empurrar", "puxar", "agachar", "hinge", "levantamento", "subir degrau",
            "carregar", "corpo inteiro", "remar", "pedalar e empurrar",
        )
        val RESISTANCE_CATEGORIES = setOf("musculacao", "peso corporal", "funcional")
        val BASIC_PATTERNS = linkedMapOf(
            "agachar" to listOf("agachar", "empurrar com pernas", "subir degrau"),
            "hinge de quadril" to listOf("hinge", "extensao de quadril"),
            "empurrar" to listOf("empurrar"),
            "puxar" to listOf("puxar", "remar"),
        )
    }
}

const val WORKOUT_ANALYSIS_DISCLAIMER: String =
    "Estas sugestões são gerais e não substituem a avaliação de um profissional de educação física. " +
        "Considere seu objetivo, experiência, recuperação e limitações."
