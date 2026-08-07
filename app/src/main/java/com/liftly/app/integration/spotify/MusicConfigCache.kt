package com.liftly.app.integration.spotify

import android.content.Context

internal data class CachedMusicConfig(
    val config: SpotifyPlaylistConfig,
    val etag: String?,
    val lastSuccessfulRefreshAtEpochMillis: Long?,
)

/** Dedicated cache. It never stores oEmbed images or authentication/session data. */
internal class MusicConfigCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): CachedMusicConfig? = runCatching {
        if (!preferences.contains(KEY_REVISION) || !preferences.contains(KEY_SPOTIFY_ID)) return null
        val updatedAt = preferences.getString(KEY_UPDATED_AT, null)
        val title = preferences.getString(KEY_TITLE, null)
        val description = preferences.getString(KEY_DESCRIPTION, null)
        val config = SpotifyPlaylistConfig(
            schemaVersion = preferences.getInt(KEY_SCHEMA_VERSION, -1),
            revision = preferences.getLong(KEY_REVISION, -1),
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            updatedAt = updatedAt,
            spotifyId = preferences.getString(KEY_SPOTIFY_ID, null).orEmpty(),
            title = title,
            description = description,
        )
        CachedMusicConfig(
            config = config,
            etag = preferences.getString(KEY_ETAG, null),
            lastSuccessfulRefreshAtEpochMillis = preferences
                .getLong(KEY_LAST_SUCCESS_EPOCH_MILLIS, -1)
                .takeIf { it >= 0 },
        )
    }.getOrElse {
        preferences.edit().clear().apply()
        null
    }

    fun save(
        config: SpotifyPlaylistConfig,
        etag: String?,
        lastSuccessfulRefreshAtEpochMillis: Long,
    ) {
        val editor = preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, config.schemaVersion)
            .putLong(KEY_REVISION, config.revision)
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_SPOTIFY_ID, config.spotifyId)
            .putLong(KEY_LAST_SUCCESS_EPOCH_MILLIS, lastSuccessfulRefreshAtEpochMillis)
        editor.putNullableString(KEY_UPDATED_AT, config.updatedAt)
        editor.putNullableString(KEY_TITLE, config.title)
        editor.putNullableString(KEY_DESCRIPTION, config.description)
        editor.putNullableString(KEY_ETAG, etag)
        editor.apply()
    }

    fun updateEtag(etag: String) {
        preferences.edit().putString(KEY_ETAG, etag).apply()
    }

    private fun android.content.SharedPreferences.Editor.putNullableString(
        key: String,
        value: String?,
    ): android.content.SharedPreferences.Editor = if (value == null) remove(key) else putString(key, value)

    private companion object {
        const val PREFERENCES_NAME = "liftly_music_cache"
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_REVISION = "revision"
        const val KEY_ENABLED = "enabled"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_SPOTIFY_ID = "spotify_id"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_ETAG = "etag"
        const val KEY_LAST_SUCCESS_EPOCH_MILLIS = "last_success_epoch_millis"
    }
}
