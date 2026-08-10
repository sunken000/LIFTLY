package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

enum class ProgressInsightKind { POSITIVE, NEUTRAL, ATTENTION }

data class ProgressInsight(
    val id: String,
    val title: String,
    val detail: String,
    val kind: ProgressInsightKind,
)

data class ProgressReading(
    val summary: String,
    val insights: List<ProgressInsight>,
    val last28DayVolume: Double,
    val previous28DayVolume: Double,
    val volumeTrendPercent: Int?,
)

/** Interpreta o histórico em perguntas úteis, sem inventar diagnósticos fisiológicos. */
object ProgressInsightEngine {
    fun calculate(
        sessions: List<SessionEntity>,
        sets: List<SessionSetEntity>,
        exercises: List<ExerciseEntity>,
        weeklyGoal: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ProgressReading {
        val validSessions = sessions.filter { it.finishedAt != null && !it.isTestMode }
        val validIds = validSessions.mapTo(mutableSetOf()) { it.id }
        val completedSets = sets.filter { it.completed && it.sessionId in validIds }
        val currentStart = nowMillis - 28L * 86_400_000L
        val previousStart = currentStart - 28L * 86_400_000L
        val sessionTimes = validSessions.associate { it.id to it.startedAt }
        fun volumeBetween(start: Long, end: Long): Double = completedSets
            .asSequence()
            .filter { (sessionTimes[it.sessionId] ?: Long.MIN_VALUE) in start..<end }
            .sumOf { it.loadKg * it.reps }
        val currentVolume = volumeBetween(currentStart, nowMillis + 1)
        val previousVolume = volumeBetween(previousStart, currentStart)
        val trend = previousVolume.takeIf { it > 0.0 }
            ?.let { (((currentVolume - it) / it) * 100.0).roundToInt() }

        val insights = mutableListOf<ProgressInsight>()
        val currentWeekStart = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        val currentWeekCount = validSessions.count { it.startedAt >= currentWeekStart }
        val goal = weeklyGoal.coerceAtLeast(1)
        insights += if (currentWeekCount >= goal) {
            ProgressInsight("adherence", "Planejamento cumprido", "$currentWeekCount/$goal treinos concluídos nesta semana.", ProgressInsightKind.POSITIVE)
        } else {
            ProgressInsight("adherence", "Ritmo da semana", "$currentWeekCount/$goal treinos concluídos. Faltam ${(goal - currentWeekCount).coerceAtLeast(0)} para sua meta escolhida.", ProgressInsightKind.NEUTRAL)
        }

        if (trend != null) {
            insights += when {
                trend >= 10 -> ProgressInsight("volume", "Volume em alta", "As últimas quatro semanas tiveram $trend% mais volume que as quatro anteriores.", ProgressInsightKind.POSITIVE)
                trend <= -20 -> ProgressInsight("volume", "Volume caiu", "As últimas quatro semanas ficaram ${-trend}% abaixo do período anterior. Veja se isso foi planejado, deload ou perda de frequência.", ProgressInsightKind.ATTENTION)
                else -> ProgressInsight("volume", "Volume estável", "A variação de volume entre blocos de quatro semanas foi de ${if (trend >= 0) "+" else ""}$trend%.", ProgressInsightKind.NEUTRAL)
            }
        }

        val sessionById = validSessions.associateBy { it.id }
        val stalled = completedSets
            .filter { it.loadKg > 0.0 && it.reps > 0 && it.trackingMode.contains("Rep", ignoreCase = true) }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseSets) ->
                val exposures = exerciseSets
                    .groupBy { it.sessionId }
                    .mapNotNull { (sessionId, values) ->
                        val session = sessionById[sessionId] ?: return@mapNotNull null
                        session.startedAt to values.maxOf { estimatedOneRepMax(it.loadKg, it.reps) }
                    }
                    .sortedByDescending { it.first }
                if (exposures.size < 5) return@mapNotNull null
                val recent = exposures.take(3).maxOf { it.second }
                val earlier = exposures.drop(3).take(3).maxOfOrNull { it.second } ?: return@mapNotNull null
                val delta = if (earlier > 0.0) (recent - earlier) / earlier else 0.0
                if (delta > 0.015) return@mapNotNull null
                Triple(exerciseId, delta, exposures.first().first)
            }
            .maxByOrNull { it.third }
        stalled?.let { (exerciseId, _, _) ->
            val name = exercises.firstOrNull { it.id == exerciseId }?.name
                ?: completedSets.firstOrNull { it.exerciseId == exerciseId }?.exerciseName
                ?: "Um exercício"
            insights += ProgressInsight(
                "stall",
                "Possível estagnação em $name",
                "As exposições recentes não superaram de forma relevante o melhor nível das anteriores. Revise RIR, técnica e recuperação antes de simplesmente aumentar a carga.",
                ProgressInsightKind.ATTENTION,
            )
        }

        val exerciseById = exercises.associateBy { it.id }
        val recentMuscleSets = completedSets
            .filter { (sessionTimes[it.sessionId] ?: 0L) >= currentStart }
            .groupingBy { exerciseById[it.exerciseId]?.muscleGroup?.ifBlank { "Outros" } ?: "Outros" }
            .eachCount()
            .filterKeys { it != "Outros" }
        if (recentMuscleSets.size >= 3) {
            val maxEntry = recentMuscleSets.maxByOrNull { it.value }
            val minEntry = recentMuscleSets.minByOrNull { it.value }
            if (maxEntry != null && minEntry != null && maxEntry.value >= minEntry.value * 3 && maxEntry.value - minEntry.value >= 6) {
                insights += ProgressInsight(
                    "balance",
                    "Distribuição desigual de séries",
                    "${maxEntry.key} recebeu ${maxEntry.value} séries nas últimas quatro semanas, enquanto ${minEntry.key} recebeu ${minEntry.value}. Isso pode ser intencional; use a leitura para conferir seu objetivo.",
                    ProgressInsightKind.NEUTRAL,
                )
            }
        }

        val summary = when {
            validSessions.isEmpty() -> "Conclua alguns treinos para o Liftly começar a interpretar tendências."
            trend != null && trend >= 10 -> "Você aumentou o trabalho recente. O próximo passo é confirmar se carga, RIR e técnica também estão evoluindo."
            stalled != null -> "Sua frequência gera dados suficientes para detectar pontos de estagnação. Priorize qualidade antes de adicionar volume."
            currentWeekCount >= goal -> "Aderência está em dia. Use os gráficos abaixo para decidir onde progredir, manter ou reduzir."
            else -> "O foco agora é acumular sessões comparáveis; tendências ficam mais confiáveis conforme o histórico cresce."
        }
        return ProgressReading(summary, insights.take(4), currentVolume, previousVolume, trend)
    }

    private fun estimatedOneRepMax(loadKg: Double, reps: Int): Double =
        if (loadKg <= 0.0 || reps <= 0) 0.0 else loadKg * (1.0 + reps.coerceAtMost(30) / 30.0)
}
