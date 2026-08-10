package com.liftly.app.domain

import kotlin.math.floor
import kotlin.math.sqrt

/** Stable keys used by persisted missions and reward events. */
enum class RewardMetric {
    WORKOUT_COMPLETED,
    COMPLETE_WORKOUT,
    RIR_SET_RECORDED,
    PERSONAL_RECORD,
}

enum class RewardPeriod { DAILY, WEEKLY, MONTHLY }

data class WorkoutRewardMetrics(
    val completedSets: Int,
    val totalSets: Int,
    val rirRecordedSets: Int = 0,
    val personalRecords: Int = 0,
) {
    init {
        require(completedSets >= 0)
        require(totalSets >= completedSets)
        require(rirRecordedSets in 0..completedSets)
        require(personalRecords >= 0)
    }

    val isValid: Boolean get() = completedSets > 0
    val isComplete: Boolean get() = totalSets > 0 && completedSets == totalSets
}

data class WorkoutRewardDecision(
    val xp: Long,
    val coins: Long,
    val missionProgress: Map<RewardMetric, Int>,
    val reasons: List<String>,
) {
    companion object {
        val NONE = WorkoutRewardDecision(0, 0, emptyMap(), emptyList())
    }
}

/**
 * One deterministic policy for every edition of the app. Persistence supplies idempotency;
 * this object only decides what a genuinely completed workout is worth.
 */
object WorkoutRewardPolicy {
    fun calculate(metrics: WorkoutRewardMetrics): WorkoutRewardDecision {
        if (!metrics.isValid) return WorkoutRewardDecision.NONE

        var xp = 80L
        var coins = 15L
        val reasons = mutableListOf("Sessão válida registrada")
        if (metrics.isComplete) {
            xp += 20
            coins += 5
            reasons += "Plano do dia concluído"
        }
        if (metrics.completedSets >= 3 && metrics.rirRecordedSets == metrics.completedSets) {
            xp += 15
            coins += 5
            reasons += "Esforço registrado com consistência"
        }
        val rewardedRecords = metrics.personalRecords.coerceAtMost(2)
        if (rewardedRecords > 0) {
            xp += 30L * rewardedRecords
            coins += 10L * rewardedRecords
            reasons += "$rewardedRecords recorde(s) pessoal(is)"
        }
        return WorkoutRewardDecision(
            xp = xp,
            coins = coins,
            missionProgress = buildMap {
                put(RewardMetric.WORKOUT_COMPLETED, 1)
                if (metrics.isComplete) put(RewardMetric.COMPLETE_WORKOUT, 1)
                if (metrics.rirRecordedSets > 0) put(RewardMetric.RIR_SET_RECORDED, metrics.rirRecordedSets)
                if (metrics.personalRecords > 0) put(RewardMetric.PERSONAL_RECORD, metrics.personalRecords)
            },
            reasons = reasons,
        )
    }
}

data class RewardLevelProgress(
    val level: Int,
    val lifetimeXp: Long,
    val xpInLevel: Long,
    val xpForNextLevel: Long,
    val fraction: Float,
)

/** Each level costs 250 XP more than the previous one: 250, 500, 750... */
object RewardProgression {
    private const val BASE_LEVEL_COST = 250L

    fun thresholdForLevel(level: Int): Long {
        val normalized = level.coerceAtLeast(1).toLong()
        return BASE_LEVEL_COST * normalized * (normalized - 1L) / 2L
    }

    fun fromLifetimeXp(value: Long): RewardLevelProgress {
        val xp = value.coerceAtLeast(0L)
        val approximate = floor((1.0 + sqrt(1.0 + 8.0 * xp / BASE_LEVEL_COST)) / 2.0)
            .toInt()
            .coerceAtLeast(1)
        var level = approximate
        while (thresholdForLevel(level + 1) <= xp) level++
        while (thresholdForLevel(level) > xp) level--
        val start = thresholdForLevel(level)
        val cost = BASE_LEVEL_COST * level
        val within = xp - start
        return RewardLevelProgress(
            level = level,
            lifetimeXp = xp,
            xpInLevel = within,
            xpForNextLevel = cost,
            fraction = (within.toDouble() / cost.toDouble()).toFloat().coerceIn(0f, 1f),
        )
    }
}
