package com.anipresence.app.data.anime

import com.anipresence.app.domain.model.ParsedMediaTitle
import java.text.Normalizer

class AnimeTitleParser {
    private val generic = Regex(
        """(?i)\b(assistindo\s+agora|now\s+playing|reproduzindo|watching\s+now)\b"""
    )
    private val compact = Regex("""(?i)\b(?:S|T)\s*(\d{1,2})\s*[:._ -]?\s*E\s*(\d{1,4})\b""")
    private val season = Regex("""(?i)\b(?:season|temporada|S|T)\s*\.?\s*(\d{1,2})\b""")
    private val ordinalSeason = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)\s+season\b""")
    private val episode = Regex("""(?i)\b(?:episode|epis[oó]dio|ep|cap[ií]tulo)\s*[.:#-]?\s*(\d{1,4})\b""")
    private val bareEpisode = Regex("""(?i)(?<![\p{L}\p{N}])E\s*(\d{1,4})\b""")
    private val separators = Regex("""\s*[-|:·]\s*""")

    fun parse(text: String): ParsedMediaTitle {
        val original = text
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("""\s+"""), " ")
            .trim()
        val compactMatch = compact.find(normalized)
        val ordinalMatch = ordinalSeason.find(normalized)
        val seasonMatch = compactMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: season.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: ordinalMatch?.groupValues?.get(1)?.toIntOrNull()
        val episodeMatch = compactMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: episode.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: bareEpisode.find(normalized)?.groupValues?.get(1)?.toIntOrNull()

        var title = normalized
            .replace(generic, " ")
            .replace(compact, " ")
            .replace(ordinalSeason, " ")
            .replace(season, " ")
            .replace(episode, " ")
            .replace(bareEpisode, " ")
            .replace(separators, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|', ':', '·')
            .ifBlank { null }

        return ParsedMediaTitle(title, seasonMatch, episodeMatch, original)
    }
}
