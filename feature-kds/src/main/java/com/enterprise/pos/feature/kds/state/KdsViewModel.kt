package com.enterprise.pos.feature.kds.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KdsUiState(
    val tickets: List<KdsTicket> = emptyList(),
    val stationFilter: String? = null,
    val isLoading: Boolean = true
)

data class KdsTicket(
    val order: Order,
    val elapsedMs: Long,
    val stationItems: Map<String, List<com.enterprise.pos.domain.model.OrderLine>>,
    val isUrgent: Boolean,
    val isCritical: Boolean
)

@HiltViewModel
class KdsViewModel @Inject constructor(
    private val orders: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(KdsUiState())
    val state: StateFlow<KdsUiState> = _state.asStateFlow()

    fun load(storeId: StoreId) {
        viewModelScope.launch {
            orders.observeOpenOrders(storeId).collect { ordersList ->
                val now = SystemClock.now()
                val tickets = ordersList
                    .filter { it.status in setOf(OrderStatus.SENT_TO_KITCHEN, OrderStatus.IN_PREPARATION, OrderStatus.READY) }
                    .filter { it.lines.any { l -> l.lineType == OrderLineType.ITEM && l.kitchenRoutingKey != null && l.sentToKitchen } }
                    .map { order ->
                        val elapsed = now - order.updatedAt
                        val stationItems = order.lines
                            .filter { it.lineType == OrderLineType.ITEM && it.kitchenRoutingKey != null && it.sentToKitchen }
                            .groupBy { it.kitchenRoutingKey!! }
                        KdsTicket(
                            order = order,
                            elapsedMs = elapsed,
                            stationItems = stationItems,
                            isUrgent = elapsed > 10 * 60 * 1000L, // >10 min
                            isCritical = elapsed > 20 * 60 * 1000L // >20 min
                        )
                    }
                    .sortedBy { it.order.updatedAt }
                _state.value = _state.value.copy(tickets = tickets, isLoading = false)
            }
        }
    }

    fun filterStation(station: String?) {
        _state.value = _state.value.copy(stationFilter = station)
    }

    fun markReady(orderId: OrderId) {
        viewModelScope.launch {
            orders.setStatus(orderId, OrderStatus.READY)
        }
    }

    fun markServed(orderId: OrderId) {
        viewModelScope.launch {
            orders.setStatus(orderId, OrderStatus.SERVED)
        }
    }

    fun recall(orderId: OrderId) {
        viewModelScope.launch {
            orders.setStatus(orderId, OrderStatus.SENT_TO_KITCHEN)
        }
    }
}
