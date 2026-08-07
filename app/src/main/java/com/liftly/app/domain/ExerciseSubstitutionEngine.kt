package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Pure, deterministic recommendation engine for replacing an exercise.
 *
 * It only uses metadata already stored in [ExerciseEntity]. The safety tier is a conservative
 * catalog classification, not a medical assessment. Callers should use
 * [SubstitutionSafetyPolicy.REQUIRE_SAME_OR_SAFER] when discomfort or reduced capacity is the
 * reason for the replacement.
 */
object ExerciseSubstitutionEngine {

    fun suggest(
        original: ExerciseEntity,
        catalog: List<ExerciseEntity>,
        options: ExerciseSubstitutionOptions = ExerciseSubstitutionOptions(),
    ): List<ExerciseSubstitution> {
        if (options.limit == 0) return emptyList()

        val originalSafety = safetyTier(original)
        val availableFamilies = options.availableEquipment
            .flatMapTo(linkedSetOf(), ::equipmentFamilyIds)
        val unavailableFamilies = options.unavailableEquipment
            .flatMapTo(linkedSetOf(), ::equipmentFamilyIds)

        return catalog.asSequence()
            .filterNot(ExerciseEntity::archived)
            .filter { it.id != original.id }
            .filter { it.id !in options.excludedExerciseIds }
            .distinctBy(ExerciseEntity::id)
            .filter { isMuscleRelevant(original, it, options.allowSecondaryMuscleMatches) }
            .filter {
                options.availableEquipment.isEmpty() ||
                    canUseWithAvailableEquipment(it.equipment, availableFamilies)
            }
            .filter {
                options.unavailableEquipment.isEmpty() ||
                    canUseWithoutUnavailableEquipment(it.equipment, unavailableFamilies)
            }
            .filter {
                options.safetyPolicy != SubstitutionSafetyPolicy.REQUIRE_SAME_OR_SAFER ||
                    safetyTier(it).ordinal <= originalSafety.ordinal
            }
            .map { candidate -> score(original, candidate, options.safetyPolicy) }
            .filter { it.score >= options.minimumScore }
            .sortedWith(
                compareByDescending<ExerciseSubstitution> { it.score }
                    .thenBy { it.safetyTier.ordinal }
                    .thenBy { it.exercise.name.searchKey() }
                    .thenBy { it.exercise.id },
            )
            .take(options.limit)
            .toList()
    }

    fun safetyTier(exercise: ExerciseEntity): SubstitutionSafetyTier {
        val category = exercise.category.searchKey()
        val movement = exercise.movementType.searchKey()
        val difficulty = difficultyRank(exercise.difficulty)
        val highDemandMovement = HIGH_DEMAND_MOVEMENT_MARKERS.any(movement::contains)
        val highDemandCategory = category in HIGH_DEMAND_CATEGORIES

        return when {
            difficulty >= 2 || highDemandCategory || highDemandMovement ->
                SubstitutionSafetyTier.ELEVATED
            difficulty == 1 -> SubstitutionSafetyTier.MODERATE
            else -> SubstitutionSafetyTier.STANDARD
        }
    }

    /** Canonical, user-facing families used by the live "equipment occupied" controls. */
    fun equipmentFamilyLabels(equipment: String): List<String> =
        equipmentFamilyIds(equipment).map { family ->
            EQUIPMENT_LABELS[family] ?: family
                .split('_')
                .joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
        }

