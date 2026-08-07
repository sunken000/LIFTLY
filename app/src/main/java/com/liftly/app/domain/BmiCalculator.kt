package com.liftly.app.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

enum class BmiSex(val displayName: String) {
    FEMALE("Feminino"),
    MALE("Masculino")
}

data class BmiResult(
    val value: Double,
    val classification: String?,
    val isMinor: Boolean,
    val adultRanges: List<BmiRange> = emptyList(),
    val healthyWeightRange: HealthyWeightRange? = null,
    val weightAdjustment: WeightAdjustment? = null,
    val ageYears: Int? = null,
    val additionalMonths: Int = 0,
    val sex: BmiSex? = null,
    val referenceLabel: String = "OMS — adultos",
    val zScore: Double? = null,
    val usesGrowthReference: Boolean = false,
    val notice: String? = null
)

data class BmiRange(
    val classification: String,
    val minimumBmi: Double?,
    val maximumBmiExclusive: Double?,
    val minimumWeightKg: Double?,
    val maximumWeightKgExclusive: Double?,
    val isCurrent: Boolean,
    val minimumInclusive: Boolean = true,
    val maximumInclusive: Boolean = false
)

data class HealthyWeightRange(val minimumKg: Double, val maximumKg: Double)

enum class WeightAdjustmentDirection { GAIN, LOSE, MAINTAIN }

data class WeightAdjustment(
    val direction: WeightAdjustmentDirection,
    val kilograms: Double,
    val targetWeightKg: Double
)

object BmiCalculator {
    /** Compatibilidade com o perfil antigo, que armazena apenas o ano de nascimento. */
    fun calculate(weightKg: Double, heightInput: Double, birthYear: Int? = null): BmiResult {
        val age = birthYear?.let { LocalDate.now().year - it }
        return calculateInternal(weightKg, heightInput, age, 0, null, validateAge = false)
    }

    fun calculateForAge(
        weightKg: Double,
        heightInput: Double,
        ageYears: Int,
        additionalMonths: Int = 0,
        sex: BmiSex? = null
    ): BmiResult = calculateInternal(weightKg, heightInput, ageYears, additionalMonths, sex, validateAge = true)

    private fun calculateInternal(
        weightKg: Double,
        heightInput: Double,
        ageYears: Int?,
        additionalMonths: Int,
        sex: BmiSex?,
        validateAge: Boolean
    ): BmiResult {
        val isGrowthAge = ageYears != null && ageYears < 20
        val minimumWeightKg = if (isGrowthAge) 5.0 else 20.0
        require(weightKg in minimumWeightKg..500.0) {
            "Informe um peso válido entre ${minimumWeightKg.toInt()} e 500 kg."
        }
        val meters = if (heightInput > 3.0) heightInput / 100.0 else heightInput
        val minimumHeightMeters = if (isGrowthAge) 0.5 else 0.8
        require(meters in minimumHeightMeters..2.5) { "Informe uma altura válida em centímetros ou metros." }
        if (validateAge) require(ageYears != null && ageYears in 5..120) { "Informe uma idade entre 5 e 120 anos." }
        require(additionalMonths in 0..11) { "Os meses adicionais devem estar entre 0 e 11." }

        val bmi = weightKg / (meters * meters)
        return when {
            ageYears != null && ageYears < 20 -> growthReferenceResult(
                bmi = bmi,
                heightSquared = meters * meters,
                ageYears = ageYears,
                additionalMonths = additionalMonths,
                sex = sex
            )
            ageYears != null && ageYears >= 60 -> olderAdultResult(bmi, weightKg, meters * meters, ageYears, sex)
            else -> adultResult(bmi, weightKg, meters * meters, ageYears, sex)
        }
    }

