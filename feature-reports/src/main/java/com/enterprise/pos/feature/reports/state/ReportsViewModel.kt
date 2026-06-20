package com.enterprise.pos.feature.reports.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AbcClass
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.SalesByCategory
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.domain.model.TaxLiabilityReport
import com.enterprise.pos.domain.repository.AnalyticsRepository
import com.enterprise.pos.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ReportTab { OVERVIEW, HOURLY, CATEGORY, EMPLOYEE, TAX, ABC, AUDIT, Z_REPORTS }

data class ReportsUiState(
    val activeTab: ReportTab = ReportTab.OVERVIEW,
    val dateFrom: Long = 0L,
    val dateTo: Long = 0L,
    val hourlySales: List<SalesByHour> = emptyList(),
    val categorySales: List<SalesByCategory> = emptyList(),
    val employeeSales: List<SalesByEmployee> = emptyList(),
    val taxReport: TaxLiabilityReport? = null,
    val abcAnalysis: List<AbcAnalysis> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val orders: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        // Default to last 30 days
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        _state.value = _state.value.copy(dateFrom = from, dateTo = to)
    }

    fun load(storeId: StoreId) {
        viewModelScope.launch {
            val from = _state.value.dateFrom
            val to = _state.value.dateTo
            analytics.salesByHour(storeId, from, to).onSuccess { _state.value = _state.value.copy(hourlySales = it, isLoading = false) }
            analytics.salesByCategory(storeId, from, to).onSuccess { _state.value = _state.value.copy(categorySales = it) }
            analytics.salesByEmployee(storeId, from, to).onSuccess { _state.value = _state.value.copy(employeeSales = it) }
            analytics.taxLiability(storeId, from, to).onSuccess { _state.value = _state.value.copy(taxReport = it) }
            analytics.abcAnalysis(storeId, from, to).onSuccess { _state.value = _state.value.copy(abcAnalysis = it) }
        }
    }

    fun setTab(tab: ReportTab) { _state.value = _state.value.copy(activeTab = tab) }

    fun setDateRange(from: Long, to: Long) {
        _state.value = _state.value.copy(dateFrom = from, dateTo = to)
    }
}
