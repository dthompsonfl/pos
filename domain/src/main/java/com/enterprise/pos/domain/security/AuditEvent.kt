package com.enterprise.pos.domain.security

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import java.util.UUID

/**
 * Severity levels for audit events.
 */
enum class Severity {
    INFO, WARNING, ERROR, CRITICAL
}

/**
 * AuditEvent is the canonical security event model for the POS system.
 *
 * PCI DSS Level 4: All access to cardholder data and all actions affecting the
 * cardholder data environment must be logged (Req 10.1, 10.2, 10.3).
 *
 * FIPS 140-2: Audit events are immutable and append-only; tampering is detectable
 * via the integrity of the audit log chain.
 */
data class AuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val severity: Severity = Severity.INFO,
    val entityType: String,
    val entityId: String,
    val employeeId: EmployeeId? = null,
    val storeId: StoreId? = null,
    val registerId: RegisterId? = null,
    val details: Map<String, String> = emptyMap(),
    val ipAddress: String? = null,
    val deviceId: String? = null
) {
    companion object {
        fun builder(eventType: String, entityType: String, entityId: String): AuditEventBuilder {
            return AuditEventBuilder(eventType, entityType, entityId)
        }
    }
}

/**
 * Fluent builder for constructing AuditEvents.
 */
class AuditEventBuilder(
    private val eventType: String,
    private val entityType: String,
    private val entityId: String
) {
    private var id: String = UUID.randomUUID().toString()
    private var timestamp: Long = System.currentTimeMillis()
    private var severity: Severity = Severity.INFO
    private var employeeId: EmployeeId? = null
    private var storeId: StoreId? = null
    private var registerId: RegisterId? = null
    private val details: MutableMap<String, String> = mutableMapOf()
    private var ipAddress: String? = null
    private var deviceId: String? = null

    fun id(id: String) = apply { this.id = id }
    fun timestamp(timestamp: Long) = apply { this.timestamp = timestamp }
    fun severity(severity: Severity) = apply { this.severity = severity }
    fun employeeId(employeeId: EmployeeId?) = apply { this.employeeId = employeeId }
    fun storeId(storeId: StoreId?) = apply { this.storeId = storeId }
    fun registerId(registerId: RegisterId?) = apply { this.registerId = registerId }
    fun detail(key: String, value: String) = apply { this.details[key] = value }
    fun details(details: Map<String, String>) = apply { this.details.putAll(details) }
    fun ipAddress(ipAddress: String?) = apply { this.ipAddress = ipAddress }
    fun deviceId(deviceId: String?) = apply { this.deviceId = deviceId }

    fun build(): AuditEvent = AuditEvent(
        id = id,
        timestamp = timestamp,
        eventType = eventType,
        severity = severity,
        entityType = entityType,
        entityId = entityId,
        employeeId = employeeId,
        storeId = storeId,
        registerId = registerId,
        details = details.toMap(),
        ipAddress = ipAddress,
        deviceId = deviceId
    )
}

/**
 * Query specification for retrieving audit events with filters.
 */
data class AuditEventQuery(
    val storeId: StoreId? = null,
    val employeeId: EmployeeId? = null,
    val registerId: RegisterId? = null,
    val eventTypes: List<String>? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val severities: List<Severity>? = null,
    val fromTimestamp: Long? = null,
    val toTimestamp: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0
) {
    fun hasFilters(): Boolean {
        return storeId != null || employeeId != null || registerId != null ||
            eventTypes != null || entityType != null || entityId != null ||
            severities != null || fromTimestamp != null || toTimestamp != null
    }
}
