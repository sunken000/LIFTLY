package com.liftly.app.integration.healthconnect

import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import java.time.Instant
import java.util.Locale

object WorkoutHealthExportMapper {
    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_NOTES_LENGTH = 1_000

    fun prepare(
        session: SessionEntity,
        sessionSets: List<SessionSetEntity>,
    ): WorkoutHealthExportPreparation {
        if (session.isTestMode) {
            return WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.TEST_SESSION,
            )
        }

        val finishedAt = session.finishedAt
            ?: return WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.SESSION_NOT_FINISHED,
            )

        if (session.startedAt <= 0L || finishedAt <= session.startedAt) {
            return WorkoutHealthExportPreparation.Skipped(
                HealthConnectExportSkipReason.INVALID_TIME_RANGE,
            )
        }

        val completedSets = sessionSets.filter { it.completed }
        val exerciseCount = completedSets.map { it.exerciseId }.distinct().size
        val totalVolume = completedSets.sumOf { set ->
            if (set.reps > 0 && set.loadKg > 0.0) set.reps * set.loadKg else 0.0
        }
        val summary = buildString {
            append("Registrado pelo Liftly")
            append(" • ")
            append(completedSets.size)
            append(if (completedSets.size == 1) " série concluída" else " séries concluídas")
            append(" • ")
            append(exerciseCount)
            append(if (exerciseCount == 1) " exercício" else " exercícios")
            if (totalVolume > 0.0) {
                append(" • volume ")
                append(String.format(Locale.ROOT, "%.0f", totalVolume))
                append(" kg·reps")
            }
            session.notes.trim().takeIf { it.isNotEmpty() }?.let { sessionNotes ->
                append("\n")
                append(sessionNotes)
            }
        }.take(MAX_NOTES_LENGTH)

        return WorkoutHealthExportPreparation.Ready(
            WorkoutHealthExport(
                sessionId = session.id,
                title = session.workoutName.trim()
                    .ifEmpty { "Treino Liftly" }
                    .take(MAX_TITLE_LENGTH),
                startedAt = Instant.ofEpochMilli(session.startedAt),
                endedAt = Instant.ofEpochMilli(finishedAt),
                notes = summary,
                // Re-inserting the same session becomes an idempotent upsert. A later finish/update
                // has a larger version and therefore replaces the older Health Connect record.
                clientRecordVersion = finishedAt,
            ),
        )
    }
}
