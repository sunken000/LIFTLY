package com.anipresence.app.domain.usecase

import com.anipresence.app.domain.model.AnimeActivity
import com.anipresence.app.domain.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceUpdatePolicyTest {
    private val activity = AnimeActivity("Frieren", 8, 1, "Player", null)

    @Test fun `waits ten seconds and avoids duplicate updates`() {
        val policy = PresenceUpdatePolicy()
        assertEquals(
            PresenceDecision.None,
            policy.evaluate(0, PlaybackState.PLAYING, activity, 90, false),
        )
        assertTrue(
            policy.evaluate(10_000, PlaybackState.PLAYING, activity, 90, false)
                is PresenceDecision.Publish
        )
        assertEquals(
            PresenceDecision.None,
            policy.evaluate(20_000, PlaybackState.PLAYING, activity, 90, false),
        )
    }

    @Test fun `does not publish low confidence or music`() {
        val low = PresenceUpdatePolicy(activeDelayMs = 0)
        assertEquals(PresenceDecision.None, low.evaluate(0, PlaybackState.PLAYING, activity, 70, false))
        val music = PresenceUpdatePolicy(activeDelayMs = 0)
        assertEquals(PresenceDecision.None, music.evaluate(0, PlaybackState.PLAYING, activity, 99, true))
    }

    @Test fun `clears presence after sixty seconds stopped`() {
        val policy = PresenceUpdatePolicy(activeDelayMs = 0)
        assertTrue(policy.evaluate(0, PlaybackState.PLAYING, activity, 90, false) is PresenceDecision.Publish)
        assertEquals(PresenceDecision.None, policy.evaluate(1_000, PlaybackState.STOPPED, null, 0, false))
        assertEquals(PresenceDecision.Clear, policy.evaluate(61_000, PlaybackState.STOPPED, null, 0, false))
    }

    @Test fun `clears presence after two minutes paused`() {
        val policy = PresenceUpdatePolicy(activeDelayMs = 0)
        policy.evaluate(0, PlaybackState.PLAYING, activity, 90, false)
        policy.evaluate(1_000, PlaybackState.PAUSED, activity, 90, false)
        assertEquals(
            PresenceDecision.Clear,
            policy.evaluate(121_000, PlaybackState.PAUSED, activity, 90, false),
        )
    }
}
