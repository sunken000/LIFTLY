package com.liftly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftlyRewardsTest {
    @Test
    fun levelCurveUsesLifetimeXpAndKeepsProgressInsideLevel() {
        assertEquals(1, RewardProgression.fromLifetimeXp(0).level)
        assertEquals(2, RewardProgression.fromLifetimeXp(250).level)
        val levelThree = RewardProgression.fromLifetimeXp(800)
        assertEquals(3, levelThree.level)
        assertEquals(50L, levelThree.xpInLevel)
        assertEquals(750L, levelThree.xpForNextLevel)
    }

    @Test
    fun invalidWorkoutNeverProducesRewardsOrMissionProgress() {
        val decision = WorkoutRewardPolicy.calculate(
            WorkoutRewardMetrics(completedSets = 0, totalSets = 4)
        )
        assertEquals(0L, decision.xp)
        assertEquals(0L, decision.coins)
        assertTrue(decision.missionProgress.isEmpty())
    }

    @Test
    fun completeAssessedWorkoutCombinesBonusesAndMissionMetrics() {
        val decision = WorkoutRewardPolicy.calculate(
            WorkoutRewardMetrics(
                completedSets = 4,
                totalSets = 4,
                rirRecordedSets = 4,
                personalRecords = 1,
            )
        )
        assertEquals(145L, decision.xp)
        assertEquals(35L, decision.coins)
        assertEquals(1, decision.missionProgress[RewardMetric.WORKOUT_COMPLETED])
        assertEquals(1, decision.missionProgress[RewardMetric.COMPLETE_WORKOUT])
        assertEquals(4, decision.missionProgress[RewardMetric.RIR_SET_RECORDED])
        assertEquals(1, decision.missionProgress[RewardMetric.PERSONAL_RECORD])
        assertFalse(decision.reasons.isEmpty())
    }
}