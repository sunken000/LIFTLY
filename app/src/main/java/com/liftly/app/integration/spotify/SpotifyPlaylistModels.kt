package com.liftly.app.integration.spotify

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

const val FALLBACK_SPOTIFY_PLAYLIST_ID = "7jOh9hQGVTDjtWyIfYe5OY"

@ConsistentCopyVisibility
data class SpotifyPlaylistConfig internal constructor(
    val schemaVersion: Int,
    val revision: Long,
    val enabled: Boolean,
    val updatedAt: String?,
    val spotifyId: String,
    val title: String?,
    val description: String?,
) {
    init {
        require(schemaVersion == SpotifyPlaylistConfigParser.SUPPORTED_SCHEMA_VERSION)
        require(revision >= 0)
        require(SpotifyPlaylistId.isValid(spotifyId))
        require(updatedAt == null || SpotifyPlaylistConfigParser.isValidUpdatedAt(updatedAt))
        require(title == null || SpotifyPlaylistConfigParser.isValidTitle(title))
        require(description == null || SpotifyPlaylistConfigParser.isValidDescription(description))
    }

    val links: SpotifyPlaylistLinks
        get() = SpotifyPlaylistLinks.fromId(spotifyId)

    companion object {
        fun fallback(): SpotifyPlaylistConfig = SpotifyPlaylistConfig(
            schemaVersion = SpotifyPlaylistConfigParser.SUPPORTED_SCHEMA_VERSION,
            revision = 0,
            enabled = true,
            updatedAt = null,
            spotifyId = FALLBACK_SPOTIFY_PLAYLIST_ID,
            title = "침몰한",
            description = "Playlist escolhida para acompanhar os treinos no Liftly.",
        )
    }
}

object SpotifyPlaylistId {
    const val LENGTH = 22
    private val pattern = Regex("^[A-Za-z0-9]{$LENGTH}$")

    fun isValid(value: String): Boolean = pattern.matches(value)
}

@ConsistentCopyVisibility
data class SpotifyPlaylistLinks private constructor(
    val spotifyId: String,
    val spotifyUri: String,
    val webUrl: String,
    val embedUrl: String,
    val oEmbedUrl: String,
) {
    companion object {
        fun fromId(spotifyId: String): SpotifyPlaylistLinks {
            require(SpotifyPlaylistId.isValid(spotifyId)) { "Spotify playlist ID inválido." }
            val webUrl = "https://open.spotify.com/playlist/$spotifyId"
            return SpotifyPlaylistLinks(
                spotifyId = spotifyId,
                spotifyUri = "spotify:playlist:$spotifyId",
                webUrl = webUrl,
                embedUrl = "https://open.spotify.com/embed/playlist/$spotifyId",
                oEmbedUrl = "https://open.spotify.com/oembed?url=" +
                    URLEncoder.encode(webUrl, StandardCharsets.UTF_8.name()),
            )
        }

        /**
         * Accepts only a canonical Spotify playlist URI or public HTTPS URL. Query parameters are
         * ignored because Spotify share links commonly append `si`; every other URL component is
         * deliberately strict so lookalike hosts cannot pass validation.
         */
        fun extractId(reference: String): String? {
            val candidate = reference.trim()
            val uriMatch = SPOTIFY_URI.matchEntire(candidate)
            if (uriMatch != null) return uriMatch.groupValues[1]

            val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                host != "open.spotify.com" ||
                uri.port != -1 ||
                uri.userInfo != null ||
                uri.fragment != null
            ) {
                return null
            }
            val match = WEB_PATH.matchEntire(uri.rawPath.orEmpty()) ?: return null
            return match.groupValues[1].takeIf(SpotifyPlaylistId::isValid)
        }

        private val SPOTIFY_URI = Regex("^spotify:playlist:([A-Za-z0-9]{22})$")
        private val WEB_PATH = Regex("^/playlist/([A-Za-z0-9]{22})/?$")
    }
}

object SpotifyEndpointValidator {
    private const val MAX_URL_LENGTH = 2_048

    /** Validates the developer-controlled public JSON endpoint. */
    fun isValidRemoteConfigUrl(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_URL_LENGTH) return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.fragment == null &&
            uri.rawPath.orEmpty().isNotEmpty() &&
            isPublicDnsName(host)
    }

    /** Spotify oEmbed thumbnails must stay on Spotify's HTTPS image CDN. */
    fun isValidThumbnailUrl(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_URL_LENGTH) return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val isSpotifyCdn = host == "i.scdn.co" ||
            host.endsWith(".scdn.co") ||
            host.endsWith(".spotifycdn.com")
        return uri.scheme.equals("https", ignoreCase = true) &&
            isSpotifyCdn &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.fragment == null
    }

    private fun isPublicDnsName(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (!HOST_PATTERN.matches(host) || !host.contains('.')) return false
        if (IPV4_PATTERN.matches(host)) return false
        // URI.host includes brackets for IPv6 on some runtimes and excludes them on others.
        if (host.contains(':') || host.startsWith('[') || host.endsWith(']')) return false
        return true
    }

    private val HOST_PATTERN = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")
    private val IPV4_PATTERN = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
}

object SpotifyRevisionPolicy {
    /** Equal revisions are idempotent; a lower revision can never replace cached data. */
    fun canReplace(currentRevision: Long?, candidateRevision: Long): Boolean =
        candidateRevision >= 0 && (currentRevision == null || candidateRevision >= currentRevision)
}
