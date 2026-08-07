package com.anipresence.app.data.discord

import com.anipresence.app.domain.model.AnimeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponto de integração isolado para o artefato oficial baixado no Developer Portal.
 * O SDK não é publicado em repositório Maven público, portanto o build aberto não
 * inclui binários proprietários nem simula Rich Presence.
 */
class DiscordSocialSdkClient : DiscordPresenceClient {
    private val mutableState = MutableStateFlow<DiscordConnectionState>(
        DiscordConnectionState.Unavailable(
            "Discord Social SDK não incluído. Use webhook ou adicione o SDK oficial.",
        )
    )
    override val connectionState: StateFlow<DiscordConnectionState> = mutableState.asStateFlow()
    override suspend fun connect() = Unit
    override suspend fun updatePresence(activity: AnimeActivity) = Unit
    override suspend fun clearPresence() = Unit
    override suspend fun disconnect() = Unit
}
