package com.enterprise.pos.feature.inventory.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.PurchaseOrder
import com.enterprise.pos.domain.model.PurchaseOrderLine
import com.enterprise.pos.domain.model.PurchaseOrderStatus
import com.enterprise.pos.domain.model.Supplier
import com.enterprise.pos.domain.repository.InventoryManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseOrderLineForm(
    val productId: com.enterprise.pos.core.ProductId? = null,
    val productName: String = "",
    val quantity: String = "1",
    val unitCost: String = ""
) {
    val total: Money
        get() {
            val qty = quantity.toIntOrNull() ?: 0
            val cost = unitCost.toDoubleOrNull() ?: 0.0
            return Money.of(cost) * qty
        }
}

data class PurchaseOrderForm(
    val supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>? = null,
    val orderDate: Long = SystemClock.now(),
    val expectedDelivery: Long? = null,
    val status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,
    val notes: String = "",
    val shippingCost: String = "0.00",
    val taxPercent: String = "0.00"
)

data class PurchaseOrderUiState(
    val orderId: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>? = null,
    val form: PurchaseOrderForm = PurchaseOrderForm(),
    val lineForms: List<PurchaseOrderLineForm> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val products: List<Product> = emptyList(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
) {
    val subtotal: Money
        get() = lineForms.fold(Money.ZERO) { acc, line -> acc + line.total }

    val shipping: Money
        get() = Money.of(form.shippingCost.toDoubleOrNull() ?: 0.0)

    val tax: Money
        get() {
            val pct = form.taxPercent.toDoubleOrNull() ?: 0.0
            return Percent.of(pct).of(subtotal)
        }

    val total: Money
        get() = subtotal + shipping + tax
}

sealed class PurchaseOrderEvent {
    data object Saved : PurchaseOrderEvent()
    data object Back : PurchaseOrderEvent()
}

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    private val inventoryRepo: InventoryManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PurchaseOrderUiState())
    val state: StateFlow<PurchaseOrderUiState> = _state.asStateFlow()

    private val _events = Channel<PurchaseOrderEvent>()
    val events = _events.receiveAsFlow()

    fun loadNew(storeId: StoreId) {
        _state.value = PurchaseOrderUiState()
        loadSuppliers(storeId)
    }

    fun loadExisting(orderId: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>, storeId: StoreId) {
        _state.value = _state.value.copy(isLoading = true, orderId = orderId)
        viewModelScope.launch {
            inventoryRepo.getPurchaseOrder(orderId)
                .onSuccess { order ->
                    if (order != null) {
                        _state.value = _state.value.copy(
                            form = PurchaseOrderForm(
                                supplierId = order.supplierId,
                                orderDate = order.orderDate,
                                expectedDelivery = order.expectedDelivery,
                                status = order.status,
                                notes = order.notes ?: "",
                                shippingCost = order.shippingCost.asBigDecimal.toPlainString(),
                                taxPercent = order.taxPercent.asDouble.toString()
                            ),
                            lineForms = order.lines.map { line ->
                                PurchaseOrderLineForm(
                                    productId = line.productId,
                                    productName = line.productName,
                                    quantity = line.quantity.toString(),
                                    unitCost = line.unitCost.asBigDecimal.toPlainString()
                                )
                            },
                            isLoading = false
                        )
                    } else {
                        _state.value = _state.value.copy(error = "Order not found", isLoading = false)
                    }
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isLoading = false)
                }
        }
        loadSuppliers(storeId)
    }

    private fun loadSuppliers(storeId: StoreId) {
        inventoryRepo.observeSuppliers(storeId)
            .onEach { suppliers ->
                _state.value = _state.value.copy(suppliers = suppliers)
            }
            .launchIn(viewModelScope)
    }

    fun setSupplier(supplier: Supplier?) {
        _state.value = _state.value.copy(form = _state.value.form.copy(supplierId = supplier?.id))
    }

    fun setOrderDate(date: Long) {
        _state.value = _state.value.copy(form = _state.value.form.copy(orderDate = date))
    }

    fun setExpectedDelivery(date: Long?) {
        _state.value = _state.value.copy(form = _state.value.form.copy(expectedDelivery = date))
    }

    fun setNotes(notes: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(notes = notes))
    }

    fun setShippingCost(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(shippingCost = value))
    }

    fun setTaxPercent(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(taxPercent = value))
    }

    fun addLineItem(product: Product) {
        val variant = product.defaultVariant
        val newLine = PurchaseOrderLineForm(
            productId = product.id,
            productName = product.name,
            quantity = "1",
            unitCost = variant?.costPrice?.asBigDecimal?.toPlainString() ?: "0.00"
        )
        _state.value = _state.value.copy(lineForms = _state.value.lineForms + newLine)
    }

    fun updateLineQuantity(index: Int, value: String) {
        val lines = _state.value.lineForms.toMutableList()
        if (index in lines.indices) {
            lines[index] = lines[index].copy(quantity = value.filter { it.isDigit() })
            _state.value = _state.value.copy(lineForms = lines)
        }
    }

    fun updateLineUnitCost(index: Int, value: String) {
        val lines = _state.value.lineForms.toMutableList()
        if (index in lines.indices) {
            lines[index] = lines[index].copy(unitCost = value)
            _state.value = _state.value.copy(lineForms = lines)
        }
    }

    fun removeLineItem(index: Int) {
        val lines = _state.value.lineForms.toMutableList()
        if (index in lines.indices) {
            lines.removeAt(index)
            _state.value = _state.value.copy(lineForms = lines)
        }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        val form = _state.value.form
        if (form.supplierId == null) {
            errors["supplier"] = "Supplier is required"
        }
        if (_state.value.lineForms.isEmpty()) {
            errors["lines"] = "At least one line item is required"
        }
        _state.value.lineForms.forEachIndexed { index, line ->
            val qty = line.quantity.toIntOrNull()
            if (qty == null || qty <= 0) {
                errors["line_${index}_qty"] = "Quantity must be greater than 0"
            }
            val cost = line.unitCost.toDoubleOrNull()
            if (cost == null || cost < 0) {
                errors["line_${index}_cost"] = "Unit cost must be 0 or greater"
            }
        }
        _state.value = _state.value.copy(validationErrors = errors)
        return errors.isEmpty()
    }

    fun saveOrder(storeId: StoreId) {
        if (!validate()) return
        val currentState = _state.value
        val orderId = currentState.orderId ?: Id.random()
        val supplierId = currentState.form.supplierId ?: return
        val supplier = currentState.suppliers.find { it.id == supplierId }

        val lines = currentState.lineForms.map { lineForm ->
            PurchaseOrderLine(
                id = java.util.UUID.randomUUID().toString(),
                productId = lineForm.productId ?: com.enterprise.pos.core.randomProductId(),
                productName = lineForm.productName,
                quantity = lineForm.quantity.toIntOrNull() ?: 1,
                unitCost = Money.of(lineForm.unitCost.toDoubleOrNull() ?: 0.0)
            )
        }

        val po = PurchaseOrder(
            id = orderId,
            storeId = storeId,
            supplierId = supplierId,
            supplierName = supplier?.name ?: "Unknown",
            orderDate = currentState.form.orderDate,
            expectedDelivery = currentState.form.expectedDelivery,
            status = currentState.form.status,
            lines = lines,
            notes = currentState.form.notes.ifBlank { null },
            shippingCost = Money.of(currentState.form.shippingCost.toDoubleOrNull() ?: 0.0),
            taxPercent = Percent.of(currentState.form.taxPercent.toDoubleOrNull() ?: 0.0)
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            inventoryRepo.upsertPurchaseOrder(po)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Purchase order saved",
                        orderId = it.id
                    )
                    _events.send(PurchaseOrderEvent.Saved)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun sendOrder() {
        val orderId = _state.value.orderId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            inventoryRepo.sendPurchaseOrder(orderId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Purchase order sent to supplier",
                        form = _state.value.form.copy(status = PurchaseOrderStatus.SENT)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun receiveOrder() {
        val orderId = _state.value.orderId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            inventoryRepo.receivePurchaseOrder(orderId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Purchase order marked as received",
                        form = _state.value.form.copy(status = PurchaseOrderStatus.RECEIVED)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun cancelOrder() {
        val orderId = _state.value.orderId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            inventoryRepo.cancelPurchaseOrder(orderId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Purchase order cancelled",
                        form = _state.value.form.copy(status = PurchaseOrderStatus.CANCELLED)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun dismissResult() {
        _state.value = _state.value.copy(result = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(PurchaseOrderEvent.Back)
        }
    }
}
