package com.liftly.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.liftly.app.LiftlyApplication
import com.liftly.app.MainActivity
import com.liftly.app.R
import com.liftly.app.data.LiftlyDatabase
import com.liftly.app.data.WorkoutEntity
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Classic launcher widget that mirrors the same scheduling priority as the Hoje screen:
 * a dated calendar entry wins, followed by the recurring weekday assigned to a workout.
 */
class TodayWorkoutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodayWorkoutWidgetUpdater.ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TodayWorkoutWidgetProvider::class.java))
                updateAsync(context, manager, ids)
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun updateAsync(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        if (widgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = runCatching {
                    withTimeout(DATABASE_TIMEOUT_MILLIS) { loadTodayState(appContext) }
                }.getOrElse { WidgetState.unavailable() }

                widgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, buildRemoteViews(appContext, widgetId, state))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun loadTodayState(context: Context): WidgetState {
        val database = (context.applicationContext as? LiftlyApplication)?.database
            ?: LiftlyDatabase.create(context)
        val dao = database.dao()
        val today = LocalDate.now()
        val workouts = dao.allWorkouts().filterNot(WorkoutEntity::archived)
        val workoutsById = workouts.associateBy(WorkoutEntity::id)
        val datedEntries = dao.allSchedule().filter { it.date == today.toString() }

        if (datedEntries.any { it.isRestDay }) return WidgetState.restDay()

        val explicitlyScheduled = datedEntries
            .asSequence()
            .filterNot { it.isRestDay }
            .mapNotNull { workoutsById[it.workoutId] }
            .distinctBy(WorkoutEntity::id)
            .toList()

        val selectedWorkouts = if (datedEntries.isNotEmpty()) {
            explicitlyScheduled
        } else {
            val weekday = today.dayOfWeek.value.toString()
            workouts.filter { workout ->
                workout.weekDays.split(',').any { it.trim() == weekday }
            }
        }

        if (selectedWorkouts.isEmpty()) return WidgetState.noPlan()

        val selectedIds = selectedWorkouts.mapTo(hashSetOf(), WorkoutEntity::id)
        val exerciseCount = dao.allWorkoutExercises().count { it.workoutId in selectedIds }
        return WidgetState.workouts(selectedWorkouts, exerciseCount)
    }

    private fun buildRemoteViews(
        context: Context,
        widgetId: Int,
        state: WidgetState
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_today_workout).apply {
        setTextViewText(R.id.widget_status, state.status)
        setTextViewText(R.id.widget_title, state.title)
        setTextViewText(R.id.widget_subtitle, state.subtitle)
        setTextViewText(R.id.widget_action, state.action)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TODAY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        setOnClickPendingIntent(R.id.widget_action, pendingIntent)
    }

    private data class WidgetState(
        val status: String,
        val title: String,
        val subtitle: String,
        val action: String
    ) {
        companion object {
            fun workouts(workouts: List<WorkoutEntity>, exerciseCount: Int): WidgetState {
                val title = if (workouts.size == 1) {
                    workouts.first().name
                } else {
                    "${workouts.first().name}  +${workouts.size - 1}"
                }
                val subtitle = when {
                    workouts.size == 1 && exerciseCount == 0 -> "Sem exercícios configurados"
                    workouts.size == 1 && exerciseCount == 1 -> "1 exercício planejado para hoje"
                    workouts.size == 1 -> "$exerciseCount exercícios planejados para hoje"
                    exerciseCount == 0 -> "${workouts.size} treinos programados"
                    exerciseCount == 1 -> "${workouts.size} treinos  •  1 exercício"
                    else -> "${workouts.size} treinos  •  $exerciseCount exercícios"
                }
                return WidgetState(
                    status = if (workouts.size == 1) "HOJE" else "${workouts.size} TREINOS",
                    title = title,
                    subtitle = subtitle,
                    action = "Abrir treino  →"
                )
            }

            fun restDay() = WidgetState(
                status = "DESCANSO",
                title = "Dia de descanso",
                subtitle = "Recupere-se hoje. Seu próximo treino agradece.",
                action = "Abrir Liftly  →"
            )

            fun noPlan() = WidgetState(
                status = "DIA LIVRE",
                title = "Nenhum treino planejado",
                subtitle = "Abra o Liftly para organizar o seu dia.",
                action = "Planejar treino  →"
            )

            fun unavailable() = WidgetState(
                status = "LIFTLY",
                title = "Não foi possível atualizar",
                subtitle = "Toque para abrir o app e tentar novamente.",
                action = "Abrir Liftly  →"
            )
        }
    }

    companion object {
        /** MainActivity may consume this extra in the future if its start destination changes. */
        const val EXTRA_OPEN_TODAY = "com.liftly.app.extra.OPEN_TODAY"
        private const val DATABASE_TIMEOUT_MILLIS = 8_000L
    }
}

/** Lightweight entry point for repository/ViewModel write paths to refresh all installed widgets. */
object TodayWorkoutWidgetUpdater {
    const val ACTION_REFRESH = "com.liftly.app.action.REFRESH_TODAY_WIDGET"

    fun requestUpdate(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(context.applicationContext, TodayWorkoutWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
        )
    }
}
