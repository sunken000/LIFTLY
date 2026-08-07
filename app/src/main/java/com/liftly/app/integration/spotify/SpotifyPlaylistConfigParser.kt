package com.liftly.app.integration.spotify

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

sealed interface SpotifyConfigParseResult {
    data class Success(val config: SpotifyPlaylistConfig) : SpotifyConfigParseResult
    data class Invalid(val reason: String) : SpotifyConfigParseResult
}

object SpotifyPlaylistConfigParser {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_CONFIG_BYTES = 64 * 1_024
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MAX_UPDATED_AT_LENGTH = 64

    private val rootKeys = setOf("schemaVersion", "revision", "enabled", "updatedAt", "playlist")
    private val playlistKeys = setOf("spotifyId", "title", "description")

    fun parse(json: String): SpotifyConfigParseResult {
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_CONFIG_BYTES) {
            return SpotifyConfigParseResult.Invalid("Configuração excede 64 KB.")
        }
        return runCatching {
            val root = StrictJson.parse(json).asObject()
            require(root.keys == root.keys.intersect(rootKeys)) { "Campo raiz desconhecido." }
            require(root.keys.containsAll(setOf("schemaVersion", "revision", "enabled", "playlist"))) {
                "Campo raiz obrigatório ausente."
            }

            val schemaVersion = root.getValue("schemaVersion").asInt()
            require(schemaVersion == SUPPORTED_SCHEMA_VERSION) { "Versão de schema incompatível." }
            val revision = root.getValue("revision").asLong()
            require(revision >= 0) { "Revisão negativa." }
            val enabled = root.getValue("enabled").asBoolean()
            val updatedAt = root["updatedAt"]?.asString()?.also {
                require(isValidUpdatedAt(it)) { "updatedAt inválido." }
            }

            val playlist = root.getValue("playlist").asObject()
            require(playlist.keys == playlist.keys.intersect(playlistKeys)) { "Campo de playlist desconhecido." }
            require("spotifyId" in playlist) { "Spotify ID ausente." }
            val spotifyId = playlist.getValue("spotifyId").asString()
            require(SpotifyPlaylistId.isValid(spotifyId)) { "Spotify ID inválido." }
            val title = playlist["title"]?.asString()?.also {
                require(isValidTitle(it)) { "Título inválido." }
            }
            val description = playlist["description"]?.asString()?.also {
                require(isValidDescription(it)) { "Descrição inválida." }
            }

            SpotifyPlaylistConfig(
                schemaVersion = schemaVersion,
                revision = revision,
                enabled = enabled,
                updatedAt = updatedAt,
                spotifyId = spotifyId,
                title = title,
                description = description,
            )
        }.fold(
            onSuccess = SpotifyConfigParseResult::Success,
            onFailure = { SpotifyConfigParseResult.Invalid(it.message ?: "JSON inválido.") },
        )
    }

    internal fun isValidTitle(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_TITLE_LENGTH && value.none(Char::isISOControl)

    internal fun isValidDescription(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_DESCRIPTION_LENGTH && value.none(Char::isISOControl)

    internal fun isValidUpdatedAt(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_UPDATED_AT_LENGTH || value.any(Char::isISOControl)) {
            return false
        }
        return runCatching {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.isSuccess
    }

    private fun JsonValue.asObject(): Map<String, JsonValue> =
        (this as? JsonValue.ObjectValue)?.values ?: error("Objeto JSON esperado.")

    private fun JsonValue.asString(): String =
        (this as? JsonValue.StringValue)?.value ?: error("String JSON esperada.")

    private fun JsonValue.asBoolean(): Boolean =
        (this as? JsonValue.BooleanValue)?.value ?: error("Booleano JSON esperado.")

    private fun JsonValue.asInt(): Int {
        val raw = (this as? JsonValue.NumberValue)?.rawValue ?: error("Número JSON esperado.")
        require(INTEGER_PATTERN.matches(raw)) { "Inteiro esperado." }
        return raw.toIntOrNull() ?: error("Inteiro fora do limite.")
    }

    private fun JsonValue.asLong(): Long {
        val raw = (this as? JsonValue.NumberValue)?.rawValue ?: error("Número JSON esperado.")
        require(INTEGER_PATTERN.matches(raw)) { "Inteiro esperado." }
        return raw.toLongOrNull() ?: error("Inteiro fora do limite.")
    }

    private val INTEGER_PATTERN = Regex("^-?(?:0|[1-9]\\d*)$")
}
