package com.liftly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionCoachTest {
    private val coach = ProgressionCoach()

    @Test
    fun `two current sets at sixty kg rir four and no pain increase from current load`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 1,
                actualLoadKg = 200.0,
                rir = 0,
                recent = listOf(history(reps = 12, load = 150.0, rir = 4)),
                currentSets = listOf(
                    currentSet(number = 1, reps = 10, load = 60.0, rir = 4, pain = 0),
                    currentSet(number = 2, reps = 10, load = 60.0, rir = 4, pain = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(61.5, recommendation.suggestedLoadKg ?: 0.0, 0.001)
        assertTrue(recommendation.reasons.any { it.contains("2 série") })
    }

    @Test
    fun `historical maximum never becomes suggested load when current sets exist`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 180.0,
                rir = 4,
                recent = listOf(
                    history(reps = 12, load = 200.0, rir = 4),
                    history(reps = 12, load = 190.0, rir = 4),
                ),
                currentSets = listOf(
                    currentSet(number = 1, reps = 12, load = 60.0, rir = 4),
                    currentSet(number = 2, reps = 12, load = 60.0, rir = 4),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(61.5, recommendation.suggestedLoadKg ?: 0.0, 0.001)
        assertTrue((recommendation.suggestedLoadKg ?: Double.MAX_VALUE) < 100.0)
    }

    @Test
    fun `moderate pain blocks progression and reduces current set load`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 150.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 12, load = 60.0, rir = 4, pain = 4),
                    currentSet(number = 2, reps = 12, load = 60.0, rir = 4, pain = 5),
                ),
            ),
        )

        assertEquals(ProgressionStatus.REDUCE, recommendation.status)
        assertEquals(54.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `pain two blocks increase but keeps current load for monitoring`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 60.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 12, load = 60.0, rir = 4, pain = 2),
                    currentSet(number = 2, reps = 12, load = 60.0, rir = 4, pain = 1),
                ),
            ),
        )

        assertEquals(ProgressionStatus.CAUTION, recommendation.status)
        assertEquals(60.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `high pain recommends stopping and provides no automatic load`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 150.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 12, load = 60.0, rir = 4, pain = 6),
                    currentSet(number = 2, reps = 12, load = 60.0, rir = 4, pain = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.CAUTION, recommendation.status)
        assertNull(recommendation.suggestedLoadKg)
        assertTrue(recommendation.title.contains("Interrompa"))
    }

    @Test
    fun `rir five uses at most five percent increase from current load`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 10,
                actualLoadKg = 60.0,
                rir = 5,
                currentSets = listOf(
                    currentSet(number = 1, reps = 10, load = 60.0, rir = 5),
                    currentSet(number = 2, reps = 10, load = 60.0, rir = 5),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(63.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `low rir keeps current load when reps are inside range`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 150.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 10, load = 60.0, rir = 1),
                    currentSet(number = 2, reps = 9, load = 60.0, rir = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.KEEP, recommendation.status)
        assertEquals(60.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `first set at minimum reps and low rir reduces load for following sets`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 8,
                actualLoadKg = 60.0,
                rir = 0,
                currentSets = listOf(
                    currentSet(number = 1, reps = 8, load = 60.0, rir = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.REDUCE, recommendation.status)
        assertEquals(55.5, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `low rir reduces current load when a set misses minimum reps`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 150.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 8, load = 60.0, rir = 1),
                    currentSet(number = 2, reps = 6, load = 60.0, rir = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.REDUCE, recommendation.status)
        assertEquals(55.5, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `missing rir in any current set prevents rir based increase`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                actualLoadKg = 150.0,
                rir = 4,
                currentSets = listOf(
                    currentSet(number = 1, reps = 10, load = 60.0, rir = 4),
                    currentSet(number = 2, reps = 10, load = 60.0, rir = null),
                ),
            ),
        )

        assertEquals(ProgressionStatus.KEEP, recommendation.status)
        assertEquals(60.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `latest assessed set drives suggestion even when an earlier set has no rir`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 1,
                actualLoadKg = 200.0,
                rir = null,
                currentSets = listOf(
                    currentSet(number = 1, reps = 9, load = 60.0, rir = null),
                    currentSet(number = 2, reps = 10, load = 62.0, rir = 4),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(64.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `mixed loads never synthesize suggestion from an older lighter set`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 1,
                actualLoadKg = 200.0,
                rir = null,
                currentSets = listOf(
                    currentSet(number = 1, reps = 12, load = 60.0, rir = 3),
                    currentSet(number = 2, reps = 10, load = 70.0, rir = 4),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(72.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `increases load after reaching top of range with rir margin`() {
        val recommendation = coach.recommend(input(actualReps = 12, rir = 3, actualLoadKg = 40.0))

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(41.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
        assertEquals(8, recommendation.suggestedRepMin)
        assertEquals(12, recommendation.suggestedRepMax)
    }

    @Test
    fun `uses a conservative lower body increment and half kilogram rounding`() {
        val recommendation = coach.recommend(
            input(
                exerciseName = "Agachamento livre",
                actualReps = 12,
                rir = 2,
                actualLoadKg = 83.0,
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(87.5, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `keeps load when top of range was reached too close to failure`() {
        val recommendation = coach.recommend(input(actualReps = 12, rir = 0))

        assertEquals(ProgressionStatus.KEEP, recommendation.status)
        assertEquals(40.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
        assertTrue(recommendation.message.contains("perto da falha"))
    }

    @Test
    fun `increases without rir only after three comparable top performances`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 12,
                rir = null,
                recent = listOf(
                    history(reps = 12, load = 40.0),
                    history(reps = 13, load = 41.0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
    }

    @Test
    fun `does not increase without rir after only one previous top performance`() {
        val recommendation = coach.recommend(
            input(actualReps = 12, rir = null, recent = listOf(history(reps = 12, load = 40.0))),
        )

        assertEquals(ProgressionStatus.KEEP, recommendation.status)
    }

    @Test
    fun `reduces load after missing minimum reps at high effort`() {
        val recommendation = coach.recommend(input(actualReps = 6, rir = 0, actualLoadKg = 40.0))

        assertEquals(ProgressionStatus.REDUCE, recommendation.status)
        assertEquals(37.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `suggests deload only after three comparable misses with low rir`() {
        val recommendation = coach.recommend(
            input(
                actualReps = 6,
                rir = 1,
                recent = listOf(
                    history(reps = 7, load = 40.0, rir = 1),
                    history(reps = 6, load = 39.0, rir = 0),
                ),
            ),
        )

        assertEquals(ProgressionStatus.DELOAD, recommendation.status)
        assertEquals(35.0, recommendation.suggestedLoadKg ?: 0.0, 0.001)
    }

    @Test
    fun `pain overrides an otherwise valid increase`() {
        val recommendation = coach.recommend(input(actualReps = 12, rir = 3, pain = 4))

        assertEquals(ProgressionStatus.REDUCE, recommendation.status)
        assertTrue(recommendation.message.contains("dor", ignoreCase = true))
        assertTrue((recommendation.suggestedLoadKg ?: 40.0) < 40.0)
    }

    @Test
    fun `strong pain gives no automatic prescription`() {
        val recommendation = coach.recommend(input(actualReps = 12, rir = 3, pain = 8))

        assertEquals(ProgressionStatus.CAUTION, recommendation.status)
        assertNull(recommendation.suggestedLoadKg)
        assertNull(recommendation.suggestedRepMin)
        assertNull(recommendation.suggestedRepMax)
    }

    @Test
    fun `bodyweight movement advances repetitions when no external load exists`() {
        val recommendation = coach.recommend(
            input(
                category = "Peso corporal",
                plannedLoadKg = 0.0,
                actualLoadKg = 0.0,
                actualReps = 12,
                rir = 3,
            ),
        )

        assertEquals(ProgressionStatus.INCREASE, recommendation.status)
        assertEquals(9, recommendation.suggestedRepMin)
        assertEquals(13, recommendation.suggestedRepMax)
    }

    @Test
    fun `cardio category does not receive automatic load progression`() {
        val recommendation = coach.recommend(
            input(category = "Cardio", actualReps = 12, rir = 4),
        )

        assertEquals(ProgressionStatus.KEEP, recommendation.status)
        assertTrue(recommendation.message.contains("tempo, distância, ritmo"))
    }

    @Test
    fun `invalid rir returns caution instead of silently coercing it`() {
        val recommendation = coach.recommend(input(actualReps = 10, rir = 11))

        assertEquals(ProgressionStatus.CAUTION, recommendation.status)
        assertNull(recommendation.suggestedLoadKg)
        assertTrue(recommendation.message.contains("RIR"))
    }

    private fun input(
        exerciseName: String = "Supino reto",
        category: String = "Musculação",
        plannedRepMin: Int = 8,
        plannedRepMax: Int = 12,
        plannedLoadKg: Double = 40.0,
        actualReps: Int,
        actualLoadKg: Double = plannedLoadKg,
        rir: Int?,
        pain: Int = 0,
        recent: List<HistoricalExercisePerformance> = emptyList(),
        currentSets: List<CurrentExerciseSetPerformance> = emptyList(),
    ) = ProgressionCoachInput(
        exerciseName = exerciseName,
        category = category,
        plannedRepMin = plannedRepMin,
        plannedRepMax = plannedRepMax,
        plannedLoadKg = plannedLoadKg,
        actualReps = actualReps,
        actualLoadKg = actualLoadKg,
        rir = rir,
        painLevel = pain,
        recentPerformances = recent,
        currentSets = currentSets,
    )

    private fun history(
        reps: Int,
        load: Double,
        rir: Int? = null,
    ) = HistoricalExercisePerformance(
        actualReps = reps,
        actualLoadKg = load,
        rir = rir,
    )

    private fun currentSet(
        number: Int,
        reps: Int,
        load: Double,
        rir: Int?,
        pain: Int = 0,
    ) = CurrentExerciseSetPerformance(
        setNumber = number,
        reps = reps,
        loadKg = load,
        rir = rir,
        painLevel = pain,
    )
}
