package com.liftly.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExerciseOrderTest {
    @Test
    fun `normalize repairs gaps and duplicates without changing tied item order`() {
        val result = WorkoutExerciseOrder.normalize(
            listOf(
                item("third", 5),
                item("first", 1),
                item("second", 1),
                item("last", 9),
            )
        )

        assertEquals(listOf("first", "second", "third", "last"), result.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), result.map { it.orderIndex })
    }

    @Test
    fun `append after middle deletion cannot reuse an occupied index`() {
        val result = WorkoutExerciseOrder.append(
            items = listOf(item("first", 0), item("third", 2)),
            item = item("new", 0),
        )

        assertEquals(listOf("first", "third", "new"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.orderIndex })
    }

    @Test
    fun `remove compacts every following index`() {
        val result = WorkoutExerciseOrder.remove(
            items = listOf(item("first", 0), item("second", 1), item("third", 2)),
            id = "second",
        )

        assertEquals(listOf("first", "third"), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.orderIndex })
    }

    @Test
    fun `move first repairs malformed data before applying direction`() {
        val result = WorkoutExerciseOrder.move(
            items = listOf(item("first", 1), item("second", 4), item("third", 4)),
            id = "third",
            direction = -1,
        )

        assertEquals(listOf("first", "third", "second"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.orderIndex })
    }

    @Test
    fun `move with unknown id still repairs malformed order`() {
        val result = WorkoutExerciseOrder.move(
            items = listOf(item("first", 2), item("second", 8)),
            id = "missing",
            direction = 1,
        )

        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.orderIndex })
    }

    @Test
    fun `move before itself is a no-op instead of moving to the end`() {
        val result = WorkoutExerciseOrder.moveBefore(
            items = listOf(item("first", 0), item("second", 1), item("third", 2)),
            id = "second",
            beforeId = "second",
        )

        assertEquals(listOf("first", "second", "third"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.orderIndex })
    }

    @Test
    fun `move before missing target places item at end with dense indices`() {
        val result = WorkoutExerciseOrder.moveBefore(
            items = listOf(item("first", 0), item("second", 1), item("third", 2)),
            id = "first",
            beforeId = "missing",
        )

        assertEquals(listOf("second", "third", "first"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.orderIndex })
    }

    private fun item(id: String, orderIndex: Int) = WorkoutExerciseEntity(
        id = id,
        workoutId = "workout",
        exerciseId = "exercise-$id",
        orderIndex = orderIndex,
    )
}
