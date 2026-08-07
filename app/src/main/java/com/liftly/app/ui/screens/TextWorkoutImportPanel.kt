package com.liftly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liftly.app.domain.GptWorkoutTextParser
import com.liftly.app.domain.ParsedSetType
import com.liftly.app.domain.ParsedWorkout
import com.liftly.app.domain.ParsedWorkoutExercise
import com.liftly.app.domain.WorkoutTextParseResult

private const val WORKOUT_TEXT_EXAMPLE = """Treino A — Peito e tríceps
Segunda e quinta
Supino reto — 4x8-10 — 60 kg — descanso 120s
Supino inclinado com halteres — 3x10-12 — 24 kg — descanso 90s
Tríceps na polia — 3x12-15 — 25 kg — descanso 60s"""

/**
 * Two-step text importer: parsing never writes data, and the normalized preview is the only value
 * forwarded to persistence. This keeps pasted AI output reviewable and preserves source order.
 */
@Composable
internal fun TextWorkoutImportPanel(
    onDismiss: () -> Unit,
    onImport: (List<ParsedWorkout>) -> Unit,
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    var showExample by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<WorkoutTextParseResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (preview == null) {
            Text("Inserir texto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Cole ou digite uma ficha de treino. Você poderá conferir tudo antes de salvar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                label = { Text("Texto do treino") },
                placeholder = { Text("Nome do treino, exercícios, séries, repetições, carga e descanso...") },
                minLines = 8,
                maxLines = 16,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = {
                    Text("Aceita texto comum; não precisa ser um arquivo nem seguir um código especial.")
                },
            )
            TextButton(onClick = { showExample = !showExample }) {
                Text(if (showExample) "Ocultar exemplo" else "Ver exemplo de texto")
            }
            if (showExample) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(
                        WORKOUT_TEXT_EXAMPLE,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    enabled = rawText.isNotBlank(),
                    onClick = {
                        runCatching { GptWorkoutTextParser.parse(rawText) }
                            .onSuccess { result ->
                                if (result.workouts.isEmpty()) {
                                    errorMessage = "Não encontrei um treino nesse texto. Confira o exemplo e tente novamente."
                                } else {
                                    preview = result.withVisibleDefaults()
                                    errorMessage = null
                                }
                            }
                            .onFailure {
                                errorMessage = it.message ?: "Não foi possível entender esse texto."
                            }
                    },
                ) { Text("Analisar texto") }
            }
        } else {
            val current = requireNotNull(preview)
            Text("Revise antes de salvar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${current.workouts.size} ${if (current.workouts.size == 1) "treino encontrado" else "treinos encontrados"} • ${current.sourceLineCount} linhas analisadas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Campos ausentes usam os padrões: 3 séries, 8–12 repetições, 0 kg, 60s e série normal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current.warnings.take(4).forEach { warning ->
                Text("• ${warning.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (current.ignoredLines.isNotEmpty()) {
                Text(
                    "${current.ignoredLines.size} ${if (current.ignoredLines.size == 1) "linha foi ignorada" else "linhas foram ignoradas"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            current.workouts.forEachIndexed { workoutIndex, workout ->
                ParsedWorkoutEditor(
                    index = workoutIndex,
                    workout = workout,
                    onChange = { changed ->
                        preview = current.copy(
                            workouts = current.workouts.mapIndexed { index, existing ->
                                if (index == workoutIndex) changed else existing
                            },
                        )
                        errorMessage = null
                    },
                )
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = {
                    preview = null
                    errorMessage = null
                }) { Text("Voltar ao texto") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    val issue = current.workouts.firstNotNullOfOrNull { workout ->
                        when {
                            workout.name.isBlank() -> "Dê um nome a todos os treinos."
                            workout.exercises.isEmpty() -> "Adicione ao menos um exercício em ${workout.name}."
                            workout.exercises.any { it.name.isBlank() } -> "Preencha o nome de todos os exercícios."
                            workout.exercises.any { !it.hasValidNumbers() } -> "Revise séries, repetições, carga e descanso."
                            else -> null
                        }
                    }
                    if (issue != null) errorMessage = issue else onImport(current.workouts)
                }) {
                    Text(if (current.workouts.size == 1) "Salvar treino" else "Salvar ${current.workouts.size} treinos")
                }
            }
        }
    }
}

