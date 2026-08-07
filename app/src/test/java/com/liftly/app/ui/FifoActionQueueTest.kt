package com.liftly.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FifoActionQueueTest {
    @Test
    fun `processes actions in insertion order and continues after failure`() = runTest {
        val attempts = mutableListOf<Int>()
        val completed = mutableListOf<Int>()
        val failures = mutableListOf<Int>()
        val queue = FifoActionQueue<Int>(
            scope = this,
            process = { value ->
                attempts += value
                delay((4 - value).coerceAtLeast(0).toLong())
                if (value == 2) error("expected")
                completed += value
            },
            onFailure = { value, _ -> failures += value },
        )

        assertTrue(queue.tryEnqueue(1))
        assertTrue(queue.tryEnqueue(2))
        assertTrue(queue.tryEnqueue(3))
        queue.awaitIdle()

        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(listOf(1, 3), completed)
        assertEquals(listOf(2), failures)
        queue.close()
    }

    @Test
    fun `idle barrier waits for all previously accepted actions`() = runTest {
        val events = mutableListOf<String>()
        val queue = FifoActionQueue<Int>(
            scope = this,
            process = { value ->
                delay(10)
                events += "saved-$value"
            },
            onFailure = { _, _ -> },
        )

        queue.tryEnqueue(60)
        queue.tryEnqueue(5)
        queue.awaitIdle()
        events += "finished"

        assertEquals(listOf("saved-60", "saved-5", "finished"), events)
        queue.close()
    }

    @Test
    fun `rejects new actions after close`() = runTest {
        val queue = FifoActionQueue<Int>(this, process = {}, onFailure = { _, _ -> })
        queue.close()

        assertFalse(queue.tryEnqueue(1))
    }
}
