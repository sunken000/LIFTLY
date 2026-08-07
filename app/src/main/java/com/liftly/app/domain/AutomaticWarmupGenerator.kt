package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.WorkoutExerciseEntity
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Configuração conservadora do aquecimento. As porcentagens são sempre menores que 100% e
 * arredondadas para baixo, portanto uma série de aproximação nunca ultrapassa a carga de trabalho.
 */
data class AutomaticWarmupConfig(
    val generalPulseRaiseSeconds: Int = 180,
    val generalMobilitySeconds: Int = 90,
    val generalPatternRehearsalSeconds: Int = 60,
    val primaryCompoundLoads: List<WarmupLoadStep> = listOf(
        WarmupLoadStep(0.40, 6),
        WarmupLoadStep(0.75, 3),
    ),
    val primaryIsolationLoads: List<WarmupLoadStep> = listOf(WarmupLoadStep(0.50, 6)),
    val newCompoundPatternLoads: List<WarmupLoadStep> = listOf(
        WarmupLoadStep(0.40, 6),
        WarmupLoadStep(0.75, 3),
    ),
    val newIsolationPatternLoads: List<WarmupLoadStep> = listOf(WarmupLoadStep(0.50, 6)),
    val relatedPatternLoads: List<WarmupLoadStep> = listOf(WarmupLoadStep(0.50, 4)),
) {
    init {
        require(generalPulseRaiseSeconds >= 0)
        require(generalMobilitySeconds >= 0)
        require(generalPatternRehearsalSeconds >= 0)
        listOf(
            primaryCompoundLoads,
            primaryIsolationLoads,
            newCompoundPatternLoads,
            newIsolationPatternLoads,
            relatedPatternLoads,
        ).flatten().forEach {
            require(it.loadFraction in 0.0..<1.0) { "A carga de aquecimento deve ficar abaixo da carga de trabalho." }
            require(it.repetitions > 0)
        }
    }
}

data class WarmupLoadStep(val loadFraction: Double, val repetitions: Int)

data class WarmupExerciseInput(
    val workoutExerciseId: String,
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val movementType: String,
    val muscleGroup: String,
    val secondaryMuscles: String = "",
    val equipment: String,
    val category: String,
    val trackingMode: String,
    val workSets: Int,
    val targetRepMin: Int,
    val targetRepMax: Int,
    val targetLoadKg: Double,
    val setType: String = "Normal",
)

data class GeneralWarmupStep(
    val title: String,
    val instruction: String,
    val reason: String,
    val durationSeconds: Int,
)

data class GeneralWarmupStage(val steps: List<GeneralWarmupStep>) {
    val estimatedDurationSeconds: Int = steps.sumOf(GeneralWarmupStep::durationSeconds)
}

enum class WarmupSetKind {
    LOADED_APPROACH,
    MOVEMENT_REHEARSAL,
    TIME_ACCLIMATION,
    DISTANCE_ACCLIMATION,
}

data class WarmupApproachSet(
    val number: Int,
    val kind: WarmupSetKind,
    val loadKg: Double? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val effortCue: String,
    val explanation: String,
    val restAfterSeconds: Int,
    val estimatedExecutionSeconds: Int,
)

enum class WarmupPriority {
    PRIMARY_COMPOUND,
    PRIMARY_EXERCISE,
    NEW_MOVEMENT_PATTERN,
    RELATED_PATTERN,
    REHEARSAL_ONLY,
    ALREADY_WARMUP,
    NOT_NEEDED,
}

data class ExerciseWarmupPlan(
    val workoutExerciseId: String,
    val exerciseId: String,
    val exerciseName: String,
    val workoutOrder: Int,
    val movementPattern: String,
    val priority: WarmupPriority,
    val sets: List<WarmupApproachSet>,
    val explanation: String,
) {
    val estimatedDurationSeconds: Int = sets.sumOf { it.estimatedExecutionSeconds + it.restAfterSeconds }
}

