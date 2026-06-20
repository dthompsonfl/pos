package com.enterprise.pos.feature.restaurant.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
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

data class ReservationForm(
    val guestName: String = "",
    val phone: String = "",
    val email: String = "",
    val partySize: String = "2",
    val date: Long = SystemClock.now(),
    val time: String = "",
    val tableId: com.enterprise.pos.core.TableId? = null,
    val specialRequests: String = "",
    val notes: String = "",
    val status: ReservationStatus = ReservationStatus.REQUESTED
)

data class ReservationEditUiState(
    val reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>? = null,
    val form: ReservationForm = ReservationForm(),
    val tables: List<RestaurantTable> = emptyList(),
    val availableTables: List<RestaurantTable> = emptyList(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

sealed class ReservationEditEvent {
    data object Saved : ReservationEditEvent()
    data object Back : ReservationEditEvent()
}

@HiltViewModel
class ReservationEditViewModel @Inject constructor(
    private val reservationRepo: ReservationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReservationEditUiState())
    val state: StateFlow<ReservationEditUiState> = _state.asStateFlow()

    private val _events = Channel<ReservationEditEvent>()
    val events = _events.receiveAsFlow()

    fun loadNew(storeId: StoreId) {
        _state.value = ReservationEditUiState()
        checkAvailability(storeId)
    }

    fun loadExisting(reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>, storeId: StoreId) {
        _state.value = _state.value.copy(isLoading = true, reservationId = reservationId)
        viewModelScope.launch {
            reservationRepo.get(reservationId)
                .onSuccess { reservation ->
                    if (reservation != null) {
                        _state.value = _state.value.copy(
                            form = ReservationForm(
                                guestName = reservation.customerName,
                                phone = reservation.phone,
                                email = reservation.email ?: "",
                                partySize = reservation.partySize.toString(),
                                date = reservation.requestedAt,
                                time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(reservation.requestedAt)),
                                tableId = reservation.tableId,
                                specialRequests = reservation.dietaryRestrictions.joinToString(", "),
                                notes = reservation.notes ?: "",
                                status = reservation.status
                            ),
                            isLoading = false
                        )
                    } else {
                        _state.value = _state.value.copy(error = "Reservation not found", isLoading = false)
                    }
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isLoading = false)
                }
        }
        checkAvailability(storeId)
    }

    private fun checkAvailability(storeId: StoreId) {
        val form = _state.value.form
        val partySize = form.partySize.toIntOrNull() ?: 0
        if (partySize > 0) {
            viewModelScope.launch {
                reservationRepo.checkTableAvailability(storeId, form.date, partySize)
                    .onSuccess { tables ->
                        _state.value = _state.value.copy(availableTables = tables)
                    }
                    .onFailure {
                        // Silently fail for availability check
                    }
            }
        }
    }

    fun setGuestName(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(guestName = value))
    }

    fun setPhone(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(phone = value))
    }

    fun setEmail(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(email = value))
    }

    fun setPartySize(value: String) {
        val digits = value.filter { it.isDigit() }
        _state.value = _state.value.copy(form = _state.value.form.copy(partySize = digits))
    }

    fun setDate(date: Long) {
        _state.value = _state.value.copy(form = _state.value.form.copy(date = date))
    }

    fun setTime(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(time = value))
    }

    fun setTable(table: RestaurantTable?) {
        _state.value = _state.value.copy(form = _state.value.form.copy(tableId = table?.id))
    }

    fun setSpecialRequests(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(specialRequests = value))
    }

    fun setNotes(value: String) {
        _state.value = _state.value.copy(form = _state.value.form.copy(notes = value))
    }

    fun setStatus(status: ReservationStatus) {
        _state.value = _state.value.copy(form = _state.value.form.copy(status = status))
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        val form = _state.value.form
        if (form.guestName.isBlank()) {
            errors["name"] = "Guest name is required"
        }
        if (form.phone.isBlank()) {
            errors["phone"] = "Phone number is required"
        } else if (!form.phone.matches(Regex("^\\+?[0-9\\s-]{7,}$"))) {
            errors["phone"] = "Invalid phone number"
        }
        val partySize = form.partySize.toIntOrNull()
        if (partySize == null || partySize <= 0) {
            errors["partySize"] = "Party size must be greater than 0"
        }
        if (form.time.isBlank()) {
            errors["time"] = "Time is required"
        }
        if (form.date <= SystemClock.now()) {
            errors["date"] = "Reservation must be in the future"
        }
        _state.value = _state.value.copy(validationErrors = errors)
        return errors.isEmpty()
    }

    fun save(storeId: StoreId) {
        if (!validate()) return
        val currentState = _state.value
        val form = currentState.form

        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = form.date
        val timeParts = form.time.split(":")
        if (timeParts.size == 2) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
            calendar.set(java.util.Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
        }
        val requestedAt = calendar.timeInMillis

        val reservation = Reservation(
            id = currentState.reservationId ?: Id.random(),
            storeId = storeId,
            customerName = form.guestName.trim(),
            phone = form.phone.trim(),
            email = form.email.ifBlank { null },
            partySize = form.partySize.toIntOrNull() ?: 2,
            requestedAt = requestedAt,
            tableId = form.tableId,
            status = form.status,
            notes = form.notes.ifBlank { null },
            dietaryRestrictions = form.specialRequests.split(",").map { it.trim() }.filter { it.isNotBlank() },
            createdAt = SystemClock.now()
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            reservationRepo.upsert(reservation)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        result = "Reservation saved",
                        reservationId = it.id
                    )
                    _events.send(ReservationEditEvent.Saved)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(error = err.message, isSaving = false)
                }
        }
    }

    fun dismissResult() {
        _state.value = _state.value.copy(result = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(ReservationEditEvent.Back)
        }
    }
}
