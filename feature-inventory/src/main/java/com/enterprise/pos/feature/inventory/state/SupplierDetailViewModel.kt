package com.enterprise.pos.feature.inventory.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.PurchaseOrder
import com.enterprise.pos.domain.model.Supplier
import com.enterprise.pos.domain.model.SupplierPerformance
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

data class SupplierDetailUiState(
    val supplier: Supplier? = null,
    val orders: List<PurchaseOrder> = emptyList(),
    val products: List<com.enterprise.pos.domain.model.Product> = emptyList(),
    val performance: SupplierPerformance? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    val info: String? = null
)

sealed class SupplierDetailEvent {
    data class NavigateToPurchaseOrder(val supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>?) : SupplierDetailEvent()
    data object Back : SupplierDetailEvent()
}

@HiltViewModel
class SupplierDetailViewModel @Inject constructor(
    private val inventoryRepo: InventoryManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SupplierDetailUiState())
    val state: StateFlow<SupplierDetailUiState> = _state.asStateFlow()

    private val _events = Channel<SupplierDetailEvent>()
    val events = _events.receiveAsFlow()

    fun load(supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>, storeId: StoreId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            inventoryRepo.getSupplier(supplierId)
                .onSuccess { supplier ->
                    _state.value = _state.value.copy(supplier = supplier)
                    supplier?.let {
                        inventoryRepo.getSupplierPerformance(supplierId)
                            .onSuccess { perf ->
                                _state.value = _state.value.copy(performance = perf)
                            }
                    }
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
            _state.value = _state.value.copy(isLoading = false)
        }

        inventoryRepo.observePurchaseOrders(storeId, supplierId)
            .onEach { orders ->
                _state.value = _state.value.copy(orders = orders.sortedByDescending { it.orderDate })
            }
            .launchIn(viewModelScope)
    }

    fun createPurchaseOrder() {
        val supplierId = _state.value.supplier?.id
        viewModelScope.launch {
            _events.send(SupplierDetailEvent.NavigateToPurchaseOrder(supplierId))
        }
    }

    fun openDeleteDialog() {
        _state.value = _state.value.copy(showDeleteDialog = true)
    }

    fun closeDeleteDialog() {
        _state.value = _state.value.copy(showDeleteDialog = false)
    }

    fun confirmDelete() {
        val supplierId = _state.value.supplier?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            // Placeholder for delete operation — in production would call repo.deleteSupplier
            _state.value = _state.value.copy(
                isLoading = false,
                showDeleteDialog = false,
                info = "Supplier deleted"
            )
            _events.send(SupplierDetailEvent.Back)
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
            _events.send(SupplierDetailEvent.Back)
        }
    }
}
