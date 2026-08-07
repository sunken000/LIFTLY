package com.liftly.app.domain

import com.liftly.app.data.ScheduleEntity
import com.liftly.app.data.WorkoutEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveScheduleResolverTest {
    private val monday = LocalDate.of(2026, 8, 3)
    private val workout = WorkoutEntity(id = "a", name = "A", weekDays = "1,3")

    @Test
    fun `recurring weekday appears without persisted calendar row`() {
        val result = EffectiveScheduleResolver.forDate(monday, listOf(workout), emptyList())

        assertEquals("a", result.single().workoutId)
        assertTrue(EffectiveScheduleResolver.isRecurringPlaceholder(result.single()))
    }

    @Test
    fun `persisted status replaces recurring placeholder`() {
        val persisted = ScheduleEntity("schedule", monday.toString(), "a", status = "Concluído")
        val result = EffectiveScheduleResolver.forDate(monday, listOf(workout), listOf(persisted))

        assertEquals(listOf(persisted), result)
    }

    @Test
    fun `rest day suppresses every recurring workout`() {
        val rest = ScheduleEntity("rest", monday.toString(), "", status = "Descanso", isRestDay = true)
        val result = EffectiveScheduleResolver.forDate(monday, listOf(workout), listOf(rest))

        assertEquals(listOf(rest), result)
    }
}
