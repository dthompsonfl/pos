package com.enterprise.pos.feature.employees.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import com.enterprise.pos.domain.repository.EmployeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RoleEditorEvent {
    data object Saved : RoleEditorEvent()
    data class Error(val message: String) : RoleEditorEvent()
}

data class PermissionGroup(
    val name: String,
    val permissions: List<String>
)

data class RoleEditorState(
    val roles: List<EmployeeRole> = EmployeeRole.entries,
    val selectedRole: EmployeeRole = EmployeeRole.CASHIER,
    val rolePermissions: Map<String, Boolean> = emptyMap(),
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RoleEditorViewModel @Inject constructor(
    private val employeeRepo: EmployeeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoleEditorState())
    val state: StateFlow<RoleEditorState> = _state.asStateFlow()

    private val _events = Channel<RoleEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val permissionGroups = listOf(
        PermissionGroup("Sales", listOf("process_refunds", "apply_discounts", "void_orders", "open_drawer", "comp_items")),
        PermissionGroup("Inventory", listOf("manage_inventory")),
        PermissionGroup("Reports", listOf("view_reports")),
        PermissionGroup("Settings", listOf("manage_settings")),
        PermissionGroup("Admin", listOf("manage_employees"))
    )

    fun selectRole(role: EmployeeRole) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedRole = role, isSaving = true)
            employeeRepo.permissions(role)
                .onSuccess { perms ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        rolePermissions = mapRolePermissions(perms)
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isSaving = false, error = it.message)
                }
        }
    }

    fun togglePermission(permission: String) {
        val current = _state.value.rolePermissions.toMutableMap()
        current[permission] = !(current[permission] ?: false)
        _state.value = _state.value.copy(rolePermissions = current)
    }

    fun saveRole() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            _state.value = _state.value.copy(isSaving = false)
            _events.send(RoleEditorEvent.Saved)
        }
    }

    private fun mapRolePermissions(perms: RolePermissions): Map<String, Boolean> {
        return mapOf(
            "process_refunds" to perms.canProcessRefunds,
            "apply_discounts" to perms.canApplyDiscounts,
            "void_orders" to perms.canVoidOrders,
            "open_drawer" to perms.canOpenDrawer,
            "manage_employees" to perms.canManageEmployees,
            "view_reports" to perms.canViewReports,
            "manage_inventory" to perms.canManageInventory,
            "comp_items" to perms.canCompItems,
            "manage_settings" to false
        )
    }
}
