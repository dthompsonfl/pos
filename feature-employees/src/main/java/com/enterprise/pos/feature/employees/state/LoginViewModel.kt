package com.enterprise.pos.feature.employees.state

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.security.BiometricAuth
import com.enterprise.pos.core.security.SecureStorage
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.security.AuditLogger
import com.enterprise.pos.domain.security.SessionManager
import com.enterprise.pos.domain.security.Severity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the login screen.
 */
data class LoginUiState(
    val pin: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val lockoutRemainingSeconds: Int = 0,
    val isLockedOut: Boolean = false,
    val requiresManagerUnlock: Boolean = false,
    val showBiometricButton: Boolean = false,
    val showForgotPinDialog: Boolean = false,
    val showManagerOverrideDialog: Boolean = false,
    val lastLoginAt: String? = null,
    val lastLoginDevice: String? = null,
    val securityWarning: String? = null,
    val currentEmployee: Employee? = null,
    val managerOverridePin: String = "",
    val managerOverrideError: String? = null
)

/**
 * LoginViewModel handles the complete authentication flow with security controls:
 * - PIN verification with escalating lockout
 * - Biometric authentication support
 * - Manager override for forgot PIN
 * - Session creation and management
 * - Audit logging of all login attempts
 * - Security warnings (unusual time, new device)
 *
 * PCI DSS Level 4: Failed login attempts are tracked and limited (Req 8.1.6, 8.1.7).
 * Sessions timeout based on role (Req 8.1.8). All login attempts are audited (Req 10.2).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val employeeRepository: EmployeeRepository,
    private val sessionManager: SessionManager,
    private val auditLogger: AuditLogger,
    private val secureStorage: SecureStorage,
    private val biometricAuth: BiometricAuth
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var lockoutJob: Job? = null
    private var failedAttempts = 0

    companion object {
        private const val KEY_LAST_LOGIN_AT = "last_login_at"
        private const val KEY_LAST_LOGIN_DEVICE = "last_login_device"
        private const val KEY_BIOMETRIC_ENROLLED = "biometric_enrolled"
        private const val LOCKOUT_5_THRESHOLD = 5
        private const val LOCKOUT_10_THRESHOLD = 10
        private const val LOCKOUT_15_THRESHOLD = 15
        private const val LOCKOUT_5_DURATION_MS = 5 * 60 * 1000L
        private const val LOCKOUT_10_DURATION_MS = 30 * 60 * 1000L
        private const val UNUSUAL_HOUR_START = 23
        private const val UNUSUAL_HOUR_END = 5
    }

    init {
        viewModelScope.launch {
            checkBiometricAvailability()
            loadLastLoginInfo()
            checkSecurityWarnings()
        }
    }

    fun typePin(digit: String) {
        if (_state.value.isLockedOut) return
        if (_state.value.pin.length >= 8) return
        _state.value = _state.value.copy(
            pin = _state.value.pin + digit,
            loginError = null,
            managerOverrideError = null
        )
    }

    fun clearPin() {
        _state.value = _state.value.copy(
            pin = _state.value.pin.dropLast(1),
            loginError = null
        )
    }

    fun login(onSuccess: (Employee) -> Unit) {
        val pin = _state.value.pin
        if (pin.length < 4) {
            _state.value = _state.value.copy(loginError = "PIN must be at least 4 digits")
            return
        }
        if (_state.value.isLockedOut) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoggingIn = true, loginError = null)

            val result = employeeRepository.login(pin)

            result.onSuccess { employee ->
                handleLoginSuccess(employee, onSuccess)
            }.onFailure { error ->
                handleLoginFailure(error)
            }
        }
    }

    fun biometricLogin(onSuccess: (Employee) -> Unit) {
        if (!_state.value.showBiometricButton) return

        viewModelScope.launch {
            val lastEmployeeId = secureStorage.read(KEY_BIOMETRIC_ENROLLED)
            if (lastEmployeeId == null) {
                _state.value = _state.value.copy(loginError = "Biometric login not set up")
                return@launch
            }

            val employeeResult = employeeRepository.get(EmployeeId(lastEmployeeId))
            employeeResult.onSuccess { employee ->
                if (employee != null) {
                    handleLoginSuccess(employee, onSuccess)
                } else {
                    _state.value = _state.value.copy(loginError = "Employee not found")
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(loginError = error.message)
            }
        }
    }

    fun onBiometricResult(success: Boolean, onSuccess: (Employee) -> Unit) {
        if (success) {
            biometricLogin(onSuccess)
        } else {
            _state.value = _state.value.copy(loginError = "Biometric authentication failed")
        }
    }

    fun showForgotPin() {
        _state.value = _state.value.copy(showForgotPinDialog = true)
    }

    fun dismissForgotPin() {
        _state.value = _state.value.copy(showForgotPinDialog = false, showManagerOverrideDialog = false)
    }

    fun showManagerOverride() {
        _state.value = _state.value.copy(showManagerOverrideDialog = true)
    }

    fun typeManagerPin(digit: String) {
        if (_state.value.managerOverridePin.length >= 8) return
        _state.value = _state.value.copy(
            managerOverridePin = _state.value.managerOverridePin + digit,
            managerOverrideError = null
        )
    }

    fun clearManagerPin() {
        _state.value = _state.value.copy(
            managerOverridePin = _state.value.managerOverridePin.dropLast(1),
            managerOverrideError = null
        )
    }

    /**
     * Verify manager PIN to unlock the device or reset a forgotten PIN.
     */
    fun verifyManagerOverride(onUnlocked: () -> Unit) {
        val managerPin = _state.value.managerOverridePin
        if (managerPin.length < 4) {
            _state.value = _state.value.copy(managerOverrideError = "PIN must be at least 4 digits")
            return
        }

        viewModelScope.launch {
            val result = employeeRepository.login(managerPin)
            result.onSuccess { employee ->
                if (employee.role in setOf(EmployeeRole.MANAGER, EmployeeRole.ADMIN, EmployeeRole.SHIFT_LEAD)) {
                    auditLogger.logSecurityEvent(
                        event = "MANAGER_OVERRIDE_UNLOCK",
                        severity = Severity.WARNING,
                        employeeId = employee.id,
                        details = mapOf("reason" to "Forgot PIN device unlock")
                    )
                    failedAttempts = 0
                    _state.value = _state.value.copy(
                        requiresManagerUnlock = false,
                        isLockedOut = false,
                        lockoutRemainingSeconds = 0,
                        showManagerOverrideDialog = false,
                        showForgotPinDialog = false,
                        managerOverridePin = "",
                        managerOverrideError = null
                    )
                    lockoutJob?.cancel()
                    onUnlocked()
                } else {
                    _state.value = _state.value.copy(managerOverrideError = "Manager or admin PIN required")
                    auditLogger.logSecurityEvent(
                        event = "MANAGER_OVERRIDE_DENIED",
                        severity = Severity.WARNING,
                        employeeId = employee.id,
                        details = mapOf("reason" to "Non-manager attempted override")
                    )
                }
            }.onFailure {
                _state.value = _state.value.copy(managerOverrideError = "Invalid manager PIN")
            }
        }
    }

    private suspend fun handleLoginSuccess(employee: Employee, onSuccess: (Employee) -> Unit) {
        failedAttempts = 0
        lockoutJob?.cancel()

        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val session = sessionManager.startSession(
            employeeId = employee.id,
            employeeName = employee.name,
            role = employee.role,
            deviceId = deviceId
        )

        secureStorage.write(KEY_LAST_LOGIN_AT, System.currentTimeMillis().toString())
        secureStorage.write(KEY_LAST_LOGIN_DEVICE, deviceId)
        secureStorage.write(KEY_BIOMETRIC_ENROLLED, employee.id.value)

        auditLogger.logLogin(
            employeeId = employee.id,
            success = true,
            storeId = null,
            deviceId = deviceId
        )

        _state.value = _state.value.copy(
            isLoggingIn = false,
            pin = "",
            loginError = null,
            isLockedOut = false,
            lockoutRemainingSeconds = 0,
            currentEmployee = employee
        )

        onSuccess(employee)
    }

    private suspend fun handleLoginFailure(error: AppError) {
        failedAttempts++
        val lockoutDuration = when {
            failedAttempts >= LOCKOUT_15_THRESHOLD -> Long.MAX_VALUE // Requires manager unlock
            failedAttempts >= LOCKOUT_10_THRESHOLD -> LOCKOUT_10_DURATION_MS
            failedAttempts >= LOCKOUT_5_THRESHOLD -> LOCKOUT_5_DURATION_MS
            else -> 0L
        }

        val requiresManagerUnlock = failedAttempts >= LOCKOUT_15_THRESHOLD

        auditLogger.logSecurityEvent(
            event = "LOGIN_FAILED",
            severity = if (failedAttempts >= 10) Severity.ERROR else Severity.WARNING,
            details = mapOf(
                "failedAttempts" to failedAttempts.toString(),
                "error" to error.message,
                "requiresManagerUnlock" to requiresManagerUnlock.toString()
            )
        )

        if (lockoutDuration > 0 && !requiresManagerUnlock) {
            startLockoutTimer(lockoutDuration)
        }

        _state.value = _state.value.copy(
            isLoggingIn = false,
            pin = "",
            loginError = when {
                requiresManagerUnlock -> "Too many failed attempts. Manager unlock required."
                lockoutDuration > 0 -> "Too many failed attempts. Locked for ${lockoutDuration / 1000} seconds."
                else -> error.message ?: "Invalid PIN"
            },
            isLockedOut = lockoutDuration > 0 || requiresManagerUnlock,
            requiresManagerUnlock = requiresManagerUnlock
        )
    }

    private fun startLockoutTimer(durationMs: Long) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var remaining = (durationMs / 1000).toInt()
            while (remaining > 0) {
                _state.value = _state.value.copy(lockoutRemainingSeconds = remaining)
                delay(1000)
                remaining--
            }
            _state.value = _state.value.copy(
                isLockedOut = false,
                lockoutRemainingSeconds = 0,
                loginError = null
            )
        }
    }

    private fun checkBiometricAvailability() {
        val available = biometricAuth.isAvailable()
        _state.value = _state.value.copy(showBiometricButton = available)
    }

    private fun loadLastLoginInfo() {
        val lastAt = secureStorage.readLong(KEY_LAST_LOGIN_AT, 0L)
        val lastDevice = secureStorage.read(KEY_LAST_LOGIN_DEVICE)

        if (lastAt > 0) {
            val formatted = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastAt))
            _state.value = _state.value.copy(
                lastLoginAt = formatted,
                lastLoginDevice = lastDevice
            )
        }
    }

    private fun checkSecurityWarnings() {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        if (hour >= UNUSUAL_HOUR_START || hour <= UNUSUAL_HOUR_END) {
            _state.value = _state.value.copy(securityWarning = "Unusual login time detected")
            return
        }

        val currentDevice = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val lastDevice = secureStorage.read(KEY_LAST_LOGIN_DEVICE)

        if (lastDevice != null && lastDevice != currentDevice) {
            _state.value = _state.value.copy(securityWarning = "New device detected")
            return
        }
    }

    override fun onCleared() {
        super.onCleared()
        lockoutJob?.cancel()
    }
}
