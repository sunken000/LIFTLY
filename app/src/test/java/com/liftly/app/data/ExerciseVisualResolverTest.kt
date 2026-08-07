package com.liftly.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseVisualResolverTest {
    @Test
    fun `every built in exercise receives a safe stable asset name and fallback`() {
        val firstPass = ExerciseCatalog.exercises.map(ExerciseVisualResolver::resolve)
        val secondPass = ExerciseCatalog.exercises.map(ExerciseVisualResolver::resolve)

        assertEquals(firstPass, secondPass)
        assertEquals(ExerciseCatalog.exercises.size, firstPass.size)
        firstPass.forEach { spec ->
            val builtIn = spec as ExerciseVisualSpec.BundledOrFallback
            assertTrue(builtIn.drawableName.matches("exercise_[a-z0-9_]+".toRegex()))
        }
        assertEquals(
            ExerciseCatalog.exercises.size,
            firstPass.map { (it as ExerciseVisualSpec.BundledOrFallback).drawableName }.distinct().size,
        )
    }

    @Test
    fun `movement takes precedence and resolves common visual families`() {
        assertEquals(ExerciseVisualKey.HORIZONTAL_PUSH, key("Peito", "Empurrar horizontal", "Musculação"))
        assertEquals(ExerciseVisualKey.VERTICAL_PULL, key("Costas", "Puxar vertical", "Musculação"))
        assertEquals(ExerciseVisualKey.SQUAT, key("Quadríceps", "Agachar", "Musculação"))
        assertEquals(ExerciseVisualKey.HIP_HINGE, key("Posteriores", "Hinge de quadril", "Musculação"))
        assertEquals(ExerciseVisualKey.CARDIO, key("Pernas", "Correr", "Cardio"))
        assertEquals(ExerciseVisualKey.MOBILITY, key("Quadril", "Rotação", "Mobilidade"))
    }

    @Test
    fun `accented metadata resolves identically`() {
        assertEquals(
            key("Bíceps", "Flexão de cotovelo", "Musculação"),
            key("Biceps", "Flexao de cotovelo", "Musculacao"),
        )
    }

    @Test
    fun `custom image uri is preserved verbatim after surrounding trim`() {
        val exercise = exercise(
            id = "custom.1",
            muscle = "Peito",
            movement = "Empurrar horizontal",
            category = "Musculação",
            isCustom = true,
            imageUri = "  content://media/exercise/42  ",
        )

        val result = ExerciseVisualResolver.resolve(exercise) as ExerciseVisualSpec.LocalImage

        assertEquals("content://media/exercise/42", result.uri)
        assertEquals(ExerciseVisualKey.HORIZONTAL_PUSH, result.fallbackKey)
    }

    @Test
    fun `custom exercise without image receives generated fallback`() {
        val result = ExerciseVisualResolver.resolve(
            exercise("custom.2", "Core", "Anti-rotação", "Funcional", isCustom = true),
        )

        assertTrue(result is ExerciseVisualSpec.GeneratedFallback)
        assertEquals(ExerciseVisualKey.CORE, result.fallbackKey)
    }

    @Test
    fun `stable ids map to distinct drawable names`() {
        val first = ExerciseVisualResolver.drawableNameFor("builtin.supino-reto")
        val second = ExerciseVisualResolver.drawableNameFor("builtin.supino_inclinado")

        assertEquals("exercise_supino_reto", first)
        assertNotEquals(first, second)
    }

    private fun key(muscle: String, movement: String, category: String): ExerciseVisualKey =
        ExerciseVisualResolver.fallbackKeyFor(exercise("builtin.test", muscle, movement, category))

    private fun exercise(
        id: String,
        muscle: String,
        movement: String,
        category: String,
        isCustom: Boolean = false,
        imageUri: String? = null,
    ) = ExerciseEntity(
        id = id,
        name = "Exercício teste",
        muscleGroup = muscle,
        equipment = "Equipamento",
        difficulty = "Iniciante",
        movementType = movement,
        category = category,
        instructions = "",
        cautions = "",
        isCustom = isCustom,
        imageUri = imageUri,
    )
}
