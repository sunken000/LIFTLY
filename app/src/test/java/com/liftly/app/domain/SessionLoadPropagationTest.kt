package com.liftly.app.domain

import com.liftly.app.data.SessionSetEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLoadPropagationTest {
    private fun set(id: String, number: Int, load: Double, completed: Boolean = false) = SessionSetEntity(
        id = id,
        sessionId = "session",
        workoutExerciseId = "workout-exercise",
        exerciseId = "exercise",
        exerciseName = "Supino",
        setNumber = number,
        reps = 8,
        loadKg = load,
        completed = completed,
        trackingMode = "Repetições",
    )

    @Test
    fun `primeira serie alterada propaga apenas para series futuras intactas`() {
        val first = set("1", 1, 60.0)
        assertTrue(SessionLoadPropagation.changedFirstWorkingSet(first, 70.0))
        assertTrue(SessionLoadPropagation.shouldInherit(first, set("2", 2, 60.0)))
        assertFalse(SessionLoadPropagation.shouldInherit(first, set("3", 3, 65.0)))
        assertFalse(SessionLoadPropagation.shouldInherit(first, set("4", 4, 60.0, completed = true)))
    }
}
