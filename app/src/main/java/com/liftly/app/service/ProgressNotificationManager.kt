package com.liftly.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.liftly.app.MainActivity
import com.liftly.app.R
import com.liftly.app.data.UserPreferences
import com.liftly.app.domain.TrainingMomentum

object ProgressNotificationManager {
    private const val CHANNEL_ID = "liftly_goals"
    private const val NOTIFICATION_ID = 3_210

    fun notifyAfterWorkout(context: Context, momentum: TrainingMomentum, preferences: UserPreferences) {
        if (!preferences.goalNotifications && !preferences.streakNotifications) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val justReachedGoal = momentum.currentWeekWorkouts == momentum.weeklyGoal
        val title: String
        val message: String
        when {
            justReachedGoal && preferences.streakNotifications && momentum.completedWeekStreak >= 2 -> {
                title = "Sequência mantida! 🔥"
                message = "Meta concluída por ${momentum.completedWeekStreak} semanas seguidas: ${momentum.currentWeekWorkouts}/${momentum.weeklyGoal} treinos."
            }
            justReachedGoal && preferences.goalNotifications -> {
                title = "Meta semanal alcançada!"
                message = "Você completou ${momentum.currentWeekWorkouts}/${momentum.weeklyGoal} treinos nesta semana."
            }
            momentum.goalReached && preferences.goalNotifications -> {
                title = "Você superou a meta!"
                message = "Já são ${momentum.currentWeekWorkouts} treinos nesta semana; sua meta era ${momentum.weeklyGoal}."
            }
            preferences.goalNotifications -> {
                title = "Mais perto da meta"
                message = "${momentum.currentWeekWorkouts}/${momentum.weeklyGoal} treinos concluídos; faltam ${momentum.remainingThisWeek}."
            }
            else -> return
        }

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Metas e sequências",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Progresso da meta semanal e sequências de treino"
            }
        )
    }
}
