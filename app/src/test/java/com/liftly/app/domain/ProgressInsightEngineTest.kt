package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightEngineTest {
    @Test
    fun `reports adherence without inventing diagnosis`() {
        val now = 1_800_000_000_000L
        val exercise = ExerciseEntity("e", "Supino", "Peitoral", equipment = "Barra", difficulty = "Médio", movementType = "Empurrar", category = "Musculação", instructions = "", cautions = "")
        val sessions = (1..3).map { index ->
            SessionEntity("s$index", "w", "A", now - index * 86_400_000L, now - index * 86_400_000L + 3_600_000L, "Concluído")
        }
        val sets = sessions.mapIndexed { index, session ->
            SessionSetEntity("set$index", session.id, "we", "e", "Supino", 1, 10, 60.0 + index, completed = true, completedAt = session.finishedAt, trackingMode = "Repetições")
        }
        val reading = ProgressInsightEngine.calculate(sessions, sets, listOf(exercise), weeklyGoal = 3, nowMillis = now)
        assertTrue(reading.insights.any { it.id == "adherence" })
    }
}
