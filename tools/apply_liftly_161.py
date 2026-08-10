from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(relative, old, new):
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"pattern not found in {relative}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def prepend_after(relative, marker, insertion):
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    if insertion.strip() in text:
        return
    if marker not in text:
        raise RuntimeError(f"marker not found in {relative}: {marker!r}")
    path.write_text(text.replace(marker, marker + insertion, 1), encoding="utf-8")


# Version: patch release for session regressions.
for gradle in ["app/build.gradle.kts", "wear/build.gradle.kts"]:
    replace_once(gradle, 'versionCode = 38\n        versionName = "1.6.0"', 'versionCode = 39\n        versionName = "1.6.1"')

# Restore safe propagation of the first working-set load to untouched future sets.
prepend_after(
    "app/src/main/java/com/liftly/app/data/LiftlyRepository.kt",
    "import com.liftly.app.domain.ProgressionCoachInput\n",
    "import com.liftly.app.domain.SessionLoadPropagation\n",
)
replace_once(
    "app/src/main/java/com/liftly/app/data/LiftlyRepository.kt",
    '''        dao.upsertSessionSet(current.copy(\n            reps = value,\n            loadKg = load.coerceAtLeast(0.0),\n            completed = completed,\n            completedAt = when {\n                !toggleCompletion -> current.completedAt\n                completed -> current.completedAt ?: System.currentTimeMillis()\n                else -> null\n            },\n            durationSeconds = if (current.trackingMode == "Tempo") value else current.durationSeconds,\n            distanceMeters = if (current.trackingMode == "Distância") value.toDouble() else current.distanceMeters,\n            rir = rir?.coerceIn(0, 10),\n            painLevel = painLevel.coerceIn(0, 10)\n        ))\n''',
    '''        val normalizedLoad = load.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0\n        val updatedCurrent = current.copy(\n            reps = value,\n            loadKg = normalizedLoad,\n            completed = completed,\n            completedAt = when {\n                !toggleCompletion -> current.completedAt\n                completed -> current.completedAt ?: System.currentTimeMillis()\n                else -> null\n            },\n            durationSeconds = if (current.trackingMode == "Tempo") value else current.durationSeconds,\n            distanceMeters = if (current.trackingMode == "Distância") value.toDouble() else current.distanceMeters,\n            rir = rir?.coerceIn(0, 10),\n            painLevel = painLevel.coerceIn(0, 10)\n        )\n        dao.upsertSessionSet(updatedCurrent)\n\n        if (SessionLoadPropagation.changedFirstWorkingSet(current, normalizedLoad)) {\n            val inherited = dao.sessionSets(current.sessionId)\n                .filter { sibling -> SessionLoadPropagation.shouldInherit(current, sibling) }\n                .map { sibling -> sibling.copy(loadKg = normalizedLoad) }\n            if (inherited.isNotEmpty()) dao.upsertSessionSets(inherited)\n        }\n''',
)

