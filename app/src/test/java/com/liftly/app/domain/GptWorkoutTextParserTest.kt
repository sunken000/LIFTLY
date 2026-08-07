package com.liftly.app.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GptWorkoutTextParserTest {
    @Test
    fun `parses common GPT markdown and preserves workout and exercise order`() {
        val result = GptWorkoutTextParser.parse(
            """
            ## Treino A — Peito e tríceps — Segunda-feira
            1. **Supino reto** — 4 x 8–12 | 80 kg | descanso 90 s | RIR 2
            2. Crucifixo inclinado — 3x12 | 14 kg | 60s

            ## Treino B — Costas — quinta
            - Puxada alta: 3 séries de 10 a 12 repetições, carga 55 kg, intervalo 2 min
            - Remada baixa: 4 x 8
            """.trimIndent(),
        )

        assertTrue(result.canImport)
        assertEquals(2, result.workouts.size)
        assertEquals(setOf(DayOfWeek.MONDAY), result.workouts[0].weekDays)
        assertEquals(setOf(DayOfWeek.THURSDAY), result.workouts[1].weekDays)
        assertEquals(listOf("Supino reto", "Crucifixo inclinado"), result.workouts[0].exercises.map { it.name })
        assertEquals(listOf("Puxada alta", "Remada baixa"), result.workouts[1].exercises.map { it.name })

        val supino = result.workouts[0].exercises.first()
        assertEquals(4, supino.sets)
        assertEquals(8, supino.repMin)
        assertEquals(12, supino.repMax)
        assertEquals(80.0, supino.loadKg!!, 0.001)
        assertEquals(90, supino.restSeconds)
        assertEquals(2, supino.rir)
        assertEquals(60, result.workouts[0].exercises[1].restSeconds)
        assertEquals(120, result.workouts[1].exercises[0].restSeconds)
    }

    @Test
    fun `parses exercise followed by labeled metadata lines`() {
        val result = GptWorkoutTextParser.parse(
            """
            Treino C - Pernas
            - Agachamento livre
              - Séries: 5
              - Repetições: 5-8
              - Carga: 92,5 kg
              - Descanso: 3 minutos
              - RIR: 1
              - Tipo de série: normal
              - Observações: manter a coluna neutra
            """.trimIndent(),
        )

        val exercise = result.workouts.single().exercises.single()
        assertEquals("Agachamento livre", exercise.name)
        assertEquals(5, exercise.sets)
        assertEquals(5, exercise.repMin)
        assertEquals(8, exercise.repMax)
        assertEquals(92.5, exercise.loadKg!!, 0.001)
        assertEquals(180, exercise.restSeconds)
        assertEquals(1, exercise.rir)
        assertEquals(ParsedSetType.NORMAL, exercise.setType)
        assertEquals("manter a coluna neutra", exercise.notes)
    }

    @Test
    fun `parses markdown table`() {
        val result = GptWorkoutTextParser.parse(
            """
            ### Sexta — Treino de superiores
            | Exercício | Séries | Reps | Carga | Descanso | RIR | Tipo | Observações |
            |---|---:|---:|---:|---:|---:|---|---|
            | Desenvolvimento | 3 | 8–10 | 24 kg | 90 s | 2 | Aquecimento | sem dor |
            | Elevação lateral | 4 | 12 | 8 kg | 1 min | 3 | Drop set | última série |
            """.trimIndent(),
        )

        val exercises = result.workouts.single().exercises
        assertEquals(2, exercises.size)
        assertEquals(ParsedSetType.WARM_UP, exercises[0].setType)
        assertEquals(ParsedSetType.DROP_SET, exercises[1].setType)
        assertEquals("última série", exercises[1].notes)
        assertEquals(60, exercises[1].restSeconds)
    }

    @Test
    fun `does not invent omitted values and emits preview warning`() {
        val result = GptWorkoutTextParser.parse(
            """
            Treino livre
            - Barra fixa
            - Flexão de braços — 3 x 15
            """.trimIndent(),
        )

        val barra = result.workouts.single().exercises.first()
        assertNull(barra.sets)
        assertNull(barra.repMin)
        assertNull(barra.loadKg)
        assertNull(barra.restSeconds)
        assertNull(barra.rir)
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INCOMPLETE_EXERCISE })
    }

    @Test
    fun `rejects unsafe or impossible numeric values instead of clamping`() {
        val result = GptWorkoutTextParser.parse(
            """
            Treino teste
            - Supino reto — 25 x 120 | 2500 kg | descanso 90 min | RIR 15
            """.trimIndent(),
        )

        val exercise = result.workouts.single().exercises.single()
        assertNull(exercise.sets)
        assertNull(exercise.repMin)
        assertNull(exercise.loadKg)
        assertNull(exercise.restSeconds)
        assertNull(exercise.rir)
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INVALID_SETS })
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INVALID_REPS })
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INVALID_LOAD })
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INVALID_REST })
        assertTrue(result.warnings.any { it.code == WorkoutTextWarningCode.INVALID_RIR })
    }

    @Test
    fun `recognizes set variants and notes in inline format`() {
        val result = GptWorkoutTextParser.parse(
            """
            Treino A
            - Cadeira extensora — 3x12 | 40 kg | dropset | obs: reduzir carga em 20%
            - Supino inclinado — 2x15 | aquecimento
            - Rosca direta — 3x10 | até a falha
            - Crucifixo + flexão — 3x12 | bi-set
            """.trimIndent(),
        )

        val exercises = result.workouts.single().exercises
        assertEquals(ParsedSetType.DROP_SET, exercises[0].setType)
        assertEquals("reduzir carga em 20%", exercises[0].notes)
        assertEquals(ParsedSetType.WARM_UP, exercises[1].setType)
        assertEquals(ParsedSetType.FAILURE, exercises[2].setType)
        assertEquals(ParsedSetType.SUPER_SET, exercises[3].setType)
    }

    @Test
    fun `supports prescription before exercise name`() {
        val result = GptWorkoutTextParser.parse(
            """
            Treino rápido
            - 3x8-12 Supino com halteres
            - 4x15 Elevação lateral
            """.trimIndent(),
        )

        val exercises = result.workouts.single().exercises
        assertEquals("Supino com halteres", exercises[0].name)
        assertEquals(3, exercises[0].sets)
        assertEquals(8, exercises[0].repMin)
        assertEquals(12, exercises[0].repMax)
        assertEquals("Elevação lateral", exercises[1].name)
        assertEquals(15, exercises[1].repMax)
    }

    @Test
    fun `standalone weekdays enrich current workout instead of creating another one`() {
        val result = GptWorkoutTextParser.parse(
            """
            ## Treino A — Peito
            Segunda e quinta
            - Supino reto — 3x8-12
            """.trimIndent(),
        )

        assertEquals(1, result.workouts.size)
        assertEquals("Treino A — Peito", result.workouts.single().name)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), result.workouts.single().weekDays)
        assertEquals("Supino reto", result.workouts.single().exercises.single().name)
    }

    @Test
    fun `empty input cannot be imported`() {
        val result = GptWorkoutTextParser.parse("   ")

        assertFalse(result.canImport)
        assertTrue(result.workouts.isEmpty())
        assertEquals(WorkoutTextWarningCode.EMPTY_INPUT, result.warnings.single().code)
    }
}
