from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"pattern not found in {path}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))


# --- Build / modules / version -------------------------------------------------
replace_once("settings.gradle.kts", 'include(":app")', 'include(":app")\ninclude(":wear")\ninclude(":macrobenchmark")')
replace_once(
    "build.gradle.kts",
    '    id("com.android.application") version "9.2.1" apply false\n',
    '    id("com.android.application") version "9.2.1" apply false\n    id("com.android.test") version "9.2.1" apply false\n',
)
replace_once("app/build.gradle.kts", '        versionCode = 37\n        versionName = "1.5.5"', '        versionCode = 38\n        versionName = "1.6.0"')
replace_once(
    "app/build.gradle.kts",
    '    implementation("androidx.health.connect:connect-client:1.1.0")\n',
    '    implementation("androidx.health.connect:connect-client:1.1.0")\n'
    '    implementation("com.google.android.gms:play-services-wearable:20.0.1")\n'
    '    implementation("androidx.profileinstaller:profileinstaller:1.4.1")\n',
)

# --- Manifest / sharing --------------------------------------------------------
replace_once(
    "app/src/main/AndroidManifest.xml",
    '        <provider\n            android:name="androidx.core.content.FileProvider"',
    '        <service\n            android:name=".integration.wear.WearCommandListenerService"\n            android:exported="true">\n'
    '            <intent-filter>\n'
    '                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />\n'
    '            </intent-filter>\n'
    '        </service>\n\n'
    '        <provider\n            android:name="androidx.core.content.FileProvider"',
)
replace_once(
    "app/src/main/res/xml/file_paths.xml",
    '    <cache-path\n        name="shared_workouts"\n        path="shared_workouts/" />',
    '    <cache-path\n        name="shared_workouts"\n        path="shared_workouts/" />\n'
    '    <cache-path\n        name="workout_reports"\n        path="workout_reports/" />',
)

# --- Session summary -----------------------------------------------------------
replace_once(
    "app/src/main/java/com/liftly/app/data/Entities.kt",
    '    val completedRewardMissions: List<String> = emptyList(),\n)',
    '    val completedRewardMissions: List<String> = emptyList(),\n    val adaptiveChanges: Int = 0,\n)',
)

# --- Repository: reward backfill, adaptive prescriptions and Wear writes -------
repo_path = "app/src/main/java/com/liftly/app/data/LiftlyRepository.kt"
replace_once(
    repo_path,
    'import com.liftly.app.domain.EffectiveScheduleResolver\n',
    'import com.liftly.app.domain.AdaptiveTrainingPlan\n'
    'import com.liftly.app.domain.CurrentExerciseSetPerformance\n'
    'import com.liftly.app.domain.EffectiveScheduleResolver\n'
    'import com.liftly.app.domain.HistoricalExercisePerformance\n'
    'import com.liftly.app.domain.ProgressionCoach\n'
    'import com.liftly.app.domain.ProgressionCoachInput\n',
)
replace_once(
    repo_path,
    '            rewardStore.initializeInTransaction(System.currentTimeMillis())\n',
    '            rewardStore.initializeInTransaction(System.currentTimeMillis())\n            backfillHistoricalRewardsInTransaction()\n',
)

repo = read(repo_path)
anchor = '    suspend fun saveExercise(exercise: ExerciseEntity) = dao.upsertExercise(exercise)\n'
if anchor not in repo:
    raise RuntimeError("repository initialize anchor missing")
