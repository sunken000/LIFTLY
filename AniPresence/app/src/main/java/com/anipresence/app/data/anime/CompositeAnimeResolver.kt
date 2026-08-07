package com.anipresence.app.data.anime

import com.anipresence.app.data.preferences.SettingsRepository
import com.anipresence.app.domain.model.AnimeMatch
import com.anipresence.app.domain.model.ParsedMediaTitle

class CompositeAnimeResolver(
    private val local: AnimeResolver,
    private val remote: AnimeResolver,
    private val settings: SettingsRepository,
) {
    suspend fun resolve(input: ParsedMediaTitle, packageName: String?): AnimeMatch? {
        settings.correction(input.originalText, packageName)?.let {
            return AnimeMatch(it.title, it.season, it.episode, 100, "correção do usuário")
        }
        return local.resolve(input) ?: remote.resolve(input)
    }
}
