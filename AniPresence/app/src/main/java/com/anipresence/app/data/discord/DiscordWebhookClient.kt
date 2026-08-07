package com.anipresence.app.data.discord

import com.anipresence.app.data.preferences.SettingsRepository
import com.anipresence.app.domain.model.AnimeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class DiscordWebhookClient(
    private val settings: SettingsRepository,
) : DiscordPresenceClient {
    private val mutableState = MutableStateFlow<DiscordConnectionState>(
        DiscordConnectionState.Disconnected
    )
    override val connectionState: StateFlow<DiscordConnectionState> = mutableState.asStateFlow()
    private var lastFingerprint: String? = null

    override suspend fun connect() {
        mutableState.value = DiscordConnectionState.Connecting
        val url = settings.webhook()
        if (url == null || !isValidWebhook(url)) {
            mutableState.value =
                DiscordConnectionState.Unavailable("Configure uma URL de webhook válida.")
            return
        }
        val check = request(url, "GET", null)
        mutableState.value = if (check.code in 200..299) {
            DiscordConnectionState.Connected("Webhook verificado")
        } else {
            DiscordConnectionState.Error(
                if (check.code == 404) "Webhook não encontrado ou removido."
                else "Não foi possível validar o webhook (HTTP ${check.code})."
            )
        }
    }

    override suspend fun updatePresence(activity: AnimeActivity) {
        val fingerprint = "${activity.animeTitle}|${activity.season}|${activity.episode}"
        if (fingerprint == lastFingerprint) return
        val webhook = settings.webhook() ?: return
        if (!isValidWebhook(webhook)) return
        val payload = JSONObject().put("content", format(activity)).toString()
        val currentId = settings.messageId()
        var result = if (currentId == null) {
            request("$webhook?wait=true", "POST", payload)
        } else {
            request("$webhook/messages/$currentId", "PATCH", payload)
        }
        if (currentId != null && result.code == 404) {
            settings.saveMessageId(null)
            result = request("$webhook?wait=true", "POST", payload)
        }
        if (result.code in 200..299) {
            if (currentId == null) {
                val id = runCatching { JSONObject(result.body).optString("id") }.getOrNull()
                if (!id.isNullOrBlank()) settings.saveMessageId(id)
            }
            lastFingerprint = fingerprint
            mutableState.value = DiscordConnectionState.Connected("Webhook (canal)")
        } else {
            mutableState.value = DiscordConnectionState.Error("Discord respondeu HTTP ${result.code}.")
        }
    }

    override suspend fun clearPresence() {
        val webhook = settings.webhook() ?: return
        val id = settings.messageId() ?: return
        val result = request("$webhook/messages/$id", "DELETE", null)
        if (result.code in 200..299 || result.code == 404) {
            settings.saveMessageId(null)
            lastFingerprint = null
        }
    }

    override suspend fun disconnect() {
        mutableState.value = DiscordConnectionState.Disconnected
        lastFingerprint = null
    }

    suspend fun sendTestMessage() {
        lastFingerprint = null
        updatePresence(
            AnimeActivity(
                animeTitle = "Teste do AniPresence",
                episode = null,
                season = null,
                sourceApp = "AniPresence",
                startedAt = null,
            )
        )
    }

    private suspend fun request(url: String, method: String, body: String?): Response =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }.getOrElse {
                mutableState.value = DiscordConnectionState.Error("Falha de rede ao acessar o Discord.")
                Response(-1, "")
            }
        }

    private fun format(activity: AnimeActivity): String = buildString {
        append("🍿 Assistindo ${activity.animeTitle}\n")
        val details = listOfNotNull(
            activity.season?.let { "Temporada $it" },
            activity.episode?.let { "Episódio $it" },
        )
        if (details.isNotEmpty()) append(details.joinToString(" · ")).append('\n')
        append("Detectado pelo AniPresence")
    }

    data class Response(val code: Int, val body: String)

    companion object {
        fun isValidWebhook(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" &&
                uri.host in setOf("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com") &&
                Regex("""^/api(?:/v\d+)?/webhooks/\d+/[\w.-]+/?$""").matches(uri.path)
        }.getOrDefault(false)
    }
}
