package com.enterprise.pos.feature.employees.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.domain.repository.EmployeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmployeesState(
    val employees: List<com.enterprise.pos.domain.model.Employee> = emptyList(),
    val currentEmployee: com.enterprise.pos.domain.model.Employee? = null,
    val pin: String = "",
    val loginError: String? = null,
    val isLoggingIn: Boolean = false
)

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val repo: EmployeeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(EmployeesState())
    val state: StateFlow<EmployeesState> = _state.asStateFlow()

    init {
        repo.observeEmployees()
            .onEach { list -> _state.value = _state.value.copy(employees = list) }
            .launchIn(viewModelScope)
    }

    fun typePin(digit: String) {
        if (_state.value.pin.length >= 6) return
        _state.value = _state.value.copy(pin = _state.value.pin + digit, loginError = null)
    }

    fun clearPin() {
        _state.value = _state.value.copy(pin = "", loginError = null)
    }

    fun lockRegister() {
        _state.value = _state.value.copy(
            currentEmployee = null,
            pin = "",
            loginError = null,
            isLoggingIn = false
        )
    }

    fun login(onSuccess: (com.enterprise.pos.domain.model.Employee) -> Unit) {
        val pin = _state.value.pin
        if (pin.length < 4) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoggingIn = true)
            repo.login(pin)
                .onSuccess { emp ->
                    _state.value = _state.value.copy(currentEmployee = emp, isLoggingIn = false, pin = "")
                    onSuccess(emp)
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoggingIn = false, loginError = it.message ?: "Login failed", pin = "")
                }
        }
    }
}
