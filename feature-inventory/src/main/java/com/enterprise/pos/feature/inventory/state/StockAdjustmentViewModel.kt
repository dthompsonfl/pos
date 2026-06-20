package com.enterprise.pos.feature.inventory.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventoryItem
import com.enterprise.pos.domain.repository.InventoryManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockAdjustmentForm(
    val productName: String = "",
    val currentQuantity: Int = 0,
    val adjustmentQuantity: String = "",
    val newQuantity: Int = 0,
    val reason: AdjustmentReason = AdjustmentReason.COUNT_CORRECTION,
    val notes: String = "",
    val employeeName: String = ""
)

data class StockAdjustmentUiState(
    val product: InventoryItem? = null,
    val form: StockAdjustmentForm = StockAdjustmentForm(),
    val reasons: List<AdjustmentReason> = AdjustmentReason.entries,
    val isSaving: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

sealed class StockAdjustmentEvent {
    data object Saved : StockAdjustmentEvent()
    data object Back : StockAdjustmentEvent()
}

@HiltViewModel
class StockAdjustmentViewModel @Inject constructor(
    private val inventoryRepo: InventoryManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StockAdjustmentUiState())
    val state: StateFlow<StockAdjustmentUiState> = _state.asStateFlow()

    private val _events = Channel<StockAdjustmentEvent>()
    val events = _events.receiveAsFlow()

    fun load(variantId: VariantId, storeId: StoreId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            inventoryRepo.getInventoryItem(variantId, storeId)
                .onSuccess { item ->
                    _state.value = _state.value.copy(
                        product = item,
                        form = StockAdjustmentForm(
                            productName = item?.productName ?: "",
                            currentQuantity = item?.onHand ?: 0
                        ),
                        isSaving = false
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun setAdjustmentQuantity(value: String) {
        val digitsOnly = value.filter { it.isDigit() || it == '-' }
        val adj = digitsOnly.toIntOrNull() ?: 0
        val current = _state.value.form.currentQuantity
        val newQty = current + adj
        _state.value = _state.value.copy(
            form = _state.value.form.copy(
                adjustmentQuantity = digitsOnly,
                newQuantity = newQty.coerceAtLeast(0)
            )
        )
    }

    fun setReason(reason: AdjustmentReason) {
        _state.value = _state.value.copy(form = _state.value.form.copy(reason = reason))
    }

    fun setNotes(notes: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(notes = notes))
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        val form = _state.value.form
        if (form.adjustmentQuantity.isBlank() || form.adjustmentQuantity.toIntOrNull() == null) {
            errors["quantity"] = "Adjustment quantity is required"
        }
        if (form.newQuantity < 0) {
            errors["quantity"] = "New quantity cannot be negative"
        }
        _state.value = _state.value.copy(validationErrors = errors)
        return errors.isEmpty()
    }

    fun saveAdjustment(storeId: StoreId, employeeId: EmployeeId) {
        if (!validate()) return
        val product = _state.value.product ?: return
        val delta = _state.value.form.adjustmentQuantity.toIntOrNull() ?: return
        val reason = _state.value.form.reason
        val notes = _state.value.form.notes

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val adjustment = InventoryAdjustment(
                id = Id.random(),
                variantId = product.variantId,
                storeId = storeId,
                delta = delta,
                reason = reason,
                notes = notes.ifBlank { null },
                employeeId = employeeId,
                timestamp = SystemClock.now()
            )
            inventoryRepo.adjust(adjustment)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Stock adjusted by $delta units"
                    )
                    _events.send(StockAdjustmentEvent.Saved)
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
            _events.send(StockAdjustmentEvent.Back)
        }
    }
}
