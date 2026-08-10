package com.liftly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftly.app.domain.AutomaticWarmupPlan
import com.liftly.app.domain.ExerciseWarmupPlan
import com.liftly.app.domain.WarmupSetKind
import com.liftly.app.ui.components.TrainingSetSurface
import java.util.Locale
import kotlin.math.roundToInt

internal data class SessionWarmupUiStep(
    val id: String,
    val workoutExerciseId: String?,
    val title: String,
    val subtitle: String,
    val prescription: String,
    val instruction: String,
    val reason: String,
    val timerSeconds: Int? = null,
    val restAfterSeconds: Int = 0,
)

internal fun AutomaticWarmupPlan.generalSessionSteps(): List<SessionWarmupUiStep> =
    general.steps.mapIndexed { index, step ->
        SessionWarmupUiStep(
            id = "general-$index",
            workoutExerciseId = null,
            title = step.title,
            subtitle = "Preparação geral",
            prescription = warmupClock(step.durationSeconds),
            instruction = step.instruction,
            reason = step.reason,
            timerSeconds = step.durationSeconds,
        )
    }

internal fun ExerciseWarmupPlan.sessionSteps(): List<SessionWarmupUiStep> = sets.map { set ->
    val prescription = when (set.kind) {
        WarmupSetKind.LOADED_APPROACH -> "${set.repetitions ?: 0} reps × ${warmupLoad(set.loadKg)}"
        WarmupSetKind.MOVEMENT_REHEARSAL -> "${set.repetitions ?: 0} reps"
        WarmupSetKind.TIME_ACCLIMATION -> warmupClock(set.durationSeconds ?: 0)
        WarmupSetKind.DISTANCE_ACCLIMATION -> "${warmupDistance(set.distanceMeters)} m"
    }
    SessionWarmupUiStep(
        id = "approach-$workoutExerciseId-${set.number}",
        workoutExerciseId = workoutExerciseId,
        title = exerciseName,
        subtitle = "Aproximação ${set.number} • $movementPattern",
        prescription = prescription,
        instruction = set.effortCue,
        reason = "${set.explanation} $explanation",
        timerSeconds = if (set.kind == WarmupSetKind.TIME_ACCLIMATION) set.durationSeconds else null,
        restAfterSeconds = set.restAfterSeconds,
    )
}

@Composable
internal fun SessionWarmupBlock(
    title: String,
    subtitle: String,
    steps: List<SessionWarmupUiStep>,
    completedIds: Set<String>,
    activeTimerStepId: String?,
    timerSecondsLeft: Int,
    timerIsRest: Boolean,
    onToggle: (SessionWarmupUiStep, Boolean) -> Unit,
    onStartTimer: (SessionWarmupUiStep) -> Unit,
    onFinishTimer: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val timerRunning = timerSecondsLeft > 0
    val blockHasActiveTimer = timerRunning && steps.any { it.id == activeTimerStepId }
    val blockCompleted = steps.all { it.id in completedIds } && !blockHasActiveTimer

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    if (blockCompleted) "Aquecimento concluído • fora do volume oficial" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (blockCompleted) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refazer", Modifier.padding(start = 4.dp))
                }
            }
        }

        val firstPendingId = steps.firstOrNull { it.id !in completedIds }?.id
        val visibleSteps = steps.filter { it.id in completedIds || it.id == firstPendingId }
        visibleSteps.forEach { step ->
            val index = steps.indexOf(step)
            val completed = step.id in completedIds
            val thisTimerActive = activeTimerStepId == step.id && timerRunning
            TrainingSetSurface(
                numberLabel = (index + 1).toString().padStart(2, '0'),
                title = step.title,
                subtitle = step.subtitle,
                completed = completed,
                onCompletedChange = { onToggle(step, it) },
                badge = "AQUECIMENTO",
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(step.prescription, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(step.instruction, style = MaterialTheme.typography.bodySmall)
                    }
                    if (thisTimerActive) {
                        Text(
                            if (timerIsRest) "DESC ${warmupClock(timerSecondsLeft)}" else warmupClock(timerSecondsLeft),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(step.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    thisTimerActive -> OutlinedButton(onClick = onFinishTimer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Text(if (timerIsRest) "Encerrar descanso" else "Concluir tempo", Modifier.padding(start = 6.dp))
                    }
                    !completed && step.timerSeconds != null -> OutlinedButton(
                        onClick = { onStartTimer(step) },
                        enabled = !timerRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Text("Iniciar ${warmupClock(step.timerSeconds)}", Modifier.padding(start = 6.dp))
                    }
                }
                if (!completed && step.restAfterSeconds > 0) {
                    Text(
                        "Depois desta série: ${step.restAfterSeconds}s de descanso.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun warmupClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, safe / 60, safe % 60)
}

private fun warmupLoad(load: Double?): String {
    val safe = load?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    return if (safe == safe.roundToInt().toDouble()) "${safe.roundToInt()} kg"
    else String.format(Locale.ROOT, "%.1f kg", safe)
}

private fun warmupDistance(distance: Double?): String {
    val safe = distance?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    return if (safe == safe.roundToInt().toDouble()) safe.roundToInt().toString()
    else String.format(Locale.ROOT, "%.1f", safe)
}
