package com.liftly.app.integration.spotify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyRevisionPolicyTest {
    @Test
    fun `first valid revision is accepted`() {
        assertTrue(SpotifyRevisionPolicy.canReplace(null, 0))
        assertTrue(SpotifyRevisionPolicy.canReplace(null, 50))
    }

    @Test
    fun `equal and newer revisions are accepted`() {
        assertTrue(SpotifyRevisionPolicy.canReplace(7, 7))
        assertTrue(SpotifyRevisionPolicy.canReplace(7, 8))
    }

    @Test
    fun `rollback and negative revision are rejected`() {
        assertFalse(SpotifyRevisionPolicy.canReplace(7, 6))
        assertFalse(SpotifyRevisionPolicy.canReplace(null, -1))
    }
}
