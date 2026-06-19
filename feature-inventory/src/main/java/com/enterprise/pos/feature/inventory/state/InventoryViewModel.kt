package com.enterprise.pos.feature.inventory.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.repository.InventoryManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val snapshots: List<InventorySnapshot> = emptyList(),
    val lowStockOnly: Boolean = false,
    val isLoading: Boolean = true,
    val totalValue: Money = Money.ZERO,
    val lowStockCount: Int = 0,
    val showAdjustmentSheet: Boolean = false,
    val adjustmentVariantId: com.enterprise.pos.core.VariantId? = null,
    val adjustmentDelta: String = "",
    val adjustmentReason: AdjustmentReason = AdjustmentReason.RECEIVED,
    val adjustmentNotes: String = "",
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepo: InventoryManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    fun load(storeId: StoreId) {
        inventoryRepo.observeLowStock(storeId)
            .onEach { snapshots ->
                _state.value = _state.value.copy(
                    snapshots = snapshots,
                    lowStockCount = snapshots.count { it.isLow },
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            inventoryRepo.valuation(storeId).onSuccess {
                _state.value = _state.value.copy(totalValue = it)
            }
        }
    }

    fun toggleLowStockFilter() {
        _state.value = _state.value.copy(lowStockOnly = !_state.value.lowStockOnly)
    }

    fun openAdjustmentSheet(variantId: com.enterprise.pos.core.VariantId) {
        _state.value = _state.value.copy(
            showAdjustmentSheet = true,
            adjustmentVariantId = variantId,
            adjustmentDelta = "",
            adjustmentReason = AdjustmentReason.RECEIVED,
            adjustmentNotes = ""
        )
    }

    fun closeAdjustmentSheet() {
        _state.value = _state.value.copy(showAdjustmentSheet = false)
    }

    fun setDelta(s: String) { _state.value = _state.value.copy(adjustmentDelta = s) }
    fun setReason(r: AdjustmentReason) { _state.value = _state.value.copy(adjustmentReason = r) }
    fun setNotes(s: String) { _state.value = _state.value.copy(adjustmentNotes = s) }

    fun submitAdjustment(storeId: StoreId, employeeId: EmployeeId) {
        val variantId = _state.value.adjustmentVariantId ?: return
        val delta = _state.value.adjustmentDelta.toIntOrNull() ?: return
        val reason = _state.value.adjustmentReason
        val notes = _state.value.adjustmentNotes
        viewModelScope.launch {
            val adj = InventoryAdjustment(
                id = com.enterprise.pos.core.Id.random(),
                variantId = variantId,
                storeId = storeId,
                delta = delta,
                reason = reason,
                notes = notes,
                employeeId = employeeId,
                timestamp = SystemClock.now()
            )
            inventoryRepo.adjust(adj)
                .onSuccess {
                    _state.value = _state.value.copy(
                        showAdjustmentSheet = false,
                        info = "Inventory adjusted by $delta"
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun reorderAll(storeId: StoreId) {
        viewModelScope.launch {
            inventoryRepo.reorderAll(storeId).onSuccess { count ->
                _state.value = _state.value.copy(info = "$count items reordered")
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
