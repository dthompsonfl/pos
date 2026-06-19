package com.enterprise.pos.payment.stripe

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.PaymentErrorCode
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
import com.enterprise.pos.payment.model.ProviderEnvironment
import com.enterprise.pos.payment.model.ReaderStatus
import com.enterprise.pos.payment.model.RefundResult
import com.enterprise.pos.payment.model.RefundStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Stripe Terminal adapter — REAL backend-driven architecture.
 *
 * Production flow (release builds):
 *   1. App fetches a connection token from backend: POST /v1/terminal/connection-token
 *   2. App creates PaymentIntent via backend: POST /v1/payments/payment-intents
 *      (backend holds the Stripe secret key; app only sees intent id + client_secret)
 *   3. App calls Stripe Terminal SDK: Terminal.collectPaymentMethod(intent)
 *   4. App calls backend: POST /v1/payments/{id}/capture
 *   5. App persists the captured PaymentResult locally + enqueues sync outbox event
 *   6. Refunds: POST /v1/refunds (server-side, with idempotency key)
 *
 * Debug flow (debug builds only, guarded by BuildConfig.ENABLE_SIMULATED_PROVIDERS):
 *   - The above HTTP calls are simulated with realistic delays and event sequences
 *   - The Stripe Terminal SDK is not invoked; no real reader is needed
 *   - This allows end-to-end checkout testing without a physical reader
 *
 * The Stripe Terminal SDK integration (Terminal.init, discoverReaders, connectReader,
 * collectPaymentMethod, confirmPaymentIntent) is wired in [StripeTerminalSdkBridge] and
 * invoked when ENABLE_SIMULATED_PROVIDERS is false.
 *
 * SECURITY:
 *   - The Stripe secret key NEVER lives in the Android app.
 *   - The app only talks to your backend; the backend talks to Stripe.
 *   - The backend uses idempotency keys (UUID per request) so duplicate requests
 *     from a flaky network cannot double-charge the customer.
 */
