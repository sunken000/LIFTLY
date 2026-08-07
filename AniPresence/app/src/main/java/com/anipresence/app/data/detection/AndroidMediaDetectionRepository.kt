package com.anipresence.app.data.detection

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.anipresence.app.domain.model.DetectedMedia
import com.anipresence.app.domain.model.DetectionSource
import com.anipresence.app.domain.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class AndroidMediaDetectionRepository(private val context: Context) : MediaDetectionRepository {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, AnimeNotificationListenerService::class.java)
    private val mutableMedia = MutableStateFlow<DetectedMedia?>(null)
    override val currentMedia: StateFlow<DetectedMedia?> = mutableMedia.asStateFlow()
    private val mutableDiagnostics = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val diagnostics: SharedFlow<String> = mutableDiagnostics.asSharedFlow()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindControllers(controllers.orEmpty())
    }

    override fun start() {
        if (started) return
        started = true
        val result = runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            bindControllers(sessionManager.getActiveSessions(listenerComponent))
        }
        result.onFailure {
            diagnose("Sessões indisponíveis: conceda novamente o acesso às notificações.")
        }
        runCatching { AnimeNotificationListenerService.requestReconnect(listenerComponent) }
        AnimeNotificationListenerService.requestSnapshot()
        diagnose("Detector iniciado; aguardando sessão ou notificação de mídia.")
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        callbacks.clear()
        mutableMedia.value = null
    }

    fun onListenerConnected() {
        diagnose("Serviço de acesso às notificações conectado.")
        if (started) {
            runCatching { bindControllers(sessionManager.getActiveSessions(listenerComponent)) }
                .onFailure { diagnose("Não foi possível consultar sessões ativas.") }
        }
    }

    fun onListenerDisconnected() {
        diagnose("Serviço de notificações desconectado; abra novamente a permissão.")
    }

    fun onMediaNotification(notification: StatusBarNotification) {
        if (!started) return
        val current = mutableMedia.value
        if (current?.source == DetectionSource.MEDIA_SESSION && !current.rawTitle.isNullOrBlank()) return
        parseNotification(notification)?.let {
            mutableMedia.value = it
            diagnose("Notificação de mídia reconhecida: ${it.appName ?: it.packageName}.")
        }
    }

    fun onNotificationRemoved(notification: StatusBarNotification) {
        val current = mutableMedia.value
        if (current?.source == DetectionSource.NOTIFICATION &&
            current.packageName == notification.packageName
        ) {
            mutableMedia.value = current.copy(
                playbackState = PlaybackState.STOPPED,
                updatedAt = Instant.now(),
            )
        }
    }

    fun injectDebug(media: DetectedMedia?) {
        if (started) mutableMedia.value = media
    }

    private fun bindControllers(controllers: List<MediaController>) {
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        callbacks.clear()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publishBest()
                override fun onPlaybackStateChanged(state: AndroidPlaybackState?) = publishBest()
                override fun onSessionDestroyed() {
                    callbacks.remove(controller)
                    publishBest()
                }
            }
            runCatching { controller.registerCallback(callback) }
            callbacks[controller] = callback
        }
        publishBest()
    }

    private fun publishBest() {
        if (!started) return
        val controllers = callbacks.keys.toList()
        val best = controllers.firstOrNull {
            it.playbackState?.state == AndroidPlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.playbackState?.state == AndroidPlaybackState.STATE_PAUSED
        } ?: controllers.firstOrNull()
        mutableMedia.value = best?.toDetectedMedia()
        if (best != null) diagnose("Sessão de mídia ativa: ${appName(best.packageName) ?: best.packageName}.")
    }

    private fun MediaController.toDetectedMedia(): DetectedMedia {
        val metadata = metadata
        val state = playbackState
        val displayTitle = metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString()
        val title = displayTitle?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val subtitle = metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.toString()
        val rawTitle = listOfNotNull(title?.takeIf(String::isNotBlank), subtitle?.takeIf(String::isNotBlank))
            .distinct()
            .joinToString(" - ")
            .ifBlank { null }
        return DetectedMedia(
            packageName = packageName,
            appName = appName(packageName),
            rawTitle = rawTitle,
            subtitle = subtitle,
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            positionMs = state?.position?.takeIf { it >= 0 },
            playbackState = state.toDomainState(),
            updatedAt = Instant.now(),
            source = DetectionSource.MEDIA_SESSION,
        )
    }

    private fun parseNotification(sbn: StatusBarNotification): DetectedMedia? {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT ||
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            notification.notificationStyle?.contains("MediaStyle", ignoreCase = true) == true
        if (!isMedia) return null

        val values = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
        ).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()
        val combined = (values + lines).distinct().joinToString(" - ").ifBlank { null }
        return DetectedMedia(
            packageName = sbn.packageName,
            appName = appName(sbn.packageName),
            rawTitle = combined,
            playbackState = if (
                notification.flags and Notification.FLAG_ONGOING_EVENT != 0
            ) PlaybackState.PLAYING else PlaybackState.PAUSED,
            updatedAt = Instant.ofEpochMilli(sbn.postTime),
            source = DetectionSource.NOTIFICATION,
        )
    }

    private fun appName(packageName: String): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun diagnose(message: String) {
        mutableDiagnostics.tryEmit(message)
    }

    private fun AndroidPlaybackState?.toDomainState() = when (this?.state) {
        AndroidPlaybackState.STATE_PLAYING,
        AndroidPlaybackState.STATE_BUFFERING,
        AndroidPlaybackState.STATE_CONNECTING -> PlaybackState.PLAYING
        AndroidPlaybackState.STATE_PAUSED -> PlaybackState.PAUSED
        else -> PlaybackState.STOPPED
    }
}

private val Notification.notificationStyle: String?
    get() = extras?.getString("android.template")
