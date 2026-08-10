package com.liftly.app.domain

import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.WorkoutExerciseEntity
import java.text.Normalizer
import java.util.Locale

data class SupersetMembership(
    val groupId: String,
    val position: Int,
    val partnerWorkoutExerciseId: String,
)

/** Pairing is explicit by adjacency: two consecutive items marked Supersérie/Bi-set form one group. */
object SupersetPlanner {
    fun isMarked(item: WorkoutExerciseEntity): Boolean {
        val type = Normalizer.normalize(item.setType, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
        return type.contains("supers") || type.contains("bi-set") || type.contains("biset") || type.contains("bi set")
    }

    fun memberships(items: List<WorkoutExerciseEntity>): Map<String, SupersetMembership> {
        val ordered = items.sortedBy(WorkoutExerciseEntity::orderIndex)
        val result = linkedMapOf<String, SupersetMembership>()
        var index = 0
        while (index < ordered.size - 1) {
            val first = ordered[index]
            val second = ordered[index + 1]
            if (isMarked(first) && isMarked(second)) {
                val groupId = "${first.id}:${second.id}"
                result[first.id] = SupersetMembership(groupId, 1, second.id)
                result[second.id] = SupersetMembership(groupId, 2, first.id)
                index += 2
            } else {
                index += 1
            }
        }
        return result
    }

    fun sequence(
        sessionSets: List<SessionSetEntity>,
        workoutItems: List<WorkoutExerciseEntity>,
    ): List<SessionSetEntity> {
        val items = workoutItems.sortedBy(WorkoutExerciseEntity::orderIndex)
        val memberships = memberships(items)
        val byWorkoutItem = sessionSets.groupBy(SessionSetEntity::workoutExerciseId)
        val result = mutableListOf<SessionSetEntity>()
        val emitted = mutableSetOf<String>()
        for (item in items) {
            if (item.id in emitted) continue
            val membership = memberships[item.id]
            if (membership?.position == 1) {
                val first = byWorkoutItem[item.id].orEmpty().sortedBy(SessionSetEntity::setNumber)
                val second = byWorkoutItem[membership.partnerWorkoutExerciseId].orEmpty().sortedBy(SessionSetEntity::setNumber)
                val rounds = maxOf(first.size, second.size)
                repeat(rounds) { round ->
                    first.getOrNull(round)?.let(result::add)
                    second.getOrNull(round)?.let(result::add)
                }
                emitted += item.id
                emitted += membership.partnerWorkoutExerciseId
            } else if (membership == null) {
                result += byWorkoutItem[item.id].orEmpty().sortedBy(SessionSetEntity::setNumber)
                emitted += item.id
            }
        }
        val knownIds = result.mapTo(mutableSetOf(), SessionSetEntity::id)
        result += sessionSets.filterNot { it.id in knownIds }
            .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber })
        return result
    }

    /** null means continue immediately to the partner; otherwise start this rest duration. */
    fun restSecondsAfter(
        workoutExerciseId: String,
        workoutItems: List<WorkoutExerciseEntity>,
    ): Int? {
        val ordered = workoutItems.sortedBy(WorkoutExerciseEntity::orderIndex)
        val byId = ordered.associateBy(WorkoutExerciseEntity::id)
        val membership = memberships(ordered)[workoutExerciseId]
        if (membership?.position == 1) return null
        val item = byId[workoutExerciseId] ?: return 60
        if (membership?.position == 2) {
            val partner = byId[membership.partnerWorkoutExerciseId]
            return maxOf(item.restSeconds, partner?.restSeconds ?: 0).coerceIn(0, 3_600)
        }
        return item.restSeconds.coerceIn(0, 3_600)
    }
}
