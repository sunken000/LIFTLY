package com.liftly.app.integration.spotify

import android.content.Context
import com.liftly.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MusicConfigSource {
    FALLBACK,
    CACHE,
    REMOTE,
}

enum class MusicRefreshIssue {
    NOT_CONFIGURED,
    INVALID_ENDPOINT,
    NETWORK,
    INVALID_PAYLOAD,
    ROLLBACK_REJECTED,
}

data class MusicState(
    val config: SpotifyPlaylistConfig,
    val source: MusicConfigSource,
    val remoteConfigured: Boolean,
    val metadata: SpotifyOEmbedMetadata? = null,
    val isRefreshing: Boolean = false,
    val lastCheckedAtEpochMillis: Long? = null,
    val lastSuccessfulRefreshAtEpochMillis: Long? = null,
    val issue: MusicRefreshIssue? = null,
) {
    val enabled: Boolean
        get() = config.enabled
    val isOffline: Boolean
        get() = issue == MusicRefreshIssue.NETWORK
    val lastFetchedAtEpochMillis: Long?
        get() = lastCheckedAtEpochMillis
    val displayTitle: String
        get() = metadata?.title ?: config.title ?: "Playlist no Spotify"
    val description: String?
        get() = config.description
    val thumbnailUrl: String?
        get() = metadata?.thumbnailUrl
    val links: SpotifyPlaylistLinks
        get() = config.links
}

sealed interface MusicRefreshResult {
    data class Updated(val state: MusicState) : MusicRefreshResult
    data class NotModified(val state: MusicState) : MusicRefreshResult
    data class KeptCurrent(val state: MusicState, val issue: MusicRefreshIssue) : MusicRefreshResult
}

/**
 * Pull-based repository for the owner-controlled playlist configuration. The UI decides when to
 * refresh; this class intentionally creates no timers, jobs, services or infinite polling loops.
 */
class MusicRepository(
    context: Context,
    private val remoteConfigUrl: String = BuildConfig.MUSIC_CONFIG_URL,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val cache = MusicConfigCache(context)
    private val remoteClient = SpotifyRemoteConfigClient()
    private val oEmbedClient = SpotifyOEmbedClient()
    private val refreshMutex = Mutex()
    private val hasValidRemoteEndpoint = SpotifyEndpointValidator.isValidRemoteConfigUrl(remoteConfigUrl)
    private val cachedAtStartup = cache.load()
    private val initialConfig = cachedAtStartup?.config ?: SpotifyPlaylistConfig.fallback()
    private val mutableState = MutableStateFlow(
        MusicState(
            config = initialConfig,
            source = if (cachedAtStartup == null) MusicConfigSource.FALLBACK else MusicConfigSource.CACHE,
            remoteConfigured = hasValidRemoteEndpoint,
            lastSuccessfulRefreshAtEpochMillis = cachedAtStartup?.lastSuccessfulRefreshAtEpochMillis,
        ),
    )

    val state: StateFlow<MusicState> = mutableState.asStateFlow()

    suspend fun refresh(): MusicRefreshResult = refreshMutex.withLock {
        mutableState.value = mutableState.value.copy(isRefreshing = true, issue = null)
        val cached = cache.load()
        return@withLock when (val result = remoteClient.fetch(remoteConfigUrl, cached?.etag)) {
            is RemoteConfigFetchResult.Success -> acceptRemote(result.remote, cached)
            is RemoteConfigFetchResult.NotModified -> {
                result.etag?.let(cache::updateEtag)
                val updated = enrichCurrent(
                    current = mutableState.value,
                    checkedAt = clockMillis(),
                    issue = null,
                )
                mutableState.value = updated
                MusicRefreshResult.NotModified(updated)
            }

            RemoteConfigFetchResult.NotConfigured -> keepCurrent(MusicRefreshIssue.NOT_CONFIGURED)
            RemoteConfigFetchResult.InvalidEndpoint -> keepCurrent(MusicRefreshIssue.INVALID_ENDPOINT)
            is RemoteConfigFetchResult.InvalidPayload -> keepCurrent(MusicRefreshIssue.INVALID_PAYLOAD)
            is RemoteConfigFetchResult.Failure -> keepCurrent(MusicRefreshIssue.NETWORK)
        }
    }

    private suspend fun acceptRemote(
        remote: RemoteSpotifyConfig,
        cached: CachedMusicConfig?,
    ): MusicRefreshResult {
        val currentRevision = cached?.config?.revision
        if (!SpotifyRevisionPolicy.canReplace(currentRevision, remote.config.revision)) {
            return keepCurrent(MusicRefreshIssue.ROLLBACK_REJECTED)
        }

        val now = clockMillis()
        cache.save(remote.config, remote.etag, now)
        val metadata = if (remote.config.enabled) {
            oEmbedClient.fetch(remote.config.spotifyId)
        } else {
            null
        }
        val updated = MusicState(
            config = remote.config,
            source = MusicConfigSource.REMOTE,
            remoteConfigured = true,
            metadata = metadata,
            isRefreshing = false,
            lastCheckedAtEpochMillis = now,
            lastSuccessfulRefreshAtEpochMillis = now,
            issue = null,
        )
        mutableState.value = updated
        return MusicRefreshResult.Updated(updated)
    }

    private suspend fun keepCurrent(issue: MusicRefreshIssue): MusicRefreshResult.KeptCurrent {
        val updated = enrichCurrent(
            current = mutableState.value,
            checkedAt = clockMillis(),
            issue = issue,
        )
        mutableState.value = updated
        return MusicRefreshResult.KeptCurrent(updated, issue)
    }

    private suspend fun enrichCurrent(
        current: MusicState,
        checkedAt: Long,
        issue: MusicRefreshIssue?,
    ): MusicState {
        val metadata = if (current.config.enabled) {
            current.metadata ?: oEmbedClient.fetch(current.config.spotifyId)
        } else {
            null
        }
        return current.copy(
            metadata = metadata,
            isRefreshing = false,
            lastCheckedAtEpochMillis = checkedAt,
            issue = issue,
        )
    }
}
