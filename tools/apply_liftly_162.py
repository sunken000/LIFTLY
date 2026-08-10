from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f"pattern not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Workouts UI: explicit two-step Bi-set pairing directly from the workout list.
workouts = Path("app/src/main/java/com/liftly/app/ui/screens/WorkoutsScreen.kt")
text = workouts.read_text()
text = text.replace(
    "import androidx.compose.material.icons.outlined.KeyboardArrowUp\n",
    "import androidx.compose.material.icons.outlined.KeyboardArrowUp\n"
    "import androidx.compose.material.icons.outlined.Link\n"
    "import androidx.compose.material.icons.outlined.LinkOff\n",
    1,
)
text = text.replace(
    "import com.liftly.app.domain.ExerciseSubstitutionOptions\n",
    "import com.liftly.app.domain.ExerciseSubstitutionOptions\n"
    "import com.liftly.app.domain.SupersetPlanner\n",
    1,
)
text = text.replace(
    "    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }\n",
    "    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }\n"
    "    var biSetSourceId by rememberSaveable(selectedWorkoutId) { mutableStateOf<String?>(null) }\n",
    1,
)
text = text.replace(
    "    LaunchedEffect(selectedWorkoutId) {\n"
    "        suggestions = null\n"
    "        ignoredFingerprints = emptySet()\n"
    "    }\n",
    "    LaunchedEffect(selectedWorkoutId) {\n"
    "        suggestions = null\n"
    "        ignoredFingerprints = emptySet()\n"
    "        biSetSourceId = null\n"
    "    }\n",
    1,
)
text = text.replace(
    "    val exerciseById = exercises.associateBy { it.id }\n",
    "    val exerciseById = exercises.associateBy { it.id }\n"
    "    val biSetMemberships = remember(selectedItems) { SupersetPlanner.memberships(selectedItems) }\n"
    "    val biSetSource = selectedItems.firstOrNull { it.id == biSetSourceId }\n",
    1,
)
old_list = '''                    } else {
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
'''
new_list = '''                    } else {
                        if (biSetSource != null) {
                            item(key = "bi-set-selection") {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                            Text("Escolha o segundo exercício", fontWeight = FontWeight.Bold)
                                            Text(
                                                "${exerciseById[biSetSource.exerciseId]?.name ?: "Exercício"} será o BI-SET A.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = { biSetSourceId = null }) { Text("Cancelar") }
                                    }
                                }
                            }
                        }
                        itemsIndexed(selectedItems, key = { _, item -> item.id }) { index, item ->
                            val membership = biSetMemberships[item.id]
                            val partnerItem = membership?.partnerWorkoutExerciseId?.let { partnerId ->
                                selectedItems.firstOrNull { it.id == partnerId }
                            }
                            val partnerName = partnerItem?.exerciseId?.let { exerciseById[it]?.name }
                            WorkoutExerciseCard(
                                number = index + 1,
                                item = item,
                                exercise = exerciseById[item.exerciseId],
                                canMoveUp = index > 0,
                                canMoveDown = index < selectedItems.lastIndex,
                                biSetPosition = membership?.position,
                                biSetPartnerName = partnerName,
                                isBiSetSource = biSetSourceId == item.id,
                                isChoosingBiSetPartner = biSetSourceId != null && biSetSourceId != item.id,
                                onBiSet = {
                                    val sourceId = biSetSourceId
                                    when {
                                        sourceId != null && sourceId != item.id -> {
                                            vm.pairWorkoutExercisesAsBiSet(workout.id, sourceId, item.id)
                                            biSetSourceId = null
                                        }
                                        sourceId == item.id -> biSetSourceId = null
                                        membership != null -> vm.unpairWorkoutBiSet(workout.id, item.id)
                                        else -> biSetSourceId = item.id
                                    }
                                },
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
'''
if old_list not in text:
    raise RuntimeError("workout list block not found")
text = text.replace(old_list, new_list, 1)

start = text.index("@Composable\nprivate fun WorkoutExerciseCard(")
end = text.index("\n@Composable\nprivate fun AnalysisPanel(", start)
new_card = '''@Composable
private fun WorkoutExerciseCard(
    number: Int,
    item: WorkoutExerciseEntity,
    exercise: ExerciseEntity?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    biSetPosition: Int?,
    biSetPartnerName: String?,
    isBiSetSource: Boolean,
    isChoosingBiSetPartner: Boolean,
    onBiSet: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onSubstitute: () -> Unit,
    onRemove: () -> Unit,
) {
    val isPaired = biSetPosition != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isBiSetSource) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isPaired || isBiSetSource) MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number.toString().padStart(2, '0'),
                modifier = Modifier.width(42.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise?.name ?: "Exercício indisponível", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${item.sets} × ${if (item.repMin == item.repMax) item.repMin else "${item.repMin}–${item.repMax}"} ${if (item.trackingMode == "Tempo") "s" else if (item.trackingMode == "Distância") "m" else "reps"}  /  ${formatLoad(item.targetLoadKg)}  /  ${item.restSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isPaired) "BI-SET ${if (biSetPosition == 1) "A" else "B"} ↔ ${biSetPartnerName ?: "parceiro"}"
                        else item.setType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!isPaired) {
                        exercise?.let { Text(it.muscleGroup.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onSubstitute, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Substituir")
                    }
                    AssistChip(
                        onClick = onBiSet,
                        label = {
                            Text(
                                when {
                                    isBiSetSource -> "Cancelar"
                                    isChoosingBiSetPartner -> "Juntar"
                                    isPaired -> "Desfazer"
                                    else -> "Bi-set"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isPaired && !isChoosingBiSetPartner) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
                if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                IconButton(enabled = canMoveUp, onClick = onMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Mover ${exercise?.name ?: "exercício"} para cima") }
                IconButton(enabled = canMoveDown, onClick = onMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Mover ${exercise?.name ?: "exercício"} para baixo") }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Configurar ${exercise?.name ?: "exercício"}") }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remover ${exercise?.name ?: "exercício"}") }
            }
        }
    }
}
'''
text = text[:start] + new_card + text[end:]
workouts.write_text(text)

