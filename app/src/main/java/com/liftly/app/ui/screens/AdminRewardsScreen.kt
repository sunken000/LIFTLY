package com.liftly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftly.app.BuildConfig
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.LiftlyBackground
import kotlin.math.roundToInt

/**
 * Read-only representation of the rewards economy shown by the admin tools.
 *
 * This deliberately has no dependency on Room, repositories or a ViewModel. The owner of the
 * screen maps the real rewards state into this object and handles every command through callbacks.
 */
data class AdminRewardsUiState(
    val coins: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val currentStreakDays: Int = 0,
    val completedMissions: Int = 0,
    val unlockedItems: Int = 0,
    val totalItems: Int = 0,
    val viewingAsRegularUser: Boolean = false,
)

enum class AdminWorkoutSimulation(
    val title: String,
    val description: String,
) {
    STANDARD(
        title = "Treino padrão",
        description = "Conclusão válida com a recompensa base.",
    ),
    COMPLETE(
        title = "Treino completo",
        description = "Todas as séries, RIR e descanso registrados.",
    ),
    PERSONAL_RECORD(
        title = "Treino com recorde",
        description = "Conclusão completa com um novo recorde pessoal.",
    ),
}

enum class AdminMissionSimulation(
    val title: String,
    val description: String,
) {
    DAILY(
        title = "Missão diária",
        description = "Simula uma missão diária concluída.",
    ),
    WEEKLY(
        title = "Missão semanal",
        description = "Simula a conclusão da meta da semana.",
    ),
    MONTHLY(
        title = "Desafio mensal",
        description = "Simula o desafio principal do mês.",
    ),
}

/**
 * Administrative rewards test panel. It is compiled for every flavor so shared navigation can
 * reference it safely, but it only exposes its controls when [BuildConfig.ADMIN_TOOLS] is true.
 */