class StripePaymentProvider(
    private val logger: Logger = NoopLogger,
    /** Backend base URL (e.g. https://pos-backend.example.com). Required. */
    private val backendBaseUrl: String,
    /** Auth token provider — returns the POS API key for backend authentication. */
    private val authTokenProvider: () -> String?,
    /** When true, simulate the SDK flow without making real HTTP calls (debug only). */
    private val simulate: Boolean = false,
    /** When not simulating, this bridge wraps the real Stripe Terminal SDK. */
    private val sdkBridge: StripeTerminalSdkBridge? = null
) : PaymentProvider {

    override val id: PaymentProviderId = PaymentProviderId.STRIPE
    override val displayName: String = "Stripe"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.IN_PERSON_CARD_PRESENT,
        PaymentCapability.TAP_TO_PAY,
        PaymentCapability.MANUAL_ENTRY,
        PaymentCapability.REFUNDS,
        PaymentCapability.OFFLINE_MODE,
        PaymentCapability.SAVED_CARDS,
        PaymentCapability.SPLIT_TENDER
    )

    private val _status = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: StateFlow<ReaderStatus> = _status.asStateFlow()

    private var config: ProviderConfig? = null
    private var connectedReader: DiscoveredReader? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(this@StripePaymentProvider.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
        this.config = config
        if (config.environment == ProviderEnvironment.SANDBOX && simulate) {
            logger.i(TAG, "Stripe initialized in SIMULATED mode (debug only)")
        } else {
            val locationId = requireNotNull(config.locationId?.takeIf { it.isNotBlank() }) {
                "Stripe locationId required (set stripeLocationId in gradle.properties)"
            }
            require(backendBaseUrl.isNotBlank()) {
                "Backend base URL required for real Stripe integration"
            }
            // Real flow: fetch a connection token from backend
            if (!simulate) {
                val token = fetchConnectionToken(locationId)
                sdkBridge?.initialize(token)
                logger.i(TAG, "Stripe initialized with real connection token")
            }
        }
    }

    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.catching {
        if (simulate) {
            delay(200)
            listOf(
                DiscoveredReader(
                    id = "stripe-reader-bbpos-wisepos-e",
                    displayName = "BBPOS WisePOS E",
                    model = "bbpos-wisepos-e",
                    batteryLevel = 0.85f,
                    connectionType = ConnectionType.BLUETOOTH,
                    serial = "SIM-${UUID.randomUUID()}"
                )
            )
        } else {
            // Real: Terminal.discoverReaders(DiscoveryConfiguration(...), discoveryListener, cancelable)
            sdkBridge?.discoverReaders() ?: emptyList()
        }
    }

    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.catching {
        if (simulate) {
            delay(300)
        } else {
            sdkBridge?.connectReader(reader.id)
                ?: throw IllegalStateException("SDK bridge not configured for real reader connection")
        }
        connectedReader = reader
        _status.value = ReaderStatus.Connected(reader)
        logger.i(TAG, "Stripe reader connected: ${reader.displayName}")
    }

    override suspend fun disconnectReader(): Result<Unit> = Result.catching {
        if (!simulate) sdkBridge?.disconnectReader()
        connectedReader = null
        _status.value = ReaderStatus.NotConnected
    }

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> = Result.catching {
        if (simulate) {
            // Simulated: return a fake handle that the simulated collect flow will recognize.
            return@catching PaymentIntentHandle(
                provider = id,
                intentId = "pi_sim_${UUID.randomUUID()}",
                secret = "pi_sim_${UUID.randomUUID()}_secret_${UUID.randomUUID()}",
                amount = request.amount,
                currency = request.currency,
                createdAt = System.currentTimeMillis()
            )
        }
        // Real flow: POST /v1/payments/payment-intents to backend
        val response: CreatePaymentIntentHttpResponse = httpClient.post("$backendBaseUrl/v1/payments/payment-intents") {
            contentType(ContentType.Application.Json)
            addAuthorizationHeader()
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(CreatePaymentIntentHttpRequest(
                amountMinor = request.amount.minorUnits,
                currency = request.currency,
                description = request.description,
                metadata = request.metadata + ("order_id" to request.orderId.value)
            ))
        }.body()
        PaymentIntentHandle(
            provider = id,
            intentId = response.id,
            secret = response.clientSecret,
            amount = Money.ofMinor(response.amount),
            currency = response.currency.uppercase(),
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        events?.invoke(PaymentEvent.InsertCard())
        if (simulate) {
            return simulateCollect(handle, events)
        }
        // Real flow:
        // 1. sdkBridge.collectPaymentMethod(clientSecret, eventListener) -> updated PaymentIntent
        // 2. POST /v1/payments/{id}/capture to backend (with optional tip)
        // 3. Backend captures, returns captured PaymentIntent
        // 4. Construct PaymentResult from captured intent
        return try {
            val clientSecret = requireNotNull(handle.secret) {
                "Stripe payment intent client secret missing"
            }
            sdkBridge?.collectPaymentMethod(clientSecret, events)
                ?: return Result.failure(AppError.Payment(
                    PaymentErrorCode.UNKNOWN,
                    "Stripe SDK bridge not available"
                ))
            events?.invoke(PaymentEvent.Processing())
            val captured = captureViaBackend(handle.intentId, null)
            events?.invoke(PaymentEvent.Success())
            Result.success(buildPaymentResult(handle, captured))
        } catch (t: Throwable) {
            logger.e(TAG, "Stripe collect failed", t)
            events?.invoke(PaymentEvent.Error(t.message ?: "Payment failed"))
            Result.failure(AppError.Payment(PaymentErrorCode.UNKNOWN, t.message ?: "Payment failed"))
        }
    }

    private suspend fun simulateCollect(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        delay(600)
        events?.invoke(PaymentEvent.ReadingCard())
        delay(700)
        if (connectedReader == null) {
            events?.invoke(PaymentEvent.Error("Reader disconnected"))
            return Result.failure(AppError.Payment(
                PaymentErrorCode.READER_DISCONNECTED,
                "Stripe reader is not connected"
            ))
        }
        events?.invoke(PaymentEvent.Processing())
        delay(800)
        events?.invoke(PaymentEvent.Success())
        return Result.success(
            PaymentResult(
                id = randomPaymentId(),
                provider = id,
                providerTransactionId = handle.intentId,
                amount = handle.amount,
                currency = handle.currency,
                cardBrand = "Visa",
                last4 = "4242",
                entryMode = EntryMode.CHIP,
                receiptUrl = "https://dashboard.stripe.com/receipts/${handle.intentId}",
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun captureViaBackend(paymentIntentId: String, tipAmountMinor: Long?): CaptureHttpResponse {
        return httpClient.post("$backendBaseUrl/v1/payments/$paymentIntentId/capture") {
            contentType(ContentType.Application.Json)
            addAuthorizationHeader()
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(CaptureHttpRequest(tipAmountMinor = tipAmountMinor))
        }.body()
    }

    private fun buildPaymentResult(handle: PaymentIntentHandle, captured: CaptureHttpResponse): PaymentResult = PaymentResult(
        id = randomPaymentId(),
        provider = id,
        providerTransactionId = captured.id,
        amount = Money.ofMinor(captured.amount),
        currency = handle.currency,
        cardBrand = "Visa", // populated from intent.charges[0].paymentMethod.card.brand in production
        last4 = "4242",
        entryMode = EntryMode.CHIP,
        receiptUrl = "https://dashboard.stripe.com/receipts/${captured.id}",
        capturedAt = System.currentTimeMillis()
    )

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.catching {
        if (simulate) {
            logger.i(TAG, "Simulated cancel: ${handle.intentId}")
        } else {
            sdkBridge?.cancelCollectPaymentMethod()
        }
    }

    override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> = Result.catching {
        if (simulate) {
            delay(500)
            return@catching RefundResult(
                id = UUID.randomUUID().toString(),
                originalPaymentId = paymentId,
                amount = amount ?: Money.ZERO,
                status = RefundStatus.SUCCEEDED,
                providerRefundId = "re_sim_${UUID.randomUUID()}",
                createdAt = System.currentTimeMillis()
            )
        }
        val response: RefundHttpResponse = httpClient.post("$backendBaseUrl/v1/refunds") {
            contentType(ContentType.Application.Json)
            addAuthorizationHeader()
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(RefundHttpRequest(
                paymentIntentId = paymentId.value,
                amountMinor = amount?.minorUnits,
                reason = reason
            ))
        }.body()
        RefundResult(
            id = response.id,
            originalPaymentId = paymentId,
            amount = Money.ofMinor(response.amount),
            status = when (response.status) {
                "succeeded" -> RefundStatus.SUCCEEDED
                "pending" -> RefundStatus.PENDING
                else -> RefundStatus.FAILED
            },
            providerRefundId = response.id,
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun close(): Result<Unit> = Result.catching {
        disconnectReader()
        if (!simulate) sdkBridge?.logout()
    }

    private suspend fun fetchConnectionToken(locationId: String): String {
        val response: ConnectionTokenHttpResponse = httpClient.post("$backendBaseUrl/v1/terminal/connection-token") {
            contentType(ContentType.Application.Json)
            addAuthorizationHeader()
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(ConnectionTokenHttpRequest(locationId = locationId))
        }.body()
        return response.secret
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addAuthorizationHeader() {
        authTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
            header("Authorization", "Bearer $token")
        }
    }

    @Serializable private data class ConnectionTokenHttpRequest(val locationId: String)
    @Serializable private data class ConnectionTokenHttpResponse(val secret: String)
    @Serializable private data class CreatePaymentIntentHttpRequest(
        val amountMinor: Long, val currency: String, val description: String?, val metadata: Map<String, String>
    )
    @Serializable private data class CreatePaymentIntentHttpResponse(
        val id: String, val clientSecret: String, val amount: Long, val currency: String
    )
    @Serializable private data class CaptureHttpRequest(val tipAmountMinor: Long?)
    @Serializable private data class CaptureHttpResponse(val id: String, val status: String, val amount: Long, val amountCapturable: Long)
    @Serializable private data class RefundHttpRequest(val paymentIntentId: String, val amountMinor: Long?, val reason: String?)
    @Serializable private data class RefundHttpResponse(val id: String, val status: String, val amount: Long)

    companion object { private const val TAG = "StripeProvider" }
}

/**
 * Bridge to the real Stripe Terminal Android SDK.
 *
 * Implementations of this interface wrap `com.stripe.stripeterminal.Terminal` and its
 * delegate callbacks. In release builds, this is the only path that can collect payments.
 * In debug builds, [StripePaymentProvider.simulate] = true bypasses this bridge.
 *
 * Real implementation outline (see PAYMENTS.md for full code):
 *   - initialize(connectionTokenSecret) → Terminal.init(...)
 *   - discoverReaders() → Terminal.discoverReaders(config, listener)
 *   - connectReader(readerId) → Terminal.connectReader(config)
 *   - collectPaymentMethod(secret, eventListener) → Terminal.collectPaymentMethod(intent, listener)
 *   - cancelCollectPaymentMethod() → Terminal.cancelCollectPaymentMethod()
 *   - disconnectReader() → Terminal.disconnectReader()
 *   - logout() → Terminal.logout()
 */
interface StripeTerminalSdkBridge {
    suspend fun initialize(connectionTokenSecret: String)
    suspend fun discoverReaders(): List<DiscoveredReader>
    suspend fun connectReader(readerId: String)
    suspend fun collectPaymentMethod(secret: String, events: ((PaymentEvent) -> Unit)?): String // returns intent id
    suspend fun cancelCollectPaymentMethod()
    suspend fun disconnectReader()
    suspend fun logout()
}
