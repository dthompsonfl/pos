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

data class ReceiptSettingsUiState(
    val hasLogo: Boolean = false,
    val headerText: String = "",
    val footerText: String = "",
    val showTaxBreakdown: Boolean = true,
    val showEmployeeName: Boolean = true,
    val showStoreInfo: Boolean = true,
    val showBarcode: Boolean = false,
    val printMode: PrintMode = PrintMode.PRINT_ON_REQUEST,
    val autoPrint: Boolean = false,
    val emailReceiptEnabled: Boolean = false,
    val smtpHost: String = "",
    val smtpPort: String = "587",
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

enum class PrintMode { ALWAYS_PRINT, DIGITAL_ONLY, PRINT_ON_REQUEST }

@HiltViewModel
class ReceiptSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptSettingsUiState())
    val state: StateFlow<ReceiptSettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("receipt_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<ReceiptSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            hasLogo = settings.hasLogo,
                            headerText = settings.headerText,
                            footerText = settings.footerText,
                            showTaxBreakdown = settings.showTaxBreakdown,
                            showEmployeeName = settings.showEmployeeName,
                            showStoreInfo = settings.showStoreInfo,
                            showBarcode = settings.showBarcode,
                            printMode = PrintMode.valueOf(settings.printMode),
                            autoPrint = settings.autoPrint,
                            emailReceiptEnabled = settings.emailReceiptEnabled,
                            smtpHost = settings.smtpHost,
                            smtpPort = settings.smtpPort,
                            smtpUsername = settings.smtpUsername,
                            smtpPassword = settings.smtpPassword,
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

    fun updateLogo(hasLogo: Boolean) { _state.value = _state.value.copy(hasLogo = hasLogo) }
    fun updateHeader(text: String) { _state.value = _state.value.copy(headerText = text) }
    fun updateFooter(text: String) { _state.value = _state.value.copy(footerText = text) }
    fun toggleShowTaxBreakdown(show: Boolean) { _state.value = _state.value.copy(showTaxBreakdown = show) }
    fun toggleShowEmployeeName(show: Boolean) { _state.value = _state.value.copy(showEmployeeName = show) }
    fun toggleShowStoreInfo(show: Boolean) { _state.value = _state.value.copy(showStoreInfo = show) }
    fun toggleShowBarcode(show: Boolean) { _state.value = _state.value.copy(showBarcode = show) }
    fun setPrintMode(mode: PrintMode) { _state.value = _state.value.copy(printMode = mode) }
    fun setAutoPrint(auto: Boolean) { _state.value = _state.value.copy(autoPrint = auto) }
    fun setEmailReceiptEnabled(enabled: Boolean) { _state.value = _state.value.copy(emailReceiptEnabled = enabled) }
    fun updateSmtp(host: String, port: String, username: String, password: String) {
        _state.value = _state.value.copy(smtpHost = host, smtpPort = port, smtpUsername = username, smtpPassword = password)
    }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val settings = ReceiptSettings(
                hasLogo = _state.value.hasLogo,
                headerText = _state.value.headerText,
                footerText = _state.value.footerText,
                showTaxBreakdown = _state.value.showTaxBreakdown,
                showEmployeeName = _state.value.showEmployeeName,
                showStoreInfo = _state.value.showStoreInfo,
                showBarcode = _state.value.showBarcode,
                printMode = _state.value.printMode.name,
                autoPrint = _state.value.autoPrint,
                emailReceiptEnabled = _state.value.emailReceiptEnabled,
                smtpHost = _state.value.smtpHost,
                smtpPort = _state.value.smtpPort,
                smtpUsername = _state.value.smtpUsername,
                smtpPassword = _state.value.smtpPassword
            )
            settingsRepo.set("receipt_settings", Json.encodeToString(settings), null).onSuccess {
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}

@kotlinx.serialization.Serializable
private data class ReceiptSettings(
    val hasLogo: Boolean,
    val headerText: String,
    val footerText: String,
    val showTaxBreakdown: Boolean,
    val showEmployeeName: Boolean,
    val showStoreInfo: Boolean,
    val showBarcode: Boolean,
    val printMode: String,
    val autoPrint: Boolean,
    val emailReceiptEnabled: Boolean,
    val smtpHost: String,
    val smtpPort: String,
    val smtpUsername: String,
    val smtpPassword: String
)
