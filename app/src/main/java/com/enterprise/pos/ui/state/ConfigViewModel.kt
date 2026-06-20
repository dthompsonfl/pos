package com.enterprise.pos.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application configuration state — loaded from the database so production
 * deployments do not rely on hardcoded store/register IDs.
 *
 * If no store or register is configured, the UI should redirect to onboarding.
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val storeRepo: StoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigState())
    val state: StateFlow<ConfigState> = _state.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            val store = storeRepo.current().getOrNull()
            val registers = store?.let { storeRepo.registers(it.id).getOrNull() } ?: emptyList()
            val register = registers.firstOrNull { it.active }
            _state.value = ConfigState(
                storeId = store?.id,
                registerId = register?.id,
                isReady = store != null && register != null
            )
        }
    }

    data class ConfigState(
        val storeId: StoreId? = null,
        val registerId: RegisterId? = null,
        val isReady: Boolean = false
    )
}
