package com.liftly.app.integration.discord

import java.net.URI
import java.util.Locale

/** Strictly limits outbound requests to Discord's official webhook endpoints. */
object DiscordWebhookUrlValidator {
    private const val MAX_URL_LENGTH = 2_048
    private val allowedHosts = setOf("discord.com", "canary.discord.com", "ptb.discord.com")
    private val webhookPath = Regex("^/api(?:/v\\d+)?/webhooks/\\d+/[A-Za-z0-9._-]+$")

    fun isValid(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_URL_LENGTH) return false

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            host in allowedHosts &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            webhookPath.matches(uri.rawPath.orEmpty())
    }
}
