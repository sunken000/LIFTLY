package com.liftly.app.domain

import com.liftly.app.data.WorkoutExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiSetPairingTest {
    @Test
    fun `pair moves second beside first and marks both`() {
        val items = listOf(
            item("a", 0),
            item("b", 1),
            item("c", 2),
            item("d", 3),
        )

        val paired = BiSetPairing.pair(items, "b", "d")

        assertEquals(listOf("a", "b", "d", "c"), paired.map { it.id })
        assertEquals("Bi-set", paired.first { it.id == "b" }.setType)
        assertEquals("Bi-set", paired.first { it.id == "d" }.setType)
        assertEquals(2, SupersetPlanner.memberships(paired).size)
    }

    @Test
    fun `pair detaches previous partners before creating new pair`() {
        val items = listOf(
            item("a", 0, "Bi-set"),
            item("b", 1, "Bi-set"),
            item("c", 2),
            item("d", 3, "Bi-set"),
            item("e", 4, "Bi-set"),
        )

        val paired = BiSetPairing.pair(items, "b", "d")
        val memberships = SupersetPlanner.memberships(paired)

        assertEquals("Normal", paired.first { it.id == "a" }.setType)
        assertEquals("Normal", paired.first { it.id == "e" }.setType)
        assertEquals("d", memberships.getValue("b").partnerWorkoutExerciseId)
        assertEquals("b", memberships.getValue("d").partnerWorkoutExerciseId)
    }

    @Test
    fun `unpair clears both members and keeps order`() {
        val items = listOf(
            item("a", 0),
            item("b", 1, "Bi-set"),
            item("c", 2, "Bi-set"),
            item("d", 3),
        )

        val result = BiSetPairing.unpair(items, "c")

        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
        assertFalse(SupersetPlanner.isMarked(result.first { it.id == "b" }))
        assertFalse(SupersetPlanner.isMarked(result.first { it.id == "c" }))
        assertTrue(SupersetPlanner.memberships(result).isEmpty())
    }

    private fun item(id: String, order: Int, type: String = "Normal") = WorkoutExerciseEntity(
        id = id,
        workoutId = "w",
        exerciseId = "e-$id",
        orderIndex = order,
        sets = 3,
        repMin = 8,
        repMax = 12,
        targetLoadKg = 10.0,
        restSeconds = 60,
        setType = type,
    )
}
