package com.liftly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liftly.app.data.BodyWeightEntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightHistorySheet(
    entries: List<BodyWeightEntryEntity>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onUpdate: (BodyWeightEntryEntity, Double, String) -> Unit,
    onDelete: (BodyWeightEntryEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<BodyWeightEntryEntity?>(null) }
    var deleting by remember { mutableStateOf<BodyWeightEntryEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pesos salvos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Edite ou remova um registro sem apagar o restante do progresso.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAdd) { Text("Novo") }
            }
            if (entries.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MonitorWeight, contentDescription = null)
                        Text("Nenhum peso registrado", Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries.sortedByDescending { it.measuredAt }, key = { it.id }) { entry ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${entry.weightKg.weightLabel()} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(entry.measuredAt.weightDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { editing = entry }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar peso de ${entry.measuredAt.weightDate()}")
                                }
                                IconButton(onClick = { deleting = entry }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Excluir peso de ${entry.measuredAt.weightDate()}", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        EditWeightDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { value, notes ->
                onUpdate(entry, value, notes)
                editing = null
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir peso salvo?") },
            text = { Text("O registro de ${entry.weightKg.weightLabel()} kg em ${entry.measuredAt.weightDate()} será removido.") },
            confirmButton = {
                Button(onClick = { onDelete(entry); deleting = null }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EditWeightDialog(
    entry: BodyWeightEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
) {
    var weightText by rememberSaveable(entry.id) { mutableStateOf(entry.weightKg.weightLabel()) }
    var notes by rememberSaveable(entry.id) { mutableStateOf(entry.notes) }
    val parsed = weightText.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.measuredAt.weightDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(6) },
                    label = { Text("Peso em kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(120) },
                    label = { Text("Observação") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onSave(it, notes.trim()) } },
                enabled = parsed != null && parsed in 20.0..500.0,
            ) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun Double.weightLabel(): String = if (this % 1.0 == 0.0) toInt().toString()
else String.format(Locale.forLanguageTag("pt-BR"), "%.1f", this)

private fun Long.weightDate(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