    private fun score(
        original: ExerciseEntity,
        candidate: ExerciseEntity,
        safetyPolicy: SubstitutionSafetyPolicy,
    ): ExerciseSubstitution {
        val reasons = buildList {
            add(muscleReason(original, candidate))

            movementReason(original, candidate)?.let(::add)
            equipmentReason(original.equipment, candidate.equipment)?.let(::add)

            if (original.category.sameAs(candidate.category)) {
                add(reason(SubstitutionReasonCode.SAME_MODALITY, 20, "Mesma modalidade"))
            }

            val originalDifficulty = difficultyRank(original.difficulty)
            val candidateDifficulty = difficultyRank(candidate.difficulty)
            when {
                originalDifficulty == candidateDifficulty ->
                    add(reason(SubstitutionReasonCode.SAME_LEVEL, 14, "Mesmo nível"))
                candidateDifficulty < originalDifficulty ->
                    add(reason(SubstitutionReasonCode.EASIER_LEVEL, 8, "Nível mais acessível"))
                else -> add(
                    reason(
                        SubstitutionReasonCode.HARDER_LEVEL,
                        -8 * (candidateDifficulty - originalDifficulty),
                        "Nível mais exigente",
                    ),
                )
            }

            if (safetyPolicy != SubstitutionSafetyPolicy.IGNORE) {
                val originalSafety = safetyTier(original)
                val candidateSafety = safetyTier(candidate)
                when {
                    candidateSafety == originalSafety ->
                        add(reason(SubstitutionReasonCode.SAME_SAFETY_TIER, 8, "Demanda de segurança equivalente"))
                    candidateSafety.ordinal < originalSafety.ordinal ->
                        add(reason(SubstitutionReasonCode.SAFER_OPTION, 12, "Opção de menor demanda técnica"))
                    else -> add(
                        reason(
                            SubstitutionReasonCode.HIGHER_SAFETY_DEMAND,
                            -18 * (candidateSafety.ordinal - originalSafety.ordinal),
                            "Exige mais técnica ou controle",
                        ),
                    )
                }
            }

            if (original.trackingUnit.sameAs(candidate.trackingUnit)) {
                add(reason(SubstitutionReasonCode.SAME_TRACKING_UNIT, 5, "Mesmo tipo de registro"))
            }

            val secondaryOverlap = muscleTokens(original.secondaryMuscles)
                .intersect(muscleTokens(candidate.secondaryMuscles))
                .size
                .coerceAtMost(4)
            if (secondaryOverlap > 0) {
                add(
                    reason(
                        SubstitutionReasonCode.SECONDARY_MUSCLE_OVERLAP,
                        secondaryOverlap * 2,
                        "Também preserva músculos auxiliares",
                    ),
                )
            }
        }

        return ExerciseSubstitution(
            exercise = candidate,
            score = reasons.sumOf(ExerciseSubstitutionReason::points),
            reasons = reasons,
            safetyTier = safetyTier(candidate),
        )
    }

    private fun muscleReason(
        original: ExerciseEntity,
        candidate: ExerciseEntity,
    ): ExerciseSubstitutionReason {
        val originalPrimary = original.muscleGroup.searchKey()
        val candidatePrimary = candidate.muscleGroup.searchKey()
        return when {
            originalPrimary == candidatePrimary ->
                reason(SubstitutionReasonCode.SAME_PRIMARY_MUSCLE, 60, "Mesmo grupo muscular principal")
            originalPrimary in muscleTokens(candidate.secondaryMuscles) ->
                reason(SubstitutionReasonCode.PRIMARY_AS_SECONDARY, 32, "Mantém o músculo-alvo como auxiliar")
            else ->
                reason(SubstitutionReasonCode.RELATED_MUSCLE_TARGET, 24, "Trabalha um músculo relacionado")
        }
    }

    private fun movementReason(
        original: ExerciseEntity,
        candidate: ExerciseEntity,
    ): ExerciseSubstitutionReason? {
        val originalMovement = original.movementType.searchKey()
        val candidateMovement = candidate.movementType.searchKey()
        return when {
            originalMovement == candidateMovement ->
                reason(SubstitutionReasonCode.SAME_MOVEMENT, 30, "Mesmo padrão de movimento")
            movementFamily(originalMovement) == movementFamily(candidateMovement) ->
                reason(SubstitutionReasonCode.RELATED_MOVEMENT, 18, "Padrão de movimento equivalente")
            else -> null
        }
    }

    private fun equipmentReason(
        original: String,
        candidate: String,
    ): ExerciseSubstitutionReason? {
        if (original.sameAs(candidate)) {
            return reason(SubstitutionReasonCode.SAME_EQUIPMENT, 26, "Mesmo equipamento")
        }

        val originalVariants = equipmentVariants(original)
        val candidateVariants = equipmentVariants(candidate)
        val bestSimilarity = originalVariants.maxOf { source ->
            candidateVariants.maxOf { target ->
                if (source.isEmpty() && target.isEmpty()) {
                    1.0
                } else {
                    val union = source union target
                    if (union.isEmpty()) 0.0 else source.intersect(target).size.toDouble() / union.size
                }
            }
        }

        return when {
            bestSimilarity == 1.0 ->
                reason(SubstitutionReasonCode.EQUIVALENT_EQUIPMENT, 20, "Equipamento equivalente")
            bestSimilarity > 0.0 ->
                reason(SubstitutionReasonCode.COMPATIBLE_EQUIPMENT, 10, "Compartilha equipamentos")
            else -> null
        }
    }

