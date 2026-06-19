package com.enterprise.pos.feature.dashboard.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val autoRefresh: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analytics: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    fun load(storeId: StoreId) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (_state.value.autoRefresh) {
                val result = analytics.dashboard(storeId, SystemClock.now())
                result
                    .onSuccess { snap -> _state.value = _state.value.copy(snapshot = snap, isLoading = false, error = null) }
                    .onFailure { err -> _state.value = _state.value.copy(isLoading = false, error = err.message) }
                delay(30_000) // refresh every 30s while auto-refresh is on
            }
        }
    }

    fun toggleAutoRefresh() {
        _state.value = _state.value.copy(autoRefresh = !_state.value.autoRefresh)
        if (_state.value.autoRefresh) {
            // Restart refresh loop
        } else {
            refreshJob?.cancel()
        }
    }

    fun refreshNow(storeId: StoreId) {
        viewModelScope.launch {
            val result = analytics.dashboard(storeId, SystemClock.now())
            result
                .onSuccess { snap -> _state.value = _state.value.copy(snapshot = snap, isLoading = false, error = null) }
                .onFailure { err -> _state.value = _state.value.copy(isLoading = false, error = err.message) }
        }
    }
}
