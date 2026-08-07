package com.liftly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarbellPlateCalculatorTest {
    @Test
    fun `eighty kilograms uses the fewest available plates on each side`() {
        val result = BarbellPlateCalculator.calculate(requestedTotalKg = 80.0, barWeightKg = 20.0)

        assertEquals(listOf(25.0, 5.0), result.platesPerSide)
        assertEquals(30.0, result.plateWeightPerSideKg, 0.001)
        assertEquals(80.0, result.achievedTotalKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun `smallest available plate reports achievable load without inventing weight`() {
        val result = BarbellPlateCalculator.calculate(
            requestedTotalKg = 82.0,
            barWeightKg = 20.0,
            availablePlatesKg = listOf(20.0, 10.0, 2.5),
        )

        assertEquals(listOf(20.0, 10.0), result.platesPerSide)
        assertEquals(80.0, result.achievedTotalKg, 0.001)
        assertFalse(result.isExact)
    }

    @Test
    fun `target below bar weight keeps empty sleeves`() {
        val result = BarbellPlateCalculator.calculate(requestedTotalKg = 15.0, barWeightKg = 20.0)

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(20.0, result.achievedTotalKg, 0.001)
        assertFalse(result.isExact)
    }
}
