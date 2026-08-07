package com.liftly.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liftly.app.LiftlyApplication
import com.liftly.app.data.BodyWeightEntryEntity
import com.liftly.app.data.BodyPhotoEntity
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.LiftlyRepository
import com.liftly.app.data.PreferencesRepository
import com.liftly.app.data.RewardSnapshot
import com.liftly.app.data.ScheduleEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.SessionSummary
import com.liftly.app.data.UserPreferences
import com.liftly.app.data.UserProfileEntity
import com.liftly.app.data.WorkoutEntity
import com.liftly.app.data.WorkoutExerciseEntity
import com.liftly.app.service.WorkoutTrackingService
import com.liftly.app.service.ProgressNotificationManager
import com.liftly.app.integration.discord.DiscordSendResult
import com.liftly.app.integration.discord.DiscordWebhookSender
import com.liftly.app.integration.discord.DiscordWebhookUrlValidator
import com.liftly.app.integration.discord.DiscordWorkoutExport
import com.liftly.app.integration.healthconnect.AndroidHealthConnectRepository
import com.liftly.app.integration.healthconnect.WorkoutHealthExportMapper
import com.liftly.app.integration.healthconnect.WorkoutHealthExportPreparation
import com.liftly.app.domain.WorkoutRewardMetrics
import com.liftly.app.domain.ParsedWorkout
import com.liftly.app.widget.TodayWorkoutWidgetUpdater
import com.liftly.app.ui.theme.PaletteColorCodec
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActionFeedback(val message: String, val isError: Boolean = false, val nonce: Long = System.nanoTime())

data class SessionWarmupRuntimeState(
    val completedStepIds: Set<String> = emptySet(),
    val timerStepId: String? = null,
    val timerEndsAtEpochMillis: Long = 0L,
    val timerIsRest: Boolean = false,
    val timerFollowUpRestSeconds: Int = 0,
)

