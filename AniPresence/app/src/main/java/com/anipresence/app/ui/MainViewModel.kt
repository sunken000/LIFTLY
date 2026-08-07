package com.anipresence.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anipresence.app.AniPresenceApplication
import com.anipresence.app.data.anime.AnimeTitleParser
import com.anipresence.app.data.anime.MusicExclusionPolicy
import com.anipresence.app.data.discord.DiscordConnectionState
import com.anipresence.app.data.discord.DiscordWebhookClient
import com.anipresence.app.domain.model.AnimeActivity
import com.anipresence.app.domain.model.AnimeMatch
import com.anipresence.app.domain.model.DetectedMedia
import com.anipresence.app.domain.model.DetectionSource
import com.anipresence.app.domain.model.ManualCorrection
import com.anipresence.app.domain.model.ParsedMediaTitle
import com.anipresence.app.domain.model.PlaybackState
import com.anipresence.app.domain.usecase.PresenceDecision
import com.anipresence.app.domain.usecase.PresenceUpdatePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MainUiState(
    val detectionEnabled: Boolean = false,
    val notificationAccess: Boolean = false,
    val media: DetectedMedia? = null,
    val parsed: ParsedMediaTitle? = null,
    val match: AnimeMatch? = null,
    val discordState: DiscordConnectionState = DiscordConnectionState.Disconnected,
    val excludedAsMusic: Boolean = false,
    val resolving: Boolean = false,
    val logs: List<String> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AniPresenceApplication
    private val parser = AnimeTitleParser()
    private val webhookClient = DiscordWebhookClient(app.settings)
    private val presencePolicy = PresenceUpdatePolicy()
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()
    private var currentProcess: Job? = null
    private var policyTimer: Job? = null

    init {
        viewModelScope.launch {
            app.settings.detectionEnabled.collectLatest { enabled ->
                mutableState.update { it.copy(detectionEnabled = enabled) }
                log(if (enabled) "Detecção ativada" else "Detecção desativada")
            }
        }
        viewModelScope.launch {
            webhookClient.connectionState.collectLatest { discord ->
                mutableState.update { it.copy(discordState = discord) }
            }
        }
        viewModelScope.launch {
            app.detectionRepository.currentMedia.collectLatest(::processMedia)
        }
        viewModelScope.launch {
            app.detectionRepository.diagnostics.collectLatest(::log)
        }
        viewModelScope.launch { webhookClient.connect() }
    }

    fun setNotificationAccess(granted: Boolean) {
        mutableState.update { it.copy(notificationAccess = granted) }
    }

    fun toggleDetection() {
        viewModelScope.launch { app.settings.setDetectionEnabled(!state.value.detectionEnabled) }
    }

    fun configureWebhook(url: String) {
        viewModelScope.launch {
            if (url.isNotBlank() && !DiscordWebhookClient.isValidWebhook(url)) {
                log("URL de webhook inválida")
                return@launch
            }
            app.settings.saveWebhook(url.trim())
            webhookClient.disconnect()
            webhookClient.connect()
            log(if (url.isBlank()) "Webhook removido" else "Webhook configurado com segurança")
        }
    }

    fun saveCorrection(title: String, season: Int?, episode: Int?) {
        val media = state.value.media ?: return
        val raw = media.rawTitle ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            app.settings.saveCorrection(
                ManualCorrection(raw, media.packageName, title.trim(), season, episode)
            )
            log("Correção manual salva")
            processMedia(media.copy(updatedAt = Instant.now()))
        }
    }

    fun confirmAndPublish() {
        val activity = currentActivity() ?: return
        viewModelScope.launch {
            webhookClient.updatePresence(activity)
            log("Publicação confirmada manualmente")
        }
    }

    fun testWebhook() {
        viewModelScope.launch {
            webhookClient.sendTestMessage()
            when (val discord = webhookClient.connectionState.value) {
                is DiscordConnectionState.Connected -> log("Mensagem de teste enviada ao Discord")
                is DiscordConnectionState.Error -> log(discord.message)
                else -> log("Webhook ainda não está conectado")
            }
        }
    }

    fun simulate(
        packageName: String,
        title: String,
        subtitle: String,
        playbackState: PlaybackState,
        durationMs: Long?,
        positionMs: Long?,
    ) {
        app.detectionRepository.injectDebug(
            DetectedMedia(
                packageName = packageName.ifBlank { "com.example.player" },
                appName = "Player simulado",
                rawTitle = title.ifBlank { null },
                subtitle = subtitle.ifBlank { null },
                durationMs = durationMs,
                positionMs = positionMs,
                playbackState = playbackState,
                source = DetectionSource.DEBUG,
            )
        )
        log("Evento de mídia simulado")
    }

    private fun processMedia(media: DetectedMedia?) {
        currentProcess?.cancel()
        policyTimer?.cancel()
        currentProcess = viewModelScope.launch {
            if (media == null) {
                mutableState.update { it.copy(media = null, parsed = null, match = null, resolving = false) }
                applyPresencePolicy(PlaybackState.STOPPED)
                schedulePolicy(PlaybackState.STOPPED, 60_000)
                return@launch
            }
            val raw = media.rawTitle
            val parsed = raw?.let(parser::parse)
            val excluded = MusicExclusionPolicy.isExcluded(media.packageName)
            mutableState.update {
                it.copy(media = media, parsed = parsed, match = null, excludedAsMusic = excluded, resolving = parsed != null)
            }
            log("${media.appName ?: media.packageName}: ${media.playbackState.name.lowercase()}")
            val match = parsed?.let { app.animeResolver.resolve(it, media.packageName) }
            mutableState.update { it.copy(match = match, resolving = false) }
            if (match == null && raw != null) log("Conteúdo detectado, mas não reconhecido como anime")
            applyPresencePolicy(media.playbackState)
            when (media.playbackState) {
                PlaybackState.PLAYING -> schedulePolicy(PlaybackState.PLAYING, 10_050)
                PlaybackState.PAUSED -> schedulePolicy(PlaybackState.PAUSED, 120_050)
                PlaybackState.STOPPED -> schedulePolicy(PlaybackState.STOPPED, 60_050)
            }
        }
    }

    private fun schedulePolicy(state: PlaybackState, delayMs: Long) {
        policyTimer = viewModelScope.launch {
            delay(delayMs)
            applyPresencePolicy(state)
        }
    }

    private suspend fun applyPresencePolicy(stateOverride: PlaybackState? = null) {
        val value = state.value
        val decision = presencePolicy.evaluate(
            nowMs = System.currentTimeMillis(),
            state = stateOverride ?: value.media?.playbackState ?: PlaybackState.STOPPED,
            activity = currentActivity(),
            confidence = value.match?.confidence ?: 0,
            excluded = value.excludedAsMusic,
        )
        when (decision) {
            PresenceDecision.None -> Unit
            is PresenceDecision.Publish -> {
                webhookClient.updatePresence(decision.activity)
                log("Atividade enviada ao Discord")
            }
            PresenceDecision.Clear -> {
                webhookClient.clearPresence()
                log("Atividade removida do Discord")
            }
        }
    }

    private fun currentActivity(): AnimeActivity? {
        val value = state.value
        val match = value.match ?: return null
        return AnimeActivity(
            animeTitle = match.canonicalTitle,
            episode = match.episode,
            season = match.season,
            sourceApp = value.media?.appName,
            startedAt = if (value.media?.playbackState == PlaybackState.PLAYING) Instant.now() else null,
        )
    }

    private fun log(message: String) {
        val time = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault()).format(Instant.now())
        mutableState.update { it.copy(logs = (listOf("$time  $message") + it.logs).take(12)) }
    }
}
