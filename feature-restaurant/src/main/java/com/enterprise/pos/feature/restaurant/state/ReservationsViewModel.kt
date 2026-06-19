package com.enterprise.pos.feature.restaurant.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.repository.ReservationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReservationsUiState(
    val today: List<Reservation> = emptyList(),
    val upcoming: List<Reservation> = emptyList(),
    val isLoading: Boolean = true,
    val showNewSheet: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val repo: ReservationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReservationsUiState())
    val state: StateFlow<ReservationsUiState> = _state.asStateFlow()

    fun load(storeId: StoreId) {
        repo.observeReservations(storeId, SystemClock.now())
            .onEach { list ->
                _state.value = _state.value.copy(today = list.sortedBy { it.requestedAt }, isLoading = false)
            }
            .launchIn(viewModelScope)
        repo.observeUpcoming(storeId, 24)
            .onEach { list -> _state.value = _state.value.copy(upcoming = list) }
            .launchIn(viewModelScope)
    }

    fun openNewSheet() { _state.value = _state.value.copy(showNewSheet = true) }
    fun closeNewSheet() { _state.value = _state.value.copy(showNewSheet = false) }

    fun createReservation(
        storeId: StoreId, customerName: String, phone: String, partySize: Int,
        requestedAt: Long, notes: String?, customerId: CustomerId?
    ) {
        viewModelScope.launch {
            val r = Reservation(
                id = com.enterprise.pos.core.Id.random(),
                storeId = storeId, customerName = customerName, customerId = customerId,
                phone = phone, email = null, partySize = partySize,
                requestedAt = requestedAt, tableId = null,
                status = ReservationStatus.REQUESTED, notes = notes,
                createdAt = SystemClock.now()
            )
            repo.upsert(r)
            _state.value = _state.value.copy(showNewSheet = false, info = "Reservation created")
        }
    }

    fun confirm(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>) {
        viewModelScope.launch { repo.setStatus(id, ReservationStatus.CONFIRMED) }
    }

    fun seat(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: com.enterprise.pos.core.TableId) {
        viewModelScope.launch { repo.seat(id, tableId) }
    }

    fun cancel(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, reason: String) {
        viewModelScope.launch { repo.cancel(id, reason) }
    }

    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