    private fun isMuscleRelevant(
        original: ExerciseEntity,
        candidate: ExerciseEntity,
        allowSecondaryMatches: Boolean,
    ): Boolean {
        val originalPrimary = original.muscleGroup.searchKey()
        val candidatePrimary = candidate.muscleGroup.searchKey()
        if (originalPrimary == candidatePrimary) return true
        if (!allowSecondaryMatches) return false

        return originalPrimary in muscleTokens(candidate.secondaryMuscles) ||
            candidatePrimary in muscleTokens(original.secondaryMuscles)
    }

    private fun canUseWithAvailableEquipment(
        equipment: String,
        availableFamilies: Set<String>,
    ): Boolean = equipmentVariants(equipment).any { variant ->
        val requirements = variant - ALWAYS_AVAILABLE_EQUIPMENT
        requirements.isEmpty() || availableFamilies.containsAll(requirements)
    }

    private fun canUseWithoutUnavailableEquipment(
        equipment: String,
        unavailableFamilies: Set<String>,
    ): Boolean = equipmentVariants(equipment).any { variant ->
        (variant - ALWAYS_AVAILABLE_EQUIPMENT).none { it in unavailableFamilies }
    }

    private fun equipmentVariants(value: String): List<Set<String>> =
        value.searchKey()
            .split(" ou ")
            .map(::equipmentFamilyIds)
            .ifEmpty { listOf(emptySet()) }

    private fun equipmentFamilyIds(value: String): Set<String> {
        val normalized = value.searchKey()
        val families = linkedSetOf<String>()

        EQUIPMENT_MARKERS.forEach { (family, markers) ->
            if (markers.any(normalized::contains)) families += family
        }

        if (families.any { it in SPECIALIZED_MACHINE_FAMILIES }) families -= "machine"
        if (families.isEmpty() && normalized.isNotBlank()) families += normalized
        return families
    }

    private fun muscleTokens(value: String): Set<String> = value
        .split(',', '/', ';')
        .map { it.searchKey() }
        .filterTo(linkedSetOf(), String::isNotBlank)

    private fun movementFamily(value: String): String = value
        .replace(MOVEMENT_QUALIFIERS, "")
        .replace(WHITESPACE, " ")
        .trim()

    private fun difficultyRank(value: String): Int = when (value.searchKey()) {
        "iniciante" -> 0
        "intermediario" -> 1
        "avancado" -> 2
        else -> 1
    }

    private fun String.sameAs(other: String): Boolean = searchKey() == other.searchKey()

    private fun String.searchKey(): String = Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private fun reason(
        code: SubstitutionReasonCode,
        points: Int,
        label: String,
    ) = ExerciseSubstitutionReason(code = code, points = points, label = label)

    private val DIACRITICS = "\\p{M}+".toRegex()
    private val NON_ALPHANUMERIC = "[^a-z0-9]+".toRegex()
    private val WHITESPACE = "\\s+".toRegex()
    private val MOVEMENT_QUALIFIERS =
        "\\b(unilateral|bilateral|alternado|alternada|simultaneo|simultanea)\\b".toRegex()

    private val HIGH_DEMAND_CATEGORIES = setOf(
        "levantamento olimpico",
        "pliometria",
    )
    private val HIGH_DEMAND_MOVEMENT_MARKERS = setOf(
        "arremessar",
        "arranco",
        "clean",
        "jerk",
        "saltar",
        "salto",
        "sprint",
    )
    private val ALWAYS_AVAILABLE_EQUIPMENT = setOf("bodyweight", "none")
    private val SPECIALIZED_MACHINE_FAMILIES = setOf(
        "smith_machine",
        "hack_machine",
        "leg_press",
    )

