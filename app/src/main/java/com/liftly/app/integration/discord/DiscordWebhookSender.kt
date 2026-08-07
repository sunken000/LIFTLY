package com.liftly.app.integration.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

sealed interface DiscordSendResult {
    data class Success(val statusCode: Int) : DiscordSendResult
    data class RetryableFailure(val statusCode: Int?, val retryAfterMillis: Long?) : DiscordSendResult
    data class PermanentFailure(val statusCode: Int?, val reason: String) : DiscordSendResult
}

class DiscordWebhookSender(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
) {
    suspend fun send(webhookUrl: String, messageJson: String): DiscordSendResult = withContext(Dispatchers.IO) {
        if (!DiscordWebhookUrlValidator.isValid(webhookUrl)) {
            return@withContext DiscordSendResult.PermanentFailure(null, "Webhook do Discord inválido.")
        }
        if (messageJson.toByteArray(StandardCharsets.UTF_8).size > MAX_REQUEST_BYTES) {
            return@withContext DiscordSendResult.PermanentFailure(null, "Resumo do treino excede o limite de envio.")
        }

        var connection: HttpURLConnection? = null
        try {
            val body = messageJson.toByteArray(StandardCharsets.UTF_8)
            connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                doOutput = true
                useCaches = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Liftly-Android/1.0")
                setFixedLengthStreamingMode(body.size)
            }
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            when {
                status in 200..299 -> DiscordSendResult.Success(status)
                status == 408 || status == 425 || status == 429 || status in 500..599 ->
                    DiscordSendResult.RetryableFailure(status, connection.retryAfterMillis())

                status == 401 || status == 403 || status == 404 ->
                    DiscordSendResult.PermanentFailure(status, "Webhook ausente, removido ou sem permissão.")

                else -> DiscordSendResult.PermanentFailure(status, "O Discord recusou o resumo do treino.")
            }
        } catch (_: Exception) {
            // Exception messages can contain the webhook token, so they must never be logged or returned.
            DiscordSendResult.RetryableFailure(null, null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun HttpURLConnection.retryAfterMillis(): Long? {
        val raw = getHeaderField("Retry-After")?.trim()?.toDoubleOrNull() ?: return null
        // Discord commonly returns seconds (possibly fractional). Cap defensive waiting at five minutes.
        return (raw * 1_000.0).toLong().coerceIn(1_000L, 5 * 60_000L)
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 64 * 1_024
    }
}
