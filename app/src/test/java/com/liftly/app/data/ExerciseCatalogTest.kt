package com.liftly.app.data

import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    private val exercises = ExerciseCatalog.exercises

    @Test
    fun catalogIsBroadAndKeepsStableBuiltInIds() {
        assertTrue("O catálogo deve ter pelo menos 250 exercícios", exercises.size >= 250)
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertTrue(exercises.all { it.id.startsWith("builtin.") })
        assertTrue(exercises.none { it.isCustom || it.archived })
    }

    @Test
    fun normalizedExerciseNamesAreUnique() {
        val normalizedNames = exercises.map { normalize(it.name) }

        assertEquals(normalizedNames.size, normalizedNames.distinct().size)
    }

    @Test
    fun everyExerciseHasValidSearchAndTrackingMetadata() {
        val supportedDifficulty = setOf("Iniciante", "Intermediário", "Avançado")
        val supportedTrackingUnits = setOf("kg", "repetições", "tempo", "distância")

        exercises.forEach { exercise ->
            assertTrue(exercise.name.isNotBlank())
            assertTrue(exercise.muscleGroup.isNotBlank())
            assertTrue(exercise.equipment.isNotBlank())
            assertTrue(exercise.movementType.isNotBlank())
            assertTrue(exercise.category.isNotBlank())
            assertTrue(exercise.instructions.isNotBlank())
            assertTrue(exercise.cautions.isNotBlank())
            assertTrue(exercise.difficulty in supportedDifficulty)
            assertTrue(exercise.trackingUnit in supportedTrackingUnits)
        }
    }

    @Test
    fun catalogCoversMajorTrainingFamilies() {
        val categories = exercises.map { it.category }.toSet()

        assertTrue("Musculação" in categories)
        assertTrue("Peso corporal" in categories)
        assertTrue("Funcional" in categories)
        assertTrue("Cardio" in categories)
        assertTrue("Mobilidade" in categories)
        assertTrue("Levantamento olímpico" in categories)
        assertTrue("Pliometria" in categories)
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