# Repository operations keep pairing atomic and order-safe.
repository = Path("app/src/main/java/com/liftly/app/data/LiftlyRepository.kt")
text = repository.read_text()
text = text.replace(
    "import com.liftly.app.domain.AdaptiveTrainingPlan\n",
    "import com.liftly.app.domain.AdaptiveTrainingPlan\nimport com.liftly.app.domain.BiSetPairing\n",
    1,
)
needle = '''    suspend fun moveWorkoutExerciseBefore(workoutId: String, id: String, beforeId: String) = database.withTransaction {
        persistWorkoutExerciseOrder(WorkoutExerciseOrder.moveBefore(dao.workoutExercises(workoutId), id, beforeId))
    }
'''
insert = needle + '''
    suspend fun pairWorkoutExercisesAsBiSet(workoutId: String, firstId: String, secondId: String) = database.withTransaction {
        val ordered = normalizedWorkoutExercises(workoutId)
        persistWorkoutExerciseOrder(BiSetPairing.pair(ordered, firstId, secondId))
    }

    suspend fun unpairWorkoutBiSet(workoutId: String, itemId: String) = database.withTransaction {
        val ordered = normalizedWorkoutExercises(workoutId)
        persistWorkoutExerciseOrder(BiSetPairing.unpair(ordered, itemId))
    }
'''
if needle not in text:
    raise RuntimeError("repository moveBefore block not found")
repository.write_text(text.replace(needle, insert, 1))

# ViewModel surface used directly by WorkoutsScreen.
vm = Path("app/src/main/java/com/liftly/app/ui/AppViewModel.kt")
text = vm.read_text()
needle = '    fun moveWorkoutExerciseBefore(workoutId: String, id: String, beforeId: String) = act { repository.moveWorkoutExerciseBefore(workoutId, id, beforeId); updateTodayWidget() }\n'
insert = needle + '''    fun pairWorkoutExercisesAsBiSet(workoutId: String, firstId: String, secondId: String) =
        act("Bi-set criado.") { repository.pairWorkoutExercisesAsBiSet(workoutId, firstId, secondId); updateTodayWidget() }
    fun unpairWorkoutBiSet(workoutId: String, itemId: String) =
        act("Bi-set desfeito.") { repository.unpairWorkoutBiSet(workoutId, itemId); updateTodayWidget() }
'''
if needle not in text:
    raise RuntimeError("viewmodel moveBefore block not found")
vm.write_text(text.replace(needle, insert, 1))

# Version 1.6.2 / build 40.
for path in ["app/build.gradle.kts", "wear/build.gradle.kts"]:
    p = Path(path)
    text = p.read_text().replace('versionCode = 39', 'versionCode = 40', 1).replace('versionName = "1.6.1"', 'versionName = "1.6.2"', 1)
    p.write_text(text)

# README and changelog.
readme = Path("README.md")
text = readme.read_text()
text = text.replace("## Versão atual — 1.6.1", "## Versão atual — 1.6.2", 1)
text = text.replace("- **versionName:** `1.6.1`", "- **versionName:** `1.6.2`", 1)
text = text.replace("- **versionCode:** `39`", "- **versionCode:** `40`", 1)
marker = "## Novidades da 1.6.1\n"
section = '''## Novidades da 1.6.2

- botão **Bi-set** diretamente em cada exercício na tela Treinos;
- fluxo de dois toques: escolha o exercício A e depois o exercício B;
- o segundo exercício é movido automaticamente para junto do primeiro e ambos viram um par real;
- os cards mostram **BI-SET A/B ↔ parceiro** e oferecem **Desfazer**;
- pares anteriores são desfeitos com segurança antes de criar um novo;
- a execução usa A1 → B1 → descanso → A2 → B2, mantendo a lógica da 1.6.1.

'''
if marker not in text:
    raise RuntimeError("README 1.6.1 marker not found")
readme.write_text(text.replace(marker, section + marker, 1))

changelog = Path("CHANGELOG.md")
text = changelog.read_text()
marker = "# Changelog\n\n"
section = '''## 1.6.2 — 2026-08-10

- Botão Bi-set adicionado diretamente aos cards da área Treinos.
- Pareamento em dois passos permite escolher quaisquer dois exercícios da ficha.
- O segundo exercício é reposicionado ao lado do primeiro e o par fica identificado como A/B.
- É possível desfazer o par pelo mesmo botão.
- Pareamentos anteriores são normalizados antes de criar um novo par.
- `versionName` 1.6.2 / `versionCode` 40.

'''
if marker not in text:
    raise RuntimeError("CHANGELOG marker not found")
changelog.write_text(text.replace(marker, marker + section, 1))

# Keep the normal build workflow aligned with the current APK name.
workflow = Path(".github/workflows/build-apk.yml")
text = workflow.read_text().replace("Liftly-v1.5.5.apk", "Liftly-v1.6.2.apk").replace("name: Liftly-v1.5.5", "name: Liftly-v1.6.2")
workflow.write_text(text)
