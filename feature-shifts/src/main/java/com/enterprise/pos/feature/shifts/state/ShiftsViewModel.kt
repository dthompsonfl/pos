package com.enterprise.pos.feature.shifts.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.Shift
import com.enterprise.pos.domain.model.ShiftStatus
import com.enterprise.pos.domain.model.TipPoolSummary
import com.enterprise.pos.domain.model.ZReport
import com.enterprise.pos.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftsUiState(
    val openShifts: List<Shift> = emptyList(),
    val currentShift: Shift? = null,
    val lastZReport: ZReport? = null,
    val tipPool: TipPoolSummary? = null,
    val showOpenSheet: Boolean = false,
    val showCloseSheet: Boolean = false,
    val startingCash: String = "100.00",
    val countedCash: String = "",
    val closeNotes: String = "",
    val isLoading: Boolean = true,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class ShiftsViewModel @Inject constructor(
    private val repo: ShiftRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftsUiState())
    val state: StateFlow<ShiftsUiState> = _state.asStateFlow()

    fun load(storeId: StoreId, registerId: RegisterId, employeeId: EmployeeId) {
        repo.observeOpenShifts(storeId)
            .onEach { shifts ->
                val current = shifts.firstOrNull { it.registerId == registerId }
                _state.value = _state.value.copy(openShifts = shifts, currentShift = current, isLoading = false)
            }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            repo.currentForEmployee(employeeId).onSuccess {
                _state.value = _state.value.copy(currentShift = it)
            }
        }
    }

    fun openOpenSheet() { _state.value = _state.value.copy(showOpenSheet = true) }
    fun closeOpenSheet() { _state.value = _state.value.copy(showOpenSheet = false) }
    fun setStartingCash(s: String) { _state.value = _state.value.copy(startingCash = s) }

    fun startShift(storeId: StoreId, registerId: RegisterId, employeeId: EmployeeId) {
        val cash = Money.of(_state.value.startingCash.toDoubleOrNull() ?: 100.0)
        viewModelScope.launch {
            repo.open(storeId, registerId, employeeId, cash)
                .onSuccess {
                    _state.value = _state.value.copy(showOpenSheet = false, currentShift = it, info = "Shift opened")
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun openCloseSheet() { _state.value = _state.value.copy(showCloseSheet = true) }
    fun closeCloseSheet() { _state.value = _state.value.copy(showCloseSheet = false) }
    fun setCountedCash(s: String) { _state.value = _state.value.copy(countedCash = s) }
    fun setCloseNotes(s: String) { _state.value = _state.value.copy(closeNotes = s) }

    fun closeShift() {
        val shift = _state.value.currentShift ?: return
        val counted = Money.of(_state.value.countedCash.toDoubleOrNull() ?: 0.0)
        val notes = _state.value.closeNotes
        viewModelScope.launch {
            repo.close(shift.id, counted, notes)
                .onSuccess { closed ->
                    _state.value = _state.value.copy(showCloseSheet = false, currentShift = null, info = "Shift closed")
                    // Generate Z-Report
                    repo.generateZReport(shift.id)
                        .onSuccess { z -> _state.value = _state.value.copy(lastZReport = z) }
                    repo.computeTipPool(shift.id)
                        .onSuccess { pool -> _state.value = _state.value.copy(tipPool = pool) }
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
