package com.liftly.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SharedWorkoutPackage
import com.liftly.app.data.WorkoutShareCodec
import com.liftly.app.data.WorkoutEntity
import com.liftly.app.data.WorkoutExerciseEntity
import com.liftly.app.domain.SuggestionSeverity
import com.liftly.app.domain.BarbellPlateCalculator
import com.liftly.app.domain.ExerciseSubstitutionEngine
import com.liftly.app.domain.ExerciseSubstitutionOptions
import com.liftly.app.domain.WORKOUT_ANALYSIS_DISCLAIMER
import com.liftly.app.domain.WorkoutAnalyzer
import com.liftly.app.domain.WorkoutSuggestion
import com.liftly.app.domain.WorkoutSuggestionAction
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.components.PlateCalculatorSheet
import com.liftly.app.util.matchesSearchQuery
import com.liftly.app.sharing.WorkoutPdfExporter
import com.liftly.app.sharing.WorkoutQrCodec
import com.liftly.app.sharing.WorkoutShareFiles
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WorkoutDestructiveAction { Archive, Delete }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    vm: AppViewModel,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val workoutExercises by vm.workoutExercises.collectAsStateWithLifecycle()
    val activeWorkouts = workouts.filterNot { it.archived }
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingWorkout by remember { mutableStateOf<WorkoutEntity?>(null) }
    var sharePayload by remember { mutableStateOf<String?>(null) }
    var shareFailure by remember { mutableStateOf<String?>(null) }
    var pendingFileExport by remember { mutableStateOf<WorkoutEntity?>(null) }
    var showImportOptions by remember { mutableStateOf(false) }
    var showTextImport by remember { mutableStateOf(false) }
    var workoutToEdit by remember { mutableStateOf<WorkoutEntity?>(null) }
    var creatingWorkout by remember { mutableStateOf(false) }
    var destructiveTarget by remember { mutableStateOf<Pair<WorkoutEntity, WorkoutDestructiveAction>?>(null) }
    var configuringItem by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var openSubstitutionsOnConfigure by remember { mutableStateOf(false) }
    var addingExercise by remember { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var suggestions by remember { mutableStateOf<List<WorkoutSuggestion>?>(null) }
    var ignoredFingerprints by remember { mutableStateOf<Set<String>>(emptySet()) }
    val analyzer = remember { WorkoutAnalyzer() }

    val workoutFileExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WorkoutShareCodec.MIME_TYPE)
    ) { uri ->
        val workout = pendingFileExport
        pendingFileExport = null
        if (uri != null && workout != null) {
            scope.launch {
                vm.createWorkoutExport(workout.id)
                    .onSuccess { payload ->
                        runCatching {
                            requireNotNull(context.contentResolver.openOutputStream(uri))
                                .bufferedWriter()
                                .use { it.write(payload) }
                        }.onSuccess {
                            vm.reportFeedback("Arquivo do treino exportado.")
                        }.onFailure {
                            vm.reportFeedback("Não foi possível salvar o arquivo do treino.", true)
                        }
                    }
                    .onFailure { vm.reportFeedback(it.message ?: "Não foi possível exportar o treino.", true) }
            }
        }
    }
    val workoutFileImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    requireNotNull(context.contentResolver.openInputStream(uri))
                        .bufferedReader()
                        .use { it.readText() }
                }.onSuccess { payload ->
                    vm.importWorkoutExport(payload) { importedId -> selectedWorkoutId = importedId }
                }.onFailure {
                    vm.reportFeedback("Não foi possível ler o arquivo selecionado.", true)
                }
            }
        }
    }
    val workoutQrImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val qrPayload = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            WorkoutQrCodec.decodePng(input)
                        }
                        WorkoutShareCodec.decodeFromQr(qrPayload)
                    }
                }.onSuccess { payload ->
                    vm.importWorkoutExport(payload) { importedId -> selectedWorkoutId = importedId }
                }.onFailure { error ->
                    vm.reportFeedback(error.message ?: "Não foi possível ler o QR selecionado.", true)
                }
            }
        }
    }

    LaunchedEffect(sharingWorkout?.id) {
        val workout = sharingWorkout
        sharePayload = null
        shareFailure = null
        if (workout != null) {
            vm.createWorkoutExport(workout.id)
                .onSuccess { sharePayload = it }
                .onFailure { shareFailure = it.message ?: "Não foi possível preparar o compartilhamento." }
        }
    }

    LaunchedEffect(activeWorkouts.map { it.id }) {
        if (selectedWorkoutId !in activeWorkouts.map { it.id }) {
            selectedWorkoutId = activeWorkouts.firstOrNull()?.id
        }
    }
    LaunchedEffect(selectedWorkoutId) {
        suggestions = null
        ignoredFingerprints = emptySet()
    }

    val selectedWorkout = activeWorkouts.firstOrNull { it.id == selectedWorkoutId }
    val selectedItems = selectedWorkout?.let { workout ->
        workoutExercises.filter { it.workoutId == workout.id }.sortedBy { it.orderIndex }
    }.orEmpty()
    val exerciseById = exercises.associateBy { it.id }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Treinos")
                        Text(
                            "${activeWorkouts.size} ${if (activeWorkouts.size == 1) "treino ativo" else "treinos ativos"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Abrir calendário semanal")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingWorkout = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Criar treino")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (activeWorkouts.isEmpty()) {
                item { EmptyWorkouts(onCreate = { creatingWorkout = true }, onCalendar = onOpenCalendar) }
                item {
                    OutlinedButton(onClick = { showImportOptions = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Importar treino compartilhado")
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(activeWorkouts, key = { it.id }) { workout ->
                            WorkoutSelectorCard(
                                workout = workout,
                                selected = workout.id == selectedWorkoutId,
                                exerciseCount = workoutExercises.count { it.workoutId == workout.id },
                                onClick = { selectedWorkoutId = workout.id },
                            )
                        }
                        item(key = "import-workout") {
                            ImportWorkoutCard(onClick = { showImportOptions = true })
                        }
                    }
                }

                selectedWorkout?.let { workout ->
                    item {
                        WorkoutHeader(
                            workout = workout,
                            itemCount = selectedItems.size,
                            onEdit = { workoutToEdit = workout },
                            onShare = { sharingWorkout = workout },
                            onDuplicate = { vm.duplicateWorkout(workout) },
                            onArchive = { destructiveTarget = workout to WorkoutDestructiveAction.Archive },
                            onDelete = { destructiveTarget = workout to WorkoutDestructiveAction.Delete },
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Sequência do treino", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("A ordem abaixo será usada ao iniciar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { addingExercise = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Adicionar")
                            }
                        }
                    }
                    if (selectedItems.isEmpty()) {
                        item {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null)
                                    Text("Este treino ainda está vazio", style = MaterialTheme.typography.titleMedium)
                                    Text("Adicione o primeiro exercício e configure séries, carga e descanso.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { addingExercise = true }) { Text("Adicionar exercício") }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(selectedItems, key = { _, item -> item.id }) { index, item ->
                            WorkoutExerciseCard(
                                number = index + 1,
                                item = item,
                                exercise = exerciseById[item.exerciseId],
                                canMoveUp = index > 0,
                                canMoveDown = index < selectedItems.lastIndex,
                                onMoveUp = { vm.moveWorkoutExercise(workout.id, item.id, -1) },
                                onMoveDown = { vm.moveWorkoutExercise(workout.id, item.id, 1) },
                                onEdit = {
                                    openSubstitutionsOnConfigure = false
                                    configuringItem = item
                                },
                                onSubstitute = {
                                    openSubstitutionsOnConfigure = true
                                    configuringItem = item
                                },
                                onRemove = { removeCandidate = item },
                            )
                        }
                    }
                    item {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                suggestions = (
                                    analyzer.analyze(workout, selectedItems, exercises) +
                                        analyzer.analyzeWeekly(activeWorkouts, workoutExercises, exercises)
                                    ).distinctBy { it.fingerprint }
                                ignoredFingerprints = emptySet()
                            },
                        ) {
                            Icon(Icons.Outlined.Analytics, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Analisar treino")
                        }
                    }
                }
            }
        }
    }

    if (showImportOptions) {
        ModalBottomSheet(onDismissRequest = { showImportOptions = false }) {
            ImportWorkoutPanel(
                onImportText = {
                    showImportOptions = false
                    showTextImport = true
                },
                onImportFile = {
                    showImportOptions = false
                    workoutFileImporter.launch(
                        arrayOf(
                            WorkoutShareCodec.MIME_TYPE,
                            "application/json",
                            "text/plain",
                            "application/octet-stream",
                        )
                    )
                },
                onImportQr = {
                    showImportOptions = false
                    workoutQrImporter.launch(arrayOf("image/png", "image/*"))
                },
            )
        }
    }

    if (showTextImport) {
        ModalBottomSheet(onDismissRequest = { showTextImport = false }) {
            TextWorkoutImportPanel(
                onDismiss = { showTextImport = false },
                onImport = { parsedWorkouts ->
                    vm.importWorkoutText(parsedWorkouts) { importedId ->
                        selectedWorkoutId = importedId
                    }
                    showTextImport = false
                },
            )
        }
    }

    sharingWorkout?.let { workout ->
        ModalBottomSheet(onDismissRequest = { sharingWorkout = null }) {
            WorkoutSharePanel(
                workout = workout,
                payload = sharePayload,
                failure = shareFailure,
                onSaveWorkoutFile = {
                    pendingFileExport = workout
                    workoutFileExporter.launch("${safeWorkoutFileName(workout.name)}.${WorkoutShareCodec.FILE_EXTENSION}")
                },
                onShareWorkoutFile = { payload ->
                    scope.launch {
                        runCatching { shareWorkoutPackage(context, workout, payload) }
                            .onFailure { vm.reportFeedback(it.message ?: "Não foi possível compartilhar o arquivo.", true) }
                    }
                },
                onShareQr = { payload ->
                    scope.launch {
                        runCatching { shareWorkoutQr(context, workout, payload) }
                            .onFailure { vm.reportFeedback(it.message ?: "Não foi possível compartilhar o QR.", true) }
                    }
                },
                onSharePdf = { payload ->
                    scope.launch {
                        runCatching { shareWorkoutPdf(context, workout, payload) }
                            .onFailure { vm.reportFeedback(it.message ?: "Não foi possível compartilhar o PDF.", true) }
                    }
                },
            )
        }
    }

    suggestions?.let { analysis ->
        val pendingSuggestions = analysis.filter { it.fingerprint !in ignoredFingerprints }
        ModalBottomSheet(onDismissRequest = { suggestions = null }) {
            AnalysisPanel(
                exerciseCount = selectedItems.size,
                totalSets = selectedItems.sumOf { it.sets },
                muscleGroupCount = selectedItems.mapNotNull { exerciseById[it.exerciseId]?.muscleGroup }.distinct().size,
                suggestions = pendingSuggestions,
                onIgnore = { ignoredFingerprints = ignoredFingerprints + it.fingerprint },
                onApply = { suggestion ->
                    when (val action = suggestion.action) {
                        is WorkoutSuggestionAction.SetRest -> {
                            selectedItems.firstOrNull { it.id == action.workoutExerciseId }?.let {
                                vm.updateWorkoutExercise(it.copy(restSeconds = action.seconds))
                            }
                            ignoredFingerprints = ignoredFingerprints + suggestion.fingerprint
                        }
                        is WorkoutSuggestionAction.MoveExercise -> {
                            selectedWorkout?.let {
                                vm.moveWorkoutExerciseBefore(it.id, action.workoutExerciseId, action.beforeWorkoutExerciseId)
                            }
                            ignoredFingerprints = ignoredFingerprints + suggestion.fingerprint
                        }
                        is WorkoutSuggestionAction.RemoveExercise -> {
                            workoutExercises.firstOrNull { it.id == action.workoutExerciseId }?.let { removeCandidate = it }
                            suggestions = null
                        }
                        is WorkoutSuggestionAction.ReviewVolume -> {
                            workoutExercises.firstOrNull { it.id in action.workoutExerciseIds }?.let {
                                openSubstitutionsOnConfigure = false
                                configuringItem = it
                            }
                            suggestions = null
                        }
                        is WorkoutSuggestionAction.ReplaceExercise -> {
                            workoutExercises.firstOrNull { it.id == action.workoutExerciseId }?.let {
                                openSubstitutionsOnConfigure = true
                                configuringItem = it
                            }
                            suggestions = null
                        }
                        null -> Unit
                    }
                },
                onClose = { suggestions = null },
            )
        }
    }

    if (creatingWorkout || workoutToEdit != null) {
        WorkoutDialog(
            workout = workoutToEdit,
            onDismiss = {
                creatingWorkout = false
                workoutToEdit = null
            },
            onSave = { name, description, days -> vm.saveWorkout(name, description, days, workoutToEdit) },
        )
    }

    destructiveTarget?.let { (workout, action) ->
        AlertDialog(
            onDismissRequest = { destructiveTarget = null },
            title = { Text(if (action == WorkoutDestructiveAction.Delete) "Excluir treino?" else "Arquivar treino?") },
            text = {
                Text(
                    if (action == WorkoutDestructiveAction.Delete) "“${workout.name}” e sua configuração serão excluídos. O histórico de sessões já concluídas será preservado."
                    else "“${workout.name}” sairá da lista principal, mas seu histórico será preservado.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == WorkoutDestructiveAction.Delete) vm.deleteWorkout(workout.id) else vm.archiveWorkout(workout.id)
                    destructiveTarget = null
                }) { Text(if (action == WorkoutDestructiveAction.Delete) "Excluir" else "Arquivar") }
            },
            dismissButton = { TextButton(onClick = { destructiveTarget = null }) { Text("Cancelar") } },
        )
    }

    if (addingExercise || configuringItem != null) {
        ConfigureExerciseDialog(
            item = configuringItem,
            exercises = exercises.filterNot { it.archived },
            showSubstitutionsInitially = openSubstitutionsOnConfigure,
            onDismiss = {
                addingExercise = false
                configuringItem = null
                openSubstitutionsOnConfigure = false
            },
            onSave = { exerciseId, sets, repMin, repMax, load, rest, type, notes, trackingMode, exerciseName ->
                val existing = configuringItem
                if (existing == null) {
                    selectedWorkout?.let { vm.addExercise(it.id, exerciseId, sets, repMin, repMax, load, rest, type, notes, exerciseName) }
                } else {
                    vm.updateWorkoutExercise(
                        existing.copy(
                            exerciseId = exerciseId,
                            sets = sets,
                            repMin = repMin,
                            repMax = repMax,
                            targetLoadKg = load,
                            restSeconds = rest,
                            setType = type,
                            notes = notes,
                            trackingMode = trackingMode,
                        ),
                        exerciseName,
                    )
                }
            },
        )
    }

    removeCandidate?.let { item ->
        val name = exerciseById[item.exerciseId]?.name ?: "este exercício"
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text("Remover exercício?") },
            text = { Text("$name será removido deste treino.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeWorkoutExercise(item.id)
                    removeCandidate = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun WorkoutSelectorCard(workout: WorkoutEntity, selected: Boolean, exerciseCount: Int, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(workout.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "$exerciseCount ${if (exerciseCount == 1) "exercício" else "exercícios"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportWorkoutCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Importar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text("Texto, arquivo ou QR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportWorkoutPanel(
    onImportText: () -> Unit,
    onImportFile: () -> Unit,
    onImportQr: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Importar treino", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "O treino será adicionado depois dos seus treinos atuais. Nada do seu histórico será substituído.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onImportText, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Inserir texto")
        }
        OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Abrir arquivo Liftly")
        }
        OutlinedButton(onClick = onImportQr, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ler QR de uma imagem")
        }
    }
}

@Composable
private fun WorkoutSharePanel(
    workout: WorkoutEntity,
    payload: String?,
    failure: String?,
    onSaveWorkoutFile: () -> Unit,
    onShareWorkoutFile: (String) -> Unit,
    onShareQr: (String) -> Unit,
    onSharePdf: (String) -> Unit,
) {
    val qrPayload = remember(payload) {
        payload?.let { runCatching { WorkoutShareCodec.encodeForQr(it) }.getOrNull() }
    }
    val qrFits = qrPayload?.let(WorkoutQrCodec::isWithinRecommendedSize) == true
    val qrBitmap = remember(qrPayload, qrFits) {
        qrPayload?.takeIf { qrFits }?.let { runCatching { WorkoutQrCodec.encode(it, 720) }.getOrNull() }
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("Compartilhar treino", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(workout.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Somente a ficha e os exercícios serão enviados — histórico e dados pessoais ficam no aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            failure != null -> Text(failure, color = MaterialTheme.colorScheme.error)
            payload == null -> CircularProgressIndicator()
            qrBitmap != null -> {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR para importar ${workout.name}",
                    modifier = Modifier.size(220.dp).background(Color.White).padding(10.dp),
                )
                Text("Escaneie ou compartilhe esta imagem", style = MaterialTheme.typography.labelMedium)
            }
            else -> Text(
                "Esta ficha é grande demais para um QR confiável. Use o arquivo do Liftly ou o PDF.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (payload != null) {
            FilledTonalButton(
                onClick = { onShareQr(payload) },
                enabled = qrFits,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar QR")
            }
            OutlinedButton(onClick = { onSharePdf(payload) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar PDF")
            }
            OutlinedButton(onClick = { onShareWorkoutFile(payload) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar arquivo editável")
            }
            TextButton(onClick = onSaveWorkoutFile) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salvar arquivo no aparelho")
            }
        }
    }
}

@Composable
private fun WorkoutHeader(
    workout: WorkoutEntity,
    itemCount: Int,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (workout.description.isNotBlank()) {
                        Text(
                            workout.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Editar ${workout.name}") }
            }
            Text(
                "$itemCount ${if (itemCount == 1) "exercício" else "exercícios"}  •  ${formatDays(workout.weekDays)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    AssistChip(onClick = onShare, label = { Text("Compartilhar") }, leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) })
                }
                item {
                    AssistChip(onClick = onDuplicate, label = { Text("Duplicar") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) })
                }
                item {
                    AssistChip(onClick = onArchive, label = { Text("Arquivar") }, leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) })
                }
                item {
                    AssistChip(onClick = onDelete, label = { Text("Excluir") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) })
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    number: Int,
    item: WorkoutExerciseEntity,
    exercise: ExerciseEntity?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onSubstitute: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise?.name ?: "Exercício indisponível", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.sets} × ${if (item.repMin == item.repMax) item.repMin else "${item.repMin}–${item.repMax}"} ${if (item.trackingMode == "Tempo") "s" else if (item.trackingMode == "Distância") "m" else "reps"} • ${formatLoad(item.targetLoadKg)} • ${item.restSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.setType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    exercise?.let { Text(it.muscleGroup, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(
                    onClick = onSubstitute,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Substituir exercício")
                }
                if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                IconButton(enabled = canMoveUp, onClick = onMoveUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Mover ${exercise?.name ?: "exercício"} para cima")
                }
                IconButton(enabled = canMoveDown, onClick = onMoveDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Mover ${exercise?.name ?: "exercício"} para baixo")
                }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Configurar ${exercise?.name ?: "exercício"}") }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remover ${exercise?.name ?: "exercício"}") }
            }
        }
    }
}

