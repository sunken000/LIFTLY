package com.liftly.app.commercial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CommercialBillingAvailability {
    NOT_CONFIGURED,
    CONNECTING,
    READY,
    ERROR,
}

data class CommercialBillingState(
    val availability: CommercialBillingAvailability,
    val statusMessage: String,
    val entitlements: CommercialEntitlementSnapshot = CommercialEntitlementSnapshot.None,
) {
    val canStartPurchase: Boolean
        get() = availability == CommercialBillingAvailability.READY

    companion object {
        val NotConfigured = CommercialBillingState(
            availability = CommercialBillingAvailability.NOT_CONFIGURED,
            statusMessage = "A Play Store e a validação segura no servidor ainda não foram ativadas.",
        )
    }
}

data class CommercialPurchaseRequest(
    val planId: String,
    val priceOptionId: String,
    val storeProductId: String,
)

sealed interface CommercialPurchaseResult {
    data class Launched(val request: CommercialPurchaseRequest) : CommercialPurchaseResult
    data class Completed(
        val request: CommercialPurchaseRequest,
        val entitlements: CommercialEntitlementSnapshot,
    ) : CommercialPurchaseResult
    data class Cancelled(val request: CommercialPurchaseRequest) : CommercialPurchaseResult
    data class Unavailable(val reason: String) : CommercialPurchaseResult
    data class Failed(val userMessage: String) : CommercialPurchaseResult
}

/**
 * Boundary for a future Play Billing implementation.
 *
 * A production implementation must verify the purchase token on a trusted backend before emitting
 * [CommercialPurchaseResult.Completed] or a snapshot with [CommercialEntitlementSnapshot.verifiedByBackend].
 */
interface CommercialBillingGateway {
    val state: StateFlow<CommercialBillingState>

    suspend fun refresh()

    suspend fun startPurchase(request: CommercialPurchaseRequest): CommercialPurchaseResult
}

/**
 * Safe default used until Play products and the entitlement backend are configured.
 *
 * It deliberately never launches a checkout and never grants an entitlement.
 */
object UnavailableCommercialBillingGateway : CommercialBillingGateway {
    private val mutableState = MutableStateFlow(CommercialBillingState.NotConfigured)

    override val state: StateFlow<CommercialBillingState> = mutableState.asStateFlow()

    override suspend fun refresh() {
        mutableState.value = CommercialBillingState.NotConfigured
    }

    override suspend fun startPurchase(
        request: CommercialPurchaseRequest,
    ): CommercialPurchaseResult = CommercialPurchaseResult.Unavailable(
        reason = "Assinaturas indisponíveis até a Play Store e o servidor de validação serem configurados.",
    )
}

fun CommercialPlan.purchaseRequest(
    priceOption: CommercialPriceOption,
): CommercialPurchaseRequest? {
    val productId = priceOption.storeProductId ?: return null
    return CommercialPurchaseRequest(
        planId = id,
        priceOptionId = priceOption.id,
        storeProductId = productId,
    )
}
