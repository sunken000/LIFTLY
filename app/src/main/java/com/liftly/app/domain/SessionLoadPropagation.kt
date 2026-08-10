package com.liftly.app.domain

import com.liftly.app.data.SessionSetEntity
import kotlin.math.abs

object SessionLoadPropagation {
    private const val EPSILON = 0.0001

    fun changedFirstWorkingSet(source: SessionSetEntity, newLoadKg: Double): Boolean =
        source.setNumber == 1 &&
            source.trackingMode == "Repetições" &&
            newLoadKg.isFinite() &&
            abs(newLoadKg - source.loadKg) > EPSILON

    fun shouldInherit(source: SessionSetEntity, sibling: SessionSetEntity): Boolean =
        sibling.id != source.id &&
            sibling.sessionId == source.sessionId &&
            sibling.workoutExerciseId == source.workoutExerciseId &&
            sibling.exerciseId == source.exerciseId &&
            sibling.setNumber > 1 &&
            !sibling.completed &&
            abs(sibling.loadKg - source.loadKg) <= EPSILON
}
