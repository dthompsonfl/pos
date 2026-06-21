// PRODUCTION WARNING: This is an in-memory implementation for development.
// Replace with a persistent database (PostgreSQL, DynamoDB, etc.) before production deployment.
// Required: encrypted token storage, TTL for OAuth states, audit logging.

package com.enterprise.pos.backend.storage

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Status of a sync event in the backend pipeline.
 *
 * PENDING   — received but not yet validated
 * ACCEPTED  — passed validation and deduplication
 * APPLIED   — applied to the in-memory state (or database in production)
 * CONFLICT  — optimistic-lock or schema-version mismatch; needs resolution
 * REJECTED  — failed validation or malformed payload
 * RESOLVED  — conflict was resolved by operator or client
 * DUPLICATE — idempotency key already processed
 */
enum class SyncEventStatus {
    PENDING, ACCEPTED, APPLIED, CONFLICT, REJECTED, RESOLVED, DUPLICATE
}

/**
 * Represents a single sync event received from the POS.
 *
 * This class is immutable so that concurrent reads are safe. Mutations happen
 * by replacing the entry in the backing store with a copy. Production deployments
 * should replace the in-memory store with a database table that has a unique
 * constraint on idempotency_key.
 */
@Serializable
@Suppress("LongParameterList")
data class SyncEvent(
    val eventId: String,
    val storeId: String,
    val registerId: String? = null,
    val employeeId: String? = null,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val schemaVersion: Int,
    val idempotencyKey: String,
    val payloadJson: String,
    val createdAt: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val status: SyncEventStatus = SyncEventStatus.PENDING,
    val appliedAt: Long? = null,
    val resolvedAt: Long? = null,
    val resolution: String? = null,
    val serverVersionJson: String? = null
)

/**
 * In-memory store for sync events. Thread-safe via ConcurrentHashMap.
 *
 * **Design note for production:**
 * Replace this class with a database-backed implementation (e.g., PostgreSQL with
 * Exposed or jOOQ). The required schema is:
 *   - events table with columns matching SyncEvent fields
 *   - UNIQUE INDEX on idempotency_key
 *   - INDEX on store_id + status for queue queries
 */
class SyncEventStore {

    private val events = ConcurrentHashMap<String, SyncEvent>() // eventId -> SyncEvent
    private val idempotencyIndex = ConcurrentHashMap<String, String>() // idempotencyKey -> eventId
    private val logger = org.slf4j.LoggerFactory.getLogger(SyncEventStore::class.java)

    /**
     * Store a sync event. Validates required fields, deduplicates by idempotency key,
     * and marks the event as ACCEPTED then APPLIED.
     *
     * Returns a Result containing the event with its final status, or a failure if
     * validation fails or the idempotency index is corrupted.
     */
    fun storeEvent(event: SyncEvent): Result<SyncEvent> {
        val existingEventId = idempotencyIndex[event.idempotencyKey]
        if (existingEventId != null) {
            val existing = events[existingEventId]
            return if (existing != null) {
                logger.info("Duplicate idempotency key {} for event {}", event.idempotencyKey, existingEventId)
                Result.success(existing.copy(status = SyncEventStatus.DUPLICATE))
            } else {
                Result.failure(IllegalStateException("Idempotency index corrupted for key ${event.idempotencyKey}"))
            }
        }

        if (event.entityType.isBlank() || event.entityId.isBlank() || event.operation.isBlank()) {
            return Result.failure(IllegalArgumentException("entityType, entityId, and operation are required"))
        }

        if (event.schemaVersion <= 0) {
            return Result.failure(IllegalArgumentException("schemaVersion must be positive"))
        }

        val accepted = event.copy(status = SyncEventStatus.ACCEPTED)
        events[event.eventId] = accepted
        idempotencyIndex[event.idempotencyKey] = event.eventId

        // Apply to in-memory state. In production this would be a database transaction.
        val applied = accepted.copy(status = SyncEventStatus.APPLIED, appliedAt = System.currentTimeMillis())
        events[event.eventId] = applied

        logger.info(
            "Stored sync event {} for store {} (entity {} {})",
            event.eventId, event.storeId, event.entityType, event.operation
        )
        return Result.success(applied)
    }

    /** Retrieve a single event by its event ID. */
    fun getEvent(eventId: String): SyncEvent? = events[eventId]

    /**
     * Query events with optional filters. All filters are ANDed together.
     * Results are sorted by createdAt descending (newest first).
     */
    fun queryEvents(
        storeId: String? = null,
        status: SyncEventStatus? = null,
        fromTimestamp: Long? = null,
        toTimestamp: Long? = null
    ): List<SyncEvent> {
        return events.values.asSequence()
            .filter { storeId == null || it.storeId == storeId }
            .filter { status == null || it.status == status }
            .filter { fromTimestamp == null || it.createdAt >= fromTimestamp }
            .filter { toTimestamp == null || it.createdAt <= toTimestamp }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    /**
     * Get pending events for a store that the POS should pull and apply.
     * Returns events in status ACCEPTED or PENDING, sorted by createdAt ascending
     * so the POS can apply them in order.
     */
    fun getPendingEvents(storeId: String): List<SyncEvent> {
        return events.values.asSequence()
            .filter { it.storeId == storeId }
            .filter { it.status == SyncEventStatus.PENDING || it.status == SyncEventStatus.ACCEPTED }
            .sortedBy { it.createdAt }
            .toList()
    }

    /**
     * Acknowledge events as processed by the POS. Events are moved to APPLIED status.
     * Returns the number of events actually acknowledged.
     */
    fun acknowledgeEvents(storeId: String, eventIds: List<String>): Result<Int> {
        var ackCount = 0
        eventIds.forEach { eventId ->
            val event = events[eventId]
            if (event != null && event.storeId == storeId) {
                events[eventId] = event.copy(
                    status = SyncEventStatus.APPLIED,
                    appliedAt = System.currentTimeMillis()
                )
                ackCount++
            } else {
                logger.warn("Acknowledge failed for event {}: not found or wrong store", eventId)
            }
        }
        return Result.success(ackCount)
    }

    /**
     * Store a conflict resolution choice for an event. The event must exist.
     * After resolution the event status becomes RESOLVED.
     */
    fun resolveEvent(eventId: String, resolution: String, localVersionJson: String?): Result<SyncEvent> {
        val event = events[eventId]
            ?: return Result.failure(NoSuchElementException("Event $eventId not found"))

        val updated = event.copy(
            status = SyncEventStatus.RESOLVED,
            resolvedAt = System.currentTimeMillis(),
            resolution = resolution,
            serverVersionJson = localVersionJson ?: event.serverVersionJson
        )
        events[eventId] = updated
        logger.info("Resolved event {} with resolution {}", eventId, resolution)
        return Result.success(updated)
    }

    /** Total count of events currently in the store. */
    fun count(): Int = events.size
}
