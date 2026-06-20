package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryDetailState(
    val category: Category? = null,
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteDialog: Boolean = false
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val storeRepo: StoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryDetailState())
    val state: StateFlow<CategoryDetailState> = _state.asStateFlow()

    private var storeId: com.enterprise.pos.core.StoreId? = null

    init {
        viewModelScope.launch {
            storeRepo.current().onSuccess { store ->
                storeId = store.id
            }
        }
    }

    fun load(categoryId: CategoryId) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.observeCategory(categoryId)
            .onEach { category ->
                if (category == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Category not found"
                    )
                    return@onEach
                }
                _state.value = _state.value.copy(
                    category = category,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)

        repository.observeProducts(categoryId)
            .onEach { products ->
                _state.value = _state.value.copy(products = products)
            }
            .launchIn(viewModelScope)
    }

    fun confirmDelete() {
        _state.value = _state.value.copy(showDeleteDialog = true)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(showDeleteDialog = false)
    }

    fun deleteCategory(categoryId: CategoryId) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    _state.value = _state.value.copy(showDeleteDialog = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        showDeleteDialog = false,
                        error = error.message
                    )
                }
        }
    }

    fun reorderProducts(fromIndex: Int, toIndex: Int) {
        val currentProducts = _state.value.products.toMutableList()
        if (fromIndex in currentProducts.indices && toIndex in currentProducts.indices) {
            val moved = currentProducts.removeAt(fromIndex)
            currentProducts.add(toIndex, moved)
            val reordered = currentProducts.mapIndexed { index, product ->
                product.copy(displayOrder = index)
            }
            _state.value = _state.value.copy(products = reordered)
            val sid = storeId ?: return
            viewModelScope.launch {
                reordered.forEach { product ->
                    repository.upsertProduct(
                        storeId = sid,
                        product = product
                    )
                }
            }
        }
    }
}
