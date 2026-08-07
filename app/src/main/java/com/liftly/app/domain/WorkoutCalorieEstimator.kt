package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.util.normalizedForSearch
import kotlin.math.roundToInt

data class WorkoutCalorieEstimate(
    val kilocalories: Int,
    val averageMet: Double,
    val durationMinutes: Double,
    val bodyWeightKg: Double,
    val durationWasCapped: Boolean,
)

/**
 * Estimates gross workout energy expenditure with standard MET values from the 2024 Adult
 * Compendium of Physical Activities. METs include resting expenditure and are population-level
 * estimates; a wearable with heart-rate and gas-exchange data may produce a different result.
 */
object WorkoutCalorieEstimator {
    private const val MAX_SESSION_DURATION_SECONDS = 4 * 60 * 60.0

    fun estimate(
        session: SessionEntity,
        sets: List<SessionSetEntity>,
        exercises: List<ExerciseEntity>,
        bodyWeightKg: Double?,
    ): WorkoutCalorieEstimate? {
        val finishedAt = session.finishedAt ?: return null
        val safeWeight = bodyWeightKg?.takeIf { it in 20.0..500.0 } ?: return null
        val completedSets = sets.filter { it.sessionId == session.id && it.completed }
        if (completedSets.isEmpty()) return null

        val rawDurationSeconds = ((finishedAt - session.startedAt) / 1_000.0)
            .coerceAtLeast(60.0)
        val durationSeconds = rawDurationSeconds.coerceAtMost(MAX_SESSION_DURATION_SECONDS)
        val exerciseById = exercises.associateBy { it.id }

        var weightedMetTotal = 0.0
        var compositionWeightTotal = 0.0
        completedSets.forEach { set ->
            val exercise = exerciseById[set.exerciseId]
            val met = metForExercise(exercise, fallbackName = set.exerciseName)
            val compositionWeight = compositionWeight(set)
            weightedMetTotal += met * compositionWeight
            compositionWeightTotal += compositionWeight
        }
        if (compositionWeightTotal <= 0.0) return null

        val averageMet = weightedMetTotal / compositionWeightTotal
        val durationMinutes = durationSeconds / 60.0
        // Standard conversion derived from 1 MET = 3.5 mL O₂/kg/min.
        val kilocalories = (averageMet * 3.5 * safeWeight / 200.0 * durationMinutes)
            .roundToInt()
            .coerceAtLeast(1)

        return WorkoutCalorieEstimate(
            kilocalories = kilocalories,
            averageMet = averageMet,
            durationMinutes = durationMinutes,
            bodyWeightKg = safeWeight,
            durationWasCapped = rawDurationSeconds > MAX_SESSION_DURATION_SECONDS,
        )
    }

    /** Uses recorded time/distance to give long cardio blocks more influence than one lifting set. */
    private fun compositionWeight(set: SessionSetEntity): Double = when {
        set.trackingMode.equals("Tempo", ignoreCase = true) && set.durationSeconds > 0 ->
            (set.durationSeconds / 60.0).coerceIn(0.5, 60.0)

        set.trackingMode.equals("Distância", ignoreCase = true) && set.distanceMeters > 0.0 ->
            (set.distanceMeters / 500.0).coerceIn(0.5, 60.0)

        else -> 1.0
    }

    internal fun metForExercise(exercise: ExerciseEntity?, fallbackName: String = ""): Double {
        val searchable = listOfNotNull(
            exercise?.name,
            exercise?.category,
            exercise?.movementType,
            exercise?.equipment,
            fallbackName,
        ).joinToString(" ").normalizedForSearch()

        return when {
            "pular corda" in searchable -> 11.0
            "escada ergometrica" in searchable || "subir degrau" in searchable -> 9.3
            "bicicleta de spinning" in searchable || "bicicleta indoor" in searchable -> 9.0
            "air bike" in searchable -> 8.0
            "corrida" in searchable || "correr" in searchable -> 7.5
            "polichinelo" in searchable || "corda naval" in searchable || "burpee" in searchable -> 7.5
            "ski erg" in searchable || "skierg" in searchable -> 6.8
            "bicicleta" in searchable || "pedalar" in searchable -> 6.8
            "natacao" in searchable || "nadar" in searchable -> 5.8
            "caminhada inclinada" in searchable || "caminhar em inclinacao" in searchable -> 5.3
            "remo ergometrico" in searchable || "remar" in searchable -> 5.0
            "eliptico" in searchable -> 5.0
            "caminhada" in searchable || "caminhar" in searchable -> 4.0
            "agachamento" in searchable || "levantamento terra" in searchable || "deadlift" in searchable -> 5.0
            "cardio" in searchable -> 6.0
            "funcional" in searchable -> 5.0
            "peso corporal" in searchable -> 3.8
            "core" in searchable -> 2.8
            "mobilidade" in searchable || "alongamento" in searchable -> 2.3
            "musculacao" in searchable -> 3.5
            else -> 3.5
        }
    }
}
