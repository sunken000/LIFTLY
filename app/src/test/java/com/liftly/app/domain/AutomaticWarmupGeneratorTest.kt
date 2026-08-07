package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.WorkoutExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticWarmupGeneratorTest {
    private val generator = AutomaticWarmupGenerator()

    @Test
    fun `orders the day and prioritizes first loaded compound`() {
        val plan = generator.generate(
            listOf(
                input(id = "curl", order = 2, movement = "Flexão de cotovelo", muscle = "Bíceps", load = 20.0),
                input(id = "bench", order = 0, movement = "Empurrar horizontal", muscle = "Peito", load = 100.0),
                input(id = "row", order = 1, movement = "Puxar horizontal", muscle = "Costas", load = 80.0),
            ),
        )

        assertEquals(listOf("bench", "row", "curl"), plan.exercises.map(ExerciseWarmupPlan::workoutExerciseId))
        assertEquals(WarmupPriority.PRIMARY_COMPOUND, plan.exercises.first().priority)
        assertEquals(listOf(40.0, 75.0), plan.exercises.first().sets.mapNotNull(WarmupApproachSet::loadKg))
        assertEquals(listOf(6, 3), plan.exercises.first().sets.mapNotNull(WarmupApproachSet::repetitions))
    }

    @Test
    fun `new compound pattern is prepared while repeated pattern is reduced`() {
        val plan = generator.generate(
            listOf(
                input(id = "bench", order = 0, movement = "Empurrar horizontal", muscle = "Peito", load = 100.0),
                input(id = "incline", order = 1, movement = "Empurrar inclinado", muscle = "Peito", load = 80.0),
                input(id = "press", order = 2, movement = "Empurrar vertical", muscle = "Ombros", load = 40.0),
            ),
        )

        val repeated = plan.exercises[1]
        assertEquals(WarmupPriority.RELATED_PATTERN, repeated.priority)
        assertEquals(1, repeated.sets.size)
        assertEquals(40.0, repeated.sets.single().loadKg ?: 0.0, 0.001)

        val newPattern = plan.exercises[2]
        assertEquals(WarmupPriority.NEW_MOVEMENT_PATTERN, newPattern.priority)
        assertEquals(listOf(16.0, 30.0), newPattern.sets.mapNotNull(WarmupApproachSet::loadKg))
    }

    @Test
    fun `leg press stays in lower body pattern and does not warm a bench press`() {
        val plan = generator.generate(
            listOf(
                input(id = "leg-press", order = 0, movement = "Empurrar com pernas", muscle = "Quadríceps", load = 120.0),
                input(id = "bench", order = 1, movement = "Empurrar horizontal", muscle = "Peito", load = 80.0),
            ),
        )

        assertTrue(plan.exercises[0].movementPattern.contains("agachamento"))
        assertEquals("empurrar horizontal", plan.exercises[1].movementPattern)
        assertEquals(WarmupPriority.NEW_MOVEMENT_PATTERN, plan.exercises[1].priority)
    }

    @Test
    fun `bodyweight and zero load use technique rehearsal without inventing load`() {
        val plan = generator.generate(
            listOf(
                input(
                    id = "pushup",
                    order = 0,
                    movement = "Empurrar horizontal",
                    muscle = "Peito",
                    equipment = "Peso corporal",
                    category = "Peso corporal",
                    load = 0.0,
                    repMin = 12,
                ),
            ),
        )

        val set = plan.exercises.single().sets.single()
        assertEquals(WarmupPriority.REHEARSAL_ONLY, plan.exercises.single().priority)
        assertEquals(WarmupSetKind.MOVEMENT_REHEARSAL, set.kind)
        assertNull(set.loadKg)
        assertEquals(8, set.repetitions)
        assertTrue(set.effortCue.contains("RIR 5+"))
    }

    @Test
    fun `very small configured load falls back to unloaded rehearsal`() {
        val plan = generator.generate(
            listOf(input(id = "tiny", order = 0, movement = "Flexão de cotovelo", muscle = "Bíceps", load = 0.4)),
        )

        val exercise = plan.exercises.single()
        assertEquals(WarmupPriority.REHEARSAL_ONLY, exercise.priority)
        assertNull(exercise.sets.single().loadKg)
        assertTrue(exercise.explanation.contains("nenhuma carga foi inventada"))
    }

    @Test
    fun `time tracking gets shorter acclimation and no load`() {
        val plan = generator.generate(
            listOf(
                input(
                    id = "bike",
                    order = 0,
                    movement = "Pedalar",
                    muscle = "Pernas",
                    category = "Cardio",
                    trackingMode = "Tempo",
                    load = 30.0,
                    repMin = 120,
                ),
            ),
        )

        val set = plan.exercises.single().sets.single()
        assertEquals(WarmupSetKind.TIME_ACCLIMATION, set.kind)
        assertEquals(48, set.durationSeconds)
        assertNull(set.loadKg)
        assertNull(set.repetitions)
        assertTrue(requireNotNull(set.durationSeconds) <= 120)
    }

    @Test
    fun `distance tracking gets bounded easy segment and no load`() {
        val plan = generator.generate(
            listOf(
                input(
                    id = "run",
                    order = 0,
                    movement = "Correr",
                    muscle = "Pernas",
                    category = "Cardio",
                    trackingMode = "Distância",
                    load = 0.0,
                    repMin = 1_000,
                ),
            ),
        )

        val set = plan.exercises.single().sets.single()
        assertEquals(WarmupSetKind.DISTANCE_ACCLIMATION, set.kind)
        assertEquals(150.0, set.distanceMeters ?: 0.0, 0.001)
        assertTrue(requireNotNull(set.distanceMeters) <= 1_000.0)
        assertNull(set.loadKg)
    }

    @Test
    fun `explicit warmup and mobility are not duplicated`() {
        val plan = generator.generate(
            listOf(
                input(id = "marked", order = 0, movement = "Empurrar", muscle = "Peito", load = 20.0, setType = "Aquecimento"),
                input(id = "mobility", order = 1, movement = "Mobilidade de ombro", muscle = "Ombros", load = 0.0, category = "Mobilidade"),
            ),
        )

        assertEquals(WarmupPriority.ALREADY_WARMUP, plan.exercises[0].priority)
        assertTrue(plan.exercises[0].sets.isEmpty())
        assertEquals(WarmupPriority.NOT_NEEDED, plan.exercises[1].priority)
        assertTrue(plan.exercises[1].sets.isEmpty())
    }

    @Test
    fun `all rounded loaded approaches stay progressive and below work load`() {
        listOf(4.0, 9.5, 37.0, 61.0, 103.0).forEach { target ->
            val plan = generator.generate(
                listOf(input(id = "lift-$target", order = 0, movement = "Agachar", muscle = "Quadríceps", load = target)),
            )
            val loads = plan.exercises.single().sets.mapNotNull(WarmupApproachSet::loadKg)
            assertEquals(loads.sorted(), loads)
            assertEquals(loads.distinct(), loads)
            assertTrue(loads.all { it > 0.0 && it < target })
        }
    }

    @Test
    fun `general stage is day specific and remains between five and ten minutes`() {
        val upper = generator.generate(
            listOf(input(id = "bench", order = 0, movement = "Empurrar horizontal", muscle = "Peito", load = 80.0)),
        ).general
        val fullBody = generator.generate(
            listOf(
                input(id = "squat", order = 0, movement = "Agachar", muscle = "Quadríceps", load = 80.0),
                input(id = "row", order = 1, movement = "Puxar horizontal", muscle = "Costas", load = 60.0),
            ),
        ).general

        assertTrue(upper.steps.any { it.instruction.contains("ombros") })
        assertFalse(upper.steps.any { it.instruction.contains("quadris, joelhos") })
        assertTrue(fullBody.steps.any { it.instruction.contains("ombros") && it.instruction.contains("quadris") })
        assertTrue(fullBody.estimatedDurationSeconds in 300..600)
    }

    @Test
    fun `duration and explanations are complete and internally consistent`() {
        val plan = generator.generate(
            listOf(
                input(id = "bench", order = 0, movement = "Empurrar horizontal", muscle = "Peito", load = 100.0),
                input(id = "curl", order = 1, movement = "Flexão de cotovelo", muscle = "Bíceps", load = 16.0),
            ),
        )

        assertTrue(plan.general.steps.all { it.title.isNotBlank() && it.instruction.isNotBlank() && it.reason.isNotBlank() })
        assertTrue(plan.exercises.all { it.explanation.isNotBlank() })
        assertTrue(plan.exercises.flatMap(ExerciseWarmupPlan::sets).all { it.explanation.isNotBlank() && it.estimatedExecutionSeconds > 0 })
        assertEquals(plan.exercises.sumOf { it.sets.size }, plan.generatedSetCount)
        assertEquals(
            plan.general.estimatedDurationSeconds + plan.exercises.sumOf(ExerciseWarmupPlan::estimatedDurationSeconds),
            plan.estimatedDurationSeconds,
        )
    }

    @Test
    fun `same input always returns identical plan`() {
        val input = listOf(
            input(id = "deadlift", order = 0, movement = "Levantamento", muscle = "Posteriores", load = 122.0),
            input(id = "row", order = 1, movement = "Puxar horizontal", muscle = "Costas", load = 57.5),
        )
        assertEquals(generator.generate(input), generator.generate(input))
    }

    @Test
    fun `entity overload resolves prescriptions and preserves workout order`() {
        val exercises = listOf(
            exercise(id = "bench", name = "Supino", movement = "Empurrar horizontal", muscle = "Peito"),
            exercise(id = "row", name = "Remada", movement = "Puxar horizontal", muscle = "Costas"),
        )
        val items = listOf(
            workoutItem(id = "row-item", exerciseId = "row", order = 1, load = 60.0),
            workoutItem(id = "bench-item", exerciseId = "bench", order = 0, load = 80.0),
            workoutItem(id = "missing-item", exerciseId = "missing", order = 2, load = 20.0),
        )

        val plan = generator.generate(items, exercises)

        assertEquals(listOf("bench-item", "row-item"), plan.exercises.map(ExerciseWarmupPlan::workoutExerciseId))
        assertEquals("Supino", plan.exercises.first().exerciseName)
        assertEquals(80.0, plan.exercises.first().sets.maxOf { it.loadKg ?: 0.0 } / 0.75, 2.5)
    }

    private fun input(
        id: String,
        order: Int,
        movement: String,
        muscle: String,
        load: Double,
        equipment: String = "Barra",
        category: String = "Musculação",
        trackingMode: String = "Repetições",
        repMin: Int = 8,
        repMax: Int = 12,
        setType: String = "Normal",
    ) = WarmupExerciseInput(
        workoutExerciseId = id,
        exerciseId = "exercise-$id",
        exerciseName = "Exercise $id",
        orderIndex = order,
        movementType = movement,
        muscleGroup = muscle,
        equipment = equipment,
        category = category,
        trackingMode = trackingMode,
        workSets = 3,
        targetRepMin = repMin,
        targetRepMax = repMax,
        targetLoadKg = load,
        setType = setType,
    )

    private fun exercise(id: String, name: String, movement: String, muscle: String) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscle,
        equipment = "Barra",
        difficulty = "Intermediário",
        movementType = movement,
        category = "Musculação",
        instructions = "",
        cautions = "",
    )

    private fun workoutItem(id: String, exerciseId: String, order: Int, load: Double) = WorkoutExerciseEntity(
        id = id,
        workoutId = "today",
        exerciseId = exerciseId,
        orderIndex = order,
        targetLoadKg = load,
        trackingMode = "Repetições",
    )
}
