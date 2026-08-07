package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.WorkoutEntity
import com.liftly.app.data.WorkoutExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAnalyzerTest {
    private val analyzer = WorkoutAnalyzer()

    @Test
    fun `detecta exercicio repetido uma unica vez`() {
        val workout = workout()
        val exercise = exercise(id = "supino", name = "Supino", movement = "Empurrar horizontal")
        val items = listOf(
            item("item-1", exercise.id, order = 0),
            item("item-2", exercise.id, order = 1),
            item("item-3", exercise.id, order = 2),
        )

        val suggestions = analyzer.analyze(workout, items, listOf(exercise))

        assertEquals(1, suggestions.count { it.code == WorkoutSuggestionCode.DUPLICATE_EXERCISE })
        assertTrue(suggestions.first { it.code == WorkoutSuggestionCode.DUPLICATE_EXERCISE }.action is WorkoutSuggestionAction.RemoveExercise)
    }

    @Test
    fun `aquecimento nao entra no volume do grupo`() {
        val workout = workout()
        val exercises = (1..5).map {
            exercise(id = "peito-$it", name = "Peito $it", movement = "Adução horizontal")
        }
        val items = listOf(
            item("aquecimento", exercises[0].id, 0, sets = 20, setType = "Aquecimento"),
            item("trabalho-1", exercises[1].id, 1, sets = 4),
            item("trabalho-2", exercises[2].id, 2, sets = 4),
            item("trabalho-3", exercises[3].id, 3, sets = 4),
        )

        val suggestions = analyzer.analyze(workout, items, exercises)

        assertFalse(suggestions.any { it.code == WorkoutSuggestionCode.HIGH_MUSCLE_VOLUME })
        assertFalse(suggestions.any { it.code == WorkoutSuggestionCode.HIGH_TOTAL_VOLUME })
    }

    @Test
    fun `volume alerta somente acima do limite`() {
        val workout = workout()
        val exercises = listOf(
            exercise("e1", "Crucifixo 1", movement = "Adução horizontal"),
            exercise("e2", "Crucifixo 2", movement = "Adução inclinada"),
            exercise("e3", "Crucifixo 3", movement = "Flexão de ombro"),
            exercise("e4", "Crucifixo 4", movement = "Abdução horizontal"),
        )
        val twelveSets = exercises.take(3).mapIndexed { index, exercise ->
            item("item-$index", exercise.id, index, sets = 4)
        }
        assertFalse(analyzer.analyze(workout, twelveSets, exercises).any { it.code == WorkoutSuggestionCode.HIGH_MUSCLE_VOLUME })

        val thirteenSets = twelveSets + item("item-3", exercises[3].id, 3, sets = 1)
        assertTrue(analyzer.analyze(workout, thirteenSets, exercises).any { it.code == WorkoutSuggestionCode.HIGH_MUSCLE_VOLUME })
    }

    @Test
    fun `sugere composto antes de isolador relacionado`() {
        val workout = workout()
        val curl = exercise("curl", "Rosca", muscle = "Bíceps", movement = "Flexão de cotovelo")
        val row = exercise(
            id = "row",
            name = "Remada",
            muscle = "Costas",
            secondary = "Bíceps, deltoide posterior",
            movement = "Puxar horizontal",
        )
        val items = listOf(item("curl-item", curl.id, 0), item("row-item", row.id, 1))

        val suggestion = analyzer.analyze(workout, items, listOf(curl, row))
            .single { it.code == WorkoutSuggestionCode.ISOLATION_BEFORE_COMPOUND }

        assertEquals(WorkoutSuggestionAction.MoveExercise("row-item", "curl-item"), suggestion.action)
    }

    @Test
    fun `descanso desativado e limite exato nao alertam`() {
        val workout = workout()
        val push = exercise("push", "Supino", movement = "Empurrar horizontal")
        val row = exercise("row", "Remada", muscle = "Costas", movement = "Puxar horizontal")
        val squat = exercise("squat", "Agachamento", muscle = "Quadríceps", movement = "Agachar")
        val items = listOf(
            item("disabled", push.id, 0, rest = 0),
            item("boundary", row.id, 1, rest = 60),
            item("short", squat.id, 2, rest = 59),
        )

        val rests = analyzer.analyze(workout, items, listOf(push, row, squat))
            .filter { it.code == WorkoutSuggestionCode.SHORT_REST }

        assertEquals(listOf("short"), rests.single().affectedWorkoutExerciseIds)
    }

    @Test
    fun `domingo e segunda consecutivos entram na recuperacao semanal`() {
        val sunday = workout(id = "domingo", weekDays = "domingo")
        val monday = workout(id = "segunda", weekDays = "segunda-feira")
        val chest = exercise("supino", "Supino", movement = "Empurrar horizontal")
        val items = listOf(
            item("domingo-item", chest.id, 0, sets = 6, workoutId = sunday.id),
            item("segunda-item", chest.id, 0, sets = 6, workoutId = monday.id),
        )

        val suggestions = analyzer.analyzeWeekly(listOf(sunday, monday), items, listOf(chest))

        assertEquals(1, suggestions.count { it.code == WorkoutSuggestionCode.SHORT_RECOVERY })
    }

    @Test
    fun `treino arquivado nao e analisado`() {
        val workout = workout(archived = true)
        val exercise = exercise("supino", "Supino", movement = "Empurrar horizontal")

        assertTrue(analyzer.analyze(workout, listOf(item("item", exercise.id, 0, sets = 50)), listOf(exercise)).isEmpty())
    }

    private fun workout(
        id: String = "workout",
        weekDays: String = "",
        archived: Boolean = false,
    ) = WorkoutEntity(id = id, name = "Treino", weekDays = weekDays, archived = archived, createdAt = 0L)

    private fun item(
        id: String,
        exerciseId: String,
        order: Int,
        sets: Int = 3,
        rest: Int = 60,
        setType: String = "Normal",
        workoutId: String = "workout",
    ) = WorkoutExerciseEntity(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        orderIndex = order,
        sets = sets,
        repMin = 8,
        repMax = 12,
        restSeconds = rest,
        setType = setType,
    )

    private fun exercise(
        id: String,
        name: String,
        muscle: String = "Peito",
        secondary: String = "Tríceps",
        movement: String,
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscle,
        secondaryMuscles = secondary,
        equipment = "Equipamento",
        difficulty = "Intermediário",
        movementType = movement,
        category = "Musculação",
        instructions = "Execute com controle.",
        cautions = "Evite perder a técnica.",
    )
}
