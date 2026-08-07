package com.liftly.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmiCalculatorTest {
    @Test fun acceptsCentimetersAndMeters() {
        assertEquals(22.86, BmiCalculator.calculate(70.0, 175.0).value, 0.01)
        assertEquals(22.86, BmiCalculator.calculate(70.0, 1.75).value, 0.01)
    }

    @Test fun adultBoundariesAreExact() {
        val height = 2.0
        assertEquals("Magreza grau III", BmiCalculator.calculate(63.99, height).classification)
        assertEquals("Magreza grau II", BmiCalculator.calculate(64.0, height).classification)
        assertEquals("Magreza grau I", BmiCalculator.calculate(68.0, height).classification)
        assertEquals("Faixa considerada adequada", BmiCalculator.calculate(74.0, height).classification)
        assertEquals("Sobrepeso", BmiCalculator.calculate(100.0, height).classification)
        assertEquals("Obesidade grau I", BmiCalculator.calculate(120.0, height).classification)
        assertEquals("Obesidade grau II", BmiCalculator.calculate(140.0, height).classification)
        assertEquals("Obesidade grau III", BmiCalculator.calculate(160.0, height).classification)
    }

    @Test fun minorHasNoAdultClassification() {
        val result = BmiCalculator.calculate(60.0, 170.0, LocalDate.now().year - 14)
        assertNull(result.classification)
        assertTrue(result.adultRanges.isEmpty())
        assertNull(result.healthyWeightRange)
        assertNull(result.weightAdjustment)
    }

    @Test fun exposesAllPersonalizedAdultRanges() {
        val result = BmiCalculator.calculate(70.0, 2.0)
        assertEquals(8, result.adultRanges.size)
        assertEquals(74.0, result.adultRanges[3].minimumWeightKg!!, 0.001)
        assertEquals(100.0, result.adultRanges[3].maximumWeightKgExclusive!!, 0.001)
        assertTrue(result.adultRanges[2].isCurrent)
    }

    @Test fun estimatesGainOrLossToHealthyRange() {
        val gain = BmiCalculator.calculate(60.0, 2.0).weightAdjustment!!
        assertEquals(WeightAdjustmentDirection.GAIN, gain.direction)
        assertEquals(14.0, gain.kilograms, 0.001)

        val loss = BmiCalculator.calculate(120.0, 2.0).weightAdjustment!!
        assertEquals(WeightAdjustmentDirection.LOSE, loss.direction)
        assertEquals(20.4, loss.kilograms, 0.001)
    }

    @Test fun adequateClassificationNeverRequestsWeightLoss() {
        val result = BmiCalculator.calculate(99.8, 2.0)
        assertEquals("Faixa considerada adequada", result.classification)
        assertEquals(WeightAdjustmentDirection.MAINTAIN, result.weightAdjustment?.direction)
    }

    @Test fun adultSexDoesNotChangeStandardClassification() {
        val female = BmiCalculator.calculateForAge(70.0, 175.0, 35, sex = BmiSex.FEMALE)
        val male = BmiCalculator.calculateForAge(70.0, 175.0, 35, sex = BmiSex.MALE)
        assertEquals(female.value, male.value, 0.0)
        assertEquals(female.classification, male.classification)
        assertEquals(female.adultRanges, male.adultRanges)
    }

    @Test fun sisvanOlderAdultCutoffsAreAppliedFromSixty() {
        assertEquals("Baixo peso", BmiCalculator.calculateForAge(88.0, 2.0, 60).classification)
        assertEquals("Peso adequado (eutrofia)", BmiCalculator.calculateForAge(88.4, 2.0, 60).classification)
        assertEquals("Sobrepeso", BmiCalculator.calculateForAge(108.0, 2.0, 60).classification)
    }

    @Test fun adolescentReferenceUsesExactAgeAndSex() {
        val girl = BmiCalculator.calculateForAge(22.0, 1.0, 13, additionalMonths = 6, sex = BmiSex.FEMALE)
        val boy = BmiCalculator.calculateForAge(22.0, 1.0, 13, additionalMonths = 6, sex = BmiSex.MALE)
        assertEquals("Eutrofia", girl.classification)
        assertEquals("Sobrepeso", boy.classification)
        assertNotNull(girl.zScore)
        assertEquals(6, girl.adultRanges.size)
        assertNull(girl.weightAdjustment)
    }

    @Test fun adolescentReferenceRequiresSex() {
        val result = BmiCalculator.calculateForAge(55.0, 165.0, 14)
        assertNull(result.classification)
        assertTrue(result.usesGrowthReference)
        assertTrue(result.notice.orEmpty().contains("sexo", ignoreCase = true))
    }

    @Test fun fiveYearsExactlyUsesWhoStandardAndAcceptsPediatricWeight() {
        val result = BmiCalculator.calculateForAge(
            weightKg = 15.2747,
            heightInput = 1.0,
            ageYears = 5,
            additionalMonths = 0,
            sex = BmiSex.FEMALE
        )

        assertEquals("Eutrofia", result.classification)
        assertEquals(0.0, result.zScore!!, 0.000_001)
        assertEquals(6, result.adultRanges.size)
    }

    @Test fun nineteenYearReferenceKeepsTheOfficialNineteenYearValues() {
        val atNineteen = BmiCalculator.calculateForAge(25.0, 1.0, 19, 0, BmiSex.MALE)
        val atNineteenEleven = BmiCalculator.calculateForAge(25.0, 1.0, 19, 11, BmiSex.MALE)

        assertEquals(atNineteen.zScore!!, atNineteenEleven.zScore!!, 0.0)
        assertEquals(atNineteen.classification, atNineteenEleven.classification)
        assertEquals(atNineteen.adultRanges, atNineteenEleven.adultRanges)
    }

    @Test fun whoExtremeTailExamplesMatchThePublishedComputation() {
        val high = BmiCalculator.calculateForAge(30.0, 1.0, 11, 0, BmiSex.MALE)
        val low = BmiCalculator.calculateForAge(14.0, 1.0, 16, 0, BmiSex.MALE)

        assertEquals(3.35, high.zScore!!, 0.01)
        assertEquals("Obesidade grave", high.classification)
        assertEquals(-3.80, low.zScore!!, 0.01)
        assertEquals("Magreza acentuada", low.classification)
    }

    @Test fun whoReferenceHasNoGapAcrossTheSupportedAgeBoundaries() {
        assertNull(WhoBmiReference.parameters(BmiSex.FEMALE, 59))
        assertNotNull(WhoBmiReference.parameters(BmiSex.FEMALE, 60))
        assertNotNull(WhoBmiReference.parameters(BmiSex.FEMALE, 61))
        assertNotNull(WhoBmiReference.parameters(BmiSex.FEMALE, 228))
        assertEquals(
            WhoBmiReference.parameters(BmiSex.FEMALE, 228),
            WhoBmiReference.parameters(BmiSex.FEMALE, 239)
        )
        assertNull(WhoBmiReference.parameters(BmiSex.FEMALE, 240))
    }

    @Test fun ageTwentyUsesAdultReference() {
        val result = BmiCalculator.calculateForAge(70.0, 175.0, 20, 0, BmiSex.FEMALE)

        assertEquals("Faixa considerada adequada", result.classification)
        assertTrue(!result.usesGrowthReference)
        assertEquals(8, result.adultRanges.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHeight() { BmiCalculator.calculate(70.0, 30.0) }
}