backfill = '''    /**
     * Idempotent retroactive migration. The immutable ledger key session:<id> prevents any
     * workout already rewarded by previous versions from minting XP/coins twice.
     */
    private suspend fun backfillHistoricalRewardsInTransaction() {
        val historical = dao.allSessions()
            .filter { it.finishedAt != null && !it.isTestMode }
            .sortedWith(compareBy<SessionEntity> { it.startedAt }.thenBy { it.id })
        val previousBest = mutableMapOf<String, Double>()
        historical.forEach { session ->
            val sessionSets = dao.sessionSets(session.id)
            val completed = sessionSets.filter(SessionSetEntity::completed)
            if (completed.isEmpty()) return@forEach
            var personalRecords = 0
            completed.groupBy(SessionSetEntity::exerciseId).forEach { (exerciseId, exerciseSets) ->
                val best = exerciseSets.maxOf(SessionSetEntity::loadKg)
                val old = previousBest[exerciseId]
                if (old != null && best > old + 0.0001) personalRecords++
                if (old == null || best > old) previousBest[exerciseId] = best
            }
            rewardStore.awardWorkoutCompletionInTransaction(
                sessionId = session.id,
                metrics = WorkoutRewardMetrics(
                    completedSets = completed.size,
                    totalSets = sessionSets.size,
                    rirRecordedSets = completed.count { it.rir != null },
                    personalRecords = personalRecords,
                ),
                occurredAt = session.finishedAt ?: session.startedAt,
            )
        }
    }

'''
repo = repo.replace(anchor, backfill + anchor, 1)
write(repo_path, repo)

replace_once(
    repo_path,
    '    suspend fun substituteSessionExercise(\n',
    '''    suspend fun updateSetFromWear(
        setId: String,
        reps: Int,
        loadKg: Double,
        rir: Int?,
        complete: Boolean,
    ) {
        val current = dao.sessionSet(setId) ?: return
        val session = dao.session(current.sessionId) ?: return
        if (session.status != "Em andamento") return
        saveSet(
            item = current,
            reps = reps,
            load = loadKg,
            toggleCompletion = complete,
            rir = rir,
            painLevel = current.painLevel,
        )
    }

    suspend fun substituteSessionExercise(
''',
)

repo = read(repo_path)
finish_anchor = '    suspend fun finishSession(sessionId: String): SessionSummary = database.withTransaction {\n'
if finish_anchor not in repo:
    raise RuntimeError("finishSession anchor missing")
adaptive_helper = '''    private suspend fun adaptWorkoutPlanAfterSession(
        session: SessionEntity,
        completedSets: List<SessionSetEntity>,
    ): Int {
        if (session.isTestMode || completedSets.isEmpty()) return 0
        val previousSessions = dao.allSessions()
            .filter { it.id != session.id && it.finishedAt != null && !it.isTestMode }
            .associate { it.id to it.startedAt }
        val historicalSets = dao.allSessionSets()
        var changed = 0
        completedSets.groupBy(SessionSetEntity::workoutExerciseId).forEach { (workoutExerciseId, current) ->
            val item = dao.workoutExercise(workoutExerciseId) ?: return@forEach
            if (current.firstOrNull()?.exerciseId != item.exerciseId) return@forEach
            if (!item.trackingMode.contains("Rep", ignoreCase = true)) return@forEach
            val exercise = dao.exercise(item.exerciseId) ?: return@forEach
            val currentPerformances = current
                .filter { it.completed }
                .sortedBy { it.setNumber }
                .map {
                    CurrentExerciseSetPerformance(
                        setNumber = it.setNumber,
                        reps = it.reps,
                        loadKg = it.loadKg,
                        rir = it.rir,
                        painLevel = it.painLevel,
                    )
                }
            if (currentPerformances.isEmpty()) return@forEach
            val recent = historicalSets
                .asSequence()
                .filter { it.exerciseId == item.exerciseId && it.sessionId in previousSessions && it.completed }
                .groupBy { "${it.sessionId}:${it.workoutExerciseId}" }
                .entries
                .sortedByDescending { entry -> previousSessions[entry.value.first().sessionId] ?: 0L }
                .mapNotNull { entry ->
                    val values = entry.value.sortedBy { it.setNumber }
                    val latest = values.lastOrNull() ?: return@mapNotNull null
                    HistoricalExercisePerformance(
                        actualReps = values.minOf { it.reps },
                        actualLoadKg = latest.loadKg,
                        rir = values.mapNotNull { it.rir }.minOrNull(),
                        painLevel = values.maxOf { it.painLevel },
                    )
                }
                .take(2)
            val latest = currentPerformances.last()
            val recommendation = ProgressionCoach().recommend(
                ProgressionCoachInput(
                    exerciseName = exercise.name,
                    category = exercise.category.ifBlank { "Musculação" },
                    plannedRepMin = item.repMin.coerceAtLeast(1),
                    plannedRepMax = item.repMax.coerceAtLeast(item.repMin.coerceAtLeast(1)),
                    plannedLoadKg = item.targetLoadKg,
                    actualReps = latest.reps,
                    actualLoadKg = latest.loadKg,
                    rir = latest.rir,
                    painLevel = currentPerformances.maxOf { it.painLevel },
                    recentPerformances = recent,
                    currentSets = currentPerformances,
                )
            )
            val prescription = AdaptiveTrainingPlan.prescription(item, recommendation) ?: return@forEach
            dao.upsertWorkoutExercise(AdaptiveTrainingPlan.apply(item, prescription))
            changed++
        }
        return changed
    }

'''
repo = repo.replace(finish_anchor, adaptive_helper + finish_anchor, 1)
write(repo_path, repo)
replace_once(
    repo_path,
    '            val completedSets = sets.filter(SessionSetEntity::completed)\n            val personalRecords = completedSets\n',
    '            val completedSets = sets.filter(SessionSetEntity::completed)\n            val adaptiveChanges = adaptWorkoutPlanAfterSession(session, completedSets)\n            val personalRecords = completedSets\n',
)
replace_once(
    repo_path,
    '                completedRewardMissions = grant.completedMissionIds,\n            )',
    '                completedRewardMissions = grant.completedMissionIds,\n                adaptiveChanges = adaptiveChanges,\n            )',
)

