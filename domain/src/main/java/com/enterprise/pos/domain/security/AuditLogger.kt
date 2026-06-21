package com.enterprise.pos.domain.security

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.AuditLogEntry
import com.enterprise.pos.domain.repository.AuditLogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import java.util.UUID

/**
 * AuditLogger provides convenience methods for logging security-relevant events
 * to the immutable audit log.
 *
 * PCI DSS Level 4: All system access and security events must be logged
 * (Req 10.1 - 10.7). Logs must be immutable, contain user identification,
 * event type, date/time, success/failure, and event origin.
 *
 * FIPS 140-2: Audit logging supports the detection of unauthorized key access
 * and cryptographic module tampering.
 */
class AuditLogger(
    private val auditLogRepository: AuditLogRepository
) {

    private val batchMutex = Mutex()
    private val batchBuffer = mutableListOf<AuditLogEntry>()
    private val batchSize = 50

    /**
     * Log a generic audit event.
     */
    suspend fun log(event: AuditEvent): Result<Unit> {
        val entry = event.toAuditLogEntry()
        return auditLogRepository.log(entry)
    }

    /**
     * Convenience method for logging a simple event.
     */
    suspend fun logEvent(
        type: String,
        entityType: String,
        entityId: String,
        details: Map<String, String> = emptyMap(),
        storeId: StoreId? = null,
        employeeId: EmployeeId? = null,
        severity: Severity = Severity.INFO
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = type,
            entityType = entityType,
            entityId = entityId,
            storeId = storeId,
            employeeId = employeeId,
            severity = severity,
            details = details
        )
        return log(event)
    }

    /**
     * Log an employee login attempt.
     */
    suspend fun logLogin(
        employeeId: EmployeeId,
        success: Boolean,
        reason: String? = null,
        storeId: StoreId? = null,
        registerId: RegisterId? = null,
        deviceId: String? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = if (success) "EMPLOYEE_LOGIN" else "EMPLOYEE_LOGIN_FAILED",
            entityType = "Employee",
            entityId = employeeId.value,
            employeeId = employeeId,
            storeId = storeId,
            registerId = registerId,
            severity = if (success) Severity.INFO else Severity.WARNING,
            details = buildMap {
                put("success", success.toString())
                reason?.let { put("reason", it) }
                deviceId?.let { put("deviceId", it) }
            }
        )
        return log(event)
    }

    /**
     * Log an order action.
     */
    suspend fun logOrderAction(
        orderId: OrderId,
        action: String,
        employeeId: EmployeeId,
        storeId: StoreId? = null,
        details: Map<String, String> = emptyMap()
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = action,
            entityType = "Order",
            entityId = orderId.value,
            employeeId = employeeId,
            storeId = storeId,
            details = details
        )
        return log(event)
    }

    /**
     * Log a payment action.
     */
    suspend fun logPaymentAction(
        paymentId: PaymentId,
        action: String,
        amount: Money,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = action,
            entityType = "Payment",
            entityId = paymentId.value,
            employeeId = employeeId,
            storeId = storeId,
            details = mapOf(
                "amount_minor" to amount.minorUnits.toString(),
                "amount_formatted" to amount.format()
            )
        )
        return log(event)
    }

    /**
     * Log a refund action.
     */
    suspend fun logRefundAction(
        orderId: OrderId,
        amount: Money,
        reason: String,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = "REFUND_ISSUED",
            entityType = "Order",
            entityId = orderId.value,
            employeeId = employeeId,
            storeId = storeId,
            severity = Severity.WARNING,
            details = mapOf(
                "amount_minor" to amount.minorUnits.toString(),
                "amount_formatted" to amount.format(),
                "reason" to reason
            )
        )
        return log(event)
    }

    /**
     * Log a void action.
     */
    suspend fun logVoidAction(
        orderId: OrderId,
        reason: String,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = "ORDER_VOIDED",
            entityType = "Order",
            entityId = orderId.value,
            employeeId = employeeId,
            storeId = storeId,
            severity = Severity.WARNING,
            details = mapOf("reason" to reason)
        )
        return log(event)
    }

    /**
     * Log a discount application.
     */
    suspend fun logDiscountApplied(
        orderId: OrderId,
        amount: Money,
        approvedBy: EmployeeId? = null,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = "DISCOUNT_APPLIED",
            entityType = "Order",
            entityId = orderId.value,
            employeeId = employeeId,
            storeId = storeId,
            severity = if (approvedBy != null) Severity.INFO else Severity.WARNING,
            details = buildMap {
                put("amount_minor", amount.minorUnits.toString())
                put("amount_formatted", amount.format())
                approvedBy?.let { put("approvedBy", it.value) }
            }
        )
        return log(event)
    }

    /**
     * Log an inventory adjustment.
     */
    suspend fun logInventoryAdjustment(
        productId: ProductId,
        oldQty: Int,
        newQty: Int,
        reason: String,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = "INVENTORY_ADJUSTED",
            entityType = "Product",
            entityId = productId.value,
            employeeId = employeeId,
            storeId = storeId,
            severity = Severity.INFO,
            details = mapOf(
                "oldQty" to oldQty.toString(),
                "newQty" to newQty.toString(),
                "delta" to (newQty - oldQty).toString(),
                "reason" to reason
            )
        )
        return log(event)
    }

    /**
     * Log an employee permission change.
     */
    suspend fun logEmployeePermissionChange(
        employeeId: EmployeeId,
        changedBy: EmployeeId,
        permissions: List<String>,
        storeId: StoreId? = null
    ): Result<Unit> {
        val event = AuditEvent(
            eventType = "EMPLOYEE_PERMISSION_CHANGED",
            entityType = "Employee",
            entityId = employeeId.value,
            employeeId = changedBy,
            storeId = storeId,
            severity = Severity.WARNING,
            details = mapOf(
                "changedBy" to changedBy.value,
                "permissions" to permissions.joinToString(",")
            )
        )
        return log(event)
    }

    /**
     * Log a security event (suspicious activity, failed auth, unauthorized access).
     */
    suspend fun logSecurityEvent(
        event: String,
        severity: Severity,
        employeeId: EmployeeId? = null,
        storeId: StoreId? = null,
        details: Map<String, String> = emptyMap()
    ): Result<Unit> {
        val auditEvent = AuditEvent(
            eventType = event,
            entityType = "Security",
            entityId = UUID.randomUUID().toString(),
            employeeId = employeeId,
            storeId = storeId,
            severity = severity,
            details = details
        )
        return log(auditEvent)
    }

    /**
     * Add an entry to the batch buffer. Flush when the batch reaches the threshold.
     *
     * PCI DSS: Batch logging ensures high-throughput operations (e.g., payment processing)
     * do not block on audit log I/O while maintaining audit trail completeness.
     */
    suspend fun logBatch(entry: AuditLogEntry): Result<Unit> {
        batchMutex.withLock {
            batchBuffer.add(entry)
            if (batchBuffer.size >= batchSize) {
                return flushBatch()
            }
        }
        return Result.success(Unit)
    }

    /**
     * Flush the current batch buffer to the repository.
     */
    suspend fun flushBatch(): Result<Unit> {
        batchMutex.withLock {
            if (batchBuffer.isEmpty()) return Result.success(Unit)
            val snapshot = batchBuffer.toList()
            batchBuffer.clear()
            for (entry in snapshot) {
                val result = auditLogRepository.log(entry)
                if (result is Result.Failure) {
                    return result
                }
            }
        }
        return Result.success(Unit)
    }

    private fun AuditEvent.toAuditLogEntry(): AuditLogEntry {
        return AuditLogEntry(
            id = com.enterprise.pos.core.Id(id),
            storeId = storeId ?: StoreId("unknown"),
            registerId = registerId,
            employeeId = employeeId ?: EmployeeId("unknown"),
            employeeName = "",
            action = runCatching { AuditAction.valueOf(eventType) }.getOrDefault(AuditAction.CONFIG_CHANGED),
            entityType = entityType,
            entityId = entityId,
            beforeJson = null,
            afterJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer()),
                details
            ),
            reason = null,
            timestamp = timestamp,
            ipAddress = ipAddress,
            deviceIdentifier = deviceId
        )
    }
}
