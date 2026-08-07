package com.anipresence.app.domain.model

import java.time.Instant

enum class PlaybackState { PLAYING, PAUSED, STOPPED }
enum class DetectionSource { MEDIA_SESSION, NOTIFICATION, DEBUG }

data class DetectedMedia(
    val packageName: String,
    val appName: String?,
    val rawTitle: String?,
    val subtitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val playbackState: PlaybackState,
    val updatedAt: Instant = Instant.now(),
    val source: DetectionSource,
)

data class ParsedMediaTitle(
    val possibleTitle: String?,
    val season: Int?,
    val episode: Int?,
    val originalText: String,
)

data class AnimeMatch(
    val canonicalTitle: String,
    val season: Int?,
    val episode: Int?,
    val confidence: Int,
    val source: String,
)

data class AnimeActivity(
    val animeTitle: String,
    val episode: Int?,
    val season: Int?,
    val sourceApp: String?,
    val startedAt: Instant?,
)

data class ManualCorrection(
    val rawTitle: String,
    val packageName: String?,
    val title: String,
    val season: Int?,
    val episode: Int?,
)