# --- AppViewModel: report + continuous Wear sync -------------------------------
vm_path = "app/src/main/java/com/liftly/app/ui/AppViewModel.kt"
replace_once(
    vm_path,
    'import com.liftly.app.domain.ParsedWorkout\n',
    'import com.liftly.app.domain.ParsedWorkout\n'
    'import com.liftly.app.domain.WorkoutReport\n'
    'import com.liftly.app.domain.WorkoutReportBuilder\n'
    'import com.liftly.app.integration.wear.WearSessionBridge\n',
)
replace_once(
    vm_path,
    'import kotlinx.coroutines.flow.MutableStateFlow\n',
    'import kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.flow.combine\n',
)
replace_once(
    vm_path,
    '    private val _lastSummary = MutableStateFlow<SessionSummary?>(null)\n    val lastSummary: StateFlow<SessionSummary?> = _lastSummary\n',
    '    private val _lastSummary = MutableStateFlow<SessionSummary?>(null)\n    val lastSummary: StateFlow<SessionSummary?> = _lastSummary\n'
    '    private val _lastReport = MutableStateFlow<WorkoutReport?>(null)\n    val lastReport: StateFlow<WorkoutReport?> = _lastReport\n',
)
replace_once(
    vm_path,
    '        viewModelScope.launch {\n            preferencesRepository.preferences.collect { prefs ->\n                lastPreferences = prefs\n                if (!_ready.value && _initializationError.value == null) initializeApplication(prefs.demoEnabled)\n            }\n        }\n',
    '        viewModelScope.launch {\n            preferencesRepository.preferences.collect { prefs ->\n                lastPreferences = prefs\n                if (!_ready.value && _initializationError.value == null) initializeApplication(prefs.demoEnabled)\n            }\n        }\n'
    '        viewModelScope.launch {\n            combine(repository.sessions, repository.sessionSets) { activeSessions, activeSets ->\n'
    '                activeSessions to activeSets\n'
    '            }.collect { (activeSessions, activeSets) ->\n'
    '                WearSessionBridge.publish(getApplication<Application>(), activeSessions, activeSets)\n'
    '            }\n'
    '        }\n',
)
replace_once(
    vm_path,
    '        val setsBeforeFinish = sessionSets.value.filter { it.sessionId == sessionId }\n        val summary = repository.finishSession(sessionId)\n',
    '        val setsBeforeFinish = sessionSets.value.filter { it.sessionId == sessionId }\n'
    '        val reportSetsSnapshot = sessionSets.value.toList()\n'
    '        val reportSessionsSnapshot = sessions.value.toList()\n'
    '        val summary = repository.finishSession(sessionId)\n',
)
replace_once(
    vm_path,
    '        _lastSummary.value = summary\n        _feedback.value = ActionFeedback(\n',
    '        _lastSummary.value = summary\n'
    '        _lastReport.value = WorkoutReportBuilder.build(\n'
    '            summary = summary,\n'
    '            currentSets = setsBeforeFinish,\n'
    '            allSets = reportSetsSnapshot,\n'
    '            sessions = reportSessionsSnapshot,\n'
    '        )\n'
    '        _feedback.value = ActionFeedback(\n',
)

