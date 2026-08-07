package com.liftly.app.integration.discord

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liftly.app.LiftlyApplication
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.TimeUnit

class DiscordWorkoutExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val webhookUrl = inputData.getString(KEY_WEBHOOK_URL).orEmpty()
        val sessionId = inputData.getString(KEY_SESSION_ID).orEmpty()
        if (!DiscordWebhookUrlValidator.isValid(webhookUrl) || sessionId.isBlank()) {
            return failure("Configuração de exportação inválida.")
        }

        val app = applicationContext as? LiftlyApplication
            ?: return failure("Aplicação indisponível para exportação.")
        val dao = app.database.dao()
        val session = dao.session(sessionId) ?: return failure("Treino não encontrado.")
        if (session.isTestMode || session.finishedAt == null) return Result.success()

        val message = DiscordWorkoutMessageFactory.create(
            session = session,
            allSets = dao.sessionSets(sessionId),
            exercises = dao.allExercises(),
            profile = dao.profile(),
        ) ?: return failure("O treino não possui séries concluídas para exportar.")

        return when (val sendResult = DiscordWebhookSender().send(webhookUrl, message.json)) {
            is DiscordSendResult.Success -> Result.success()
            is DiscordSendResult.PermanentFailure -> failure(sendResult.reason)
            is DiscordSendResult.RetryableFailure -> {
                if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                    failure("Não foi possível contatar o Discord após várias tentativas.")
                } else {
                    sendResult.retryAfterMillis
                        ?.coerceAtMost(MAX_INLINE_RETRY_DELAY_MILLIS)
                        ?.let { delay(it) }
                    Result.retry()
                }
            }
        }
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR_MESSAGE to message.take(300)))

    companion object {
        internal const val KEY_WEBHOOK_URL = "discord_webhook_url"
        internal const val KEY_SESSION_ID = "session_id"
        const val KEY_ERROR_MESSAGE = "discord_export_error"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val MAX_INLINE_RETRY_DELAY_MILLIS = 30_000L
    }
}

object DiscordWorkoutExport {
    /**
     * Schedules an idempotent export for [sessionId]. The webhook is stored temporarily in
     * WorkManager's private database so the upload survives process death. It is never logged.
     * Returns null when the URL/session is invalid.
     */
    fun enqueue(context: Context, webhookUrl: String, sessionId: String): UUID? {
        val normalizedUrl = webhookUrl.trim()
        if (!DiscordWebhookUrlValidator.isValid(normalizedUrl) || sessionId.isBlank()) return null

        val request = OneTimeWorkRequestBuilder<DiscordWorkoutExportWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DiscordWorkoutExportWorker.KEY_WEBHOOK_URL, normalizedUrl)
                    .putString(DiscordWorkoutExportWorker.KEY_SESSION_ID, sessionId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "discord-workout-$sessionId",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return request.id
    }

    const val TAG = "discord-workout-export"
}
