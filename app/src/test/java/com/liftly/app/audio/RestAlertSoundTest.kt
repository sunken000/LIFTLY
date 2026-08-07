package com.liftly.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAlertSoundTest {
    @Test
    fun `unknown stored sound falls back to ascending`() {
        assertEquals(RestAlertSound.ASCENDING, RestAlertSound.fromId("inexistente"))
        assertEquals(RestAlertSound.ASCENDING, RestAlertSound.fromId(null))
    }

    @Test
    fun `every alert creates a one second non silent buffer`() {
        RestAlertSound.entries.forEach { sound ->
            val samples = synthesizeRestAlert(sound, sampleRate = 8_000)
            assertEquals(8_000, samples.size)
            assertTrue("${sound.id} must contain audible samples", samples.any { it.toInt() != 0 })
            assertEquals(0, samples.first().toInt())
            assertEquals(0, samples.last().toInt())
        }
    }

    @Test
    fun `alert variants have different pcm signatures`() {
        val signatures = RestAlertSound.entries.map { sound ->
            synthesizeRestAlert(sound, sampleRate = 8_000).contentHashCode()
        }
        assertEquals(RestAlertSound.entries.size, signatures.distinct().size)
    }

    @Test
    fun `synthesis stays inside a conservative amplitude`() {
        RestAlertSound.entries.forEach { sound ->
            val samples = synthesizeRestAlert(sound, sampleRate = 8_000)
            assertFalse(samples.any { kotlin.math.abs(it.toInt()) > (Short.MAX_VALUE * 0.79).toInt() })
        }
    }

    @Test
    fun `full alert buffer repeats the sound for the requested duration`() {
        val oneSecond = synthesizeRestAlert(RestAlertSound.PULSE, sampleRate = 8_000)
        val threeSeconds = renderRestAlert(
            sound = RestAlertSound.PULSE,
            durationSeconds = 3,
            sampleRate = 8_000,
        )

        assertEquals(24_000, threeSeconds.size)
        assertTrue(oneSecond.contentEquals(threeSeconds.copyOfRange(0, 8_000)))
        assertTrue(oneSecond.contentEquals(threeSeconds.copyOfRange(8_000, 16_000)))
        assertTrue(oneSecond.contentEquals(threeSeconds.copyOfRange(16_000, 24_000)))
    }
}
