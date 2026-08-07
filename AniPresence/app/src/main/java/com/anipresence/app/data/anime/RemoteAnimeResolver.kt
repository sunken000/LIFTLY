package com.anipresence.app.data.anime

import com.anipresence.app.data.preferences.SettingsRepository
import com.anipresence.app.domain.model.AnimeMatch
import com.anipresence.app.domain.model.ParsedMediaTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class RemoteAnimeResolver(
    private val settings: SettingsRepository,
) : AnimeResolver {
    override suspend fun resolve(input: ParsedMediaTitle): AnimeMatch? {
        val query = input.possibleTitle ?: return null
        val json = settings.cachedAnime(query) ?: fetch(query)?.also {
            settings.cacheAnime(query, it)
        } ?: return null
        return parse(query, input, json)
    }

    private suspend fun fetch(query: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val connection = URL("https://api.jikan.moe/v4/anime?q=$encoded&limit=5")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AniPresence/1.0")
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun parse(query: String, input: ParsedMediaTitle, json: String): AnimeMatch? {
        val data = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull() ?: return null
        var bestTitle: String? = null
        var bestScore = 0
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val canonical = item.optString("title_english").ifBlank {
                item.optString("title").ifBlank { item.optString("title_japanese") }
            }
            val candidates = mutableListOf(canonical, item.optString("title"))
            val titles = item.optJSONArray("titles") ?: JSONArray()
            for (titleIndex in 0 until titles.length()) {
                candidates += titles.optJSONObject(titleIndex)?.optString("title").orEmpty()
            }
            val score = candidates.filter { it.isNotBlank() }
                .maxOfOrNull { TitleSimilarity.score(query, it) } ?: 0
            if (score > bestScore) {
                bestScore = score
                bestTitle = canonical
            }
        }
        if (bestTitle.isNullOrBlank() || bestScore < 58) return null
        return AnimeMatch(
            canonicalTitle = bestTitle,
            season = input.season,
            episode = input.episode,
            confidence = (bestScore * 0.82).toInt().coerceIn(50, 88),
            source = "Jikan",
        )
    }
}
