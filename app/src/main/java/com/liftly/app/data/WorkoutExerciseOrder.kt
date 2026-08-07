package com.liftly.app.data

/**
 * Keeps the exercise order of a workout dense (0, 1, 2...) and deterministic.
 *
 * The input order is used as the tie-breaker when old data contains duplicated
 * [WorkoutExerciseEntity.orderIndex] values. This preserves the order the user
 * was already seeing while repairing gaps and duplicates.
 */
internal object WorkoutExerciseOrder {
    fun normalize(items: List<WorkoutExerciseEntity>): List<WorkoutExerciseEntity> =
        items
            .sortedBy(WorkoutExerciseEntity::orderIndex)
            .mapIndexed { index, item ->
                if (item.orderIndex == index) item else item.copy(orderIndex = index)
            }

    fun append(
        items: List<WorkoutExerciseEntity>,
        item: WorkoutExerciseEntity,
    ): List<WorkoutExerciseEntity> {
        val normalized = normalize(items)
        return normalized + item.copy(orderIndex = normalized.size)
    }

    fun remove(
        items: List<WorkoutExerciseEntity>,
        id: String,
    ): List<WorkoutExerciseEntity> =
        normalize(items.filterNot { it.id == id })

    fun move(
        items: List<WorkoutExerciseEntity>,
        id: String,
        direction: Int,
    ): List<WorkoutExerciseEntity> {
        val ordered = normalize(items)
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0 || direction == 0) return ordered
        val to = (from + direction).coerceIn(0, ordered.lastIndex)
        if (from == to) return ordered
        return ordered.toMutableList()
            .apply { add(to, removeAt(from)) }
            .mapIndexed { index, item -> item.copy(orderIndex = index) }
    }

    fun moveBefore(
        items: List<WorkoutExerciseEntity>,
        id: String,
        beforeId: String,
    ): List<WorkoutExerciseEntity> {
        val ordered = normalize(items)
        if (id == beforeId) return ordered
        val moving = ordered.firstOrNull { it.id == id } ?: return ordered
        val result = ordered.toMutableList().apply { remove(moving) }
        val target = result.indexOfFirst { it.id == beforeId }
            .let { if (it < 0) result.size else it }
        result.add(target, moving)
        return result.mapIndexed { index, item -> item.copy(orderIndex = index) }
    }
}
