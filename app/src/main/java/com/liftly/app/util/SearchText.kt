package com.liftly.app.util

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")

/**
 * Produces a locale-stable representation suitable for human-facing text search.
 * Accents are removed so, for example, "triceps" matches "Tríceps".
 */
fun String.normalizedForSearch(): String = Normalizer.normalize(
    lowercase(Locale.ROOT),
    Normalizer.Form.NFD,
).replace(COMBINING_MARKS, "")

/**
 * Matches every word in [query] against the combined searchable [fields].
 * Matching is case- and accent-insensitive, while the order of results remains
 * the responsibility of the caller.
 */
fun matchesSearchQuery(query: String, vararg fields: String): Boolean {
    val terms = query.normalizedForSearch()
        .trim()
        .split(WHITESPACE)
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return true

    val searchable = fields.joinToString(" ").normalizedForSearch()
    return terms.all(searchable::contains)
}
