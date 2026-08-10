package com.liftly.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.liftly.app.LiftlyApplication
import com.liftly.app.data.RewardSlots
import com.liftly.app.domain.TrainingMomentumCalculator
import com.liftly.app.integration.spotify.MusicConfigSource
import com.liftly.app.integration.spotify.MusicRefreshIssue
import com.liftly.app.integration.spotify.MusicState
import com.liftly.app.integration.spotify.PersonalSpotifyPlaylist
import com.liftly.app.integration.spotify.SpotifyLauncher
import com.liftly.app.integration.spotify.SpotifyPlaylistLinks
import com.liftly.app.ui.components.LiftlyBackground
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.rewards.MissionPeriod
import com.liftly.app.ui.rewards.RewardCategory
import com.liftly.app.ui.rewards.RewardCosmetics
import com.liftly.app.ui.rewards.RewardsActions
import com.liftly.app.ui.rewards.RewardsScreen
import com.liftly.app.ui.rewards.toUiState
import com.liftly.app.ui.screens.CalendarScreen
import com.liftly.app.ui.screens.ExercisesScreen
import com.liftly.app.ui.screens.MusicScreen
import com.liftly.app.ui.screens.MusicScreenState
import com.liftly.app.ui.screens.OnboardingScreen
import com.liftly.app.ui.screens.ProfileScreen
import com.liftly.app.ui.screens.ProgressScreen
import com.liftly.app.ui.screens.SessionScreen
import com.liftly.app.ui.screens.StopwatchScreen
import com.liftly.app.ui.screens.TodayScreen
import com.liftly.app.ui.screens.WorkoutsScreen
import com.liftly.app.ui.theme.LiftlyCustomPalette
import com.liftly.app.ui.theme.LiftlyTheme
import com.liftly.app.ui.theme.isLiftlyBackgroundLight
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val mainDestinations = listOf(
    MainDestination("today", "Hoje", Icons.Default.Home),
    MainDestination("workouts", "Treinos", Icons.Default.CalendarToday),
    MainDestination("exercises", "Exercícios", Icons.Default.FitnessCenter),
    MainDestination("progress", "Progresso", Icons.Default.Insights),
    MainDestination("profile", "Perfil", Icons.Default.Person),
)

@Composable
fun LiftlyApp(vm: AppViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val rewardSnapshot by vm.rewards.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val initializationError by vm.initializationError.collectAsStateWithLifecycle()
    val view = LocalView.current
    val preferencePalette = LiftlyCustomPalette(
        enabled = prefs.customPaletteEnabled,
        primary = prefs.customPrimaryColor,
        secondary = prefs.customSecondaryColor,
        background = prefs.customBackgroundColor,
        surface = prefs.customSurfaceColor,
        text = prefs.customTextColor,
    )
    val equippedThemeAssetKey = rewardSnapshot.store
        .firstOrNull { it.equipped && it.item.slot == RewardSlots.THEME }
        ?.item
        ?.assetKey
    val equippedWallpaperAssetKey = rewardSnapshot.store
        .firstOrNull { it.equipped && it.item.slot == RewardSlots.WALLPAPER }
        ?.item
        ?.assetKey
    val equippedRestSoundAssetKey = rewardSnapshot.store
        .firstOrNull { it.equipped && it.item.slot == RewardSlots.REST_SOUND }
        ?.item
        ?.assetKey
    val equippedRestSoundId = RewardCosmetics.restSoundId(equippedRestSoundAssetKey)
    val customPalette = RewardCosmetics.palette(equippedThemeAssetKey) ?: preferencePalette
    val useDarkSystemBarIcons = isLiftlyBackgroundLight(prefs.theme, customPalette)

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkSystemBarIcons
            isAppearanceLightNavigationBars = useDarkSystemBarIcons
        }
    }

    LaunchedEffect(equippedRestSoundId, prefs.restEndSoundType) {
        if (equippedRestSoundId != null && equippedRestSoundId != prefs.restEndSoundType) {
            vm.setRestEndSoundType(equippedRestSoundId)
        }
    }

    LiftlyTheme(prefs.theme, customPalette) {
        LiftlyBackground(
            customWallpaperUri = prefs.customWallpaperUri.takeIf { prefs.customWallpaperEnabled },
            wallpaperDimPercent = prefs.wallpaperDimPercent,
            rewardWallpaperKey = equippedWallpaperAssetKey,
        ) {
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (initializationError == null) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(initializationError ?: "Falha ao abrir o banco local.")
                            Button(onClick = vm::retryInitialization) { Text("Tentar novamente") }
                        }
                    }
                }
            } else {
                LiftlyNavigation(vm, prefs.onboardingDone)
            }
        }
    }
}

