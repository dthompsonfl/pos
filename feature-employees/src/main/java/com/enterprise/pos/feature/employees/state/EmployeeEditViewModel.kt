package com.enterprise.pos.feature.employees.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import com.enterprise.pos.domain.repository.EmployeeRepository
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

sealed class EmployeeEditEvent {
    data object Saved : EmployeeEditEvent()
    data class Error(val message: String) : EmployeeEditEvent()
    data class ValidationFailed(val errors: Map<String, String>) : EmployeeEditEvent()
}

data class EmployeeEditForm(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: EmployeeRole = EmployeeRole.CASHIER,
    val pin: String = "",
    val permissions: List<String> = emptyList(),
    val hourlyRate: String = "",
    val active: Boolean = true,
    val hireDate: String = "",
    val notes: String = ""
)

data class EmployeeEditState(
    val form: EmployeeEditForm = EmployeeEditForm(),
    val errors: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val employeeId: String? = null,
    val roles: List<EmployeeRole> = EmployeeRole.entries,
    val allPermissions: List<Pair<String, String>> = DEFAULT_PERMISSIONS
)

val DEFAULT_PERMISSIONS = listOf(
    "process_refunds" to "Process Refunds",
    "apply_discounts" to "Apply Discounts",
    "void_orders" to "Void Orders",
    "open_drawer" to "Open Drawer",
    "manage_employees" to "Manage Employees",
    "view_reports" to "View Reports",
    "manage_inventory" to "Manage Inventory",
    "comp_items" to "Comp Items",
    "manage_settings" to "Manage Settings"
)

@HiltViewModel
class EmployeeEditViewModel @Inject constructor(
    private val employeeRepo: EmployeeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmployeeEditState())
    val state: StateFlow<EmployeeEditState> = _state.asStateFlow()

    private val _events = Channel<EmployeeEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun loadEmployee(id: EmployeeId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            employeeRepo.get(id)
                .onSuccess { employee ->
                    employee?.let { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            employeeId = e.id.value,
                            form = EmployeeEditForm(
                                firstName = e.firstName,
                                lastName = e.lastName,
                                email = e.email ?: "",
                                phone = e.phone ?: "",
                                role = e.role,
                                pin = "",
                                permissions = e.customPermissions,
                                hourlyRate = if (e.hourlyRate.isZero()) "" else (e.hourlyRate.minorUnits / 100.0).toString(),
                                active = e.active,
                                hireDate = e.hireDate?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it)) } ?: "",
                                notes = e.notes ?: ""
                            )
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false)
                    _events.send(EmployeeEditEvent.Error(it.message ?: "Failed to load employee"))
                }
        }
    }

    fun updateForm(update: (EmployeeEditForm) -> EmployeeEditForm) {
        _state.value = _state.value.copy(form = update(_state.value.form), errors = emptyMap())
    }

    fun togglePermission(permission: String) {
        val current = _state.value.form.permissions
        val updated = if (current.contains(permission)) {
            current - permission
        } else {
            current + permission
        }
        updateForm { it.copy(permissions = updated) }
    }

    fun save(isAdmin: Boolean) {
        val validationErrors = validate(_state.value.form, isAdmin)
        if (validationErrors.isNotEmpty()) {
            _state.value = _state.value.copy(errors = validationErrors)
            viewModelScope.launch { _events.send(EmployeeEditEvent.ValidationFailed(validationErrors)) }
            return
        }

        val form = _state.value.form
        val employeeId = _state.value.employeeId?.let { EmployeeId(it) } ?: EmployeeId(Id.random<Any>().value)
        val fullName = "${form.firstName} ${form.lastName}".trim()

        viewModelScope.launch {
            val pinHash = if (form.pin.isNotBlank()) {
                PinHasher.hash(form.pin)
            } else {
                val existing = employeeRepo.get(employeeId).getOrNull()
                existing?.pinHash ?: run {
                    _state.value = _state.value.copy(errors = mapOf("pin" to "PIN is required for new employees"))
                    _events.send(EmployeeEditEvent.ValidationFailed(mapOf("pin" to "PIN is required for new employees")))
                    return@launch
                }
            }

            val hourlyRate = form.hourlyRate.toDoubleOrNull()?.let { com.enterprise.pos.core.Money.of(it) } ?: com.enterprise.pos.core.Money.ZERO
            val hireDate = try {
                form.hireDate.takeIf { it.isNotBlank() }?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time
                }
            } catch (_: Exception) { null }

            val employee = Employee(
                id = employeeId,
                name = fullName,
                firstName = form.firstName.trim(),
                lastName = form.lastName.trim(),
                pinHash = pinHash,
                role = form.role,
                active = form.active,
                email = form.email.trim().ifBlank { null },
                phone = form.phone.trim().ifBlank { null },
                hourlyRate = hourlyRate,
                hireDate = hireDate,
                notes = form.notes.trim().ifBlank { null },
                customPermissions = form.permissions
            )

            _state.value = _state.value.copy(isSaving = true)
            employeeRepo.upsert(employee)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false)
                    _events.send(EmployeeEditEvent.Saved)
                }
                .onFailure {
                    _state.value = _state.value.copy(isSaving = false)
                    _events.send(EmployeeEditEvent.Error(it.message ?: "Save failed"))
                }
        }
    }

    private fun validate(form: EmployeeEditForm, isAdmin: Boolean): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.firstName.isBlank()) errors["firstName"] = "First name is required"
        if (form.lastName.isBlank()) errors["lastName"] = "Last name is required"
        if (form.email.isNotBlank() && !form.email.matches(EMAIL_REGEX)) errors["email"] = "Invalid email format"
        if (form.pin.isNotBlank() && !form.pin.all { it.isDigit() }) errors["pin"] = "PIN must be digits only"
        if (form.pin.isNotBlank() && form.pin.length !in 4..6) errors["pin"] = "PIN must be 4-6 digits"
        return errors
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$")
    }
}
