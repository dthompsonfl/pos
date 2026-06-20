package com.enterprise.pos.feature.inventory.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventoryItem
import com.enterprise.pos.domain.model.StockMovement
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

data class InventoryDetailUiState(
    val item: InventoryItem? = null,
    val movements: List<StockMovement> = emptyList(),
    val supplier: Supplier? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAdjustDialog: Boolean = false,
    val showReorderDialog: Boolean = false,
    val reorderPoint: String = "",
    val reorderQuantity: String = "",
    val info: String? = null
)

sealed class InventoryDetailEvent {
    data class NavigateToAdjustment(val variantId: VariantId) : InventoryDetailEvent()
    data class NavigateToPurchaseOrder(val supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>?) : InventoryDetailEvent()
    data object Back : InventoryDetailEvent()
}

@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    private val inventoryRepo: InventoryManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryDetailUiState())
    val state: StateFlow<InventoryDetailUiState> = _state.asStateFlow()

    private val _events = Channel<InventoryDetailEvent>()
    val events = _events.receiveAsFlow()

    fun load(variantId: VariantId, storeId: StoreId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            inventoryRepo.getInventoryItem(variantId, storeId)
                .onSuccess { item ->
                    _state.value = _state.value.copy(
                        item = item,
                        reorderPoint = item?.reorderPoint?.toString() ?: "",
                        reorderQuantity = item?.reorderQuantity?.toString() ?: ""
                    )
                    item?.supplierId?.let { supplierId ->
                        inventoryRepo.getSupplier(supplierId)
                            .onSuccess { sup ->
                                _state.value = _state.value.copy(supplier = sup)
                            }
                    }
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
            _state.value = _state.value.copy(isLoading = false)
        }

        inventoryRepo.observeStockMovements(storeId, variantId)
            .onEach { movements ->
                _state.value = _state.value.copy(movements = movements)
            }
            .launchIn(viewModelScope)
    }

    fun openAdjustDialog() {
        val variantId = _state.value.item?.variantId ?: return
        viewModelScope.launch {
            _events.send(InventoryDetailEvent.NavigateToAdjustment(variantId))
        }
    }

    fun openReorderDialog() {
        _state.value = _state.value.copy(showReorderDialog = true)
    }

    fun closeReorderDialog() {
        _state.value = _state.value.copy(showReorderDialog = false)
    }

    fun setReorderPoint(value: String) {
        _state.value = _state.value.copy(reorderPoint = value.filter { it.isDigit() })
    }

    fun setReorderQuantity(value: String) {
        _state.value = _state.value.copy(reorderQuantity = value.filter { it.isDigit() })
    }

    fun saveReorderPoint(storeId: StoreId) {
        val item = _state.value.item ?: return
        val point = _state.value.reorderPoint.toIntOrNull() ?: return
        val qty = _state.value.reorderQuantity.toIntOrNull() ?: return
        viewModelScope.launch {
            inventoryRepo.setReorderPoint(item.variantId, storeId, point, qty)
                .onSuccess {
                    _state.value = _state.value.copy(
                        showReorderDialog = false,
                        info = "Reorder point updated",
                        item = item.copy(reorderPoint = point, reorderQuantity = qty)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun createPurchaseOrder() {
        val supplierId = _state.value.supplier?.id ?: _state.value.item?.supplierId
        viewModelScope.launch {
            _events.send(InventoryDetailEvent.NavigateToPurchaseOrder(supplierId))
        }
    }

    fun dismissInfo() {
        _state.value = _state.value.copy(info = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(InventoryDetailEvent.Back)
        }
    }
}