# --- Navigation to post-workout report ----------------------------------------
nav_path = "app/src/main/java/com/liftly/app/ui/LiftlyApp.kt"
replace_once(
    nav_path,
    'import com.liftly.app.ui.screens.ProfileScreen\n',
    'import com.liftly.app.ui.screens.PostWorkoutReportScreen\nimport com.liftly.app.ui.screens.ProfileScreen\n',
)
replace_once(
    nav_path,
    '                    onFinished = {\n                        if (!navController.popBackStack("today", false)) {\n                            navController.navigate("today") {\n                                popUpTo(navController.graph.findStartDestination().id) {\n                                    inclusive = false\n                                }\n                                launchSingleTop = true\n                            }\n                        }\n                    },\n',
    '                    onFinished = {\n'
    '                        navController.navigate("report") {\n'
    '                            popUpTo("today") { inclusive = false }\n'
    '                            launchSingleTop = true\n'
    '                        }\n'
    '                    },\n',
)
replace_once(
    nav_path,
    '            composable("calendar") {\n                CalendarScreen(vm) { navController.popBackStack() }\n            }\n',
    '            composable("calendar") {\n                CalendarScreen(vm) { navController.popBackStack() }\n            }\n'
    '            composable("report") {\n'
    '                PostWorkoutReportScreen(vm = vm) {\n'
    '                    navController.navigate("today") {\n'
    '                        popUpTo("report") { inclusive = true }\n'
    '                        launchSingleTop = true\n'
    '                    }\n'
    '                }\n'
    '            }\n',
)

# --- Focused session and shared set surface -----------------------------------
today_path = "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt"
replace_once(
    today_path,
    'import com.liftly.app.ui.components.PlateCalculatorSheet\n',
    'import com.liftly.app.ui.components.PlateCalculatorSheet\nimport com.liftly.app.ui.components.TrainingSetSurface\n',
)
replace_once(
    today_path,
    '    val nextWorkSet = sets.firstOrNull { !it.completed }\n',
    '    val nextWorkSet = sets.firstOrNull { !it.completed }\n'
    '    val visibleExerciseGroups = nextWorkSet?.workoutExerciseId?.let { focusedId ->\n'
    '        orderedExerciseGroups.filter { it.first().workoutExerciseId == focusedId }\n'
    '    } ?: orderedExerciseGroups\n',
)
replace_once(today_path, '            orderedExerciseGroups.forEachIndexed { groupIndex, exerciseSets ->', '            visibleExerciseGroups.forEachIndexed { groupIndex, exerciseSets ->')
replace_once(today_path, '                val isFirstGroupForWorkoutItem = orderedExerciseGroups\n                    .indexOfFirst { it.first().workoutExerciseId == workoutExerciseId } == groupIndex', '                val isFirstGroupForWorkoutItem = visibleExerciseGroups\n                    .indexOfFirst { it.first().workoutExerciseId == workoutExerciseId } == groupIndex')
replace_once(
    today_path,
    '                items(exerciseSets, key = { it.id }) { set ->\n                    val equipment = exercises.firstOrNull { it.id == set.exerciseId }?.equipment.orEmpty()\n                    SessionSetRow(\n                        set = set,\n                        supportsPlateCalculator = equipment.contains("barra", ignoreCase = true) ||',
    '                val visibleSets = exerciseSets.filter { it.completed || it.id == nextWorkSet?.id }\n'
    '                items(visibleSets, key = { it.id }) { set ->\n'
    '                    val equipment = exercises.firstOrNull { it.id == set.exerciseId }?.equipment.orEmpty()\n'
    '                    val previousSet = allSets.asSequence()\n'
    '                        .filter { it.sessionId != sessionId && it.exerciseId == set.exerciseId && it.completed }\n'
    '                        .maxByOrNull { it.completedAt ?: 0L }\n'
    '                    SessionSetRow(\n'
    '                        set = set,\n'
    '                        isFocus = nextWorkSet?.id == set.id,\n'
    '                        previousLoadKg = previousSet?.loadKg,\n'
    '                        previousReps = previousSet?.reps,\n'
    '                        supportsPlateCalculator = equipment.contains("barra", ignoreCase = true) ||',
)

