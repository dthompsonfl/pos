package com.enterprise.pos.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.RolePermissions
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.SettingsRepository
import com.enterprise.pos.domain.repository.StoreRepository
import com.enterprise.pos.domain.service.TaxConfiguration
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.PaymentRouterConfig
import com.enterprise.pos.payment.router.PaymentRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SettingsUiState(
    val store: com.enterprise.pos.domain.model.Store? = null,
    val register: com.enterprise.pos.domain.model.Register? = null,
    val printerName: String = "",
    val drawerName: String = "",
    val taxConfig: TaxConfiguration = TaxConfiguration.RESTAURANT,
    val defaultProvider: PaymentProviderId = PaymentProviderId.STRIPE,
    val enabledProviders: Set<PaymentProviderId> = emptySet(),
    val rolePermissions: Map<EmployeeRole, RolePermissions> = emptyMap(),
    val maxOfflineAmount: Money = Money.of(500.0),
    val darkMode: Boolean = false,
    val autoPrintReceipts: Boolean = true,
    val enableOfflineMode: Boolean = true,
    val enableQuickCheckout: Boolean = true,
    val employeeCount: Int = 0,
    val lastBackupAt: Long? = null,
    val appVersion: String = "1.0.0",
    val buildNumber: String = "1",
    val isLoading: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val employeeRepo: EmployeeRepository,
    private val settingsRepo: SettingsRepository,
    private val router: PaymentRouter
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val perms = mutableMapOf<EmployeeRole, RolePermissions>()
            EmployeeRole.entries.forEach { role ->
                employeeRepo.permissions(role).onSuccess { perms[role] = it }
            }
            val employees = try { employeeRepo.observeEmployees().first().size } catch (_: Exception) { 0 }
            val store = storeRepo.current().getOrNull()
            val registers = store?.let { storeRepo.registers(it.id).getOrNull() } ?: emptyList()
            val register = registers.firstOrNull { it.active } ?: registers.firstOrNull()
            _state.value = _state.value.copy(
                store = store,
                register = register,
                rolePermissions = perms,
                enabledProviders = router.availableProviders,
                employeeCount = employees,
                isLoading = false
            )
        }
    }

    fun setDefaultProvider(p: PaymentProviderId) {
        _state.value = _state.value.copy(defaultProvider = p)
        saveConfig()
    }

    fun toggleProvider(p: PaymentProviderId) {
        val current = _state.value.enabledProviders.toMutableSet()
        if (p in current) current.remove(p) else current.add(p)
        _state.value = _state.value.copy(enabledProviders = current)
        saveConfig()
    }

    fun setDarkMode(b: Boolean) { _state.value = _state.value.copy(darkMode = b); saveConfig() }
    fun setAutoPrint(b: Boolean) { _state.value = _state.value.copy(autoPrintReceipts = b); saveConfig() }
    fun setOfflineMode(b: Boolean) { _state.value = _state.value.copy(enableOfflineMode = b); saveConfig() }
    fun setQuickCheckout(b: Boolean) { _state.value = _state.value.copy(enableQuickCheckout = b); saveConfig() }
    fun setMaxOfflineAmount(m: Money) { _state.value = _state.value.copy(maxOfflineAmount = m); saveConfig() }
    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }

    private fun saveConfig() {
        viewModelScope.launch {
            val s = _state.value
            val config = PaymentRouterConfig(
                enabledProviders = s.enabledProviders,
                defaultProvider = s.defaultProvider,
                enableOfflineMode = s.enableOfflineMode,
                maxOfflineAmount = s.maxOfflineAmount
            )
            settingsRepo.set("payment_router_config", Json.encodeToString(config), null)
        }
    }
}
