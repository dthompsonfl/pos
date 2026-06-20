package com.enterprise.pos.payment.router

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentErrorCode
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.randomPaymentId
import com.enterprise.pos.payment.model.CreatePaymentRequest
import com.enterprise.pos.payment.model.PaymentCapability
import com.enterprise.pos.payment.model.PaymentEvent
import com.enterprise.pos.payment.model.PaymentIntentHandle
import com.enterprise.pos.payment.model.PaymentProvider
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentResult
import com.enterprise.pos.payment.model.PaymentRoutingPolicy
import com.enterprise.pos.payment.model.PaymentRouterConfig
import com.enterprise.pos.payment.model.ReaderStatus
import com.enterprise.pos.payment.model.RefundResult
import com.enterprise.pos.payment.model.RoutingDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The payment router is the single entry point used by the rest of the app.
 * It holds all configured providers, decides which one to use for each payment,
 * exposes aggregated reader status, and forwards provider events to the UI.
 *
 * This is THE abstraction that makes the POS "configurable to be used with stripe, square,
 * shopify, and any other the other main payment providers".
 */
class PaymentRouter(
    private val providers: Map<PaymentProviderId, PaymentProvider>,
    private val policy: PaymentRoutingPolicy,
    private val config: PaymentRouterConfig,
    private val logger: Logger = NoopLogger
) {
    init {
        require(providers.isNotEmpty()) { "At least one provider required" }
        require(config.defaultProvider in providers) {
            "Default provider ${config.defaultProvider} not registered"
        }
    }

    private val _routerState = MutableStateFlow(PaymentRouterState())
    val routerState: StateFlow<PaymentRouterState> = _routerState.asStateFlow()

    val availableProviders: Set<PaymentProviderId>
        get() = providers.keys intersect config.enabledProviders

    val aggregatedReaderStatus: Flow<Map<PaymentProviderId, ReaderStatus>> =
        combine(providers.values.map { p -> p.readerStatus.map { p.id to it } }) { pairs ->
            pairs.toMap()
        }

    suspend fun initializeAll(): Result<Unit> {
        val failures = mutableListOf<String>()
        for ((id, provider) in providers) {
            if (id !in config.enabledProviders) continue
            val cfg = config.providerConfig(id)
            val r = provider.initialize(cfg)
            if (r is Result.Failure) {
                logger.w(TAG, "Failed to initialize $id: ${r.error.message}")
                failures.add("$id: ${r.error.message}")
            }
        }
        return if (failures.isEmpty()) Result.success(Unit)
        else Result.failure(AppError.Generic("Some providers failed: $failures"))
    }

    suspend fun discoverAllReaders(): Result<Map<PaymentProviderId, List<com.enterprise.pos.payment.model.DiscoveredReader>>> {
        val result = mutableMapOf<PaymentProviderId, List<com.enterprise.pos.payment.model.DiscoveredReader>>()
        for ((id, provider) in providers) {
            if (id !in availableProviders) continue
            val r = provider.discoverReaders()
            if (r is Result.Success) result[id] = r.value
        }
        return Result.success(result)
    }

    suspend fun connectReader(provider: PaymentProviderId, reader: com.enterprise.pos.payment.model.DiscoveredReader): Result<Unit> {
        val p = providers[provider] ?: return Result.failure(AppError.Generic("Unknown provider"))
        return p.connectReader(reader)
    }

    suspend fun disconnectAll(): Result<Unit> {
        providers.values.forEach { runCatching { it.disconnectReader() } }
        return Result.success(Unit)
    }

    /**
     * The main entry point used by checkout. Decides the provider, creates the intent,
     * and returns a handle that the UI passes back to [collectPayment].
     */
    suspend fun initiatePayment(
        orderId: OrderId,
        amount: Money,
        requestedProvider: PaymentProviderId? = null,
        customerId: CustomerId? = null,
        description: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Result<RoutedPaymentIntent> {
        val connectedMap = _routerState.value.connectedReaders
        val decision = policy.decide(amount, requestedProvider, availableProviders, connectedMap, config)
        val provider = providers[decision.provider]
            ?: return Result.failure(AppError.Payment(
                PaymentErrorCode.PROVIDER_NOT_CONFIGURED,
                "${decision.provider} not configured"
            ))

        val request = CreatePaymentRequest(
            orderId = orderId,
            amount = amount,
            customerId = customerId,
            description = description,
            metadata = metadata + ("routed_via" to decision.provider.name),
            enableOffline = decision.allowsOffline
        )

        return provider.createPaymentIntent(request).map { handle ->
            RoutedPaymentIntent(
                routerPaymentId = randomPaymentId(),
                decision = decision,
                intentHandle = handle
            )
        }
    }

    /**
     * Collects payment using the routed intent. Streams events to [onEvent]. Long-running.
     */
    suspend fun collectPayment(
        routed: RoutedPaymentIntent,
        onEvent: (PaymentEvent) -> Unit
    ): Result<PaymentResult> {
        val provider = providers[routed.decision.provider]
            ?: return Result.failure(AppError.Payment(
                PaymentErrorCode.PROVIDER_NOT_CONFIGURED,
                "${routed.decision.provider} no longer available"
            ))
        return provider.collectPayment(routed.intentHandle) { event ->
            onEvent(event)
            if (event is PaymentEvent.Success || event is PaymentEvent.Error || event is PaymentEvent.Cancelled) {
                _routerState.value = _routerState.value.copy(lastEvent = event)
            }
        }
    }

    suspend fun cancelPayment(routed: RoutedPaymentIntent): Result<Unit> {
        val provider = providers[routed.decision.provider]
            ?: return Result.failure(AppError.Generic("Provider unavailable"))
        return provider.cancelPayment(routed.intentHandle)
    }

    suspend fun refund(
        paymentId: PaymentId,
        originalProvider: PaymentProviderId,
        amount: Money?,
        reason: String
    ): Result<RefundResult> {
        val provider = providers[originalProvider]
            ?: return Result.failure(AppError.Payment(
                PaymentErrorCode.PROVIDER_NOT_CONFIGURED,
                "$originalProvider not configured"
            ))
        return provider.refund(paymentId, amount, reason)
    }

    suspend fun closeAll(): Result<Unit> {
        providers.values.forEach { runCatching { it.close() } }
        return Result.success(Unit)
    }

    fun capabilities(provider: PaymentProviderId): Set<PaymentCapability> =
        providers[provider]?.capabilities ?: emptySet()

    companion object {
        private const val TAG = "PaymentRouter"
    }
}

data class RoutedPaymentIntent(
    val routerPaymentId: PaymentId,
    val decision: RoutingDecision,
    val intentHandle: PaymentIntentHandle
)

data class PaymentRouterState(
    val connectedReaders: Map<PaymentProviderId, Boolean> = emptyMap(),
    val lastEvent: PaymentEvent? = null,
    val isProcessing: Boolean = false
)

// Helper to fetch provider-specific config — extended by the DI layer.
private fun PaymentRouterConfig.providerConfig(id: PaymentProviderId): com.enterprise.pos.payment.model.ProviderConfig =
    com.enterprise.pos.payment.model.ProviderConfig(
        environment = com.enterprise.pos.payment.model.ProviderEnvironment.SANDBOX
    )
