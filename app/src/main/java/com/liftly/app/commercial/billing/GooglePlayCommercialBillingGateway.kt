package com.liftly.app.commercial.billing

import android.app.Activity
import android.content.Context
import com.liftly.app.commercial.CommercialBillingAvailability
import com.liftly.app.commercial.CommercialBillingGateway
import com.liftly.app.commercial.CommercialBillingState
import com.liftly.app.commercial.CommercialEntitlementSnapshot
import com.liftly.app.commercial.CommercialPlanCatalog
import com.liftly.app.commercial.CommercialPurchaseRequest
import com.liftly.app.commercial.CommercialPurchaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Adapts the Google Play client to the commercial UI boundary.
 *
 * A purchase launched here is never treated as an active subscription. Only an [Active] state
 * returned by [BillingEntitlementBackend] is mapped to usable entitlements.
 */
class GooglePlayCommercialBillingGateway(
    context: Context,
    private val activityProvider: () -> Activity?,
    entitlementBackend: BillingEntitlementBackend = UnconfiguredBillingEntitlementBackend,
) : CommercialBillingGateway, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = LiftlyBillingClient(
        context = context.applicationContext,
        entitlementBackend = entitlementBackend,
    )
    private val mutableState = MutableStateFlow(
        CommercialBillingState(
            availability = CommercialBillingAvailability.CONNECTING,
            statusMessage = "Conectando à Google Play…",
        ),
    )

    override val state: StateFlow<CommercialBillingState> = mutableState.asStateFlow()

    init {
        scope.launch {
            client.state.collectLatest { billingState ->
                mutableState.value = billingState.toCommercialState()
            }
        }
        client.connect()
    }

    override suspend fun refresh() {
        client.refresh()
    }

    override suspend fun startPurchase(
        request: CommercialPurchaseRequest,
    ): CommercialPurchaseResult {
        val activity = activityProvider()
            ?: return CommercialPurchaseResult.Unavailable(
                "Não foi possível abrir a tela segura da Google Play.",
            )
        val plan = when (request.priceOptionId) {
            "pro_monthly" -> LiftlyProPlan.MONTHLY
            "pro_annual" -> LiftlyProPlan.ANNUAL
            else -> return CommercialPurchaseResult.Unavailable(
                "Este plano é contratado diretamente com a equipe comercial.",
            )
        }
        return when (val result = client.launchSubscriptionPurchase(activity, plan)) {
            BillingFlowLaunchResult.Started -> CommercialPurchaseResult.Launched(request)
            BillingFlowLaunchResult.NotConnected -> CommercialPurchaseResult.Unavailable(
                "A Google Play ainda está conectando. Tente novamente.",
            )
            BillingFlowLaunchResult.OfferUnavailable -> CommercialPurchaseResult.Unavailable(
                "O plano ainda não está publicado para esta conta ou região.",
            )
            is BillingFlowLaunchResult.Failed -> CommercialPurchaseResult.Failed(result.message)
        }
    }

    override fun close() {
        client.close()
        scope.cancel()
    }
}

private fun LiftlyBillingState.toCommercialState(): CommercialBillingState {
    val availability = when (connectionStatus) {
        BillingConnectionStatus.IDLE,
        BillingConnectionStatus.CONNECTING,
        -> CommercialBillingAvailability.CONNECTING

        BillingConnectionStatus.READY -> CommercialBillingAvailability.READY
        BillingConnectionStatus.UNAVAILABLE -> CommercialBillingAvailability.NOT_CONFIGURED
        BillingConnectionStatus.DISCONNECTED,
        BillingConnectionStatus.ERROR,
        BillingConnectionStatus.CLOSED,
        -> CommercialBillingAvailability.ERROR
    }
    val statusMessage = lastError ?: when {
        connectionStatus == BillingConnectionStatus.READY && offers.isNotEmpty() ->
            "Google Play conectada. A licença só é liberada após validação segura no servidor."
        connectionStatus == BillingConnectionStatus.READY ->
            "Google Play conectada; publique liftly_pro com os planos monthly e annual."
        connectionStatus == BillingConnectionStatus.CONNECTING ->
            "Conectando à Google Play…"
        connectionStatus == BillingConnectionStatus.UNAVAILABLE ->
            "Assinaturas não estão disponíveis neste aparelho ou nesta instalação."
        connectionStatus == BillingConnectionStatus.DISCONNECTED ->
            "A conexão com a Google Play foi interrompida."
        connectionStatus == BillingConnectionStatus.CLOSED ->
            "A conexão de assinaturas foi encerrada."
        else -> "Aguardando configuração da Google Play e do servidor de licenças."
    }
    val entitlementSnapshot = when (val value = entitlement) {
        is CommercialEntitlementState.Active -> CommercialEntitlementSnapshot(
            activeEntitlements = CommercialPlanCatalog.proIndividual.entitlements,
            activePlanId = CommercialPlanCatalog.proIndividual.id,
            verifiedByBackend = true,
            validUntilEpochMillis = value.validUntilEpochMillis,
        )
        else -> CommercialEntitlementSnapshot.None
    }
    return CommercialBillingState(
        availability = availability,
        statusMessage = statusMessage,
        entitlements = entitlementSnapshot,
    )
}
