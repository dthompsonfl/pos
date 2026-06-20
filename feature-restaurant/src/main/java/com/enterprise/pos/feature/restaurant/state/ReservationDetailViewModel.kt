package com.enterprise.pos.feature.restaurant.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.repository.ReservationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReservationDetailUiState(
    val reservation: Reservation? = null,
    val table: RestaurantTable? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val info: String? = null
)

sealed class ReservationDetailEvent {
    data object NavigateToEdit : ReservationDetailEvent()
    data object Back : ReservationDetailEvent()
}

@HiltViewModel
class ReservationDetailViewModel @Inject constructor(
    private val reservationRepo: ReservationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReservationDetailUiState())
    val state: StateFlow<ReservationDetailUiState> = _state.asStateFlow()

    private val _events = Channel<ReservationDetailEvent>()
    val events = _events.receiveAsFlow()

    fun load(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            reservationRepo.get(reservationId)
                .onSuccess { reservation ->
                    _state.value = _state.value.copy(reservation = reservation)
                    reservation?.tableId?.let { tableId ->
                        // In production, would load table details from OrderRepository
                    }
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun confirm(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>) {
        viewModelScope.launch {
            reservationRepo.setStatus(reservationId, ReservationStatus.CONFIRMED)
                .onSuccess {
                    _state.value = _state.value.copy(
                        reservation = _state.value.reservation?.copy(status = ReservationStatus.CONFIRMED),
                        info = "Reservation confirmed"
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun cancel(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>) {
        viewModelScope.launch {
            reservationRepo.cancel(reservationId, "Cancelled by staff")
                .onSuccess {
                    _state.value = _state.value.copy(
                        reservation = _state.value.reservation?.copy(status = ReservationStatus.CANCELLED),
                        info = "Reservation cancelled"
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun seat(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: TableId) {
        viewModelScope.launch {
            reservationRepo.seat(reservationId, tableId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        reservation = _state.value.reservation?.copy(
                            status = ReservationStatus.SEATED,
                            tableId = tableId
                        ),
                        info = "Guest seated"
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun markNoShow(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>) {
        viewModelScope.launch {
            reservationRepo.setStatus(reservationId, ReservationStatus.NO_SHOW)
                .onSuccess {
                    _state.value = _state.value.copy(
                        reservation = _state.value.reservation?.copy(status = ReservationStatus.NO_SHOW),
                        info = "Marked as no-show"
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message)
                }
        }
    }

    fun edit() {
        viewModelScope.launch {
            _events.send(ReservationDetailEvent.NavigateToEdit)
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(ReservationDetailEvent.Back)
        }
    }

    fun dismissInfo() {
        _state.value = _state.value.copy(info = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
