package com.liftly.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liftly.app.integration.spotify.MusicRepository
import com.liftly.app.integration.spotify.PersonalPlaylistSaveResult
import com.liftly.app.integration.spotify.PersonalSpotifyPlaylistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Estado da playlist em destaque e da biblioteca local de links do Spotify. */
class MusicViewModel(
    private val repository: MusicRepository,
    private val personalPlaylistRepository: PersonalSpotifyPlaylistRepository,
) : ViewModel() {
    val state = repository.state
    val personalPlaylists = personalPlaylistRepository.playlists
    val selectedPersonalPlaylistId = personalPlaylistRepository.selectedPlaylistId

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive != true) {
            refreshJob = viewModelScope.launch { repository.refresh() }
        }
    }

    fun savePersonalPlaylist(reference: String, title: String): Boolean = when (
        personalPlaylistRepository.save(reference, title)
    ) {
        PersonalPlaylistSaveResult.Added -> {
            mutableMessages.tryEmit("Playlist salva neste aparelho.")
            true
        }
        PersonalPlaylistSaveResult.Updated -> {
            mutableMessages.tryEmit("O nome desta playlist foi atualizado.")
            true
        }
        PersonalPlaylistSaveResult.InvalidLink -> {
            mutableMessages.tryEmit("Cole um link de playlist do Spotify ou o ID de 22 caracteres.")
            false
        }
        PersonalPlaylistSaveResult.LimitReached -> {
            mutableMessages.tryEmit("Você pode salvar até 50 playlists neste aparelho.")
            false
        }
    }

    fun removePersonalPlaylist(spotifyId: String) {
        personalPlaylistRepository.remove(spotifyId)
        mutableMessages.tryEmit("Playlist removida deste aparelho.")
    }

    fun selectPersonalPlaylist(spotifyId: String) {
        personalPlaylistRepository.select(spotifyId)
    }

    companion object {
        fun factory(
            repository: MusicRepository,
            personalPlaylistRepository: PersonalSpotifyPlaylistRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MusicViewModel(repository, personalPlaylistRepository) as T
        }
    }
}
