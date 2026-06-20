package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.ModifierGroup
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailState(
    val product: Product? = null,
    val inventory: InventorySnapshot? = null,
    val category: Category? = null,
    val modifierGroups: List<ModifierGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDeleted: Boolean = false,
    val showDeleteDialog: Boolean = false
)

sealed class ProductDetailEvent {
    data class ProductDeleted(val productId: ProductId) : ProductDetailEvent()
    data class AvailabilityToggled(val available: Boolean) : ProductDetailEvent()
    data class Error(val message: String) : ProductDetailEvent()
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val storeRepo: StoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProductDetailState())
    val state: StateFlow<ProductDetailState> = _state.asStateFlow()

    private val _events = Channel<ProductDetailEvent>(Channel.BUFFERED)
    val events: Flow<ProductDetailEvent> = _events.receiveAsFlow()

    private var storeId: StoreId? = null

    init {
        viewModelScope.launch {
            storeRepo.current().onSuccess { store ->
                storeId = store.id
            }
        }
    }

    fun load(productId: ProductId) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.observeProduct(productId)
            .onEach { product ->
                if (product == null) {
                    _state.value = _state.value.copy(
                        product = null,
                        isLoading = false,
                        error = "Product not found"
                    )
                    return@onEach
                }
                val category = repository.getCategory(product.categoryId).getOrNull()
                val modifierGroups = product.modifierGroupIds.mapNotNull {
                    repository.getModifierGroup(it).getOrNull()
                }
                _state.value = _state.value.copy(
                    product = product,
                    category = category,
                    modifierGroups = modifierGroups,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            storeId?.let { sid ->
                repository.observeProduct(productId).collect { product ->
                    product?.defaultVariant?.let { variant ->
                        repository.observeInventory(sid, variant.id).collect { inv ->
                            _state.value = _state.value.copy(inventory = inv)
                        }
                    }
                }
            }
        }
    }

    fun toggleAvailability(productId: ProductId) {
        val current = _state.value.product ?: return
        val sid = storeId ?: return
        val newAvailable = !current.isAvailable
        viewModelScope.launch {
            repository.setAvailable(sid, productId, newAvailable)
                .onSuccess {
                    _events.send(ProductDetailEvent.AvailabilityToggled(newAvailable))
                }
                .onFailure { error ->
                    _events.send(ProductDetailEvent.Error(error.message))
                }
        }
    }

    fun confirmDelete() {
        _state.value = _state.value.copy(showDeleteDialog = true)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(showDeleteDialog = false)
    }

    fun deleteProduct(productId: ProductId) {
        val sid = storeId ?: return
        viewModelScope.launch {
            repository.deleteProduct(sid, productId)
                .onSuccess {
                    _state.value = _state.value.copy(isDeleted = true, showDeleteDialog = false)
                    _events.send(ProductDetailEvent.ProductDeleted(productId))
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(showDeleteDialog = false)
                    _events.send(ProductDetailEvent.Error(error.message))
                }
        }
    }
}
