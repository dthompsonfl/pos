package com.enterprise.pos.feature.sales.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.service.CartEngine
import com.enterprise.pos.domain.service.OrderTotalBreakdown
import com.enterprise.pos.domain.usecase.AddItemToOrderUseCase
import com.enterprise.pos.domain.usecase.ApplyDiscountUseCase
import com.enterprise.pos.domain.usecase.FinalizeOrderForPaymentUseCase
import com.enterprise.pos.domain.usecase.GetOrderTotalsUseCase
import com.enterprise.pos.domain.usecase.SendToKitchenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val order: Order? = null,
    val breakdown: OrderTotalBreakdown? = null,
    val isLoading: Boolean = false,
    val isSendingToKitchen: Boolean = false,
    val isFinalizing: Boolean = false,
    val error: String? = null,
    val showDiscountSheet: Boolean = false,
    val showTipSheet: Boolean = false,
    val showPaymentScreen: Boolean = false,
    val info: String? = null
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val orders: OrderRepository,
    private val catalog: CatalogRepository,
    private val cartEngine: CartEngine,
    private val addItem: AddItemToOrderUseCase,
    private val sendToKitchen: SendToKitchenUseCase,
    private val finalize: FinalizeOrderForPaymentUseCase,
    private val applyDiscount: ApplyDiscountUseCase,
    private val totals: GetOrderTotalsUseCase,
    private val logger: Logger = NoopLogger
) : ViewModel() {

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    fun loadOrder(orderId: OrderId) {
        viewModelScope.launch {
            orders.observeOrder(orderId).collect { o ->
                if (o != null) {
                    val bd = totals.invoke(o).getOrNull() ?: OrderTotalBreakdown.of(o)
                    _state.value = _state.value.copy(order = o, breakdown = bd, isLoading = false)
                }
            }
        }
    }

    fun addProduct(productId: ProductId, variantId: VariantId? = null) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            addItem(order, productId, variantId).onSuccess { updated ->
                _state.value = _state.value.copy(order = updated, info = "Added ${updated.lines.lastOrNull()?.name}")
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun changeQuantity(lineId: OrderLineId, newQty: com.enterprise.pos.core.Quantity) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            val now = SystemClock.now()
            val updated = cartEngine.changeQuantity(order, lineId, newQty, now).getOrThrow()
            orders.updateOrder(updated)
        }
    }

    fun removeLine(lineId: OrderLineId) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            val updated = cartEngine.removeLine(order, lineId, SystemClock.now())
            orders.updateOrder(updated)
        }
    }

    fun addNote(lineId: OrderLineId, note: String) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            val idx = order.lines.indexOfFirst { it.id == lineId }
            if (idx >= 0) {
                val updated = order.lines[idx].copy(notes = note)
                val newLines = order.lines.toMutableList().also { it[idx] = updated }
                orders.updateOrder(order.copy(lines = newLines, updatedAt = SystemClock.now()))
            }
        }
    }

    fun sendToKitchen() {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSendingToKitchen = true)
            sendToKitchen(order).onSuccess {
                _state.value = _state.value.copy(isSendingToKitchen = false, info = "Sent to kitchen")
            }.onFailure {
                _state.value = _state.value.copy(isSendingToKitchen = false, error = it.message)
            }
        }
    }

    fun applyDiscount(percent: Int, requestingEmployee: EmployeeId) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            val disc = Discount(
                id = java.util.UUID.randomUUID().toString(),
                name = "${percent}% off",
                type = DiscountType.PERCENTAGE,
                percent = Percent.of(percent.toDouble()),
                requiresManagerApproval = percent > 25
            )
            applyDiscount(order, disc, requestingEmployee).onSuccess {
                _state.value = _state.value.copy(order = it, showDiscountSheet = false, info = "Discount applied")
            }.onFailure { _state.value = _state.value.copy(error = it.message, showDiscountSheet = false) }
        }
    }

    fun setTip(amount: Money) {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            val updated = cartEngine.setTip(order, amount, SystemClock.now())
            orders.updateOrder(updated)
            _state.value = _state.value.copy(order = updated, showTipSheet = false)
        }
    }

    fun requestPayment() {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isFinalizing = true)
            finalize(order).onSuccess { finalized ->
                _state.value = _state.value.copy(order = finalized, isFinalizing = false, showPaymentScreen = true)
            }.onFailure {
                _state.value = _state.value.copy(isFinalizing = false, error = it.message)
            }
        }
    }

    fun openDiscountSheet() { _state.value = _state.value.copy(showDiscountSheet = true) }
    fun closeDiscountSheet() { _state.value = _state.value.copy(showDiscountSheet = false) }
    fun openTipSheet() { _state.value = _state.value.copy(showTipSheet = true) }
    fun closeTipSheet() { _state.value = _state.value.copy(showTipSheet = false) }
    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
    fun exitPayment() { _state.value = _state.value.copy(showPaymentScreen = false) }
}
