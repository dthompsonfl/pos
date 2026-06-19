package com.enterprise.pos.payment.square

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
import com.enterprise.pos.payment.model.ReaderStatus
import com.enterprise.pos.payment.model.RefundResult
import com.enterprise.pos.payment.model.RefundStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Square Reader SDK adapter. Same simulation pattern as StripePaymentProvider — real SDK
 * calls (ReaderSdk.initialize, ReaderSettingsActivity, CardManager.startCardEntryAsync,
 * RefundManager.beginRefund) replace the simulate blocks. See SQUARE_INTEGRATION.md.
 */
class SquarePaymentProvider(
    private val logger: Logger = NoopLogger
) : PaymentProvider {

    override val id: PaymentProviderId = PaymentProviderId.SQUARE
    override val displayName: String = "Square"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.IN_PERSON_CARD_PRESENT,
        PaymentCapability.TAP_TO_PAY,
        PaymentCapability.MANUAL_ENTRY,
        PaymentCapability.REFUNDS,
        PaymentCapability.OFFLINE_MODE,
        PaymentCapability.SPLIT_TENDER
    )

    private val _status = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: StateFlow<ReaderStatus> = _status.asStateFlow()

    private var config: ProviderConfig? = null
    private var connectedReader: DiscoveredReader? = null

    override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.catching {
        this.config = config
        // Real: ReaderSdk.initialize() — must happen before any other call.
        logger.i(TAG, "Square initialized (merchant=${config.merchantId})")
    }

    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.catching {
        // Real: ReaderSdk.readerManager().startPairing() — opens UI; SDK doesn't expose a list API,
        // we model the discovered reader here for the abstraction.
        delay(200)
        listOf(
            DiscoveredReader(
                id = "square-reader-contactless-2",
                displayName = "Square Contactless + Chip",
                model = "contactless-2",
                batteryLevel = 0.92f,
                connectionType = ConnectionType.BLUETOOTH,
                serial = "SQ-${UUID.randomUUID()}"
            )
        )
    }

    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.catching {
        // Real: handled by Square's ReaderSettingsActivity result.
        delay(300)
        connectedReader = reader
        _status.value = ReaderStatus.Connected(reader)
    }

    override suspend fun disconnectReader(): Result<Unit> = Result.catching {
        connectedReader = null
        _status.value = ReaderStatus.NotConnected
    }

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> = Result.catching {
        // Real: Square doesn't have a separate "intent" — checkout is atomic. We synthesize a
        // handle so the router contract is uniform across providers.
        require(config?.merchantId?.isNotBlank() == true) {
            "Square merchantId required (set squareApplicationId)"
        }
        PaymentIntentHandle(
            provider = id,
            intentId = "sq_${UUID.randomUUID()}",
            amount = request.amount,
            currency = request.currency,
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        // Real:
        //   CardEntry.startCardEntryActivity(this, REQUEST_CODE)
        //   On CheckoutResult callback: paymentId, totalMoney, cardBrand, etc.
        events?.invoke(PaymentEvent.InsertCard())
        delay(500)
        events?.invoke(PaymentEvent.ReadingCard())
        delay(800)
        if (connectedReader == null) {
            events?.invoke(PaymentEvent.Error("Square reader not connected"))
            return Result.failure(AppError.Payment(
                PaymentErrorCode.READER_DISCONNECTED,
                "Square reader is not connected"
            ))
        }
        events?.invoke(PaymentEvent.Processing())
        delay(600)
        events?.invoke(PaymentEvent.Success())
        return Result.success(
            PaymentResult(
                id = randomPaymentId(),
                provider = id,
                providerTransactionId = handle.intentId,
                amount = handle.amount,
                currency = handle.currency,
                cardBrand = "Mastercard",
                last4 = "5318",
                entryMode = EntryMode.CONTACTLESS,
                receiptUrl = null,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.catching {
        // Real: CardEntry.cancelCardEntryAction()
    }

    override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> = Result.catching {
        // Real: RefundManager.beginRefund(refundInfo, callback)
        delay(400)
        RefundResult(
            id = UUID.randomUUID().toString(),
            originalPaymentId = paymentId,
            amount = amount ?: Money.ZERO,
            status = RefundStatus.SUCCEEDED,
            providerRefundId = "sq-refund-${UUID.randomUUID()}",
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun close(): Result<Unit> = Result.catching {
        disconnectReader()
    }

    companion object { private const val TAG = "SquareProvider" }
}
