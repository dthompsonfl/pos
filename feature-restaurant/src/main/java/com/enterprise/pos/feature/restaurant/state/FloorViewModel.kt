package com.enterprise.pos.feature.restaurant.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.usecase.SeatTableUseCase
import com.enterprise.pos.domain.usecase.StartTakeoutOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FloorViewModel @Inject constructor(
    private val orders: OrderRepository,
    private val seatTable: SeatTableUseCase,
    private val startTakeoutUseCase: StartTakeoutOrderUseCase,
    private val logger: Logger = NoopLogger
) : ViewModel() {

    private val _state = MutableStateFlow(FloorState(isLoading = true))
    val state: StateFlow<FloorState> = _state.asStateFlow()

    private val _startState = MutableStateFlow(StartOrderState())
    val startState: StateFlow<StartOrderState> = _startState.asStateFlow()

    fun loadTables(storeId: StoreId) {
        orders.observeTables(storeId)
            .onEach { tables ->
                _state.value = _state.value.copy(
                    tables = tables.filter { t -> _state.value.sectionFilter?.let { t.section == it } ?: true },
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    fun selectSection(section: String?) {
        _state.value = _state.value.copy(sectionFilter = section)
    }

    fun selectTable(table: RestaurantTable) {
        _state.value = _state.value.copy(selectedTable = table)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedTable = null)
    }

    fun startDineInHostSeated(
        table: RestaurantTable,
        serverId: EmployeeId,
        storeId: StoreId,
        registerId: RegisterId,
        guestCount: Int
    ) {
        viewModelScope.launch {
            seatTable(table.id, serverId, storeId, registerId, guestCount)
                .onSuccess { order ->
                    _startState.value = _startState.value.copy(order = order, diningMode = DiningMode.DINE_IN_HOST_SEATED)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun startDineInSelfSeated(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId
    ) {
        viewModelScope.launch {
            orders.createOrder(storeId, registerId, employeeId, DiningMode.DINE_IN_SELF_SEATED)
                .onSuccess { order ->
                    _startState.value = _startState.value.copy(order = order, diningMode = DiningMode.DINE_IN_SELF_SEATED)
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun startTakeout(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId,
        customerId: CustomerId? = null
    ) {
        viewModelScope.launch {
            startTakeoutUseCase(storeId, registerId, employeeId, customerId)
                .onSuccess { order ->
                    _startState.value = _startState.value.copy(order = order, diningMode = DiningMode.TO_GO)
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun setGuestCount(count: Int) {
        _startState.value = _startState.value.copy(guestCount = count.coerceAtLeast(1))
    }

    fun setTable(tableId: TableId?) {
        _startState.value = _startState.value.copy(selectedTableId = tableId)
    }

    fun setServer(serverId: EmployeeId?) {
        _startState.value = _startState.value.copy(selectedServerId = serverId)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
