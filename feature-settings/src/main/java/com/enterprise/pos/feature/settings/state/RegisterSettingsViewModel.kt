package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Register
import com.enterprise.pos.domain.repository.SettingsRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterSettingsUiState(
    val register: Register? = null,
    val printerName: String = "",
    val drawerName: String = "",
    val displayName: String = "",
    val scannerName: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RegisterSettingsViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterSettingsUiState())
    val state: StateFlow<RegisterSettingsUiState> = _state.asStateFlow()

    init {
        loadRegister()
    }

    private fun loadRegister() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            storeRepo.current().onSuccess { store ->
                storeRepo.registers(store.id).onSuccess { registers ->
                    val reg = registers.firstOrNull { it.active } ?: registers.firstOrNull()
                    _state.value = _state.value.copy(register = reg, isLoading = false)
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false)
            }
            settingsRepo.get("register_hardware_printer").onSuccess { value ->
                if (value != null) _state.value = _state.value.copy(printerName = value)
            }
            settingsRepo.get("register_hardware_drawer").onSuccess { value ->
                if (value != null) _state.value = _state.value.copy(drawerName = value)
            }
            settingsRepo.get("register_hardware_display").onSuccess { value ->
                if (value != null) _state.value = _state.value.copy(displayName = value)
            }
            settingsRepo.get("register_hardware_scanner").onSuccess { value ->
                if (value != null) _state.value = _state.value.copy(scannerName = value)
            }
        }
    }

    fun updateRegister(
        name: String = _state.value.register?.name ?: "",
        deviceId: String = _state.value.register?.deviceIdentifier ?: "",
        active: Boolean = _state.value.register?.active ?: true
    ) {
        val current = _state.value.register
        if (current != null) {
            _state.value = _state.value.copy(
                register = current.copy(name = name, deviceIdentifier = deviceId, active = active)
            )
        } else {
            viewModelScope.launch {
                val storeId = try {
                    storeRepo.current().getOrNull()?.id ?: StoreId("default")
                } catch (_: Exception) { StoreId("default") }
                _state.value = _state.value.copy(
                    register = Register(
                        id = RegisterId(com.enterprise.pos.core.Id.random<Any>().value),
                        storeId = storeId,
                        name = name,
                        deviceIdentifier = deviceId,
                        active = active
                    )
                )
            }
        }
    }

    fun updateHardwareAssignments(
        printer: String = _state.value.printerName,
        drawer: String = _state.value.drawerName,
        display: String = _state.value.displayName,
        scanner: String = _state.value.scannerName
    ) {
        _state.value = _state.value.copy(
            printerName = printer,
            drawerName = drawer,
            displayName = display,
            scannerName = scanner
        )
    }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val register = _state.value.register
            if (register == null || register.name.isBlank()) {
                _state.value = _state.value.copy(isSaving = false, error = "Register name is required")
                return@launch
            }
            storeRepo.upsertRegister(register).onSuccess {
                settingsRepo.set("register_hardware_printer", _state.value.printerName, null)
                settingsRepo.set("register_hardware_drawer", _state.value.drawerName, null)
                settingsRepo.set("register_hardware_display", _state.value.displayName, null)
                settingsRepo.set("register_hardware_scanner", _state.value.scannerName, null)
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    @Suppress("EmptyFunctionBlock") // Hardware test dispatch stub
    fun testHardware(type: HardwareType) {
        // In production: dispatch to hardware manager
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}

enum class HardwareType { PRINTER, DRAWER, DISPLAY, SCANNER }
