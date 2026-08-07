package com.liftly.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.BuildConfig
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.BodyPhotoEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.UserProfileEntity
import com.liftly.app.data.RewardSlots
import com.liftly.app.audio.RestAlertPlayer
import com.liftly.app.audio.RestAlertSound
import com.liftly.app.domain.BmiCalculator
import com.liftly.app.domain.BmiResult
import com.liftly.app.domain.BmiRange
import com.liftly.app.domain.BmiSex
import com.liftly.app.domain.WeightAdjustmentDirection
import com.liftly.app.domain.WorkoutCalorieEstimator
import com.liftly.app.integration.discord.DiscordWebhookUrlValidator
import com.liftly.app.integration.healthconnect.AndroidHealthConnectRepository
import com.liftly.app.integration.healthconnect.HealthConnectAvailability
import com.liftly.app.integration.healthconnect.HealthConnectPermissionState
import com.liftly.app.integration.healthconnect.HealthConnectReadResult
import com.liftly.app.integration.healthconnect.HealthConnectSnapshot
import com.liftly.app.integration.healthconnect.LiftlyHealthPermissions
import com.liftly.app.ui.AppViewModel
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton
import com.liftly.app.ui.components.InteractiveGlassCard
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.components.PaletteEditorDialog
import com.liftly.app.ui.components.WeightHistorySheet
import com.liftly.app.ui.rewards.RewardCosmetics
import com.liftly.app.ui.theme.LiftlyCustomPalette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    onOpenBusiness: () -> Unit = {},
    onOpenRewards: () -> Unit = {},
    onOpenRewardsAdmin: () -> Unit = {},
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val bodyPhotos by vm.bodyPhotos.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val rewards by vm.rewards.collectAsStateWithLifecycle()
    val equippedProfileTitle = RewardCosmetics.profileTitle(
        rewards.store.firstOrNull { it.equipped && it.item.slot == RewardSlots.PROFILE_TITLE }?.item?.assetKey
    )
    val equippedProfileFrameColor = RewardCosmetics.profileFrameArgb(
        rewards.store.firstOrNull { it.equipped && it.item.slot == RewardSlots.PROFILE_FRAME }?.item?.assetKey
    )?.let { Color(it.toInt()) }
    val selectedTheme = when (prefs.theme.trim().lowercase()) {
        "claro", "light", "branco", "white" -> "Branco"
        "escuro", "dark", "noturno", "preto", "black" -> "Preto"
        else -> "Roxo"
    }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val sessionSets by vm.sessionSets.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthConnect = remember(context) {
        AndroidHealthConnectRepository(context.applicationContext)
    }
    var healthAvailability by remember { mutableStateOf(healthConnect.availability()) }
    var healthPermissions by remember { mutableStateOf(HealthConnectPermissionState.None) }
    var healthSnapshot by remember { mutableStateOf<HealthConnectSnapshot?>(null) }
    var healthLoading by remember { mutableStateOf(false) }
    var healthMessage by remember { mutableStateOf<String?>(null) }
    val refreshHealth: () -> Unit = {
        scope.launch {
            healthLoading = true
            healthAvailability = healthConnect.availability()
            when (val result = healthConnect.readLatestMetrics()) {
                is HealthConnectReadResult.Success -> {
                    healthSnapshot = result.snapshot
                    healthPermissions = result.snapshot.permissions
                    healthMessage = "Dados atualizados."
                }
                is HealthConnectReadResult.Unavailable -> {
                    healthAvailability = result.availability
                    healthMessage = "Health Connect indisponível neste aparelho."
                }
                is HealthConnectReadResult.Failed -> {
                    healthMessage = "Não foi possível sincronizar agora."
                }
            }
            healthLoading = false
        }
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        AndroidHealthConnectRepository.permissionRequestContract(),
    ) { granted ->
        healthPermissions = HealthConnectPermissionState(
            canReadWeight = LiftlyHealthPermissions.readWeight in granted,
            canReadSleep = LiftlyHealthPermissions.readSleep in granted,
            canWriteExercise = LiftlyHealthPermissions.writeExercise in granted,
        )
        healthMessage = if (granted.isEmpty()) {
            "Nenhuma permissão foi concedida."
        } else {
            "Permissões atualizadas."
        }
        refreshHealth()
    }
    val restAlertPlayer = remember(context) { RestAlertPlayer(context.applicationContext) }
    var restSoundTypeDraft by rememberSaveable { mutableStateOf(prefs.restEndSoundType) }
    var restSoundDurationDraft by rememberSaveable {
        mutableFloatStateOf(prefs.restEndSoundDurationSeconds.toFloat())
    }
    var wallpaperDimDraft by rememberSaveable { mutableFloatStateOf(prefs.wallpaperDimPercent.toFloat()) }
    var restSoundTestMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var restSoundTestFailed by rememberSaveable { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showWeightHistory by remember { mutableStateOf(false) }
    var showHeight by remember { mutableStateOf(false) }
    var showBmi by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBodyPhotos by remember { mutableStateOf(false) }
    var showPaletteEditor by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var discordWebhookDraft by rememberSaveable { mutableStateOf(prefs.discordWebhookUrl) }

    LaunchedEffect(healthConnect) {
        healthAvailability = healthConnect.availability()
        healthPermissions = healthConnect.grantedPermissions()
        if (healthPermissions.hasAnyReadPermission) {
            when (val result = healthConnect.readLatestMetrics()) {
                is HealthConnectReadResult.Success -> {
                    healthSnapshot = result.snapshot
                    healthPermissions = result.snapshot.permissions
                }
                is HealthConnectReadResult.Unavailable -> healthAvailability = result.availability
                is HealthConnectReadResult.Failed -> Unit
            }
        }
    }

    LaunchedEffect(prefs.discordWebhookUrl) {
        discordWebhookDraft = prefs.discordWebhookUrl
    }

    LaunchedEffect(prefs.restEndSoundDurationSeconds) {
        restSoundDurationDraft = prefs.restEndSoundDurationSeconds.toFloat()
    }
    LaunchedEffect(prefs.restEndSoundType) {
        restSoundTypeDraft = prefs.restEndSoundType
    }
    LaunchedEffect(prefs.wallpaperDimPercent) {
        wallpaperDimDraft = prefs.wallpaperDimPercent.toFloat()
    }
    LaunchedEffect(prefs.restEndSound) {
        if (!prefs.restEndSound) restAlertPlayer.stop()
    }
    DisposableEffect(restAlertPlayer) {
        onDispose { restAlertPlayer.stop() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            vm.createBackup().onSuccess { json -> context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()?.let(vm::importBackup)
    }
    val profilePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selectedUri = uri.toString()
            val grantPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (grantPersisted) {
                vm.setProfilePhotoUri(selectedUri)
            }
        }
    }
    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val grantPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (grantPersisted) vm.setCustomWallpaperUri(uri.toString())
        }
    }
    val bodyPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val grantPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (grantPersisted) vm.saveBodyPhoto(uri.toString())
        }
    }
    val removeProfilePhoto = { vm.setProfilePhotoUri("") }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Perfil", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                InteractiveGlassCard(
                    onClick = { showProfile = true },
                    modifier = Modifier.fillMaxWidth(),
                    onClickLabel = "Editar perfil",
                    elevation = 1.dp,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(84.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (prefs.profilePhotoUri.isBlank()) "Adicionar foto de perfil" else "Trocar foto de perfil",
                                ) { profilePhotoLauncher.launch(arrayOf("image/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (equippedProfileFrameColor == null) 1.dp else 2.dp,
                                    equippedProfileFrameColor ?: MaterialTheme.colorScheme.outlineVariant,
                                ),
                                shadowElevation = 0.dp,
                            ) {
                                if (prefs.profilePhotoUri.isBlank()) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, "Adicionar foto de perfil", modifier = Modifier.size(36.dp))
                                    }
                                } else {
                                    PersistedProfilePhoto(
                                        uri = prefs.profilePhotoUri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.size(30.dp).align(Alignment.BottomEnd),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shadowElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                profile?.nickname?.ifBlank { "Seu perfil" } ?: "Seu perfil",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            equippedProfileTitle?.let { title ->
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(profile?.objective ?: "Configure seus dados e objetivo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { profilePhotoLauncher.launch(arrayOf("image/*")) }) {
                                    Text(if (prefs.profilePhotoUri.isBlank()) "Adicionar foto" else "Trocar foto")
                                }
                                if (prefs.profilePhotoUri.isNotBlank()) {
                                    TextButton(onClick = removeProfilePhoto) { Text("Remover") }
                                }
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
            item {
                MenuCard(
                    Icons.Default.Storefront,
                    "Liftly Rewards",
                    "Nível ${rewards.level.level} • ${rewards.wallet.coinBalance} Lift Coins",
                    onClick = onOpenRewards,
                )
            }
            if (BuildConfig.ADMIN_TOOLS) {
                item {
                    MenuCard(
                        Icons.Default.AdminPanelSettings,
                        "Laboratório Rewards",
                        "Simule treinos, missões, saldo e a experiência do usuário",
                        onClick = onOpenRewardsAdmin,
                    )
                }
            }
            if (BuildConfig.COMMERCIAL_EDITION) {
                item {
                    MenuCard(
                        Icons.Default.BusinessCenter,
                        "Painel Business",
                        "Planos, licenças e preparação da academia",
                        onClick = onOpenBusiness,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Medidas corporais",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        IconButton(onClick = { vm.setHideBodyMetrics(!prefs.hideBodyMetrics) }) {
                            Icon(
                                imageVector = if (prefs.hideBodyMetrics) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (prefs.hideBodyMetrics) "Mostrar peso e altura" else "Ocultar peso e altura",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HealthMetric(
                            "Peso atual",
                            if (prefs.hideBodyMetrics) "•••" else profile?.currentWeightKg?.let { "${it.pretty()} kg" } ?: "—",
                            Icons.Default.MonitorWeight,
                            Modifier.weight(1f),
                            onClick = { showWeight = true },
                            onClickLabel = "Editar peso atual",
                        )
                        HealthMetric(
                            "Altura",
                            if (prefs.hideBodyMetrics) "•••" else profile?.heightCm?.let { "${it.pretty()} cm" } ?: "—",
                            Icons.Default.Straighten,
                            Modifier.weight(1f),
                            onClick = { showHeight = true },
                            onClickLabel = "Editar altura",
                        )
                    }
                }
            }
            item {
                val finishedCount = sessions.count { it.finishedAt != null }
                MenuCard(
                    Icons.Default.History,
                    "Histórico de treinos",
                    when (finishedCount) {
                        0 -> "Nenhum treino finalizado"
                        1 -> "1 treino finalizado"
                        else -> "$finishedCount treinos finalizados"
                    },
                    onClick = { showHistory = true }
                )
            }
            item {
                MenuCard(
                    Icons.Default.PhotoLibrary,
                    "Fotos de evolução",
                    when (bodyPhotos.size) {
                        0 -> "Registre e compare sua evolução corporal"
                        1 -> "1 foto salva com data"
                        else -> "${bodyPhotos.size} fotos salvas • compare duas"
                    },
                    onClick = { showBodyPhotos = true }
                )
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Histórico de peso", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showWeightHistory = true }) { Text("Gerenciar") }
                            GradientActionButton(
                                onClick = { showWeight = true },
                                onClickLabel = "Registrar peso",
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text("Registrar") }
                        }
                        if (prefs.hideBodyMetrics) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Histórico oculto. Use o olho em Medidas corporais para revelar.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            WeightChart(weights.map { it.weightKg })
                            weights.takeLast(3).reversed().forEach { entry ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(java.time.Instant.ofEpochMilli(entry.measuredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                    Text("${entry.weightKg.pretty()} kg", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (weights.isEmpty()) Text("Registre seu peso para acompanhar a evolução.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { MenuCard(Icons.Default.Calculate, "Calculadora de IMC", "Triagem simples com orientações", onClick = { showBmi = true }) }
            item {
                Text("Preferências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("Tema visual", fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (selectedTheme) {
                                        "Branco" -> "Fundo claro e contraste suave"
                                        "Preto" -> "Preto OLED e superfícies discretas"
                                        else -> "Escuro com roxo como destaque"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Roxo" to "Roxo", "Branco" to "Branco", "Preto" to "Preto").forEach { (value, label) ->
                                FilterChip(
                                    selected = selectedTheme == value,
                                    onClick = { vm.setTheme(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("Cores personalizadas", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (prefs.customPaletteEnabled) "Paleta aplicada ao app inteiro" else "Use qualquer combinação de cores",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = prefs.customPaletteEnabled,
                                    onCheckedChange = vm::setCustomPaletteEnabled,
                                    enabled = prefs.customPrimaryColor.isNotBlank(),
                                )
                            }
                            OutlinedButton(
                                onClick = { showPaletteEditor = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Palette, null)
                                Text(
                                    if (prefs.customPrimaryColor.isBlank()) "Personalizar todas as cores" else "Editar paleta personalizada",
                                    Modifier.padding(start = 8.dp),
                                )
                            }
                            Text(
                                "Destaques, fundo, cartões e textos com contraste automático.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("Wallpaper personalizado", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Use uma foto do aparelho atrás das ondas animadas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = prefs.customWallpaperEnabled && prefs.customWallpaperUri.isNotBlank(),
                                    onCheckedChange = vm::setCustomWallpaperEnabled,
                                    enabled = prefs.customWallpaperUri.isNotBlank(),
                                )
                            }
                            if (prefs.customWallpaperUri.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clickable { wallpaperLauncher.launch(arrayOf("image/*")) },
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    PersistedProfilePhoto(
                                        uri = prefs.customWallpaperUri,
                                        modifier = Modifier.fillMaxSize(),
                                        contentDescription = "Prévia do wallpaper personalizado",
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Contraste do conteúdo", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${wallpaperDimDraft.roundToInt().coerceIn(20, 80)}%",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Slider(
                                    value = wallpaperDimDraft.coerceIn(20f, 80f),
                                    onValueChange = { wallpaperDimDraft = it },
                                    onValueChangeFinished = {
                                        vm.setWallpaperDimPercent(wallpaperDimDraft.roundToInt().coerceIn(20, 80))
                                    },
                                    valueRange = 20f..80f,
                                    steps = 5,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { wallpaperLauncher.launch(arrayOf("image/*")) },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Trocar") }
                                    TextButton(
                                        onClick = { vm.setCustomWallpaperUri("") },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Remover") }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { wallpaperLauncher.launch(arrayOf("image/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null)
                                    Text("Escolher imagem", Modifier.padding(start = 8.dp))
                                }
                            }
                            Text(
                                "A imagem fica somente neste aparelho e não é incluída no backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToggleRow("Feedback háptico", "Checks e ações importantes", prefs.haptics, vm::setHaptics)
                        ToggleRow("Cronômetro de descanso", "Inicia ao concluir uma série", prefs.restTimer, vm::setRestTimer)
                        ToggleRow(
                            "Vibrar ao fim do descanso",
                            "Alerta mesmo com o app em segundo plano ou a tela apagada",
                            prefs.restEndVibration,
                            vm::setRestEndVibration
                        )
                        ToggleRow(
                            "Som ao fim do descanso",
                            "Usa mídia/fones e abaixa a música durante o aviso",
                            prefs.restEndSound,
                        ) { enabled ->
                            if (!enabled) {
                                restAlertPlayer.stop()
                                restSoundTestMessage = null
                            }
                            vm.setRestEndSound(enabled)
                        }
                        if (prefs.restEndSound) {
                            Column(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Som do alerta",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                RestAlertSound.entries.forEach { sound ->
                                    FilterChip(
                                        selected = restSoundTypeDraft == sound.id,
                                        onClick = {
                                            restAlertPlayer.stop()
                                            restSoundTestMessage = null
                                            restSoundTypeDraft = sound.id
                                            vm.setRestEndSoundType(sound.id)
                                        },
                                        label = { Text(sound.displayName) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                val durationSeconds = restSoundDurationDraft.roundToInt().coerceIn(
                                    RestAlertPlayer.MIN_DURATION_SECONDS,
                                    RestAlertPlayer.MAX_DURATION_SECONDS,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Duração do aviso", fontWeight = FontWeight.SemiBold)
                                    Text("$durationSeconds s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = restSoundDurationDraft.coerceIn(
                                        RestAlertPlayer.MIN_DURATION_SECONDS.toFloat(),
                                        RestAlertPlayer.MAX_DURATION_SECONDS.toFloat(),
                                    ),
                                    onValueChange = { restSoundDurationDraft = it },
                                    onValueChangeFinished = {
                                        vm.setRestEndSoundDurationSeconds(durationSeconds)
                                    },
                                    valueRange = RestAlertPlayer.MIN_DURATION_SECONDS.toFloat()..RestAlertPlayer.MAX_DURATION_SECONDS.toFloat(),
                                    steps = (RestAlertPlayer.MAX_DURATION_SECONDS - RestAlertPlayer.MIN_DURATION_SECONDS - 1).coerceAtLeast(0),
                                )
                                Text(
                                    "O aviso usa o áudio de mídia e os fones conectados. Se houver música tocando, ela abaixa temporariamente durante o alerta.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = {
                                        restAlertPlayer.stop()
                                        val volume = restAlertPlayer.mediaVolumePercent()
                                        val result = restAlertPlayer.play(restSoundTypeDraft, durationSeconds)
                                        restSoundTestFailed = result != RestAlertPlayer.PlayResult.STARTED
                                        restSoundTestMessage = when (result) {
                                            RestAlertPlayer.PlayResult.STARTED ->
                                                "Reproduzindo ${RestAlertSound.fromId(restSoundTypeDraft).displayName} por $durationSeconds s • volume de mídia $volume%."
                                            RestAlertPlayer.PlayResult.MEDIA_VOLUME_MUTED ->
                                                "O volume de mídia está em 0%. Aumente-o pelos botões do aparelho e teste novamente."
                                            RestAlertPlayer.PlayResult.AUDIO_FOCUS_DENIED ->
                                                "O Android não liberou o áudio. Feche outro app que esteja usando áudio e tente novamente."
                                            RestAlertPlayer.PlayResult.OUTPUT_ERROR ->
                                                "Não foi possível abrir a saída de áudio. Desconecte/reconecte o fone ou reinicie o app."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text("Testar som")
                                }
                                restSoundTestMessage?.let { message ->
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (restSoundTestFailed) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Integrações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                HealthConnectCard(
                    availability = healthAvailability,
                    permissions = healthPermissions,
                    snapshot = healthSnapshot,
                    loading = healthLoading,
                    message = healthMessage,
                    onConnect = {
                        runCatching {
                            healthPermissionLauncher.launch(LiftlyHealthPermissions.all)
                        }.onFailure {
                            healthMessage = "Não foi possível abrir as permissões do Health Connect."
                        }
                    },
                    onInstallOrUpdate = {
                        runCatching {
                            context.startActivity(
                                AndroidHealthConnectRepository.providerInstallOrUpdateIntent(context),
                            )
                        }.recoverCatching {
                            context.startActivity(
                                AndroidHealthConnectRepository.providerWebInstallIntent(),
                            )
                        }.onFailure {
                            healthMessage = "Não foi possível abrir a instalação do Health Connect."
                        }
                    },
                    onRefresh = refreshHealth,
                    onUseWeight = { weight ->
                        vm.saveWeight(weight, "Importado do Health Connect")
                        healthMessage = "Peso importado para o Liftly."
                    },
                    onManage = {
                        runCatching {
                            context.startActivity(
                                AndroidHealthConnectRepository.manageHealthConnectIntent(context),
                            )
                        }.onFailure {
                            healthMessage = "Não foi possível abrir o Health Connect."
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeonIcon(Icons.Default.CloudUpload, null, selected = true, size = 32.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("Exportação para Discord", fontWeight = FontWeight.Bold)
                                Text("Envia um resumo ao finalizar um treino real", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(prefs.discordWebhookEnabled, vm::setDiscordWebhookEnabled)
                        }
                        OutlinedTextField(
                            value = discordWebhookDraft,
                            onValueChange = { discordWebhookDraft = it.trim().take(2_048) },
                            label = { Text("URL do webhook") },
                            placeholder = { Text("https://discord.com/api/webhooks/…") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val validDiscordUrl = DiscordWebhookUrlValidator.isValid(discordWebhookDraft)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.testDiscordWebhook(discordWebhookDraft) },
                                enabled = validDiscordUrl,
                                modifier = Modifier.weight(1f),
                            ) { Text("Testar") }
                            Button(
                                onClick = { vm.setDiscordWebhookUrl(discordWebhookDraft) },
                                enabled = discordWebhookDraft.isBlank() || validDiscordUrl,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (discordWebhookDraft.isBlank()) "Remover" else "Salvar") }
                        }
                        Text(
                            "Opcional. O webhook fica somente neste aparelho e não entra no backup. Ao ativar, nome do treino, séries, reps, cargas, RIR, duração e calorias estimadas são enviados ao canal; fotos nunca são enviadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text("Privacidade e dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                MenuCard(Icons.Default.Backup, "Exportar backup", "Salve seus dados em JSON") { exportLauncher.launch("liftly-backup-${LocalDate.now()}.json") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar backup JSON") }
                Spacer(Modifier.height(8.dp))
                MenuCard(Icons.Default.PrivacyTip, "Política de privacidade", "Como seus dados são tratados") { showPrivacy = true }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteForever, null)
                    Text("Apagar todos os dados", Modifier.padding(start = 8.dp))
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    if (showProfile) ProfileDialog(profile ?: UserProfileEntity(), { showProfile = false }) { vm.saveProfile(it); showProfile = false }
    if (showWeight) WeightDialog({ showWeight = false }) { vm.saveWeight(it); showWeight = false }
    if (showWeightHistory) WeightHistorySheet(
        entries = weights,
        onDismiss = { showWeightHistory = false },
        onAdd = {
            showWeightHistory = false
            showWeight = true
        },
        onUpdate = { entry, value, notes -> vm.updateWeight(entry, value, notes) },
        onDelete = { entry -> vm.deleteWeight(entry.id) },
    )
    if (showHeight) HeightDialog(
        initialHeightCm = profile?.heightCm,
        onDismiss = { showHeight = false },
        onSave = { heightCm ->
            vm.saveProfile((profile ?: UserProfileEntity()).copy(heightCm = heightCm))
            showHeight = false
        },
    )
    if (showBmi) BmiDialog(profile, { showBmi = false })
    if (showHistory) WorkoutHistorySheet(
        sessions = sessions,
        allSets = sessionSets,
        exercises = exercises,
        bodyWeightKg = profile?.currentWeightKg,
        onDeleteSession = vm::deleteHistoricalSession,
        onDismiss = { showHistory = false }
    )
    if (showBodyPhotos) BodyPhotoGallerySheet(
        photos = bodyPhotos,
        onAdd = { bodyPhotoLauncher.launch(arrayOf("image/*")) },
        onDelete = vm::deleteBodyPhoto,
        onDismiss = { showBodyPhotos = false },
    )
    if (showPaletteEditor) {
        PaletteEditorDialog(
            themeMode = prefs.theme,
            currentPalette = LiftlyCustomPalette(
                enabled = prefs.customPaletteEnabled,
                primary = prefs.customPrimaryColor,
                secondary = prefs.customSecondaryColor,
                background = prefs.customBackgroundColor,
                surface = prefs.customSurfaceColor,
                text = prefs.customTextColor,
            ),
            onApply = { palette ->
                vm.setCustomPaletteColors(
                    primary = palette.primary,
                    secondary = palette.secondary,
                    background = palette.background,
                    surface = palette.surface,
                    text = palette.text,
                )
            },
            onRestore = vm::resetCustomPalette,
            onDismiss = { showPaletteEditor = false },
        )
    }
    if (showPrivacy) PrivacyDialog { showPrivacy = false }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Apagar tudo?") },
        text = { Text("Treinos, sessões, cargas, peso, perfil, fotos de evolução e exercícios personalizados serão removidos. Os arquivos originais das fotos não serão apagados. Esta ação não pode ser desfeita. Exporte um backup antes se quiser preservar os registros.") },
        confirmButton = {
            Button(onClick = {
                vm.deleteAllData()
                showDelete = false
            }) { Text("Apagar definitivamente") }
        },
        dismissButton = { OutlinedButton(onClick = { showDelete = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun HealthConnectCard(
    availability: HealthConnectAvailability,
    permissions: HealthConnectPermissionState,
    snapshot: HealthConnectSnapshot?,
    loading: Boolean,
    message: String?,
    onConnect: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onUseWeight: (Double) -> Unit,
    onManage: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonIcon(Icons.Default.HealthAndSafety, null, selected = true, size = 32.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Health Connect", fontWeight = FontWeight.Bold)
                    Text(
                        when (availability) {
                            HealthConnectAvailability.AVAILABLE ->
                                if (permissions.hasAllPermissions) "Conectado" else "Conexão opcional"
                            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED ->
                                "Instalação ou atualização necessária"
                            HealthConnectAvailability.UNAVAILABLE -> "Indisponível neste aparelho"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (availability == HealthConnectAvailability.AVAILABLE) {
                Text(
                    "Peso: ${if (permissions.canReadWeight) "autorizado" else "não autorizado"}  •  " +
                        "Sono: ${if (permissions.canReadSleep) "autorizado" else "não autorizado"}  •  " +
                        "Treinos: ${if (permissions.canWriteExercise) "autorizado" else "não autorizado"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                snapshot?.latestWeight?.let { weight ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Peso mais recente: ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", weight.kilograms)} kg",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onUseWeight(weight.kilograms) }) {
                            Text("Usar")
                        }
                    }
                }
                snapshot?.latestSleep?.let { sleep ->
                    val totalMinutes = sleep.sleepDuration.toMinutes().coerceAtLeast(0)
                    Text(
                        "Sono mais recente: ${totalMinutes / 60}h ${totalMinutes % 60}min",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (permissions.canReadWeight && snapshot?.latestWeight == null) {
                    Text(
                        "Nenhum peso encontrado nos últimos 30 dias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (permissions.canReadSleep && snapshot?.latestSleep == null) {
                    Text(
                        "Nenhum sono encontrado nos últimos 30 dias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (permissions.canWriteExercise) {
                        "Treinos reais finalizados são exportados automaticamente."
                    } else {
                        "Autorize o envio para registrar seus treinos no Health Connect."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (permissions.hasAllPermissions) "Revisar permissões" else "Conectar")
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !loading && permissions.hasAnyReadPermission,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (loading) "Sincronizando…" else "Sincronizar")
                    }
                    OutlinedButton(
                        onClick = onManage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Gerenciar")
                    }
                }
            } else if (availability == HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED) {
                Text(
                    "Instale ou atualize o Health Connect para liberar as permissões.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onInstallOrUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Instalar ou atualizar")
                }
            } else {
                Text(
                    "Este aparelho, versão do Android ou perfil de usuário não oferece Health Connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "O Health Connect não exige login no Liftly: ele lê dados que Google Fit, Samsung Health ou outro app compatível já tenha sincronizado. Esses dados não são enviados ao Discord.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
    onClickLabel: String,
) {
    InteractiveGlassCard(
        onClick = onClick,
        modifier = modifier,
        onClickLabel = onClickLabel,
        contentDescription = "$label: $value. Toque para editar.",
        elevation = 5.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonIcon(icon, null, selected = true, intensity = 1.05f, size = 30.dp)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PersistedProfilePhoto(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Foto de perfil",
    contentScale: ContentScale = ContentScale.Crop,
    maxDecodeDimension: Int = 1_280,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, maxDecodeDimension) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                loadProfilePhoto(
                    context = context,
                    uri = Uri.parse(uri),
                    maxDimension = maxDecodeDimension,
                )
            }.getOrNull()
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            NeonIcon(Icons.Default.Person, "$contentDescription indisponível", selected = true, intensity = 1.1f, size = 54.dp)
        }
    }
}

@Composable
private fun ZoomablePersistedProfilePhoto(
    uri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (nextScale <= 1.001f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val maxX = viewportSize.width * (nextScale - 1f) / 2f
            val maxY = viewportSize.height * (nextScale - 1f) / 2f
            val nextOffset = offset + panChange
            scale = nextScale
            offset = Offset(
                x = nextOffset.x.coerceIn(-maxX, maxX),
                y = nextOffset.y.coerceIn(-maxY, maxY),
            )
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        PersistedProfilePhoto(
            uri = uri,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            maxDecodeDimension = 3_072,
        )

        if (scale > 1.001f) {
            TextButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            ) {
                Text("Redefinir zoom", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyPhotoGallerySheet(
    photos: List<BodyPhotoEntity>,
    onAdd: () -> Unit,
    onDelete: (BodyPhotoEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showComparison by remember { mutableStateOf(false) }
    var expandedPhoto by remember { mutableStateOf<BodyPhotoEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<BodyPhotoEntity?>(null) }
    val selectedPhotos = photos.filter { it.id in selectedIds }.sortedBy { it.addedAt }

    LaunchedEffect(photos.map { it.id }) {
        selectedIds = selectedIds.intersect(photos.mapTo(mutableSetOf()) { it.id })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fotos de evolução", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Cada foto guarda a data em que foi adicionada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAdd) { Icon(Icons.Default.AddAPhoto, "Adicionar foto") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddAPhoto, null)
                    Text("Adicionar", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = { showComparison = true },
                    enabled = selectedPhotos.size == 2,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Compare, null)
                    Text("Comparar ${selectedPhotos.size}/2", Modifier.padding(start = 6.dp))
                }
            }
            Text(
                "Selecione exatamente duas fotos para vê-las lado a lado. As imagens permanecem no armazenamento escolhido por você.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (photos.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NeonIcon(Icons.Default.PhotoLibrary, null, selected = true, size = 58.dp)
                Text("Sua linha do tempo começa aqui", fontWeight = FontWeight.Bold)
                Text("Adicione uma foto e compare com outra no futuro.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    val selected = photo.id in selectedIds
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = MaterialTheme.shapes.large) {
                                PersistedProfilePhoto(
                                    uri = photo.imageUri,
                                    modifier = Modifier.fillMaxWidth().height(260.dp),
                                    contentDescription = "Foto de evolução de ${photo.addedAt.toHistoryDateTime()}",
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(photo.addedAt.toHistoryDateTime(), fontWeight = FontWeight.SemiBold)
                                    if (photo.notes.isNotBlank()) Text(photo.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedIds = when {
                                            selected -> selectedIds - photo.id
                                            selectedIds.size < 2 -> selectedIds + photo.id
                                            else -> selectedIds.drop(1).toSet() + photo.id
                                        }
                                    },
                                    label = { Text(if (selected) "Selecionada" else "Comparar") },
                                )
                                IconButton(onClick = { deleteCandidate = photo }) {
                                    Icon(Icons.Default.DeleteOutline, "Excluir foto", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showComparison && selectedPhotos.size == 2) {
        Dialog(
            onDismissRequest = {
                expandedPhoto = null
                showComparison = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Comparação lado a lado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Toque em uma foto para ampliar sem recorte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        selectedPhotos.forEach { photo ->
                            Column(
                                modifier = Modifier.fillMaxHeight().weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clickable { expandedPhoto = photo },
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    PersistedProfilePhoto(
                                        uri = photo.imageUri,
                                        modifier = Modifier.fillMaxSize(),
                                        contentDescription = "Ampliar foto de ${photo.addedAt.toHistoryDateTime()}",
                                        contentScale = ContentScale.Fit,
                                        maxDecodeDimension = 2_048,
                                    )
                                }
                                Text(
                                    Instant.ofEpochMilli(photo.addedAt)
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            expandedPhoto = null
                            showComparison = false
                        }) { Text("Fechar") }
                    }
                }
            }
        }
    }

    expandedPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { expandedPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black,
            ) {
                Box(Modifier.fillMaxSize()) {
                    ZoomablePersistedProfilePhoto(
                        uri = photo.imageUri,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Foto ampliada de ${photo.addedAt.toHistoryDateTime()}",
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Black.copy(alpha = 0.72f),
                    ) {
                        Text(
                            photo.addedAt.toHistoryDateTime(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = { expandedPhoto = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Use dois dedos para ampliar e arrastar",
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    deleteCandidate?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Excluir esta foto?") },
            text = { Text("A foto adicionada em ${photo.addedAt.toHistoryDateTime()} sairá da linha do tempo. O arquivo original não será apagado do armazenamento.") },
            confirmButton = {
                Button(onClick = {
                    selectedIds = selectedIds - photo.id
                    onDelete(photo)
                    deleteCandidate = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } },
        )
    }
}

private data class CachedProfilePhoto(
    val bitmap: Bitmap,
    val decodedForMaxDimension: Int,
)

private val profilePhotoCache = object : LruCache<String, CachedProfilePhoto>(
    (Runtime.getRuntime().maxMemory() / 1_024L / 8L)
        .toInt()
        .coerceIn(16 * 1_024, 96 * 1_024)
) {
    override fun sizeOf(key: String, value: CachedProfilePhoto): Int =
        (value.bitmap.allocationByteCount / 1_024).coerceAtLeast(1)
}

private fun loadProfilePhoto(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val boundedMaxDimension = maxDimension.coerceIn(640, 3_072)
    val cacheKey = uri.toString()
    profilePhotoCache.get(cacheKey)
        ?.takeIf { !it.bitmap.isRecycled && it.decodedForMaxDimension >= boundedMaxDimension }
        ?.let { return it.bitmap }

    val decoded = decodeProfilePhoto(context, uri, boundedMaxDimension) ?: return null
    return synchronized(profilePhotoCache) {
        val newerCached = profilePhotoCache.get(cacheKey)
        if (
            newerCached != null &&
            !newerCached.bitmap.isRecycled &&
            newerCached.decodedForMaxDimension >= boundedMaxDimension
        ) {
            decoded.recycle()
            newerCached.bitmap
        } else {
            profilePhotoCache.put(
                cacheKey,
                CachedProfilePhoto(decoded, decodedForMaxDimension = boundedMaxDimension),
            )
            decoded
        }
    }
}

private fun decodeProfilePhoto(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sourceWidth = info.size.width
            val sourceHeight = info.size.height
            val sourceMax = maxOf(sourceWidth, sourceHeight)
            if (sourceMax > maxDimension) {
                val ratio = maxDimension.toFloat() / sourceMax.toFloat()
                decoder.setTargetSize(
                    (sourceWidth * ratio).roundToInt().coerceAtLeast(1),
                    (sourceHeight * ratio).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    return scaleBitmapToMaxDimension(applyExifOrientation(decoded, orientation), maxDimension)
}

private fun scaleBitmapToMaxDimension(source: Bitmap, maxDimension: Int): Bitmap {
    val sourceMax = maxOf(source.width, source.height)
    if (sourceMax <= maxDimension) return source
    val ratio = maxDimension.toFloat() / sourceMax.toFloat()
    val scaled = Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).roundToInt().coerceAtLeast(1),
        (source.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== source) source.recycle()
    return scaled
}

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    if (matrix.isIdentity) return source
    val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (oriented !== source) source.recycle()
    return oriented
}

@Composable
private fun MenuCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    InteractiveGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        onClickLabel = "Abrir $title",
        elevation = 5.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, null, selected = true, intensity = 1.05f, size = 34.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChecked)
    }
}

@Composable
private fun WeightChart(points: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(120.dp)) {
        if (points.size < 2) Text("O gráfico surge após dois registros.", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val min = points.minOrNull() ?: 0.0
            val max = points.maxOrNull() ?: 1.0
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            points.forEachIndexed { index, value ->
                val offset = Offset(size.width * index / (points.size - 1), size.height - size.height * ((value - min) / range).toFloat())
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                drawCircle(color, 4.dp.toPx(), offset)
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutHistorySheet(
    sessions: List<SessionEntity>,
    allSets: List<SessionSetEntity>,
    exercises: List<ExerciseEntity>,
    bodyWeightKg: Double?,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val finishedSessions = remember(sessions) {
        sessions.filter { it.finishedAt != null }.sortedByDescending { it.finishedAt }
    }
    val setsBySession = remember(allSets) { allSets.groupBy { it.sessionId } }
    var deleteCandidate by remember { mutableStateOf<SessionEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Histórico de treinos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Veja o que foi planejado e o que você realmente registrou em cada série.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (bodyWeightKg != null) {
                    "Calorias são estimativas MET do Compêndio 2024, usando seu peso atual, a duração e a combinação dos exercícios."
                } else {
                    "Registre seu peso para o app estimar as calorias de cada treino."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (finishedSessions.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonIcon(Icons.Default.History, null, selected = true, intensity = 1.25f, size = 48.dp)
                Text("Nenhum treino finalizado", fontWeight = FontWeight.Bold)
                Text(
                    "Ao finalizar um treino, os exercícios, séries e alterações feitas durante a sessão aparecerão aqui.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(finishedSessions, key = { it.id }) { session ->
                    HistorySessionCard(
                        session = session,
                        sets = setsBySession[session.id].orEmpty()
                            .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber }),
                        exercises = exercises,
                        bodyWeightKg = bodyWeightKg,
                        onDelete = { deleteCandidate = session }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    deleteCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Excluir este treino do histórico?") },
            text = {
                Text("${session.workoutName} • ${session.startedAt.toHistoryDateTime()}\n\nAs séries, cargas e recordes ligados somente a esta sessão serão removidos. Os outros treinos serão preservados.")
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteSession(session.id)
                    deleteCandidate = null
                }) { Text("Excluir treino") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HistorySessionCard(
    session: SessionEntity,
    sets: List<SessionSetEntity>,
    exercises: List<ExerciseEntity>,
    bodyWeightKg: Double?,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val completedSets = sets.count { it.completed }
    val volume = sets.asSequence()
        .filter { it.completed && it.trackingMode.equals("Repetições", ignoreCase = true) }
        .sumOf { it.reps * it.loadKg }
    val groupedExercises = remember(sets) {
        sets.groupBy { "${it.workoutExerciseId}:${it.exerciseId}" }
    }
    val calorieEstimate = remember(session, sets, exercises, bodyWeightKg) {
        WorkoutCalorieEstimator.estimate(session, sets, exercises, bodyWeightKg)
    }

    InteractiveGlassCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        onClickLabel = if (expanded) "Recolher ${session.workoutName}" else "Ver detalhes de ${session.workoutName}",
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        elevation = if (expanded) 7.dp else 4.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.workoutName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${session.status} • ${session.startedAt.toHistoryDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "Excluir ${session.workoutName}", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Recolher" else "Ver detalhes")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistorySummaryValue("Séries", "$completedSets/${sets.size}", Modifier.weight(1f))
                HistorySummaryValue("Duração", historyDuration(session), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistorySummaryValue("Volume", if (volume > 0.0) "${volume.pretty()} kg·reps" else "—", Modifier.weight(1f))
                HistorySummaryValue(
                    "Calorias estimadas",
                    calorieEstimate?.let { "≈${it.kilocalories} kcal" }
                        ?: if (bodyWeightKg == null) "Informe o peso" else "—",
                    Modifier.weight(1f),
                )
            }
            if (session.notes.isNotBlank()) {
                Text("Observações: ${session.notes}", style = MaterialTheme.typography.bodySmall)
            }
            if (expanded) {
                if (sets.isEmpty()) {
                    Text("Esta sessão não possui séries registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    groupedExercises.values.forEachIndexed { index, exerciseSets ->
                        if (index > 0) Spacer(Modifier.height(2.dp))
                        HistoryExerciseBlock(exerciseSets)
                    }
                }
            } else {
                Text(
                    "Toque para ver exercícios e alterações",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HistorySummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryExerciseBlock(sets: List<SessionSetEntity>) {
    val first = sets.first()
    val hasLegacyComparison = sets.any { it.plannedReps == null || it.plannedLoadKg == null }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(first.exerciseName, fontWeight = FontWeight.Bold)
            sets.forEach { set -> HistorySetRow(set) }
            if (hasLegacyComparison) {
                Text(
                    "Comparação planejado → realizado indisponível em séries registradas antes desta versão.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistorySetRow(set: SessionSetEntity) {
    val metric = set.historyMetric()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (set.completed) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Série ${set.setNumber}${if (set.completed) "" else " • não concluída"}",
                Modifier.padding(start = if (set.completed) 6.dp else 0.dp),
                fontWeight = FontWeight.SemiBold,
                color = if (set.completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!set.completed) {
            Text(
                buildString {
                    append("Planejado: ")
                    append(set.plannedReps?.let { "${it.prettyMetric()} ${metric.unit}" } ?: "não registrado")
                    append(" • ")
                    append(set.plannedLoadKg?.let { "${it.pretty()} kg" } ?: "carga não registrada")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            historyChangeLabel(metric.label, set.plannedReps?.toDouble(), metric.actual, metric.unit),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            historyChangeLabel("Carga", set.plannedLoadKg, set.loadKg, "kg"),
            style = MaterialTheme.typography.bodySmall
        )
        if (set.rir != null || set.painLevel > 0) {
            Text(
                buildString {
                    set.rir?.let { append("RIR $it") }
                    if (set.rir != null && set.painLevel > 0) append(" • ")
                    if (set.painLevel > 0) append("Dor ${set.painLevel}/10")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (set.painLevel > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (set.notes.isNotBlank()) {
            Text("Observação: ${set.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class HistoryMetric(val label: String, val actual: Double, val unit: String)

private fun SessionSetEntity.historyMetric(): HistoryMetric = when {
    trackingMode.equals("Tempo", ignoreCase = true) -> HistoryMetric("Tempo", durationSeconds.toDouble(), "s")
    trackingMode.equals("Distância", ignoreCase = true) -> HistoryMetric("Distância", distanceMeters, "m")
    else -> HistoryMetric("Repetições", reps.toDouble(), "reps")
}

private fun historyChangeLabel(label: String, planned: Double?, actual: Double, unit: String): String {
    if (planned == null) return "$label realizado: ${actual.prettyMetric()} $unit"
    val delta = actual - planned
    val deltaLabel = when {
        kotlin.math.abs(delta) < 0.0001 -> "sem alteração"
        delta > 0 -> "+${delta.prettyMetric()} $unit"
        else -> "${delta.prettyMetric()} $unit"
    }
    return "$label: ${planned.prettyMetric()} → ${actual.prettyMetric()} $unit ($deltaLabel)"
}

private fun Int.prettyMetric(): String = toString()

private fun Double.prettyMetric(): String = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.forLanguageTag("pt-BR"), "%.1f", this)

private fun Long.toHistoryDateTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")))

private fun historyDuration(session: SessionEntity): String {
    val finishedAt = session.finishedAt ?: return "—"
    val minutes = Duration.ofMillis((finishedAt - session.startedAt).coerceAtLeast(0L)).toMinutes()
    if (minutes < 1) return "<1 min"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours == 0L) "${minutes}min" else if (remainder == 0L) "${hours}h" else "${hours}h ${remainder}min"
}

@Composable
private fun ProfileDialog(initial: UserProfileEntity, onDismiss: () -> Unit, onSave: (UserProfileEntity) -> Unit) {
    var name by remember { mutableStateOf(initial.nickname) }
    var birth by remember { mutableStateOf(initial.birthYear?.toString() ?: "") }
    var bio by remember { mutableStateOf(initial.objective) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome ou apelido") }, singleLine = true)
            OutlinedTextField(birth, { birth = it.filter(Char::isDigit).take(4) }, label = { Text("Ano de nascimento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") })
        } },
        confirmButton = { Button(onClick = { onSave(initial.copy(nickname = name.trim(), birthYear = birth.toIntOrNull(), objective = bio.trim())) }) { Text("Salvar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun HeightDialog(
    initialHeightCm: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var value by remember(initialHeightCm) { mutableStateOf(initialHeightCm?.pretty() ?: "") }
    val parsedHeight = value.replace(',', '.').toDoubleOrNull()
    val normalizedHeightCm = parsedHeight?.let { if (it <= 3.0) it * 100.0 else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar altura") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.filter { character ->
                        character.isDigit() || character == ',' || character == '.'
                    }.take(6)
                },
                label = { Text("Altura (cm ou m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { normalizedHeightCm?.let(onSave) },
                enabled = normalizedHeightCm?.let { it in 80.0..250.0 } == true,
            ) {
                Text("Salvar")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WeightDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registrar peso") }, text = {
        OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Peso em kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    }, confirmButton = { Button(onClick = { value.replace(',', '.').toDoubleOrNull()?.let(onSave) }, enabled = value.replace(',', '.').toDoubleOrNull()?.let { it in 20.0..500.0 } == true) { Text("Registrar") } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun BmiDialog(profile: UserProfileEntity?, onDismiss: () -> Unit) {
    var weight by remember { mutableStateOf(profile?.currentWeightKg?.pretty() ?: "") }
    var height by remember { mutableStateOf(profile?.heightCm?.pretty() ?: "") }
    var age by remember {
        mutableStateOf(profile?.birthYear?.let { (LocalDate.now().year - it).coerceAtLeast(0).toString() } ?: "")
    }
    var additionalMonths by remember { mutableStateOf("0") }
    var sex by remember { mutableStateOf<BmiSex?>(null) }
    var result by remember { mutableStateOf<BmiResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val parsedHeight = height.replace(',', '.').toDoubleOrNull()
    val parsedAge = age.toIntOrNull()
    val parsedMonths = additionalMonths.toIntOrNull()
    val isGrowthAge = parsedAge != null && parsedAge < 20

    fun invalidateResult() {
        result = null
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HealthAndSafety, null) },
        title = { Text("Calculadora de IMC") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        weight,
                        {
                            weight = it.filter { character -> character.isDigit() || character == ',' || character == '.' }.take(7)
                            invalidateResult()
                        },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        height,
                        {
                            height = it.filter { character -> character.isDigit() || character == ',' || character == '.' }.take(6)
                            invalidateResult()
                        },
                        label = { Text("Altura (cm ou m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            age,
                            {
                                age = it.filter(Char::isDigit).take(3)
                                invalidateResult()
                            },
                            label = { Text("Idade (anos)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            additionalMonths,
                            {
                                additionalMonths = it.filter(Char::isDigit).take(2)
                                invalidateResult()
                            },
                            label = { Text("Meses") },
                            supportingText = { if (!isGrowthAge) Text("Só até 19") },
                            enabled = isGrowthAge,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isGrowthAge) "Sexo da curva de crescimento (obrigatório)" else "Sexo",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BmiSex.entries.forEach { option ->
                                FilterChip(
                                    selected = sex == option,
                                    onClick = {
                                        sex = option
                                        invalidateResult()
                                    },
                                    label = { Text(option.displayName) }
                                )
                            }
                        }
                        Text(
                            if (isGrowthAge) {
                                "Dos 5 aos 19 anos, a OMS usa curvas diferentes por idade em meses e sexo."
                            } else {
                                "Em adultos, o sexo não altera a fórmula nem os pontos de corte do IMC."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                result?.let { value ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                String.format(Locale.forLanguageTag("pt-BR"), "Seu IMC: %.2f", value.value),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                value.classification ?: "Classificação indisponível para os dados informados.",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                value.referenceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            value.zScore?.let { zScore ->
                                Text(
                                    String.format(Locale.forLanguageTag("pt-BR"), "Escore-z do IMC para idade: %.2f", zScore),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    value.notice?.let { notice ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                Text(notice, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (value.healthyWeightRange != null && value.weightAdjustment != null) {
                        item { PersonalizedBmiGuidance(value) }
                    }
                    if (value.adultRanges.isNotEmpty()) {
                        item {
                            Text(
                                if (value.usesGrowthReference) "Faixas para esta idade, sexo e altura" else "Todas as faixas para a sua altura",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        value.adultRanges.forEach { range -> item { BmiRangeCard(range) } }
                    }
                }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item {
                    Text(
                        "O IMC é uma medida de triagem. As diferenças de peso acima são estimativas matemáticas, não metas clínicas. Massa muscular, composição corporal e condições individuais exigem avaliação profissional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsedWeight != null && parsedHeight != null && parsedAge != null && parsedMonths != null &&
                    parsedAge in 5..120 && parsedMonths in 0..11 && (!isGrowthAge || sex != null),
                onClick = {
                    runCatching {
                        BmiCalculator.calculateForAge(
                            weightKg = requireNotNull(parsedWeight),
                            heightInput = requireNotNull(parsedHeight),
                            ageYears = requireNotNull(parsedAge),
                            additionalMonths = if (parsedAge < 20) requireNotNull(parsedMonths) else 0,
                            sex = sex
                        )
                    }
                        .onSuccess { result = it; error = null }
                        .onFailure { error = it.message ?: "Confira o peso, a altura e a idade informados." }
                }
            ) { Text("Calcular") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun PersonalizedBmiGuidance(result: BmiResult) {
    val range = result.healthyWeightRange ?: return
    val adjustment = result.weightAdjustment ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Referência de faixa adequada", fontWeight = FontWeight.Bold)
            Text("Para sua altura: ${range.minimumKg.pretty()} a ${range.maximumKg.pretty()} kg")
            Text(
                when (adjustment.direction) {
                    WeightAdjustmentDirection.GAIN -> "Estimativa: ganhar cerca de ${adjustment.kilograms.pretty()} kg para chegar ao limite inferior (${adjustment.targetWeightKg.pretty()} kg)."
                    WeightAdjustmentDirection.LOSE -> "Estimativa: perder cerca de ${adjustment.kilograms.pretty()} kg para chegar ao limite superior (${adjustment.targetWeightKg.pretty()} kg)."
                    WeightAdjustmentDirection.MAINTAIN -> "Seu peso já está dentro da faixa considerada adequada pelo IMC."
                }
            )
        }
    }
}

@Composable
private fun BmiRangeCard(range: BmiRange) {
    val bmiLabel = when {
        range.minimumBmi == null -> if (range.maximumInclusive) {
            "IMC até ${range.maximumBmiExclusive!!.pretty()}"
        } else {
            "IMC abaixo de ${range.maximumBmiExclusive!!.pretty()}"
        }
        range.maximumBmiExclusive == null -> if (range.minimumInclusive) {
            "IMC a partir de ${range.minimumBmi.pretty()}"
        } else {
            "IMC acima de ${range.minimumBmi.pretty()}"
        }
        else -> {
            val lower = if (range.minimumInclusive) "a partir de" else "acima de"
            val upper = if (range.maximumInclusive) "até" else "abaixo de"
            "IMC $lower ${range.minimumBmi.pretty()} e $upper ${range.maximumBmiExclusive.pretty()}"
        }
    }
    val weightLabel = when {
        range.minimumWeightKg == null -> if (range.maximumInclusive) {
            "Até ${range.maximumWeightKgExclusive!!.pretty()} kg para esta altura"
        } else {
            "Abaixo de ${range.maximumWeightKgExclusive!!.pretty()} kg para esta altura"
        }
        range.maximumWeightKgExclusive == null -> if (range.minimumInclusive) {
            "A partir de ${range.minimumWeightKg.pretty()} kg para esta altura"
        } else {
            "Acima de ${range.minimumWeightKg.pretty()} kg para esta altura"
        }
        else -> {
            val lower = if (range.minimumInclusive) "A partir de" else "Acima de"
            val upper = if (range.maximumInclusive) "até" else "abaixo de"
            "$lower ${range.minimumWeightKg.pretty()} kg e $upper ${range.maximumWeightKgExclusive.pretty()} kg"
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (range.isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(range.classification, fontWeight = FontWeight.SemiBold)
                Text(bmiLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(weightLabel, style = MaterialTheme.typography.bodySmall)
            }
            if (range.isCurrent) Text("Sua faixa", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Privacidade no Liftly") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Por padrão, treinos, cargas, metas e dados corporais ficam somente no banco local deste dispositivo. O Liftly não exige conta, não possui anúncios, telemetria ou analytics.")
            Text("A exportação automática para Discord é opcional e permanece desligada até você salvar um webhook e ativá-la. Quando ativa, envia ao canal o resumo do treino finalizado; fotos e o próprio webhook nunca entram na mensagem. A URL fica no armazenamento privado do app e não entra no backup.")
            Text("Ao abrir a aba Música, o Liftly consulta uma configuração pública e o oEmbed do Spotify para mostrar a playlist selecionada. Não recebe senha, token ou histórico musical; reprodução e login acontecem no aplicativo ou site do Spotify.")
            Text("Notificação, som e vibração acompanham o treino, o descanso e, se habilitado, metas/sequências. O som usa a saída de mídia/fones e pode abaixar outra mídia apenas durante o aviso.")
            Text("As calorias são estimadas localmente por MET, peso, duração e combinação dos exercícios. Elas são uma referência populacional, não uma medição clínica ou de relógio/sensor.")
            Text("A foto de perfil e as fotos de evolução são lidas apenas dos arquivos escolhidos no seletor do Android. O backup guarda os registros e URIs das fotos, mas não copia os arquivos de imagem; em outro aparelho eles precisam ser selecionados novamente.")
            Text("Você pode exportar um backup ou apagar todos os dados a qualquer momento. Proteja o arquivo JSON exportado, pois ele contém as informações que você registrou.")
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Entendi") } })
}

private fun Double.pretty(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)
