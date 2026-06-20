package com.enterprise.pos.payment.shopify

import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.randomPaymentId
import com.enterprise.pos.payment.model.ConnectionType
import com.enterprise.pos.payment.model.CreatePaymentRequest
import com.enterprise.pos.payment.model.DiscoveredReader
import com.enterprise.pos.payment.model.EntryMode
import com.enterprise.pos.payment.model.PaymentCapability
import com.enterprise.pos.payment.model.PaymentEvent
import com.enterprise.pos.payment.model.PaymentIntentHandle
import com.enterprise.pos.payment.model.PaymentProvider
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentResult
import com.enterprise.pos.payment.model.ProviderConfig
import com.enterprise.pos.payment.model.ReaderStatus
import com.enterprise.pos.payment.model.RefundResult
import com.enterprise.pos.payment.model.RefundStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Shopify POS integration. Shopify's mobile POS is itself a separate app; we integrate via:
 *   - Deep-link launch (shopifypos://cart/...) for handoff to Shopify POS for payment.
 *   - Admin API (REST + GraphQL) for order sync, refunds, and product catalog mirror.
 *
 * This adapter models Shopify as a "provider" so the router treats it uniformly. When the user
 * selects Shopify at checkout, we hand off to Shopify POS and the result returns via the
 * registered deep-link callback. See SHOPIFY_INTEGRATION.md.
 */
class ShopifyPaymentProvider(
    private val logger: Logger = NoopLogger,
    private val deepLinkLauncher: (url: String) -> Unit = {},
    private val adminApi: ShopifyAdminApi? = null
) : PaymentProvider {

    override val id: PaymentProviderId = PaymentProviderId.SHOPIFY
    override val displayName: String = "Shopify"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.MANUAL_ENTRY,
        PaymentCapability.REFUNDS,
        PaymentCapability.SPLIT_TENDER
    )

    private val _status = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: StateFlow<ReaderStatus> = _status.asStateFlow()

    private var config: ProviderConfig? = null

    override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
        this.config = config
        val storefrontDomain = requireNotNull(config.storefrontDomain?.takeIf { it.isNotBlank() }) {
            "Shopify shop domain required"
        }
        val accessToken = config.accessToken
        // Verify the Admin API token by issuing a lightweight shop query.
        if (adminApi != null && !accessToken.isNullOrBlank()) {
            val ok = adminApi.verifyToken(storefrontDomain, accessToken)
            if (!ok) throw RuntimeException("Shopify Admin API token invalid")
        }
        _status.value = ReaderStatus.Connected(
            DiscoveredReader(
                id = "shopify-pos-app",
                displayName = "Shopify POS App",
                model = "Shopify POS",
                connectionType = ConnectionType.BUILT_IN
            )
        )
    }

    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.success(emptyList())

    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.success(Unit)
    override suspend fun disconnectReader(): Result<Unit> = Result.success(Unit)

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> = Result.catching {
        // Construct the Shopify POS deep-link cart URL. The launched Shopify POS app handles
        // card entry, tip capture, etc., then returns to us via deep-link callback.
        val store = config?.storefrontDomain
        require(!store.isNullOrBlank()) { "Shopify storefront domain required" }
        val url = buildString {
            append("shopifypos://cart/")
            append(request.amount.asBigDecimal.toPlainString())
            append("?return_url=enterprise-pos://payment-callback")
            append("&shop=").append(store)
            request.description?.let { append("&note=").append(it) }
        }
        // Fire-and-forget: the actual collection happens in Shopify POS app.
        deepLinkLauncher(url)
        PaymentIntentHandle(
            provider = id,
            intentId = "shopify-${UUID.randomUUID()}",
            amount = request.amount,
            currency = request.currency,
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        // In real integration, we register a BroadcastReceiver / deep-link callback handler
        // that completes a Deferred<PaymentResult> when Shopify POS returns control.
        // Here we simulate the round-trip.
        events?.invoke(PaymentEvent.Processing("Handing off to Shopify POS…"))
        delay(1200)
        events?.invoke(PaymentEvent.Success())
        return Result.success(
            PaymentResult(
                id = randomPaymentId(),
                provider = id,
                providerTransactionId = handle.intentId,
                amount = handle.amount,
                currency = handle.currency,
                cardBrand = "Visa",
                last4 = "1881",
                entryMode = EntryMode.QR,
                receiptUrl = "https://${config?.storefrontDomain}/orders/${handle.intentId}",
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.success(Unit)

    override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> = Result.catching {
        val currentConfig = config
        val storefrontDomain = currentConfig?.storefrontDomain
        val accessToken = currentConfig?.accessToken
        if (adminApi != null && !storefrontDomain.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            adminApi.refund(storefrontDomain, accessToken, paymentId.value, amount)
        }
        delay(600)
        RefundResult(
            id = UUID.randomUUID().toString(),
            originalPaymentId = paymentId,
            amount = amount ?: Money.ZERO,
            status = RefundStatus.SUCCEEDED,
            providerRefundId = "shopify-refund-${UUID.randomUUID()}",
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun close(): Result<Unit> = Result.success(Unit)

    companion object { private const val TAG = "ShopifyProvider" }
}

/** Minimal Shopify Admin API surface — implemented with Ktor in production. */
interface ShopifyAdminApi {
    suspend fun verifyToken(shop: String, token: String): Boolean
    suspend fun refund(shop: String, token: String, orderId: String, amount: Money?): Boolean
    suspend fun pullOrders(shop: String, token: String): List<String>
    suspend fun pushProduct(shop: String, token: String, productJson: String): Boolean
}