today = read(today_path)
pattern = re.compile(r'@Composable\nprivate fun SessionSetRow\(.*?\n\}\n\n@Composable\nprivate fun ProgressionCoachCard', re.S)
match = pattern.search(today)
if not match:
    raise RuntimeError("SessionSetRow block not found")
new_set_row = r'''@Composable
private fun SessionSetRow(
    set: SessionSetEntity,
    isFocus: Boolean,
    previousLoadKg: Double?,
    previousReps: Int?,
    supportsPlateCalculator: Boolean,
    onChange: (Int, Double, Boolean, Int?, Int) -> Unit,
) {
    var repsText by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(set.reps.toString()) }
    var loadText by rememberSaveable(set.id, set.exerciseId, set.loadKg) { mutableStateOf(set.loadKg.toClean()) }
    var rir by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(set.rir) }
    var painLevel by rememberSaveable(set.id, set.exerciseId) { mutableIntStateOf(set.painLevel) }
    var showEffort by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(false) }
    var showPlateCalculator by rememberSaveable(set.id, set.exerciseId) { mutableStateOf(false) }

    fun persist(toggle: Boolean = false) {
        onChange(
            repsText.toIntOrNull() ?: 0,
            loadText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            toggle,
            rir,
            painLevel,
        )
    }

    TrainingSetSurface(
        numberLabel = set.setNumber.toString().padStart(2, '0'),
        title = set.exerciseName,
        subtitle = if (isFocus) "SÉRIE ATUAL" else "Série concluída",
        completed = set.completed,
        onCompletedChange = { persist(toggle = true) },
        badge = if (isFocus && !set.completed) "AGORA" else null,
    ) {
        if (isFocus && previousLoadKg != null && previousReps != null) {
            Text(
                "Anterior: ${previousLoadKg.toClean()} kg × $previousReps reps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                repsText,
                onValueChange = { value ->
                    repsText = value.filter(Char::isDigit).take(3)
                    persist()
                },
                label = { Text(if (set.trackingMode == "Tempo") "Segundos" else if (set.trackingMode == "Distância") "Metros" else "Reps") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !set.completed,
            )
            OutlinedTextField(
                loadText,
                onValueChange = { value ->
                    loadText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                    persist()
                },
                label = { Text("kg") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = !set.completed,
            )
        }
        if (supportsPlateCalculator && set.isCoachRepetitionBased() && !set.completed) {
            val loadValue = loadText.replace(',', '.').toDoubleOrNull() ?: 0.0
            TextButton(onClick = { showPlateCalculator = true }, enabled = loadValue > 0.0) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                Text("Calcular anilhas", Modifier.padding(start = 7.dp))
            }
        }
        if (!set.completed) {
            TextButton(onClick = { showEffort = !showEffort }) {
                Text(if (rir == null && painLevel == 0) "RIR e dor (opcional)" else "RIR ${rir?.toString() ?: "—"} • dor $painLevel/10")
            }
            AnimatedVisibility(showEffort) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Repetições em reserva (RIR)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf<Int?>(null, 0, 1, 2, 3, 4, 5)) { value ->
                            FilterChip(
                                selected = rir == value,
                                onClick = { rir = value; persist() },
                                label = { Text(if (value == null) "Não sei" else if (value == 5) "5+" else value.toString()) },
                            )
                        }
                    }
                    Text("Dor durante o movimento: $painLevel/10", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = painLevel.toFloat(),
                        onValueChange = { painLevel = it.toInt() },
                        onValueChangeFinished = { persist() },
                        valueRange = 0f..10f,
                        steps = 9,
                    )
                }
            }
        }
        if (isFocus && !set.completed) {
            Button(onClick = { persist(toggle = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Concluir série", fontWeight = FontWeight.Black)
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
private fun ProgressionCoachCard'''
today = pattern.sub(new_set_row, today, count=1)
write(today_path, today)

