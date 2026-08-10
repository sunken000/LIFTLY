package com.liftly.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.WorkoutExerciseEntity
import com.liftly.app.domain.AutomaticWarmupGenerator
import com.liftly.app.domain.AUTOMATIC_WARMUP_DISCLAIMER
import com.liftly.app.domain.CurrentExerciseSetPerformance
import com.liftly.app.domain.ExerciseSubstitutionEngine
import com.liftly.app.domain.ExerciseSubstitutionOptions
import com.liftly.app.domain.HistoricalExercisePerformance
import com.liftly.app.domain.ProgressionCoach
import com.liftly.app.domain.ProgressionCoachInput
import com.liftly.app.domain.ProgressionRecommendation
import com.liftly.app.domain.ProgressionStatus
import com.liftly.app.domain.PROGRESSION_COACH_DISCLAIMER
import com.liftly.app.service.WorkoutTrackingService
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.SessionWarmupRuntimeState
import com.liftly.app.ui.components.ExerciseDetailSheet
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.components.PlateCalculatorSheet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: AppViewModel,
    onOpenSession: (String) -> Unit,
    onOpenWarmupSession: (String) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenStopwatch: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val items by vm.workoutExercises.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val automaticWarmupSessions by vm.automaticWarmupSessions.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val scheduledId = schedule.firstOrNull { it.date == today.toString() && !it.isRestDay }?.workoutId
    val weekdayNumber = today.dayOfWeek.value.toString()
    val workout = workouts.firstOrNull { it.id == scheduledId }
        ?: workouts.firstOrNull { it.weekDays.split(",").contains(weekdayNumber) }
    val active = sessions.firstOrNull { it.status == "Em andamento" }
    val workoutItems = items.filter { it.workoutId == workout?.id }.sortedBy { it.orderIndex }
    val context = LocalContext.current
    var pendingSessionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val action = pendingSessionAction
        pendingSessionAction = null
        action?.invoke()
    }

    fun runWithWorkoutNotificationPermission(action: () -> Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSessionAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "LIFTLY / HOJE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pt-BR")))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR"))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodayQuickAction(Icons.Default.CalendarMonth, "Planejar", onOpenCalendar, Modifier.weight(1f))
                    TodayQuickAction(Icons.Default.Timer, "Cronômetro", onOpenStopwatch, Modifier.weight(1f))
                    TodayQuickAction(Icons.Default.LibraryMusic, "Música", onOpenMusic, Modifier.weight(1f))
                }
            }

            if (active != null) {
                item {
                    Surface(
                        onClick = {
                            runWithWorkoutNotificationPermission {
                                if (active.id in automaticWarmupSessions) onOpenWarmupSession(active.id)
                                else onOpenSession(active.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (active.isTestMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            if (active.isTestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(4.dp).height(42.dp).background(
                                    if (active.isTestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    if (active.isTestMode) "MODO TESTE / AO VIVO" else "TREINO / AO VIVO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    active.workoutName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("RETOMAR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (workout == null) {
                item { EmptyToday(onOpenCalendar) }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "PLANO DO DIA",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("PRONTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            }
                            Text(workout.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            if (workout.description.isNotBlank()) {
                                Text(workout.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                TodayMetric("EXERCÍCIOS", workoutItems.size.toString(), Modifier.weight(1f))
                                TodayMetric("SÉRIES", workoutItems.sumOf { it.sets }.toString(), Modifier.weight(1f))
                            }
                            GradientActionButton(
                                onClick = {
                                    runWithWorkoutNotificationPermission {
                                        vm.startSession(workoutId = workout.id, automaticWarmup = true, onStarted = onOpenWarmupSession)
                                    }
                                },
                                enabled = workoutItems.isNotEmpty() && active == null,
                                modifier = Modifier.fillMaxWidth(),
                                onClickLabel = "Iniciar ${workout.name}",
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Text("Iniciar treino", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(
                                    onClick = {
                                        runWithWorkoutNotificationPermission { vm.startSession(workout.id, onStarted = onOpenSession) }
                                    },
                                    enabled = workoutItems.isNotEmpty() && active == null,
                                ) { Text("Sem aquecimento") }
                                TextButton(
                                    onClick = {
                                        runWithWorkoutNotificationPermission { vm.startTestSession(workout.id, onOpenSession) }
                                    },
                                    enabled = workoutItems.isNotEmpty() && active == null,
                                ) {
                                    Icon(Icons.Default.Science, null)
                                    Text("Modo teste", Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SEQUÊNCIA",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${workoutItems.size} movimentos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                itemsIndexed(workoutItems, key = { _, item -> item.id }) { index, item ->
                    val exercise = exercises.firstOrNull { it.id == item.exerciseId }
                    Column {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (index + 1).toString().padStart(2, '0'),
                                modifier = Modifier.width(42.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    exercise?.name ?: "Exercício",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${item.sets} × ${item.repMin}–${item.repMax} ${if (item.trackingMode == "Tempo") "s" else if (item.trackingMode == "Distância") "m" else "reps"}  /  ${item.targetLoadKg.toClean()} kg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    "${item.restSeconds}s",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyToday(onOpenCalendar: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RECUPERAÇÃO / HOJE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Sem treino programado.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Descanso também faz parte da progressão. Se quiser treinar, ajuste sua semana.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientActionButton(onClick = onOpenCalendar, onClickLabel = "Planejar semana") {
                Text("Planejar semana", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    vm: AppViewModel,
    sessionId: String,
    showAutomaticWarmup: Boolean = false,
    onMinimize: () -> Unit,
    onFinished: () -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val allSets by vm.sessionSets.collectAsStateWithLifecycle()
    val workoutItems by vm.workoutExercises.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val sessionWarmupStates by vm.sessionWarmupStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val session = sessions.firstOrNull { it.id == sessionId }
    val sets = allSets
        .filter { it.sessionId == sessionId }
        .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber })
    val grouped = sets.groupBy { "${it.workoutExerciseId}:${it.exerciseId}" }
    val orderedExerciseGroups = grouped.values.sortedBy { it.first().exerciseOrder }
    val completed = sets.count { it.completed }
    val progress = if (sets.isEmpty()) 0f else completed.toFloat() / sets.size
    var confirmFinish by remember { mutableStateOf(false) }
    var substitutionTarget by remember { mutableStateOf<SessionSetEntity?>(null) }
    var detailTarget by remember { mutableStateOf<SessionSetEntity?>(null) }
    var unavailableEquipmentState by rememberSaveable(sessionId) { mutableStateOf("") }
    val unavailableEquipment = unavailableEquipmentState
        .split('|')
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()

    fun setEquipmentUnavailable(label: String, unavailable: Boolean) {
        val updated = if (unavailable) unavailableEquipment + label else unavailableEquipment - label
        unavailableEquipmentState = updated.sorted().joinToString("|")
    }
    var restEndsAt by rememberSaveable(sessionId) {
        mutableLongStateOf(WorkoutTrackingService.getRestEndEpochMillis(context) ?: 0L)
    }
    var lastNotifiedRestEnd by rememberSaveable { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val sessionWorkoutItems = remember(session?.workoutId, workoutItems) {
        workoutItems.filter { it.workoutId == session?.workoutId }.sortedBy { it.orderIndex }
    }
    val automaticWarmupPlan = remember(
        showAutomaticWarmup,
        session?.workoutId,
        sessionWorkoutItems,
        exercises,
    ) {
        if (showAutomaticWarmup && sessionWorkoutItems.isNotEmpty()) {
            AutomaticWarmupGenerator().generate(sessionWorkoutItems, exercises)
        } else {
            null
        }
    }
    val generalWarmupSteps = remember(automaticWarmupPlan) {
        automaticWarmupPlan?.generalSessionSteps().orEmpty()
    }
    val exerciseWarmupSteps = remember(automaticWarmupPlan) {
        automaticWarmupPlan?.exercises
            ?.associate { it.workoutExerciseId to it.sessionSteps() }
            .orEmpty()
    }
    val allWarmupSteps = remember(generalWarmupSteps, exerciseWarmupSteps) {
        generalWarmupSteps + exerciseWarmupSteps.values.flatten()
    }
    val warmupState = sessionWarmupStates[sessionId] ?: SessionWarmupRuntimeState()
    val warmupCompletedIds = warmupState.completedStepIds
    val warmupTimerStepId = warmupState.timerStepId
    val warmupTimerEndsAt = warmupState.timerEndsAtEpochMillis
    val warmupTimerIsRest = warmupState.timerIsRest
    val warmupTimerFollowUpRest = warmupState.timerFollowUpRestSeconds
    var warmupSecondsLeft by remember { mutableIntStateOf(0) }

    fun startWarmupRestAlarm(step: SessionWarmupUiStep, durationSeconds: Int) {
        WorkoutTrackingService.startRest(
            context = context,
            exerciseName = "Aquecimento: ${step.title}",
            durationSeconds = durationSeconds,
            workoutName = session?.workoutName.orEmpty(),
            vibrateOnFinish = preferences.restEndVibration,
            playSoundOnFinish = preferences.restEndSound,
            soundId = preferences.restEndSoundType,
            soundDurationSeconds = preferences.restEndSoundDurationSeconds,
        )
    }

    fun clearWarmupTimer() {
        if (warmupTimerIsRest) {
            WorkoutTrackingService.cancelRest(
                context = context,
                exerciseName = "Aquecimento",
                workoutName = session?.workoutName.orEmpty(),
            )
        }
        vm.updateSessionWarmupState(sessionId) {
            it.copy(
                timerStepId = null,
                timerEndsAtEpochMillis = 0L,
                timerIsRest = false,
                timerFollowUpRestSeconds = 0,
            )
        }
        warmupSecondsLeft = 0
    }

    fun setWarmupCompleted(stepId: String, checked: Boolean) {
        val shouldClearTimer = !checked && warmupTimerStepId == stepId
        vm.updateSessionWarmupState(sessionId) { current ->
            current.copy(
                completedStepIds = if (checked) {
                    current.completedStepIds + stepId
                } else {
                    current.completedStepIds - stepId
                },
                timerStepId = if (shouldClearTimer) null else current.timerStepId,
                timerEndsAtEpochMillis = if (shouldClearTimer) 0L else current.timerEndsAtEpochMillis,
                timerIsRest = if (shouldClearTimer) false else current.timerIsRest,
                timerFollowUpRestSeconds = if (shouldClearTimer) 0 else current.timerFollowUpRestSeconds,
            )
        }
        if (shouldClearTimer) warmupSecondsLeft = 0
        if (shouldClearTimer && warmupTimerIsRest) {
            WorkoutTrackingService.cancelRest(
                context = context,
                exerciseName = "Aquecimento",
                workoutName = session?.workoutName.orEmpty(),
            )
        }
    }

    fun startWarmupTimer(
        step: SessionWarmupUiStep,
        durationSeconds: Int,
        isRest: Boolean = false,
        followUpRest: Int = 0,
    ) {
        if (durationSeconds <= 0) return
        if (warmupSecondsLeft > 0 && warmupTimerStepId != step.id) return
        if (secondsLeft > 0) {
            restEndsAt = 0L
            WorkoutTrackingService.cancelRest(
                context = context,
                exerciseName = "Aquecimento: ${step.title}",
                workoutName = session?.workoutName.orEmpty(),
            )
        }
        val timerEndsAt = System.currentTimeMillis() + durationSeconds * 1_000L
        vm.updateSessionWarmupState(sessionId) {
            it.copy(
                timerStepId = step.id,
                timerEndsAtEpochMillis = timerEndsAt,
                timerIsRest = isRest,
                timerFollowUpRestSeconds = followUpRest.coerceAtLeast(0),
            )
        }
        warmupSecondsLeft = durationSeconds
        if (isRest) startWarmupRestAlarm(step, durationSeconds)
    }

    fun finishWarmupTimer() {
        val stepId = warmupTimerStepId ?: return
        val step = allWarmupSteps.firstOrNull { it.id == stepId }
        val nextRest = if (warmupTimerIsRest) 0 else warmupTimerFollowUpRest
        if (step != null && nextRest > 0) {
            val nextEnd = System.currentTimeMillis() + nextRest * 1_000L
            vm.updateSessionWarmupState(sessionId) {
                it.copy(
                    completedStepIds = it.completedStepIds + stepId,
                    timerStepId = stepId,
                    timerEndsAtEpochMillis = nextEnd,
                    timerIsRest = true,
                    timerFollowUpRestSeconds = 0,
                )
            }
            warmupSecondsLeft = nextRest
            startWarmupRestAlarm(step, nextRest)
        } else {
            vm.updateSessionWarmupState(sessionId) {
                it.copy(
                    completedStepIds = it.completedStepIds + stepId,
                    timerStepId = null,
                    timerEndsAtEpochMillis = 0L,
                    timerIsRest = false,
                    timerFollowUpRestSeconds = 0,
                )
            }
            warmupSecondsLeft = 0
        }
    }

    fun resetWarmupSteps(stepsToReset: List<SessionWarmupUiStep>) {
        val ids = stepsToReset.mapTo(mutableSetOf()) { it.id }
        val shouldClearTimer = warmupTimerStepId in ids
        vm.updateSessionWarmupState(sessionId) {
            it.copy(
                completedStepIds = it.completedStepIds - ids,
                timerStepId = if (shouldClearTimer) null else it.timerStepId,
                timerEndsAtEpochMillis = if (shouldClearTimer) 0L else it.timerEndsAtEpochMillis,
                timerIsRest = if (shouldClearTimer) false else it.timerIsRest,
                timerFollowUpRestSeconds = if (shouldClearTimer) 0 else it.timerFollowUpRestSeconds,
            )
        }
        if (shouldClearTimer) warmupSecondsLeft = 0
        if (shouldClearTimer && warmupTimerIsRest) {
            WorkoutTrackingService.cancelRest(
                context = context,
                exerciseName = "Aquecimento",
                workoutName = session?.workoutName.orEmpty(),
            )
        }
    }

    LaunchedEffect(warmupTimerStepId, warmupTimerEndsAt) {
        val stepId = warmupTimerStepId ?: run {
            warmupSecondsLeft = 0
            return@LaunchedEffect
        }
        val targetEnd = warmupTimerEndsAt
        if (targetEnd <= 0L) return@LaunchedEffect
        while (targetEnd > System.currentTimeMillis()) {
            warmupSecondsLeft = ((targetEnd - System.currentTimeMillis() + 999L) / 1_000L).toInt()
            delay(250)
        }
        warmupSecondsLeft = 0
        if (warmupTimerStepId == stepId && warmupTimerEndsAt == targetEnd) finishWarmupTimer()
    }

    val nextWorkSet = sets.firstOrNull { !it.completed }
    val nextWarmupStep = if (showAutomaticWarmup) {
        generalWarmupSteps.firstOrNull { it.id !in warmupCompletedIds }
            ?: nextWorkSet?.workoutExerciseId?.let { workoutExerciseId ->
                exerciseWarmupSteps[workoutExerciseId]
                    ?.firstOrNull { it.id !in warmupCompletedIds }
            }
    } else null
    val currentExerciseName = nextWarmupStep?.let { "Aquecimento: ${it.title}" }
        ?: nextWorkSet?.exerciseName
        ?: if (sets.isNotEmpty()) "Todas as séries concluídas" else "Preparando treino"

    LaunchedEffect(restEndsAt) {
        val targetEnd = restEndsAt
        if (targetEnd <= 0L) {
            secondsLeft = 0
            return@LaunchedEffect
        }

        while (targetEnd > System.currentTimeMillis()) {
            secondsLeft = ((targetEnd - System.currentTimeMillis() + 999) / 1000).toInt()
            delay(250)
        }
        secondsLeft = 0
        if (restEndsAt == targetEnd && lastNotifiedRestEnd != targetEnd) {
            lastNotifiedRestEnd = targetEnd
            snackbarHostState.showSnackbar(
                message = "Descanso concluído. Hora da próxima série.",
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(session?.id, session?.workoutName, currentExerciseName) {
        if (session != null) {
            WorkoutTrackingService.startOrUpdate(
                context = context,
                exerciseName = currentExerciseName,
                workoutName = session.workoutName,
            )
        }
    }

    LaunchedEffect(preferences.restTimer) {
        if (!preferences.restTimer) {
            restEndsAt = 0L
            WorkoutTrackingService.cancelRest(
                context = context,
                exerciseName = currentExerciseName,
                workoutName = session?.workoutName.orEmpty(),
            )
        }
    }

    BackHandler(enabled = !confirmFinish, onBack = onMinimize)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.workoutName ?: "Treino")
                        if (session?.isTestMode == true) {
                            Text(
                                "MODO TESTE • não salva progresso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onMinimize) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        Text("Minimizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(16.dp)) {
                Button(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth(), enabled = sets.isNotEmpty()) {
                    Text(
                        if (session?.isTestMode == true) "Encerrar teste e descartar"
                        else if (completed == sets.size && sets.isNotEmpty()) "Finalizar treino"
                        else "Finalizar como parcial"
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = if (secondsLeft > 0) 104.dp else 90.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progresso", fontWeight = FontWeight.SemiBold)
                        Text("$completed/${sets.size} séries", color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                }
            }
            if (unavailableEquipment.isNotEmpty()) {
                item(key = "unavailable-equipment-summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "Equipamentos ocupados agora",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(unavailableEquipment.toList().sorted(), key = { "occupied-$it" }) { equipment ->
                                FilterChip(
                                    selected = true,
                                    onClick = { setEquipmentUnavailable(equipment, false) },
                                    label = { Text("$equipment • liberar") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
            }
            if (showAutomaticWarmup && generalWarmupSteps.isNotEmpty()) {
                item(key = "automatic-warmup-intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Aquecimento dentro do treino",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "A preparação aparece imediatamente antes de cada exercício e não conta como série, volume ou progresso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "automatic-warmup-general") {
                    SessionWarmupBlock(
                        title = "Preparação geral",
                        subtitle = "Faça uma vez antes do primeiro exercício",
                        steps = generalWarmupSteps,
                        completedIds = warmupCompletedIds,
                        activeTimerStepId = warmupTimerStepId,
                        timerSecondsLeft = warmupSecondsLeft,
                        timerIsRest = warmupTimerIsRest,
                        onToggle = { step, checked -> setWarmupCompleted(step.id, checked) },
                        onStartTimer = { step ->
                            step.timerSeconds?.let { startWarmupTimer(step, it) }
                        },
                        onFinishTimer = ::finishWarmupTimer,
                        onReset = { resetWarmupSteps(generalWarmupSteps) },
                    )
                }
            }
            orderedExerciseGroups.forEachIndexed { groupIndex, exerciseSets ->
                val workoutExerciseId = exerciseSets.first().workoutExerciseId
                val currentExercise = exercises.firstOrNull { it.id == exerciseSets.first().exerciseId }
                val plannedWorkoutItem = sessionWorkoutItems.firstOrNull { it.id == workoutExerciseId }
                val plannedExercise = exercises.firstOrNull { it.id == plannedWorkoutItem?.exerciseId }
                val isSubstituted = plannedExercise != null && currentExercise?.id != plannedExercise.id
                val equipmentFamilies = currentExercise?.let {
                    ExerciseSubstitutionEngine.equipmentFamilyLabels(it.equipment)
                }.orEmpty().filterNot { it == "Peso corporal" || it == "Sem equipamento" }
                val warmupBeforeExercise = exerciseWarmupSteps[workoutExerciseId].orEmpty()
                val isFirstGroupForWorkoutItem = orderedExerciseGroups
                    .indexOfFirst { it.first().workoutExerciseId == workoutExerciseId } == groupIndex
                if (showAutomaticWarmup && warmupBeforeExercise.isNotEmpty() && isFirstGroupForWorkoutItem) {
                    item(key = "$workoutExerciseId-automatic-warmup") {
                        SessionWarmupBlock(
                            title = "Antes de ${exerciseSets.first().exerciseName}",
                            subtitle = "Aquecimento específico deste exercício",
                            steps = warmupBeforeExercise,
                            completedIds = warmupCompletedIds,
                            activeTimerStepId = warmupTimerStepId,
                            timerSecondsLeft = warmupSecondsLeft,
                            timerIsRest = warmupTimerIsRest,
                            onToggle = { step, checked ->
                                setWarmupCompleted(step.id, checked)
                                if (checked && step.restAfterSeconds > 0) {
                                    startWarmupTimer(step, step.restAfterSeconds, isRest = true)
                                }
                            },
                            onStartTimer = { step ->
                                step.timerSeconds?.let {
                                    startWarmupTimer(
                                        step = step,
                                        durationSeconds = it,
                                        followUpRest = step.restAfterSeconds,
                                    )
                                }
                            },
                            onFinishTimer = ::finishWarmupTimer,
                            onReset = { resetWarmupSteps(warmupBeforeExercise) },
                        )
                    }
                }
                item(key = "${exerciseSets.first().workoutExerciseId}:${exerciseSets.first().exerciseId}-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                exerciseSets.first().exerciseName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = { detailTarget = exerciseSets.first() }) {
                                Icon(Icons.Default.Info, contentDescription = "Ver execução")
                            }
                            TextButton(
                                onClick = { substitutionTarget = exerciseSets.first() },
                                enabled = exerciseSets.any { !it.completed },
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Trocar agora")
                            }
                        }
                        if (exerciseSets.any { !it.completed } && equipmentFamilies.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    "Disponibilidade do equipamento",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(equipmentFamilies, key = { "$workoutExerciseId-equipment-$it" }) { family ->
                                        val occupied = family in unavailableEquipment
                                        FilterChip(
                                            selected = occupied,
                                            onClick = {
                                                setEquipmentUnavailable(family, !occupied)
                                                if (!occupied) substitutionTarget = exerciseSets.first()
                                            },
                                            label = { Text(if (occupied) "$family ocupado" else family) },
                                            leadingIcon = if (occupied) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                        if (isSubstituted && plannedExercise != null && exerciseSets.any { !it.completed }) {
                            OutlinedButton(
                                onClick = {
                                    vm.substituteSessionExercise(
                                        sessionId = sessionId,
                                        workoutExerciseId = workoutExerciseId,
                                        replacementExerciseId = plannedExercise.id,
                                    )
                                },
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Voltar para ${plannedExercise.name}")
                            }
                        }
                        ProgressionCoachCard(
                            recommendation = buildCoachRecommendation(
                                currentSessionId = sessionId,
                                exerciseSets = exerciseSets,
                                allSets = allSets,
                                sessions = sessions,
                                workoutItem = workoutItems.firstOrNull { it.id == exerciseSets.first().workoutExerciseId },
                                exercise = exercises.firstOrNull { it.id == exerciseSets.first().exerciseId },
                            ),
                            hasPendingSet = exerciseSets.any { !it.completed },
                        )
                    }
                }
                items(exerciseSets, key = { it.id }) { set ->
                    val equipment = exercises.firstOrNull { it.id == set.exerciseId }?.equipment.orEmpty()
                    SessionSetRow(
                        set = set,
                        supportsPlateCalculator = equipment.contains("barra", ignoreCase = true) ||
                            equipment.contains("smith", ignoreCase = true) ||
                            equipment.contains("anilha", ignoreCase = true),
                    ) { reps, load, check, rir, painLevel ->
                        vm.saveSet(set, reps, load, toggleCompletion = check, rir = rir, painLevel = painLevel)
                        if (check && !set.completed && warmupSecondsLeft > 0) clearWarmupTimer()
                        if (check && !set.completed && preferences.restTimer) {
                            val restSeconds = workoutItems
                                .firstOrNull { it.id == set.workoutExerciseId }
                                ?.restSeconds
                                ?.coerceIn(0, 3_600)
                                ?: 60
                            if (restSeconds > 0) {
                                restEndsAt = System.currentTimeMillis() + restSeconds * 1_000L
                                val nextExerciseName = sets
                                    .firstOrNull { candidate -> !candidate.completed && candidate.id != set.id }
                                    ?.exerciseName
                                    ?: "Todas as séries concluídas"
                                WorkoutTrackingService.startRest(
                                    context = context,
                                    exerciseName = nextExerciseName,
                                    durationSeconds = restSeconds,
                                    workoutName = session?.workoutName.orEmpty(),
                                    vibrateOnFinish = preferences.restEndVibration,
                                    playSoundOnFinish = preferences.restEndSound,
                                    soundId = preferences.restEndSoundType,
                                    soundDurationSeconds = preferences.restEndSoundDurationSeconds,
                                )
                            }
                        }
                    }
                }
            }
            if (showAutomaticWarmup && allWarmupSteps.isNotEmpty()) {
                item(key = "automatic-warmup-disclaimer") {
                    Text(
                        AUTOMATIC_WARMUP_DISCLAIMER,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

            AnimatedVisibility(
                visible = secondsLeft > 0,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Default.Timer, contentDescription = null, selected = true, size = 30.dp)
                        Text(
                            "Descanso: ${secondsLeft}s",
                            Modifier.weight(1f).padding(start = 8.dp),
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                restEndsAt = 0L
                                WorkoutTrackingService.cancelRest(
                                    context = context,
                                    exerciseName = currentExerciseName,
                                    workoutName = session?.workoutName.orEmpty(),
                                )
                            }
                        ) { Text("Pular") }
                    }
                }
            }
        }
    }

    detailTarget?.let { target ->
        exercises.firstOrNull { it.id == target.exerciseId }?.let { exercise ->
            val alternatives = remember(exercise, exercises, unavailableEquipment) {
                ExerciseSubstitutionEngine.suggest(
                    original = exercise,
                    catalog = exercises,
                    options = ExerciseSubstitutionOptions(
                        limit = 5,
                        unavailableEquipment = unavailableEquipment,
                    ),
                ).filter { it.exercise.trackingUnit.equals(exercise.trackingUnit, ignoreCase = true) }
            }
            val hasPendingSets = sets.any {
                it.workoutExerciseId == target.workoutExerciseId && !it.completed
            }
            ExerciseDetailSheet(
                exercise = exercise,
                alternatives = alternatives,
                onDismiss = { detailTarget = null },
                onChooseAlternative = if (hasPendingSets) ({ replacement ->
                    vm.substituteSessionExercise(
                        sessionId = sessionId,
                        workoutExerciseId = target.workoutExerciseId,
                        replacementExerciseId = replacement.id,
                    )
                    detailTarget = null
                }) else null,
            )
        }
    }

    substitutionTarget?.let { target ->
        val original = exercises.firstOrNull { it.id == target.exerciseId }
        val plannedExerciseId = sessionWorkoutItems.firstOrNull { it.id == target.workoutExerciseId }?.exerciseId
        val plannedExercise = exercises.firstOrNull { it.id == plannedExerciseId }
        val canReturnToOriginal = plannedExercise != null && plannedExercise.id != target.exerciseId
        val alternatives = remember(original, exercises, unavailableEquipment) {
            original?.let { source ->
                ExerciseSubstitutionEngine.suggest(
                    original = source,
                    catalog = exercises,
                    options = ExerciseSubstitutionOptions(
                        limit = exercises.size.coerceAtMost(500),
                        unavailableEquipment = unavailableEquipment,
                    ),
                ).filter { suggestion ->
                    suggestion.exercise.trackingUnit.equals(source.trackingUnit, ignoreCase = true) &&
                        suggestion.exercise.id != plannedExerciseId
                }.take(6)
            }.orEmpty()
        }
        ModalBottomSheet(onDismissRequest = { substitutionTarget = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Substituir durante o treino",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Troque apenas as séries ainda não concluídas de ${target.exerciseName}. " +
                                "A ficha original não será alterada e a carga da variante começará em 0.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (unavailableEquipment.isNotEmpty()) {
                            Text(
                                "Sugestões sem os equipamentos marcados como ocupados:",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(unavailableEquipment.toList().sorted(), key = { "sheet-occupied-$it" }) { equipment ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { setEquipmentUnavailable(equipment, false) },
                                        label = { Text("$equipment • liberar") },
                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Marque o equipamento ocupado no cartão do exercício para filtrar as opções.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (canReturnToOriginal && plannedExercise != null) {
                            OutlinedButton(
                                onClick = {
                                    vm.substituteSessionExercise(
                                        sessionId = sessionId,
                                        workoutExerciseId = target.workoutExerciseId,
                                        replacementExerciseId = plannedExercise.id,
                                    )
                                    substitutionTarget = null
                                },
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Voltar para ${plannedExercise.name}")
                            }
                        }
                    }
                }
                if (alternatives.isEmpty()) {
                    item {
                        Text(
                            if (unavailableEquipment.isNotEmpty()) {
                                "Nenhuma variante compatível está disponível. Libere um equipamento acima ou tente novamente."
                            } else "Nenhuma variante compatível foi encontrada no catálogo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(alternatives, key = { "live-substitution-${it.exercise.id}" }) { suggestion ->
                        OutlinedCard(
                            onClick = {
                                vm.substituteSessionExercise(
                                    sessionId = sessionId,
                                    workoutExerciseId = target.workoutExerciseId,
                                    replacementExerciseId = suggestion.exercise.id,
                                )
                                substitutionTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(suggestion.exercise.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${suggestion.exercise.muscleGroup} • ${suggestion.exercise.equipment}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    suggestion.reasons
                                        .filter { it.points > 0 }
                                        .take(2)
                                        .joinToString(" • ") { it.label },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text(if (session?.isTestMode == true) "Encerrar modo teste?" else "Finalizar treino?") },
        text = {
            Text(
                if (session?.isTestMode == true) "Esta sessão e todas as alterações de séries, repetições e cargas serão descartadas. Nada aparecerá no Progresso ou no Histórico."
                else if (completed < sets.size) "Há ${sets.size - completed} séries não concluídas. O treino será salvo como parcial."
                else "Ótimo trabalho. Suas cargas e séries ficarão salvas no histórico."
            )
        },
        confirmButton = { Button(onClick = { confirmFinish = false; vm.finishSession(sessionId, onFinished) }) { Text(if (session?.isTestMode == true) "Descartar teste" else "Finalizar") } },
        dismissButton = { OutlinedButton(onClick = { confirmFinish = false }) { Text("Continuar treino") } }
    )
}

@Composable
private fun SessionSetRow(
    set: SessionSetEntity,
    supportsPlateCalculator: Boolean,
    onChange: (Int, Double, Boolean, Int?, Int) -> Unit,
) {
    // O rascunho visível não deve voltar a um valor antigo enquanto o Room confirma teclas anteriores.
    var repsText by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(set.reps.toString()) }
    var loadText by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(set.loadKg.toClean()) }
    var rir by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(set.rir) }
    var painLevel by rememberSaveable(set.id, set.exerciseId) { mutableIntStateOf(set.painLevel) }
    var showEffort by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(false) }
    var showPlateCalculator by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = if (set.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${set.setNumber}", Modifier.size(28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    repsText,
                    onValueChange = { value -> repsText = value.filter(Char::isDigit).take(3); onChange(repsText.toIntOrNull() ?: 0, loadText.replace(',', '.').toDoubleOrNull() ?: 0.0, false, rir, painLevel) },
                    label = { Text(if (set.trackingMode == "Tempo") "Segundos" else if (set.trackingMode == "Distância") "Metros" else "Reps") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    loadText,
                    onValueChange = { value -> loadText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6); onChange(repsText.toIntOrNull() ?: 0, loadText.replace(',', '.').toDoubleOrNull() ?: 0.0, false, rir, painLevel) },
                    label = { Text("kg") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(checked = set.completed, onCheckedChange = { onChange(repsText.toIntOrNull() ?: 0, loadText.replace(',', '.').toDoubleOrNull() ?: 0.0, true, rir, painLevel) })
            }
            if (supportsPlateCalculator && set.isCoachRepetitionBased()) {
                val loadValue = loadText.replace(',', '.').toDoubleOrNull() ?: 0.0
                TextButton(onClick = { showPlateCalculator = true }, enabled = loadValue > 0.0) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null)
                    Text("Calcular anilhas", Modifier.padding(start = 7.dp))
                }
            }
            TextButton(onClick = { showEffort = !showEffort }) {
                Text(
                    if (rir == null && painLevel == 0) "Informar esforço (opcional)"
                    else "RIR ${rir?.toString() ?: "—"} • dor $painLevel/10"
                )
            }
            AnimatedVisibility(showEffort) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Repetições em reserva (RIR)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf<Int?>(null, 0, 1, 2, 3, 4, 5)) { value ->
                            FilterChip(
                                selected = rir == value,
                                onClick = {
                                    rir = value
                                    onChange(repsText.toIntOrNull() ?: 0, loadText.replace(',', '.').toDoubleOrNull() ?: 0.0, false, rir, painLevel)
                                },
                                label = { Text(if (value == null) "Não sei" else if (value == 5) "5+" else value.toString()) }
                            )
                        }
                    }
                    Text("Dor durante o movimento: $painLevel/10", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = painLevel.toFloat(),
                        onValueChange = { painLevel = it.toInt() },
                        onValueChangeFinished = {
                            onChange(repsText.toIntOrNull() ?: 0, loadText.replace(',', '.').toDoubleOrNull() ?: 0.0, false, rir, painLevel)
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                    )
                    Text(
                        "RIR é quantas repetições ainda caberiam com boa técnica. Dor não é a queimação muscular normal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showPlateCalculator) {
        PlateCalculatorSheet(
            initialTotalKg = loadText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            onDismiss = { showPlateCalculator = false },
        )
    }
}

@Composable
private fun ProgressionCoachCard(
    recommendation: ProgressionRecommendation?,
    hasPendingSet: Boolean,
) {
    val accent = when (recommendation?.status) {
        ProgressionStatus.INCREASE -> MaterialTheme.colorScheme.primary
        ProgressionStatus.REDUCE, ProgressionStatus.DELOAD, ProgressionStatus.CAUTION -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = accent)
                Text(if (hasPendingSet) "Próxima série" else "Próximo treino", fontWeight = FontWeight.Bold, color = accent)
            }
            if (recommendation == null) {
                Text("Finalize a primeira série e informe o RIR.")
            } else {
                Text(recommendation.title, fontWeight = FontWeight.SemiBold)
                Text(recommendation.message, style = MaterialTheme.typography.bodySmall)
                recommendation.suggestedLoadKg?.let { load ->
                    val reps = if (recommendation.suggestedRepMin != null && recommendation.suggestedRepMax != null) {
                        " • ${recommendation.suggestedRepMin}–${recommendation.suggestedRepMax} reps"
                    } else ""
                    Text(
                        "${if (hasPendingSet) "Próxima série" else "Próximo treino"}: ${load.toClean()} kg$reps",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                recommendation.reasons.take(4).forEach { reason ->
                    Text(
                        "• $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(PROGRESSION_COACH_DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun buildCoachRecommendation(
    currentSessionId: String,
    exerciseSets: List<SessionSetEntity>,
    allSets: List<SessionSetEntity>,
    sessions: List<SessionEntity>,
    workoutItem: WorkoutExerciseEntity?,
    exercise: ExerciseEntity?,
): ProgressionRecommendation? {
    val exerciseId = exerciseSets.firstOrNull()?.exerciseId ?: return null
    val sessionTimes = sessions.filter { it.finishedAt != null && !it.isTestMode }.associate { it.id to it.startedAt }

    fun performance(values: List<SessionSetEntity>): HistoricalExercisePerformance? {
        val comparable = values.filter { it.completed && it.isCoachRepetitionBased() }
            .sortedBy { it.setNumber }
        if (comparable.isEmpty()) return null
        val latest = comparable.last()
        return HistoricalExercisePerformance(
            actualReps = latest.reps,
            actualLoadKg = latest.loadKg,
            rir = latest.rir,
            painLevel = comparable.maxOf { it.painLevel },
        )
    }

    val repetitionSets = exerciseSets.filter { it.isCoachRepetitionBased() }
    // O coach orienta as séries seguintes assim que a primeira for registrada. Uma série com RIR
    // ou dor preenchidos também conta imediatamente, sem misturar séries futuras ainda intocadas.
    val assessedSets = repetitionSets.filter { set ->
        set.completed || set.rir != null || set.painLevel > 0
    }
    if (assessedSets.isEmpty()) return null

    val currentSets = assessedSets
        .sortedBy { it.setNumber }
        .map { set ->
            CurrentExerciseSetPerformance(
                setNumber = set.setNumber,
                reps = set.reps,
                loadKg = set.loadKg,
                rir = set.rir,
                painLevel = set.painLevel,
            )
        }
    // Durante uma sessão ativa, histórico nunca deve aparecer como se fosse o desempenho atual.
    val previousCandidates = allSets.asSequence()
        .filter { it.exerciseId == exerciseId && it.sessionId != currentSessionId && it.sessionId in sessionTimes }
        .toList()
    val sameWorkoutItemHistory = previousCandidates.filter {
        it.workoutExerciseId == exerciseSets.first().workoutExerciseId
    }
    val previous = (sameWorkoutItemHistory.ifEmpty { previousCandidates })
        .groupBy { "${it.sessionId}:${it.workoutExerciseId}" }
        .entries
        .sortedByDescending { entry -> sessionTimes[entry.value.first().sessionId] ?: 0L }
        .mapNotNull { performance(it.value) }
    val repMin = exerciseSets.mapNotNull { it.plannedReps }.minOrNull() ?: workoutItem?.repMin ?: 1
    val repMax = workoutItem?.repMax ?: repMin
    val latestCurrentSet = currentSets.last()
    val actualReps = latestCurrentSet.reps
    val actualLoad = latestCurrentSet.loadKg
    val substitutedDuringSession = workoutItem != null && workoutItem.exerciseId != exerciseId
    val plannedLoad = if (substitutedDuringSession) {
        exerciseSets.first().plannedLoadKg ?: actualLoad
    } else {
        exerciseSets.first().plannedLoadKg ?: workoutItem?.targetLoadKg ?: actualLoad
    }
    return ProgressionCoach().recommend(
        ProgressionCoachInput(
            exerciseName = exerciseSets.first().exerciseName,
            category = exercise?.category?.ifBlank { "Musculação" } ?: "Musculação",
            plannedRepMin = repMin.coerceAtLeast(1),
            plannedRepMax = repMax.coerceAtLeast(repMin),
            plannedLoadKg = plannedLoad,
            actualReps = actualReps,
            actualLoadKg = actualLoad,
            rir = latestCurrentSet.rir,
            painLevel = currentSets.maxOf { it.painLevel },
            recentPerformances = previous,
            currentSets = currentSets,
        )
    )
}

private fun SessionSetEntity.isCoachRepetitionBased(): Boolean =
    !trackingMode.contains("tempo", ignoreCase = true) &&
        !trackingMode.contains("dist", ignoreCase = true)

private fun Double.toClean(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)
