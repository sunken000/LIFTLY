package com.liftly.app.integration.spotify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonalSpotifyPlaylist(
    val spotifyId: String,
    val title: String,
    val addedAtEpochMillis: Long,
) {
    val links: SpotifyPlaylistLinks
        get() = SpotifyPlaylistLinks.fromId(spotifyId)
}

sealed interface PersonalPlaylistSaveResult {
    data object Added : PersonalPlaylistSaveResult
    data object Updated : PersonalPlaylistSaveResult
    data object InvalidLink : PersonalPlaylistSaveResult
    data object LimitReached : PersonalPlaylistSaveResult
}

/** Local-only library. It never reads the user's Spotify account or sends playlist data to Liftly. */
class PersonalSpotifyPlaylistRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutablePlaylists = MutableStateFlow(load())
    private val mutableSelectedPlaylistId = MutableStateFlow(loadSelectedPlaylistId())

    val playlists: StateFlow<List<PersonalSpotifyPlaylist>> = mutablePlaylists.asStateFlow()
    val selectedPlaylistId: StateFlow<String?> = mutableSelectedPlaylistId.asStateFlow()

    fun save(reference: String, title: String, nowMillis: Long = System.currentTimeMillis()): PersonalPlaylistSaveResult {
        val spotifyId = PersonalSpotifyPlaylistInput.extractId(reference)
            ?: return PersonalPlaylistSaveResult.InvalidLink
        val existing = mutablePlaylists.value.firstOrNull { it.spotifyId == spotifyId }
        if (existing == null && mutablePlaylists.value.size >= MAX_PLAYLISTS) {
            return PersonalPlaylistSaveResult.LimitReached
        }
        val normalizedTitle = PersonalSpotifyPlaylistInput.titleOrDefault(title, spotifyId)
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids += spotifyId
        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(titleKey(spotifyId), normalizedTitle)
            .putLong(addedAtKey(spotifyId), existing?.addedAtEpochMillis ?: nowMillis)
            .putString(KEY_SELECTED_ID, spotifyId)
            .apply()
        mutablePlaylists.value = load()
        mutableSelectedPlaylistId.value = spotifyId
        return if (existing == null) PersonalPlaylistSaveResult.Added else PersonalPlaylistSaveResult.Updated
    }

    fun remove(spotifyId: String) {
        if (!SpotifyPlaylistId.isValid(spotifyId)) return
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        if (!ids.remove(spotifyId)) return
        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(titleKey(spotifyId))
            .remove(addedAtKey(spotifyId))
            .apply()
        mutablePlaylists.value = load()
        if (mutableSelectedPlaylistId.value == spotifyId) {
            val next = mutablePlaylists.value.firstOrNull()?.spotifyId
            preferences.edit().putNullableString(KEY_SELECTED_ID, next).apply()
            mutableSelectedPlaylistId.value = next
        }
    }

    fun select(spotifyId: String) {
        if (mutablePlaylists.value.none { it.spotifyId == spotifyId }) return
        preferences.edit().putString(KEY_SELECTED_ID, spotifyId).apply()
        mutableSelectedPlaylistId.value = spotifyId
    }

    private fun loadSelectedPlaylistId(): String? {
        val id = preferences.getString(KEY_SELECTED_ID, null)
        return id?.takeIf(SpotifyPlaylistId::isValid)
    }

    private fun android.content.SharedPreferences.Editor.putNullableString(
        key: String,
        value: String?,
    ): android.content.SharedPreferences.Editor = if (value == null) remove(key) else putString(key, value)

    private fun load(): List<PersonalSpotifyPlaylist> {
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val validIds = ids.filter(SpotifyPlaylistId::isValid).take(MAX_PLAYLISTS)
        return validIds.map { spotifyId ->
            PersonalSpotifyPlaylist(
                spotifyId = spotifyId,
                title = PersonalSpotifyPlaylistInput.titleOrDefault(
                    preferences.getString(titleKey(spotifyId), null).orEmpty(),
                    spotifyId,
                ),
                addedAtEpochMillis = preferences.getLong(addedAtKey(spotifyId), 0L).coerceAtLeast(0L),
            )
        }.sortedWith(compareByDescending<PersonalSpotifyPlaylist> { it.addedAtEpochMillis }.thenBy { it.title })
    }

    private companion object {
        const val PREFERENCES_NAME = "liftly_personal_spotify_playlists"
        const val KEY_IDS = "playlist_ids"
        const val KEY_SELECTED_ID = "selected_playlist_id"
        const val MAX_PLAYLISTS = 50
        fun titleKey(spotifyId: String) = "title_$spotifyId"
        fun addedAtKey(spotifyId: String) = "added_at_$spotifyId"
    }
}

object PersonalSpotifyPlaylistInput {
    private const val MAX_REFERENCE_LENGTH = 2_048
    private const val MAX_TITLE_LENGTH = 80

    fun extractId(reference: String): String? {
        val candidate = reference.trim()
        if (candidate.isEmpty() || candidate.length > MAX_REFERENCE_LENGTH) return null
        return candidate.takeIf(SpotifyPlaylistId::isValid) ?: SpotifyPlaylistLinks.extractId(candidate)
    }

    fun titleOrDefault(rawTitle: String, spotifyId: String): String {
        val normalized = rawTitle.trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_TITLE_LENGTH)
        return normalized.ifBlank { "Minha playlist ${spotifyId.take(6)}" }
    }
}
