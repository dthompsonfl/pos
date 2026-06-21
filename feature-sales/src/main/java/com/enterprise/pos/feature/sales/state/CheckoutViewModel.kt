package com.enterprise.pos.feature.sales.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentErrorCode
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Payment as DomainPayment
import com.enterprise.pos.domain.model.TenderSplit
import com.enterprise.pos.domain.repository.GiftCardRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.ReturnsRepository
import com.enterprise.pos.domain.service.SplitTenderEngine
import com.enterprise.pos.payment.model.PaymentEvent
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentResult
import com.enterprise.pos.payment.router.PaymentRouter
import com.enterprise.pos.payment.router.RoutedPaymentIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Strongly-typed one-shot UI events for transient messages (info / success / error toasts).
 * Using a Channel prevents state drift: the message is consumed once and disappears.
 */
sealed class CheckoutUiEvent {
    data class Info(val message: String) : CheckoutUiEvent()
    data class Error(val message: String) : CheckoutUiEvent()
    data class PaymentCompleted(val provider: PaymentProviderId, val amount: Money) : CheckoutUiEvent()
    data class GiftCardApplied(val amount: Money, val remainingBalance: Money) : CheckoutUiEvent()
    data object PaymentCancelled : CheckoutUiEvent()
}

data class CheckoutState(
    val amountDue: Money = Money.ZERO,
    val originalTotal: Money = Money.ZERO,
    val selectedProvider: PaymentProviderId? = null,
    val availableProviders: Set<PaymentProviderId> = emptySet(),
    val currentEvent: PaymentEvent? = null,
    val isProcessing: Boolean = false,
    val routedIntent: RoutedPaymentIntent? = null,
    val result: PaymentResult? = null,
    val error: String? = null,
    val splitMode: Boolean = false,
    val splitTenders: MutableList<TenderSplit> = mutableListOf(),
    val completedTenders: List<TenderSplit> = emptyList(),
    val giftCardCode: String = "",
    val giftCardBalance: Money? = null,
    val giftCardError: String? = null,
    val cashTenderedInput: String = "",
    val cashChangeDue: Money = Money.ZERO,
) {
    /** Sum of completed tenders + gift card redemptions already applied. */
    @Suppress("unused")
    val amountPaid: Money
        get() = completedTenders.fold(Money.ZERO) { acc, t -> acc + t.amount }
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val router: PaymentRouter,
    private val giftCards: GiftCardRepository,
    @Suppress("unused") private val returns: ReturnsRepository,
    private val orders: OrderRepository,
    @Suppress("unused") private val splitEngine: SplitTenderEngine,
    private val logger: Logger = NoopLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    private val _events = Channel<CheckoutUiEvent>(capacity = Channel.BUFFERED)
    @Suppress("unused")
    val events = _events.receiveAsFlow()

    init {
        _state.value = _state.value.copy(availableProviders = router.availableProviders)
    }

    fun loadOrder(orderId: OrderId) {
        // Pull live total from the order repository.
        viewModelScope.launch {
            orders.getById(orderId)
                .onSuccess { order ->
                    val due = order?.grandTotal ?: Money.ZERO
                    _state.value = _state.value.copy(
                        amountDue = due,
                        originalTotal = due
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(amountDue = Money.ZERO, originalTotal = Money.ZERO)
                }
        }
    }

    fun selectProvider(provider: PaymentProviderId) {
        _state.value = _state.value.copy(selectedProvider = provider)
    }

    @Suppress("unused")
    fun setSplitMode(enabled: Boolean) {
        _state.value = _state.value.copy(
            splitMode = enabled,
            splitTenders = mutableListOf(),
            completedTenders = emptyList()
        )
    }

    @Suppress("unused")
    fun addSplit(provider: PaymentProviderId, amount: Money) {
        val list = _state.value.splitTenders.toMutableList()
        list.removeAll { it.provider == provider.name }
        list.add(TenderSplit(provider = provider.name, amount = amount))
        _state.value = _state.value.copy(splitTenders = list)
    }

    @Suppress("unused")
    fun removeSplit(provider: PaymentProviderId) {
        val list = _state.value.splitTenders.toMutableList()
        list.removeAll { it.provider == provider.name }
        _state.value = _state.value.copy(splitTenders = list)
    }

    @Suppress("unused")
    fun setGiftCardCode(s: String) {
        _state.value = _state.value.copy(giftCardCode = s, giftCardError = null)
    }

    @Suppress("unused")
    fun checkGiftCard() {
        val code = _state.value.giftCardCode
        if (code.isBlank()) return
        viewModelScope.launch {
            giftCards.getByCode(code)
                .onSuccess { card ->
                    if (card == null) {
                        _state.value = _state.value.copy(giftCardError = "Gift card not found", giftCardBalance = null)
                    } else if (!card.active) {
                        _state.value = _state.value.copy(giftCardError = "Gift card inactive", giftCardBalance = null)
                    } else {
                        _state.value = _state.value.copy(giftCardBalance = card.balance, giftCardError = null)
                    }
                }
                .onFailure { _state.value = _state.value.copy(giftCardError = it.message) }
        }
    }

    @Suppress("unused")
    fun applyGiftCard(orderId: OrderId, employeeId: EmployeeId) {
        val code = _state.value.giftCardCode
        val balance = _state.value.giftCardBalance ?: return
        val amountToApply = if (balance > _state.value.amountDue) _state.value.amountDue else balance
        if (code.isBlank() || amountToApply.isZero()) return
        viewModelScope.launch {
            giftCards.redeem(code, amountToApply, orderId, employeeId)
                .onSuccess { card ->
                    // Record the gift-card tender as a completed split with proper provider type.
                    val newCompleted = _state.value.completedTenders + TenderSplit(
                        provider = "GIFT_CARD",
                        amount = amountToApply,
                        reference = "gift_card:$code"
                    )
                    _state.value = _state.value.copy(
                        giftCardBalance = card.balance,
                        amountDue = _state.value.amountDue - amountToApply,
                        giftCardCode = "",
                        completedTenders = newCompleted
                    )
                    _events.trySend(CheckoutUiEvent.GiftCardApplied(amountToApply, card.balance))
                }
                .onFailure {
                    _state.value = _state.value.copy(giftCardError = it.message)
                    _events.trySend(CheckoutUiEvent.Error(it.message))
                }
        }
    }

    // --- Cash tender ---
    fun setCashTendered(input: String) {
        val parsed = input.toDoubleOrNull() ?: 0.0
        val tendered = Money.of(parsed)
        val change = tendered - _state.value.amountDue
        _state.value = _state.value.copy(
            cashTenderedInput = input,
            cashChangeDue = if (change.isNegative()) Money.ZERO else change
        )
    }

    fun canProcessCash(): Boolean {
        val tendered = Money.of(_state.value.cashTenderedInput.toDoubleOrNull() ?: 0.0)
        return (!tendered.isZero()) && (tendered >= _state.value.amountDue)
    }

    /**
     * Process the active payment. Routes to split-tender path or single-tender path.
     * Marks the order PAID only after the amount due reaches zero AND all tenders are persisted.
     */
    fun startPayment(orderId: OrderId, employeeId: EmployeeId) {
        if (_state.value.isProcessing) {
            logger.w(TAG, "startPayment called while already processing; ignoring duplicate")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, error = null)
            try {
                if (_state.value.splitMode && _state.value.splitTenders.isNotEmpty()) {
                    processSplitPayment(orderId, employeeId)
                } else {
                    processSinglePayment(orderId, employeeId)
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Payment failed", t)
                _state.value = _state.value.copy(isProcessing = false, error = t.message ?: "Payment failed")
                _events.trySend(CheckoutUiEvent.Error(t.message ?: "Payment failed"))
            }
        }
    }

    private suspend fun processSinglePayment(orderId: OrderId, employeeId: EmployeeId) {
        val amount = _state.value.amountDue
        val requested = _state.value.selectedProvider
            ?: run {
                _state.value = _state.value.copy(isProcessing = false, error = "Select a payment method")
                return
            }

        // Cash has a special path that needs the tendered amount.
        if (requested == PaymentProviderId.CASH) {
            if (!canProcessCash()) {
                _state.value = _state.value.copy(isProcessing = false, error = "Enter cash tendered >= amount due")
                return
            }
            processCashPayment(orderId, employeeId)
            return
        }

        when (val init = router.initiatePayment(orderId, amount, requestedProvider = requested)) {
            is Result.Failure -> {
                _state.value = _state.value.copy(isProcessing = false, error = init.error.message)
                _events.trySend(CheckoutUiEvent.Error(init.error.message))
            }
            is Result.Success -> {
                _state.value = _state.value.copy(routedIntent = init.value)
                val r = router.collectPayment(init.value) { event ->
                    _state.value = _state.value.copy(currentEvent = event)
                }
                when (r) {
                    is Result.Success -> completeOrderWithPayment(orderId, employeeId, r.value)
                    is Result.Failure -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            error = r.error.message,
                            currentEvent = null
                        )
                        _events.trySend(CheckoutUiEvent.Error(r.error.message))
                    }
                }
            }
        }
    }

    private suspend fun processCashPayment(orderId: OrderId, employeeId: EmployeeId) {
        val tendered = Money.of(_state.value.cashTenderedInput.toDoubleOrNull() ?: 0.0)
        val due = _state.value.amountDue
        // The PaymentRouter.CashPaymentProvider does not know the tendered amount; we record both.
        val init = router.initiatePayment(orderId, due, requestedProvider = PaymentProviderId.CASH)
        if (init is Result.Failure) {
            _state.value = _state.value.copy(isProcessing = false, error = init.error.message)
            _events.trySend(CheckoutUiEvent.Error(init.error.message))
            return
        }
            when (val r = router.collectPayment((init as Result.Success).value) { event ->
                _state.value = _state.value.copy(currentEvent = event)
            }) {
                is Result.Success -> {
                    val cashResult = r.value.copy(
                    // Augment with cash-specific accounting metadata.
                    metadata = mapOf(
                        "cash_tendered_minor" to tendered.minorUnits.toString(),
                        "cash_change_minor" to (tendered - due).minorUnits.toString(),
                        "employee_id" to employeeId.value
                    )
                )
                completeOrderWithPayment(orderId, employeeId, cashResult)
            }
            is Result.Failure -> {
                _state.value = _state.value.copy(isProcessing = false, error = r.error.message)
                _events.trySend(CheckoutUiEvent.Error(r.error.message))
            }
        }
    }

    private suspend fun processSplitPayment(orderId: OrderId, employeeId: EmployeeId) {
        val tenders = _state.value.splitTenders.toList()
        val completed = _state.value.completedTenders.toMutableList()
        var remaining = _state.value.amountDue

        for (split in tenders) {
            if (remaining.isZero()) break
            val providerId = runCatching { PaymentProviderId.valueOf(split.provider) }.getOrNull()
            if (providerId == null) {
                _events.trySend(CheckoutUiEvent.Error("Unknown provider: ${split.provider}"))
                continue
            }
            val init = router.initiatePayment(orderId, split.amount, requestedProvider = providerId)
            if (init is Result.Failure) {
                _state.value = _state.value.copy(isProcessing = false, error = "Split ${split.provider} failed: ${init.error.message}")
                _events.trySend(CheckoutUiEvent.Error("Split ${split.provider} failed: ${init.error.message}"))
                return
            }
            val r = router.collectPayment((init as Result.Success).value) { event ->
                _state.value = _state.value.copy(currentEvent = event)
            }
            when (r) {
                is Result.Failure -> {
                    _state.value = _state.value.copy(isProcessing = false, error = "Split ${split.provider} failed: ${r.error.message}")
                    _events.trySend(CheckoutUiEvent.Error("Split ${split.provider} failed: ${r.error.message}"))
                    return
                }
                is Result.Success -> {
                    val paymentResult = r.value
                    // Persist each tender independently via markPaid.
                    // markPaid accumulates payments and only marks the order PAID when amountDue reaches zero.
                    orders.markPaid(orderId, paymentResult.toDomainPayment(orderId), employeeId)
                        .onFailure { err ->
                            logger.e(TAG, "Failed to persist split tender: ${err.message}")
                            _state.value = _state.value.copy(isProcessing = false, error = err.message)
                            _events.trySend(CheckoutUiEvent.Error(err.message))
                            return
                        }
                    completed.add(
                        TenderSplit(
                            provider = split.provider,
                            amount = split.amount,
                            paymentId = paymentResult.id,
                            reference = paymentResult.providerTransactionId
                        )
                    )
                    remaining -= split.amount
                    _state.value = _state.value.copy(completedTenders = completed.toList())
                }
            }
        }

        if (remaining.isZero()) {
            // All tenders completed and persisted. Order is now fully paid.
            _state.value = _state.value.copy(
                isProcessing = false,
                amountDue = Money.ZERO,
                currentEvent = PaymentEvent.Success()
            )
            val firstTender = completed.firstOrNull()
            val provider = firstTender?.let {
                runCatching { PaymentProviderId.valueOf(it.provider) }.getOrNull()
            } ?: PaymentProviderId.CASH
            _events.trySend(CheckoutUiEvent.PaymentCompleted(provider, _state.value.originalTotal))
        } else {
            _state.value = _state.value.copy(
                isProcessing = false,
                amountDue = remaining,
                error = "Split tender incomplete: ${remaining.format()} still due"
            )
            _events.trySend(CheckoutUiEvent.Error("Split tender incomplete: ${remaining.format()} still due"))
        }
    }

    /**
     * Mark order PAID + persist the payment + write audit log + queue sync event.
     * All in one transactional call.
     */
    private fun completeOrderWithPayment(orderId: OrderId, employeeId: EmployeeId, payment: PaymentResult) {
        viewModelScope.launch {
            // 1. Persist the payment via the order repository's transactional close.
            orders.markPaid(orderId, payment.toDomainPayment(orderId), employeeId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        result = payment,
                        amountDue = Money.ZERO,
                        currentEvent = PaymentEvent.Success()
                    )
                    _events.trySend(CheckoutUiEvent.PaymentCompleted(payment.provider, payment.amount))
                }
                .onFailure { err ->
                    logger.e(TAG, "Failed to mark order paid: ${err.message}")
                    _state.value = _state.value.copy(isProcessing = false, error = err.message)
                    _events.trySend(CheckoutUiEvent.Error(err.message))
                }
        }
    }

    fun cancel() {
        val intent = _state.value.routedIntent ?: run {
            _state.value = _state.value.copy(isProcessing = false)
            _events.trySend(CheckoutUiEvent.PaymentCancelled)
            return
        }
        viewModelScope.launch {
            router.cancelPayment(intent)
            _state.value = _state.value.copy(
                isProcessing = false,
                currentEvent = PaymentEvent.Cancelled(),
                routedIntent = null
            )
            _events.trySend(CheckoutUiEvent.PaymentCancelled)
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    // --- Refunds ---
    @Suppress("unused")
    fun refund(paymentId: PaymentId, originalProvider: PaymentProviderId, amount: Money, reason: String) {
        viewModelScope.launch {
            router.refund(paymentId, originalProvider, amount, reason)
                .onSuccess { _events.trySend(CheckoutUiEvent.Info("Refunded ${amount.format()}")) }
                .onFailure {
                    _state.value = _state.value.copy(error = it.message)
                    _events.trySend(CheckoutUiEvent.Error(it.message ?: "Refund failed"))
                }
        }
    }

    companion object { private const val TAG = "CheckoutViewModel" }
}

private fun PaymentResult.toDomainPayment(orderId: OrderId): DomainPayment = DomainPayment(
    id = this.id,
    orderId = orderId,
    provider = this.provider.name,
    providerTransactionId = this.providerTransactionId,
    amount = this.amount,
    currency = this.currency,
    cardBrand = this.cardBrand,
    last4 = this.last4,
    entryMode = this.entryMode?.name,
    receiptUrl = this.receiptUrl,
    capturedAt = this.capturedAt
)
