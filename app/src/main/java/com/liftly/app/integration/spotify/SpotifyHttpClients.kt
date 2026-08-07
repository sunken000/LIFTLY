package com.liftly.app.integration.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class RemoteSpotifyConfig(
    val config: SpotifyPlaylistConfig,
    val etag: String?,
)

internal sealed interface RemoteConfigFetchResult {
    data class Success(val remote: RemoteSpotifyConfig) : RemoteConfigFetchResult
    data class NotModified(val etag: String?) : RemoteConfigFetchResult
    data object NotConfigured : RemoteConfigFetchResult
    data object InvalidEndpoint : RemoteConfigFetchResult
    data class Failure(val statusCode: Int?) : RemoteConfigFetchResult
    data class InvalidPayload(val reason: String) : RemoteConfigFetchResult
}

internal class SpotifyRemoteConfigClient(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    suspend fun fetch(endpoint: String, cachedEtag: String?): RemoteConfigFetchResult =
        withContext(Dispatchers.IO) {
            if (endpoint.isBlank()) return@withContext RemoteConfigFetchResult.NotConfigured
            if (!SpotifyEndpointValidator.isValidRemoteConfigUrl(endpoint)) {
                return@withContext RemoteConfigFetchResult.InvalidEndpoint
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint.trim()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    useCaches = false
                    connectTimeout = connectTimeoutMillis
                    readTimeout = readTimeoutMillis
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("User-Agent", "Liftly-Android/1.0")
                    cachedEtag?.takeIf(::isSafeEtag)?.let { setRequestProperty("If-None-Match", it) }
                }
                val status = connection.responseCode
                val responseEtag = connection.getHeaderField("ETag")?.takeIf(::isSafeEtag)
                when (status) {
                    HttpURLConnection.HTTP_NOT_MODIFIED ->
                        RemoteConfigFetchResult.NotModified(responseEtag)

                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.readUtf8Body(SpotifyPlaylistConfigParser.MAX_CONFIG_BYTES)
                            ?: return@withContext RemoteConfigFetchResult.InvalidPayload(
                                "Resposta maior que 64 KB ou UTF-8 inválido.",
                            )
                        when (val parsed = SpotifyPlaylistConfigParser.parse(body)) {
                            is SpotifyConfigParseResult.Success -> RemoteConfigFetchResult.Success(
                                RemoteSpotifyConfig(parsed.config, responseEtag),
                            )

                            is SpotifyConfigParseResult.Invalid ->
                                RemoteConfigFetchResult.InvalidPayload(parsed.reason)
                        }
                    }

                    else -> RemoteConfigFetchResult.Failure(status)
                }
            } catch (_: Exception) {
                RemoteConfigFetchResult.Failure(null)
            } finally {
                connection?.disconnect()
            }
        }

    private fun isSafeEtag(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_ETAG_LENGTH && value.none(Char::isISOControl)

    private companion object {
        const val MAX_ETAG_LENGTH = 512
    }
}

data class SpotifyOEmbedMetadata(
    val title: String?,
    val thumbnailUrl: String?,
)

internal object SpotifyOEmbedParser {
    private const val MAX_OEMBED_TITLE_LENGTH = 200

    fun parse(json: String): SpotifyOEmbedMetadata? = runCatching {
        if (json.toByteArray(StandardCharsets.UTF_8).size > SpotifyPlaylistConfigParser.MAX_CONFIG_BYTES) {
            return null
        }
        val root = (StrictJson.parse(json) as? JsonValue.ObjectValue)?.values ?: return null
        val title = (root["title"] as? JsonValue.StringValue)?.value?.takeIf {
            it.isNotBlank() && it.length <= MAX_OEMBED_TITLE_LENGTH && it.none(Char::isISOControl)
        }
        val thumbnail = (root["thumbnail_url"] as? JsonValue.StringValue)?.value
            ?.takeIf(SpotifyEndpointValidator::isValidThumbnailUrl)
        SpotifyOEmbedMetadata(title = title, thumbnailUrl = thumbnail)
    }.getOrNull()
}

internal class SpotifyOEmbedClient(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    suspend fun fetch(spotifyId: String): SpotifyOEmbedMetadata? = withContext(Dispatchers.IO) {
        if (!SpotifyPlaylistId.isValid(spotifyId)) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(SpotifyPlaylistLinks.fromId(spotifyId).oEmbedUrl)
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Liftly-Android/1.0")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.readUtf8Body(SpotifyPlaylistConfigParser.MAX_CONFIG_BYTES)
                ?: return@withContext null
            SpotifyOEmbedParser.parse(body)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

private fun HttpURLConnection.readUtf8Body(maxBytes: Int): String? {
    val declaredLength = getHeaderFieldLong("Content-Length", -1L)
    if (declaredLength > maxBytes) return null
    val bytes = inputStream.use { input ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1_024))
        val buffer = ByteArray(4 * 1_024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) return null
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    return runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}
