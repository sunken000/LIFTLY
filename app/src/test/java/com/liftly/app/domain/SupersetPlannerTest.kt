package com.liftly.app.domain

import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.WorkoutExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupersetPlannerTest {
    private val first = WorkoutExerciseEntity("a", "w", "ea", 0, restSeconds = 45, setType = "Bi-set")
    private val second = WorkoutExerciseEntity("b", "w", "eb", 1, restSeconds = 60, setType = "Supersérie")

    @Test
    fun `intercala rodadas e descansa apenas depois do exercicio B`() {
        val sets = listOf(
            set("a1", "a", "ea", "A", 1, 0),
            set("a2", "a", "ea", "A", 2, 0),
            set("b1", "b", "eb", "B", 1, 1),
            set("b2", "b", "eb", "B", 2, 1),
        )

        assertEquals(listOf("a1", "b1", "a2", "b2"), SupersetPlanner.sequence(sets, listOf(first, second)).map { it.id })
        assertNull(SupersetPlanner.restSecondsAfter(first.id, listOf(first, second)))
        assertEquals(60, SupersetPlanner.restSecondsAfter(second.id, listOf(first, second)))
    }

    private fun set(id: String, workoutExerciseId: String, exerciseId: String, name: String, number: Int, order: Int) =
        SessionSetEntity(
            id = id,
            sessionId = "session",
            workoutExerciseId = workoutExerciseId,
            exerciseId = exerciseId,
            exerciseName = name,
            setNumber = number,
            reps = 8,
            loadKg = 10.0,
            exerciseOrder = order,
        )
}
