package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Percent
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.repository.SettingsRepository
import com.enterprise.pos.domain.service.TaxConfiguration
import com.enterprise.pos.domain.service.TaxRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class TaxSettingsUiState(
    val rules: List<TaxRule> = emptyList(),
    val defaultCategory: TaxCategory = TaxCategory.STANDARD,
    val inclusivePricing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val editingRule: TaxRule? = null,
    val showDialog: Boolean = false
)

@HiltViewModel
class TaxSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TaxSettingsUiState())
    val state: StateFlow<TaxSettingsUiState> = _state.asStateFlow()

    init {
        loadTaxConfig()
    }

    private fun loadTaxConfig() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            settingsRepo.get("tax_config").onSuccess { json ->
                try {
                    val config = json?.let { Json.decodeFromString<TaxConfiguration>(it) } ?: TaxConfiguration.DEFAULT
                    _state.value = _state.value.copy(
                        rules = config.rules,
                        defaultCategory = config.defaultCategory,
                        isLoading = false
                    )
                } catch (_: Exception) {
                    _state.value = _state.value.copy(rules = TaxConfiguration.DEFAULT.rules, isLoading = false)
                }
            }.onFailure {
                _state.value = _state.value.copy(rules = TaxConfiguration.DEFAULT.rules, isLoading = false)
            }
            settingsRepo.get("tax_inclusive_pricing").onSuccess { json ->
                _state.value = _state.value.copy(inclusivePricing = json?.toBooleanStrictOrNull() ?: false)
            }
        }
    }

    fun addRule(rule: TaxRule) {
        val current = _state.value.rules.toMutableList()
        if (current.any { it.name == rule.name }) {
            _state.value = _state.value.copy(error = "A tax rule with this name already exists")
            return
        }
        current.add(rule)
        _state.value = _state.value.copy(rules = current, showDialog = false, error = null)
    }

    fun updateRule(oldName: String, rule: TaxRule) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.name == oldName }
        if (idx >= 0) {
            current[idx] = rule
            _state.value = _state.value.copy(rules = current, showDialog = false, editingRule = null, error = null)
        }
    }

    fun deleteRule(name: String) {
        val current = _state.value.rules.filter { it.name != name }
        _state.value = _state.value.copy(rules = current, error = null)
    }

    fun setDefaultCategory(category: TaxCategory) {
        _state.value = _state.value.copy(defaultCategory = category)
    }

    fun setInclusivePricing(inclusive: Boolean) {
        _state.value = _state.value.copy(inclusivePricing = inclusive)
    }

    fun openAddDialog() { _state.value = _state.value.copy(showDialog = true, editingRule = null, error = null) }
    fun openEditDialog(rule: TaxRule) { _state.value = _state.value.copy(showDialog = true, editingRule = rule, error = null) }
    fun closeDialog() { _state.value = _state.value.copy(showDialog = false, editingRule = null, error = null) }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val config = TaxConfiguration(rules = _state.value.rules, defaultCategory = _state.value.defaultCategory)
            settingsRepo.set("tax_config", Json.encodeToString(config), null).onSuccess {
                settingsRepo.set("tax_inclusive_pricing", _state.value.inclusivePricing.toString(), null)
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
