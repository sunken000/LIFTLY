package com.liftly.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class GamificationWorkout(
    val sessionId: String,
    val startedAt: Long,
)

data class GamificationSet(
    val sessionId: String,
    val exerciseId: String,
    val loadKg: Double,
    val reps: Int,
    val rir: Int?,
)

data class ConsistencyLevel(
    val score: Int,
    val label: String,
    val detail: String,
)

data class TrainingMilestone(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
)

data class MonthlyTrainingChallenge(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
) {
    val completed: Boolean get() = progress >= target
    val progressFraction: Float get() = (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class TrainingGamificationSummary(
    val consistency: ConsistencyLevel,
    val milestones: List<TrainingMilestone>,
    val monthlyChallenges: List<MonthlyTrainingChallenge>,
)

/** Gamificação derivada do histórico, sem pontos artificiais ou competição entre usuários. */
object TrainingGamificationEngine {
    fun calculate(
        workouts: List<GamificationWorkout>,
        sets: List<GamificationSet>,
        weeklyGoal: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TrainingGamificationSummary {
        val safeGoal = weeklyGoal.coerceIn(1, 14)
        val sessionsWithWork = sets.mapTo(mutableSetOf()) { it.sessionId }
        val validWorkouts = workouts
            .filter { it.sessionId in sessionsWithWork }
            .distinctBy { it.sessionId }
        val dates = validWorkouts.map {
            Instant.ofEpochMilli(it.startedAt).atZone(zoneId).toLocalDate()
        }
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekCounts = dates.groupingBy {
            it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.eachCount()
        val adherence = (0L until CONSISTENCY_WINDOW_WEEKS).map { offset ->
            val count = weekCounts[currentWeek.minusWeeks(offset)] ?: 0
            (count.toDouble() / safeGoal).coerceIn(0.0, 1.0)
        }.average()
        val score = (adherence * 100.0).toInt().coerceIn(0, 100)
        val consistency = when {
            score >= 85 -> ConsistencyLevel(score, "Excelente", "Sua rotina permaneceu estável nas últimas 8 semanas.")
            score >= 65 -> ConsistencyLevel(score, "Consistente", "Você mantém uma frequência sólida e previsível.")
            score >= 40 -> ConsistencyLevel(score, "Regular", "O ritmo está formado; reduza as semanas interrompidas.")
            else -> ConsistencyLevel(score, "Construindo ritmo", "Cada semana concluída aumenta sua base de consistência.")
        }

        val momentum = TrainingMomentumCalculator.calculate(
            completedSessionTimes = validWorkouts.map { it.startedAt },
            weeklyGoal = safeGoal,
            today = today,
            zoneId = zoneId,
        )
        val rirSets = sets.count { it.rir != null }
        val improvedExercises = improvedExerciseCount(validWorkouts, sets)
        val milestones = listOf(
            TrainingMilestone("first", "Primeiro registro", "Concluiu o primeiro treino com séries registradas.", validWorkouts.isNotEmpty()),
            TrainingMilestone("ten", "Ritmo estabelecido", "Concluiu 10 treinos registrados.", validWorkouts.size >= 10),
            TrainingMilestone("four-weeks", "Quatro semanas", "Cumpriu a meta em quatro semanas consecutivas.", momentum.longestCompletedWeekStreak >= 4),
            TrainingMilestone("progressive", "Progressão comprovada", "Evoluiu a carga em pelo menos três exercícios.", improvedExercises >= 3),
            TrainingMilestone("effort", "Esforço bem registrado", "Informou RIR em 25 séries.", rirSets >= 25),
        )

        val month = YearMonth.from(today)
        val monthWorkoutIds = validWorkouts.filter {
            YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zoneId).toLocalDate()) == month
        }.mapTo(mutableSetOf()) { it.sessionId }
        val monthSets = sets.filter { it.sessionId in monthWorkoutIds }
        val monthPrs = monthlyPersonalRecords(validWorkouts, sets, month, zoneId)
        val monthTarget = (safeGoal * 4).coerceAtLeast(4)
        val challenges = listOf(
            MonthlyTrainingChallenge(
                id = "frequency-${month}",
                title = "Frequência do mês",
                description = "Complete $monthTarget treinos com ao menos uma série.",
                progress = monthWorkoutIds.size,
                target = monthTarget,
            ),
            MonthlyTrainingChallenge(
                id = "rir-${month}",
                title = "Autorregulação",
                description = "Registre o RIR em 20 séries neste mês.",
                progress = monthSets.count { it.rir != null },
                target = 20,
            ),
            MonthlyTrainingChallenge(
                id = "pr-${month}",
                title = "Evolução pessoal",
                description = "Supere sua própria carga em três exercícios.",
                progress = monthPrs,
                target = 3,
            ),
        )

        return TrainingGamificationSummary(consistency, milestones, challenges)
    }

    private fun improvedExerciseCount(
        workouts: List<GamificationWorkout>,
        sets: List<GamificationSet>,
    ): Int {
        val timeBySession = workouts.associate { it.sessionId to it.startedAt }
        return sets.filter { it.loadKg > 0.0 && it.reps > 0 && it.sessionId in timeBySession }
            .groupBy { it.exerciseId }
            .count { (_, values) ->
                val ordered = values.sortedBy { timeBySession[it.sessionId] }
                ordered.size >= 2 && ordered.last().loadKg > ordered.first().loadKg
            }
    }

    private fun monthlyPersonalRecords(
        workouts: List<GamificationWorkout>,
        sets: List<GamificationSet>,
        month: YearMonth,
        zoneId: ZoneId,
    ): Int {
        val timeBySession = workouts.associate { it.sessionId to it.startedAt }
        return sets.filter { it.loadKg > 0.0 && it.sessionId in timeBySession }
            .groupBy { it.exerciseId }
            .count { (_, values) ->
                val before = values.filter {
                    YearMonth.from(Instant.ofEpochMilli(timeBySession.getValue(it.sessionId)).atZone(zoneId).toLocalDate()) < month
                }.maxOfOrNull { it.loadKg }
                val during = values.filter {
                    YearMonth.from(Instant.ofEpochMilli(timeBySession.getValue(it.sessionId)).atZone(zoneId).toLocalDate()) == month
                }.maxOfOrNull { it.loadKg }
                during != null && before != null && during > before
            }
    }

    private const val CONSISTENCY_WINDOW_WEEKS = 8L
}
