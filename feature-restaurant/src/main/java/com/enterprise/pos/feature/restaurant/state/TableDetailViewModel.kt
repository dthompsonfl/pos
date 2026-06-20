package com.enterprise.pos.feature.restaurant.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.ReservationRepository
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

data class TableDetailUiState(
    val table: RestaurantTable? = null,
    val currentOrder: Order? = null,
    val reservations: List<Reservation> = emptyList(),
    val history: List<Order> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCombineDialog: Boolean = false,
    val showSplitDialog: Boolean = false,
    val showServerDialog: Boolean = false,
    val showStatusDialog: Boolean = false
)

sealed class TableDetailEvent {
    data class NavigateToOrder(val orderId: OrderId) : TableDetailEvent()
    data object Back : TableDetailEvent()
}

@HiltViewModel
class TableDetailViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val reservationRepo: ReservationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TableDetailUiState())
    val state: StateFlow<TableDetailUiState> = _state.asStateFlow()

    private val _events = Channel<TableDetailEvent>()
    val events = _events.receiveAsFlow()

    fun load(tableId: TableId, storeId: StoreId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            orderRepo.observeTables(storeId)
                .onEach { tables ->
                    val table = tables.find { it.id == tableId }
                    _state.value = _state.value.copy(table = table)
                    table?.currentOrderId?.let { orderId ->
                        orderRepo.observeOrder(orderId)
                            .onEach { order ->
                                _state.value = _state.value.copy(currentOrder = order)
                            }
                            .launchIn(viewModelScope)
                    }
                }
                .launchIn(viewModelScope)

            reservationRepo.observeReservations(storeId, System.currentTimeMillis())
                .onEach { reservations ->
                    _state.value = _state.value.copy(
                        reservations = reservations.filter { it.tableId == tableId },
                        isLoading = false
                    )
                }
                .launchIn(viewModelScope)
        }
    }

    fun changeStatus(tableId: TableId, status: TableStatus) {
        viewModelScope.launch {
            orderRepo.setTableStatus(tableId, status)
                .onSuccess {
                    _state.value = _state.value.copy(
                        showStatusDialog = false,
                        table = _state.value.table?.copy(status = status)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, showStatusDialog = false)
                }
        }
    }

    fun assignServer(tableId: TableId, serverId: EmployeeId?) {
        viewModelScope.launch {
            orderRepo.assignServer(tableId, serverId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        showServerDialog = false,
                        table = _state.value.table?.copy(serverId = serverId)
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, showServerDialog = false)
                }
        }
    }

    fun openCombineDialog() {
        _state.value = _state.value.copy(showCombineDialog = true)
    }

    fun closeCombineDialog() {
        _state.value = _state.value.copy(showCombineDialog = false)
    }

    fun openSplitDialog() {
        _state.value = _state.value.copy(showSplitDialog = true)
    }

    fun closeSplitDialog() {
        _state.value = _state.value.copy(showSplitDialog = false)
    }

    fun openServerDialog() {
        _state.value = _state.value.copy(showServerDialog = true)
    }

    fun closeServerDialog() {
        _state.value = _state.value.copy(showServerDialog = false)
    }

    fun openStatusDialog() {
        _state.value = _state.value.copy(showStatusDialog = true)
    }

    fun closeStatusDialog() {
        _state.value = _state.value.copy(showStatusDialog = false)
    }

    fun viewOrder(orderId: OrderId) {
        viewModelScope.launch {
            _events.send(TableDetailEvent.NavigateToOrder(orderId))
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(TableDetailEvent.Back)
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
