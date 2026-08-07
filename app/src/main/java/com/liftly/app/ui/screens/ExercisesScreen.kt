package com.liftly.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.domain.ExerciseSubstitutionEngine
import com.liftly.app.domain.ExerciseSubstitutionOptions
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.components.ExerciseDetailSheet
import com.liftly.app.ui.components.ExerciseVisualThumbnail
import com.liftly.app.util.matchesSearchQuery
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(vm: AppViewModel) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sessionSets by vm.sessionSets.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var difficulty by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var recentOnly by rememberSaveable { mutableStateOf(false) }
    var filtersLoaded by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ExerciseEntity?>(null) }
    var selectedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    val available = exercises.filterNot { it.archived }
    val recentIds = remember(sessionSets) { sessionSets.sortedByDescending { it.completedAt ?: 0L }.map { it.exerciseId }.distinct().take(20).toSet() }
    val filtered = remember(available, query, muscle, equipment, difficulty, category, favoritesOnly, recentOnly, recentIds) {
        available.filter { exercise ->
            matchesSearchQuery(
                query,
                exercise.name,
                exercise.muscleGroup,
                exercise.secondaryMuscles,
                exercise.equipment,
                exercise.movementType,
                exercise.category,
            ) &&
                (muscle == null || exercise.muscleGroup == muscle) &&
                (equipment == null || exercise.equipment == equipment) &&
                (difficulty == null || exercise.difficulty == difficulty) &&
                (category == null || exercise.category == category) &&
                (!favoritesOnly || exercise.isFavorite) &&
                (!recentOnly || exercise.id in recentIds)
        }.sortedWith(compareByDescending<ExerciseEntity> { it.isFavorite }.thenBy { it.name })
    }
    val hasActiveFilters = query.isNotBlank() || muscle != null || equipment != null ||
        difficulty != null || category != null || favoritesOnly || recentOnly
    val clearFilters = {
        query = ""
        muscle = null
        equipment = null
        difficulty = null
        category = null
        favoritesOnly = false
        recentOnly = false
    }

    LaunchedEffect(preferences.exerciseFilters) {
        if (!filtersLoaded) {
            val parts = preferences.exerciseFilters.split('|')
            if (parts.size == 6) {
                muscle = parts[0].ifBlank { null }
                equipment = parts[1].ifBlank { null }
                difficulty = parts[2].ifBlank { null }
                category = parts[3].ifBlank { null }
                favoritesOnly = parts[4].toBooleanStrictOrNull() ?: false
                recentOnly = parts[5].toBooleanStrictOrNull() ?: false
            }
            filtersLoaded = true
        }
    }
    LaunchedEffect(muscle, equipment, difficulty, category, favoritesOnly, recentOnly, filtersLoaded) {
        if (filtersLoaded) vm.setExerciseFilters(listOf(muscle.orEmpty(), equipment.orEmpty(), difficulty.orEmpty(), category.orEmpty(), favoritesOnly, recentOnly).joinToString("|"))
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Column {
                        Text("Exercícios")
                        Text(
                            "${filtered.size} no catálogo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Criar exercício personalizado")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar no catálogo") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasActiveFilters) {
                        item {
                            AssistChip(
                                onClick = clearFilters,
                                label = { Text("Limpar") },
                                leadingIcon = { Icon(Icons.Outlined.Clear, contentDescription = null) },
                            )
                        }
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("Favoritos") },
                            leadingIcon = {
                                Icon(
                                    if (favoritesOnly) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    item {
                        FilterChip(
                            selected = recentOnly,
                            onClick = { recentOnly = !recentOnly },
                            label = { Text("Recentes") },
                        )
                    }
                    item {
                        FilterMenu("Músculo", muscle, available.map { it.muscleGroup }.distinct().sorted()) { muscle = it }
                    }
                    item {
                        FilterMenu("Equipamento", equipment, available.map { it.equipment }.distinct().sorted()) { equipment = it }
                    }
                    item {
                        FilterMenu("Dificuldade", difficulty, available.map { it.difficulty }.distinct().sorted()) { difficulty = it }
                    }
                    item {
                        FilterMenu("Tipo", category, available.map { it.category }.distinct().sorted()) { category = it }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyExercises(onClear = clearFilters)
                }
            } else {
                items(filtered, key = { it.id }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onFavorite = { vm.toggleFavorite(exercise) },
                        onOpenDetails = { selectedExerciseId = exercise.id },
                    )
                }
            }
        }
    }

    selectedExerciseId?.let { selectedId ->
        available.firstOrNull { it.id == selectedId }?.let { exercise ->
            val alternatives = remember(exercise, available) {
                ExerciseSubstitutionEngine.suggest(
                    original = exercise,
                    catalog = available,
                    options = ExerciseSubstitutionOptions(limit = 5),
                )
            }
            ExerciseDetailSheet(
                exercise = exercise,
                alternatives = alternatives,
                onDismiss = { selectedExerciseId = null },
                onChooseAlternative = { selectedExerciseId = it.id },
                onEdit = if (exercise.isCustom) ({
                    selectedExerciseId = null
                    editing = exercise
                }) else null,
                onDelete = if (exercise.isCustom) ({
                    selectedExerciseId = null
                    deleteCandidate = exercise
                }) else null,
            )
        }
    }

    if (creating || editing != null) {
        CustomExerciseDialog(
            exercise = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { vm.saveCustomExercise(it) },
        )
    }

    deleteCandidate?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Excluir exercício?") },
            text = { Text("“${exercise.name}” será removido se não estiver sendo usado em um treino. Caso esteja em uso, ele será arquivado para preservar seu histórico.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCustomExercise(exercise)
                    deleteCandidate = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilterMenu(label: String, selected: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected ?: label) },
            trailingIcon = { Icon(Icons.Outlined.ExpandMore, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntity,
    onFavorite: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    OutlinedCard(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseVisualThumbnail(
                    exercise = exercise,
                    modifier = Modifier.size(78.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${exercise.muscleGroup} • ${exercise.equipment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (exercise.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (exercise.isFavorite) "Remover ${exercise.name} dos favoritos" else "Adicionar ${exercise.name} aos favoritos",
                        tint = if (exercise.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenDetails) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Ver execução de ${exercise.name}",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(exercise.difficulty, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exercise.isCustom) Text("Personalizado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun EmptyExercises(onClear: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Nenhum exercício encontrado", style = MaterialTheme.typography.titleMedium)
            Text("Tente outros termos ou remova alguns filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClear) { Text("Limpar filtros") }
        }
    }
}

@Composable
private fun CustomExerciseDialog(
    exercise: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (ExerciseEntity) -> Unit,
) {
    var name by remember(exercise) { mutableStateOf(exercise?.name.orEmpty()) }
    var muscle by remember(exercise) { mutableStateOf(exercise?.muscleGroup.orEmpty()) }
    var secondary by remember(exercise) { mutableStateOf(exercise?.secondaryMuscles.orEmpty()) }
    var equipment by remember(exercise) { mutableStateOf(exercise?.equipment.orEmpty()) }
    var difficulty by remember(exercise) { mutableStateOf(exercise?.difficulty ?: "Iniciante") }
    var movement by remember(exercise) { mutableStateOf(exercise?.movementType.orEmpty()) }
    var category by remember(exercise) { mutableStateOf(exercise?.category ?: "Musculação") }
    var instructions by remember(exercise) { mutableStateOf(exercise?.instructions.orEmpty()) }
    var cautions by remember(exercise) { mutableStateOf(exercise?.cautions.orEmpty()) }
    var unit by remember(exercise) { mutableStateOf(exercise?.trackingUnit ?: "kg") }
    var favorite by remember(exercise) { mutableStateOf(exercise?.isFavorite ?: false) }
    var imageUri by remember(exercise) { mutableStateOf(exercise?.imageUri) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
        }
    }
    val valid = name.isNotBlank() && muscle.isNotBlank() && equipment.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise == null) "Novo exercício" else "Editar exercício") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { DialogField(name, { name = it }, "Nome *") }
                item { DialogField(muscle, { muscle = it }, "Grupo muscular *") }
                item { DialogField(secondary, { secondary = it }, "Músculos secundários") }
                item { DialogField(equipment, { equipment = it }, "Equipamento *") }
                item { DialogField(movement, { movement = it }, "Tipo de movimento") }
                item { ChoiceRow("Dificuldade", listOf("Iniciante", "Intermediário", "Avançado"), difficulty) { difficulty = it } }
                item { ChoiceRow("Categoria", listOf("Musculação", "Funcional", "Mobilidade", "Cardio", "Peso corporal"), category) { category = it } }
                item { DialogField(instructions, { instructions = it }, "Instruções", singleLine = false) }
                item { DialogField(cautions, { cautions = it }, "Cuidados e observações", singleLine = false) }
                item { ChoiceRow("Unidade", listOf("kg", "repetições", "tempo", "distância"), unit) { unit = it } }
                item {
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Text(if (imageUri == null) "Escolher imagem opcional" else "Trocar imagem selecionada")
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Marcar como favorito", Modifier.weight(1f))
                        Switch(checked = favorite, onCheckedChange = { favorite = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        ExerciseEntity(
                            id = exercise?.id ?: "custom.${UUID.randomUUID()}",
                            name = name.trim(),
                            muscleGroup = muscle.trim(),
                            secondaryMuscles = secondary.trim(),
                            equipment = equipment.trim(),
                            difficulty = difficulty,
                            movementType = movement.trim().ifBlank { "Personalizado" },
                            category = category,
                            instructions = instructions.trim(),
                            cautions = cautions.trim(),
                            trackingUnit = unit,
                            isCustom = true,
                            isFavorite = favorite,
                            imageUri = imageUri,
                            archived = false,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun ChoiceRow(label: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices) { choice ->
                AssistChip(onClick = { onSelect(choice) }, label = {
                    Text(if (choice == selected) "✓ $choice" else choice)
                })
            }
        }
    }
}
