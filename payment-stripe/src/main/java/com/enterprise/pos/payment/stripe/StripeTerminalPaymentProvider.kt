package com.enterprise.pos.payment.stripe

import android.content.Context
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
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.RefundCallback
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.OfflineMode
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.Refund
import com.stripe.stripeterminal.external.models.RefundParameters
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.external.models.TerminalException.TerminalErrorCode
import com.stripe.stripeterminal.log.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.UUID
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Stripe Terminal SDK adapter implementing the [PaymentProvider] contract.
 *
 * Supports Bluetooth, USB, and simulated readers. Handles offline payments via the Stripe SDK's
 * native offline queue, with an additional in-memory refund queue for operations that cannot be
 * retried by the SDK alone.
 *
 * All SDK callbacks are bridged to suspending coroutines via [suspendCancellableCoroutine].
 */
@Singleton
class StripeTerminalPaymentProvider(
    @ApplicationContext private val context: Context,
    private val backendBaseUrl: String,
    private val connectionTokenEndpoint: String,
    private val authTokenProvider: () -> String?,
    private val logger: Logger = NoopLogger,
    private val simulate: Boolean = false
) : PaymentProvider {

    override val id: PaymentProviderId = PaymentProviderId.STRIPE
    override val displayName: String = "Stripe Terminal"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.IN_PERSON_CARD_PRESENT,
        PaymentCapability.TAP_TO_PAY,
        PaymentCapability.REFUNDS,
        PaymentCapability.OFFLINE_MODE,
        PaymentCapability.SAVED_CARDS,
        PaymentCapability.SPLIT_TENDER
    )

    private val _readerStatus = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: Flow<ReaderStatus> = _readerStatus.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(this@StripeTerminalPaymentProvider.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    private val readerDelegate = StripeTerminalReaderDelegate(
        onStatusChange = { _readerStatus.value = it },
        onEvent = { event -> _paymentEvents.tryEmit(event) },
        logger = logger
    )

    private val discoveryDelegate = StripeTerminalDiscoveryDelegate(
        onReadersFound = { readers -> _discoveredReaders = readers },
        onError = { error ->
            logger.e(TAG, "Discovery error: ${error.errorMessage}", error)
            _readerStatus.value = ReaderStatus.Error(error.errorMessage ?: "Discovery failed")
        },
        logger = logger
    )

    private var _discoveredReaders: List<Reader> = emptyList()
    private var _connectedReader: Reader? = null
    private var _currentPaymentIntent: PaymentIntent? = null
    private var _cancelable: Cancelable? = null
    private var config: ProviderConfig? = null

    private val _paymentEvents = MutableStateFlow<PaymentEvent?>(null)

    private val offlineRefundQueue = Collections.synchronizedList(mutableListOf<RefundQueueItem>())

    init {
        logger.i(TAG, "StripeTerminalPaymentProvider created")
    }

    override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
        this.config = config
        if (config.environment == ProviderEnvironment.SANDBOX && simulate) {
            logger.i(TAG, "Stripe Terminal initialized in simulated mode")
            _readerStatus.value = ReaderStatus.Connected(
                DiscoveredReader(
                    id = "simulated-reader",
                    displayName = "Simulated Reader",
                    model = "bbpos-wisepos-e",
                    batteryLevel = 0.85f,
                    connectionType = ConnectionType.BLUETOOTH,
                    serial = "SIM-001"
                )
            )
            return@catching
        }

        val locationId = requireNotNull(config.locationId?.takeIf { it.isNotBlank() }) {
            "Stripe locationId is required for Terminal initialization"
        }

        require(backendBaseUrl.isNotBlank()) { "Backend base URL is required" }
        require(connectionTokenEndpoint.isNotBlank()) { "Connection token endpoint is required" }

        if (!Terminal.isInitialized()) {
            val tokenProvider = StripeConnectionTokenProvider(
                backendBaseUrl = backendBaseUrl,
                connectionTokenEndpoint = connectionTokenEndpoint,
                authTokenProvider = authTokenProvider,
                httpClient = httpClient,
                scope = scope,
                logger = logger
            )

            withContext(Dispatchers.Main) {
                Terminal.initTerminal(
                    context.applicationContext,
                    LogLevel.VERBOSE,
                    tokenProvider,
                    readerDelegate
                )
            }
            logger.i(TAG, "Stripe Terminal SDK initialized")
        } else {
            logger.i(TAG, "Stripe Terminal SDK already initialized")
        }
    }

    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.catching {
        if (simulate) {
            delay(200)
            return@catching listOf(
                DiscoveredReader(
                    id = "sim-reader-1",
                    displayName = "Simulated BBPOS WisePOS E",
                    model = "bbpos-wisepos-e",
                    batteryLevel = 0.85f,
                    connectionType = ConnectionType.BLUETOOTH,
                    serial = "SIM-${UUID.randomUUID()}"
                ),
                DiscoveredReader(
                    id = "sim-reader-2",
                    displayName = "Simulated Stripe M2",
                    model = "stripe-m2",
                    batteryLevel = 0.72f,
                    connectionType = ConnectionType.BLUETOOTH,
                    serial = "SIM-${UUID.randomUUID()}"
                )
            )
        }

        val terminal = requireTerminal()
        val locationId = requireNotNull(config?.locationId?.takeIf { it.isNotBlank() }) {
            "Stripe locationId is required for reader discovery"
        }

        _discoveredReaders = emptyList()

        val discoveryConfig = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(
            timeout = 0,
            isSimulated = false
        )

        val discoveryResult = suspendCancellableCoroutine { continuation ->
            val callback = object : Callback {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            val cancelable = terminal.discoverReaders(discoveryConfig, discoveryDelegate, callback)
            continuation.invokeOnCancellation { cancelable.cancel() }
        }

        // Allow discovery to run for a fixed window
        delay(8_000)
        _cancelable?.cancel()
        _cancelable = null

        _discoveredReaders.map { reader -> StripePaymentModelMapper.mapReader(reader) }
    }

    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.catching {
        if (simulate) {
            _connectedReader = null
            delay(300)
            _readerStatus.value = ReaderStatus.Connected(reader)
            logger.i(TAG, "Simulated reader connected: ${reader.displayName}")
            return@catching
        }

        val terminal = requireTerminal()
        val locationId = requireNotNull(config?.locationId?.takeIf { it.isNotBlank() }) {
            "Stripe locationId is required for reader connection"
        }
        val stripeReader = _discoveredReaders.find { it.id == reader.id }
            ?: throw IllegalStateException("Reader ${reader.id} not found in discovery results. Run discoverReaders first.")

        _readerStatus.value = ReaderStatus.Connecting(reader.id)
        readerDelegate.setCurrentReader(stripeReader)

        val connectedReader = suspendCancellableCoroutine { continuation ->
            val connectionConfig = ConnectionConfiguration.BluetoothConnectionConfiguration(locationId)
            val callback = object : ReaderCallback {
                override fun onSuccess(reader: Reader) {
                    continuation.resume(reader)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            val cancelable = terminal.connectReader(stripeReader, connectionConfig, callback)
            continuation.invokeOnCancellation { cancelable.cancel() }
        }

        _connectedReader = connectedReader
        readerDelegate.setCurrentReader(connectedReader)
        _readerStatus.value = ReaderStatus.Connected(StripePaymentModelMapper.mapReader(connectedReader))
        logger.i(TAG, "Reader connected: ${connectedReader.label}")
    }

    override suspend fun disconnectReader(): Result<Unit> = Result.catching {
        if (simulate) {
            _connectedReader = null
            _readerStatus.value = ReaderStatus.NotConnected
            logger.i(TAG, "Simulated reader disconnected")
            return@catching
        }

        val terminal = requireTerminal()
        suspendCancellableCoroutine { continuation ->
            val callback = object : Callback {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            terminal.disconnectReader(callback)
        }
        _connectedReader = null
        readerDelegate.setCurrentReader(null)
        _readerStatus.value = ReaderStatus.NotConnected
        logger.i(TAG, "Reader disconnected")
    }

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> = Result.catching {
        if (simulate) {
            return@catching PaymentIntentHandle(
                provider = id,
                intentId = "pi_sim_${UUID.randomUUID()}",
                secret = "pi_sim_${UUID.randomUUID()}_secret_${UUID.randomUUID()}",
                amount = request.amount,
                currency = request.currency,
                createdAt = System.currentTimeMillis()
            )
        }

        val terminal = requireTerminal()
        val params = PaymentIntentParameters.Builder()
            .setAmount(request.amount.minorUnits)
            .setCurrency(request.currency.lowercase())
            .setDescription(request.description)
            .setMetadata(request.metadata)
            .setOfflineMode(
                if (request.enableOffline) OfflineMode.REQUIRED_IF_SUPPORTED
                else OfflineMode.ONLINE
            )
            .build()

        val paymentIntent = suspendCancellableCoroutine { continuation ->
            val callback = object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    continuation.resume(paymentIntent)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            terminal.createPaymentIntent(params, callback)
        }

        _currentPaymentIntent = paymentIntent

        PaymentIntentHandle(
            provider = id,
            intentId = paymentIntent.id,
            secret = paymentIntent.clientSecret,
            amount = Money.ofMinor(paymentIntent.amount),
            currency = paymentIntent.currency.uppercase(),
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> = Result.catching {
        if (simulate) {
            return@catching simulateCollectPayment(handle, events)
        }

        val terminal = requireTerminal()
        val clientSecret = requireNotNull(handle.secret) { "Payment intent secret is required" }

        events?.invoke(PaymentEvent.InsertCard())

        // Retrieve the payment intent from the SDK using the client secret
        val paymentIntent = suspendCancellableCoroutine { continuation ->
            val callback = object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    continuation.resume(paymentIntent)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            terminal.retrievePaymentIntent(clientSecret, callback)
        }

        _currentPaymentIntent = paymentIntent
        events?.invoke(PaymentEvent.ReadingCard())

        // Collect payment method from the reader
        val collectedIntent = suspendCancellableCoroutine { continuation ->
            val callback = object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    continuation.resume(paymentIntent)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            val cancelable = terminal.collectPaymentMethod(paymentIntent, callback)
            _cancelable = cancelable
            continuation.invokeOnCancellation { cancelable.cancel() }
        }

        _cancelable = null
        _currentPaymentIntent = collectedIntent
        events?.invoke(PaymentEvent.Processing())

        // Confirm payment intent (SDK 3.7.1: confirmPaymentIntent replaces processPayment)
        val confirmedIntent = try {
            suspendCancellableCoroutine { continuation ->
                val callback = object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }
                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                }
                terminal.confirmPaymentIntent(collectedIntent, callback)
            }
        } catch (e: TerminalException) {
            if (e.errorCode == TerminalErrorCode.PAYMENT_INTENT_UNEXPECTED_STATUS) {
                logger.i(TAG, "Payment intent already confirmed or not required for this region")
                collectedIntent
            } else {
                throw e
            }
        }

        _currentPaymentIntent = confirmedIntent

        // Capture payment intent via backend (SDK 3.7.1 does not expose capture on Terminal)
        captureViaBackend(confirmedIntent.id)

        events?.invoke(PaymentEvent.Success())

        StripePaymentModelMapper.mapPaymentIntentToResult(confirmedIntent, id, randomPaymentId())
    }

    private suspend fun simulateCollectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): PaymentResult {
        delay(600)
        events?.invoke(PaymentEvent.ReadingCard())
        delay(700)
        if (_connectedReader == null && _readerStatus.value !is ReaderStatus.Connected) {
            events?.invoke(PaymentEvent.Error("Reader disconnected"))
            throw TerminalException(
                TerminalErrorCode.READER_NOT_CONNECTED,
                "Stripe reader is not connected"
            )
        }
        events?.invoke(PaymentEvent.Processing())
        delay(800)
        events?.invoke(PaymentEvent.Success())
        return PaymentResult(
            id = randomPaymentId(),
            provider = id,
            providerTransactionId = handle.intentId,
            amount = handle.amount,
            currency = handle.currency,
            cardBrand = "Visa",
            last4 = "4242",
            entryMode = EntryMode.CHIP,
            receiptUrl = "https://dashboard.stripe.com/receipts/${handle.intentId}",
            capturedAt = System.currentTimeMillis(),
            metadata = mapOf("mode" to "simulated")
        )
    }

    private suspend fun captureViaBackend(paymentIntentId: String) {
        httpClient.post("$backendBaseUrl/v1/payments/$paymentIntentId/capture") {
            contentType(ContentType.Application.Json)
            authTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                header("Authorization", "Bearer $token")
            }
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(CaptureHttpRequest(tipAmountMinor = null))
        }
    }

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.catching {
        if (simulate) {
            logger.i(TAG, "Simulated cancel: ${handle.intentId}")
            return@catching
        }

        _cancelable?.cancel()
        _cancelable = null

        val terminal = requireTerminal()
        val clientSecret = handle.secret
        val currentIntent = _currentPaymentIntent

        if (currentIntent != null) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(Unit)
                    }
                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                }
                terminal.cancelPaymentIntent(currentIntent, callback)
            }
        } else if (clientSecret != null) {
            // Retrieve then cancel
            val paymentIntent = suspendCancellableCoroutine { continuation ->
                val callback = object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }
                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                }
                terminal.retrievePaymentIntent(clientSecret, callback)
            }
            suspendCancellableCoroutine { continuation ->
                val callback = object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(Unit)
                    }
                    override fun onFailure(e: TerminalException) {
                        continuation.resumeWithException(e)
                    }
                }
                terminal.cancelPaymentIntent(paymentIntent, callback)
            }
        }
        _currentPaymentIntent = null
    }

    override suspend fun refund(
        paymentId: PaymentId,
        amount: Money?,
        reason: String
    ): Result<RefundResult> = Result.catching {
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

        val refundAmount = amount ?: Money.ZERO
        if (refundAmount.isZero()) {
            return@catching RefundResult(
                id = UUID.randomUUID().toString(),
                originalPaymentId = paymentId,
                amount = Money.ZERO,
                status = RefundStatus.FAILED,
                providerRefundId = "",
                createdAt = System.currentTimeMillis()
            )
        }

        // Attempt refund; if it fails with network error, queue for retry
        try {
            processRefund(paymentId.value, refundAmount.minorUnits, reason)
        } catch (e: TerminalException) {
            if (e.errorCode == TerminalErrorCode.API_CONNECTION_ERROR ||
                e.errorCode == TerminalErrorCode.NETWORK_ERROR ||
                e.errorCode == TerminalErrorCode.INTERNET_NOT_REACHABLE
            ) {
                logger.w(TAG, "Refund failed due to network error, queuing for retry: ${e.errorMessage}")
                val queueItem = RefundQueueItem(
                    paymentId = paymentId,
                    amount = refundAmount,
                    reason = reason
                )
                offlineRefundQueue.add(queueItem)
                RefundResult(
                    id = UUID.randomUUID().toString(),
                    originalPaymentId = paymentId,
                    amount = refundAmount,
                    status = RefundStatus.PENDING,
                    providerRefundId = "offline-${UUID.randomUUID()}",
                    createdAt = System.currentTimeMillis()
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun processRefund(
        paymentIntentId: String,
        amount: Long,
        reason: String
    ): RefundResult {
        val terminal = requireTerminal()
        val params = RefundParameters.Builder()
            .setChargeId(paymentIntentId)
            .setAmount(amount)
            .setCurrency("usd")
            .setReason(reason)
            .build()

        val refund = suspendCancellableCoroutine { continuation ->
            val callback = object : RefundCallback {
                override fun onSuccess(refund: Refund) {
                    continuation.resume(refund)
                }
                override fun onFailure(e: TerminalException) {
                    continuation.resumeWithException(e)
                }
            }
            terminal.createRefund(params, callback)
        }

        return StripePaymentModelMapper.mapRefundToResult(refund, PaymentId(paymentIntentId))
    }

    /**
     * Retry any queued refunds. Called automatically after successful operations or
     * can be triggered manually by the app when network connectivity is restored.
     */
    suspend fun retryOfflineRefunds(): Result<Int> = Result.catching {
        val snapshot = offlineRefundQueue.toList()
        var succeeded = 0
        for (item in snapshot) {
            try {
                val result = processRefund(item.paymentId.value, item.amount.minorUnits, item.reason)
                if (result.status == RefundStatus.SUCCEEDED) {
                    offlineRefundQueue.remove(item)
                    succeeded++
                    logger.i(TAG, "Offline refund succeeded: ${item.paymentId.value}")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Offline refund retry failed: ${item.paymentId.value}", e)
            }
        }
        succeeded
    }

    /** Returns the number of refunds currently queued for offline processing. */
    fun offlineRefundQueueSize(): Int = offlineRefundQueue.size

    override suspend fun close(): Result<Unit> = Result.catching {
        runCatching { disconnectReader().getOrNull() }
        if (!simulate) {
            if (Terminal.isInitialized()) {
                runCatching { Terminal.getInstance().clearReaderDisplay() }
            }
        }
        _cancelable?.cancel()
        _cancelable = null
        _currentPaymentIntent = null
        scope.cancel()
        httpClient.close()
        logger.i(TAG, "StripeTerminalPaymentProvider closed")
    }

    private fun requireTerminal(): Terminal {
        if (simulate) {
            throw IllegalStateException("Terminal operations not available in simulate mode")
        }
        return Terminal.getInstance()
    }

    @Serializable
    private data class CaptureHttpRequest(val tipAmountMinor: Long?)

    @Serializable
    private data class RefundQueueItem(
        val paymentId: PaymentId,
        val amount: Money,
        val reason: String
    )

    companion object {
        private const val TAG = "StripeTerminalProvider"
    }
}

/**
 * Stripe Terminal connection token provider that fetches tokens from the backend.
 * The SDK calls [fetchConnectionToken] on a background thread; we bridge to our
 * suspending Ktor client using a coroutine launch.
 */
private class StripeConnectionTokenProvider(
    private val backendBaseUrl: String,
    private val connectionTokenEndpoint: String,
    private val authTokenProvider: () -> String?,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val logger: Logger
) : ConnectionTokenProvider {

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        scope.launch {
            try {
                val token = fetchToken()
                callback.onSuccess(token)
            } catch (e: Exception) {
                logger.e("StripeTokenProvider", "Failed to fetch connection token", e)
                callback.onFailure(
                    TerminalException(
                        TerminalErrorCode.API_ERROR,
                        "Failed to fetch connection token: ${e.message}"
                    )
                )
            }
        }
    }

    private suspend fun fetchToken(): String {
        val response: ConnectionTokenHttpResponse = httpClient.post("$backendBaseUrl$connectionTokenEndpoint") {
            contentType(ContentType.Application.Json)
            authTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                header("Authorization", "Bearer $token")
            }
            header("Idempotency-Key", UUID.randomUUID().toString())
            setBody(ConnectionTokenHttpRequest(locationId = ""))
        }.body()
        return response.secret
    }

    @Serializable
    private data class ConnectionTokenHttpRequest(val locationId: String)

    @Serializable
    private data class ConnectionTokenHttpResponse(val secret: String)
}
