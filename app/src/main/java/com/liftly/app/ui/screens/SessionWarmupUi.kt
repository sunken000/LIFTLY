package com.liftly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftly.app.domain.AutomaticWarmupPlan
import com.liftly.app.domain.ExerciseWarmupPlan
import com.liftly.app.domain.WarmupSetKind
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.NeonIcon
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

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (blockCompleted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonIcon(
                imageVector = if (blockCompleted) Icons.Default.CheckCircle else Icons.Default.FitnessCenter,
                contentDescription = null,
                selected = true,
                size = 38.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (blockCompleted) "Concluído • não entra no progresso" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (blockCompleted) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, null)
                    Text("Refazer", Modifier.padding(start = 4.dp))
                }
            }
        }

        if (!blockCompleted) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEach { step ->
                    val completed = step.id in completedIds
                    val thisTimerActive = activeTimerStepId == step.id && timerRunning
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (completed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = completed,
                                    onCheckedChange = { onToggle(step, it) },
                                    enabled = !timerRunning || completed,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    Text(
                                        if (step.workoutExerciseId == null) step.title else step.subtitle,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(step.instruction, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    step.prescription,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                step.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (thisTimerActive) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        if (timerIsRest) "Descanso: ${warmupClock(timerSecondsLeft)}"
                                        else "Tempo: ${warmupClock(timerSecondsLeft)}",
                                        Modifier.weight(1f).padding(start = 8.dp),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    TextButton(onClick = onFinishTimer) { Text("Concluir") }
                                }
                            } else if (!completed && step.timerSeconds != null) {
                                OutlinedButton(
                                    onClick = { onStartTimer(step) },
                                    enabled = !timerRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Timer, null)
                                    Text("Cronometrar ${warmupClock(step.timerSeconds)}", Modifier.padding(start = 6.dp))
                                }
                            }
                            if (!completed && step.restAfterSeconds > 0) {
                                Text(
                                    "Depois: ${step.restAfterSeconds}s de descanso.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
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