# Warmup advances one step at a time as the normal workout does.
warmup_path = "app/src/main/java/com/liftly/app/ui/screens/SessionWarmupUi.kt"
replace_once(
    warmup_path,
    '        steps.forEachIndexed { index, step ->\n            val completed = step.id in completedIds\n',
    '        val firstPendingId = steps.firstOrNull { it.id !in completedIds }?.id\n'
    '        val visibleSteps = steps.filter { it.id in completedIds || it.id == firstPendingId }\n'
    '        visibleSteps.forEach { step ->\n'
    '            val index = steps.indexOf(step)\n'
    '            val completed = step.id in completedIds\n',
)

# --- Progress: interpret instead of only charting ------------------------------
progress_path = "app/src/main/java/com/liftly/app/ui/screens/ProgressScreen.kt"
replace_once(
    progress_path,
    'import com.liftly.app.domain.MonthlyTrainingChallenge\n',
    'import com.liftly.app.domain.MonthlyTrainingChallenge\n'
    'import com.liftly.app.domain.ProgressInsightEngine\n'
    'import com.liftly.app.domain.ProgressInsightKind\n',
)
replace_once(
    progress_path,
    '    val personalRanking = remember(completedSets, optionNames) {\n',
    '    val progressReading = remember(sessions, sets, exercises, preferences.weeklyWorkoutGoal) {\n'
    '        ProgressInsightEngine.calculate(\n'
    '            sessions = sessions,\n'
    '            sets = sets,\n'
    '            exercises = exercises,\n'
    '            weeklyGoal = preferences.weeklyWorkoutGoal,\n'
    '        )\n'
    '    }\n'
    '    val personalRanking = remember(completedSets, optionNames) {\n',
)
replace_once(
    progress_path,
    '            item { MetricCard("Volume total", "${totalVolume.clean()} kg", Icons.Default.Insights, Modifier.fillMaxWidth()) }\n',
    '            item { MetricCard("Volume total", "${totalVolume.clean()} kg", Icons.Default.Insights, Modifier.fillMaxWidth()) }\n'
    '            item {\n'
    '                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {\n'
    '                    Text("LEITURA DO LIFTLY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)\n'
    '                    Text(progressReading.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n'
    '                    progressReading.insights.forEach { insight ->\n'
    '                        val accent = when (insight.kind) {\n'
    '                            ProgressInsightKind.POSITIVE -> MaterialTheme.colorScheme.primary\n'
    '                            ProgressInsightKind.ATTENTION -> MaterialTheme.colorScheme.error\n'
    '                            ProgressInsightKind.NEUTRAL -> MaterialTheme.colorScheme.tertiary\n'
    '                        }\n'
    '                        Surface(\n'
    '                            modifier = Modifier.fillMaxWidth(),\n'
    '                            shape = MaterialTheme.shapes.large,\n'
    '                            color = MaterialTheme.colorScheme.surface,\n'
    '                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.42f)),\n'
    '                        ) {\n'
    '                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n'
    '                                Text(insight.title, fontWeight = FontWeight.Black, color = accent)\n'
    '                                Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n'
    '                            }\n'
    '                        }\n'
    '                    }\n'
    '                }\n'
    '            }\n',
)

