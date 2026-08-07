package com.liftly.app

import android.app.Application
import com.liftly.app.data.LiftlyDatabase
import com.liftly.app.data.LiftlyRepository
import com.liftly.app.data.PreferencesRepository
import com.liftly.app.integration.spotify.MusicRepository
import com.liftly.app.integration.spotify.PersonalSpotifyPlaylistRepository
import java.security.KeyStore

class LiftlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        clearRemovedSpotifyAccountData()
    }

    val database by lazy { LiftlyDatabase.create(this) }
    val repository by lazy { LiftlyRepository(database, this) }
    val preferencesRepository by lazy { PreferencesRepository(this) }
    val musicRepository by lazy { MusicRepository(this) }
    val personalSpotifyPlaylistRepository by lazy { PersonalSpotifyPlaylistRepository(this) }

    /** Removes credentials left by versions that still offered Spotify account connection. */
    private fun clearRemovedSpotifyAccountData() {
        runCatching {
            getSharedPreferences(LEGACY_SPOTIFY_AUTH_PREFERENCES, MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(LEGACY_SPOTIFY_KEY_ALIAS)) {
                keyStore.deleteEntry(LEGACY_SPOTIFY_KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val LEGACY_SPOTIFY_AUTH_PREFERENCES = "liftly_spotify_auth"
        const val LEGACY_SPOTIFY_KEY_ALIAS = "liftly_spotify_oauth_v1"
    }
}
