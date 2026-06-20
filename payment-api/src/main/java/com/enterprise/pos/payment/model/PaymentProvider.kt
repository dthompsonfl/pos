package com.enterprise.pos.payment.model

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Every provider integration implements this contract. The POS knows nothing about
 * Stripe, Square, or Shopify at the business-logic layer — it only talks to PaymentProvider. */
interface PaymentProvider {
    val id: PaymentProviderId
    val displayName: String
    val capabilities: Set<PaymentCapability>
    val readerStatus: Flow<ReaderStatus>

    suspend fun initialize(config: ProviderConfig): Result<Unit>
    suspend fun discoverReaders(): Result<List<DiscoveredReader>>
    suspend fun connectReader(reader: DiscoveredReader): Result<Unit>
    suspend fun disconnectReader(): Result<Unit>

    /**
     * Begin a payment intent with the provider. Returns a [PaymentIntentHandle] that the
     * router stores; the actual capture happens in [collectPayment].
     */
    suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle>

    /**
     * Run the physical card collection flow. Caller should observe [events] in parallel.
     * Returns the final result. This call is long-running and cancellable.
     */
    suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult>

    suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit>

    suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult>

    suspend fun close(): Result<Unit>
}

enum class PaymentProviderId(val displayName: String) {
    STRIPE("Stripe"),
    SQUARE("Square"),
    SHOPIFY("Shopify"),
    CASH("Cash"),
    MANUAL("Manual Card Entry"),
    EXTERNAL("External")
}

enum class PaymentCapability {
    IN_PERSON_CARD_PRESENT,
    TAP_TO_PAY,
    MANUAL_ENTRY,
    REFUNDS,
    OFFLINE_MODE,
    SPLIT_TENDER,
    SAVED_CARDS,
    REWARDS_REDEMPTION
}

@Serializable
data class ProviderConfig(
    val apiKey: String? = null,
    val locationId: String? = null,
    val merchantId: String? = null,
    val storefrontDomain: String? = null,
    val accessToken: String? = null,
    val environment: ProviderEnvironment = ProviderEnvironment.SANDBOX
)

enum class ProviderEnvironment { SANDBOX, PRODUCTION }

@Serializable
data class DiscoveredReader(
    val id: String,
    val displayName: String,
    val model: String,
    val batteryLevel: Float? = null,
    val connectionType: ConnectionType,
    val serial: String? = null
)

enum class ConnectionType { BLUETOOTH, USB, WIFI, BUILT_IN }

@Serializable
sealed class ReaderStatus {
    data object NotConnected : ReaderStatus()
    data class Connecting(val readerId: String) : ReaderStatus()
    data class Connected(val reader: DiscoveredReader) : ReaderStatus()
    data class Error(val message: String) : ReaderStatus()
}

@Serializable
data class CreatePaymentRequest(
    val orderId: OrderId,
    val amount: Money,
    val currency: String = "USD",
    val customerId: CustomerId? = null,
    val captureImmediately: Boolean = true,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val enableOffline: Boolean = true
)

@Serializable
data class PaymentIntentHandle(
    val provider: PaymentProviderId,
    val intentId: String,
    val secret: String? = null,
    val amount: Money,
    val currency: String,
    val createdAt: Long
)

@Serializable
data class PaymentResult(
    val id: PaymentId,
    val provider: PaymentProviderId,
    val providerTransactionId: String,
    val amount: Money,
    val currency: String,
    val cardBrand: String? = null,
    val last4: String? = null,
    val entryMode: EntryMode? = null,
    val receiptUrl: String? = null,
    val capturedAt: Long,
    val metadata: Map<String, String> = emptyMap()
)

enum class EntryMode { CONTACTLESS, CHIP, SWIPE, MANUAL, CASH, QR }

@Serializable
data class RefundResult(
    val id: String,
    val originalPaymentId: PaymentId,
    val amount: Money,
    val status: RefundStatus,
    val providerRefundId: String,
    val createdAt: Long
)

enum class RefundStatus { PENDING, SUCCEEDED, FAILED }

/** Real-time events emitted during [PaymentProvider.collectPayment]. */
sealed class PaymentEvent {
    data class InsertCard(val message: String = "Insert, tap, or swipe card") : PaymentEvent()
    data class ReadingCard(val message: String = "Reading card…") : PaymentEvent()
    data class Processing(val message: String = "Processing payment…") : PaymentEvent()
    data class WaitingForSignature(val message: String = "Signature required") : PaymentEvent()
    data class WaitingForPin(val message: String = "Enter PIN on reader") : PaymentEvent()
    data class Success(val message: String = "Approved") : PaymentEvent()
    data class Declined(val reason: String) : PaymentEvent()
    data class Error(val message: String) : PaymentEvent()
    data class Cancelled(val message: String = "Cancelled") : PaymentEvent()
    data class ReaderMessage(val message: String) : PaymentEvent()
    data class OfflineQueued(val message: String = "Queued for offline processing") : PaymentEvent()
}