    private fun adultResult(
        bmi: Double,
        weightKg: Double,
        heightSquared: Double,
        ageYears: Int?,
        sex: BmiSex?
    ): BmiResult {
        val classification = when {
            bmi < 16.0 -> "Magreza grau III"
            bmi < 17.0 -> "Magreza grau II"
            bmi < 18.5 -> "Magreza grau I"
            bmi < 25.0 -> "Faixa considerada adequada"
            bmi < 30.0 -> "Sobrepeso"
            bmi < 35.0 -> "Obesidade grau I"
            bmi < 40.0 -> "Obesidade grau II"
            else -> "Obesidade grau III"
        }
        val definitions = listOf(
            RangeDefinition("Magreza grau III", null, 16.0),
            RangeDefinition("Magreza grau II", 16.0, 17.0),
            RangeDefinition("Magreza grau I", 17.0, 18.5),
            RangeDefinition("Faixa considerada adequada", 18.5, 25.0),
            RangeDefinition("Sobrepeso", 25.0, 30.0),
            RangeDefinition("Obesidade grau I", 30.0, 35.0),
            RangeDefinition("Obesidade grau II", 35.0, 40.0),
            RangeDefinition("Obesidade grau III", 40.0, null)
        )
        val healthyMinimum = 18.5 * heightSquared
        val healthyMaximum = 24.9 * heightSquared
        val adjustment = when {
            bmi < 18.5 -> WeightAdjustment(WeightAdjustmentDirection.GAIN, healthyMinimum - weightKg, healthyMinimum)
            bmi >= 25.0 -> WeightAdjustment(WeightAdjustmentDirection.LOSE, weightKg - healthyMaximum, healthyMaximum)
            else -> WeightAdjustment(WeightAdjustmentDirection.MAINTAIN, 0.0, weightKg)
        }
        return BmiResult(
            value = bmi,
            classification = classification,
            isMinor = false,
            adultRanges = definitions.toRanges(heightSquared, classification),
            healthyWeightRange = HealthyWeightRange(healthyMinimum, healthyMaximum),
            weightAdjustment = adjustment,
            ageYears = ageYears,
            sex = sex,
            referenceLabel = "OMS / SISVAN — adultos de 20 a 59 anos",
            notice = "Em adultos, idade e sexo não alteram a fórmula nem os pontos de corte do IMC."
        )
    }

    private fun olderAdultResult(
        bmi: Double,
        weightKg: Double,
        heightSquared: Double,
        ageYears: Int,
        sex: BmiSex?
    ): BmiResult {
        val classification = when {
            bmi <= 22.0 -> "Baixo peso"
            bmi < 27.0 -> "Peso adequado (eutrofia)"
            else -> "Sobrepeso"
        }
        val definitions = listOf(
            RangeDefinition("Baixo peso", null, 22.0, maximumInclusive = true),
            RangeDefinition("Peso adequado (eutrofia)", 22.0, 27.0, minimumInclusive = false),
            RangeDefinition("Sobrepeso", 27.0, null)
        )
        val healthyMinimum = 22.1 * heightSquared
        val healthyMaximum = 26.9 * heightSquared
        val adjustment = when {
            bmi <= 22.0 -> WeightAdjustment(WeightAdjustmentDirection.GAIN, (healthyMinimum - weightKg).coerceAtLeast(0.0), healthyMinimum)
            bmi >= 27.0 -> WeightAdjustment(WeightAdjustmentDirection.LOSE, (weightKg - healthyMaximum).coerceAtLeast(0.0), healthyMaximum)
            else -> WeightAdjustment(WeightAdjustmentDirection.MAINTAIN, 0.0, weightKg)
        }
        return BmiResult(
            value = bmi,
            classification = classification,
            isMinor = false,
            adultRanges = definitions.toRanges(heightSquared, classification),
            healthyWeightRange = HealthyWeightRange(healthyMinimum, healthyMaximum),
            weightAdjustment = adjustment,
            ageYears = ageYears,
            sex = sex,
            referenceLabel = "Ministério da Saúde / SISVAN — pessoa idosa (≥60 anos)",
            notice = "Para pessoas idosas, o SISVAN usa pontos de corte próprios: ≤22, >22 e <27, e ≥27 kg/m²."
        )
    }

