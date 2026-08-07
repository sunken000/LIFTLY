package com.anipresence.app.data.anime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeTitleParserTest {
    private val parser = AnimeTitleParser()

    @Test fun `parses Frieren episode`() {
        val result = parser.parse("Frieren - Episode 8")
        assertEquals("Frieren", result.possibleTitle)
        assertEquals(8, result.episode)
    }

    @Test fun `parses compact season and episode`() {
        val result = parser.parse("Jujutsu Kaisen S02E04")
        assertEquals("Jujutsu Kaisen", result.possibleTitle)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test fun `preserves 86 in title`() {
        val result = parser.parse("86 Episode 3")
        assertEquals("86", result.possibleTitle)
        assertEquals(3, result.episode)
    }

    @Test fun `preserves 91 Days in title`() {
        val result = parser.parse("91 Days - Episode 7")
        assertEquals("91 Days", result.possibleTitle)
        assertEquals(7, result.episode)
    }

    @Test fun `does not guess bare trailing number`() {
        val result = parser.parse("One Piece 1120")
        assertEquals("One Piece 1120", result.possibleTitle)
        assertNull(result.episode)
    }

    @Test fun `parses ordinal season`() {
        val result = parser.parse("Solo Leveling 2nd Season Ep. 7")
        assertEquals("Solo Leveling", result.possibleTitle)
        assertEquals(2, result.season)
        assertEquals(7, result.episode)
    }

    @Test fun `parses episode before title`() {
        val result = parser.parse("Episode 12 - Dungeon Meshi")
        assertEquals("Dungeon Meshi", result.possibleTitle)
        assertEquals(12, result.episode)
    }

    @Test fun `normalizes unicode and duplicated spaces`() {
        val result = parser.parse("  JUJUTSU   KAISEN Temporada 2 Episódio 4 ")
        assertEquals("JUJUTSU KAISEN", result.possibleTitle)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test fun `parses Portuguese pipe format`() {
        val result = parser.parse("One Piece | Episódio 1120")
        assertEquals("One Piece", result.possibleTitle)
        assertEquals(1120, result.episode)
    }

    @Test fun `parses T colon E`() {
        val result = parser.parse("Dan Da Dan T1:E5")
        assertEquals("Dan Da Dan", result.possibleTitle)
        assertEquals(1, result.season)
        assertEquals(5, result.episode)
    }
}
