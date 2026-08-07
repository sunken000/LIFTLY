package com.liftly.app.integration.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyOEmbedParserTest {
    @Test
    fun `keeps official title and Spotify CDN thumbnail`() {
        val result = SpotifyOEmbedParser.parse(
            """{"title":"침몰한 ","thumbnail_url":"https://image-cdn-ak.spotifycdn.com/image/abc","provider_name":"Spotify"}""",
        )

        assertEquals("침몰한 ", result?.title)
        assertEquals("https://image-cdn-ak.spotifycdn.com/image/abc", result?.thumbnailUrl)
    }

    @Test
    fun `drops spoofed thumbnail without rejecting safe title`() {
        val result = SpotifyOEmbedParser.parse(
            """{"title":"Liftly Power","thumbnail_url":"https://i.scdn.co.evil.example/image/abc"}""",
        )

        assertEquals("Liftly Power", result?.title)
        assertNull(result?.thumbnailUrl)
    }

    @Test
    fun `rejects malformed and oversized oEmbed JSON`() {
        assertNull(SpotifyOEmbedParser.parse("not json"))
        assertNull(SpotifyOEmbedParser.parse("{" + " ".repeat(SpotifyPlaylistConfigParser.MAX_CONFIG_BYTES)))
    }
}
