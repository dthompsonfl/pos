package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Store
import com.enterprise.pos.domain.repository.SettingsRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoreSettingsUiState(
    val store: Store? = null,
    val receiptHeader: String = "",
    val receiptFooter: String = "",
    val hasLogo: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StoreSettingsViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StoreSettingsUiState())
    val state: StateFlow<StoreSettingsUiState> = _state.asStateFlow()

    init {
        loadStore()
        loadReceiptSettings()
    }

    private fun loadStore() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            storeRepo.current().onSuccess { store ->
                _state.value = _state.value.copy(store = store, isLoading = false)
            }.onFailure { err ->
                _state.value = _state.value.copy(isLoading = false, error = err.message)
            }
        }
    }

    private fun loadReceiptSettings() {
        viewModelScope.launch {
            settingsRepo.get("store_receipt_header").onSuccess { header ->
                if (header != null) _state.value = _state.value.copy(receiptHeader = header)
            }
            settingsRepo.get("store_receipt_footer").onSuccess { footer ->
                if (footer != null) _state.value = _state.value.copy(receiptFooter = footer)
            }
            settingsRepo.get("store_receipt_has_logo").onSuccess { logo ->
                if (logo != null) _state.value = _state.value.copy(hasLogo = logo.toBooleanStrictOrNull() ?: false)
            }
        }
    }

    fun updateStore(
        name: String = _state.value.store?.name ?: "",
        address: String = _state.value.store?.address ?: "",
        phone: String = _state.value.store?.phone ?: "",
        taxId: String? = _state.value.store?.taxIdentifier,
        currency: String = _state.value.store?.currency ?: "USD",
        timezone: String = _state.value.store?.timezone ?: "America/New_York"
    ) {
        val current = _state.value.store ?: Store(
            id = StoreId(com.enterprise.pos.core.Id.random<Any>().value),
            name = name,
            address = address,
            phone = phone,
            taxIdentifier = taxId,
            currency = currency,
            timezone = timezone
        )
        _state.value = _state.value.copy(
            store = current.copy(
                name = name,
                address = address,
                phone = phone,
                taxIdentifier = taxId,
                currency = currency,
                timezone = timezone
            )
        )
    }

    fun updateReceiptHeader(header: String) { _state.value = _state.value.copy(receiptHeader = header) }
    fun updateReceiptFooter(footer: String) { _state.value = _state.value.copy(receiptFooter = footer) }
    fun toggleLogo(hasLogo: Boolean) { _state.value = _state.value.copy(hasLogo = hasLogo) }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saved = false, error = null)
            val store = _state.value.store
            if (store == null) {
                _state.value = _state.value.copy(isSaving = false, error = "No store data")
                return@launch
            }
            if (store.name.isBlank() || store.address.isBlank() || store.phone.isBlank()) {
                _state.value = _state.value.copy(isSaving = false, error = "Name, address, and phone are required")
                return@launch
            }
            storeRepo.upsertStore(store).onSuccess {
                settingsRepo.set("store_receipt_header", _state.value.receiptHeader, null)
                settingsRepo.set("store_receipt_footer", _state.value.receiptFooter, null)
                settingsRepo.set("store_receipt_has_logo", _state.value.hasLogo.toString(), null)
                _state.value = _state.value.copy(isSaving = false, saved = true)
            }.onFailure { err ->
                _state.value = _state.value.copy(isSaving = false, error = err.message)
            }
        }
    }

    fun dismissSaved() { _state.value = _state.value.copy(saved = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
