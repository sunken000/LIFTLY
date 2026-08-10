package com.liftly.app.domain

import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.SessionSummary
import kotlin.math.roundToInt

data class WorkoutReportExercise(
    val exerciseId: String,
    val name: String,
    val completedSets: Int,
    val bestLoadKg: Double,
    val bestReps: Int,
    val previousBestLoadKg: Double?,
    val loadDeltaKg: Double?,
    val averageRir: Double?,
    val maxPain: Int,
    val personalRecord: Boolean,
)

data class WorkoutReport(
    val sessionId: String,
    val workoutName: String,
    val durationMinutes: Int,
    val completedSets: Int,
    val totalSets: Int,
    val volumeKg: Double,
    val previousVolumeKg: Double?,
    val volumeDeltaPercent: Int?,
    val personalRecords: Int,
    val rewardXp: Long,
    val rewardCoins: Long,
    val coachHeadline: String,
    val coachDetail: String,
    val exercises: List<WorkoutReportExercise>,
    val isTestMode: Boolean,
)

/** Pure report builder used by both the in-app summary and the share card. */
object WorkoutReportBuilder {
    fun build(
        summary: SessionSummary,
        currentSets: List<SessionSetEntity>,
        allSets: List<SessionSetEntity>,
        sessions: List<SessionEntity>,
    ): WorkoutReport {
        val finishedAt = summary.finishedAt ?: summary.startedAt
        val durationMinutes = ((finishedAt - summary.startedAt).coerceAtLeast(0L) / 60_000.0)
            .roundToInt()
            .coerceAtLeast(1)
        val previousSession = sessions
            .asSequence()
            .filter { it.id != summary.sessionId && it.workoutName == summary.workoutName && it.finishedAt != null && !it.isTestMode }
            .maxByOrNull { it.startedAt }
        val previousVolume = previousSession?.let { previous ->
            allSets.asSequence()
                .filter { it.sessionId == previous.id && it.completed }
                .sumOf { it.loadKg * it.reps }
        }
        val volumeDelta = previousVolume
            ?.takeIf { it > 0.0 }
            ?.let { (((summary.volume - it) / it) * 100.0).roundToInt() }

        val previousFinishedIds = sessions
            .filter { it.id != summary.sessionId && it.finishedAt != null && !it.isTestMode }
            .mapTo(mutableSetOf()) { it.id }
        val previousSets = allSets.filter { it.sessionId in previousFinishedIds && it.completed }
        val exercises = currentSets
            .filter { it.completed }
            .groupBy { it.exerciseId }
            .map { (exerciseId, sets) ->
                val best = sets.maxWithOrNull(compareBy<SessionSetEntity> { it.loadKg }.thenBy { it.reps }) ?: sets.first()
                val previousBest = previousSets
                    .asSequence()
                    .filter { it.exerciseId == exerciseId }
                    .maxOfOrNull { it.loadKg }
                val isPr = previousBest != null && best.loadKg > previousBest + 0.0001
                WorkoutReportExercise(
                    exerciseId = exerciseId,
                    name = best.exerciseName,
                    completedSets = sets.size,
                    bestLoadKg = best.loadKg,
                    bestReps = best.reps,
                    previousBestLoadKg = previousBest,
                    loadDeltaKg = previousBest?.let { best.loadKg - it },
                    averageRir = sets.mapNotNull { it.rir }.takeIf { it.isNotEmpty() }?.average(),
                    maxPain = sets.maxOf { it.painLevel },
                    personalRecord = isPr,
                )
            }
            .sortedWith(compareByDescending<WorkoutReportExercise> { it.personalRecord }.thenBy { it.name })

        val prs = exercises.count { it.personalRecord }
        val maxPain = exercises.maxOfOrNull { it.maxPain } ?: 0
        val avgRir = currentSets.filter { it.completed }.mapNotNull { it.rir }.takeIf { it.isNotEmpty() }?.average()
        val headline: String
        val detail: String
        when {
            maxPain >= 7 -> {
                headline = "Priorize segurança na próxima sessão"
                detail = "Houve registro de dor alta. O Liftly não aumenta automaticamente a prescrição nesses movimentos."
            }
            prs > 0 -> {
                headline = "$prs recorde${if (prs == 1) " pessoal" else "s pessoais"} hoje"
                detail = "A progressão foi detectada pelo seu próprio histórico, sem comparar você com outras pessoas."
            }
            volumeDelta != null && volumeDelta >= 8 -> {
                headline = "Mais trabalho com controle"
                detail = "O volume ficou $volumeDelta% acima do treino anterior com a mesma ficha."
            }
            avgRir != null && avgRir < 1.0 -> {
                headline = "Sessão muito próxima da falha"
                detail = "O esforço médio ficou alto. O Coach vai preservar ou reduzir a próxima prescrição quando necessário."
            }
            else -> {
                headline = "Sessão consolidada"
                detail = "O histórico foi atualizado e o Coach usará estes dados para preparar a próxima exposição."
            }
        }

        return WorkoutReport(
            sessionId = summary.sessionId,
            workoutName = summary.workoutName,
            durationMinutes = durationMinutes,
            completedSets = summary.completedSets,
            totalSets = summary.totalSets,
            volumeKg = summary.volume,
            previousVolumeKg = previousVolume,
            volumeDeltaPercent = volumeDelta,
            personalRecords = prs,
            rewardXp = summary.rewardXp,
            rewardCoins = summary.rewardCoins,
            coachHeadline = headline,
            coachDetail = detail,
            exercises = exercises,
            isTestMode = summary.isTestMode,
        )
    }
}
