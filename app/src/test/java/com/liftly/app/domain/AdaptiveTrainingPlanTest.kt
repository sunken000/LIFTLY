package com.liftly.app.domain

import com.liftly.app.data.WorkoutExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveTrainingPlanTest {
    private val item = WorkoutExerciseEntity("i", "w", "e", 0, repMin = 8, repMax = 12, targetLoadKg = 70.0)

    @Test
    fun `persists supported load increase`() {
        val recommendation = ProgressionRecommendation(
            ProgressionStatus.INCREASE,
            "Avance",
            "ok",
            72.5,
            8,
            12,
            listOf("histórico"),
        )
        val result = AdaptiveTrainingPlan.prescription(item, recommendation)!!
        assertEquals(72.5, result.targetLoadKg, 0.001)
    }

    @Test
    fun `caution never rewrites workout`() {
        val recommendation = ProgressionRecommendation(
            ProgressionStatus.CAUTION,
            "Dor",
            "pare",
            null,
            null,
            null,
            listOf("dor"),
        )
        assertNull(AdaptiveTrainingPlan.prescription(item, recommendation))
    }
}
