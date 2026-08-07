package com.liftly.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingGamificationTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 31)

    @Test
    fun `session without a completed work set does not count`() {
        val result = TrainingGamificationEngine.calculate(
            workouts = listOf(workout("empty", today)),
            sets = emptyList(),
            weeklyGoal = 3,
            today = today,
            zoneId = zone,
        )

        assertFalse(result.milestones.first { it.id == "first" }.unlocked)
        assertEquals(0, result.monthlyChallenges.first().progress)
    }

    @Test
    fun `consistent weeks unlock discreet milestone`() {
        val workouts = buildList {
            var index = 0
            repeat(4) { week ->
                repeat(3) { day ->
                    add(workout("s${index++}", today.minusWeeks(week.toLong()).minusDays(day.toLong())))
                }
            }
        }
        val sets = workouts.map { GamificationSet(it.sessionId, "squat", 60.0, 8, 2) }

        val result = TrainingGamificationEngine.calculate(workouts, sets, 3, today, zone)

        assertTrue(result.milestones.first { it.id == "four-weeks" }.unlocked)
        assertTrue(result.consistency.score >= 50)
    }

    private fun workout(id: String, date: LocalDate) = GamificationWorkout(
        sessionId = id,
        startedAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
