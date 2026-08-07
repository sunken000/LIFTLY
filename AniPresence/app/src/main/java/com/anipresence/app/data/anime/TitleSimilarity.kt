package com.anipresence.app.data.anime

import java.text.Normalizer

object TitleSimilarity {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    fun score(a: String, b: String): Int {
        val left = normalize(a)
        val right = normalize(b)
        if (left == right) return 100
        if (left.isBlank() || right.isBlank()) return 0
        val leftTokens = left.split(" ").toSet()
        val rightTokens = right.split(" ").toSet()
        val union = leftTokens union rightTokens
        val tokenScore = ((leftTokens intersect rightTokens).size * 100.0 / union.size).toInt()
        val distanceScore = ((1.0 - levenshtein(left, right).toDouble() /
            maxOf(left.length, right.length)) * 100).toInt()
        return (tokenScore * 0.65 + distanceScore * 0.35).toInt().coerceIn(0, 100)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[b.length]
    }
}