    private val EQUIPMENT_MARKERS: List<Pair<String, Set<String>>> = listOf(
        "bodyweight" to setOf("peso corporal"),
        "none" to setOf("sem equipamento"),
        "dumbbell" to setOf("halter"),
        "kettlebell" to setOf("kettlebell"),
        "pull_up_bar" to setOf("barra fixa"),
        "parallel_bars" to setOf("barras paralelas"),
        "t_bar" to setOf("barra t"),
        "barbell" to setOf("barra", "anilha", "landmine"),
        "cable" to setOf("polia", "cabo"),
        "smith_machine" to setOf("smith"),
        "hack_machine" to setOf("maquina hack"),
        "leg_press" to setOf("leg press"),
        "machine" to setOf("maquina", "peck deck"),
        "bench" to setOf("banco"),
        "rack" to setOf("rack"),
        "band" to setOf("faixa elastica"),
        "suspension" to setOf("fitas de suspensao"),
        "medicine_ball" to setOf("bola medicinal"),
        "swiss_ball" to setOf("bola suica"),
        "plyo_box" to setOf("caixa pliometrica"),
        "step" to setOf("step", "degrau"),
        "wall" to setOf("parede", "batente de porta"),
        "sled" to setOf("treno"),
        "rope" to setOf("corda"),
        "bike" to setOf("bicicleta", "air bike"),
        "treadmill" to setOf("esteira"),
        "rowing_machine" to setOf("remo ergometrico"),
        "ski_erg" to setOf("skierg"),
        "elliptical" to setOf("eliptico"),
        "pool" to setOf("piscina"),
    )
    private val EQUIPMENT_LABELS = mapOf(
        "bodyweight" to "Peso corporal",
        "none" to "Sem equipamento",
        "dumbbell" to "Halteres",
        "kettlebell" to "Kettlebell",
        "pull_up_bar" to "Barra fixa",
        "parallel_bars" to "Barras paralelas",
        "t_bar" to "Barra T",
        "barbell" to "Barra/anilhas",
        "cable" to "Polia/cabo",
        "smith_machine" to "Máquina Smith",
        "hack_machine" to "Máquina hack",
        "leg_press" to "Leg press",
        "machine" to "Máquina",
        "bench" to "Banco",
        "rack" to "Rack",
        "band" to "Faixa elástica",
        "suspension" to "Fitas de suspensão",
        "medicine_ball" to "Bola medicinal",
        "swiss_ball" to "Bola suíça",
        "plyo_box" to "Caixa pliométrica",
        "step" to "Step/degrau",
        "wall" to "Parede",
        "sled" to "Trenó",
        "rope" to "Corda",
        "bike" to "Bicicleta",
        "treadmill" to "Esteira",
        "rowing_machine" to "Remo ergométrico",
        "ski_erg" to "SkiErg",
        "elliptical" to "Elíptico",
        "pool" to "Piscina",
    )
}

data class ExerciseSubstitutionOptions(
    val limit: Int = 5,
    /**
     * Human-readable equipment names, for example `setOf("Halteres", "Banco")`.
     * Empty means that equipment availability is unknown and should not filter results.
     */
    val availableEquipment: Set<String> = emptySet(),
    /** Equipment temporarily unavailable in the current gym session. */
    val unavailableEquipment: Set<String> = emptySet(),
    val excludedExerciseIds: Set<String> = emptySet(),
    val allowSecondaryMuscleMatches: Boolean = false,
    val safetyPolicy: SubstitutionSafetyPolicy =
        SubstitutionSafetyPolicy.PREFER_SAME_OR_SAFER,
    val minimumScore: Int = 1,
) {
    init {
        require(limit >= 0) { "limit must be non-negative" }
    }
}

data class ExerciseSubstitution(
    val exercise: ExerciseEntity,
    val score: Int,
    val reasons: List<ExerciseSubstitutionReason>,
    val safetyTier: SubstitutionSafetyTier,
)

data class ExerciseSubstitutionReason(
    val code: SubstitutionReasonCode,
    val points: Int,
    val label: String,
)

enum class SubstitutionSafetyPolicy {
    PREFER_SAME_OR_SAFER,
    REQUIRE_SAME_OR_SAFER,
    IGNORE,
}

enum class SubstitutionSafetyTier {
    STANDARD,
    MODERATE,
    ELEVATED,
}

enum class SubstitutionReasonCode {
    SAME_PRIMARY_MUSCLE,
    PRIMARY_AS_SECONDARY,
    RELATED_MUSCLE_TARGET,
    SAME_MOVEMENT,
    RELATED_MOVEMENT,
    SAME_EQUIPMENT,
    EQUIVALENT_EQUIPMENT,
    COMPATIBLE_EQUIPMENT,
    SAME_MODALITY,
    SAME_LEVEL,
    EASIER_LEVEL,
    HARDER_LEVEL,
    SAME_SAFETY_TIER,
    SAFER_OPTION,
    HIGHER_SAFETY_DEMAND,
    SAME_TRACKING_UNIT,
    SECONDARY_MUSCLE_OVERLAP,
}
