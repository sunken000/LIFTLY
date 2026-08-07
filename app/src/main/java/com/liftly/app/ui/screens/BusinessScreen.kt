package com.liftly.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftly.app.commercial.CommercialBillingAvailability
import com.liftly.app.commercial.CommercialBillingGateway
import com.liftly.app.commercial.CommercialFeatureCatalog
import com.liftly.app.commercial.CommercialPlan
import com.liftly.app.commercial.CommercialPlanCatalog
import com.liftly.app.commercial.CommercialPriceOption
import com.liftly.app.commercial.CommercialPurchaseResult
import com.liftly.app.commercial.CommercialSalesChannel
import com.liftly.app.commercial.UnavailableCommercialBillingGateway
import com.liftly.app.commercial.purchaseRequest
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton
import com.liftly.app.ui.components.LiftlyBackground
import kotlinx.coroutines.launch

data class BusinessDashboardSnapshot(
    val gymName: String = "Sua academia",
    val activeMembers: Int? = null,
    val workoutsLast30Days: Int? = null,
    val adherencePercent: Int? = null,
)

/**
 * Commercial edition home.
 *
 * Integration:
 * ```
 * BusinessScreen(
 *     dashboard = dashboard,
 *     billingGateway = billingGateway,
 *     onPlanInterest = { plan -> openSalesContact(plan.id) },
 *     onOpenActivationGuide = { navigator.openCommercialSetup() },
 * )
 * ```
 *
 * [billingGateway] is unavailable by default and cannot simulate a successful purchase.
 */
