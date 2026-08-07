package com.anipresence.app.data.anime

import com.anipresence.app.domain.model.ManualCorrection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverPolicyTest {
    @Test fun `exact alias has high confidence`() = runTest {
        val parsed = AnimeTitleParser().parse("Frieren - Episode 8")
        val match = LocalAnimeResolver().resolve(parsed)!!
        assertEquals("Frieren: Beyond Journey's End", match.canonicalTitle)
        assertTrue(match.confidence >= 90)
    }

    @Test fun `manual correction prefers package specific match`() {
        val global = ManualCorrection("Unknown ep 2", null, "Global", null, 2)
        val specific = ManualCorrection("unknown EP 2", "video.app", "Correct", 1, 2)
        val match = ManualCorrectionMatcher.find(listOf(global, specific), "UNKNOWN ep 2", "video.app")
        assertEquals("Correct", match?.title)
    }

    @Test fun `music apps are excluded`() {
        assertTrue(MusicExclusionPolicy.isExcluded("com.spotify.music"))
        assertTrue(MusicExclusionPolicy.isExcluded("com.google.android.apps.youtube.music"))
        assertFalse(MusicExclusionPolicy.isExcluded("com.example.video"))
    }
}
