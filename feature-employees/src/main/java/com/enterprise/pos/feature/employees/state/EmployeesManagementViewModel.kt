package com.enterprise.pos.feature.employees.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmployeesManagementState(
    val employees: List<Employee> = emptyList(),
    val selected: Employee? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class EmployeesManagementViewModel @Inject constructor(
    private val repo: EmployeeRepository,
    private val shifts: ShiftRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmployeesManagementState())
    val state: StateFlow<EmployeesManagementState> = _state.asStateFlow()

    init {
        repo.observeEmployees()
            .onEach { emps -> _state.value = _state.value.copy(employees = emps, isLoading = false) }
            .launchIn(viewModelScope)
    }

    fun select(emp: Employee?) { _state.value = _state.value.copy(selected = emp) }

    fun upsert(name: String, pin: String, role: EmployeeRole, email: String?, phone: String?) {
        viewModelScope.launch {
            val pinHash = if (pin.isNotBlank()) {
                com.enterprise.pos.domain.security.PinHasher.hash(pin)
            } else {
                _state.value.selected?.pinHash ?: throw IllegalArgumentException("PIN required for new employee")
            }
            val emp = (_state.value.selected ?: Employee(
                id = com.enterprise.pos.core.Id.random(),
                name = name, pinHash = pinHash, role = role, email = email, phone = phone
            )).copy(name = name, pinHash = pinHash, role = role, email = email, phone = phone)
            repo.upsert(emp)
            _state.value = _state.value.copy(selected = null)
        }
    }

    fun deactivate(id: EmployeeId) {
        viewModelScope.launch { repo.deactivate(id) }
    }
}
