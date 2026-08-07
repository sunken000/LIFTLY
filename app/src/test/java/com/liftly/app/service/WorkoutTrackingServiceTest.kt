package com.liftly.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTrackingServiceTest {
    @Test
    fun `remaining rest is rounded up and formatted as minutes and seconds`() {
        assertEquals("00:00", formatWorkoutRestRemaining(-1L))
        assertEquals("00:01", formatWorkoutRestRemaining(1L))
        assertEquals("00:01", formatWorkoutRestRemaining(1_000L))
        assertEquals("01:01", formatWorkoutRestRemaining(60_001L))
        assertEquals("60:00", formatWorkoutRestRemaining(3_600_000L))
    }
}
