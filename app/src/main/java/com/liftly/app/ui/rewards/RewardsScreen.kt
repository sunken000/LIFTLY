package com.liftly.app.ui.rewards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton
import com.liftly.app.ui.components.InteractiveGlassCard
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.components.SectionHeader
import com.liftly.app.ui.theme.LiftlyTheme
import com.liftly.app.ui.theme.liftlyColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Complete presentation layer for Liftly Rewards.
 *
 * The screen owns only ephemeral presentation state (open preview and confirmation). Balance,
 * ownership and mission progress always come from [RewardsUiState], while persistence and
 * business rules remain outside the presentation layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    state: RewardsUiState,
    actions: RewardsActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var previewItem by remember { mutableStateOf<RewardItemUi?>(null) }
    var pendingPurchase by remember { mutableStateOf<RewardItemUi?>(null) }
    var inventoryOpen by remember { mutableStateOf(false) }
    var activityOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    onBack?.let { goBack ->
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                title = {
                    Column {
                        Text("Conquistas", fontWeight = FontWeight.Bold)
                        Text(
                            "Seu histórico vira identidade, não obrigação",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            inventoryOpen = true
                            actions.onOpenInventory()
                        },
                    ) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Meus itens")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                RewardsAccountCard(
                    account = state.account,
                    onHistory = {
                        activityOpen = true
                        actions.onOpenHistory()
                    },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (state.featuredItems.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Em destaque",
                            subtitle = "Uma seleção curta para personalizar sua experiência.",
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.featuredItems, key = RewardItemUi::id) { item ->
                                FeaturedRewardCard(item = item, onClick = { previewItem = item })
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Loja",
                        subtitle = "Personalização conquistada com treino real.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(RewardCategory.entries, key = RewardCategory::name) { category ->
                            FilterChip(
                                selected = state.selectedCategory == category,
                                onClick = { actions.onCategorySelected(category) },
                                label = { Text(category.label) },
                                leadingIcon = category.takeUnless { it == RewardCategory.All }?.let {
                                    { Icon(it.icon(), contentDescription = null, Modifier.size(18.dp)) }
                                },
                            )
                        }
                    }
                }
            }

            items(
                items = state.visibleItems.chunked(2),
                key = { row -> row.joinToString(separator = ":", transform = RewardItemUi::id) },
            ) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    rowItems.forEach { item ->
                        RewardCatalogCard(
                            item = item,
                            canAfford = state.account.coinBalance >= item.price,
                            onClick = { previewItem = item },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                MissionsSection(
                    missions = state.visibleMissions,
                    selectedPeriod = state.selectedMissionPeriod,
                    onPeriodSelected = actions.onMissionPeriodSelected,
                    onClaim = actions.onClaimMission,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    previewItem?.let { item ->
        RewardPreviewSheet(
            item = item,
            coinBalance = state.account.coinBalance,
            onDismiss = { previewItem = null },
            onEquip = {
                actions.onEquip(item)
                previewItem = null
            },
            onBuy = { pendingPurchase = item },
        )
    }

    if (inventoryOpen) {
        InventorySheet(
            items = state.ownedItems,
            onDismiss = { inventoryOpen = false },
            onSelect = {
                inventoryOpen = false
                previewItem = it
            },
        )
    }

    if (activityOpen) {
        RewardActivitySheet(
            activity = state.activity,
            onDismiss = { activityOpen = false },
        )
    }

    pendingPurchase?.let { item ->
        PurchaseConfirmation(
            item = item,
            currentBalance = state.account.coinBalance,
            onDismiss = { pendingPurchase = null },
            onConfirm = {
                actions.onBuy(item)
                pendingPurchase = null
                previewItem = null
            },
        )
    }
}

@Composable
private fun RewardsAccountCard(
    account: RewardsAccountUi,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), elevation = 4.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(account.level.toString(), fontWeight = FontWeight.Black)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Nível ${account.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(account.levelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.AccountBalanceWallet, null, selected = true, size = 26.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(account.coinBalance.coins(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Lift Coins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Próximo nível", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${account.currentXp.formatNumber()} / ${account.nextLevelXp.formatNumber()} XP",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { account.levelProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.liftlyColors.warning)
                Text(
                    "${account.workoutStreak} semanas cumprindo sua meta",
                    Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onHistory) { Text("Extrato") }
            }
        }
    }
}

@Composable
private fun FeaturedRewardCard(item: RewardItemUi, onClick: () -> Unit) {
    InteractiveGlassCard(
        onClick = onClick,
        modifier = Modifier.width(268.dp),
        contentPadding = PaddingValues(0.dp),
        elevation = 3.dp,
    ) {
        RewardArtwork(item, Modifier.fillMaxWidth().height(132.dp), large = true)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OwnershipBadge(item)
            }
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PriceLabel(item)
        }
    }
}