class AppViewModel(
    application: Application,
    private val repository: LiftlyRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {
    private val started = SharingStarted.WhileSubscribed(5_000)
    val preferences = preferencesRepository.preferences.stateIn(viewModelScope, started, UserPreferences())
    val exercises = repository.exercises.stateIn(viewModelScope, started, emptyList())
    val workouts = repository.workouts.stateIn(viewModelScope, started, emptyList())
    val workoutExercises = repository.workoutExercises.stateIn(viewModelScope, started, emptyList())
    val schedule = repository.schedule.stateIn(viewModelScope, started, emptyList())
    val sessions = repository.sessions.stateIn(viewModelScope, started, emptyList())
    val sessionSets = repository.sessionSets.stateIn(viewModelScope, started, emptyList())
    val weights = repository.weights.stateIn(viewModelScope, started, emptyList())
    val bodyPhotos = repository.bodyPhotos.stateIn(viewModelScope, started, emptyList())
    val profile = repository.profile.stateIn(viewModelScope, started, null)
    val rewards = repository.rewards.stateIn(viewModelScope, started, RewardSnapshot())

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError
    private var lastPreferences = UserPreferences()
    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback
    private val _lastSummary = MutableStateFlow<SessionSummary?>(null)
    val lastSummary: StateFlow<SessionSummary?> = _lastSummary
    private val _automaticWarmupSessions = MutableStateFlow<Set<String>>(emptySet())
    val automaticWarmupSessions: StateFlow<Set<String>> = _automaticWarmupSessions
    private val _sessionWarmupStates =
        MutableStateFlow<Map<String, SessionWarmupRuntimeState>>(emptyMap())
    val sessionWarmupStates: StateFlow<Map<String, SessionWarmupRuntimeState>> =
        _sessionWarmupStates
    private val setUpdateQueue = FifoActionQueue<SetUpdateCommand>(
        scope = viewModelScope,
        process = { command ->
            repository.saveSet(
                command.item,
                command.reps,
                command.load,
                command.toggleCompletion,
                command.rir,
                command.painLevel,
            )
            command.successMessage?.let { _feedback.value = ActionFeedback(it) }
        },
        onFailure = { _, error ->
            _feedback.value = ActionFeedback(
                error.message ?: "Não foi possível salvar a série.",
                true,
            )
        },
    )
    private val healthConnectRepository = AndroidHealthConnectRepository(application)

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                lastPreferences = prefs
                if (!_ready.value && _initializationError.value == null) initializeApplication(prefs.demoEnabled)
            }
        }
    }

    private suspend fun initializeApplication(useDemo: Boolean) {
        runCatching { repository.initialize(useDemo) }
            .onSuccess {
                _initializationError.value = null
                _ready.value = true
                updateTodayWidget()
            }
            .onFailure {
                _initializationError.value = it.message ?: "Falha ao preparar os dados locais."
                _ready.value = false
            }
    }

    fun retryInitialization() {
        if (_ready.value) return
        _initializationError.value = null
        viewModelScope.launch { initializeApplication(lastPreferences.demoEnabled) }
    }

    fun finishOnboarding(useDemo: Boolean) = act("Tudo pronto para treinar!") {
        repository.initialize(useDemo)
        preferencesRepository.finishOnboarding(useDemo)
        updateTodayWidget()
    }

    fun setTheme(value: String) = act { preferencesRepository.setTheme(value) }
    fun setHaptics(value: Boolean) = act { preferencesRepository.setHaptics(value) }
    fun setRestTimer(value: Boolean) = act { preferencesRepository.setRestTimer(value) }
    fun setRestEndVibration(value: Boolean) = act { preferencesRepository.setRestEndVibration(value) }
    fun setRestEndSound(value: Boolean) = act { preferencesRepository.setRestEndSound(value) }
    fun setRestEndSoundType(value: String) = act { preferencesRepository.setRestEndSoundType(value) }
    fun setRestEndSoundDurationSeconds(value: Int) = act { preferencesRepository.setRestEndSoundDurationSeconds(value) }
    fun setExerciseFilters(value: String) = act { preferencesRepository.setFilters(value) }
    fun setHideBodyMetrics(value: Boolean) = act { preferencesRepository.setHideBodyMetrics(value) }
    fun setProfilePhotoUri(value: String) = act(if (value.isBlank()) "Foto removida." else "Foto de perfil atualizada.") {
        val previousUri = lastPreferences.profilePhotoUri
        preferencesRepository.setProfilePhotoUri(value)
        if (previousUri.isNotBlank() && previousUri != value) {
            val stillUsed = previousUri == lastPreferences.customWallpaperUri ||
                repository.allBodyPhotos().any { it.imageUri == previousUri }
            if (!stillUsed) releasePhotoPermission(previousUri)
        }
    }
    fun setCustomWallpaperUri(value: String) = act(if (value.isBlank()) "Wallpaper removido." else "Wallpaper aplicado.") {
        val previousUri = lastPreferences.customWallpaperUri
        preferencesRepository.setCustomWallpaperUri(value)
        if (previousUri.isNotBlank() && previousUri != value) {
            val stillUsed = previousUri == lastPreferences.profilePhotoUri ||
                repository.allBodyPhotos().any { it.imageUri == previousUri }
            if (!stillUsed) releasePhotoPermission(previousUri)
        }
    }
    fun setCustomWallpaperEnabled(value: Boolean) = act {
        require(!value || lastPreferences.customWallpaperUri.isNotBlank()) { "Escolha uma imagem primeiro." }
        preferencesRepository.setCustomWallpaperEnabled(value)
    }
    fun setWallpaperDimPercent(value: Int) = act { preferencesRepository.setWallpaperDimPercent(value) }
    fun setCustomPaletteColors(
        primary: String,
        secondary: String,
        background: String,
        surface: String,
        text: String,
    ) = act("Paleta personalizada aplicada.") {
        val normalizedPrimary = PaletteColorCodec.normalize(primary)
        val normalizedSecondary = PaletteColorCodec.normalize(secondary)
        val normalizedBackground = PaletteColorCodec.normalize(background)
        val normalizedSurface = PaletteColorCodec.normalize(surface)
        val normalizedText = PaletteColorCodec.normalize(text)
        require(
            listOf(
                normalizedPrimary,
                normalizedSecondary,
                normalizedBackground,
                normalizedSurface,
                normalizedText,
            ).all { it != null }
        ) { "Use cores hexadecimais válidas, por exemplo #BD5CFF." }
        preferencesRepository.setCustomPalette(
            primary = requireNotNull(normalizedPrimary),
            secondary = requireNotNull(normalizedSecondary),
            background = requireNotNull(normalizedBackground),
            surface = requireNotNull(normalizedSurface),
            text = requireNotNull(normalizedText),
        )
    }
    fun setCustomPaletteEnabled(value: Boolean) = act {
        if (value) {
            require(
                listOf(
                    lastPreferences.customPrimaryColor,
                    lastPreferences.customSecondaryColor,
                    lastPreferences.customBackgroundColor,
                    lastPreferences.customSurfaceColor,
                    lastPreferences.customTextColor,
                ).all { PaletteColorCodec.normalize(it) != null }
            ) { "Escolha e aplique todas as cores primeiro." }
        }
        preferencesRepository.setCustomPaletteEnabled(value)
    }
    fun resetCustomPalette() = act("Cores originais do tema restauradas.") {
        preferencesRepository.resetCustomPalette()
    }
    fun setWeeklyWorkoutGoal(value: Int) = act("Meta semanal atualizada.") {
        preferencesRepository.setWeeklyWorkoutGoal(value)
    }
    fun setGoalNotifications(value: Boolean) = act { preferencesRepository.setGoalNotifications(value) }
    fun setStreakNotifications(value: Boolean) = act { preferencesRepository.setStreakNotifications(value) }
    fun setDiscordWebhookEnabled(value: Boolean) = act {
        if (value) require(DiscordWebhookUrlValidator.isValid(lastPreferences.discordWebhookUrl)) {
            "Salve um webhook válido antes de ativar a exportação."
        }
        preferencesRepository.setDiscordWebhookEnabled(value)
    }
    fun setDiscordWebhookUrl(value: String) = act(if (value.isBlank()) "Webhook removido." else "Webhook salvo somente neste aparelho.") {
        require(value.isBlank() || DiscordWebhookUrlValidator.isValid(value)) {
            "Use uma URL HTTPS de webhook oficial do Discord."
        }
        preferencesRepository.setDiscordWebhookUrl(value)
        if (value.isBlank()) preferencesRepository.setDiscordWebhookEnabled(false)
    }

    fun testDiscordWebhook(value: String) {
        viewModelScope.launch {
            val url = value.trim()
            if (!DiscordWebhookUrlValidator.isValid(url)) {
                _feedback.value = ActionFeedback("Webhook do Discord inválido.", true)
                return@launch
            }
            val json = """{"username":"Liftly","content":"✅ Integração configurada. Os próximos treinos finalizados poderão ser enviados automaticamente."}"""
            _feedback.value = when (DiscordWebhookSender().send(url, json)) {
                is DiscordSendResult.Success -> ActionFeedback("Teste enviado ao Discord com sucesso.")
                is DiscordSendResult.RetryableFailure -> ActionFeedback("Discord ou internet indisponível. Tente novamente.", true)
                is DiscordSendResult.PermanentFailure -> ActionFeedback("O Discord recusou esse webhook. Revise a URL.", true)
            }
        }
    }

    fun saveWorkout(name: String, description: String, days: Set<DayOfWeek>, existing: WorkoutEntity? = null) =
        act("Treino salvo.") {
            require(name.isNotBlank()) { "Dê um nome ao treino." }
            if (existing == null) repository.createWorkout(name, description, days)
            else repository.saveWorkout(existing.copy(name = name.trim(), description = description.trim(), weekDays = days.joinToString(",") { it.value.toString() }))
            updateTodayWidget()
        }

    fun duplicateWorkout(workout: WorkoutEntity) = act("Treino duplicado.") { repository.duplicateWorkout(workout); updateTodayWidget() }
    fun archiveWorkout(id: String) = act("Treino arquivado.") { repository.archiveWorkout(id); updateTodayWidget() }
    fun deleteWorkout(id: String) = act("Treino excluído.") { repository.deleteWorkout(id); updateTodayWidget() }

    fun addExercise(workoutId: String, exerciseId: String, sets: Int, repMin: Int, repMax: Int, load: Double, rest: Int, type: String, notes: String, exerciseName: String? = null) =
        act("Exercício adicionado.") { repository.addExerciseToWorkout(workoutId, exerciseId, sets, repMin, repMax, load, rest, type, notes, exerciseName); updateTodayWidget() }

    fun updateWorkoutExercise(item: WorkoutExerciseEntity, exerciseName: String? = null) = act("Configuração salva.") { repository.updateWorkoutExercise(item, exerciseName); updateTodayWidget() }
    fun removeWorkoutExercise(id: String) = act("Exercício removido.") { repository.removeWorkoutExercise(id); updateTodayWidget() }
    fun moveWorkoutExercise(workoutId: String, id: String, direction: Int) = act { repository.moveWorkoutExercise(workoutId, id, direction); updateTodayWidget() }
    fun moveWorkoutExerciseBefore(workoutId: String, id: String, beforeId: String) = act { repository.moveWorkoutExerciseBefore(workoutId, id, beforeId); updateTodayWidget() }

    fun saveCustomExercise(exercise: ExerciseEntity) = act("Exercício personalizado salvo.") { repository.saveExercise(exercise) }
    fun renameExercise(id: String, name: String) = act("Nome do exercício atualizado.") { repository.renameExercise(id, name) }
    fun toggleFavorite(exercise: ExerciseEntity) = act { repository.toggleFavorite(exercise) }
    fun deleteCustomExercise(exercise: ExerciseEntity) = act("Exercício removido.") { repository.deleteCustomExercise(exercise) }

    fun scheduleWorkout(date: LocalDate, workoutId: String) = act("Programação atualizada.") { repository.scheduleWorkout(date, workoutId); updateTodayWidget() }
    fun setRestDay(date: LocalDate) = act("Dia de descanso definido.") { repository.setRestDay(date); updateTodayWidget() }
    fun removeSchedule(id: String) = act { repository.removeSchedule(id); updateTodayWidget() }
    fun setScheduleStatus(item: ScheduleEntity, status: String) = act("Status atualizado.") { repository.setScheduleStatus(item, status); updateTodayWidget() }
    fun copyWeek(source: LocalDate, target: LocalDate) = act("Semana copiada.") { repository.copyWeek(source, target); updateTodayWidget() }

    fun startSession(
        workoutId: String,
        isTestMode: Boolean = false,
        automaticWarmup: Boolean = false,
        onStarted: (String) -> Unit,
        onFailure: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                if (activeSession() == null) WorkoutTrackingService.stop(getApplication<Application>())
                repository.startSession(workoutId, isTestMode)
            }.onSuccess { sessionId ->
                _automaticWarmupSessions.value = if (automaticWarmup) {
                    _automaticWarmupSessions.value + sessionId
                } else {
                    _automaticWarmupSessions.value - sessionId
                }
                onStarted(sessionId)
            }.onFailure { error ->
                _feedback.value = ActionFeedback(
                    error.message ?: "Não foi possível iniciar o treino.",
                    true,
                )
                onFailure?.invoke()
            }
        }
    }

    fun startTestSession(workoutId: String, onStarted: (String) -> Unit) =
        startSession(workoutId, isTestMode = true, onStarted = onStarted)

    fun saveSet(
        item: SessionSetEntity,
        reps: Int,
        load: Double,
        toggleCompletion: Boolean = false,
        rir: Int? = item.rir,
        painLevel: Int = item.painLevel,
    ) {
        val successMessage = if (!toggleCompletion) null
        else if (item.completed) "Marcação desfeita."
        else "Série concluída."
        val enqueued = setUpdateQueue.tryEnqueue(
            SetUpdateCommand(
                item = item,
                reps = reps,
                load = load,
                toggleCompletion = toggleCompletion,
                rir = rir,
                painLevel = painLevel,
                successMessage = successMessage,
            )
        )
        if (!enqueued) {
            _feedback.value = ActionFeedback("Não foi possível enfileirar a alteração da série.", true)
        }
    }

    fun substituteSessionExercise(
        sessionId: String,
        workoutExerciseId: String,
        replacementExerciseId: String,
    ) = act("Exercício substituído somente neste treino. Confira a carga antes de continuar.") {
        setUpdateQueue.awaitIdle()
        repository.substituteSessionExercise(
            sessionId = sessionId,
            workoutExerciseId = workoutExerciseId,
            replacementExerciseId = replacementExerciseId,
        )
    }

    fun finishSession(sessionId: String, onFinished: () -> Unit) = act {
        val sessionBeforeFinish = sessions.value.firstOrNull { it.id == sessionId }
        // A completion tap can happen immediately after the final load/RIR edit. The barrier
        // guarantees that every accepted set update is committed before the session is read.
        setUpdateQueue.awaitIdle()
        val setsBeforeFinish = sessionSets.value.filter { it.sessionId == sessionId }
        val summary = repository.finishSession(sessionId)
        clearSessionRuntimeState(sessionId)
        WorkoutTrackingService.stop(getApplication<Application>())
        _lastSummary.value = summary
        _feedback.value = ActionFeedback(
            if (summary.isTestMode) "Teste finalizado sem alterar seu progresso."
            else buildString {
                append("Treino registrado!")
                if (summary.rewardXp > 0 || summary.rewardCoins > 0) {
                    append(" +${summary.rewardXp} XP • +${summary.rewardCoins} Lift Coins")
                }
            }
        )
        if (!summary.isTestMode) {
            sessionBeforeFinish?.copy(finishedAt = summary.finishedAt)?.let { completedSession ->
                when (val preparation = WorkoutHealthExportMapper.prepare(completedSession, setsBeforeFinish)) {
                    is WorkoutHealthExportPreparation.Ready -> {
                        viewModelScope.launch {
                            runCatching {
                                healthConnectRepository.exportWorkout(preparation.payload)
                            }
                        }
                    }
                    is WorkoutHealthExportPreparation.Skipped -> Unit
                }
            }
            val momentum = repository.trainingMomentum(lastPreferences.weeklyWorkoutGoal)
            ProgressNotificationManager.notifyAfterWorkout(
                getApplication<Application>(),
                momentum,
                lastPreferences,
            )
            if (lastPreferences.discordWebhookEnabled) {
                DiscordWorkoutExport.enqueue(
                    getApplication<Application>(),
                    lastPreferences.discordWebhookUrl,
                    summary.sessionId,
                )
            }
            updateTodayWidget()
        }
        onFinished()
    }

    fun deleteHistoricalSession(sessionId: String) =
        act("Treino removido do histórico.") { repository.deleteHistoricalSession(sessionId) }

    fun deleteHistoryForDate(date: LocalDate) = act {
        val removed = repository.deleteHistoryForDate(date)
        _feedback.value = ActionFeedback(
            if (removed == 0) "Não há treinos concluídos nessa data."
            else if (removed == 1) "Treino do dia removido do histórico."
            else "$removed treinos do dia removidos do histórico."
        )
    }

    fun saveWeight(value: Double, notes: String = "") = act("Peso registrado.") { repository.saveWeight(value, notes) }

    fun purchaseRewardItem(itemId: String) = act("Item adquirido e adicionado à sua coleção.") {
        repository.purchaseRewardItem(itemId)
    }

    fun equipRewardItem(itemId: String) = act("Personalização aplicada.") {
        repository.equipRewardItem(itemId)
    }

    fun unequipRewardSlot(slot: String) = act("Personalização removida.") {
        repository.unequipRewardSlot(slot)
    }

    fun adminGrantRewards(xp: Long, coins: Long) = act("Saldo de teste atualizado.") {
        repository.adminGrantRewards(xp, coins)
    }

    fun adminUnlockAllRewards() = act("Catálogo liberado para teste.") {
        repository.adminUnlockAllRewards()
    }

    fun adminSimulateRewardWorkout(mode: String) = act("Treino simulado e recompensas processadas.") {
        val metrics = when (mode.uppercase()) {
            "PR" -> WorkoutRewardMetrics(4, 4, 4, personalRecords = 1)
            "COMPLETE" -> WorkoutRewardMetrics(4, 4, 4)
            else -> WorkoutRewardMetrics(3, 5, 2)
        }
        repository.adminSimulateWorkout(metrics)
    }

    fun adminCompleteRewardMission(missionId: String) = act("Missão concluída no modo administrador.") {
        repository.adminCompleteRewardMission(missionId)
    }

    fun adminResetCurrentRewardMissions() = act("Missões atuais reiniciadas para teste.") {
        repository.adminResetCurrentRewardMissions()
    }

    fun adminResetRewardEconomy() = act("Economia de recompensas reiniciada.") {
        repository.adminResetRewardEconomy()
    }
    fun updateWeight(entry: BodyWeightEntryEntity, value: Double, notes: String = "") =
        act("Peso atualizado.") { repository.updateWeight(entry.id, value, notes) }
    fun deleteWeight(id: String) = act { repository.deleteWeight(id) }
    fun saveBodyPhoto(imageUri: String, notes: String = "") = act("Foto de evolução adicionada.") {
        repository.saveBodyPhoto(imageUri, notes)
    }
    fun deleteBodyPhoto(photo: BodyPhotoEntity) = act("Foto de evolução removida.") {
        repository.deleteBodyPhoto(photo.id)
        val stillUsed = repository.allBodyPhotos().any { it.imageUri == photo.imageUri } ||
            lastPreferences.profilePhotoUri == photo.imageUri ||
            lastPreferences.customWallpaperUri == photo.imageUri
        if (!stillUsed) releasePhotoPermission(photo.imageUri)
    }
    fun saveProfile(value: UserProfileEntity) = act("Perfil salvo.") { repository.saveProfile(value) }

    suspend fun createWorkoutExport(workoutId: String): Result<String> =
        runCatching { repository.exportWorkout(workoutId) }

    fun importWorkoutExport(payload: String, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.importWorkout(payload) }
                .onSuccess { workoutId ->
                    updateTodayWidget()
                    _feedback.value = ActionFeedback("Treino importado e adicionado ao final da lista.")
                    onImported(workoutId)
                }
                .onFailure { error ->
                    _feedback.value = ActionFeedback(
                        error.message ?: "Não foi possível importar este treino.",
                        true,
                    )
                }
        }
    }

    fun importWorkoutText(workouts: List<ParsedWorkout>, onImported: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.importTextWorkouts(workouts) }
                .onSuccess { importedIds ->
                    updateTodayWidget()
                    _feedback.value = ActionFeedback(
                        if (importedIds.size == 1) "Treino montado a partir do texto."
                        else "${importedIds.size} treinos montados a partir do texto."
                    )
                    importedIds.firstOrNull()?.let(onImported)
                }
                .onFailure { error ->
                    _feedback.value = ActionFeedback(
                        error.message ?: "Não foi possível montar os treinos deste texto.",
                        true,
                    )
                }
        }
    }

    suspend fun createBackup(): Result<String> = runCatching { repository.backupJson() }
    fun importBackup(json: String) = act("Backup importado com sucesso.") {
        val previousPhotoUris = repository.allBodyPhotos().map { it.imageUri }
        repository.importJson(json)
        clearAllSessionRuntimeState()
        val restoredPhotoUris = repository.allBodyPhotos().mapTo(mutableSetOf()) { it.imageUri }
        previousPhotoUris
            .filterNot {
                it in restoredPhotoUris ||
                    it == lastPreferences.profilePhotoUri ||
                    it == lastPreferences.customWallpaperUri
            }
            .distinct()
            .forEach(::releasePhotoPermission)
        WorkoutTrackingService.stop(getApplication<Application>())
        updateTodayWidget()
    }
    fun deleteAllData() = act("Todos os dados pessoais foram apagados.") {
        val photoUris = repository.allBodyPhotos().map { it.imageUri } +
            lastPreferences.profilePhotoUri +
            lastPreferences.customWallpaperUri
        repository.deleteAllData()
        clearAllSessionRuntimeState()
        WorkoutTrackingService.stop(getApplication<Application>())
        preferencesRepository.reset()
        photoUris.filter(String::isNotBlank).distinct().forEach(::releasePhotoPermission)
        updateTodayWidget()
    }
    fun resetProgress() = act("Progresso de treinos resetado.") {
        repository.resetProgress()
        clearAllSessionRuntimeState()
        WorkoutTrackingService.stop(getApplication<Application>())
    }

    fun updateSessionWarmupState(
        sessionId: String,
        transform: (SessionWarmupRuntimeState) -> SessionWarmupRuntimeState,
    ) {
        _sessionWarmupStates.update { states ->
            states + (sessionId to transform(states[sessionId] ?: SessionWarmupRuntimeState()))
        }
    }

    fun clearSessionRuntimeState(sessionId: String) {
        _automaticWarmupSessions.value = _automaticWarmupSessions.value - sessionId
        _sessionWarmupStates.update { it - sessionId }
    }

    private fun clearAllSessionRuntimeState() {
        _automaticWarmupSessions.value = emptySet()
        _sessionWarmupStates.value = emptyMap()
    }

    fun clearFeedback() { _feedback.value = null }
    fun reportFeedback(message: String, isError: Boolean = false) {
        _feedback.value = ActionFeedback(message, isError)
    }

    fun workout(id: String): WorkoutEntity? = workouts.value.firstOrNull { it.id == id }
    fun exercise(id: String): ExerciseEntity? = exercises.value.firstOrNull { it.id == id }
    fun itemsForWorkout(id: String): List<WorkoutExerciseEntity> = workoutExercises.value.filter { it.workoutId == id }.sortedBy { it.orderIndex }
    fun setsForSession(id: String): List<SessionSetEntity> = sessionSets.value
        .filter { it.sessionId == id }
        .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber })
    fun activeSession(): SessionEntity? = sessions.value.firstOrNull { it.status == "Em andamento" }
    fun schedulesFor(date: LocalDate): List<ScheduleEntity> = schedule.value.filter { it.date == date.toString() }

    private fun releasePhotoPermission(rawUri: String) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(rawUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun updateTodayWidget() {
        TodayWorkoutWidgetUpdater.requestUpdate(getApplication<Application>())
    }

    private fun act(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { if (success != null) _feedback.value = ActionFeedback(success) }
                .onFailure { _feedback.value = ActionFeedback(it.message ?: "Não foi possível concluir a ação.", true) }
        }
    }

    override fun onCleared() {
        setUpdateQueue.close()
        super.onCleared()
    }

    private data class SetUpdateCommand(
        val item: SessionSetEntity,
        val reps: Int,
        val load: Double,
        val toggleCompletion: Boolean,
        val rir: Int?,
        val painLevel: Int,
        val successMessage: String?,
    )

    companion object {
        fun factory(app: LiftlyApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app, app.repository, app.preferencesRepository) as T
        }
    }
}
