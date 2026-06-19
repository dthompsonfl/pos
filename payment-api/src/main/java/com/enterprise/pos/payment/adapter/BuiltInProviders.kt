package com.enterprise.pos.payment.adapter

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Money
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

/** Cash drawer tender — always available, never needs a reader. */
class CashPaymentProvider : PaymentProvider {
    override val id: PaymentProviderId = PaymentProviderId.CASH
    override val displayName: String = "Cash"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.REFUNDS, PaymentCapability.SPLIT_TENDER
    )

    private val _status = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: StateFlow<ReaderStatus> = _status.asStateFlow()

    private val builtinReader = DiscoveredReader(
        id = "cash-drawer",
        displayName = "Cash Drawer",
        model = "Built-in",
        connectionType = ConnectionType.BUILT_IN
    )

    override suspend fun initialize(config: ProviderConfig): Result<Unit> {
        _status.value = ReaderStatus.Connected(builtinReader)
        return Result.success(Unit)
    }

    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.success(listOf(builtinReader))
    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> = Result.success(Unit)
    override suspend fun disconnectReader(): Result<Unit> = Result.success(Unit)

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> =
        Result.success(
            PaymentIntentHandle(
                provider = id,
                intentId = "cash-${UUID.randomUUID()}",
                amount = request.amount,
                currency = request.currency,
                createdAt = System.currentTimeMillis()
            )
        )

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        events?.invoke(PaymentEvent.Processing("Awaiting cash tendered…"))
        delay(50) // virtual settling time
        events?.invoke(PaymentEvent.Success("Cash accepted"))
        return Result.success(
            PaymentResult(
                id = randomPaymentId(),
                provider = id,
                providerTransactionId = handle.intentId,
                amount = handle.amount,
                currency = handle.currency,
                entryMode = EntryMode.CASH,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.success(Unit)

    override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> =
        Result.success(
            RefundResult(
                id = UUID.randomUUID().toString(),
                originalPaymentId = paymentId,
                amount = amount ?: Money.ZERO,
                status = RefundStatus.SUCCEEDED,
                providerRefundId = "cash-refund-${UUID.randomUUID()}",
                createdAt = System.currentTimeMillis()
            )
        )

    override suspend fun close(): Result<Unit> = Result.success(Unit)
}

/** Manual card entry — operator types the card number. Useful as a last-resort fallback. */
class ManualCardPaymentProvider : PaymentProvider {
    override val id: PaymentProviderId = PaymentProviderId.MANUAL
    override val displayName: String = "Manual Card Entry"
    override val capabilities: Set<PaymentCapability> = setOf(
        PaymentCapability.MANUAL_ENTRY, PaymentCapability.REFUNDS
    )

    private val _status = MutableStateFlow<ReaderStatus>(ReaderStatus.NotConnected)
    override val readerStatus: StateFlow<ReaderStatus> = _status.asStateFlow()

    override suspend fun initialize(config: ProviderConfig): Result<Unit> = Result.success(Unit)
    override suspend fun discoverReaders(): Result<List<DiscoveredReader>> = Result.success(emptyList())
    override suspend fun connectReader(reader: DiscoveredReader): Result<Unit> =
        Result.failure(AppError.Generic("Manual entry has no reader"))
    override suspend fun disconnectReader(): Result<Unit> = Result.success(Unit)

    override suspend fun createPaymentIntent(request: CreatePaymentRequest): Result<PaymentIntentHandle> =
        Result.success(
            PaymentIntentHandle(
                provider = id,
                intentId = "manual-${UUID.randomUUID()}",
                amount = request.amount,
                currency = request.currency,
                createdAt = System.currentTimeMillis()
            )
        )

    override suspend fun collectPayment(
        handle: PaymentIntentHandle,
        events: ((PaymentEvent) -> Unit)?
    ): Result<PaymentResult> {
        events?.invoke(PaymentEvent.Processing("Processing manual entry…"))
        delay(800) // simulate processing
        events?.invoke(PaymentEvent.Success("Approved"))
        return Result.success(
            PaymentResult(
                id = randomPaymentId(),
                provider = id,
                providerTransactionId = handle.intentId,
                amount = handle.amount,
                currency = handle.currency,
                entryMode = EntryMode.MANUAL,
                last4 = "4242",
                cardBrand = "Visa",
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun cancelPayment(handle: PaymentIntentHandle): Result<Unit> = Result.success(Unit)
    override suspend fun refund(paymentId: PaymentId, amount: Money?, reason: String): Result<RefundResult> =
        Result.success(
            RefundResult(
                id = UUID.randomUUID().toString(),
                originalPaymentId = paymentId,
                amount = amount ?: Money.ZERO,
                status = RefundStatus.SUCCEEDED,
                providerRefundId = "manual-refund-${UUID.randomUUID()}",
                createdAt = System.currentTimeMillis()
            )
        )
    override suspend fun close(): Result<Unit> = Result.success(Unit)
}
