package com.liftly.app.commercial.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Google Play Billing 9.x client for the commercial Liftly edition.
 *
 * This class handles Play connection, catalog and purchase flow only. Google Play purchase data is
 * sent to [BillingEntitlementBackend] and does not unlock anything by itself. The backend remains
 * the sole source of truth for entitlements and purchase acknowledgement.
 */
class LiftlyBillingClient(
    context: Context,
    private val entitlementBackend: BillingEntitlementBackend,
    verificationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PurchasesUpdatedListener, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val verificationScope = CoroutineScope(SupervisorJob() + verificationDispatcher)
    private val eventIds = AtomicLong(0L)
    private val verificationInFlight = ConcurrentHashMap.newKeySet<String>()
    private val connectionLock = Any()

    @Volatile
    private var connectionInProgress = false

    @Volatile
    private var closed = false

    @Volatile
    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

    private val billingClient: BillingClient = BillingClient.newBuilder(applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                // Mandatory Billing Library acknowledgement that pending purchases are handled.
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val mutableState = MutableStateFlow(LiftlyBillingState())
    val state: StateFlow<LiftlyBillingState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<BillingEventEnvelope>(
        extraBufferCapacity = 16,
    )
    val events: SharedFlow<BillingEventEnvelope> = mutableEvents.asSharedFlow()

    // StateFlow alternative for simple Compose consumers that do not collect one-shot events.
    private val mutableLatestEvent = MutableStateFlow<BillingEventEnvelope?>(null)
    val latestEvent: StateFlow<BillingEventEnvelope?> = mutableLatestEvent.asStateFlow()

    fun connect() {
        if (closed) return
        if (billingClient.isReady) {
            mutableState.update {
                it.copy(
                    connectionStatus = BillingConnectionStatus.READY,
                    lastError = null,
                )
            }
            refresh()
            return
        }

        synchronized(connectionLock) {
            if (connectionInProgress || closed) return
            connectionInProgress = true
        }
        mutableState.update {
            it.copy(
                connectionStatus = BillingConnectionStatus.CONNECTING,
                lastError = null,
            )
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                synchronized(connectionLock) {
                    connectionInProgress = false
                }
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.update {
                        it.copy(
                            connectionStatus = BillingConnectionStatus.READY,
                            lastError = null,
                        )
                    }
                    emit(BillingEvent.ConnectionReady)
                    refresh()
                } else {
                    handleBillingFailure(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                synchronized(connectionLock) {
                    connectionInProgress = false
                }
                mutableState.update {
                    it.copy(connectionStatus = BillingConnectionStatus.DISCONNECTED)
                }
                emit(BillingEvent.ConnectionLost)
                // enableAutoServiceReconnection() reconnects on the next BillingClient operation.
            }
        })
    }

    fun refresh() {
        if (closed) return
        if (!billingClient.isReady) {
            connect()
            return
        }
        queryProductDetails()
        queryCurrentPurchases()
    }

    fun queryProductDetails() {
        if (!ensureReady()) return
        mutableState.update { it.copy(isRefreshingProducts = true, lastError = null) }

        val products = LiftlyPlayProducts.subscriptionProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.update { it.copy(isRefreshingProducts = false) }
                handleBillingFailure(result)
                return@queryProductDetailsAsync
            }

            val details = queryResult.productDetailsList
            productDetailsById = details.associateBy { it.productId }
            val offers = details.flatMap(::mapSubscriptionOffers)
                .sortedBy { it.plan.ordinal }

            val missingPlanCount = LiftlyProPlan.values().count { expected ->
                offers.none { it.plan == expected }
            }
            val catalogMessage = when {
                details.isEmpty() -> "Produto Liftly Pro não encontrado no Google Play."
                missingPlanCount > 0 -> "$missingPlanCount plano(s) ainda não disponível(is)."
                queryResult.unfetchedProductList.isNotEmpty() ->
                    "${queryResult.unfetchedProductList.size} produto(s) não pôde/puderam ser consultado(s)."
                else -> null
            }
            mutableState.update {
                it.copy(
                    offers = offers,
                    isRefreshingProducts = false,
                    lastError = catalogMessage,
                )
            }
            emit(BillingEvent.OffersUpdated(offers.size))
        }
    }

    fun queryCurrentPurchases() {
        if (!ensureReady()) return
        mutableState.update { it.copy(isRefreshingPurchases = true, lastError = null) }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            mutableState.update { it.copy(isRefreshingPurchases = false) }
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                handleBillingFailure(result)
            }
        }
    }

    /**
     * Launches an eligible offer returned by Google Play for the requested base plan.
     *
     * [obfuscatedAccountId] and [obfuscatedProfileId] must be non-PII, stable hashes generated by
     * the authenticated account system. They are intentionally optional for preview builds.
     */
    fun launchSubscriptionPurchase(
        activity: Activity,
        plan: LiftlyProPlan,
        obfuscatedAccountId: String? = null,
        obfuscatedProfileId: String? = null,
    ): BillingFlowLaunchResult {
        if (!ensureReady()) return BillingFlowLaunchResult.NotConnected
        val details = productDetailsById[plan.productId]
            ?: return BillingFlowLaunchResult.OfferUnavailable
        val offer = selectOfferDetails(details, plan)
            ?: return BillingFlowLaunchResult.OfferUnavailable

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val flowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
        sanitizedObfuscatedId(obfuscatedAccountId)?.let(flowBuilder::setObfuscatedAccountId)
        sanitizedObfuscatedId(obfuscatedProfileId)?.let(flowBuilder::setObfuscatedProfileId)

        val result = billingClient.launchBillingFlow(activity, flowBuilder.build())
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            emit(BillingEvent.PurchaseFlowStarted(plan))
            BillingFlowLaunchResult.Started
        } else {
            handleBillingFailure(result)
            BillingFlowLaunchResult.Failed(
                responseCode = result.responseCode,
                message = result.debugMessage.safeMessage(),
            )
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    emit(
                        BillingEvent.Error(
                            code = result.responseCode,
                            message = "O Google Play não retornou os dados da compra.",
                        ),
                    )
                } else {
                    processPurchases(purchases)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                emit(BillingEvent.PurchaseCancelled)
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryCurrentPurchases()
            }

            else -> handleBillingFailure(result)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        synchronized(connectionLock) {
            connectionInProgress = false
        }
        verificationInFlight.clear()
        verificationScope.cancel()
        billingClient.endConnection()
        productDetailsById = emptyMap()
        mutableState.update {
            it.copy(
                connectionStatus = BillingConnectionStatus.CLOSED,
                isRefreshingProducts = false,
                isRefreshingPurchases = false,
            )
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val snapshots = purchases.map { purchase ->
            PlayPurchaseSnapshot(
                purchaseToken = purchase.purchaseToken,
                productIds = purchase.products,
                purchaseTimeEpochMillis = purchase.purchaseTime,
                isAcknowledged = purchase.isAcknowledged,
                isPending = purchase.purchaseState == Purchase.PurchaseState.PENDING,
            )
        }
        mutableState.update { it.copy(purchases = snapshots) }

        val relevant = purchases.filter { purchase ->
            purchase.products.any(LiftlyPlayProducts.subscriptionProductIds::contains)
        }
        if (relevant.isEmpty()) {
            mutableState.update {
                it.copy(entitlement = CommercialEntitlementState.NoVerifiedEntitlement)
            }
            return
        }

        relevant.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    emit(BillingEvent.PurchasePending(purchase.products))
                }

                Purchase.PurchaseState.PURCHASED -> submitForServerVerification(purchase)

                else -> Unit
            }
        }
    }

    private fun submitForServerVerification(purchase: Purchase) {
        val request = PurchaseVerificationRequest(
            packageName = applicationContext.packageName,
            purchaseToken = purchase.purchaseToken,
            productIds = purchase.products,
            purchaseTimeEpochMillis = purchase.purchaseTime,
            orderId = purchase.orderId,
            isAcknowledged = purchase.isAcknowledged,
            obfuscatedAccountId = purchase.accountIdentifiers?.obfuscatedAccountId,
            obfuscatedProfileId = purchase.accountIdentifiers?.obfuscatedProfileId,
        )

        // A Play PURCHASED state is not authorization. Pro remains locked while the server checks it.
        mutableState.update {
            it.copy(
                entitlement = CommercialEntitlementState.PendingServerVerification(
                    purchaseToken = request.purchaseToken,
                    productIds = request.productIds,
                ),
            )
        }
        emit(BillingEvent.VerificationRequired(request))

        if (!verificationInFlight.add(request.purchaseToken)) return
        verificationScope.launch {
            val serverResult = runCatching {
                entitlementBackend.verifySubscription(request)
            }.getOrElse { error ->
                ServerEntitlementResult.TemporarilyUnavailable(
                    reason = error.message ?: "Falha temporária ao verificar a assinatura.",
                )
            }
            verificationInFlight.remove(request.purchaseToken)
            applyServerResult(serverResult)
        }
    }

    private fun applyServerResult(result: ServerEntitlementResult) {
        when (result) {
            is ServerEntitlementResult.VerifiedActive -> {
                // Only this backend-verified result may unlock commercial features.
                mutableState.update {
                    it.copy(
                        entitlement = CommercialEntitlementState.Active(
                            entitlementId = result.entitlementId,
                            plan = result.plan,
                            validUntilEpochMillis = result.validUntilEpochMillis,
                        ),
                        lastError = null,
                    )
                }
                emit(BillingEvent.EntitlementVerified(result.plan))
            }

            is ServerEntitlementResult.VerifiedInactive -> {
                mutableState.update {
                    it.copy(
                        entitlement = CommercialEntitlementState.Inactive(result.reason),
                        lastError = result.reason,
                    )
                }
                emit(BillingEvent.EntitlementRejected(result.reason))
            }

            is ServerEntitlementResult.TemporarilyUnavailable -> {
                mutableState.update {
                    it.copy(
                        entitlement = CommercialEntitlementState.VerificationTemporarilyUnavailable(
                            result.reason,
                        ),
                        lastError = result.reason,
                    )
                }
                emit(
                    BillingEvent.Error(
                        code = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                        message = result.reason,
                    ),
                )
            }
        }
    }

    private fun mapSubscriptionOffers(details: ProductDetails): List<LiftlySubscriptionOffer> =
        LiftlyProPlan.values().mapNotNull { plan ->
            if (plan.productId != details.productId) return@mapNotNull null
            val offer = selectOfferDetails(details, plan) ?: return@mapNotNull null
            val pricingPhase = offer.pricingPhases.pricingPhaseList.firstOrNull()
                ?: return@mapNotNull null
            LiftlySubscriptionOffer(
                plan = plan,
                productId = details.productId,
                basePlanId = offer.basePlanId,
                title = details.title,
                description = details.description,
                formattedPrice = pricingPhase.formattedPrice,
                billingPeriodIso8601 = pricingPhase.billingPeriod,
                offerToken = offer.offerToken,
                hasPromotionalOffer = offer.offerId != null ||
                    offer.pricingPhases.pricingPhaseList.size > 1,
            )
        }

    private fun selectOfferDetails(
        details: ProductDetails,
        plan: LiftlyProPlan,
    ): ProductDetails.SubscriptionOfferDetails? {
        val eligible = details.subscriptionOfferDetails.orEmpty()
            .filter { it.basePlanId == plan.basePlanId }
        // Prefer the plain base-plan offer to avoid silently applying an unintended promotion.
        return eligible.firstOrNull { it.offerId == null } ?: eligible.firstOrNull()
    }

    private fun ensureReady(): Boolean {
        if (closed) return false
        if (billingClient.isReady) return true
        connect()
        return false
    }

    private fun handleBillingFailure(result: BillingResult) {
        val status = when (result.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> BillingConnectionStatus.UNAVAILABLE

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                BillingConnectionStatus.DISCONNECTED

            else -> BillingConnectionStatus.ERROR
        }
        val message = result.debugMessage.safeMessage()
        mutableState.update {
            it.copy(
                connectionStatus = status,
                lastError = message,
                isRefreshingProducts = false,
                isRefreshingPurchases = false,
            )
        }
        emit(BillingEvent.Error(result.responseCode, message))
    }

    private fun emit(event: BillingEvent) {
        val envelope = BillingEventEnvelope(
            id = eventIds.incrementAndGet(),
            event = event,
        )
        mutableLatestEvent.value = envelope
        mutableEvents.tryEmit(envelope)
    }

    private fun sanitizedObfuscatedId(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 64 }

    private fun String.safeMessage(): String =
        takeIf(String::isNotBlank) ?: "Falha na comunicação com o Google Play."
}
