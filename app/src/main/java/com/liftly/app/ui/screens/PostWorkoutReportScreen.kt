package com.liftly.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.domain.WorkoutReport
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.WorkoutReportShare
import com.liftly.app.ui.components.GradientActionButton
import java.util.Locale

@Composable
fun PostWorkoutReportScreen(
    vm: AppViewModel,
    onDone: () -> Unit,
) {
    val report by vm.lastReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        val value = report
        if (value == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Relatório indisponível", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Seu treino foi salvo. Volte para Hoje para continuar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone) { Text("Voltar para Hoje") }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("LIFTLY / RELATÓRIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(value.workoutName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text(value.coachHeadline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(value.coachDetail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { ReportMetrics(value) }
            if (value.personalRecords > 0) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null)
                            Column {
                                Text("${value.personalRecords} recorde${if (value.personalRecords == 1) " pessoal" else "s pessoais"}", fontWeight = FontWeight.Black)
                                Text("Comparação feita apenas com o seu histórico anterior.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Text("EXERCÍCIOS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            items(value.exercises, key = { it.exerciseId }) { exercise ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(exercise.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (exercise.personalRecord) Text("PR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                        Text("${exercise.completedSets} séries • melhor ${exercise.bestLoadKg.clean()} kg × ${exercise.bestReps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        exercise.loadDeltaKg?.takeIf { kotlin.math.abs(it) > 0.0001 }?.let {
                            Text("${if (it > 0) "+" else ""}${it.clean()} kg vs. melhor anterior", style = MaterialTheme.typography.bodySmall)
                        }
                        exercise.averageRir?.let { Text("RIR médio ${String.format(Locale.US, "%.1f", it)}", style = MaterialTheme.typography.bodySmall) }
                        if (exercise.maxPain > 0) Text("Dor máxima registrada ${exercise.maxPain}/10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (!value.isTestMode && (value.rewardXp > 0 || value.rewardCoins > 0)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progressão", fontWeight = FontWeight.Bold)
                        Text("+${value.rewardXp} XP • +${value.rewardCoins} Lift Coins", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                GradientActionButton(
                    onClick = { WorkoutReportShare.share(context, value) },
                    modifier = Modifier.fillMaxWidth(),
                    onClickLabel = "Compartilhar relatório",
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("Compartilhar card", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null)
                    Text("Concluir", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportMetrics(report: WorkoutReport) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(report.durationMinutes.toString(), "MIN")
            Metric("${report.completedSets}/${report.totalSets}", "SÉRIES")
            Metric(report.volumeKg.shortKg(), "VOLUME")
            Metric(report.personalRecords.toString(), "PRS")
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Double.shortKg(): String = if (this >= 1000.0) String.format(Locale.US, "%.1fk", this / 1000.0) else clean()
private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)
