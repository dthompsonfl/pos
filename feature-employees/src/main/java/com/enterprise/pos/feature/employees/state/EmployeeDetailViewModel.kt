package com.enterprise.pos.feature.employees.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.Shift
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.ShiftRepository
import com.enterprise.pos.domain.security.PinHasher
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

sealed class EmployeeDetailEvent {
    data object Deactivated : EmployeeDetailEvent()
    data object PinReset : EmployeeDetailEvent()
    data class Error(val message: String) : EmployeeDetailEvent()
}

data class EmployeeStats(
    val totalSales: com.enterprise.pos.core.Money = com.enterprise.pos.core.Money.ZERO,
    val orderCount: Int = 0,
    val averageOrderValue: com.enterprise.pos.core.Money = com.enterprise.pos.core.Money.ZERO,
    val hoursWorked: Double = 0.0
)

data class EmployeeDetailState(
    val employee: Employee? = null,
    val shifts: List<Shift> = emptyList(),
    val stats: EmployeeStats = EmployeeStats(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EmployeeDetailViewModel @Inject constructor(
    private val employeeRepo: EmployeeRepository,
    private val shiftRepo: ShiftRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmployeeDetailState())
    val state: StateFlow<EmployeeDetailState> = _state.asStateFlow()

    private val _events = Channel<EmployeeDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(employeeId: EmployeeId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            employeeRepo.observeEmployee(employeeId)
                .onEach { emp ->
                    _state.value = _state.value.copy(employee = emp, isLoading = false)
                }
                .launchIn(viewModelScope)

            shiftRepo.currentForEmployee(employeeId)
                .onSuccess { shift ->
                    shift?.let {
                        _state.value = _state.value.copy(shifts = listOf(it))
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(error = it.message)
                }
        }
    }

    fun deactivate(employeeId: EmployeeId) {
        viewModelScope.launch {
            employeeRepo.deactivate(employeeId)
                .onSuccess { _events.send(EmployeeDetailEvent.Deactivated) }
                .onFailure { _events.send(EmployeeDetailEvent.Error(it.message ?: "Deactivate failed")) }
        }
    }

    fun resetPin(employeeId: EmployeeId, newPin: String) {
        viewModelScope.launch {
            if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
                _events.send(EmployeeDetailEvent.Error("PIN must be 4-6 digits"))
                return@launch
            }
            employeeRepo.resetPin(employeeId, newPin)
                .onSuccess { _events.send(EmployeeDetailEvent.PinReset) }
                .onFailure { _events.send(EmployeeDetailEvent.Error(it.message ?: "PIN reset failed")) }
        }
    }
}