@Composable
fun AdminRewardsScreen(
    state: AdminRewardsUiState,
    modifier: Modifier = Modifier,
    onViewAsRegularUserChange: (Boolean) -> Unit,
    onAddCoins: (Int) -> Unit,
    onRemoveCoins: (Int) -> Unit,
    onAddXp: (Int) -> Unit,
    onRemoveXp: (Int) -> Unit,
    onSimulateWorkout: (AdminWorkoutSimulation) -> Unit,
    onSimulateMission: (AdminMissionSimulation) -> Unit,
    onUnlockAllItems: () -> Unit,
    onResetEconomy: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (!BuildConfig.ADMIN_TOOLS) {
        AdminToolsUnavailable(modifier = modifier, onBack = onBack)
        return
    }

    var amountInput by remember { mutableStateOf("100") }
    var confirmReset by remember { mutableStateOf(false) }
    val amount = amountInput.toIntOrNull()?.coerceIn(1, 100_000)

    LiftlyBackground(modifier = modifier.fillMaxSize(), showGlow = false) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AdminRewardsHeader(onBack = onBack)
            }
            item {
                PreviewModeCard(
                    checked = state.viewingAsRegularUser,
                    onCheckedChange = onViewAsRegularUserChange,
                )
            }
            item {
                RewardsSnapshotCard(state)
            }

            if (state.viewingAsRegularUser) {
                item {
                    RegularUserPreview(state)
                }
            } else {
                item {
                    BalanceControls(
                        amountInput = amountInput,
                        amount = amount,
                        onAmountChange = { input ->
                            amountInput = input.filter(Char::isDigit).take(6)
                        },
                        onUsePreset = { amountInput = it.toString() },
                        onAddCoins = { amount?.let(onAddCoins) },
                        onRemoveCoins = { amount?.let(onRemoveCoins) },
                        onAddXp = { amount?.let(onAddXp) },
                        onRemoveXp = { amount?.let(onRemoveXp) },
                    )
                }
                item {
                    SimulationSection(
                        title = "Simular treino",
                        subtitle = "Valide recompensas, sequência e recordes sem concluir um treino real.",
                        icon = Icons.Default.FitnessCenter,
                    ) {
                        AdminWorkoutSimulation.entries.forEach { scenario ->
                            SimulationAction(
                                title = scenario.title,
                                description = scenario.description,
                                onClick = { onSimulateWorkout(scenario) },
                            )
                        }
                    }
                }
                item {
                    SimulationSection(
                        title = "Simular missão",
                        subtitle = "Teste cada ciclo de missão e sua recompensa.",
                        icon = Icons.Default.CheckCircle,
                    ) {
                        AdminMissionSimulation.entries.forEach { scenario ->
                            SimulationAction(
                                title = scenario.title,
                                description = scenario.description,
                                onClick = { onSimulateMission(scenario) },
                            )
                        }
                    }
                }
                item {
                    CatalogAndResetCard(
                        unlockedItems = state.unlockedItems,
                        totalItems = state.totalItems,
                        onUnlockAllItems = onUnlockAllItems,
                        onReset = { confirmReset = true },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
            title = { Text("Resetar economia de teste?") },
            text = {
                Text(
                    "Moedas, XP, nível, missões, sequência e itens desbloqueados voltarão ao estado inicial definido pela economia.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        onResetEconomy()
                    },
                ) {
                    Text("Resetar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun AdminRewardsHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(23.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Rewards Lab",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Ambiente administrativo de testes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Text(
                text = "ADM",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun PreviewModeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Visualizar como usuário comum", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (checked) {
                        "Controles administrativos ocultos. A carteira aparece como no app normal."
                    } else {
                        "Ative para conferir a experiência sem atalhos de teste."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun RewardsSnapshotCard(state: AdminRewardsUiState) {
    GlassCard(contentPadding = PaddingValues(18.dp)) {
        Text("Estado atual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SnapshotMetric(
                icon = Icons.Default.Storefront,
                value = state.coins.toString(),
                label = "Lift Coins",
                modifier = Modifier.weight(1f),
            )
            SnapshotMetric(
                icon = Icons.Default.Bolt,
                value = state.xp.toString(),
                label = "XP total",
                modifier = Modifier.weight(1f),
            )
            SnapshotMetric(
                icon = Icons.Default.Stars,
                value = state.level.toString(),
                label = "Nível",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Progresso do nível · ${(state.levelProgress.coerceIn(0f, 1f) * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { state.levelProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp),
        )
    }
}

@Composable
private fun SnapshotMetric(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RegularUserPreview(state: AdminRewardsUiState) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Sua jornada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Esta é a prévia limpa que o usuário comum verá: saldo, evolução e conquistas, sem nenhum botão administrativo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        UserPreviewRow(Icons.Default.EmojiEvents, "Sequência atual", "${state.currentStreakDays} dias")
        UserPreviewRow(Icons.Default.CheckCircle, "Missões concluídas", state.completedMissions.toString())
        UserPreviewRow(
            Icons.Default.LockOpen,
            "Itens desbloqueados",
            "${state.unlockedItems}/${state.totalItems}",
        )
    }
}

@Composable
private fun UserPreviewRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BalanceControls(
    amountInput: String,
    amount: Int?,
    onAmountChange: (String) -> Unit,
    onUsePreset: (Int) -> Unit,
    onAddCoins: () -> Unit,
    onRemoveCoins: () -> Unit,
    onAddXp: () -> Unit,
    onRemoveXp: () -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(18.dp)) {
        Text("Saldo e nível", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Aplique valores temporários para testar compras, níveis e estados sem saldo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Quantidade") },
            supportingText = {
                if (amount == null) Text("Informe um valor entre 1 e 100.000")
            },
            isError = amount == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 500, 1_000).forEach { preset ->
                OutlinedButton(
                    onClick = { onUsePreset(preset) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(if (preset == 1_000) "1.000" else preset.toString())
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddCoins, enabled = amount != null, modifier = Modifier.weight(1f)) {
                Text("+ Moedas")
            }
            OutlinedButton(onClick = onRemoveCoins, enabled = amount != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RemoveCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  Moedas")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAddXp, enabled = amount != null, modifier = Modifier.weight(1f)) {
                Text("+ XP")
            }
            OutlinedButton(onClick = onRemoveXp, enabled = amount != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RemoveCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  XP")
            }
        }
    }
}

@Composable
private fun SimulationSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SimulationAction(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CatalogAndResetCard(
    unlockedItems: Int,
    totalItems: Int,
    onUnlockAllItems: () -> Unit,
    onReset: () -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(18.dp)) {
        Text("Catálogo e limpeza", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "$unlockedItems de $totalItems itens liberados nesta conta de teste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onUnlockAllItems, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LockOpen, contentDescription = null)
            Text("  Desbloquear todo o catálogo")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Text("  Resetar economia de teste")
        }
    }
}

@Composable
private fun AdminToolsUnavailable(
    modifier: Modifier,
    onBack: (() -> Unit)?,
) {
    LiftlyBackground(modifier = modifier.fillMaxSize(), showGlow = false) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Ferramentas disponíveis somente no Liftly Admin",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A versão comum não contém comandos para alterar a economia.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onBack != null) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onBack) {
                    Text("Voltar")
                }
            }
        }
    }
}
