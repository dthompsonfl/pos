package com.enterprise.pos.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.Register
import com.enterprise.pos.domain.model.Store
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.StoreRepository
import com.enterprise.pos.domain.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

enum class OnboardingStep {
    WELCOME, STORE, REGISTER, EMPLOYEE, PRODUCT, PAYMENT, COMPLETE
}

@kotlinx.serialization.Serializable
data class OnboardingProgress(
    val currentStepIndex: Int = 0,
    val completedSteps: Set<Int> = emptySet(),
    val storeName: String = "",
    val storeAddress: String = "",
    val storePhone: String = "",
    val storeTaxId: String = "",
    val storeCurrency: String = "USD",
    val storeTimezone: String = "America/New_York",
    val registerName: String = "",
    val deviceType: String = "",
    val adminName: String = "",
    val adminEmail: String = "",
    val adminPin: String = "",
    val termsAccepted: Boolean = false
)

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val completedSteps: Set<OnboardingStep> = emptySet(),
    val progress: OnboardingProgress = OnboardingProgress(),
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val canSkip: Boolean = true,
    val canGoBack: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storeRepo: StoreRepository,
    private val employeeRepo: EmployeeRepository
) : ViewModel() {

    private val dataStore = context.onboardingDataStore

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restoreProgress() }
    }

    private suspend fun restoreProgress() {
        val prefs = dataStore.data.first()
        val json = prefs[stringPreferencesKey("progress")]
        if (json != null) {
            try {
                val saved = Json.decodeFromString<OnboardingProgress>(json)
                _state.value = _state.value.copy(
                    currentStep = OnboardingStep.entries[saved.currentStepIndex.coerceIn(0, OnboardingStep.entries.size - 1)],
                    completedSteps = saved.completedSteps.mapNotNull { OnboardingStep.entries.getOrNull(it) }.toSet(),
                    progress = saved,
                    canGoBack = saved.currentStepIndex > 0
                )
            } catch (_: Exception) { }
        }
    }

    private suspend fun saveProgress() {
        val current = _state.value
        val progress = current.progress.copy(
            currentStepIndex = current.currentStep.ordinal,
            completedSteps = current.completedSteps.map { it.ordinal }.toSet()
        )
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("progress")] = Json.encodeToString(progress)
        }
    }

    fun goToNext() {
        val current = _state.value.currentStep
        val nextOrdinal = current.ordinal + 1
        if (nextOrdinal >= OnboardingStep.entries.size) return
        val next = OnboardingStep.entries[nextOrdinal]
        val completed = _state.value.completedSteps.toMutableSet().apply { add(current) }
        _state.value = _state.value.copy(
            currentStep = next,
            completedSteps = completed,
            canGoBack = true,
            canSkip = next != OnboardingStep.COMPLETE,
            error = null
        )
        viewModelScope.launch { saveProgress() }
    }

    fun goBack() {
        val current = _state.value.currentStep
        if (current.ordinal <= 0) return
        val prev = OnboardingStep.entries[current.ordinal - 1]
        _state.value = _state.value.copy(
            currentStep = prev,
            canGoBack = prev.ordinal > 0,
            canSkip = true,
            error = null
        )
        viewModelScope.launch { saveProgress() }
    }

    fun skipStep() {
        goToNext()
    }

    fun updateProgress(block: OnboardingProgress. -> OnboardingProgress) {
        _state.value = _state.value.copy(progress = block(_state.value.progress))
    }

    fun validateCurrentStep(): Boolean {
        val s = _state.value
        return when (s.currentStep) {
            OnboardingStep.WELCOME -> s.progress.termsAccepted
            OnboardingStep.STORE -> s.progress.storeName.isNotBlank() && s.progress.storeAddress.isNotBlank() && s.progress.storePhone.isNotBlank()
            OnboardingStep.REGISTER -> s.progress.registerName.isNotBlank()
            OnboardingStep.EMPLOYEE -> s.progress.adminName.isNotBlank() && s.progress.adminPin.length >= 4
            OnboardingStep.PRODUCT -> true
            OnboardingStep.PAYMENT -> true
            OnboardingStep.COMPLETE -> true
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val p = _state.value.progress

            // Create store
            val store = Store(
                id = StoreId(com.enterprise.pos.core.Id.random<Any>().value),
                name = p.storeName,
                address = p.storeAddress,
                phone = p.storePhone,
                taxIdentifier = p.storeTaxId.ifBlank { null },
                currency = p.storeCurrency,
                timezone = p.storeTimezone
            )
            storeRepo.upsertStore(store).onFailure { err ->
                _state.value = _state.value.copy(isLoading = false, error = err.message)
                return@launch
            }

            // Create register
            val register = Register(
                id = RegisterId(com.enterprise.pos.core.Id.random<Any>().value),
                storeId = store.id,
                name = p.registerName.ifBlank { "Main Register" },
                deviceIdentifier = p.deviceType.ifBlank { android.os.Build.MODEL },
                active = true
            )
            storeRepo.upsertRegister(register).onFailure { err ->
                _state.value = _state.value.copy(isLoading = false, error = err.message)
                return@launch
            }

            // Create admin employee
            val employee = Employee(
                id = EmployeeId(com.enterprise.pos.core.Id.random<Any>().value),
                name = p.adminName,
                pinHash = PinHasher.hash(p.adminPin),
                role = EmployeeRole.ADMIN,
                active = true,
                email = p.adminEmail.ifBlank { null }
            )
            employeeRepo.upsert(employee).onFailure { err ->
                _state.value = _state.value.copy(isLoading = false, error = err.message)
                return@launch
            }

            // Clear onboarding progress
            dataStore.edit { it.clear() }
            _state.value = _state.value.copy(isLoading = false, isComplete = true)
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
