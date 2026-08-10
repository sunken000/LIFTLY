package com.liftly.app.domain

import com.liftly.app.data.WorkoutExerciseEntity

/**
 * Creates and removes explicit two-exercise bi-sets while keeping the persisted order compatible
 * with [SupersetPlanner]. No schema migration is required: the pair is represented by two adjacent
 * workout items marked as `Bi-set`.
 */
object BiSetPairing {
    private const val BI_SET = "Bi-set"
    private const val NORMAL = "Normal"

    fun pair(
        items: List<WorkoutExerciseEntity>,
        firstId: String,
        secondId: String,
    ): List<WorkoutExerciseEntity> {
        require(firstId != secondId) { "Escolha dois exercícios diferentes." }
        val ordered = items.sortedBy(WorkoutExerciseEntity::orderIndex)
        val first = requireNotNull(ordered.firstOrNull { it.id == firstId }) { "Primeiro exercício não encontrado." }
        val second = requireNotNull(ordered.firstOrNull { it.id == secondId }) { "Segundo exercício não encontrado." }
        require(first.workoutId == second.workoutId) { "Os exercícios precisam pertencer ao mesmo treino." }

        // Detach either selected exercise from an old pair before creating the new one.
        val memberships = SupersetPlanner.memberships(ordered)
        val detachIds = buildSet {
            listOf(firstId, secondId).forEach { id ->
                memberships[id]?.let { membership ->
                    add(id)
                    add(membership.partnerWorkoutExerciseId)
                }
            }
        }
        val normalized = ordered.map { item ->
            if (item.id in detachIds) item.copy(setType = NORMAL) else item
        }

        // Keep the first selected exercise in place and move the second immediately after it.
        val selectedSecond = requireNotNull(normalized.firstOrNull { it.id == secondId })
        val reordered = normalized.filterNot { it.id == secondId }.toMutableList()
        val firstIndex = reordered.indexOfFirst { it.id == firstId }
        check(firstIndex >= 0) { "Primeiro exercício não encontrado após reordenação." }
        reordered.add(firstIndex + 1, selectedSecond)

        return reordered.mapIndexed { index, item ->
            when (item.id) {
                firstId, secondId -> item.copy(orderIndex = index, setType = BI_SET)
                else -> item.copy(orderIndex = index)
            }
        }
    }

    fun unpair(
        items: List<WorkoutExerciseEntity>,
        itemId: String,
    ): List<WorkoutExerciseEntity> {
        val ordered = items.sortedBy(WorkoutExerciseEntity::orderIndex)
        require(ordered.any { it.id == itemId }) { "Exercício não encontrado." }
        val membership = SupersetPlanner.memberships(ordered)[itemId]
        val ids = if (membership != null) {
            setOf(itemId, membership.partnerWorkoutExerciseId)
        } else {
            setOf(itemId)
        }
        return ordered.mapIndexed { index, item ->
            if (item.id in ids) item.copy(orderIndex = index, setType = NORMAL)
            else item.copy(orderIndex = index)
        }
    }
}
