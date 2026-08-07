package com.anipresence.app.domain.usecase

import com.anipresence.app.domain.model.AnimeActivity
import com.anipresence.app.domain.model.PlaybackState

sealed interface PresenceDecision {
    data object None : PresenceDecision
    data class Publish(val activity: AnimeActivity) : PresenceDecision
    data object Clear : PresenceDecision
}

class PresenceUpdatePolicy(
    private val activeDelayMs: Long = 10_000,
    private val stoppedClearMs: Long = 60_000,
    private val pausedClearMs: Long = 120_000,
) {
    private var activeSince: Long? = null
    private var inactiveSince: Long? = null
    private var candidateKey: String? = null
    private var publishedKey: String? = null

    fun evaluate(
        nowMs: Long,
        state: PlaybackState,
        activity: AnimeActivity?,
        confidence: Int,
        excluded: Boolean,
    ): PresenceDecision {
        val key = activity?.let { "${it.animeTitle}|${it.season}|${it.episode}" }
        return when (state) {
            PlaybackState.PLAYING -> {
                inactiveSince = null
                if (key != candidateKey) {
                    candidateKey = key
                    activeSince = nowMs
                }
                if (activity != null && confidence >= 75 && !excluded &&
                    nowMs - (activeSince ?: nowMs) >= activeDelayMs &&
                    key != publishedKey
                ) {
                    publishedKey = key
                    PresenceDecision.Publish(activity)
                } else PresenceDecision.None
            }
            PlaybackState.PAUSED, PlaybackState.STOPPED -> {
                activeSince = null
                candidateKey = null
                val since = inactiveSince ?: nowMs.also { inactiveSince = it }
                val threshold = if (state == PlaybackState.STOPPED) stoppedClearMs else pausedClearMs
                if (publishedKey != null && nowMs - since >= threshold) {
                    publishedKey = null
                    PresenceDecision.Clear
                } else PresenceDecision.None
            }
        }
    }
}
