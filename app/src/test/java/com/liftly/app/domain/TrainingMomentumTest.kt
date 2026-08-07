package com.liftly.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingMomentumTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun LocalDate.epoch() = atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun `conta treinos e limita progresso da semana atual`() {
        val today = LocalDate.of(2026, 7, 22)
        val result = TrainingMomentumCalculator.calculate(
            listOf(today.minusDays(2).epoch(), today.epoch(), today.plusDays(1).epoch()),
            weeklyGoal = 3,
            today = today,
            zoneId = zone,
        )
        assertEquals(2, result.currentWeekWorkouts)
        assertEquals(1, result.remainingThisWeek)
        assertFalse(result.goalReached)
    }

    @Test fun `mantem sequencia da semana anterior enquanto a atual esta aberta`() {
        val today = LocalDate.of(2026, 7, 22)
        val sessions = listOf(
            LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 20),
        ).map { it.epoch() }
        val result = TrainingMomentumCalculator.calculate(sessions, 2, today, zone)
        assertEquals(2, result.completedWeekStreak)
        assertEquals(2, result.longestCompletedWeekStreak)
    }

    @Test fun `inclui semana atual ao atingir a meta`() {
        val today = LocalDate.of(2026, 7, 22)
        val sessions = listOf(
            LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22),
        ).map { it.epoch() }
        val result = TrainingMomentumCalculator.calculate(sessions, 2, today, zone)
        assertTrue(result.goalReached)
        assertEquals(2, result.completedWeekStreak)
    }
}