@Composable
private fun LiftlyNavigation(vm: AppViewModel, onboardingDone: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in mainDestinations.map { it.route } || route == "stopwatch" || route == "music"
    val snackbar = remember { SnackbarHostState() }
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val sessionSets by vm.sessionSets.collectAsStateWithLifecycle()
    val automaticWarmupSessions by vm.automaticWarmupSessions.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val activeSession = sessions.firstOrNull { it.status == "Em andamento" }
    val activeSets = sessionSets.filter { it.sessionId == activeSession?.id }
    val activeCompletedSets = activeSets.count { it.completed }

    fun openSession(sessionId: String, automaticWarmup: Boolean) {
        navController.navigate(sessionRoute(sessionId, automaticWarmup)) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(feedback?.nonce) {
        feedback?.let {
            if (prefs.haptics) {
                haptic.performHapticFeedback(
                    if (it.isError) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm,
                )
            }
            snackbar.showSnackbar(it.message)
            vm.clearFeedback()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                    ),
                ) {
                    Column {
                        activeSession?.let { session ->
                            ActiveWorkoutDock(
                                workoutName = session.workoutName,
                                isTestMode = session.isTestMode,
                                completedSets = activeCompletedSets,
                                totalSets = activeSets.size,
                                onResume = {
                                    openSession(
                                        sessionId = session.id,
                                        automaticWarmup = session.id in automaticWarmupSessions,
                                    )
                                },
                            )
                        }
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier.padding(horizontal = 1.dp),
                        ) {
                            mainDestinations.forEach { destination ->
                                val selected = route == destination.route
                                NavigationBarItem(
                                    selected = selected,
                                    alwaysShowLabel = true,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(width = 20.dp, height = 3.dp)
                                                    .background(
                                                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        MaterialTheme.shapes.extraSmall,
                                                    ),
                                            )
                                            NeonIcon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.label,
                                                selected = selected,
                                                intensity = 0f,
                                                size = 24.dp,
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingDone) "today" else "onboarding",
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        ) {
            composable("onboarding") {
                OnboardingScreen { demo ->
                    vm.finishOnboarding(demo)
                    navController.navigate("today") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }
            composable("today") {
                TodayScreen(
                    vm = vm,
                    onOpenSession = { id -> openSession(id, automaticWarmup = false) },
                    onOpenWarmupSession = { id -> openSession(id, automaticWarmup = true) },
                    onOpenCalendar = { navController.navigate("calendar") },
                    onOpenStopwatch = { navController.navigate("stopwatch") },
                    onOpenMusic = { navController.navigate("music") },
                )
            }
            composable("workouts") {
                WorkoutsScreen(vm) { navController.navigate("calendar") }
            }
            composable("exercises") { ExercisesScreen(vm) }
            composable("progress") { ProgressScreen(vm) }
            composable("stopwatch") { StopwatchScreen() }
            composable("music") { MusicDestination() }
            composable("profile") {
                ProfileScreen(
                    vm = vm,
                    onOpenRewards = { navController.navigate("rewards") },
                )
            }
            composable("rewards") {
                RewardsDestination(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("calendar") {
                CalendarScreen(vm) { navController.popBackStack() }
            }
            composable(
                route = "session/{sessionId}?warmup={warmup}",
                arguments = listOf(
                    navArgument("warmup") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("sessionId").orEmpty()
                SessionScreen(
                    vm = vm,
                    sessionId = id,
                    showAutomaticWarmup = entry.arguments?.getBoolean("warmup") ?: false,
                    onMinimize = {
                        if (!navController.popBackStack()) {
                            navController.navigate("today") { launchSingleTop = true }
                        }
                    },
                    onFinished = {
                        if (!navController.popBackStack("today", false)) {
                            navController.navigate("today") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RewardsDestination(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val snapshot by vm.rewards.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    var selectedCategoryName by rememberSaveable {
        mutableStateOf(RewardCategory.All.name)
    }
    var selectedMissionPeriodName by rememberSaveable {
        mutableStateOf(MissionPeriod.Daily.name)
    }
    val selectedCategory = RewardCategory.entries
        .firstOrNull { it.name == selectedCategoryName }
        ?: RewardCategory.All
    val selectedMissionPeriod = MissionPeriod.entries
        .firstOrNull { it.name == selectedMissionPeriodName }
        ?: MissionPeriod.Daily
    val completedWeekStreak = remember(sessions, preferences.weeklyWorkoutGoal) {
        TrainingMomentumCalculator.calculate(
            completedSessionTimes = sessions
                .asSequence()
                .filter { it.finishedAt != null && !it.isTestMode }
                .map { it.startedAt }
                .toList(),
            weeklyGoal = preferences.weeklyWorkoutGoal,
        ).completedWeekStreak
    }

    RewardsScreen(
        state = snapshot.toUiState(
            workoutStreak = completedWeekStreak,
            selectedCategory = selectedCategory,
            selectedMissionPeriod = selectedMissionPeriod,
        ),
        actions = RewardsActions(
            onCategorySelected = { selectedCategoryName = it.name },
            onMissionPeriodSelected = { selectedMissionPeriodName = it.name },
            onBuy = { vm.purchaseRewardItem(it.id) },
            onEquip = { vm.equipRewardItem(it.id) },
            onClaimMission = {},
        ),
        onBack = onBack,
    )
}

@Composable
private fun ActiveWorkoutDock(
    workoutName: String,
    isTestMode: Boolean,
    completedSets: Int,
    totalSets: Int,
    onResume: () -> Unit,
) {
    Surface(
        onClick = onResume,
        modifier = Modifier.fillMaxWidth(),
        color = if (isTestMode) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        },
        contentColor = if (isTestMode) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonIcon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                selected = true,
                intensity = 0.45f,
                size = 24.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isTestMode) "Teste em andamento" else "Treino em andamento",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTestMode) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = workoutName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "$completedSets/$totalSets • Retomar",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun sessionRoute(sessionId: String, automaticWarmup: Boolean): String =
    if (automaticWarmup) "session/$sessionId?warmup=true" else "session/$sessionId"

@Composable
private fun MusicDestination() {
    val context = LocalContext.current
    val app = context.applicationContext as LiftlyApplication
    val musicVm: MusicViewModel = viewModel(
        key = "liftly_music",
        factory = MusicViewModel.factory(
            app.musicRepository,
            app.personalSpotifyPlaylistRepository,
        ),
    )
    val state by musicVm.state.collectAsStateWithLifecycle()
    val personalPlaylists by musicVm.personalPlaylists.collectAsStateWithLifecycle()
    val selectedPersonalPlaylistId by musicVm.selectedPersonalPlaylistId.collectAsStateWithLifecycle()

    LaunchedEffect(musicVm, context) {
        musicVm.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(musicVm) {
        musicVm.refresh()
    }

    MusicScreen(
        state = state.toScreenState(
            personalPlaylists = personalPlaylists,
            selectedPersonalPlaylistId = selectedPersonalPlaylistId,
        ),
        onRefresh = musicVm::refresh,
        onOpenSpotify = {
            if (!SpotifyLauncher.openPlaylist(context, state.links)) {
                Toast.makeText(
                    context,
                    "Não foi possível abrir o Spotify neste aparelho.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
        onSavePersonalPlaylist = musicVm::savePersonalPlaylist,
        onDeletePersonalPlaylist = musicVm::removePersonalPlaylist,
        onSelectPersonalPlaylist = musicVm::selectPersonalPlaylist,
        onOpenPersonalPlaylist = { id ->
            if (!SpotifyLauncher.openPlaylist(context, SpotifyPlaylistLinks.fromId(id))) {
                Toast.makeText(
                    context,
                    "Não foi possível abrir o Spotify neste aparelho.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
    )
}

private fun MusicState.toScreenState(
    personalPlaylists: List<PersonalSpotifyPlaylist>,
    selectedPersonalPlaylistId: String?,
): MusicScreenState = MusicScreenState(
    id = config.spotifyId,
    title = displayTitle,
    description = description.orEmpty(),
    thumbnailUrl = thumbnailUrl.orEmpty(),
    enabled = enabled,
    isLoading = source == MusicConfigSource.FALLBACK &&
        metadata == null &&
        lastCheckedAtEpochMillis == null,
    isRefreshing = isRefreshing,
    isOffline = isOffline,
    lastUpdatedText = formatMusicUpdatedAt(config.updatedAt),
    remoteConfigured = remoteConfigured,
    errorMessage = when (issue) {
        MusicRefreshIssue.NETWORK -> "Sem conexão agora. A última seleção válida foi mantida."
        MusicRefreshIssue.INVALID_PAYLOAD -> "A atualização remota foi rejeitada por estar inválida."
        MusicRefreshIssue.ROLLBACK_REJECTED -> "Uma versão antiga da playlist foi ignorada com segurança."
        MusicRefreshIssue.INVALID_ENDPOINT -> "O endereço da configuração musical é inválido."
        MusicRefreshIssue.NOT_CONFIGURED,
        null,
        -> null
    },
    personalPlaylists = personalPlaylists.map {
        com.liftly.app.ui.screens.PersonalSpotifyPlaylistUi(it.spotifyId, it.title)
    },
    selectedPersonalPlaylistId = selectedPersonalPlaylistId,
)

private fun formatMusicUpdatedAt(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        MUSIC_DATE_FORMAT.format(OffsetDateTime.parse(raw).toLocalDateTime())
    } catch (_: DateTimeParseException) {
        raw
    }
}

private val MUSIC_DATE_FORMAT = DateTimeFormatter.ofPattern(
    "dd/MM/yyyy 'às' HH:mm",
    Locale.forLanguageTag("pt-BR"),
)
