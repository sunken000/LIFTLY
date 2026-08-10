package com.liftly.app.domain

import com.liftly.app.data.WorkoutExerciseEntity

/** Alteração persistente que o Coach pode aplicar à próxima exposição do exercício. */
data class AdaptivePrescription(
    val targetLoadKg: Double,
    val repMin: Int,
    val repMax: Int,
    val status: ProgressionStatus,
    val rationale: String,
)

/**
 * Traduz uma recomendação do ProgressionCoach para uma prescrição persistente.
 * CAUTION nunca altera automaticamente a ficha; dor forte exige decisão humana.
 */
object AdaptiveTrainingPlan {
    fun prescription(
        item: WorkoutExerciseEntity,
        recommendation: ProgressionRecommendation,
    ): AdaptivePrescription? {
        if (recommendation.status == ProgressionStatus.CAUTION) return null
        val load = recommendation.suggestedLoadKg ?: return null
        val repMin = recommendation.suggestedRepMin ?: item.repMin
        val repMax = recommendation.suggestedRepMax ?: item.repMax
        val normalizedLoad = load.coerceAtLeast(0.0)
        val normalizedMin = repMin.coerceAtLeast(1)
        val normalizedMax = repMax.coerceAtLeast(normalizedMin)
        val changed = kotlin.math.abs(item.targetLoadKg - normalizedLoad) > 0.0001 ||
            item.repMin != normalizedMin || item.repMax != normalizedMax
        if (!changed) return null
        return AdaptivePrescription(
            targetLoadKg = normalizedLoad,
            repMin = normalizedMin,
            repMax = normalizedMax,
            status = recommendation.status,
            rationale = recommendation.title,
        )
    }

    fun apply(item: WorkoutExerciseEntity, prescription: AdaptivePrescription): WorkoutExerciseEntity =
        item.copy(
            targetLoadKg = prescription.targetLoadKg,
            repMin = prescription.repMin,
            repMax = prescription.repMax,
        )
}
