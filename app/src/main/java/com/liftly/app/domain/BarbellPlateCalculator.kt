package com.liftly.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resultado de uma montagem simétrica de barra. [platesPerSide] lista somente um lado da barra;
 * a mesma sequência deve ser repetida no outro lado.
 */
data class BarbellPlateLoadout(
    val requestedTotalKg: Double,
    val barWeightKg: Double,
    val platesPerSide: List<Double>,
    val achievedTotalKg: Double,
) {
    val plateWeightPerSideKg: Double = platesPerSide.sum()
    val differenceKg: Double = achievedTotalKg - requestedTotalKg
    val isExact: Boolean = abs(differenceKg) < 0.001
}

/** Calcula anilhas iguais nos dois lados sem assumir limite de pares disponíveis. */
object BarbellPlateCalculator {
    val defaultAvailablePlatesKg = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

    fun calculate(
        requestedTotalKg: Double,
        barWeightKg: Double = 20.0,
        availablePlatesKg: List<Double> = defaultAvailablePlatesKg,
    ): BarbellPlateLoadout {
        require(requestedTotalKg.isFinite() && requestedTotalKg >= 0.0) {
            "A carga total precisa ser um número válido."
        }
        require(barWeightKg.isFinite() && barWeightKg >= 0.0) {
            "O peso da barra precisa ser um número válido."
        }
        require(availablePlatesKg.isNotEmpty()) { "Informe ao menos um tamanho de anilha." }
        require(availablePlatesKg.all { it.isFinite() && it > 0.0 }) {
            "As anilhas disponíveis precisam ter pesos positivos."
        }

        if (requestedTotalKg <= barWeightKg) {
            return BarbellPlateLoadout(
                requestedTotalKg = requestedTotalKg,
                barWeightKg = barWeightKg,
                platesPerSide = emptyList(),
                achievedTotalKg = barWeightKg,
            )
        }

        // Trabalhar em centésimos de kg evita erros de ponto flutuante com 1,25 kg e 0,5 kg.
        val denominations = availablePlatesKg
            .distinct()
            .sortedDescending()
            .map { (it * SCALE).roundToInt() to it }
        val requestedPerSideUnits = (((requestedTotalKg - barWeightKg) / 2.0) * SCALE).roundToInt()
        val selected = mutableListOf<Double>()
        var remaining = requestedPerSideUnits

        denominations.forEach { (units, displayWeight) ->
            while (units <= remaining) {
                selected += displayWeight
                remaining -= units
            }
        }

        val achievedPerSide = selected.sum()
        return BarbellPlateLoadout(
            requestedTotalKg = requestedTotalKg,
            barWeightKg = barWeightKg,
            platesPerSide = selected,
            achievedTotalKg = barWeightKg + achievedPerSide * 2.0,
        )
    }

    private const val SCALE = 100
}
