package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.PromotionRepository
import com.enterprise.pos.domain.repository.StoreRepository
import com.enterprise.pos.domain.service.TaxConfiguration
import com.enterprise.pos.domain.service.TaxRule
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentRouterConfig
import com.enterprise.pos.payment.router.PaymentRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val taxConfig: TaxConfiguration = TaxConfiguration.RESTAURANT,
    val defaultProvider: PaymentProviderId = PaymentProviderId.STRIPE,
    val enabledProviders: Set<PaymentProviderId> = emptySet(),
    val rolePermissions: Map<EmployeeRole, RolePermissions> = emptyMap(),
    val maxOfflineAmount: Money = Money.of(500.0),
    val darkMode: Boolean = false,
    val autoPrintReceipts: Boolean = true,
    val enableOfflineMode: Boolean = true,
    val enableQuickCheckout: Boolean = true,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val employeeRepo: EmployeeRepository,
    private val promotionRepo: PromotionRepository,
    private val router: PaymentRouter
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load role permissions for all roles
            val perms = mutableMapOf<EmployeeRole, RolePermissions>()
            EmployeeRole.values().forEach { role ->
                employeeRepo.permissions(role).onSuccess { perms[role] = it }
            }
            _state.value = _state.value.copy(
                rolePermissions = perms,
                enabledProviders = router.availableProviders
            )
        }
    }

    fun setDefaultProvider(p: PaymentProviderId) {
        _state.value = _state.value.copy(defaultProvider = p)
    }

    fun toggleProvider(p: PaymentProviderId) {
        val current = _state.value.enabledProviders.toMutableSet()
        if (p in current) current.remove(p) else current.add(p)
        _state.value = _state.value.copy(enabledProviders = current)
    }

    fun setDarkMode(b: Boolean) { _state.value = _state.value.copy(darkMode = b) }
    fun setAutoPrint(b: Boolean) { _state.value = _state.value.copy(autoPrintReceipts = b) }
    fun setOfflineMode(b: Boolean) { _state.value = _state.value.copy(enableOfflineMode = b) }
    fun setQuickCheckout(b: Boolean) { _state.value = _state.value.copy(enableQuickCheckout = b) }
    fun setMaxOfflineAmount(m: Money) { _state.value = _state.value.copy(maxOfflineAmount = m) }
    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
