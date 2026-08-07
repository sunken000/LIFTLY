package com.liftly.app.commercial.billing

/**
 * Google Play Console identifiers for the Liftly Pro subscription.
 *
 * Configure one subscription product with two auto-renewing base plans. These identifiers must
 * match Play Console exactly before the commercial edition can sell subscriptions.
 */
object LiftlyPlayProducts {
    const val LIFTLY_PRO_PRODUCT_ID = "liftly_pro"
    const val MONTHLY_BASE_PLAN_ID = "monthly"
    const val ANNUAL_BASE_PLAN_ID = "annual"

    val subscriptionProductIds: Set<String> = setOf(LIFTLY_PRO_PRODUCT_ID)
}

enum class LiftlyProPlan(
    val productId: String,
    val basePlanId: String,
) {
    MONTHLY(
        productId = LiftlyPlayProducts.LIFTLY_PRO_PRODUCT_ID,
        basePlanId = LiftlyPlayProducts.MONTHLY_BASE_PLAN_ID,
    ),
    ANNUAL(
        productId = LiftlyPlayProducts.LIFTLY_PRO_PRODUCT_ID,
        basePlanId = LiftlyPlayProducts.ANNUAL_BASE_PLAN_ID,
    ),
}

enum class BillingConnectionStatus {
    IDLE,
    CONNECTING,
    READY,
    DISCONNECTED,
    UNAVAILABLE,
    ERROR,
    CLOSED,
}

data class LiftlySubscriptionOffer(
    val plan: LiftlyProPlan,
    val productId: String,
    val basePlanId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val billingPeriodIso8601: String,
    val offerToken: String,
    val hasPromotionalOffer: Boolean,
)

data class PlayPurchaseSnapshot(
    val purchaseToken: String,
    val productIds: List<String>,
    val purchaseTimeEpochMillis: Long,
    val isAcknowledged: Boolean,
    val isPending: Boolean,
)

/**
 * Entitlements deliberately have no "locally purchased = active" state.
 *
 * [Active] may only be installed from a successful response returned by
 * [BillingEntitlementBackend]. A PURCHASED response from Google Play becomes
 * [PendingServerVerification], never an entitlement.
 */
sealed interface CommercialEntitlementState {
    data object NotLoaded : CommercialEntitlementState
    data object NoVerifiedEntitlement : CommercialEntitlementState

    data class PendingServerVerification(
        val purchaseToken: String,
        val productIds: List<String>,
    ) : CommercialEntitlementState

    data class Active(
        val entitlementId: String,
        val plan: LiftlyProPlan,
        val validUntilEpochMillis: Long?,
    ) : CommercialEntitlementState

    data class Inactive(
        val reason: String,
    ) : CommercialEntitlementState

    data class VerificationTemporarilyUnavailable(
        val reason: String,
    ) : CommercialEntitlementState
}

data class LiftlyBillingState(
    val connectionStatus: BillingConnectionStatus = BillingConnectionStatus.IDLE,
    val offers: List<LiftlySubscriptionOffer> = emptyList(),
    val purchases: List<PlayPurchaseSnapshot> = emptyList(),
    val entitlement: CommercialEntitlementState = CommercialEntitlementState.NotLoaded,
    val isRefreshingProducts: Boolean = false,
    val isRefreshingPurchases: Boolean = false,
    val lastError: String? = null,
)

data class PurchaseVerificationRequest(
    val packageName: String,
    val purchaseToken: String,
    val productIds: List<String>,
    val purchaseTimeEpochMillis: Long,
    val orderId: String?,
    val isAcknowledged: Boolean,
    val obfuscatedAccountId: String?,
    val obfuscatedProfileId: String?,
)

/**
 * Implement this interface with an authenticated HTTPS call to Liftly's secure backend.
 *
 * The backend must verify the purchase token through the Google Play Developer API, determine the
 * current subscription state, persist the entitlement, and acknowledge the purchase only after
 * entitlement delivery. The Android client must never acknowledge a purchase or trust local
 * purchase fields as proof of access.
 */
fun interface BillingEntitlementBackend {
    suspend fun verifySubscription(
        request: PurchaseVerificationRequest,
    ): ServerEntitlementResult
}

sealed interface ServerEntitlementResult {
    /**
     * Returned only after the secure server has verified the token with Google Play.
     */
    data class VerifiedActive(
        val entitlementId: String,
        val plan: LiftlyProPlan,
        val validUntilEpochMillis: Long?,
    ) : ServerEntitlementResult

    data class VerifiedInactive(
        val reason: String,
    ) : ServerEntitlementResult

    data class TemporarilyUnavailable(
        val reason: String,
    ) : ServerEntitlementResult
}

/**
 * Safe placeholder for preview builds. It makes billing screens usable without ever unlocking Pro.
 */
object UnconfiguredBillingEntitlementBackend : BillingEntitlementBackend {
    override suspend fun verifySubscription(
        request: PurchaseVerificationRequest,
    ): ServerEntitlementResult = ServerEntitlementResult.TemporarilyUnavailable(
        reason = "Servidor de assinaturas ainda não configurado.",
    )
}

sealed interface BillingEvent {
    data object ConnectionReady : BillingEvent
    data object ConnectionLost : BillingEvent
    data class OffersUpdated(val count: Int) : BillingEvent
    data class PurchaseFlowStarted(val plan: LiftlyProPlan) : BillingEvent
    data object PurchaseCancelled : BillingEvent
    data class PurchasePending(val productIds: List<String>) : BillingEvent
    data class VerificationRequired(val request: PurchaseVerificationRequest) : BillingEvent
    data class EntitlementVerified(val plan: LiftlyProPlan) : BillingEvent
    data class EntitlementRejected(val reason: String) : BillingEvent
    data class Error(val code: Int, val message: String) : BillingEvent
}

data class BillingEventEnvelope(
    val id: Long,
    val event: BillingEvent,
)

sealed interface BillingFlowLaunchResult {
    data object Started : BillingFlowLaunchResult
    data object NotConnected : BillingFlowLaunchResult
    data object OfferUnavailable : BillingFlowLaunchResult
    data class Failed(val responseCode: Int, val message: String) : BillingFlowLaunchResult
}
