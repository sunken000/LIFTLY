package com.liftly.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test
    fun `normalization removes Portuguese accents and ignores case`() {
        assertEquals(
            "agachamento bulgaro para triceps e gluteos",
            "AGACHAMENTO BÚLGARO para Tríceps e Glúteos".normalizedForSearch(),
        )
    }

    @Test
    fun `query without accent finds accented exercise name`() {
        assertTrue(matchesSearchQuery("triceps", "Tríceps testa", "Braços"))
        assertTrue(matchesSearchQuery("agachamento bulgaro", "Agachamento búlgaro", "Quadríceps"))
    }

    @Test
    fun `words can match different searchable fields`() {
        assertTrue(matchesSearchQuery("peito halter", "Supino reto", "Peito", "Halteres"))
    }

    @Test
    fun `matching is independent of query word order and extra whitespace`() {
        assertTrue(matchesSearchQuery("  BULGARO   agachamento ", "Agachamento búlgaro"))
    }

    @Test
    fun `blank query matches and unrelated query does not`() {
        assertTrue(matchesSearchQuery("   ", "Elevação pélvica"))
        assertFalse(matchesSearchQuery("triceps", "Elevação pélvica", "Glúteos"))
    }
}