@Composable
private fun RewardCatalogCard(
    item: RewardItemUi,
    canAfford: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InteractiveGlassCard(
        onClick = onClick,
        modifier = modifier,
        enabled = item.available || item.owned,
        contentPadding = PaddingValues(0.dp),
        elevation = 1.dp,
    ) {
        RewardArtwork(item, Modifier.fillMaxWidth().aspectRatio(1.42f), large = false)
        Column(
            modifier = Modifier.padding(14.dp).heightIn(min = 116.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.rarity.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (!item.owned && item.available && !canAfford) {
                Text("Saldo insuficiente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            PriceLabel(item)
        }
    }
}

@Composable
private fun RewardArtwork(item: RewardItemUi, modifier: Modifier, large: Boolean) {
    val colors = item.previewColors.map { Color(it) }.ifEmpty {
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primaryContainer)
    }
    Box(
        modifier = modifier.background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.82f, size.height * 0.12f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.10f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.12f, size.height * 0.95f),
            )
        }
        Surface(
            shape = RoundedCornerShape(if (large) 22.dp else 16.dp),
            color = Color.Black.copy(alpha = 0.24f),
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = item.visual.icon(),
                contentDescription = null,
                modifier = Modifier.padding(if (large) 22.dp else 15.dp).size(if (large) 38.dp else 28.dp),
            )
        }
        item.limitedLabel?.let { label ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
            ) {
                Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PriceLabel(item: RewardItemUi) {
    when {
        item.equipped -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, Modifier.size(17.dp), tint = MaterialTheme.liftlyColors.success)
                Text("Equipado", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.liftlyColors.success)
            }
        }
        item.owned -> Text("Disponível", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        !item.available -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" Bloqueado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item.price == 0 -> Text("Conquista", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(" ${item.price.coins()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OwnershipBadge(item: RewardItemUi) {
    val label = when {
        item.equipped -> "Em uso"
        item.owned -> "Adquirido"
        else -> null
    } ?: return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MissionsSection(
    missions: List<RewardMissionUi>,
    selectedPeriod: MissionPeriod,
    onPeriodSelected: (MissionPeriod) -> Unit,
    onClaim: (RewardMissionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Marcos de treino",
            subtitle = "Metas semanais e mensais, sem punição por perder um dia.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MissionPeriod.entries.filterNot { it == MissionPeriod.Daily }.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.label) },
                    leadingIcon = {
                        Icon(period.icon(), contentDescription = null, Modifier.size(17.dp))
                    },
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            if (missions.isEmpty()) {
                Text(
                    "Nenhuma missão disponível neste período.",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                missions.forEachIndexed { index, mission ->
                    MissionRow(mission = mission, onClaim = { onClaim(mission) })
                    if (index != missions.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                        )
                    }
                }
            }
        }
        Text(
            "Treinos antigos também entram no histórico de recompensas. Não existe perda de XP por descanso ou por quebrar sequência, e um mesmo treino nunca paga duas vezes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissionRow(mission: RewardMissionUi, onClaim: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (mission.completed) MaterialTheme.liftlyColors.successContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (mission.completed) MaterialTheme.liftlyColors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    imageVector = if (mission.completed) Icons.Rounded.Check else Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(mission.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(mission.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+${mission.coinReward}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("moedas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { mission.progressFraction },
                modifier = Modifier.weight(1f).height(7.dp),
                color = if (mission.completed) MaterialTheme.liftlyColors.success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Text(
                "${mission.progress.coerceAtMost(mission.target)}/${mission.target}",
                Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mission.completed) {
            Button(
                onClick = onClaim,
                enabled = !mission.claimed,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(if (mission.claimed) Icons.Rounded.CheckCircle else Icons.Rounded.Redeem, contentDescription = null)
                Text(if (mission.claimed) "Recompensa recebida" else "Receber +${mission.coinReward} moedas e +${mission.xpReward} XP", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardPreviewSheet(
    item: RewardItemUi,
    coinBalance: Int,
    onDismiss: () -> Unit,
    onEquip: () -> Unit,
    onBuy: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RewardArtwork(item, Modifier.fillMaxWidth().height(220.dp), large = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.rarity.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                PriceLabel(item)
            }
            Text(item.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (item.visual) {
                    RewardVisual.Theme -> "A prévia representa fundo, superfícies e contraste do tema. Você poderá trocar quando quiser."
                    RewardVisual.Wallpaper -> "O movimento respeita a configuração de animações do aparelho e pode ser desativado."
                    RewardVisual.ProfileFrame, RewardVisual.ProfileTitle -> "Após adquirir, equipe este item em Perfil > Personalização."
                    RewardVisual.Timer -> "Este visual altera apenas a apresentação do cronômetro; seus dados continuam iguais."
                    RewardVisual.Sound -> "Você poderá testar e ajustar o volume antes de equipar o som."
                    RewardVisual.Chart -> "Aplica um novo estilo aos gráficos sem alterar seus registros."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                item.equipped -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("Equipado", Modifier.padding(start = 8.dp))
                }
                item.owned -> GradientActionButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("Equipar")
                }
                !item.available -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                    Text(item.limitedLabel ?: "Ainda não disponível", Modifier.padding(start = 8.dp))
                }
                coinBalance < item.price -> {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null)
                        Text("Faltam ${(item.price - coinBalance).coins()}", Modifier.padding(start = 8.dp))
                    }
                    Text(
                        "Conclua missões para aumentar seu saldo.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> GradientActionButton(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null)
                    Text("Comprar por ${item.price.coins()}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySheet(
    items: List<RewardItemUi>,
    onDismiss: () -> Unit,
    onSelect: (RewardItemUi) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Meus itens",
                subtitle = "${items.size} adquirido${if (items.size == 1) "" else "s"} • escolha o que deseja equipar.",
            )
            if (items.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Seu inventário está vazio", fontWeight = FontWeight.Bold)
                    Text("Conclua missões e explore a loja para adquirir o primeiro item.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items.forEach { item ->
                    InteractiveGlassCard(
                        onClick = { onSelect(item) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RewardArtwork(item, Modifier.size(68.dp), large = false)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(item.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OwnershipBadge(item)
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardActivitySheet(
    activity: List<RewardActivityUi>,
    onDismiss: () -> Unit,
) {
    val emptyStateTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = "Extrato de recompensas",
                subtitle = "Créditos e compras ficam registrados neste aparelho.",
            )
            if (activity.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
                    Text("Nenhuma movimentação ainda", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Conclua um treino válido ou uma missão para receber XP e Lift Coins.",
                        color = emptyStateTextColor,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(activity, key = RewardActivityUi::id) { entry ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${entry.detail} • ${entry.occurredAt.rewardDate()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (entry.xp != 0) Text(entry.xp.signed("XP"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    if (entry.coins != 0) Text(entry.coins.signed("LC"), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseConfirmation(
    item: RewardItemUi,
    currentBalance: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ShoppingBag, contentDescription = null) },
        title = { Text("Confirmar compra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Comprar ${item.title} por ${item.price.coins()}?")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Saldo após a compra", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((currentBalance - item.price).coerceAtLeast(0).coins(), fontWeight = FontWeight.Bold)
                    }
                }
                Text("O item ficará disponível permanentemente em Meus itens.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Comprar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun RewardCategory.icon(): ImageVector = when (this) {
    RewardCategory.All -> Icons.Rounded.ShoppingBag
    RewardCategory.Themes -> Icons.Rounded.Palette
    RewardCategory.Wallpapers -> Icons.Rounded.Wallpaper
    RewardCategory.Profile -> Icons.Rounded.Person
    RewardCategory.Focus -> Icons.Rounded.Timer
    RewardCategory.Sounds -> Icons.Rounded.Headphones
}

private fun RewardVisual.icon(): ImageVector = when (this) {
    RewardVisual.Theme -> Icons.Rounded.Palette
    RewardVisual.Wallpaper -> Icons.Rounded.Wallpaper
    RewardVisual.ProfileFrame -> Icons.Rounded.Person
    RewardVisual.ProfileTitle -> Icons.Rounded.AutoAwesome
    RewardVisual.Timer -> Icons.Rounded.Timer
    RewardVisual.Sound -> Icons.Rounded.Headphones
    RewardVisual.Chart -> Icons.Rounded.BarChart
}

private fun MissionPeriod.icon(): ImageVector = when (this) {
    MissionPeriod.Daily -> Icons.Rounded.Bolt
    MissionPeriod.Weekly -> Icons.Rounded.CalendarMonth
    MissionPeriod.Monthly -> Icons.Rounded.EmojiEvents
}

private val ptBrNumberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))
private fun Int.formatNumber(): String = synchronized(ptBrNumberFormat) { ptBrNumberFormat.format(this) }
private fun Int.coins(): String = "${formatNumber()} LC"
private fun Int.signed(unit: String): String = "${if (this > 0) "+" else ""}${formatNumber()} $unit"
private val rewardDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM • HH:mm", Locale("pt", "BR"))
private fun Long.rewardDate(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(rewardDateFormatter)

@Preview(showBackground = true, backgroundColor = 0xFF08070A, heightDp = 1500)
@Composable
private fun RewardsScreenPreview() {
    LiftlyTheme(themeMode = "preto") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            RewardsScreen(
                state = RewardsPreviewData.state,
                actions = RewardsActions(),
            )
        }
    }
}
