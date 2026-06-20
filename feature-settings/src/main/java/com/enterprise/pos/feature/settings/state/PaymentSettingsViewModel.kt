package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.domain.repository.SettingsRepository
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.ProviderConfig
import com.enterprise.pos.payment.model.ProviderEnvironment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class PaymentSettingsUiState(
    val providers: Map<PaymentProviderId, ProviderConfig> = emptyMap(),
    val activeProviders: Set<PaymentProviderId> = emptySet(),
    val defaultProvider: PaymentProviderId = PaymentProviderId.STRIPE,
    val cashDrawerAutoOpen: Boolean = true,
    val cashDrawerCloseConfirm: Boolean = false,
    val tipPercentages: List<Int> = listOf(15, 18, 20, 25),
    val tipFixedAmounts: List<String> = emptyList(),
    val tipCustomEnabled: Boolean = true,
    val tipEntryMode: TipEntryMode = TipEntryMode.ON_SCREEN,
    val refundPolicy: RefundPolicy = RefundPolicy.ORIGINAL_METHOD,
    val partialRefundMinimum: String = "0.00",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val editingProvider: PaymentProviderId? = null,
    val showProviderDialog: Boolean = false
)

enum class TipEntryMode { ON_READER, ON_SCREEN, BOTH }
enum class RefundPolicy { ORIGINAL_METHOD, STORE_CREDIT_ALLOWED, NO_RESTRICTIONS }

@HiltViewModel
class PaymentSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentSettingsUiState())
    val state: StateFlow<PaymentSettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("payment_providers_config").onSuccess { json ->
                try {
                    val map = json?.let { Json.decodeFromString<Map<PaymentProviderId, ProviderConfig>>(it) } ?: emptyMap()
                    _state.value = _state.value.copy(providers = map)
                } catch (_: Exception) { }
            }
            settingsRepo.get("payment_active_providers").onSuccess { json ->
                try {
                    val list = json?.let { Json.decodeFromString<List<PaymentProviderId>>(it) } ?: emptyList()
                    _state.value = _state.value.copy(activeProviders = list.toSet())
                } catch (_: Exception) { }
            }
            settingsRepo.get("payment_default_provider").onSuccess { json ->
                json?.let { _state.value = _state.value.copy(defaultProvider = PaymentProviderId.valueOf(it)) }
            }
            settingsRepo.get("cash_drawer_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<CashDrawerSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            cashDrawerAutoOpen = settings.autoOpen,
                            cashDrawerCloseConfirm = settings.closeConfirm
                        )
                    }
                } catch (_: Exception) { }
            }
            settingsRepo.get("tip_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<TipSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            tipPercentages = settings.percentages,
                            tipFixedAmounts = settings.fixedAmounts,
                            tipCustomEnabled = settings.customEnabled,
                            tipEntryMode = TipEntryMode.valueOf(settings.entryMode)
                        )
                    }
                } catch (_: Exception) { }
            }
            settingsRepo.get("refund_settings").onSuccess { json ->
                try {
                    val settings = json?.let { Json.decodeFromString<RefundSettings>(it) }
                    if (settings != null) {
                        _state.value = _state.value.copy(
                            refundPolicy = RefundPolicy.valueOf(settings.policy),
                            partialRefundMinimum = settings.partialMinimum
                        )
                    }
                } catch (_: Exception) { }
            }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun toggleProvider(provider: PaymentProviderId) {
        val current = _state.value.activeProviders.toMutableSet()
        if (provider in current) current.remove(provider) else current.add(provider)
        _state.value = _state.value.copy(activeProviders = current)
    }

    fun setDefaultProvider(provider: PaymentProviderId) {
        _state.value = _state.value.copy(defaultProvider = provider)
    }

    fun updateProviderConfig(provider: PaymentProviderId, config: ProviderConfig) {
        val current = _state.value.providers.toMutableMap()
        current[provider] = config
        _state.value = _state.value.copy(providers = current, showProviderDialog = false, editingProvider = null)
    }

    fun openProviderDialog(provider: PaymentProviderId) {
        _state.value = _state.value.copy(editingProvider = provider, showProviderDialog = true)
    }

    fun closeProviderDialog() {
        _state.value = _state.value.copy(editingProvider = null, showProviderDialog = false)
    }

    fun setCashDrawerSettings(autoOpen: Boolean, closeConfirm: Boolean) {
        _state.value = _state.value.copy(cashDrawerAutoOpen = autoOpen, cashDrawerCloseConfirm = closeConfirm)
    }

    fun setTipSettings(
        percentages: List<Int>,
        fixedAmounts: List<String>,
        customEnabled: Boolean,
        entryMode: TipEntryMode
    ) {
        _state.value = _state.value.copy(
            tipPercentages = percentages,
            tipFixedAmounts = fixedAmounts,
            tipCustomEnabled = customEnabled,
            tipEntryMode = entryMode
        )
    }

    fun setRefundPolicy(policy: RefundPolicy, partialMinimum: String) {
        _state.value = _state.value.copy(refundPolicy = policy, partialRefundMinimum = partialMinimum)
    }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val errors = mutableListOf<String>()
            settingsRepo.set("payment_providers_config", Json.encodeToString(_state.value.providers), null)
                .onFailure { errors.add(it.message ?: "Provider config failed") }
            settingsRepo.set("payment_active_providers", Json.encodeToString(_state.value.activeProviders.toList()), null)
                .onFailure { errors.add(it.message ?: "Active providers failed") }
            settingsRepo.set("payment_default_provider", _state.value.defaultProvider.name, null)
                .onFailure { errors.add(it.message ?: "Default provider failed") }
            settingsRepo.set("cash_drawer_settings", Json.encodeToString(CashDrawerSettings(_state.value.cashDrawerAutoOpen, _state.value.cashDrawerCloseConfirm)), null)
                .onFailure { errors.add(it.message ?: "Cash drawer failed") }
            settingsRepo.set("tip_settings", Json.encodeToString(TipSettings(_state.value.tipPercentages, _state.value.tipFixedAmounts, _state.value.tipCustomEnabled, _state.value.tipEntryMode.name)), null)
                .onFailure { errors.add(it.message ?: "Tip settings failed") }
            settingsRepo.set("refund_settings", Json.encodeToString(RefundSettings(_state.value.refundPolicy.name, _state.value.partialRefundMinimum)), null)
                .onFailure { errors.add(it.message ?: "Refund settings failed") }
            if (errors.isEmpty()) {
                _state.value = _state.value.copy(isSaving = false, saved = true)
            } else {
                _state.value = _state.value.copy(isSaving = false, error = errors.joinToString("; "))
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}

@kotlinx.serialization.Serializable
private data class CashDrawerSettings(val autoOpen: Boolean, val closeConfirm: Boolean)

@kotlinx.serialization.Serializable
private data class TipSettings(val percentages: List<Int>, val fixedAmounts: List<String>, val customEnabled: Boolean, val entryMode: String)

@kotlinx.serialization.Serializable
private data class RefundSettings(val policy: String, val partialMinimum: String)