@Composable
fun BusinessScreen(
    modifier: Modifier = Modifier,
    dashboard: BusinessDashboardSnapshot = BusinessDashboardSnapshot(),
    billingGateway: CommercialBillingGateway = UnavailableCommercialBillingGateway,
    onPlanInterest: (CommercialPlan) -> Unit = {},
    onOpenActivationGuide: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val billingState by billingGateway.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }

    LiftlyBackground(modifier = modifier.fillMaxSize(), showGlow = false) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                BusinessHeader(
                    gymName = dashboard.gymName,
                    onBack = onBack,
                )
            }
            item {
                DashboardSummary(dashboard)
            }
            item {
                BillingSafetyCard(
                    availability = billingState.availability,
                    message = billingState.statusMessage,
                    feedback = feedback,
                    onRefresh = {
                        scope.launch {
                            billingGateway.refresh()
                            feedback = billingGateway.state.value.statusMessage
                        }
                    },
                    onOpenActivationGuide = onOpenActivationGuide,
                )
            }
            item {
                SectionTitle(
                    title = "Planos",
                    subtitle = "Preços transparentes para uso individual e operações de diferentes tamanhos.",
                )
            }
            items(
                items = CommercialPlanCatalog.all,
                key = CommercialPlan::id,
            ) { plan ->
                CommercialPlanCard(
                    plan = plan,
                    billingReady = billingState.canStartPurchase,
                    onSelectPrice = { price ->
                        when (plan.salesChannel) {
                            CommercialSalesChannel.SALES_CONTRACT -> onPlanInterest(plan)
                            CommercialSalesChannel.PLAY_STORE -> {
                                val request = plan.purchaseRequest(price)
                                if (request == null) {
                                    feedback = "Este plano ainda não possui produto configurado."
                                } else if (!billingState.canStartPurchase) {
                                    feedback = "Ative a Play Store e o servidor antes de oferecer assinaturas."
                                    onOpenActivationGuide()
                                } else {
                                    scope.launch {
                                        feedback = when (val result = billingGateway.startPurchase(request)) {
                                            is CommercialPurchaseResult.Launched ->
                                                "Compra aberta com segurança pela Play Store."
                                            is CommercialPurchaseResult.Completed ->
                                                if (result.entitlements.verifiedByBackend) {
                                                    "Assinatura confirmada pelo servidor."
                                                } else {
                                                    "Aguardando validação segura da assinatura."
                                                }
                                            is CommercialPurchaseResult.Cancelled -> "Compra cancelada."
                                            is CommercialPurchaseResult.Unavailable -> result.reason
                                            is CommercialPurchaseResult.Failed -> result.userMessage
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
            item {
                OperationsBenefits()
            }
        }
    }
}

@Composable
private fun BusinessHeader(
    gymName: String,
    onBack: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = "LIFTLY BUSINESS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = gymName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "A operação da academia, sem perder de vista o treino de cada aluno.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardSummary(snapshot: BusinessDashboardSnapshot) {
    GlassCard(
        contentPadding = PaddingValues(18.dp),
        elevation = 1.dp,
    ) {
        Text(
            text = "Visão geral",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashboardMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Groups,
                value = snapshot.activeMembers?.toString() ?: "—",
                label = "Alunos ativos",
            )
            DashboardMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Insights,
                value = snapshot.workoutsLast30Days?.toString() ?: "—",
                label = "Treinos / 30d",
            )
            DashboardMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Verified,
                value = snapshot.adherencePercent?.let { "${it.coerceIn(0, 100)}%" } ?: "—",
                label = "Adesão",
            )
        }
        if (
            snapshot.activeMembers == null &&
            snapshot.workoutsLast30Days == null &&
            snapshot.adherencePercent == null
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Os indicadores aparecerão quando a conta da academia estiver conectada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardMetric(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun BillingSafetyCard(
    availability: CommercialBillingAvailability,
    message: String,
    feedback: String?,
    onRefresh: () -> Unit,
    onOpenActivationGuide: () -> Unit,
) {
    val isReady = availability == CommercialBillingAvailability.READY
    val container = if (isReady) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (isReady) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    GlassCard(
        containerColor = container,
        contentColor = content,
        contentPadding = PaddingValues(18.dp),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = content.copy(alpha = 0.10f),
                contentColor = content,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Default.Verified else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(21.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (isReady) "Cobrança segura ativa" else "Cobrança ainda não ativada",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!feedback.isNullOrBlank() && feedback != message) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onRefresh) {
                Text("Rever status")
            }
            if (!isReady) {
                FilledTonalButton(onClick = onOpenActivationGuide) {
                    Text("Como ativar")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommercialPlanCard(
    plan: CommercialPlan,
    billingReady: Boolean,
    onSelectPrice: (CommercialPriceOption) -> Unit,
) {
    val features = remember(plan.id) { CommercialFeatureCatalog.featuresFor(plan) }
    val primaryPrice = plan.priceOptions.first()

    GlassCard(
        containerColor = if (plan.featured) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = PaddingValues(20.dp),
        elevation = if (plan.featured) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (plan.featured) {
                    Text(
                        text = "MAIS ESCOLHIDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = plan.capacityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = when (plan.salesChannel) {
                    CommercialSalesChannel.PLAY_STORE -> Icons.Default.Storefront
                    CommercialSalesChannel.SALES_CONTRACT -> Icons.Default.AdminPanelSettings
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = primaryPrice.displayPrice,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (plan.priceOptions.size > 1) {
            Text(
                text = plan.priceOptions.drop(1).joinToString("  •  ") { it.displayPrice },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = plan.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(14.dp))
        features.take(6).forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).size(17.dp),
                )
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (features.size > 6) {
            Text(
                text = "+ ${features.size - 6} recursos corporativos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 26.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        when (plan.salesChannel) {
            CommercialSalesChannel.SALES_CONTRACT -> {
                GradientActionButton(
                    onClick = { onSelectPrice(primaryPrice) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Falar sobre o plano", fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            CommercialSalesChannel.PLAY_STORE -> {
                OutlinedButton(
                    onClick = { onSelectPrice(primaryPrice) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = if (billingReady) "Assinar pela Play Store" else "Configurar assinatura",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationsBenefits() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle(
            title = "Feito para a operação real",
            subtitle = "Uma base comercial só é útil quando reduz trabalho e protege os dados.",
        )
        Spacer(Modifier.height(12.dp))
        GlassCard(
            contentPadding = PaddingValues(18.dp),
            elevation = 1.dp,
        ) {
            BenefitRow(
                icon = Icons.Default.Groups,
                title = "Acompanhamento em escala",
                description = "Alunos, professores e planos de treino dentro do mesmo fluxo.",
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            BenefitRow(
                icon = Icons.Default.Insights,
                title = "Indicadores acionáveis",
                description = "Frequência e adesão sem preencher planilhas paralelas.",
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            BenefitRow(
                icon = Icons.Default.Lock,
                title = "Acesso verificado",
                description = "Recursos pagos só são liberados após validação no servidor.",
            )
        }
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
