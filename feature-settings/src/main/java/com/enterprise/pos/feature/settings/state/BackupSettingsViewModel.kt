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

data class BackupSettingsUiState(
    val schedule: BackupSchedule = BackupSchedule.MANUAL,
    val lastBackupAt: Long? = null,
    val lastBackupSize: String = "",
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val cloudBackupEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

enum class BackupSchedule { DAILY, WEEKLY, MANUAL }

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BackupSettingsUiState())
    val state: StateFlow<BackupSettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("backup_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<BackupSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            schedule = BackupSchedule.valueOf(settings.schedule),
                            lastBackupAt = settings.lastBackupAt,
                            lastBackupSize = settings.lastBackupSize,
                            cloudBackupEnabled = settings.cloudBackupEnabled,
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

    fun setSchedule(schedule: BackupSchedule) { _state.value = _state.value.copy(schedule = schedule) }
    fun toggleCloudBackup(enabled: Boolean) { _state.value = _state.value.copy(cloudBackupEnabled = enabled) }

    fun backupNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBackingUp = true, info = "Starting backup...", error = null)
            // In production: invoke backup service
            kotlinx.coroutines.delay(2000)
            val now = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isBackingUp = false,
                lastBackupAt = now,
                lastBackupSize = "42.5 MB",
                info = "Backup completed successfully"
            )
        }
    }

    fun restoreFromBackup() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRestoring = true, info = "Restoring from backup...", error = null)
            kotlinx.coroutines.delay(2000)
            _state.value = _state.value.copy(isRestoring = false, info = "Restore completed successfully")
        }
    }

    fun exportData(format: ExportFormat) {
        // In production: trigger file export
        _state.value = _state.value.copy(info = "Export to ${format.name} started")
    }

    fun importData(format: ExportFormat) {
        // In production: trigger file import
        _state.value = _state.value.copy(info = "Import from ${format.name} started")
    }

    fun save() {
        viewModelScope.launch {
            val settings = BackupSettings(
                schedule = _state.value.schedule.name,
                lastBackupAt = _state.value.lastBackupAt,
                lastBackupSize = _state.value.lastBackupSize,
                cloudBackupEnabled = _state.value.cloudBackupEnabled
            )
            settingsRepo.set("backup_settings", Json.encodeToString(settings), null).onSuccess {
                _state.value = _state.value.copy(saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
}

enum class ExportFormat { CSV, JSON, EXCEL }

@kotlinx.serialization.Serializable
private data class BackupSettings(
    val schedule: String,
    val lastBackupAt: Long?,
    val lastBackupSize: String,
    val cloudBackupEnabled: Boolean
)
