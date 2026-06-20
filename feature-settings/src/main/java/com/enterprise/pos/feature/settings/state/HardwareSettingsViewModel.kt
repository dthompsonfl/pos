package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HardwareSettingsUiState(
    val discoveredDevices: List<HardwareDevice> = emptyList(),
    val connectedDevices: List<HardwareDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val showManualAddDialog: Boolean = false
)

data class HardwareDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val connectionType: String,
    val address: String,
    val port: Int = 0,
    val protocol: String = "",
    val isConnected: Boolean = false,
    val settings: DeviceSettings = DeviceSettings()
)

data class DeviceSettings(
    val paperWidth: Int = 80,
    val beepEnabled: Boolean = true,
    val baudRate: Int = 9600
)

enum class DeviceType { PRINTER, SCANNER, CASH_DRAWER, CUSTOMER_DISPLAY }

@HiltViewModel
class HardwareSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HardwareSettingsUiState())
    val state: StateFlow<HardwareSettingsUiState> = _state.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("hardware_devices").onSuccess { json ->
                try {
                    val devices = json?.let { Json.decodeFromString<List<HardwareDevice>>(it) } ?: emptyList()
                    _state.value = _state.value.copy(
                        connectedDevices = devices.filter { it.isConnected },
                        isLoading = false
                    )
                } catch (_: Exception) {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun scanDevices() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, error = null)
            // In production: invoke hardware discovery service
            kotlinx.coroutines.delay(1500)
            val simulated = listOf(
                HardwareDevice("sim1", "Star TSP100", DeviceType.PRINTER, "BLUETOOTH", "00:11:22:33:44:55"),
                HardwareDevice("sim2", "Honeywell Voyager", DeviceType.SCANNER, "USB", "usb-1"),
                HardwareDevice("sim3", "APG Cash Drawer", DeviceType.CASH_DRAWER, "USB", "usb-2")
            )
            _state.value = _state.value.copy(discoveredDevices = simulated, isScanning = false)
        }
    }

    fun connect(device: HardwareDevice) {
        val updated = device.copy(isConnected = true)
        val connected = _state.value.connectedDevices.toMutableList().apply { add(updated) }
        val discovered = _state.value.discoveredDevices.filter { it.id != device.id }
        _state.value = _state.value.copy(connectedDevices = connected, discoveredDevices = discovered)
    }

    fun disconnect(device: HardwareDevice) {
        val connected = _state.value.connectedDevices.filter { it.id != device.id }
        val discovered = _state.value.discoveredDevices.toMutableList().apply { add(device.copy(isConnected = false)) }
        _state.value = _state.value.copy(connectedDevices = connected, discoveredDevices = discovered)
    }

    fun updateDeviceSettings(deviceId: String, settings: DeviceSettings) {
        val connected = _state.value.connectedDevices.map {
            if (it.id == deviceId) it.copy(settings = settings) else it
        }
        _state.value = _state.value.copy(connectedDevices = connected)
    }

    fun addManualDevice(name: String, type: DeviceType, address: String, port: Int, protocol: String) {
        val device = HardwareDevice(
            id = "manual-${System.currentTimeMillis()}",
            name = name,
            type = type,
            connectionType = "MANUAL",
            address = address,
            port = port,
            protocol = protocol,
            isConnected = true
        )
        val connected = _state.value.connectedDevices.toMutableList().apply { add(device) }
        _state.value = _state.value.copy(connectedDevices = connected, showManualAddDialog = false)
    }

    fun testDevice(device: HardwareDevice) {
        // In production: dispatch test command to hardware manager
    }

    fun openManualAddDialog() { _state.value = _state.value.copy(showManualAddDialog = true) }
    fun closeManualAddDialog() { _state.value = _state.value.copy(showManualAddDialog = false) }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val all = _state.value.connectedDevices + _state.value.discoveredDevices
            settingsRepo.set("hardware_devices", Json.encodeToString(all), null).onSuccess {
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
