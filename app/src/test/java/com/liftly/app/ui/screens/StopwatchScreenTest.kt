package com.liftly.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class StopwatchScreenTest {
    @Test
    fun `formats centiseconds below one hour`() {
        assertEquals("01:01.23", formatStopwatchTime(61_239L))
    }

    @Test
    fun `formats hours without losing centiseconds`() {
        assertEquals("01:01:01.09", formatStopwatchTime(3_661_099L))
    }

    @Test
    fun `spoken time is stable within the same second`() {
        assertEquals(
            stopwatchSpokenDescription(61_001L),
            stopwatchSpokenDescription(61_999L),
        )
        assertEquals("1 minuto, 1 segundo", stopwatchSpokenDescription(61_999L))
    }

    @Test
    fun `paused stopwatch ignores clock changes`() {
        assertEquals(
            12_345L,
            calculateElapsedMillis(
                accumulatedMillis = 12_345L,
                startedAtElapsedRealtime = 1_000L,
                startedAtEpochMillis = 10_000L,
                isRunning = false,
                nowElapsedRealtime = 9_000L,
                nowEpochMillis = 18_000L,
            ),
        )
    }

    @Test
    fun `running stopwatch combines accumulated and monotonic time`() {
        assertEquals(
            6_500L,
            calculateElapsedMillis(
                accumulatedMillis = 2_000L,
                startedAtElapsedRealtime = 10_000L,
                startedAtEpochMillis = 100_000L,
                isRunning = true,
                nowElapsedRealtime = 14_500L,
                nowEpochMillis = 104_500L,
            ),
        )
    }

    @Test
    fun `uses wall time when monotonic clock was reset by reboot`() {
        assertEquals(
            7_500L,
            calculateElapsedMillis(
                accumulatedMillis = 1_500L,
                startedAtElapsedRealtime = 80_000L,
                startedAtEpochMillis = 200_000L,
                isRunning = true,
                nowElapsedRealtime = 5_000L,
                nowEpochMillis = 206_000L,
            ),
        )
    }
}