# Restore bi-set execution order while keeping the 1.6 coach/history improvements.
prepend_after(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    "import com.liftly.app.domain.ProgressionStatus\n",
    "import com.liftly.app.domain.SupersetPlanner\n",
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''    val sessionWorkoutItems = remember(session?.workoutId, workoutItems) {\n        workoutItems.filter { it.workoutId == session?.workoutId }.sortedBy { it.orderIndex }\n    }\n    val automaticWarmupPlan = remember(\n''',
    '''    val sessionWorkoutItems = remember(session?.workoutId, workoutItems) {\n        workoutItems.filter { it.workoutId == session?.workoutId }.sortedBy { it.orderIndex }\n    }\n    val supersetMemberships = remember(sessionWorkoutItems) { SupersetPlanner.memberships(sessionWorkoutItems) }\n    val executionSequence = remember(sets, sessionWorkoutItems) {\n        SupersetPlanner.sequence(sets, sessionWorkoutItems)\n    }\n    val automaticWarmupPlan = remember(\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''    val nextWorkSet = sets.firstOrNull { !it.completed }\n    val visibleExerciseGroups = nextWorkSet?.workoutExerciseId?.let { focusedId ->\n        orderedExerciseGroups.filter { it.first().workoutExerciseId == focusedId }\n    } ?: orderedExerciseGroups\n''',
    '''    val nextWorkSet = executionSequence.firstOrNull { !it.completed }\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    "            visibleExerciseGroups.forEachIndexed { groupIndex, exerciseSets ->\n",
    "            orderedExerciseGroups.forEachIndexed { groupIndex, exerciseSets ->\n",
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''                val isFirstGroupForWorkoutItem = visibleExerciseGroups\n                    .indexOfFirst { it.first().workoutExerciseId == workoutExerciseId } == groupIndex\n''',
    '''                val isFirstGroupForWorkoutItem = orderedExerciseGroups\n                    .indexOfFirst { it.first().workoutExerciseId == workoutExerciseId } == groupIndex\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''                        if (exerciseSets.any { !it.completed } && equipmentFamilies.isNotEmpty()) {\n''',
    '''                        supersetMemberships[workoutExerciseId]?.let { membership ->\n                            val partnerName = sessionWorkoutItems\n                                .firstOrNull { it.id == membership.partnerWorkoutExerciseId }\n                                ?.exerciseId\n                                ?.let { partnerExerciseId -> exercises.firstOrNull { it.id == partnerExerciseId }?.name }\n                                ?: "exercício parceiro"\n                            Surface(\n                                shape = MaterialTheme.shapes.small,\n                                color = MaterialTheme.colorScheme.secondaryContainer,\n                            ) {\n                                Text(\n                                    "BI-SET ${if (membership.position == 1) "A" else "B"} • $partnerName",\n                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),\n                                    style = MaterialTheme.typography.labelMedium,\n                                    color = MaterialTheme.colorScheme.onSecondaryContainer,\n                                    fontWeight = FontWeight.Bold,\n                                )\n                            }\n                        }\n                        if (exerciseSets.any { !it.completed } && equipmentFamilies.isNotEmpty()) {\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''                val visibleSets = exerciseSets.filter { it.completed || it.id == nextWorkSet?.id }\n                items(visibleSets, key = { it.id }) { set ->\n''',
    '''                items(exerciseSets, key = { it.id }) { set ->\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''                        if (check && !set.completed && preferences.restTimer) {\n                            val restSeconds = workoutItems\n                                .firstOrNull { it.id == set.workoutExerciseId }\n                                ?.restSeconds\n                                ?.coerceIn(0, 3_600)\n                                ?: 60\n                            if (restSeconds > 0) {\n                                restEndsAt = System.currentTimeMillis() + restSeconds * 1_000L\n                                val nextExerciseName = sets\n                                    .firstOrNull { candidate -> !candidate.completed && candidate.id != set.id }\n                                    ?.exerciseName\n                                    ?: "Todas as séries concluídas"\n                                WorkoutTrackingService.startRest(\n                                    context = context,\n                                    exerciseName = nextExerciseName,\n                                    durationSeconds = restSeconds,\n                                    workoutName = session?.workoutName.orEmpty(),\n                                    vibrateOnFinish = preferences.restEndVibration,\n                                    playSoundOnFinish = preferences.restEndSound,\n                                    soundId = preferences.restEndSoundType,\n                                    soundDurationSeconds = preferences.restEndSoundDurationSeconds,\n                                )\n                            }\n                        }\n''',
    '''                        if (check && !set.completed && preferences.restTimer) {\n                            val restSeconds = SupersetPlanner.restSecondsAfter(set.workoutExerciseId, sessionWorkoutItems)\n                            if (restSeconds == null) {\n                                // Bi-set A: segue direto para o parceiro, sem descanso intermediário.\n                                restEndsAt = 0L\n                                WorkoutTrackingService.cancelRest(\n                                    context = context,\n                                    exerciseName = set.exerciseName,\n                                    workoutName = session?.workoutName.orEmpty(),\n                                )\n                            } else if (restSeconds > 0) {\n                                restEndsAt = System.currentTimeMillis() + restSeconds * 1_000L\n                                val nextExerciseName = executionSequence\n                                    .firstOrNull { candidate -> !candidate.completed && candidate.id != set.id }\n                                    ?.exerciseName\n                                    ?: "Todas as séries concluídas"\n                                WorkoutTrackingService.startRest(\n                                    context = context,\n                                    exerciseName = nextExerciseName,\n                                    durationSeconds = restSeconds,\n                                    workoutName = session?.workoutName.orEmpty(),\n                                    vibrateOnFinish = preferences.restEndVibration,\n                                    playSoundOnFinish = preferences.restEndSound,\n                                    soundId = preferences.restEndSoundType,\n                                    soundDurationSeconds = preferences.restEndSoundDurationSeconds,\n                                )\n                            }\n                        }\n''',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/TodayScreen.kt",
    '''        subtitle = if (isFocus) "SÉRIE ATUAL" else "Série concluída",\n''',
    '''        subtitle = when {\n            set.completed -> "SÉRIE CONCLUÍDA"\n            isFocus -> "SÉRIE ATUAL"\n            else -> "SÉRIE PLANEJADA"\n        },\n''',
)

# Make the bi-set control explicit in workout configuration; old Supersérie values remain compatible.
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/WorkoutsScreen.kt",
    'var setType by remember(item) { mutableStateOf(item?.setType ?: "Normal") }',
    'var setType by remember(item) { mutableStateOf(if (item?.setType?.contains("supers", ignoreCase = true) == true) "Bi-set" else item?.setType ?: "Normal") }',
)
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/WorkoutsScreen.kt",
    'items(listOf("Normal", "Aquecimento", "Dropset", "Supersérie")) { option ->',
    'items(listOf("Normal", "Aquecimento", "Dropset", "Bi-set")) { option ->',
)

# Docs.
replace_once("README.md", "## Versão atual — 1.6.0", "## Versão atual — 1.6.1")
replace_once("README.md", "- **versionName:** `1.6.0`\n- **versionCode:** `38`", "- **versionName:** `1.6.1`\n- **versionCode:** `39`")
replace_once(
    "README.md",
    "## Novidades da 1.6.0\n\nA 1.6.0 aprofunda o Liftly como sistema de treino:\n",
    "## Novidades da 1.6.1\n\nA 1.6.1 corrige regressões da sessão sem remover as melhorias da 1.6.0:\n\n- todas as séries voltam a ficar visíveis para planejamento, com a próxima destacada;\n- carga digitada na primeira série é herdada pelas séries seguintes ainda intactas;\n- Bi-set volta a aparecer explicitamente e executa A → B antes do descanso;\n- o seletor de tipo de série volta a exibir Bi-set diretamente.\n\n## Novidades da 1.6.0\n\nA 1.6.0 aprofunda o Liftly como sistema de treino:\n",
)
replace_once(
    "README.md",
    "- sessão focada em uma série por vez, com histórico anterior visível;",
    "- sessão com histórico anterior e destaque da próxima série; todas as séries permanecem visíveis para planejamento;",
)
prepend_after(
    "CHANGELOG.md",
    "# Changelog\n",
    "\n## 1.6.1 — 2026-08-10\n\n- Restaurada a propagação da carga da primeira série para séries futuras ainda intactas.\n- Todas as séries voltam a ficar visíveis durante a sessão; a próxima permanece destacada.\n- Bi-set restaurado com indicação A/B, execução intercalada e descanso após o exercício B.\n- Tipo de série passa a exibir Bi-set explicitamente no editor.\n- `versionName` 1.6.1 / `versionCode` 39.\n",
)

patch_notes = ROOT / "docs/PATCH_NOTES_1.6.1.md"
patch_notes.write_text(
    """# Liftly 1.6.1\n\n## Sessão\n\n- Todas as séries do treino permanecem visíveis para permitir planejamento.\n- A próxima série continua destacada como `AGORA`; séries futuras são marcadas como planejadas.\n- A carga digitada na primeira série de um exercício é copiada para séries seguintes que ainda estejam intactas.\n- Séries concluídas ou já editadas manualmente não são sobrescritas.\n\n## Bi-set\n\n- O controle `Bi-set` volta a aparecer no editor da ficha.\n- Dois exercícios consecutivos marcados como Bi-set/Supersérie formam um par A/B.\n- A execução é intercalada por rodada: A1 → B1 → descanso → A2 → B2.\n- A sessão identifica visualmente `BI-SET A` e `BI-SET B`.\n\n## Versão\n\n- `versionName`: 1.6.1\n- `versionCode`: 39\n- `applicationId`: com.liftly.app\n""",
    encoding="utf-8",
)

print("Liftly 1.6.1 session fixes applied")
