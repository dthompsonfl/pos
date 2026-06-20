package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: CategoryId? = null,
    val products: List<Product> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val storeId: StoreId? = null,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val storeRepo: StoreRepository,
    @Suppress("unused") private val logger: Logger = NoopLogger
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogState(isLoading = true))
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            storeRepo.current().onSuccess { store ->
                store?.let { _state.value = _state.value.copy(storeId = it.id) }
            }
        }
    }

    fun loadCategories() {
        catalog.observeCategories()
            .onEach { cats ->
                val first = cats.firstOrNull()?.id
                _state.value = _state.value.copy(categories = cats, selectedCategory = _state.value.selectedCategory ?: first)
                if ((first != null) && (_state.value.selectedCategory == null)) selectCategory(first)
            }
            .launchIn(viewModelScope)
    }

    fun selectCategory(id: CategoryId) {
        _state.value = _state.value.copy(selectedCategory = id, query = "")
        catalog.observeProducts(id)
            .onEach { products -> _state.value = _state.value.copy(products = products, isLoading = false) }
            .launchIn(viewModelScope)
    }

    fun search(q: String) {
        _state.value = _state.value.copy(query = q)
        if (q.length < 2) {
            _state.value.selectedCategory?.let { selectCategory(it) }
            return
        }
        viewModelScope.launch {
            catalog.search(q).onSuccess { results ->
                _state.value = _state.value.copy(products = results, isLoading = false)
            }
        }
    }

    fun setAvailable(productId: ProductId, available: Boolean) {
        val storeId = _state.value.storeId ?: return
        viewModelScope.launch { catalog.setAvailable(storeId, productId, available) }
    }
}
