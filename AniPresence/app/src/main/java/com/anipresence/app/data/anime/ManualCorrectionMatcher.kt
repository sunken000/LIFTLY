package com.anipresence.app.data.anime

import com.anipresence.app.domain.model.ManualCorrection

object ManualCorrectionMatcher {
    fun find(
        corrections: Collection<ManualCorrection>,
        rawTitle: String,
        packageName: String?,
    ): ManualCorrection? {
        val normalized = TitleSimilarity.normalize(rawTitle)
        return corrections.firstOrNull {
            it.packageName == packageName && TitleSimilarity.normalize(it.rawTitle) == normalized
        } ?: corrections.firstOrNull {
            it.packageName == null && TitleSimilarity.normalize(it.rawTitle) == normalized
        }
    }
}