    private fun growthReferenceResult(
        bmi: Double,
        heightSquared: Double,
        ageYears: Int,
        additionalMonths: Int,
        sex: BmiSex?
    ): BmiResult {
        val ageMonths = ageYears * 12 + additionalMonths
        val growthReferenceLabel = if (ageMonths == 60) {
            "OMS 2006 / SISVAN — IMC para idade e sexo"
        } else {
            "OMS 2007 / SISVAN — IMC para idade e sexo"
        }
        if (sex == null) {
            return BmiResult(
                value = bmi,
                classification = null,
                isMinor = ageYears < 18,
                ageYears = ageYears,
                additionalMonths = additionalMonths,
                referenceLabel = "OMS / SISVAN — IMC para idade",
                usesGrowthReference = true,
                notice = "Selecione o sexo da curva de crescimento para interpretar o IMC de pessoas com menos de 20 anos."
            )
        }
        val parameters = WhoBmiReference.parameters(sex, ageMonths)
            ?: return BmiResult(
                value = bmi,
                classification = null,
                isMinor = ageYears < 18,
                ageYears = ageYears,
                additionalMonths = additionalMonths,
                sex = sex,
                referenceLabel = growthReferenceLabel,
                usesGrowthReference = true,
                notice = "A referência incorporada cobre de 5 anos completos a 19 anos e 11 meses."
            )

        val thresholds = listOf(-3.0, -2.0, 1.0, 2.0, 3.0).associateWith { z -> parameters.valueAtZ(z) }
        val zScore = parameters.zScoreFor(bmi)
        val classification = when {
            zScore < -3.0 -> "Magreza acentuada"
            zScore < -2.0 -> "Magreza"
            zScore <= 1.0 -> "Eutrofia"
            zScore <= 2.0 -> "Sobrepeso"
            zScore <= 3.0 -> "Obesidade"
            else -> "Obesidade grave"
        }
        val definitions = listOf(
            RangeDefinition("Magreza acentuada", null, thresholds.getValue(-3.0)),
            RangeDefinition("Magreza", thresholds.getValue(-3.0), thresholds.getValue(-2.0)),
            RangeDefinition("Eutrofia", thresholds.getValue(-2.0), thresholds.getValue(1.0), maximumInclusive = true),
            RangeDefinition("Sobrepeso", thresholds.getValue(1.0), thresholds.getValue(2.0), minimumInclusive = false, maximumInclusive = true),
            RangeDefinition("Obesidade", thresholds.getValue(2.0), thresholds.getValue(3.0), minimumInclusive = false, maximumInclusive = true),
            RangeDefinition("Obesidade grave", thresholds.getValue(3.0), null, minimumInclusive = false)
        )
        return BmiResult(
            value = bmi,
            classification = classification,
            isMinor = ageYears < 18,
            adultRanges = definitions.toRanges(heightSquared, classification),
            ageYears = ageYears,
            additionalMonths = additionalMonths,
            sex = sex,
            referenceLabel = growthReferenceLabel,
            zScore = zScore,
            usesGrowthReference = true,
            notice = "Em pessoas de 5 a 19 anos, a interpretação usa idade em meses e sexo da curva. Não é adequado transformar o resultado em uma meta isolada de peso."
        )
    }

    private data class RangeDefinition(
        val label: String,
        val minimum: Double?,
        val maximum: Double?,
        val minimumInclusive: Boolean = true,
        val maximumInclusive: Boolean = false
    )

    private fun List<RangeDefinition>.toRanges(heightSquared: Double, current: String): List<BmiRange> = map { range ->
        BmiRange(
            classification = range.label,
            minimumBmi = range.minimum,
            maximumBmiExclusive = range.maximum,
            minimumWeightKg = range.minimum?.times(heightSquared),
            maximumWeightKgExclusive = range.maximum?.times(heightSquared),
            isCurrent = range.label == current,
            minimumInclusive = range.minimumInclusive,
            maximumInclusive = range.maximumInclusive
        )
    }

    private fun WhoLmsParameters.valueAtZ(z: Double): Double = if (abs(l) < 1e-12) {
        m * kotlin.math.exp(s * z)
    } else {
        m * (1.0 + l * s * z).pow(1.0 / l)
    }

    private fun WhoLmsParameters.zScoreFor(bmi: Double): Double {
        val raw = if (abs(l) < 1e-12) ln(bmi / m) / s else ((bmi / m).pow(l) - 1.0) / (l * s)
        return when {
            raw > 3.0 -> 3.0 + (bmi - valueAtZ(3.0)) / (valueAtZ(3.0) - valueAtZ(2.0))
            raw < -3.0 -> -3.0 + (bmi - valueAtZ(-3.0)) / (valueAtZ(-2.0) - valueAtZ(-3.0))
            else -> raw
        }
    }
}