# --- Rewards: historical-first, weekly/monthly, less obligation ----------------
rewards_domain = "app/src/main/java/com/liftly/app/domain/LiftlyRewards.kt"
replace_once(rewards_domain, '        var xp = 100L\n        var coins = 25L\n        val reasons = mutableListOf("Treino concluído")', '        var xp = 80L\n        var coins = 15L\n        val reasons = mutableListOf("Sessão válida registrada")')
replace_once(rewards_domain, '            xp += 30\n            coins += 10\n            reasons += "Ficha completa"', '            xp += 20\n            coins += 5\n            reasons += "Plano do dia concluído"')
replace_once(rewards_domain, '            xp += 20\n            coins += 5\n            reasons += "RIR registrado em todas as séries"', '            xp += 15\n            coins += 5\n            reasons += "Esforço registrado com consistência"')
replace_once(rewards_domain, '        val rewardedRecords = metrics.personalRecords.coerceAtMost(3)\n        if (rewardedRecords > 0) {\n            xp += 50L * rewardedRecords\n            coins += 20L * rewardedRecords', '        val rewardedRecords = metrics.personalRecords.coerceAtMost(2)\n        if (rewardedRecords > 0) {\n            xp += 30L * rewardedRecords\n            coins += 10L * rewardedRecords')

catalog_path = "app/src/main/java/com/liftly/app/data/LiftlyRewardCatalog.kt"
catalog = read(catalog_path)
old_missions = '''        return listOf(
            mission("daily.$date.workout", RewardPeriod.DAILY, RewardMetric.WORKOUT_COMPLETED, "Treino do dia", "Conclua um treino válido hoje.", 1, 50, 20, dayStart, dayEnd, 10),
            mission("daily.$date.rir", RewardPeriod.DAILY, RewardMetric.RIR_SET_RECORDED, "Treino consciente", "Registre o RIR em 3 séries concluídas.", 3, 30, 10, dayStart, dayEnd, 11),
            mission("weekly.$weekDate.workouts", RewardPeriod.WEEKLY, RewardMetric.WORKOUT_COMPLETED, "Ritmo semanal", "Conclua 3 treinos nesta semana.", 3, 150, 50, weekStart, weekEnd, 20),
            mission("weekly.$weekDate.complete", RewardPeriod.WEEKLY, RewardMetric.COMPLETE_WORKOUT, "Plano cumprido", "Finalize 2 fichas sem deixar séries pendentes.", 2, 100, 35, weekStart, weekEnd, 21),
            mission("monthly.$month.workouts", RewardPeriod.MONTHLY, RewardMetric.WORKOUT_COMPLETED, "Consistência mensal", "Conclua 12 treinos neste mês.", 12, 400, 150, monthStart, monthEnd, 30),
            mission("monthly.$month.records", RewardPeriod.MONTHLY, RewardMetric.PERSONAL_RECORD, "Evolução mensurável", "Registre 3 novos recordes pessoais.", 3, 200, 75, monthStart, monthEnd, 31),
        )'''
new_missions = '''        return listOf(
            mission("weekly.$weekDate.workouts", RewardPeriod.WEEKLY, RewardMetric.WORKOUT_COMPLETED, "Semana consistente", "Conclua 3 treinos válidos nesta semana, nos dias que fizerem sentido para você.", 3, 120, 35, weekStart, weekEnd, 20),
            mission("weekly.$weekDate.rir", RewardPeriod.WEEKLY, RewardMetric.RIR_SET_RECORDED, "Registro de qualidade", "Registre o RIR em 6 séries concluídas ao longo da semana.", 6, 80, 25, weekStart, weekEnd, 21),
            mission("monthly.$month.workouts", RewardPeriod.MONTHLY, RewardMetric.WORKOUT_COMPLETED, "Bloco consistente", "Conclua 10 treinos válidos neste mês.", 10, 300, 100, monthStart, monthEnd, 30),
            mission("monthly.$month.records", RewardPeriod.MONTHLY, RewardMetric.PERSONAL_RECORD, "Evolução mensurável", "Registre 2 novos recordes pessoais no mês.", 2, 150, 50, monthStart, monthEnd, 31),
        )'''
if old_missions not in catalog:
    raise RuntimeError("mission factory block not found")
catalog = catalog.replace(old_missions, new_missions, 1)
write(catalog_path, catalog)

# Remove unused daily epoch variables after daily missions leave the policy.
catalog = read(catalog_path)
catalog = re.sub(r'        val dayStart = .*?\n        val dayEnd = .*?\n', '', catalog, count=1)
write(catalog_path, catalog)

