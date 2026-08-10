package com.liftly.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.data.SessionEntity
import com.liftly.app.domain.WorkoutCalorieEstimator
import com.liftly.app.domain.TrainingMomentumCalculator
import com.liftly.app.domain.GamificationSet
import com.liftly.app.domain.GamificationWorkout
import com.liftly.app.domain.MonthlyTrainingChallenge
import com.liftly.app.domain.TrainingGamificationEngine
import com.liftly.app.domain.TrainingMilestone
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.NeonIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val sets by vm.sessionSets.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    var selectedExerciseId by remember { mutableStateOf<String?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showHistoryManager by remember { mutableStateOf(false) }
    var expandedManagerDate by remember { mutableStateOf<LocalDate?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }
    var dateToDelete by remember { mutableStateOf<LocalDate?>(null) }
    val finishedSessionIds = sessions.filter { it.finishedAt != null }.mapTo(mutableSetOf()) { it.id }
    val finishedSessions = remember(sessions) {
        sessions.filter { it.finishedAt != null && !it.isTestMode }
    }
    val finishedDates = remember(sessions) {
        finishedSessions.asSequence()
            .map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .distinct()
            .sortedDescending()
            .toList()
    }
    val completedSets = sets.filter { it.completed && it.sessionId in finishedSessionIds }
    val sessionsWithCompletedWork = completedSets.mapTo(mutableSetOf()) { it.sessionId }
    val validFinishedSessions = finishedSessions.filter { it.id in sessionsWithCompletedWork }
    val historicalExerciseOptions = completedSets
        .groupBy { it.exerciseId }
        .map { (id, values) ->
            ExerciseProgressFilter(
                id = id,
                name = values.maxByOrNull { it.completedAt ?: 0L }?.exerciseName
                    ?: "Exercício"
            )
        }
    val exerciseOptions = (exercises.map { ExerciseProgressFilter(it.id, it.name) } + historicalExerciseOptions)
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
    LaunchedEffect(exerciseOptions.map { it.id }) {
        if (selectedExerciseId != null && exerciseOptions.none { it.id == selectedExerciseId }) selectedExerciseId = null
    }
    LaunchedEffect(finishedDates) {
        if (expandedManagerDate != null && expandedManagerDate !in finishedDates) {
            expandedManagerDate = null
        }
        if (finishedDates.isEmpty()) showHistoryManager = false
    }
    val relevant = selectedExerciseId?.let { id -> completedSets.filter { it.exerciseId == id } } ?: completedSets
    val sessionMap = sessions.associateBy { it.id }
    val grouped = relevant.groupBy { set ->
        sessionMap[set.sessionId]?.startedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
    }.toSortedMap()
    val loads = grouped.map { (date, daySets) -> date to (daySets.maxOfOrNull { it.loadKg } ?: 0.0) }
    val volumes = grouped.map { (date, daySets) -> date to daySets.sumOf { it.loadKg * it.reps } }
    val best = relevant.maxOfOrNull { it.loadKg } ?: 0.0
    val totalVolume = relevant.sumOf { it.loadKg * it.reps }
    val thirtyDaysAgo = LocalDate.now().minusDays(29)
    val frequency = sessions.count { session ->
        session.finishedAt != null && Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() >= thirtyDaysAgo
    }
    val momentum = remember(validFinishedSessions, preferences.weeklyWorkoutGoal) {
        TrainingMomentumCalculator.calculate(
            validFinishedSessions.map { it.startedAt },
            preferences.weeklyWorkoutGoal,
        )
    }
    val gamification = remember(validFinishedSessions, completedSets, preferences.weeklyWorkoutGoal) {
        TrainingGamificationEngine.calculate(
            workouts = validFinishedSessions.map { GamificationWorkout(it.id, it.startedAt) },
            sets = completedSets.map {
                GamificationSet(
                    sessionId = it.sessionId,
                    exerciseId = it.exerciseId,
                    loadKg = it.loadKg,
                    reps = it.reps,
                    rir = it.rir,
                )
            },
            weeklyGoal = preferences.weeklyWorkoutGoal,
        )
    }
    val optionNames = exerciseOptions.associate { it.id to it.name }
    val personalRanking = remember(completedSets, optionNames) {
        completedSets
            .filter { it.loadKg > 0.0 && it.reps > 0 && it.isRepetitionTracked() }
            .groupBy { it.exerciseId }
            .map { (id, values) ->
                val bestSet = values.maxByOrNull { it.estimatedOneRepMax() } ?: values.first()
                PersonalRecordRank(
                    name = optionNames[id] ?: bestSet.exerciseName,
                    maxLoad = values.maxOf { it.loadKg },
                    bestSetLoad = bestSet.loadKg,
                    estimatedOneRepMax = bestSet.estimatedOneRepMax(),
                    bestReps = bestSet.reps,
                )
            }
            .sortedByDescending { it.estimatedOneRepMax }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "LIFTLY / PROGRESSO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "Seu ritmo",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                        )
                        OutlinedButton(onClick = { showHistoryManager = true }, enabled = finishedDates.isNotEmpty()) { Text("Gerenciar") }
                    }
                    Text(
                        "${momentum.currentWeekWorkouts}/${momentum.weeklyGoal} treinos nesta semana  /  ${momentum.completedWeekStreak} sem. em sequência",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Melhor carga", "${best.clean()} kg", Icons.Default.EmojiEvents, Modifier.weight(1f))
                    MetricCard("Últimos 30 dias", frequency.toString(), Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                }
            }
            item { MetricCard("Volume total", "${totalVolume.clean()} kg", Icons.Default.Insights, Modifier.fillMaxWidth()) }
            item {
                GlassCard(Modifier.fillMaxWidth(), elevation = 7.dp) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            NeonIcon(Icons.Default.Flag, null, selected = true, size = 30.dp)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("Meta semanal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (momentum.goalReached) "Meta semanal alcançada" else "Faltam ${momentum.remainingThisWeek} treino(s) nesta semana",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text("${momentum.currentWeekWorkouts}/${momentum.weeklyGoal}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { momentum.progress },
                            modifier = Modifier.fillMaxWidth().height(9.dp),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Meta semanal", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(
                                onClick = { vm.setWeeklyWorkoutGoal(preferences.weeklyWorkoutGoal - 1) },
                                enabled = preferences.weeklyWorkoutGoal > 1,
                            ) { Icon(Icons.Default.Remove, "Diminuir meta") }
                            Text("${preferences.weeklyWorkoutGoal} treinos", fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { vm.setWeeklyWorkoutGoal(preferences.weeklyWorkoutGoal + 1) },
                                enabled = preferences.weeklyWorkoutGoal < 14,
                            ) { Icon(Icons.Default.Add, "Aumentar meta") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricMini("Sequência", "${momentum.completedWeekStreak} sem.", Modifier.weight(1f))
                            MetricMini("Melhor sequência", "${momentum.longestCompletedWeekStreak} sem.", Modifier.weight(1f))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("Avisos de meta", fontWeight = FontWeight.SemiBold)
                                Text("Avisar ao atingir a meta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(preferences.goalNotifications, vm::setGoalNotifications)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("Avisos de sequência", fontWeight = FontWeight.SemiBold)
                                Text("Avisar sobre semanas consecutivas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(preferences.streakNotifications, vm::setStreakNotifications)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Nível de consistência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    gamification.consistency.label,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text("${gamification.consistency.score}/100", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { gamification.consistency.score / 100f },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                        )
                        Text(
                            gamification.consistency.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Desafios deste mês", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    gamification.monthlyChallenges.forEach { challenge ->
                        MonthlyChallengeCard(challenge)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Marcos pessoais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Conquistas discretas baseadas apenas no seu próprio histórico.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(gamification.milestones, key = { it.id }) { milestone ->
                            MilestoneCard(milestone)
                        }
                    }
                }
            }
            item {
                Text("Filtrar por exercício", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = selectedExerciseId == null, onClick = { selectedExerciseId = null }, label = { Text("Todos") })
                    }
                    items(exerciseOptions, key = { it.id }) { exercise ->
                        FilterChip(
                            selected = selectedExerciseId == exercise.id,
                            onClick = { selectedExerciseId = exercise.id },
                            label = { Text(exercise.name, maxLines = 1) }
                        )
                    }
                }
            }
            item {
                ChartCard(
                    title = "Evolução de carga",
                    subtitle = if (loads.isEmpty()) "Conclua séries com carga para começar o gráfico." else "Maior carga por dia",
                    points = loads.map { it.second },
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ChartCard(
                    title = "Volume total",
                    subtitle = "Carga × repetições",
                    points = volumes.map { it.second },
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Recordes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Estimativa de 1RM pela fórmula de Epley.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (personalRanking.isEmpty()) Text("Seus recordes aparecerão aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        personalRanking.take(10).forEachIndexed { index, record ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (index < 3) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = when (index) {
                                        0 -> Color(0xFFFFC857)
                                        1 -> Color(0xFFC8D1DB)
                                        else -> Color(0xFFCD8C5C)
                                    })
                                } else {
                                    Text("${index + 1}º", Modifier.size(24.dp), fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(record.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Série do e1RM: ${record.bestSetLoad.clean()} kg × ${record.bestReps} • carga máx. ${record.maxLoad.clean()} kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("e1RM\n${record.estimatedOneRepMax.clean()} kg", textAlign = androidx.compose.ui.text.style.TextAlign.End, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    if (showHistoryManager) {
        AlertDialog(
            onDismissRequest = {
                showHistoryManager = false
                expandedManagerDate = null
            },
            title = { Text("Gerenciar progresso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Expanda um dia para excluir um treino específico ou, se preferir, todo o progresso daquela data.")
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(finishedDates, key = { it.toString() }) { date ->
                            val daySessions = finishedSessions.filter {
                                Instant.ofEpochMilli(it.startedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate() == date
                            }.sortedByDescending { it.startedAt }
                            val isExpanded = expandedManagerDate == date
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            expandedManagerDate = if (isExpanded) null else date
                                        }.padding(12.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                date.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                if (daySessions.size == 1) "1 treino concluído" else "${daySessions.size} treinos concluídos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Recolher dia" else "Ver treinos do dia",
                                        )
                                    }

                                    if (isExpanded) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        daySessions.forEach { session ->
                                            val completedSetCount = sets.count {
                                                it.sessionId == session.id && it.completed
                                            }
                                            val calorieEstimate = WorkoutCalorieEstimator.estimate(
                                                session = session,
                                                sets = sets,
                                                exercises = exercises,
                                                bodyWeightKg = profile?.currentWeightKg,
                                            )
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        session.workoutName.ifBlank { "Treino sem nome" },
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        sessionManagerDetails(
                                                            session = session,
                                                            completedSetCount = completedSetCount,
                                                            estimatedCalories = calorieEstimate?.kilocalories,
                                                            hasBodyWeight = profile?.currentWeightKg != null,
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showHistoryManager = false
                                                        sessionToDelete = session
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Excluir somente ${session.workoutName}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                showHistoryManager = false
                                                dateToDelete = date
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                            Text("Excluir o dia inteiro", Modifier.padding(start = 6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showHistoryManager = false
                    expandedManagerDate = null
                    showResetConfirmation = true
                }) { Text("Resetar tudo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHistoryManager = false
                    expandedManagerDate = null
                }) { Text("Fechar") }
            }
        )
    }

    sessionToDelete?.let { session ->
        val startedAt = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = {
                sessionToDelete = null
                showHistoryManager = true
            },
            title = { Text("Excluir somente este treino?") },
            text = {
                Text(
                    "${session.workoutName.ifBlank { "Treino sem nome" }}, iniciado em " +
                        startedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")) +
                        ", será removido com suas séries, cargas e repetições. Os outros treinos do dia serão preservados."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.deleteHistoricalSession(session.id)
                    selectedExerciseId = null
                    sessionToDelete = null
                    showHistoryManager = true
                }) { Text("Excluir treino") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sessionToDelete = null
                    showHistoryManager = true
                }) { Text("Cancelar") }
            },
        )
    }

    dateToDelete?.let { date ->
        val count = finishedSessions.count {
                Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
        AlertDialog(
            onDismissRequest = {
                dateToDelete = null
                showHistoryManager = true
            },
            title = { Text("Excluir progresso deste dia?") },
            text = {
                Text(
                    "${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}: ${if (count == 1) "1 treino" else "$count treinos"}, séries, cargas e recordes relacionados serão removidos. Os outros dias serão preservados."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.deleteHistoryForDate(date)
                    selectedExerciseId = null
                    dateToDelete = null
                    expandedManagerDate = null
                    showHistoryManager = true
                }) { Text("Excluir dia") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dateToDelete = null
                    showHistoryManager = true
                }) { Text("Cancelar") }
            }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Resetar todo o progresso?") },
            text = {
                Text("Sessões, histórico de treinos, séries concluídas, cargas, volume e recordes serão apagados. Seus treinos, exercícios, perfil e histórico de peso serão mantidos. Esta ação não pode ser desfeita.")
            },
            confirmButton = {
                Button(onClick = {
                    vm.resetProgress()
                    selectedExerciseId = null
                    showResetConfirmation = false
                }) { Text("Resetar progresso") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancelar") } }
        )
    }
}

private data class ExerciseProgressFilter(val id: String, val name: String)
private data class PersonalRecordRank(
    val name: String,
    val maxLoad: Double,
    val bestSetLoad: Double,
    val estimatedOneRepMax: Double,
    val bestReps: Int,
)

private fun com.liftly.app.data.SessionSetEntity.estimatedOneRepMax(): Double {
    val safeReps = reps.coerceIn(1, 30)
    return loadKg * (1.0 + safeReps / 30.0)
}

private fun com.liftly.app.data.SessionSetEntity.isRepetitionTracked(): Boolean =
    !trackingMode.contains("tempo", ignoreCase = true) &&
        !trackingMode.contains("dist", ignoreCase = true)

private fun sessionManagerDetails(
    session: SessionEntity,
    completedSetCount: Int,
    estimatedCalories: Int?,
    hasBodyWeight: Boolean,
): String {
    val startTime = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val durationMillis = ((session.finishedAt ?: session.startedAt) - session.startedAt)
        .coerceAtLeast(0L)
    val totalMinutes = durationMillis / 60_000L
    val duration = when {
        totalMinutes < 1L -> "menos de 1 min"
        totalMinutes < 60L -> "$totalMinutes min"
        else -> "${totalMinutes / 60L}h ${totalMinutes % 60L}min"
    }
    val setLabel = if (completedSetCount == 1) "1 série" else "$completedSetCount séries"
    val calorieLabel = when {
        estimatedCalories != null -> "≈$estimatedCalories kcal estimadas"
        !hasBodyWeight -> "registre o peso para estimar calorias"
        else -> "calorias indisponíveis"
    }
    return "$startTime • $setLabel • $duration\n$calorieLabel"
}

@Composable
private fun MonthlyChallengeCard(challenge: MonthlyTrainingChallenge) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (challenge.completed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(challenge.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    if (challenge.completed) "Concluído" else "${challenge.progress.coerceAtMost(challenge.target)}/${challenge.target}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { challenge.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Text(challenge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MilestoneCard(milestone: TrainingMilestone) {
    Card(
        modifier = Modifier.size(width = 210.dp, height = 116.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.unlocked) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = if (milestone.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    if (milestone.unlocked) "Alcançado" else "Em andamento",
                    Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(milestone.title, fontWeight = FontWeight.Bold)
            Text(
                milestone.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(title.uppercase(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Box(Modifier.fillMaxWidth(0.36f).height(3.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
        }
    }
}

@Composable
private fun MetricMini(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, points: List<Double>, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                if (points.size > 1) {
                    Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                        val min = points.minOrNull() ?: 0.0
                        val max = points.maxOrNull() ?: 1.0
                        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                        val path = Path()
                        points.forEachIndexed { index, value ->
                            val x = size.width * index / (points.size - 1)
                            val y = size.height - (size.height * ((value - min) / range).toFloat())
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color, 4.dp.toPx(), Offset(x, y))
                        }
                        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                } else {
                    Text(
                        if (points.isEmpty()) "Sem dados ainda" else "Mais um registro formará a linha",
                        Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.1f", this)
