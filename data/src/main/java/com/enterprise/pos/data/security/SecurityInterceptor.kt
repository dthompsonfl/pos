package com.enterprise.pos.data.security

import android.util.Log
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.security.AuditLogger
import com.enterprise.pos.domain.security.PermissionChecker
import com.enterprise.pos.domain.security.Severity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.TimeZone

/**
 * SecurityInterceptor intercepts repository operations to enforce permissions,
 * rate limiting, business hours validation, and audit logging.
 *
 * PCI DSS Level 4: Access controls must be enforced for all data mutations (Req 7).
 * Rate limiting prevents abuse of sensitive operations like refunds and voids.
 * All access to the cardholder data environment is logged (Req 10).
 *
 * FIPS 140-2: Access control complements cryptographic protections by limiting
 * which operations can be performed on sensitive data.
 */
class SecurityInterceptor(
    private val permissionChecker: PermissionChecker,
    private val auditLogger: AuditLogger
) {
    private val tag = "SecurityInterceptor"

    // Rate limiting buckets for sensitive operations
    private val rateLimitBuckets = mutableMapOf<String, RateLimitBucket>()
    private val rateLimitMutex = Mutex()

    data class RateLimitBucket(
        val key: String,
        val maxRequests: Int,
        val windowMs: Long,
        var count: Int = 0,
        var windowStart: Long = 0L
    )

    /**
     * Intercept a mutation operation with permission check and audit logging.
     *
     * @param employee The actor performing the operation
     * @param action The action being attempted
     * @param resource The resource being acted upon
     * @param operationName Human-readable name for the audit log
     * @param operationId Identifier for the target entity
     * @param block The actual operation to execute
     */
    suspend fun <T> interceptMutation(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationName: String,
        operationId: String,
        block: suspend () -> Result<T>
    ): Result<T> {
        // Permission check
        if (!permissionChecker.checkPermission(employee, action, resource)) {
            logDenied(employee, action, resource, operationName, operationId)
            return Result.failure(
                AppError.Permission(
                    required = "${action.name}_${resource.name}",
                    message = "Permission denied: ${action.name} ${resource.name}"
                )
            )
        }

        // Rate limiting for sensitive operations
        if (isRateLimited(resource)) {
            val rateLimitKey = "${employee.id.value}:${resource.name}"
            if (!checkRateLimit(rateLimitKey, getRateLimitConfig(resource))) {
                logRateLimitExceeded(employee, action, resource, operationName)
                return Result.failure(
                    AppError.Permission(
                        required = "rate_limit",
                        message = "Rate limit exceeded for ${resource.name}. Please wait before retrying."
                    )
                )
            }
        }

        // Business hours check for sensitive operations
        if (requiresBusinessHours(resource, action)) {
            if (!isWithinBusinessHours()) {
                if (!permissionChecker.checkPermission(employee, PermissionChecker.Action.MANAGE, resource)) {
                    logOutsideBusinessHours(employee, action, resource, operationName)
                    return Result.failure(
                        AppError.Permission(
                            required = "business_hours_manager",
                            message = "Operation requires manager approval outside business hours."
                        )
                    )
                }
            }
        }

        // Execute the operation
        val result = block()

        // Log the result
        logResult(employee, action, resource, operationName, operationId, result)

        return result
    }

    /**
     * Log data access (read operations) to the audit log.
     * This is a lighter-weight check than interceptMutation.
     */
    suspend fun logDataAccess(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationId: String,
        success: Boolean = true
    ) {
        val severity = if (success) Severity.INFO else Severity.WARNING
        auditLogger.logSecurityEvent(
            event = "DATA_ACCESS_${action.name}_${resource.name}",
            severity = severity,
            employeeId = employee.id,
            details = mapOf(
                "resource" to resource.name,
                "action" to action.name,
                "entityId" to operationId,
                "success" to success.toString(),
                "employeeRole" to employee.role.name
            )
        )
    }

    /**
     * Check if an operation is rate limited based on resource sensitivity.
     */
    private fun isRateLimited(resource: PermissionChecker.Resource): Boolean {
        return resource in setOf(
            PermissionChecker.Resource.REFUND,
            PermissionChecker.Resource.PAYMENT,
            PermissionChecker.Resource.ORDER
        )
    }

    /**
     * Get rate limit configuration for a resource.
     */
    private fun getRateLimitConfig(resource: PermissionChecker.Resource): Pair<Int, Long> {
        return when (resource) {
            PermissionChecker.Resource.REFUND -> 5 to 60_000L // 5 per minute
            PermissionChecker.Resource.PAYMENT -> 30 to 60_000L // 30 per minute
            PermissionChecker.Resource.ORDER -> 60 to 60_000L // 60 per minute
            else -> 100 to 60_000L // 100 per minute default
        }
    }

    /**
     * Check if an operation requires business hours validation.
     */
    private fun requiresBusinessHours(
        resource: PermissionChecker.Resource,
        action: PermissionChecker.Action
    ): Boolean {
        return resource in setOf(
            PermissionChecker.Resource.REFUND,
            PermissionChecker.Resource.INVENTORY,
            PermissionChecker.Resource.SETTING
        ) && action in setOf(PermissionChecker.Action.PROCESS, PermissionChecker.Action.MANAGE, PermissionChecker.Action.DELETE)
    }

    /**
     * Check if the current time is within standard business hours (6:00 - 23:00).
     */
    private fun isWithinBusinessHours(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour in 6..22
    }

    /**
     * Rate limit check using a sliding window counter.
     */
    private suspend fun checkRateLimit(key: String, config: Pair<Int, Long>): Boolean {
        val (maxRequests, windowMs) = config
        val now = System.currentTimeMillis()

        rateLimitMutex.withLock {
            val bucket = rateLimitBuckets.getOrPut(key) {
                RateLimitBucket(key, maxRequests, windowMs, 0, now)
            }

            if (now - bucket.windowStart > windowMs) {
                bucket.windowStart = now
                bucket.count = 0
            }

            bucket.count++
            return bucket.count <= bucket.maxRequests
        }
    }

    private suspend fun logDenied(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationName: String,
        operationId: String
    ) {
        Log.w(tag, "Permission denied: ${employee.id.value} attempted ${action.name} ${resource.name} on $operationName/$operationId")
        auditLogger.logSecurityEvent(
            event = "PERMISSION_DENIED",
            severity = Severity.WARNING,
            employeeId = employee.id,
            details = mapOf(
                "action" to action.name,
                "resource" to resource.name,
                "operation" to operationName,
                "entityId" to operationId,
                "role" to employee.role.name
            )
        )
    }

    private suspend fun logRateLimitExceeded(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationName: String
    ) {
        Log.w(tag, "Rate limit exceeded: ${employee.id.value} on ${resource.name} for $operationName")
        auditLogger.logSecurityEvent(
            event = "RATE_LIMIT_EXCEEDED",
            severity = Severity.WARNING,
            employeeId = employee.id,
            details = mapOf(
                "action" to action.name,
                "resource" to resource.name,
                "operation" to operationName,
                "role" to employee.role.name
            )
        )
    }

    private suspend fun logOutsideBusinessHours(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationName: String
    ) {
        Log.w(tag, "Outside business hours attempt: ${employee.id.value} on ${action.name} ${resource.name} for $operationName")
        auditLogger.logSecurityEvent(
            event = "OUTSIDE_BUSINESS_HOURS",
            severity = Severity.WARNING,
            employeeId = employee.id,
            details = mapOf(
                "action" to action.name,
                "resource" to resource.name,
                "operation" to operationName,
                "role" to employee.role.name
            )
        )
    }

    private suspend fun <T> logResult(
        employee: Employee,
        action: PermissionChecker.Action,
        resource: PermissionChecker.Resource,
        operationName: String,
        operationId: String,
        result: Result<T>
    ) {
        val success = result is Result.Success
        val severity = if (success) Severity.INFO else Severity.ERROR
        auditLogger.logSecurityEvent(
            event = "OPERATION_${if (success) "SUCCESS" else "FAILURE"}",
            severity = severity,
            employeeId = employee.id,
            details = mapOf(
                "action" to action.name,
                "resource" to resource.name,
                "operation" to operationName,
                "entityId" to operationId,
                "success" to success.toString()
            )
        )
    }
}