replace_once(
    nav_path,
    '        mutableStateOf(MissionPeriod.Daily.name)\n',
    '        mutableStateOf(MissionPeriod.Weekly.name)\n',
)
rewards_ui = "app/src/main/java/com/liftly/app/ui/rewards/RewardsScreen.kt"
replace_once(rewards_ui, '                        Text("Rewards", fontWeight = FontWeight.Bold)', '                        Text("Conquistas", fontWeight = FontWeight.Bold)')
replace_once(rewards_ui, '                            "Sua consistência tem valor",', '                            "Seu histórico vira identidade, não obrigação",')
replace_once(rewards_ui, '                    "${account.workoutStreak} semanas na sequência",', '                    "${account.workoutStreak} semanas cumprindo sua meta",')
replace_once(rewards_ui, '            title = "Missões",\n            subtitle = "Objetivos ajustados à sua rotina de treino.",', '            title = "Marcos de treino",\n            subtitle = "Metas semanais e mensais, sem punição por perder um dia.",')
replace_once(rewards_ui, '            MissionPeriod.entries.forEach { period ->', '            MissionPeriod.entries.filterNot { it == MissionPeriod.Daily }.forEach { period ->')
replace_once(rewards_ui, '            "As recompensas consideram treinos concluídos e registros válidos. Um mesmo treino não gera moedas duplicadas.",', '            "Treinos antigos também entram no histórico de recompensas. Não existe perda de XP por descanso ou por quebrar sequência, e um mesmo treino nunca paga duas vezes.",')

# --- README / changelog --------------------------------------------------------
readme_path = "README.md"
readme = read(readme_path)
readme = readme.replace('## Versão atual — 1.5.5', '## Versão atual — 1.6.0', 1)
readme = readme.replace('**versionName:** `1.5.5`', '**versionName:** `1.6.0`', 1)
readme = readme.replace('**versionCode:** `37`', '**versionCode:** `38`', 1)
marker = '## Novidades da 1.5.5\n'
if marker not in readme:
    raise RuntimeError("README release marker missing")
new_release = '''## Novidades da 1.6.0

A 1.6.0 aprofunda o Liftly como sistema de treino:

- Coach fecha o ciclo e ajusta a próxima prescrição após a sessão quando os dados sustentam aumento, manutenção, redução ou deload;
- sessão focada em uma série por vez, com histórico anterior visível;
- aquecimento usa a mesma superfície e o mesmo fluxo de conclusão das séries normais, sem entrar no volume oficial;
- relatório pós-treino com duração, volume, recordes, comparação e card PNG compartilhável;
- Progresso interpreta aderência, tendência de volume, possíveis estagnações e distribuição de séries;
- Rewards retroativas recalculadas a partir de treinos antigos com ledger idempotente;
- Rewards passam a priorizar marcos semanais/mensais e não punem descanso ou quebra de sequência;
- módulo Wear OS com série ativa, carga, reps, RIR, conclusão remota e frequência cardíaca via Health Services;
- ProfileInstaller, Baseline Profile, Macrobenchmark, teste visual e CI ampliado.

'''
readme = readme.replace(marker, new_release + marker, 1)
write(readme_path, readme)

changelog_path = "CHANGELOG.md"
changelog = read(changelog_path)
heading = '# Changelog\n\n'
if heading not in changelog:
    raise RuntimeError("CHANGELOG heading missing")
entry = '''## 1.6.0 — 2026-08-10

- Coach adaptativo agora persiste a próxima prescrição quando há evidência suficiente.
- Sessão e aquecimento unificados em uma experiência focada, uma etapa por vez.
- Relatório pós-treino e card compartilhável.
- Progresso interpretativo com tendências e alertas de estagnação.
- Rewards retroativas para treinos históricos, com proteção contra duplicidade.
- Rewards reorganizadas em marcos semanais e mensais sem punição por descanso.
- Companion Wear OS com Data Layer e frequência cardíaca via Health Services.
- Baseline Profile, Macrobenchmark, screenshot smoke test e ProfileInstaller.
- `versionName` 1.6.0 / `versionCode` 38.

'''
changelog = changelog.replace(heading, heading + entry, 1)
write(changelog_path, changelog)

print("Liftly 1.6.0 transformation applied")
