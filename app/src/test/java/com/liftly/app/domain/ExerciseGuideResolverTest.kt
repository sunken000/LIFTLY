package com.liftly.app.domain

import com.liftly.app.data.ExerciseCatalog
import com.liftly.app.data.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGuideResolverTest {
    @Test
    fun everyBuiltInExerciseReceivesACompleteDeterministicGuide() {
        val first = ExerciseCatalog.exercises.map(ExerciseGuideResolver::resolve)
        val second = ExerciseCatalog.exercises.map(ExerciseGuideResolver::resolve)

        assertEquals(first, second)
        assertEquals(ExerciseCatalog.exercises.map { it.id }, first.map { it.exerciseId })
        first.forEach { guide ->
            assertTrue(guide.steps.size >= 2)
            assertTrue(guide.steps.all(String::isNotBlank))
            assertTrue(guide.primaryMuscle.isNotBlank())
            assertTrue(guide.commonMistakes.size >= 2)
            assertTrue(guide.commonMistakes.all(String::isNotBlank))
            assertTrue(guide.postureTips.size >= 2)
            assertTrue(guide.postureTips.all(String::isNotBlank))
        }
    }

    @Test
    fun usesExerciseSpecificMetadataAlongsideMovementTemplate() {
        val exercise = ExerciseEntity(
            id = "custom.guide",
            name = "Teste de remada",
            muscleGroup = "Costas",
            secondaryMuscles = "Bíceps, deltoide posterior",
            equipment = "Cabo",
            difficulty = "Iniciante",
            movementType = "Puxar horizontal",
            category = "Musculação",
            instructions = "Puxe a manopla em direção ao abdômen.",
            cautions = "Evite balançar o tronco.",
        )

        val guide = ExerciseGuideResolver.resolve(exercise)

        assertEquals(ExerciseMovementFamily.HORIZONTAL_PULL, guide.movementFamily)
        assertEquals(listOf("Bíceps", "deltoide posterior"), guide.secondaryMuscles)
        assertTrue(guide.steps.any { it.contains("abdômen") })
        assertTrue(guide.commonMistakes.any { it.contains("balançar") })
    }
}