@Composable
private fun AnalysisPanel(
    exerciseCount: Int,
    totalSets: Int,
    muscleGroupCount: Int,
    suggestions: List<WorkoutSuggestion>,
    onIgnore: (WorkoutSuggestion) -> Unit,
    onApply: (WorkoutSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 620.dp).animateContentSize()) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Análise do treino", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Fechar análise") }
            }
            OutlinedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Análise concluída", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("$exerciseCount exercício(s) • $totalSets série(s) • $muscleGroupCount grupo(s) muscular(es)")
                    Text(
                        if (suggestions.isEmpty()) "Nenhuma recomendação pendente para esta configuração."
                        else "${suggestions.size} recomendação(ões) encontrada(s). Revise cada uma abaixo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (suggestions.isEmpty()) {
                Text("A ordem, os intervalos, o volume e o equilíbrio geral foram verificados.")
            } else {
                suggestions.forEach { suggestion ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            suggestion.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = when (suggestion.severity) {
                                SuggestionSeverity.ATTENTION -> MaterialTheme.colorScheme.error
                                SuggestionSeverity.SUGGESTION -> MaterialTheme.colorScheme.primary
                                SuggestionSeverity.INFORMATION -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(suggestion.message, style = MaterialTheme.typography.bodyMedium)
                        if (suggestion.evidence.isNotEmpty()) {
                            Text(suggestion.evidence.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onIgnore(suggestion) }) { Text("Ignorar") }
                            if (suggestion.action != null) {
                                TextButton(onClick = { onApply(suggestion) }) {
                                    Text(
                                        when (suggestion.action) {
                                            is WorkoutSuggestionAction.RemoveExercise -> "Revisar remoção"
                                            is WorkoutSuggestionAction.ReviewVolume -> "Revisar volume"
                                            is WorkoutSuggestionAction.ReplaceExercise -> "Escolher alternativa"
                                            else -> "Aplicar"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            Text(WORKOUT_ANALYSIS_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutDialog(
    workout: WorkoutEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Set<DayOfWeek>) -> Unit,
) {
    var name by remember(workout) { mutableStateOf(workout?.name.orEmpty()) }
    var description by remember(workout) { mutableStateOf(workout?.description.orEmpty()) }
    var days by remember(workout) { mutableStateOf(parseDays(workout?.weekDays.orEmpty())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (workout == null) "Novo treino" else "Editar treino") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descrição") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.entries) { day ->
                        AssistChip(
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(if (day in days) "✓ ${dayShort(day)}" else dayShort(day)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name.trim(), description.trim(), days)
                    onDismiss()
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfigureExerciseDialog(
    item: WorkoutExerciseEntity?,
    exercises: List<ExerciseEntity>,
    showSubstitutionsInitially: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Double, Int, String, String, String, String?) -> Unit,
) {
    var selectedExerciseId by remember(item) { mutableStateOf(item?.exerciseId) }
    var query by remember { mutableStateOf("") }
    var sets by remember(item) { mutableStateOf((item?.sets ?: 3).toString()) }
    var repMin by remember(item) { mutableStateOf((item?.repMin ?: 8).toString()) }
    var repMax by remember(item) { mutableStateOf((item?.repMax ?: 12).toString()) }
    var load by remember(item) { mutableStateOf((item?.targetLoadKg ?: 0.0).toEditableNumber()) }
    var rest by remember(item) { mutableStateOf((item?.restSeconds ?: 60).toString()) }
    var setType by remember(item) { mutableStateOf(item?.setType ?: "Normal") }
    var notes by remember(item) { mutableStateOf(item?.notes.orEmpty()) }
    var showPlateCalculator by remember(item) { mutableStateOf(false) }
    var showSubstitutions by remember(item, showSubstitutionsInitially) {
        mutableStateOf(showSubstitutionsInitially)
    }
    val selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId }
    var exerciseName by remember(selectedExerciseId, selectedExercise?.name) { mutableStateOf(selectedExercise?.name.orEmpty()) }
    val normalizedExerciseName = exerciseName.trim().replace(Regex("\\s+"), " ")
    val trackingMode = when {
        selectedExercise?.trackingUnit?.contains("tempo", ignoreCase = true) == true -> "Tempo"
        selectedExercise?.trackingUnit?.contains("dist", ignoreCase = true) == true -> "Distância"
        else -> item?.trackingMode ?: "Repetições"
    }
    val matching = remember(exercises, query) {
        exercises.filter {
            matchesSearchQuery(query, it.name, it.muscleGroup, it.equipment)
        }.sortedWith(compareByDescending<ExerciseEntity> { it.isFavorite }.thenBy { it.name }).take(12)
    }
    val substitutions = remember(selectedExercise, exercises) {
        selectedExercise?.let {
            ExerciseSubstitutionEngine.suggest(
                original = it,
                catalog = exercises,
                options = ExerciseSubstitutionOptions(limit = 5),
            )
        }.orEmpty()
    }
    val setsValue = sets.toIntOrNull()
    val minValue = repMin.toIntOrNull()
    val maxValue = repMax.toIntOrNull()
    val loadValue = load.replace(',', '.').toDoubleOrNull()
    val restValue = rest.toIntOrNull()
    val quickPlateLoadout = remember(loadValue) {
        loadValue?.takeIf { it > 0.0 }?.let {
            runCatching { BarbellPlateCalculator.calculate(it) }.getOrNull()
        }
    }
    val validName = selectedExercise == null || normalizedExerciseName.isNotBlank()
    val valid = selectedExerciseId != null && validName && setsValue != null && setsValue > 0 && minValue != null && minValue >= 0 &&
        maxValue != null && maxValue >= minValue && loadValue != null && loadValue >= 0 && restValue != null && restValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Adicionar exercício" else "Configurar exercício") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item == null && selectedExercise == null) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Buscar exercício") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                        )
                    }
                    items(matching, key = { it.id }) { exercise ->
                        OutlinedCard(onClick = { selectedExerciseId = exercise.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text("${exercise.muscleGroup} • ${exercise.equipment}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                selectedExercise?.let { Text("${it.muscleGroup} • ${it.trackingUnit}", style = MaterialTheme.typography.bodySmall) }
                            }
                            TextButton(onClick = { showSubstitutions = !showSubstitutions }) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Substituir")
                            }
                            if (item == null) TextButton(onClick = { selectedExerciseId = null }) { Text("Buscar") }
                        }
                    }
                    if (showSubstitutions) {
                        item {
                            Text(
                                "Alternativas inteligentes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Priorizadas por músculo, movimento, equipamento, nível e demanda técnica.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (substitutions.isEmpty()) {
                            item {
                                Text(
                                    "Nenhuma alternativa segura foi encontrada no catálogo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            items(substitutions, key = { "substitution-${it.exercise.id}" }) { suggestion ->
                                OutlinedCard(
                                    onClick = {
                                        selectedExerciseId = suggestion.exercise.id
                                        load = "0"
                                        showSubstitutions = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(suggestion.exercise.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${suggestion.exercise.muscleGroup} • ${suggestion.exercise.equipment}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            suggestion.reasons
                                                .filter { it.points > 0 }
                                                .take(3)
                                                .joinToString(" • ") { it.label },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (selectedExercise != null) {
                        item {
                            OutlinedTextField(
                                value = exerciseName,
                                onValueChange = { exerciseName = it.take(80) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nome do exercício") },
                                supportingText = { Text("A alteração também aparecerá no catálogo e nos outros treinos.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                        }
                    } else {
                        item { Text("Exercício indisponível no catálogo.", color = MaterialTheme.colorScheme.error) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(sets, { sets = it }, "Séries", Modifier.weight(1f))
                            NumberField(repMin, { repMin = it }, if (trackingMode == "Tempo") "Seg. mín." else if (trackingMode == "Distância") "m mín." else "Rep. mín.", Modifier.weight(1f))
                            NumberField(repMax, { repMax = it }, if (trackingMode == "Tempo") "Seg. máx." else if (trackingMode == "Distância") "m máx." else "Rep. máx.", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(load, { load = it }, "Carga (kg)", Modifier.weight(1f), decimal = true)
                            NumberField(rest, { rest = it }, "Descanso (s)", Modifier.weight(1f))
                        }
                    }
                    val plateLoadout = quickPlateLoadout
                    if (trackingMode == "Repetições" && plateLoadout != null) {
                        item {
                            OutlinedCard(
                                onClick = { showPlateCalculator = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text("Montagem da barra", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (plateLoadout.platesPerSide.isEmpty()) {
                                                "Sem anilhas com barra padrão de 20 kg"
                                            } else {
                                                "Cada lado: " + plateLoadout.platesPerSide.joinToString(" + ") { "${formatLoad(it)} kg" }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text("Ajustar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    item {
                        Text("Tipo de série", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("Normal", "Aquecimento", "Dropset", "Supersérie")) { option ->
                                AssistChip(onClick = { setType = option }, label = { Text(if (setType == option) "✓ $option" else option) })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Observações") },
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(selectedExerciseId!!, setsValue!!, minValue!!, maxValue!!, loadValue!!, restValue!!, setType, notes.trim(), trackingMode, selectedExercise?.let { normalizedExerciseName })
                    onDismiss()
                },
            ) { Text(if (item == null) "Adicionar" else "Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (showPlateCalculator) {
        PlateCalculatorSheet(
            initialTotalKg = loadValue ?: 0.0,
            onDismiss = { showPlateCalculator = false },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onValueChange(next)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
private fun EmptyWorkouts(onCreate: () -> Unit, onCalendar: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Monte seu primeiro treino", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Organize exercícios, séries e dias da semana em poucos passos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onCreate) { Text("Criar treino") }
            OutlinedButton(onClick = onCalendar) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Ver calendário")
            }
        }
    }
}

private suspend fun shareWorkoutPackage(context: Context, workout: WorkoutEntity, payload: String) {
    val file = withContext(Dispatchers.IO) {
        WorkoutShareFiles.createTemporaryFile(
            context,
            safeWorkoutFileName(workout.name),
            WorkoutShareCodec.FILE_EXTENSION,
        ).also { outputFile ->
            outputFile.outputStream().bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
        }
    }
    openShareSheet(context, file, WorkoutShareCodec.MIME_TYPE, "Treino ${workout.name}")
}

private suspend fun shareWorkoutQr(context: Context, workout: WorkoutEntity, payload: String) {
    val qrPayload = WorkoutShareCodec.encodeForQr(payload)
    require(WorkoutQrCodec.isWithinRecommendedSize(qrPayload)) {
        "Esta ficha é grande demais para um QR confiável. Compartilhe o arquivo do treino."
    }
    val file = withContext(Dispatchers.IO) {
        WorkoutShareFiles.createTemporaryFile(
            context,
            "${safeWorkoutFileName(workout.name)}-qr",
            "png",
        ).also { outputFile ->
            outputFile.outputStream().buffered().use { WorkoutQrCodec.writePng(qrPayload, it) }
        }
    }
    openShareSheet(context, file, "image/png", "QR do treino ${workout.name}")
}

private suspend fun shareWorkoutPdf(context: Context, workout: WorkoutEntity, payload: String) {
    val shared: SharedWorkoutPackage = WorkoutShareCodec.decode(payload)
    val qrPayload = WorkoutShareCodec.encodeForQr(payload)
    val file = withContext(Dispatchers.IO) {
        WorkoutShareFiles.createTemporaryFile(
            context,
            safeWorkoutFileName(workout.name),
            "pdf",
        ).also { outputFile ->
            val qr = qrPayload
                .takeIf(WorkoutQrCodec::isWithinRecommendedSize)
                ?.let { WorkoutQrCodec.encode(it, 720) }
            try {
                outputFile.outputStream().buffered().use { output ->
                    WorkoutPdfExporter.write(output, shared, qr)
                }
            } finally {
                qr?.recycle()
            }
        }
    }
    openShareSheet(context, file, "application/pdf", "Ficha ${workout.name}")
}

private fun openShareSheet(context: Context, file: File, mimeType: String, title: String) {
    val sendIntent = WorkoutShareFiles.buildSendIntent(context, file, mimeType, title)
    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar treino"))
}

private fun safeWorkoutFileName(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_-]+"), "-")
    .trim('-')
    .take(40)
    .ifBlank { "treino-liftly" }

private fun parseDays(raw: String): Set<DayOfWeek> = raw.split(',').mapNotNull { value ->
    value.trim().toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
}.toSet()

private fun formatDays(raw: String): String {
    val days = parseDays(raw)
    return if (days.isEmpty()) "Sem dias definidos" else days.sortedBy { it.value }.joinToString(", ") { dayShort(it) }
}

private fun dayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Seg"
    DayOfWeek.TUESDAY -> "Ter"
    DayOfWeek.WEDNESDAY -> "Qua"
    DayOfWeek.THURSDAY -> "Qui"
    DayOfWeek.FRIDAY -> "Sex"
    DayOfWeek.SATURDAY -> "Sáb"
    DayOfWeek.SUNDAY -> "Dom"
}

private fun formatLoad(value: Double): String = if (value <= 0) "sem carga" else "${value.toEditableNumber()} kg"

private fun Double.toEditableNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