@Composable
private fun ParsedWorkoutEditor(
    index: Int,
    workout: ParsedWorkout,
    onChange: (ParsedWorkout) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Treino ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = workout.name,
                onValueChange = { onChange(workout.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do treino") },
                singleLine = true,
            )
            OutlinedTextField(
                value = workout.description,
                onValueChange = { onChange(workout.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descrição (opcional)") },
                minLines = 2,
                maxLines = 4,
            )
            if (workout.weekDays.isNotEmpty()) {
                Text(
                    "Dias: ${workout.weekDays.joinToString { it.displayNamePtBr() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                ParsedExerciseEditor(
                    number = exerciseIndex + 1,
                    exercise = exercise,
                    onChange = { changed ->
                        onChange(
                            workout.copy(
                                exercises = workout.exercises.mapIndexed { position, existing ->
                                    if (position == exerciseIndex) changed else existing
                                },
                            )
                        )
                    },
                    onRemove = {
                        onChange(workout.copy(exercises = workout.exercises.filterIndexed { position, _ -> position != exerciseIndex }))
                    },
                )
            }
        }
    }
}

@Composable
private fun ParsedExerciseEditor(
    number: Int,
    exercise: ParsedWorkoutExercise,
    onChange: (ParsedWorkoutExercise) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                number.toString(),
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = exercise.name,
                onValueChange = { onChange(exercise.copy(name = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Exercício") },
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remover exercício")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField(
                value = exercise.sets?.toString().orEmpty(),
                label = "Séries",
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(sets = it.toIntOrNull())) },
            )
            CompactNumberField(
                value = exercise.repMin?.toString().orEmpty(),
                label = "Rep. mín.",
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(repMin = it.toIntOrNull())) },
            )
            CompactNumberField(
                value = exercise.repMax?.toString().orEmpty(),
                label = "Rep. máx.",
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(repMax = it.toIntOrNull())) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField(
                value = exercise.loadKg?.toCleanNumber().orEmpty(),
                label = "Carga (kg)",
                decimal = true,
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(loadKg = it.replace(',', '.').toDoubleOrNull())) },
            )
            CompactNumberField(
                value = exercise.restSeconds?.toString().orEmpty(),
                label = "Descanso (s)",
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(restSeconds = it.toIntOrNull())) },
            )
            CompactNumberField(
                value = exercise.rir?.toString().orEmpty(),
                label = "RIR",
                modifier = Modifier.weight(1f),
                onValueChange = { onChange(exercise.copy(rir = it.toIntOrNull())) },
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            val allowed = entered.filter { character ->
                character.isDigit() || (decimal && (character == ',' || character == '.'))
            }
            onValueChange(allowed)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

private fun WorkoutTextParseResult.withVisibleDefaults(): WorkoutTextParseResult = copy(
    workouts = workouts.map { workout ->
        workout.copy(
            exercises = workout.exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets ?: 3,
                    repMin = exercise.repMin ?: 8,
                    repMax = exercise.repMax ?: 12,
                    loadKg = exercise.loadKg ?: 0.0,
                    restSeconds = exercise.restSeconds ?: 60,
                    setType = exercise.setType ?: ParsedSetType.NORMAL,
                )
            },
        )
    },
)

private fun ParsedWorkoutExercise.hasValidNumbers(): Boolean =
    (sets ?: 0) in 1..20 &&
        (repMin ?: 0) in 1..999 &&
        (repMax ?: 0) in (repMin ?: 0)..999 &&
        (loadKg ?: -1.0) in 0.0..9999.0 &&
        (restSeconds ?: -1) in 0..900 &&
        (rir == null || rir in 0..10)

private fun Double.toCleanNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun java.time.DayOfWeek.displayNamePtBr(): String = when (this) {
    java.time.DayOfWeek.MONDAY -> "seg"
    java.time.DayOfWeek.TUESDAY -> "ter"
    java.time.DayOfWeek.WEDNESDAY -> "qua"
    java.time.DayOfWeek.THURSDAY -> "qui"
    java.time.DayOfWeek.FRIDAY -> "sex"
    java.time.DayOfWeek.SATURDAY -> "sáb"
    java.time.DayOfWeek.SUNDAY -> "dom"
}
