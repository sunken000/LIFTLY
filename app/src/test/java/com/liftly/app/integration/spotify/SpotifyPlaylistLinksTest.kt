package com.liftly.app.integration.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaylistLinksTest {
    private val validId = FALLBACK_SPOTIFY_PLAYLIST_ID

    @Test
    fun `playlist id is exact 22 character base62`() {
        assertTrue(SpotifyPlaylistId.isValid(validId))
        assertTrue(SpotifyPlaylistId.isValid("0123456789ABCDEFGHIJKL"))
        assertFalse(SpotifyPlaylistId.isValid("0123456789ABCDEFGHIJK"))
        assertFalse(SpotifyPlaylistId.isValid("0123456789ABCDEFGHIJKLM"))
        assertFalse(SpotifyPlaylistId.isValid("0123456789ABCDEFGHIJK_"))
        assertFalse(SpotifyPlaylistId.isValid(" $validId"))
    }

    @Test
    fun `generates official spotify destinations`() {
        val links = SpotifyPlaylistLinks.fromId(validId)

        assertEquals("spotify:playlist:$validId", links.spotifyUri)
        assertEquals("https://open.spotify.com/playlist/$validId", links.webUrl)
        assertEquals("https://open.spotify.com/embed/playlist/$validId", links.embedUrl)
        assertTrue(links.oEmbedUrl.startsWith("https://open.spotify.com/oembed?url="))
    }

    @Test
    fun `extracts canonical URI and web URL`() {
        assertEquals(validId, SpotifyPlaylistLinks.extractId("spotify:playlist:$validId"))
        assertEquals(validId, SpotifyPlaylistLinks.extractId("https://open.spotify.com/playlist/$validId"))
        assertEquals(validId, SpotifyPlaylistLinks.extractId("https://open.spotify.com/playlist/$validId?si=abc"))
    }

    @Test
    fun `personal library accepts a bare id URI and canonical link`() {
        assertEquals(validId, PersonalSpotifyPlaylistInput.extractId(validId))
        assertEquals(validId, PersonalSpotifyPlaylistInput.extractId("spotify:playlist:$validId"))
        assertEquals(
            validId,
            PersonalSpotifyPlaylistInput.extractId("https://open.spotify.com/playlist/$validId?si=share"),
        )
    }

    @Test
    fun `personal library rejects unsafe playlist reference`() {
        assertNull(PersonalSpotifyPlaylistInput.extractId("https://open.spotify.com.attacker.example/playlist/$validId"))
        assertNull(PersonalSpotifyPlaylistInput.extractId("<script>alert(1)</script>"))
    }

    @Test
    fun `personal library normalizes local title and falls back safely`() {
        assertEquals("Treino de pernas", PersonalSpotifyPlaylistInput.titleOrDefault("  Treino   de pernas  ", validId))
        assertEquals("Minha playlist ${validId.take(6)}", PersonalSpotifyPlaylistInput.titleOrDefault("  ", validId))
    }

    @Test
    fun `rejects spotify lookalike URLs and unsafe components`() {
        assertNull(SpotifyPlaylistLinks.extractId("https://open.spotify.com.attacker.example/playlist/$validId"))
        assertNull(SpotifyPlaylistLinks.extractId("http://open.spotify.com/playlist/$validId"))
        assertNull(SpotifyPlaylistLinks.extractId("https://user@open.spotify.com/playlist/$validId"))
        assertNull(SpotifyPlaylistLinks.extractId("https://open.spotify.com:443/playlist/$validId"))
        assertNull(SpotifyPlaylistLinks.extractId("https://open.spotify.com/playlist/$validId#fragment"))
        assertNull(SpotifyPlaylistLinks.extractId("https://open.spotify.com/album/$validId"))
    }

    @Test
    fun `remote endpoint accepts only public HTTPS DNS URL`() {
        assertTrue(SpotifyEndpointValidator.isValidRemoteConfigUrl("https://config.example.com/liftly/playlist.json"))
        assertFalse(SpotifyEndpointValidator.isValidRemoteConfigUrl("http://config.example.com/playlist.json"))
        assertFalse(SpotifyEndpointValidator.isValidRemoteConfigUrl("https://localhost/playlist.json"))
        assertFalse(SpotifyEndpointValidator.isValidRemoteConfigUrl("https://127.0.0.1/playlist.json"))
        assertFalse(SpotifyEndpointValidator.isValidRemoteConfigUrl("https://user@config.example.com/playlist.json"))
        assertFalse(SpotifyEndpointValidator.isValidRemoteConfigUrl("https://config.example.com/playlist.json#x"))
    }

    @Test
    fun `thumbnail accepts Spotify CDN and rejects host spoofing`() {
        assertTrue(SpotifyEndpointValidator.isValidThumbnailUrl("https://i.scdn.co/image/abc"))
        assertTrue(SpotifyEndpointValidator.isValidThumbnailUrl("https://image-cdn-ak.spotifycdn.com/image/abc"))
        assertTrue(SpotifyEndpointValidator.isValidThumbnailUrl("https://image-cdn-ak.spotifycdn-com.akamaized.net.scdn.co/a"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("http://i.scdn.co/image/abc"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("https://i.scdn.co.attacker.example/image/abc"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("https://evilscdn.co/image/abc"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("https://evilspotifycdn.com/image/abc"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("https://spotifycdn.com.attacker.example/image/abc"))
        assertFalse(SpotifyEndpointValidator.isValidThumbnailUrl("https://user@i.scdn.co/image/abc"))
    }
}
