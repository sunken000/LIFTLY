package com.anipresence.app

import android.app.Application
import com.anipresence.app.data.anime.CompositeAnimeResolver
import com.anipresence.app.data.anime.LocalAnimeResolver
import com.anipresence.app.data.anime.RemoteAnimeResolver
import com.anipresence.app.data.detection.AndroidMediaDetectionRepository
import com.anipresence.app.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AniPresenceApplication : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var detectionRepository: AndroidMediaDetectionRepository
        private set
    lateinit var animeResolver: CompositeAnimeResolver
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        detectionRepository = AndroidMediaDetectionRepository(this)
        animeResolver = CompositeAnimeResolver(
            local = LocalAnimeResolver(),
            remote = RemoteAnimeResolver(settings),
            settings = settings,
        )
        applicationScope.launch {
            settings.detectionEnabled.collectLatest { enabled ->
                if (enabled) detectionRepository.start() else detectionRepository.stop()
            }
        }
    }
}
