package com.liftly.app.data

import java.text.Normalizer
import java.util.Locale

/** Small, reusable visual families used while an individual licensed asset is unavailable. */
enum class ExerciseVisualKey {
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    SQUAT,
    HIP_HINGE,
    SINGLE_LEG,
    ARMS,
    SHOULDERS,
    CORE,
    CARDIO,
    MOBILITY,
    PLYOMETRIC,
    OLYMPIC_LIFT,
    FULL_BODY,
    GENERIC_STRENGTH,
}

sealed interface ExerciseVisualSpec {
    val fallbackKey: ExerciseVisualKey

    /** A persistable URI selected by the user for a custom exercise. */
    data class LocalImage(
        val uri: String,
        override val fallbackKey: ExerciseVisualKey,
    ) : ExerciseVisualSpec

    /**
     * Stable resource name reserved for a future per-exercise drawable. If it is not packaged,
     * the UI renders [fallbackKey] without changing database rows or catalog identifiers.
     */
    data class BundledOrFallback(
        val drawableName: String,
        override val fallbackKey: ExerciseVisualKey,
    ) : ExerciseVisualSpec

    data class GeneratedFallback(
        override val fallbackKey: ExerciseVisualKey,
    ) : ExerciseVisualSpec
}

/** Pure resolver: no Android resources, network, clock or mutable registry. */
object ExerciseVisualResolver {
    private const val DRAWABLE_PREFIX = "exercise_"

    fun resolve(exercise: ExerciseEntity): ExerciseVisualSpec {
        val fallback = fallbackKeyFor(exercise)
        exercise.imageUri?.trim()?.takeIf(String::isNotEmpty)?.let { uri ->
            return ExerciseVisualSpec.LocalImage(uri = uri, fallbackKey = fallback)
        }
        return if (!exercise.isCustom && exercise.id.startsWith("builtin.")) {
            ExerciseVisualSpec.BundledOrFallback(
                drawableName = drawableNameFor(exercise.id),
                fallbackKey = fallback,
            )
        } else {
            ExerciseVisualSpec.GeneratedFallback(fallback)
        }
    }

    fun drawableNameFor(exerciseId: String): String {
        val stablePart = exerciseId
            .removePrefix("builtin.")
            .normalized()
            .replace(NON_RESOURCE_CHARS, "_")
            .trim('_')
            .take(MAX_RESOURCE_KEY_LENGTH)
            .ifEmpty { "generic" }
        return DRAWABLE_PREFIX + stablePart
    }

    fun fallbackKeyFor(exercise: ExerciseEntity): ExerciseVisualKey {
        val category = exercise.category.normalized()
        val movement = exercise.movementType.normalized()
        val muscle = exercise.muscleGroup.normalized()
        val name = exercise.name.normalized()
        val searchable = "$movement $muscle $name"

        return when {
            category.contains("levantamento olimpico") -> ExerciseVisualKey.OLYMPIC_LIFT
            category.contains("pliometr") || containsAny(searchable, "saltar", "salto") -> ExerciseVisualKey.PLYOMETRIC
            category.contains("cardio") || containsAny(searchable, "correr", "caminhar", "pedalar", "nadar", "remo ergometrico", "ski erg") -> ExerciseVisualKey.CARDIO
            category.contains("mobilidade") || containsAny(searchable, "mobilidade", "alongamento", "rotacao articular") -> ExerciseVisualKey.MOBILITY
            containsAny(movement, "agachar", "empurrar com pernas", "leg press") -> ExerciseVisualKey.SQUAT
            containsAny(movement, "hinge", "levantamento terra", "extensao de quadril") -> ExerciseVisualKey.HIP_HINGE
            containsAny(movement, "afundo", "passada", "subir degrau", "unilateral de joelho") -> ExerciseVisualKey.SINGLE_LEG
            containsAny(movement, "empurrar horizontal", "aducao horizontal") -> ExerciseVisualKey.HORIZONTAL_PUSH
            containsAny(movement, "empurrar vertical", "abducao de ombro", "flexao de ombro") -> ExerciseVisualKey.VERTICAL_PUSH
            containsAny(movement, "puxar horizontal", "remar") -> ExerciseVisualKey.HORIZONTAL_PULL
            containsAny(movement, "puxar vertical", "extensao de ombro") -> ExerciseVisualKey.VERTICAL_PULL
            containsAny(muscle, "biceps", "triceps", "antebraco") -> ExerciseVisualKey.ARMS
            containsAny(muscle, "ombro", "deltoide", "manguito") -> ExerciseVisualKey.SHOULDERS
            containsAny(muscle, "core", "abdomen", "abdominal", "lombar") ||
                containsAny(movement, "anti-extensao", "anti-rotacao", "flexao de tronco") -> ExerciseVisualKey.CORE
            containsAny(muscle, "corpo inteiro") -> ExerciseVisualKey.FULL_BODY
            else -> ExerciseVisualKey.GENERIC_STRENGTH
        }
    }

    private fun containsAny(value: String, vararg terms: String): Boolean = terms.any(value::contains)

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private val NON_RESOURCE_CHARS = "[^a-z0-9_]+".toRegex()
    private const val MAX_RESOURCE_KEY_LENGTH = 70
}