data class AutomaticWarmupPlan(
    val general: GeneralWarmupStage,
    val exercises: List<ExerciseWarmupPlan>,
) {
    val estimatedDurationSeconds: Int =
        general.estimatedDurationSeconds + exercises.sumOf(ExerciseWarmupPlan::estimatedDurationSeconds)

    val generatedSetCount: Int = exercises.sumOf { it.sets.size }
}

/**
 * Gerador local, puro e determinístico. Ele somente descreve um plano; não persiste séries,
 * não inicia sessões e não cria registros de progresso.
 */
class AutomaticWarmupGenerator(
    private val config: AutomaticWarmupConfig = AutomaticWarmupConfig(),
) {
    /**
     * Conveniência para a camada de aplicação. [workoutExercises] deve conter apenas o treino do
     * dia selecionado; a ordem é reconstruída por [WorkoutExerciseEntity.orderIndex].
     */
    fun generate(
        workoutExercises: List<WorkoutExerciseEntity>,
        exercises: List<ExerciseEntity>,
    ): AutomaticWarmupPlan {
        val exerciseById = exercises.associateBy(ExerciseEntity::id)
        return generate(
            workoutExercises.mapNotNull { item ->
                val exercise = exerciseById[item.exerciseId] ?: return@mapNotNull null
                WarmupExerciseInput(
                    workoutExerciseId = item.id,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    orderIndex = item.orderIndex,
                    movementType = exercise.movementType,
                    muscleGroup = exercise.muscleGroup,
                    secondaryMuscles = exercise.secondaryMuscles,
                    equipment = exercise.equipment,
                    category = exercise.category,
                    trackingMode = item.trackingMode,
                    workSets = item.sets,
                    targetRepMin = item.repMin,
                    targetRepMax = item.repMax,
                    targetLoadKg = item.targetLoadKg,
                    setType = item.setType,
                )
            },
        )
    }

    fun generate(inputs: List<WarmupExerciseInput>): AutomaticWarmupPlan {
        val ordered = inputs.sortedWith(compareBy(WarmupExerciseInput::orderIndex, WarmupExerciseInput::workoutExerciseId))
        if (ordered.isEmpty()) return AutomaticWarmupPlan(GeneralWarmupStage(emptyList()), emptyList())

        val resistance = ordered.filter { it.isResistanceExercise() && it.workSets > 0 && !it.isExplicitWarmup() }
        val primaryLoadedCompoundId = resistance.firstOrNull { it.isCompound() && it.hasExternalLoad() }?.workoutExerciseId
        val primaryLoadedExerciseId = resistance.firstOrNull { it.hasExternalLoad() }?.workoutExerciseId
        val primaryCompoundId = resistance.firstOrNull { it.isCompound() }?.workoutExerciseId
        val preparedPatterns = linkedSetOf<String>()

        val plans = ordered.map { input ->
            val pattern = input.canonicalPattern()
            val isPatternPrepared = pattern in preparedPatterns
            val isPrimary = when {
                primaryLoadedCompoundId != null -> input.workoutExerciseId == primaryLoadedCompoundId
                primaryLoadedExerciseId != null -> input.workoutExerciseId == primaryLoadedExerciseId
                else -> input.workoutExerciseId == primaryCompoundId
            }

            val plan = buildExercisePlan(input, pattern, isPatternPrepared, isPrimary)
            if (input.workSets > 0 && input.isResistanceExercise()) preparedPatterns += pattern
            plan
        }

        return AutomaticWarmupPlan(
            general = buildGeneralStage(ordered),
            exercises = plans,
        )
    }

    private fun buildGeneralStage(inputs: List<WarmupExerciseInput>): GeneralWarmupStage {
        val active = inputs.filter { it.workSets > 0 && !it.isExplicitWarmup() }
        if (active.isEmpty()) return GeneralWarmupStage(emptyList())

        val patterns = active.asSequence().filter { it.isResistanceExercise() }
            .map { it.canonicalPattern() }.distinct().take(3).toList()
        val hasUpper = patterns.any { it in UPPER_PATTERNS }
        val hasLower = patterns.any { it in LOWER_PATTERNS }
        val mobilityInstruction = when {
            hasUpper && hasLower -> "Mobilize dinamicamente ombros, escápulas, quadris, joelhos e tornozelos, sem sustentar alongamentos intensos."
            hasLower -> "Mobilize dinamicamente quadris, joelhos e tornozelos nas amplitudes que serão usadas hoje."
            hasUpper -> "Mobilize dinamicamente ombros, escápulas, cotovelos e punhos nas amplitudes que serão usadas hoje."
            else -> "Faça movimentos articulares suaves nas regiões que serão exigidas pelo treino."
        }
        val patternNames = patterns.map(::patternLabel)
        val steps = buildList {
            if (config.generalPulseRaiseSeconds > 0) add(
                GeneralWarmupStep(
                    title = if (active.all { it.isCardio() }) "Entrada gradual" else "Elevar a temperatura",
                    instruction = "Use bicicleta, caminhada ou outro movimento confortável em ritmo leve; deve ser possível conversar normalmente.",
                    reason = "Aumentar gradualmente a temperatura e a frequência cardíaca antes das tarefas do dia.",
                    durationSeconds = config.generalPulseRaiseSeconds,
                ),
            )
            if (config.generalMobilitySeconds > 0) add(
                GeneralWarmupStep(
                    title = "Mobilidade dinâmica do dia",
                    instruction = mobilityInstruction,
                    reason = "Preparar as articulações conforme os padrões presentes neste treino, sem gerar fadiga.",
                    durationSeconds = config.generalMobilitySeconds,
                ),
            )
            if (patternNames.isNotEmpty() && config.generalPatternRehearsalSeconds > 0) add(
                GeneralWarmupStep(
                    title = "Ensaiar os padrões principais",
                    instruction = "Faça movimentos sem esforço de ${patternNames.joinToString(limit = 3)} antes de adicionar carga.",
                    reason = "Relembrar a técnica dos primeiros padrões do treino e identificar desconforto antes das séries de aproximação.",
                    durationSeconds = config.generalPatternRehearsalSeconds,
                ),
            )
        }
        return GeneralWarmupStage(steps)
    }

    private fun buildExercisePlan(
        input: WarmupExerciseInput,
        pattern: String,
        isPatternPrepared: Boolean,
        isPrimary: Boolean,
    ): ExerciseWarmupPlan {
        fun plan(priority: WarmupPriority, sets: List<WarmupApproachSet>, explanation: String) = ExerciseWarmupPlan(
            workoutExerciseId = input.workoutExerciseId,
            exerciseId = input.exerciseId,
            exerciseName = input.exerciseName,
            workoutOrder = input.orderIndex,
            movementPattern = patternLabel(pattern),
            priority = priority,
            sets = sets,
            explanation = explanation,
        )

        if (input.isExplicitWarmup()) return plan(
            WarmupPriority.ALREADY_WARMUP,
            emptyList(),
            "Este item já está marcado como aquecimento; nenhuma série automática foi duplicada.",
        )
        if (input.workSets <= 0) return plan(
            WarmupPriority.NOT_NEEDED,
            emptyList(),
            "O exercício não possui séries de trabalho válidas, por isso não recebeu aproximações.",
        )
        if (input.isMobility()) return plan(
            WarmupPriority.NOT_NEEDED,
            emptyList(),
            "Este exercício já é uma tarefa de mobilidade e funciona como preparação, não como série de aproximação.",
        )
        if (input.isTimeTracked()) {
            val seconds = acclimationDuration(input.targetRepMin)
            if (seconds == null) return plan(
                WarmupPriority.NOT_NEEDED,
                emptyList(),
                "Não há duração alvo válida para calcular uma entrada gradual.",
            )
            return plan(
                WarmupPriority.REHEARSAL_ONLY,
                listOf(
                    WarmupApproachSet(
                        number = 1,
                        kind = WarmupSetKind.TIME_ACCLIMATION,
                        durationSeconds = seconds,
                        effortCue = "Ritmo leve e técnica confortável",
                        explanation = "Entrada curta para ajustar ritmo e equipamento antes do intervalo principal.",
                        restAfterSeconds = 30,
                        estimatedExecutionSeconds = seconds,
                    ),
                ),
                "Exercício medido por tempo: foi criada uma entrada curta, sem inventar carga ou repetições.",
            )
        }
        if (input.isDistanceTracked()) {
            val distance = acclimationDistance(input.targetRepMin.toDouble())
            if (distance == null) return plan(
                WarmupPriority.NOT_NEEDED,
                emptyList(),
                "Não há distância alvo válida para calcular uma entrada gradual.",
            )
            return plan(
                WarmupPriority.REHEARSAL_ONLY,
                listOf(
                    WarmupApproachSet(
                        number = 1,
                        kind = WarmupSetKind.DISTANCE_ACCLIMATION,
                        distanceMeters = distance,
                        effortCue = "Ritmo leve e controlado",
                        explanation = "Trecho curto para adaptar técnica e ritmo antes da distância principal.",
                        restAfterSeconds = 30,
                        estimatedExecutionSeconds = distanceExecutionSeconds(distance),
                    ),
                ),
                "Exercício medido por distância: foi criado um trecho leve menor ou igual à meta de trabalho.",
            )
        }

        if (!input.hasExternalLoad()) {
            if (isPatternPrepared && !isPrimary) return plan(
                WarmupPriority.RELATED_PATTERN,
                emptyList(),
                "O mesmo padrão já foi preparado anteriormente e não há carga externa alvo; outra série seria redundante.",
            )
            val reps = input.targetRepMin.takeIf { it > 0 }?.coerceIn(3, 8) ?: 5
            return plan(
                WarmupPriority.REHEARSAL_ONLY,
                listOf(
                    WarmupApproachSet(
                        number = 1,
                        kind = WarmupSetKind.MOVEMENT_REHEARSAL,
                        repetitions = reps,
                        effortCue = "RIR 5+; use uma variação mais fácil se necessário",
                        explanation = "Ensaio técnico sem carga externa antes das séries de trabalho, usando uma variação assistida ou mais fácil quando necessário.",
                        restAfterSeconds = 30,
                        estimatedExecutionSeconds = (reps * 4).coerceAtLeast(20),
                    ),
                ),
                "A carga alvo é zero ou o exercício usa peso corporal; o Liftly não inventa uma carga e sugere apenas um ensaio técnico.",
            )
        }

        val compound = input.isCompound()
        val priority: WarmupPriority
        val template: List<WarmupLoadStep>
        val explanation: String
        when {
            isPrimary && compound -> {
                priority = WarmupPriority.PRIMARY_COMPOUND
                template = config.primaryCompoundLoads
                explanation = "Primeiro exercício composto com carga do treino: recebe a progressão mais completa e conservadora."
            }
            isPrimary -> {
                priority = WarmupPriority.PRIMARY_EXERCISE
                template = config.primaryIsolationLoads
                explanation = "Primeiro exercício com carga do treino: recebe uma aproximação gradual antes das séries de trabalho."
            }
            !isPatternPrepared && compound -> {
                priority = WarmupPriority.NEW_MOVEMENT_PATTERN
                template = config.newCompoundPatternLoads
                explanation = "Este exercício introduz um novo padrão composto no treino e precisa de aproximações próprias."
            }
            !isPatternPrepared -> {
                priority = WarmupPriority.NEW_MOVEMENT_PATTERN
                template = config.newIsolationPatternLoads
                explanation = "Este exercício introduz um novo padrão, mas exige menos aproximações que o composto principal."
            }
            else -> {
                priority = WarmupPriority.RELATED_PATTERN
                template = config.relatedPatternLoads
                explanation = "O padrão já foi preparado anteriormente; foi mantida apenas uma série curta para adaptação ao exercício."
            }
        }

        val sets = loadedSets(input, template)
        if (sets.isEmpty()) {
            val reps = input.targetRepMin.takeIf { it > 0 }?.coerceIn(3, 6) ?: 5
            return plan(
                WarmupPriority.REHEARSAL_ONLY,
                listOf(
                    WarmupApproachSet(
                        number = 1,
                        kind = WarmupSetKind.MOVEMENT_REHEARSAL,
                        repetitions = reps,
                        effortCue = "RIR 5+; fácil e longe da falha",
                        explanation = "A carga alvo é menor que o incremento seguro de arredondamento; use apenas um ensaio técnico.",
                        restAfterSeconds = 30,
                        estimatedExecutionSeconds = (reps * 4).coerceAtLeast(20),
                    ),
                ),
                "Não foi possível gerar uma carga intermediária menor que a carga de trabalho; nenhuma carga foi inventada.",
            )
        }
        return plan(priority, sets, explanation)
    }

    private fun loadedSets(input: WarmupExerciseInput, template: List<WarmupLoadStep>): List<WarmupApproachSet> {
        val increment = loadIncrement(input.targetLoadKg)
        val byLoad = linkedMapOf<Double, WarmupLoadStep>()
        template.forEach { step ->
            val rounded = floor((input.targetLoadKg * step.loadFraction + EPSILON) / increment) * increment
            if (rounded > 0.0 && rounded < input.targetLoadKg) byLoad[rounded.cleanLoad()] = step
        }
        val selected = byLoad.entries.sortedBy(Map.Entry<Double, WarmupLoadStep>::key)
        return selected.mapIndexed { index, (load, step) ->
            val reps = step.repetitions.coerceAtMost((input.targetRepMin + 3).coerceAtLeast(1))
            val last = index == selected.lastIndex
            WarmupApproachSet(
                number = index + 1,
                kind = WarmupSetKind.LOADED_APPROACH,
                loadKg = load,
                repetitions = reps,
                effortCue = "RIR 5+; fácil e longe da falha",
                explanation = "${(step.loadFraction * 100).roundToInt()}% da carga alvo, arredondados para baixo ao incremento disponível.",
                restAfterSeconds = if (last) 90 else if (index == 0) 45 else 60,
                estimatedExecutionSeconds = (reps * 4).coerceAtLeast(20),
            )
        }
    }

    private fun acclimationDuration(targetSeconds: Int): Int? {
        if (targetSeconds <= 0) return null
        return (targetSeconds * 0.40).roundToInt().coerceIn(1, 90).coerceAtMost(targetSeconds)
    }

    private fun acclimationDistance(targetMeters: Double): Double? {
        if (!targetMeters.isFinite() || targetMeters <= 0.0) return null
        val raw = targetMeters * 0.15
        val rounded = if (raw >= 5.0) floor(raw / 5.0) * 5.0 else raw
        return rounded.coerceAtLeast(minOf(1.0, targetMeters)).coerceAtMost(minOf(500.0, targetMeters)).cleanLoad()
    }

    private fun distanceExecutionSeconds(distanceMeters: Double): Int =
        (distanceMeters / 1.5).roundToInt().coerceIn(15, 180)

    private fun loadIncrement(targetLoadKg: Double): Double = when {
        targetLoadKg < 10.0 -> 0.5
        targetLoadKg < 50.0 -> 1.0
        else -> 2.5
    }

    private fun WarmupExerciseInput.hasExternalLoad(): Boolean = targetLoadKg.isFinite() && targetLoadKg > 0.0

    private fun WarmupExerciseInput.isExplicitWarmup(): Boolean = setType.normalized().contains("aquec")

    private fun WarmupExerciseInput.isTimeTracked(): Boolean = trackingMode.normalized().contains("tempo")

    private fun WarmupExerciseInput.isDistanceTracked(): Boolean = trackingMode.normalized().contains("dist")

    private fun WarmupExerciseInput.isMobility(): Boolean = category.normalized().contains("mobil")

    private fun WarmupExerciseInput.isCardio(): Boolean = category.normalized().contains("cardio")

    private fun WarmupExerciseInput.isResistanceExercise(): Boolean {
        val normalizedCategory = category.normalized()
        return normalizedCategory in RESISTANCE_CATEGORIES ||
            (!isCardio() && !isMobility() && !isTimeTracked() && !isDistanceTracked())
    }

    private fun WarmupExerciseInput.isCompound(): Boolean {
        val movement = movementType.normalized()
        return COMPOUND_ALIASES.any(movement::contains)
    }

    private fun WarmupExerciseInput.canonicalPattern(): String {
        val movement = movementType.normalized()
        return when {
            movement.contains("empurrar com pernas") || movement.contains("leg press") -> "squat"
            movement.contains("empurrar vertical") -> "vertical_push"
            movement.contains("empurrar") || movement.contains("aducao horizontal") -> "horizontal_push"
            movement.contains("puxar vertical") || movement.contains("extensao de ombro") -> "vertical_pull"
            movement.contains("puxar horizontal") || movement.contains("remar") -> "horizontal_pull"
            movement.contains("agachar") || movement.contains("subir degrau") || movement.contains("afundo") -> "squat"
            movement.contains("hinge") || movement.contains("levantamento") || movement.contains("extensao de quadril") -> "hip_hinge"
            movement.contains("flexao de joelho") -> "knee_flexion"
            movement.contains("extensao de joelho") -> "knee_extension"
            movement.contains("flexao de cotovelo") || muscleGroup.normalized().contains("biceps") -> "elbow_flexion"
            movement.contains("extensao de cotovelo") || muscleGroup.normalized().contains("triceps") -> "elbow_extension"
            movement.contains("panturr") || muscleGroup.normalized().contains("panturr") -> "calf"
            movement.contains("core") || muscleGroup.normalized().contains("core") || movement.contains("anti-") -> "core"
            isCardio() -> "cardio"
            isMobility() -> "mobility"
            else -> movement.substringBefore(" unilateral").ifBlank { muscleGroup.normalized().ifBlank { "general" } }
        }
    }

    private fun patternLabel(pattern: String): String = when (pattern) {
        "vertical_push" -> "empurrar vertical"
        "horizontal_push" -> "empurrar horizontal"
        "vertical_pull" -> "puxar vertical"
        "horizontal_pull" -> "puxar horizontal"
        "squat" -> "dominância de joelho/agachamento"
        "hip_hinge" -> "dominância de quadril"
        "knee_flexion" -> "flexão de joelho"
        "knee_extension" -> "extensão de joelho"
        "elbow_flexion" -> "flexão de cotovelo"
        "elbow_extension" -> "extensão de cotovelo"
        "calf" -> "panturrilhas"
        "core" -> "estabilidade do tronco"
        "cardio" -> "cardiorrespiratório"
        "mobility" -> "mobilidade"
        else -> pattern.replace('_', ' ')
    }

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()

    private fun Double.cleanLoad(): Double = (this * 100.0).roundToInt() / 100.0

    private companion object {
        const val EPSILON = 1e-9
        val RESISTANCE_CATEGORIES = setOf("musculacao", "peso corporal", "funcional")
        val COMPOUND_ALIASES = listOf(
            "empurrar", "puxar", "agachar", "hinge", "levantamento", "subir degrau",
            "carregar", "corpo inteiro", "remar", "pedalar e empurrar",
        )
        val UPPER_PATTERNS = setOf("vertical_push", "horizontal_push", "vertical_pull", "horizontal_pull", "elbow_flexion", "elbow_extension")
        val LOWER_PATTERNS = setOf("squat", "hip_hinge", "knee_flexion", "knee_extension", "calf")
    }
}

const val AUTOMATIC_WARMUP_DISCLAIMER: String =
    "Aquecimento é uma orientação geral, não uma avaliação individual. Reduza ou interrompa diante de dor, tontura ou mal-estar e adapte com um profissional quando necessário."
