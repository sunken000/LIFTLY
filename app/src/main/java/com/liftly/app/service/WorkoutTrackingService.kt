package com.liftly.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.liftly.app.MainActivity
import com.liftly.app.R
import com.liftly.app.audio.RestAlertPlayer
import com.liftly.app.audio.RestAlertSound
import java.util.Locale
import kotlin.math.abs

/**
 * Keeps a user-started workout visible while Liftly is backgrounded and owns the reliable rest
 * countdown. This service intentionally has no dependency on Compose or the app ViewModel.
 */
class WorkoutTrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private var state = TrackingState()
    private var restWakeLock: PowerManager.WakeLock? = null
    private var restAlertPlayer: RestAlertPlayer? = null

    private val countdownTick = object : Runnable {
        override fun run() {
            if (!state.active || state.restEndElapsedRealtime <= 0L) return

            val remainingMillis = state.restEndElapsedRealtime - SystemClock.elapsedRealtime()
            if (remainingMillis <= 0L) {
                finishRest()
                return
            }

            showForeground(buildNotification(remainingMillis))
            val delay = (remainingMillis % ONE_SECOND_MILLIS).takeIf { it > 0L }
                ?: ONE_SECOND_MILLIS
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        state = restoreState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_UPDATE -> handleStartOrUpdate(intent)
            ACTION_START_REST -> handleStartRest(intent)
            ACTION_CANCEL_REST -> handleCancelRest(intent)
            null -> restoreAfterRecreation()
            else -> restoreAfterRecreation()
        }

        return if (state.active) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(countdownTick)
        releaseRestWakeLock()
        stopRestAlertPlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStartOrUpdate(intent: Intent) {
        // The service may be started explicitly after a device reboot instead of through a
        // START_STICKY recreation. Rebuild the monotonic deadline from wall clock in that case.
        state = state.withRestDeadlineRestored()
        val updatedWorkoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME).orEmpty().trim()
        val updatedExerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty().trim()
        state = state.copy(
            active = true,
            workoutName = updatedWorkoutName.ifBlank { state.workoutName },
            exerciseName = updatedExerciseName.ifBlank { state.exerciseName },
            restJustFinished = false,
        )
        persistState()
        handler.removeCallbacks(countdownTick)
        showForeground(buildNotification(remainingRestMillis()))
        if (state.restEndElapsedRealtime > 0L) handler.post(countdownTick)
    }

    private fun handleStartRest(intent: Intent) {
        stopRestAlertPlayer()
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
            .coerceIn(0, MAX_REST_DURATION_SECONDS)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val updatedWorkoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME).orEmpty().trim()
        val updatedExerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty().trim()
        state = TrackingState(
            active = true,
            workoutName = updatedWorkoutName.ifBlank { state.workoutName },
            exerciseName = updatedExerciseName.ifBlank { state.exerciseName },
            restEndElapsedRealtime = nowElapsed + durationSeconds * ONE_SECOND_MILLIS,
            restEndEpochMillis = nowEpoch + durationSeconds * ONE_SECOND_MILLIS,
            bootEpochMillis = nowEpoch - nowElapsed,
            vibrateOnRestFinish = intent.getBooleanExtra(EXTRA_VIBRATE, true),
            playSoundOnRestFinish = intent.getBooleanExtra(EXTRA_PLAY_SOUND, true),
            restAlertSoundId = intent.getStringExtra(EXTRA_SOUND_ID)
                .orEmpty()
                .ifBlank { RestAlertSound.ASCENDING.id },
            restAlertSoundDurationSeconds = intent.getIntExtra(
                EXTRA_SOUND_DURATION_SECONDS,
                DEFAULT_SOUND_DURATION_SECONDS,
            ).coerceIn(
                RestAlertPlayer.MIN_DURATION_SECONDS,
                RestAlertPlayer.MAX_DURATION_SECONDS,
            ),
        )
        persistState()
        handler.removeCallbacks(countdownTick)
        acquireRestWakeLock(durationSeconds * ONE_SECOND_MILLIS)
        showForeground(buildNotification(durationSeconds * ONE_SECOND_MILLIS))
        handler.post(countdownTick)
    }

    private fun handleCancelRest(intent: Intent) {
        handler.removeCallbacks(countdownTick)
        releaseRestWakeLock()
        stopRestAlertPlayer()
        val updatedWorkoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME).orEmpty().trim()
        val updatedExerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty().trim()
        state = state.copy(
            active = true,
            workoutName = updatedWorkoutName.ifBlank { state.workoutName },
            exerciseName = updatedExerciseName.ifBlank { state.exerciseName },
            restEndElapsedRealtime = 0L,
            restEndEpochMillis = 0L,
            bootEpochMillis = 0L,
            vibrateOnRestFinish = false,
            playSoundOnRestFinish = false,
            restAlertSoundId = "",
            restAlertSoundDurationSeconds = 0,
            restJustFinished = false,
        )
        persistState()
        showForeground(buildNotification(null))
    }

    private fun restoreAfterRecreation() {
        if (!state.active) {
            stopSelf()
            return
        }

        state = state.withRestDeadlineRestored()
        persistState()
        val remainingMillis = remainingRestMillis()
        showForeground(buildNotification(remainingMillis))
        handler.removeCallbacks(countdownTick)
        if (state.restEndElapsedRealtime > 0L) {
            acquireRestWakeLock(remainingMillis ?: 0L)
            handler.post(countdownTick)
        }
    }

    private fun finishRest() {
        handler.removeCallbacks(countdownTick)
        releaseRestWakeLock()
        val shouldVibrate = state.vibrateOnRestFinish
        val shouldPlaySound = state.playSoundOnRestFinish
        val soundId = state.restAlertSoundId.ifBlank { RestAlertSound.ASCENDING.id }
        val soundDurationSeconds = state.restAlertSoundDurationSeconds.coerceIn(
            RestAlertPlayer.MIN_DURATION_SECONDS,
            RestAlertPlayer.MAX_DURATION_SECONDS,
        )
        state = state.copy(
            restEndElapsedRealtime = 0L,
            restEndEpochMillis = 0L,
            bootEpochMillis = 0L,
            vibrateOnRestFinish = false,
            playSoundOnRestFinish = false,
            restAlertSoundId = "",
            restAlertSoundDurationSeconds = 0,
            restJustFinished = true,
        )
        // Clear the deadline before alerting so a service recreation cannot notify twice.
        persistState()
        showForeground(buildNotification())
        if (shouldVibrate) vibrateRestFinished()
        if (shouldPlaySound) playRestFinishedSound(soundId, soundDurationSeconds)
    }

    private fun remainingRestMillis(): Long? {
        if (state.restEndElapsedRealtime <= 0L) return null
        return (state.restEndElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun showForeground(notification: Notification) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun acquireRestWakeLock(remainingMillis: Long) {
        releaseRestWakeLock()
        if (remainingMillis <= 0L) return
        val timeout = (remainingMillis + WAKE_LOCK_GRACE_MILLIS)
            .coerceAtMost(MAX_WAKE_LOCK_MILLIS)
        restWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(timeout)
            }
    }

    private fun releaseRestWakeLock() {
        restWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) wakeLock.release()
        }
        restWakeLock = null
    }

    private fun playRestFinishedSound(soundId: String, durationSeconds: Int) {
        val player = restAlertPlayer ?: RestAlertPlayer(applicationContext).also {
            restAlertPlayer = it
        }
        runCatching {
            player.play(
                soundId = soundId,
                durationSeconds = durationSeconds.coerceIn(
                    RestAlertPlayer.MIN_DURATION_SECONDS,
                    RestAlertPlayer.MAX_DURATION_SECONDS,
                ),
            )
        }.onFailure {
            stopRestAlertPlayer()
        }
    }

    private fun stopRestAlertPlayer() {
        restAlertPlayer?.let { player -> runCatching { player.stop() } }
        restAlertPlayer = null
    }

    private fun buildNotification(remainingMillis: Long? = remainingRestMillis()): Notification {
        val exercise = state.exerciseName.ifBlank { "Treino em andamento" }
        val title = state.workoutName.ifBlank { "Liftly • treino ativo" }
        val text = when {
            remainingMillis != null && remainingMillis > 0L -> {
                "Descanso ${formatWorkoutRestRemaining(remainingMillis)} • Próximo: $exercise"
            }
            state.restJustFinished -> "$exercise • Descanso concluído"
            else -> "Exercício atual: $exercise"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Treino em andamento",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Exibe o exercício atual e o cronômetro de descanso"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun vibrateRestFinished() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0L, 250L, 120L, 350L), -1),
        )
    }

    private fun persistState() {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putString(KEY_WORKOUT_NAME, state.workoutName)
            .putString(KEY_EXERCISE_NAME, state.exerciseName)
            .putLong(KEY_REST_END_ELAPSED, state.restEndElapsedRealtime)
            .putLong(KEY_REST_END_EPOCH, state.restEndEpochMillis)
            .putLong(KEY_BOOT_EPOCH, state.bootEpochMillis)
            .putBoolean(KEY_VIBRATE, state.vibrateOnRestFinish)
            .putBoolean(KEY_PLAY_SOUND, state.playSoundOnRestFinish)
            .putString(KEY_SOUND_ID, state.restAlertSoundId)
            .putInt(KEY_SOUND_DURATION_SECONDS, state.restAlertSoundDurationSeconds)
            .putBoolean(KEY_REST_JUST_FINISHED, state.restJustFinished)
            .apply()
    }

    private fun restoreState() = TrackingState(
        active = preferences.getBoolean(KEY_ACTIVE, false),
        workoutName = preferences.getString(KEY_WORKOUT_NAME, null).orEmpty(),
        exerciseName = preferences.getString(KEY_EXERCISE_NAME, null).orEmpty(),
        restEndElapsedRealtime = preferences.getLong(KEY_REST_END_ELAPSED, 0L),
        restEndEpochMillis = preferences.getLong(KEY_REST_END_EPOCH, 0L),
        bootEpochMillis = preferences.getLong(KEY_BOOT_EPOCH, 0L),
        vibrateOnRestFinish = preferences.getBoolean(KEY_VIBRATE, false),
        playSoundOnRestFinish = preferences.getBoolean(KEY_PLAY_SOUND, false),
        restAlertSoundId = preferences.getString(
            KEY_SOUND_ID,
            RestAlertSound.ASCENDING.id,
        ).orEmpty().ifBlank { RestAlertSound.ASCENDING.id },
        restAlertSoundDurationSeconds = preferences.getInt(
            KEY_SOUND_DURATION_SECONDS,
            DEFAULT_SOUND_DURATION_SECONDS,
        ).coerceIn(
            RestAlertPlayer.MIN_DURATION_SECONDS,
            RestAlertPlayer.MAX_DURATION_SECONDS,
        ),
        restJustFinished = preferences.getBoolean(KEY_REST_JUST_FINISHED, false),
    )

    private fun TrackingState.withRestDeadlineRestored(): TrackingState {
        if (restEndEpochMillis <= 0L) return this

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val currentBootEpoch = nowEpoch - nowElapsed
        val sameBoot = bootEpochMillis > 0L &&
            abs(currentBootEpoch - bootEpochMillis) <= BOOT_EPOCH_TOLERANCE_MILLIS
        val restoredDeadline = if (sameBoot && restEndElapsedRealtime > 0L) {
            restEndElapsedRealtime
        } else {
            nowElapsed + (restEndEpochMillis - nowEpoch).coerceAtLeast(0L)
        }
        return copy(restEndElapsedRealtime = restoredDeadline)
    }

    private data class TrackingState(
        val active: Boolean = false,
        val workoutName: String = "",
        val exerciseName: String = "",
        val restEndElapsedRealtime: Long = 0L,
        val restEndEpochMillis: Long = 0L,
        val bootEpochMillis: Long = 0L,
        val vibrateOnRestFinish: Boolean = false,
        val playSoundOnRestFinish: Boolean = false,
        val restAlertSoundId: String = RestAlertSound.ASCENDING.id,
        val restAlertSoundDurationSeconds: Int = DEFAULT_SOUND_DURATION_SECONDS,
        val restJustFinished: Boolean = false,
    )

    companion object {
        private const val CHANNEL_ID = "active_workout"
        private const val NOTIFICATION_ID = 1407
        private const val OPEN_APP_REQUEST_CODE = 1408
        private const val PREFERENCES_NAME = "workout_tracking_service"
        private const val ONE_SECOND_MILLIS = 1_000L
        private const val BOOT_EPOCH_TOLERANCE_MILLIS = 60_000L
        private const val MAX_REST_DURATION_SECONDS = 3_600
        private const val WAKE_LOCK_GRACE_MILLIS = 15_000L
        private const val MAX_WAKE_LOCK_MILLIS =
            MAX_REST_DURATION_SECONDS * ONE_SECOND_MILLIS + WAKE_LOCK_GRACE_MILLIS
        private const val WAKE_LOCK_TAG = "Liftly::WorkoutRest"
        private const val DEFAULT_SOUND_DURATION_SECONDS = 2

        private const val ACTION_START_OR_UPDATE = "com.liftly.app.action.START_OR_UPDATE_WORKOUT"
        private const val ACTION_START_REST = "com.liftly.app.action.START_WORKOUT_REST"
        private const val ACTION_CANCEL_REST = "com.liftly.app.action.CANCEL_WORKOUT_REST"

        private const val EXTRA_WORKOUT_NAME = "workout_name"
        private const val EXTRA_EXERCISE_NAME = "exercise_name"
        private const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val EXTRA_PLAY_SOUND = "play_sound"
        private const val EXTRA_SOUND_ID = "sound_id"
        private const val EXTRA_SOUND_DURATION_SECONDS = "sound_duration_seconds"

        private const val KEY_ACTIVE = "active"
        private const val KEY_WORKOUT_NAME = "workout_name"
        private const val KEY_EXERCISE_NAME = "exercise_name"
        private const val KEY_REST_END_ELAPSED = "rest_end_elapsed"
        private const val KEY_REST_END_EPOCH = "rest_end_epoch"
        private const val KEY_BOOT_EPOCH = "boot_epoch"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_PLAY_SOUND = "play_sound"
        private const val KEY_SOUND_ID = "sound_id"
        private const val KEY_SOUND_DURATION_SECONDS = "sound_duration_seconds"
        private const val KEY_REST_JUST_FINISHED = "rest_just_finished"

        /** Starts tracking or moves the ongoing notification to another exercise. */
        fun startOrUpdate(
            context: Context,
            exerciseName: String,
            workoutName: String = "",
        ) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_WORKOUT_NAME, workoutName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Starts a rest countdown that remains accurate while the app is backgrounded. */
        fun startRest(
            context: Context,
            exerciseName: String,
            durationSeconds: Int,
            workoutName: String = "",
            vibrateOnFinish: Boolean = true,
            playSoundOnFinish: Boolean = true,
            soundId: String = RestAlertSound.ASCENDING.id,
            soundDurationSeconds: Int = DEFAULT_SOUND_DURATION_SECONDS,
        ) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START_REST
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_WORKOUT_NAME, workoutName)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds.coerceIn(0, MAX_REST_DURATION_SECONDS))
                putExtra(EXTRA_VIBRATE, vibrateOnFinish)
                putExtra(EXTRA_PLAY_SOUND, playSoundOnFinish)
                putExtra(EXTRA_SOUND_ID, soundId)
                putExtra(
                    EXTRA_SOUND_DURATION_SECONDS,
                    soundDurationSeconds.coerceIn(
                        RestAlertPlayer.MIN_DURATION_SECONDS,
                        RestAlertPlayer.MAX_DURATION_SECONDS,
                    ),
                )
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Cancels an active rest without vibrating while keeping workout tracking alive. */
        fun cancelRest(
            context: Context,
            exerciseName: String = "",
            workoutName: String = "",
        ) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_CANCEL_REST
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_WORKOUT_NAME, workoutName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Returns the persisted wall-clock rest deadline so UI timers can resynchronize. */
        fun getRestEndEpochMillis(context: Context): Long? {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(KEY_ACTIVE, false)) return null
            return preferences.getLong(KEY_REST_END_EPOCH, 0L)
                .takeIf { it > System.currentTimeMillis() }
        }

        /** Explicitly ends tracking, removes the ongoing notification and clears saved state. */
        fun stop(context: Context) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            context.stopService(Intent(context, WorkoutTrackingService::class.java))
        }

    }
}

internal fun formatWorkoutRestRemaining(remainingMillis: Long): String {
    val totalSeconds = ((remainingMillis.coerceAtLeast(0L) + 999L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
