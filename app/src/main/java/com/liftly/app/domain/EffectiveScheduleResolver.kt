package com.liftly.app.domain

import com.liftly.app.data.ScheduleEntity
import com.liftly.app.data.WorkoutEntity
import java.time.LocalDate

/** Combines dated calendar entries with the recurring weekdays configured on each workout. */
object EffectiveScheduleResolver {
    fun forDate(
        date: LocalDate,
        workouts: List<WorkoutEntity>,
        persisted: List<ScheduleEntity>,
    ): List<ScheduleEntity> {
        val dateText = date.toString()
        val explicit = persisted.filter { it.date == dateText }
        if (explicit.any(ScheduleEntity::isRestDay)) return explicit.filter(ScheduleEntity::isRestDay)

        val activeWorkoutIds = workouts.asSequence()
            .filterNot(WorkoutEntity::archived)
            .map(WorkoutEntity::id)
            .toSet()
        val validExplicit = explicit.filter { it.workoutId in activeWorkoutIds }
        val explicitWorkoutIds = validExplicit.mapTo(mutableSetOf(), ScheduleEntity::workoutId)
        val weekday = date.dayOfWeek.value.toString()
        val recurring = workouts.asSequence()
            .filterNot(WorkoutEntity::archived)
            .filter { workout ->
                workout.weekDays.split(',').any { it.trim() == weekday } && workout.id !in explicitWorkoutIds
            }
            .map { workout ->
                ScheduleEntity(
                    id = "recurring-$dateText-${workout.id}",
                    date = dateText,
                    workoutId = workout.id,
                    status = "Planejado",
                )
            }
            .toList()
        val workoutOrder = workouts.mapIndexed { index, workout -> workout.id to index }.toMap()
        return (validExplicit + recurring).sortedWith(
            compareBy<ScheduleEntity> { workoutOrder[it.workoutId] ?: Int.MAX_VALUE }
                .thenBy(ScheduleEntity::workoutId)
        )
    }

    fun isRecurringPlaceholder(item: ScheduleEntity): Boolean = item.id.startsWith("recurring-")
}
