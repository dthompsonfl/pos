package com.enterprise.pos.feature.migration.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.domain.model.MigrationJob
import com.enterprise.pos.domain.model.MigrationSource
import com.enterprise.pos.domain.model.MigrationType
import com.enterprise.pos.domain.repository.MigrationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MigrationUiState(
    val jobs: List<MigrationJob> = emptyList(),
    val showCreateSheet: Boolean = false,
    val newSource: MigrationSource = MigrationSource.SHOPIFY,
    val newType: MigrationType = MigrationType.ALL,
    val configJson: String = "",
    val isWorking: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val repo: MigrationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationUiState())
    val state: StateFlow<MigrationUiState> = _state.asStateFlow()

    init {
        repo.observeJobs()
            .onEach { jobs -> _state.value = _state.value.copy(jobs = jobs) }
            .launchIn(viewModelScope)
    }

    fun openCreateSheet(source: MigrationSource) {
        val template = when (source) {
            MigrationSource.SHOPIFY -> """{"shop":"your-store.myshopify.com","accessToken":"shpat_...","types":["products","customers","orders"]}"""
            MigrationSource.SQUARE -> """{"applicationId":"sq0idp-...","accessToken":"EAAAl...","types":["catalog","customers","payments"]}"""
            MigrationSource.STRIPE -> """{"secretKey":"sk_live_...","locationId":"loc_...","types":["products","customers","payments"]}"""
            MigrationSource.CSV -> """{"filePath":"/path/to/file.csv","mapping":{}}"""
            MigrationSource.OTHER -> "{}"
        }
        _state.value = _state.value.copy(
            showCreateSheet = true,
            newSource = source,
            newType = MigrationType.ALL,
            configJson = template
        )
    }

    fun closeCreateSheet() { _state.value = _state.value.copy(showCreateSheet = false) }

    fun setType(t: MigrationType) { _state.value = _state.value.copy(newType = t) }
    fun setConfigJson(s: String) { _state.value = _state.value.copy(configJson = s) }

    fun startMigration(employeeId: EmployeeId) {
        val src = _state.value.newSource
        val cfg = _state.value.configJson
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true)
            val createResult = when (src) {
                MigrationSource.SHOPIFY -> repo.importFromShopify(cfg, employeeId)
                MigrationSource.SQUARE -> repo.importFromSquare(cfg, employeeId)
                MigrationSource.STRIPE -> repo.importFromStripe(cfg, employeeId)
                MigrationSource.CSV -> repo.importFromCsv(cfg, employeeId)
                MigrationSource.OTHER -> repo.createJob(src, _state.value.newType, cfg, employeeId)
            }
            createResult
                .onSuccess { job ->
                    val started = repo.startJob(job.id)
                    started.onSuccess {
                        _state.value = _state.value.copy(isWorking = false, showCreateSheet = false, info = "Migration completed")
                    }.onFailure {
                        _state.value = _state.value.copy(isWorking = false, error = it.message)
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isWorking = false, error = it.message)
                }
        }
    }

    fun cancelJob(jobId: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.MigrationJobTag>) {
        viewModelScope.launch { repo.cancelJob(jobId) }
    }

    fun dismissInfo() { _state.value = _state.value.copy(info = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
