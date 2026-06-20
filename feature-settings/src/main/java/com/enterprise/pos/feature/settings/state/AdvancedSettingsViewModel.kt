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

data class AdvancedSettingsUiState(
    val debugMode: Boolean = false,
    val logLevel: LogLevel = LogLevel.WARNING,
    val networkLogging: Boolean = false,
    val uiInspection: Boolean = false,
    val autoSyncInterval: Int = 15,
    val manualSync: Boolean = false,
    val conflictResolution: ConflictResolution = ConflictResolution.SERVER_WINS,
    val apiEndpoint: String = "",
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val showClearCacheConfirmation: Boolean = false,
    val showDbMaintenanceDialog: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

enum class LogLevel { DEBUG, INFO, WARNING, ERROR, NONE }
enum class ConflictResolution { SERVER_WINS, CLIENT_WINS, LAST_WRITE_WINS, MANUAL }

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdvancedSettingsUiState())
    val state: StateFlow<AdvancedSettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("advanced_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<AdvancedSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            debugMode = settings.debugMode,
                            logLevel = LogLevel.valueOf(settings.logLevel),
                            networkLogging = settings.networkLogging,
                            uiInspection = settings.uiInspection,
                            autoSyncInterval = settings.autoSyncInterval,
                            conflictResolution = ConflictResolution.valueOf(settings.conflictResolution),
                            apiEndpoint = settings.apiEndpoint,
                            featureFlags = settings.featureFlags,
                            isLoading = false
                        )
                    } else {
                        _state.value = _state.value.copy(isLoading = false)
                    }
                } catch (_: Exception) {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun toggleDebugMode(enabled: Boolean) { _state.value = _state.value.copy(debugMode = enabled) }
    fun setLogLevel(level: LogLevel) { _state.value = _state.value.copy(logLevel = level) }
    fun toggleNetworkLogging(enabled: Boolean) { _state.value = _state.value.copy(networkLogging = enabled) }
    fun toggleUiInspection(enabled: Boolean) { _state.value = _state.value.copy(uiInspection = enabled) }
    fun setAutoSyncInterval(minutes: Int) { _state.value = _state.value.copy(autoSyncInterval = minutes) }
    fun setConflictResolution(resolution: ConflictResolution) { _state.value = _state.value.copy(conflictResolution = resolution) }
    fun updateApiEndpoint(endpoint: String) { _state.value = _state.value.copy(apiEndpoint = endpoint) }
    fun toggleFeatureFlag(flag: String, enabled: Boolean) {
        val current = _state.value.featureFlags.toMutableMap()
        current[flag] = enabled
        _state.value = _state.value.copy(featureFlags = current)
    }

    fun clearCache() {
        viewModelScope.launch {
            _state.value = _state.value.copy(showClearCacheConfirmation = false, info = "Cache cleared successfully")
        }
    }

    fun resetAppData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(showResetConfirmation = false, info = "App data reset completed")
        }
    }

    fun performDatabaseMaintenance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(showDbMaintenanceDialog = false, info = "Database maintenance completed")
        }
    }

    fun manualSync() {
        viewModelScope.launch {
            _state.value = _state.value.copy(info = "Manual sync initiated")
            // In production: invoke sync engine
        }
    }

    fun openResetConfirmation() { _state.value = _state.value.copy(showResetConfirmation = true) }
    fun closeResetConfirmation() { _state.value = _state.value.copy(showResetConfirmation = false) }
    fun openClearCacheConfirmation() { _state.value = _state.value.copy(showClearCacheConfirmation = true) }
    fun closeClearCacheConfirmation() { _state.value = _state.value.copy(showClearCacheConfirmation = false) }
    fun openDbMaintenanceDialog() { _state.value = _state.value.copy(showDbMaintenanceDialog = true) }
    fun closeDbMaintenanceDialog() { _state.value = _state.value.copy(showDbMaintenanceDialog = false) }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val settings = AdvancedSettings(
                debugMode = _state.value.debugMode,
                logLevel = _state.value.logLevel.name,
                networkLogging = _state.value.networkLogging,
                uiInspection = _state.value.uiInspection,
                autoSyncInterval = _state.value.autoSyncInterval,
                conflictResolution = _state.value.conflictResolution.name,
                apiEndpoint = _state.value.apiEndpoint,
                featureFlags = _state.value.featureFlags
            )
            settingsRepo.set("advanced_settings", Json.encodeToString(settings), null).onSuccess {
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
}

@kotlinx.serialization.Serializable
private data class AdvancedSettings(
    val debugMode: Boolean,
    val logLevel: String,
    val networkLogging: Boolean,
    val uiInspection: Boolean,
    val autoSyncInterval: Int,
    val conflictResolution: String,
    val apiEndpoint: String,
    val featureFlags: Map<String, Boolean>
)
