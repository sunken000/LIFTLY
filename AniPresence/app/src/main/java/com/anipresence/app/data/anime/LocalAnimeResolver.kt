package com.anipresence.app.data.anime

import com.anipresence.app.domain.model.AnimeMatch
import com.anipresence.app.domain.model.ParsedMediaTitle

class LocalAnimeResolver : AnimeResolver {
    private data class Entry(val canonical: String, val aliases: List<String>)

    private val entries = listOf(
        Entry("Frieren: Beyond Journey's End", listOf("Frieren", "Sousou no Frieren")),
        Entry("Jujutsu Kaisen", listOf("JUJUTSU KAISEN", "JJK")),
        Entry("Solo Leveling", listOf("Ore dake Level Up na Ken")),
        Entry("One Piece", emptyList()),
        Entry("Delicious in Dungeon", listOf("Dungeon Meshi")),
        Entry("Dandadan", listOf("Dan Da Dan")),
        Entry("86 Eighty-Six", listOf("86", "86 Eighty Six")),
        Entry("91 Days", emptyList()),
    )

    override suspend fun resolve(input: ParsedMediaTitle): AnimeMatch? {
        val title = input.possibleTitle ?: return null
        val best = entries
            .flatMap { entry -> (entry.aliases + entry.canonical).map { entry to TitleSimilarity.score(title, it) } }
            .maxByOrNull { it.second }
            ?: return null
        if (best.second < 62) return null
        val confidence = if (best.second == 100) 96 else (best.second * 0.9).toInt().coerceAtMost(90)
        return AnimeMatch(best.first.canonical, input.season, input.episode, confidence, "aliases locais")
    }
}
