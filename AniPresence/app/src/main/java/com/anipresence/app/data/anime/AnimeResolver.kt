package com.anipresence.app.data.anime

import com.anipresence.app.domain.model.AnimeMatch
import com.anipresence.app.domain.model.ParsedMediaTitle

interface AnimeResolver {
    suspend fun resolve(input: ParsedMediaTitle): AnimeMatch?
}
