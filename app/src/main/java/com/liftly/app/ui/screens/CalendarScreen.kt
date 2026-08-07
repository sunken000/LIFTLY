package com.liftly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.data.ScheduleEntity
import com.liftly.app.data.WorkoutEntity
import com.liftly.app.domain.EffectiveScheduleResolver
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.components.InteractiveGlassCard
import com.liftly.app.ui.components.NeonIcon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: AppViewModel, onBack: () -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    val workoutMap = workouts.associateBy { it.id }

    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            title = { Text("Calendário semanal", fontWeight = FontWeight.Bold) }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Icon(Icons.Default.ChevronLeft, "Semana anterior") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pt-BR")))} – ${weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pt-BR")))}", fontWeight = FontWeight.Bold)
                        if (!LocalDate.now().isBefore(weekStart) && !LocalDate.now().isAfter(weekStart.plusDays(6))) Text("Esta semana", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Icon(Icons.Default.ChevronRight, "Próxima semana") }
                }
            }
            items(7) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val entries = EffectiveScheduleResolver.forDate(date, workouts, schedule)
                val isToday = date == LocalDate.now()
                InteractiveGlassCard(
                    onClick = { editingDate = date },
                    modifier = Modifier.fillMaxWidth(),
                    onClickLabel = "Editar programação de ${date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR")))}",
                    elevation = if (isToday) 8.dp else 4.dp,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.size(48.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(date.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR"))).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                if (entries.isEmpty()) Text("Sem programação", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                entries.forEach { entry ->
                                    if (entry.isRestDay) Text("Descanso", fontWeight = FontWeight.SemiBold)
                                    else Text(workoutMap[entry.workoutId]?.name ?: "Treino", fontWeight = FontWeight.SemiBold)
                                    Text(entry.status, style = MaterialTheme.typography.labelSmall, color = statusColor(entry.status))
                                }
                            }
                            NeonIcon(Icons.Default.Add, "Programar", selected = true, intensity = 1.1f, size = 32.dp)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { confirmCopy = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("Copiar para a próxima semana", Modifier.padding(start = 8.dp))
                }
            }
            item { Spacer(Modifier.padding(bottom = 90.dp)) }
        }
    }

    editingDate?.let { date ->
        ScheduleDialog(
            date = date,
            workouts = workouts.filterNot { it.archived },
            entries = EffectiveScheduleResolver.forDate(date, workouts, schedule),
            vm = vm,
            onDismiss = { editingDate = null },
        )
    }
    if (confirmCopy) AlertDialog(
        onDismissRequest = { confirmCopy = false },
        title = { Text("Copiar programação?") },
        text = { Text("Os treinos e dias de descanso desta semana serão adicionados à semana seguinte.") },
        confirmButton = { Button(onClick = { vm.copyWeek(weekStart, weekStart.plusWeeks(1)); weekStart = weekStart.plusWeeks(1); confirmCopy = false }) { Text("Copiar") } },
        dismissButton = { OutlinedButton(onClick = { confirmCopy = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun ScheduleDialog(date: LocalDate, workouts: List<WorkoutEntity>, entries: List<ScheduleEntity>, vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))).replaceFirstChar { it.uppercase() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { entry ->
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (entry.isRestDay) "Descanso" else workouts.firstOrNull { it.id == entry.workoutId }?.name ?: "Treino")
                                if (EffectiveScheduleResolver.isRecurringPlaceholder(entry)) {
                                    Text(
                                        "Recorrente pela ficha do treino",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!EffectiveScheduleResolver.isRecurringPlaceholder(entry)) {
                                IconButton(onClick = { vm.removeSchedule(entry.id) }) { Icon(Icons.Default.Delete, "Remover") }
                            }
                        }
                        if (!entry.isRestDay) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Concluído" to "Feito", "Parcial" to "Parcial", "Não realizado" to "Não feito").forEach { (status, label) ->
                                    OutlinedButton(
                                        onClick = { vm.setScheduleStatus(entry, status) },
                                        enabled = entry.status != status,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
                if (workouts.isEmpty()) Text("Crie um treino em Meus treinos primeiro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                workouts.forEach { workout -> FilledTonalButton(onClick = { vm.scheduleWorkout(date, workout.id) }, modifier = Modifier.fillMaxWidth()) { Text("+ ${workout.name}") } }
                OutlinedButton(onClick = { vm.setRestDay(date) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Bedtime, null); Text("Definir descanso", Modifier.padding(start = 8.dp)) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Concluído") } }
    )
}

@Composable
private fun statusColor(status: String) = when (status) {
    "Concluído" -> MaterialTheme.colorScheme.primary
    "Parcial" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
