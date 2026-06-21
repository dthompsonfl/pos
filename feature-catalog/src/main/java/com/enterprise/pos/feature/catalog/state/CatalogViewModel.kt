package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.StoreRepository
import com.enterprise.pos.domain.usecase.AddItemToOrderUseCase
import com.enterprise.pos.domain.usecase.CreateOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val activeOrderId: OrderId? = null,
    val pendingCartOrderId: OrderId? = null,
    val isAddingProduct: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val storeRepo: StoreRepository,
    private val orders: OrderRepository,
    private val createOrder: CreateOrderUseCase,
    private val addItemToOrder: AddItemToOrderUseCase,
    @Suppress("unused") private val logger: Logger = NoopLogger
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogState(isLoading = true))
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var openOrdersJob: Job? = null

    init {
        viewModelScope.launch {
            storeRepo.current()
                .onSuccess { store ->
                    _state.value = _state.value.copy(storeId = store.id)
                    observeRetailOrder(store.id)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isLoading = false, error = error.message)
                }
        }
    }

    fun loadCategories() {
        catalog.observeCategories()
            .onEach { cats ->
                val first = cats.firstOrNull()?.id
                val selected = _state.value.selectedCategory ?: first
                _state.value = _state.value.copy(categories = cats, selectedCategory = selected)
                if ((first != null) && (_state.value.products.isEmpty())) selectCategory(selected ?: first)
            }
            .launchIn(viewModelScope)
    }

    fun selectCategory(id: CategoryId) {
        _state.value = _state.value.copy(selectedCategory = id, query = "", isLoading = true)
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
            _state.value = _state.value.copy(isLoading = true)
            catalog.search(q)
                .onSuccess { results ->
                    _state.value = _state.value.copy(products = results, isLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isLoading = false, error = error.message)
                }
        }
    }

    fun addProductToActiveCart(
        productId: ProductId,
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId
    ) {
        if (_state.value.isAddingProduct) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isAddingProduct = true,
                pendingCartOrderId = null,
                message = null,
                error = null
            )

            runCatching {
                val order = resolveEditableRetailOrder(storeId, registerId, employeeId)
                addItemToOrder(order, productId).getOrThrow()
            }.onSuccess { updatedOrder ->
                val addedName = updatedOrder.lines.lastOrNull()?.name ?: "item"
                _state.value = _state.value.copy(
                    activeOrderId = updatedOrder.id,
                    pendingCartOrderId = updatedOrder.id,
                    isAddingProduct = false,
                    message = "Added $addedName"
                )
            }.onFailure { throwable ->
                logger.e(TAG, "Failed to add product to active cart", throwable)
                _state.value = _state.value.copy(
                    isAddingProduct = false,
                    error = throwable.message ?: "Unable to add item"
                )
            }
        }
    }

    fun consumeCartNavigation() {
        _state.value = _state.value.copy(pendingCartOrderId = null)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun setAvailable(productId: ProductId, available: Boolean) {
        val storeId = _state.value.storeId ?: return
        viewModelScope.launch { catalog.setAvailable(storeId, productId, available) }
    }

    private suspend fun resolveEditableRetailOrder(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId
    ): Order {
        val activeOrder = _state.value.activeOrderId
            ?.let { orders.getById(it).getOrNull() }
            ?.takeIf { it.isEditableRetailOrder(storeId, registerId) }

        if (activeOrder != null) return activeOrder

        return createOrder(
            storeId = storeId,
            registerId = registerId,
            employeeId = employeeId,
            diningMode = DiningMode.RETAIL
        ).getOrThrow()
    }

    private fun observeRetailOrder(storeId: StoreId) {
        openOrdersJob?.cancel()
        openOrdersJob = orders.observeOpenOrders(storeId)
            .onEach { openOrders ->
                val current = _state.value.activeOrderId
                val currentStillOpen = openOrders.any { order ->
                    order.id == current && order.isEditableRetailOrder(storeId, order.registerId)
                }
                if (currentStillOpen) return@onEach

                val latestRetailOrder = openOrders
                    .filter { order ->
                        order.diningMode == DiningMode.RETAIL && order.status in editableCartStatuses
                    }
                    .maxByOrNull { it.updatedAt }
                    ?.id

                if (_state.value.activeOrderId != latestRetailOrder) {
                    _state.value = _state.value.copy(activeOrderId = latestRetailOrder)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun Order.isEditableRetailOrder(storeId: StoreId, registerId: RegisterId): Boolean =
        this.storeId == storeId &&
            this.registerId == registerId &&
            diningMode == DiningMode.RETAIL &&
            status in editableCartStatuses

    companion object {
        private const val TAG = "CatalogViewModel"
        private val editableCartStatuses = setOf(OrderStatus.DRAFT, OrderStatus.OPEN)
    }
}
