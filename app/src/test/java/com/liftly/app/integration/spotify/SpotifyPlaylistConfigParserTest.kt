package com.liftly.app.integration.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaylistConfigParserTest {
    @Test
    fun `parses minimal schema version one config`() {
        val result = SpotifyPlaylistConfigParser.parse(
            validJson(revision = 0, playlistFields = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\""),
        )

        assertTrue(result is SpotifyConfigParseResult.Success)
        val config = (result as SpotifyConfigParseResult.Success).config
        assertEquals(1, config.schemaVersion)
        assertEquals(0, config.revision)
        assertTrue(config.enabled)
        assertEquals(FALLBACK_SPOTIFY_PLAYLIST_ID, config.spotifyId)
        assertNull(config.updatedAt)
        assertNull(config.title)
        assertNull(config.description)
    }

    @Test
    fun `parses all bounded optional strings`() {
        val result = SpotifyPlaylistConfigParser.parse(
            """{
                "schemaVersion":1,
                "revision":42,
                "enabled":false,
                "updatedAt":"2026-07-21T23:30:00Z",
                "playlist":{
                    "spotifyId":"$FALLBACK_SPOTIFY_PLAYLIST_ID",
                    "title":"Treino pesado",
                    "description":"Atualizada pelo proprietário"
                }
            }""".trimIndent(),
        )

        assertTrue(result is SpotifyConfigParseResult.Success)
        val config = (result as SpotifyConfigParseResult.Success).config
        assertEquals(42, config.revision)
        assertFalse(config.enabled)
        assertEquals("Treino pesado", config.title)
        assertEquals("Atualizada pelo proprietário", config.description)
    }

    @Test
    fun `rejects incompatible schema missing fields and unknown fields`() {
        assertInvalid(validJson(schemaVersion = 2))
        assertInvalid("""{"schemaVersion":1,"revision":0,"playlist":{"spotifyId":"$FALLBACK_SPOTIFY_PLAYLIST_ID"}}""")
        assertInvalid(validJson(extraRoot = ",\"admin\":true"))
        assertInvalid(validJson(playlistFields = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\",\"secret\":\"x\""))
    }

    @Test
    fun `rejects invalid revision forms`() {
        assertInvalid(validJson(revisionRaw = "-1"))
        assertInvalid(validJson(revisionRaw = "1.0"))
        assertInvalid(validJson(revisionRaw = "1e2"))
        assertInvalid(validJson(revisionRaw = "9223372036854775808"))
        assertInvalid(validJson(revisionRaw = "\"1\""))
    }

    @Test
    fun `rejects invalid IDs and optional string types`() {
        assertInvalid(validJson(playlistFields = "\"spotifyId\":\"short\""))
        assertInvalid(validJson(playlistFields = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\",\"title\":null"))
        assertInvalid(validJson(playlistFields = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\",\"description\":42"))
        assertInvalid(validJson(extraRoot = ",\"updatedAt\":null"))
        assertInvalid(validJson(extraRoot = ",\"updatedAt\":\"21 de julho de 2026\""))
    }

    @Test
    fun `rejects empty overlong and control character text`() {
        assertInvalid(validJson(playlistFields = playlistWith(title = "")))
        assertInvalid(validJson(playlistFields = playlistWith(title = "x".repeat(SpotifyPlaylistConfigParser.MAX_TITLE_LENGTH + 1))))
        assertInvalid(
            validJson(
                playlistFields = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\",\"title\":\"linha\\nquebrada\"",
            ),
        )
        assertInvalid(
            validJson(
                playlistFields = playlistWith(
                    description = "x".repeat(SpotifyPlaylistConfigParser.MAX_DESCRIPTION_LENGTH + 1),
                ),
            ),
        )
    }

    @Test
    fun `rejects duplicate keys malformed JSON and responses over 64 KB`() {
        assertInvalid(
            """{"schemaVersion":1,"schemaVersion":1,"revision":0,"enabled":true,"playlist":{"spotifyId":"$FALLBACK_SPOTIFY_PLAYLIST_ID"}}""",
        )
        assertInvalid("{" + " ".repeat(SpotifyPlaylistConfigParser.MAX_CONFIG_BYTES))
        assertInvalid("not json")
    }

    private fun assertInvalid(json: String) {
        assertTrue(
            "Expected invalid JSON but got ${SpotifyPlaylistConfigParser.parse(json)}",
            SpotifyPlaylistConfigParser.parse(json) is SpotifyConfigParseResult.Invalid,
        )
    }

    private fun playlistWith(title: String? = null, description: String? = null): String = buildString {
        append("\"spotifyId\":\"")
        append(FALLBACK_SPOTIFY_PLAYLIST_ID)
        append('"')
        title?.let {
            append(",\"title\":\"")
            append(it)
            append('"')
        }
        description?.let {
            append(",\"description\":\"")
            append(it)
            append('"')
        }
    }

    private fun validJson(
        schemaVersion: Int = 1,
        revision: Long = 0,
        revisionRaw: String = revision.toString(),
        playlistFields: String = "\"spotifyId\":\"$FALLBACK_SPOTIFY_PLAYLIST_ID\"",
        extraRoot: String = "",
    ): String =
        """{"schemaVersion":$schemaVersion,"revision":$revisionRaw,"enabled":true,"playlist":{$playlistFields}$extraRoot}"""
}
