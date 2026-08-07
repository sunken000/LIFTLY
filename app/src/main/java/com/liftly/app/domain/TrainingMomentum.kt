package com.liftly.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class TrainingMomentum(
    val currentWeekWorkouts: Int,
    val weeklyGoal: Int,
    /** Semanas consecutivas em que a meta foi cumprida. */
    val completedWeekStreak: Int,
    val longestCompletedWeekStreak: Int,
) {
    val goalReached: Boolean get() = currentWeekWorkouts >= weeklyGoal
    val remainingThisWeek: Int get() = (weeklyGoal - currentWeekWorkouts).coerceAtLeast(0)
    val progress: Float get() = (currentWeekWorkouts.toFloat() / weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
}

object TrainingMomentumCalculator {
    fun calculate(
        completedSessionTimes: List<Long>,
        weeklyGoal: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TrainingMomentum {
        val safeGoal = weeklyGoal.coerceIn(1, 14)
        val currentWeek = today.weekStart()
        val counts = completedSessionTimes.asSequence()
            .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .filter { !it.isAfter(today) }
            .groupingBy { it.weekStart() }
            .eachCount()
        val completedWeeks = counts.filterValues { it >= safeGoal }.keys

        var streak = 0
        var cursor = if (currentWeek in completedWeeks) currentWeek else currentWeek.minusWeeks(1)
        while (cursor in completedWeeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }

        var longest = 0
        var running = 0
        val first = completedWeeks.minOrNull()
        if (first != null) {
            cursor = first
            while (!cursor.isAfter(currentWeek)) {
                if (cursor in completedWeeks) {
                    running++
                    longest = maxOf(longest, running)
                } else {
                    running = 0
                }
                cursor = cursor.plusWeeks(1)
            }
        }

        return TrainingMomentum(
            currentWeekWorkouts = counts[currentWeek] ?: 0,
            weeklyGoal = safeGoal,
            completedWeekStreak = streak,
            longestCompletedWeekStreak = longest,
        )
    }
}

private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
