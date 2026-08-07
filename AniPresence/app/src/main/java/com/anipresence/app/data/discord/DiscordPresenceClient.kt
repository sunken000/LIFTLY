package com.anipresence.app.data.discord

import com.anipresence.app.domain.model.AnimeActivity
import kotlinx.coroutines.flow.StateFlow

sealed interface DiscordConnectionState {
    data object Disconnected : DiscordConnectionState
    data object Connecting : DiscordConnectionState
    data class Connected(val mode: String) : DiscordConnectionState
    data class Unavailable(val reason: String) : DiscordConnectionState
    data class Error(val message: String) : DiscordConnectionState
}

interface DiscordPresenceClient {
    val connectionState: StateFlow<DiscordConnectionState>
    suspend fun connect()
    suspend fun updatePresence(activity: AnimeActivity)
    suspend fun clearPresence()
    suspend fun disconnect()
}
