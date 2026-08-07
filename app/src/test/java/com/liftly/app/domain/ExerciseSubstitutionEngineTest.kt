package com.liftly.app.domain

import com.liftly.app.data.ExerciseCatalog
import com.liftly.app.data.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSubstitutionEngineTest {

    @Test
    fun excludesOriginalArchivedAndExplicitlyExcludedExercises() {
        val original = exercise(id = "source")
        val archived = exercise(id = "archived", archived = true)
        val excluded = exercise(id = "excluded")
        val valid = exercise(id = "valid")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original = original,
            catalog = listOf(original, archived, excluded, valid),
            options = ExerciseSubstitutionOptions(excludedExerciseIds = setOf(excluded.id)),
        )

        assertEquals(listOf("valid"), suggestions.map { it.exercise.id })
    }

    @Test
    fun duplicateCatalogRowsNeverProduceDuplicateSuggestions() {
        val original = exercise(id = "source")
        val candidate = exercise(id = "candidate")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(candidate, candidate.copy(name = "Nome repetido")),
        )

        assertEquals(listOf("candidate"), suggestions.map { it.exercise.id })
    }

    @Test
    fun exactMovementAndEquivalentEquipmentWinWithinSameMuscle() {
        val original = exercise(
            id = "source",
            equipment = "Halteres e banco",
            movement = "Empurrar horizontal",
        )
        val samePattern = exercise(
            id = "same-pattern",
            equipment = "Halter e banco",
            movement = "Empurrar horizontal",
        )
        val differentPattern = exercise(
            id = "different-pattern",
            equipment = "Halteres e banco",
            movement = "Adução horizontal",
        )

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(differentPattern, samePattern),
        )

        assertEquals("same-pattern", suggestions.first().exercise.id)
        assertTrue(suggestions.first().hasReason(SubstitutionReasonCode.SAME_MOVEMENT))
        assertTrue(suggestions.first().hasReason(SubstitutionReasonCode.EQUIVALENT_EQUIPMENT))
    }

    @Test
    fun modalityAndLevelAffectRankingAndAreExplained() {
        val original = exercise(id = "source", category = "Musculação", difficulty = "Intermediário")
        val matching = exercise(
            id = "matching",
            category = "MUSCULACAO",
            difficulty = "INTERMEDIARIO",
        )
        val other = exercise(id = "other", category = "Funcional", difficulty = "Avançado")

        val suggestions = ExerciseSubstitutionEngine.suggest(original, listOf(other, matching))

        assertEquals("matching", suggestions.first().exercise.id)
        assertTrue(suggestions.first().hasReason(SubstitutionReasonCode.SAME_MODALITY))
        assertTrue(suggestions.first().hasReason(SubstitutionReasonCode.SAME_LEVEL))
        assertTrue(
            suggestions.first().score ==
                suggestions.first().reasons.sumOf(ExerciseSubstitutionReason::points),
        )
    }

    @Test
    fun defaultRequiresSamePrimaryMuscle() {
        val original = exercise(
            id = "source",
            muscle = "Peito",
            secondary = "Tríceps",
        )
        val triceps = exercise(
            id = "triceps",
            muscle = "Tríceps",
            secondary = "Peito",
        )

        assertTrue(
            ExerciseSubstitutionEngine.suggest(original, listOf(triceps)).isEmpty(),
        )
    }

    @Test
    fun secondaryMuscleMatchesCanBeEnabledExplicitly() {
        val original = exercise(
            id = "source",
            muscle = "Peito",
            secondary = "Tríceps",
        )
        val triceps = exercise(
            id = "triceps",
            muscle = "Tríceps",
            secondary = "Peito, deltoide anterior",
        )

        val result = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(triceps),
            ExerciseSubstitutionOptions(allowSecondaryMuscleMatches = true),
        ).single()

        assertTrue(result.hasReason(SubstitutionReasonCode.PRIMARY_AS_SECONDARY))
    }

    @Test
    fun strictSafetyPolicyRejectsMoreDemandingCandidates() {
        val original = exercise(id = "source", difficulty = "Iniciante")
        val beginner = exercise(id = "beginner", difficulty = "Iniciante")
        val advanced = exercise(id = "advanced", difficulty = "Avançado")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(advanced, beginner),
            ExerciseSubstitutionOptions(
                safetyPolicy = SubstitutionSafetyPolicy.REQUIRE_SAME_OR_SAFER,
            ),
        )

        assertEquals(listOf("beginner"), suggestions.map { it.exercise.id })
    }

    @Test
    fun elevatedCategoryIsClassifiedIndependentlyOfBeginnerLabel() {
        val plyometric = exercise(
            id = "jump",
            difficulty = "Iniciante",
            category = "Pliometria",
            movement = "Saltar",
        )

        assertEquals(
            SubstitutionSafetyTier.ELEVATED,
            ExerciseSubstitutionEngine.safetyTier(plyometric),
        )
    }

    @Test
    fun saferCandidateGetsPositiveReasonAndOutranksHarderCandidate() {
        val original = exercise(id = "source", difficulty = "Intermediário")
        val safer = exercise(id = "safer", difficulty = "Iniciante")
        val harder = exercise(id = "harder", difficulty = "Avançado")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(harder, safer),
        )

        assertEquals("safer", suggestions.first().exercise.id)
        assertTrue(suggestions.first().hasReason(SubstitutionReasonCode.SAFER_OPTION))
        assertTrue(
            suggestions.last().hasReason(SubstitutionReasonCode.HIGHER_SAFETY_DEMAND),
        )
    }

    @Test
    fun availableEquipmentFiltersCandidatesAndKeepsBodyweightOptions() {
        val original = exercise(id = "source", equipment = "Barra e banco")
        val dumbbells = exercise(id = "dumbbells", equipment = "Halteres e banco")
        val machine = exercise(id = "machine", equipment = "Máquina")
        val bodyweight = exercise(id = "bodyweight", equipment = "Peso corporal")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(machine, bodyweight, dumbbells),
            ExerciseSubstitutionOptions(availableEquipment = setOf("Banco", "Halteres")),
        )

        assertEquals(setOf("dumbbells", "bodyweight"), suggestions.map { it.exercise.id }.toSet())
        assertFalse(suggestions.any { it.exercise.id == "machine" })
    }

    @Test
    fun alternativeEquipmentDescriptorAcceptsEitherAvailableOption() {
        val original = exercise(id = "source", equipment = "Barra")
        val alternative = exercise(id = "alternative", equipment = "Halter ou kettlebell")

        val withKettlebell = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(alternative),
            ExerciseSubstitutionOptions(availableEquipment = setOf("Kettlebell")),
        )

        assertEquals("alternative", withKettlebell.single().exercise.id)
    }

    @Test
    fun unavailableEquipmentRejectsRequiredComponentsButKeepsUsableAlternatives() {
        val original = exercise(id = "source", equipment = "Barra e banco")
        val needsBench = exercise(id = "bench", equipment = "Halteres e banco")
        val freeWeights = exercise(id = "free", equipment = "Halter ou kettlebell")
        val bodyweight = exercise(id = "bodyweight", equipment = "Peso corporal")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(needsBench, freeWeights, bodyweight),
            ExerciseSubstitutionOptions(unavailableEquipment = setOf("Banco")),
        )

        assertFalse(suggestions.any { it.exercise.id == needsBench.id })
        assertEquals(setOf("free", "bodyweight"), suggestions.map { it.exercise.id }.toSet())
    }

    @Test
    fun oneFreeVariantKeepsAlternativeEquipmentDescriptorAvailable() {
        val original = exercise(id = "source")
        val alternative = exercise(id = "alternative", equipment = "Halter ou kettlebell")

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            listOf(alternative),
            ExerciseSubstitutionOptions(unavailableEquipment = setOf("Halteres")),
        )

        assertEquals("alternative", suggestions.single().exercise.id)
    }

    @Test
    fun equipmentFamilyLabelsAreCanonicalAndAvoidGenericMachineDuplicates() {
        assertEquals(
            listOf("Máquina Smith"),
            ExerciseSubstitutionEngine.equipmentFamilyLabels("Máquina Smith"),
        )
        assertEquals(
            listOf("Halteres", "Banco"),
            ExerciseSubstitutionEngine.equipmentFamilyLabels("Halteres e banco"),
        )
    }

    @Test
    fun comparisonsAreAccentAndCaseInsensitive() {
        val original = exercise(
            id = "source",
            muscle = "Bíceps",
            category = "Musculação",
            difficulty = "Intermediário",
        )
        val candidate = exercise(
            id = "candidate",
            muscle = "BICEPS",
            category = "MUSCULACAO",
            difficulty = "INTERMEDIARIO",
        )

        val result = ExerciseSubstitutionEngine.suggest(original, listOf(candidate)).single()

        assertTrue(result.hasReason(SubstitutionReasonCode.SAME_PRIMARY_MUSCLE))
        assertTrue(result.hasReason(SubstitutionReasonCode.SAME_MODALITY))
        assertTrue(result.hasReason(SubstitutionReasonCode.SAME_LEVEL))
    }

    @Test
    fun orderingIsDeterministicForTiesRegardlessOfCatalogOrder() {
        val original = exercise(id = "source")
        val alpha = exercise(id = "z-id", name = "Ágata")
        val beta = exercise(id = "a-id", name = "Beta")

        val firstRun = ExerciseSubstitutionEngine.suggest(original, listOf(beta, alpha))
        val secondRun = ExerciseSubstitutionEngine.suggest(original, listOf(alpha, beta))

        assertEquals(listOf("z-id", "a-id"), firstRun.map { it.exercise.id })
        assertEquals(firstRun.map { it.exercise.id }, secondRun.map { it.exercise.id })
    }

    @Test
    fun respectsLimitAndZeroLimitAvoidsWork() {
        val original = exercise(id = "source")
        val candidates = (1..10).map { exercise(id = "candidate-$it", name = "Exercício $it") }

        assertEquals(
            3,
            ExerciseSubstitutionEngine.suggest(
                original,
                candidates,
                ExerciseSubstitutionOptions(limit = 3),
            ).size,
        )
        assertTrue(
            ExerciseSubstitutionEngine.suggest(
                original,
                candidates,
                ExerciseSubstitutionOptions(limit = 0),
            ).isEmpty(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLimit() {
        ExerciseSubstitutionOptions(limit = -1)
    }

    @Test
    fun realCatalogProducesRelevantSupinoAlternatives() {
        val original = ExerciseCatalog.exercises.first { it.id == "builtin.supino_reto_barra" }

        val suggestions = ExerciseSubstitutionEngine.suggest(
            original,
            ExerciseCatalog.exercises,
            ExerciseSubstitutionOptions(limit = 8),
        )

        assertEquals(8, suggestions.size)
        assertTrue(suggestions.none { it.exercise.id == original.id })
        assertTrue(suggestions.all { it.exercise.muscleGroup == original.muscleGroup })
        assertTrue(suggestions.zipWithNext().all { (left, right) -> left.score >= right.score })
    }

    private fun ExerciseSubstitution.hasReason(code: SubstitutionReasonCode): Boolean =
        reasons.any { it.code == code }

    private fun exercise(
        id: String,
        name: String = id,
        muscle: String = "Peito",
        secondary: String = "Tríceps, deltoide anterior",
        equipment: String = "Barra e banco",
        difficulty: String = "Intermediário",
        movement: String = "Empurrar horizontal",
        category: String = "Musculação",
        trackingUnit: String = "kg",
        archived: Boolean = false,
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscle,
        secondaryMuscles = secondary,
        equipment = equipment,
        difficulty = difficulty,
        movementType = movement,
        category = category,
        instructions = "Execução controlada.",
        cautions = "Interrompa em caso de dor.",
        trackingUnit = trackingUnit,
        archived = archived,
    )
}
