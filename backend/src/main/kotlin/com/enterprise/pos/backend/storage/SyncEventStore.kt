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
 * constraint on merchant_id + idempotency_key.
 */
@Serializable
@Suppress("LongParameterList")
data class SyncEvent(
    val eventId: String,
    val merchantId: String = "unknown",
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
 *   - UNIQUE INDEX on merchant_id + idempotency_key
 *   - INDEX on merchant_id + store_id + status for queue queries
 */
class SyncEventStore {

    private val events = ConcurrentHashMap<String, SyncEvent>() // eventId -> SyncEvent
    private val idempotencyIndex = ConcurrentHashMap<String, String>() // merchant:idempotencyKey -> eventId
    private val logger = org.slf4j.LoggerFactory.getLogger(SyncEventStore::class.java)

    fun storeEvent(event: SyncEvent): Result<SyncEvent> {
        if (event.merchantId.isBlank() || event.storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("merchantId and storeId are required"))
        }
        if (event.entityType.isBlank() || event.entityId.isBlank() || event.operation.isBlank()) {
            return Result.failure(IllegalArgumentException("entityType, entityId, and operation are required"))
        }
        if (event.schemaVersion <= 0) {
            return Result.failure(IllegalArgumentException("schemaVersion must be positive"))
        }

        val scopedIdempotencyKey = idempotencyScope(event.merchantId, event.idempotencyKey)
        val existingEventId = idempotencyIndex[scopedIdempotencyKey]
        if (existingEventId != null) {
            val existing = events[existingEventId]
            return if (existing != null) {
                logger.info(
                    "Duplicate idempotency key {} for merchant {} event {}",
                    event.idempotencyKey,
                    event.merchantId,
                    existingEventId
                )
                Result.success(existing.copy(status = SyncEventStatus.DUPLICATE))
            } else {
                Result.failure(IllegalStateException("Idempotency index corrupted for key $scopedIdempotencyKey"))
            }
        }

        val accepted = event.copy(status = SyncEventStatus.ACCEPTED)
        events[event.eventId] = accepted
        idempotencyIndex[scopedIdempotencyKey] = event.eventId

        // Apply to in-memory state. In production this would be a database transaction.
        val applied = accepted.copy(status = SyncEventStatus.APPLIED, appliedAt = System.currentTimeMillis())
        events[event.eventId] = applied

        logger.info(
            "Stored sync event {} for merchant {} store {} (entity {} {})",
            event.eventId,
            event.merchantId,
            event.storeId,
            event.entityType,
            event.operation
        )
        return Result.success(applied)
    }

    /** Retrieve a single event by its event ID. */
    fun getEvent(eventId: String): SyncEvent? = events[eventId]

    fun queryEvents(
        merchantId: String? = null,
        storeId: String? = null,
        status: SyncEventStatus? = null,
        fromTimestamp: Long? = null,
        toTimestamp: Long? = null
    ): List<SyncEvent> {
        return events.values.asSequence()
            .filter { merchantId == null || it.merchantId == merchantId }
            .filter { storeId == null || it.storeId == storeId }
            .filter { status == null || it.status == status }
            .filter { fromTimestamp == null || it.createdAt >= fromTimestamp }
            .filter { toTimestamp == null || it.createdAt <= toTimestamp }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun getPendingEvents(storeId: String, merchantId: String? = null): List<SyncEvent> {
        return events.values.asSequence()
            .filter { merchantId == null || it.merchantId == merchantId }
            .filter { it.storeId == storeId }
            .filter { it.status == SyncEventStatus.PENDING || it.status == SyncEventStatus.ACCEPTED }
            .sortedBy { it.createdAt }
            .toList()
    }

    fun acknowledgeEvents(storeId: String, eventIds: List<String>, merchantId: String? = null): Result<Int> {
        var ackCount = 0
        eventIds.forEach { eventId ->
            val event = events[eventId]
            if (event != null && event.storeId == storeId && (merchantId == null || event.merchantId == merchantId)) {
                events[eventId] = event.copy(
                    status = SyncEventStatus.APPLIED,
                    appliedAt = System.currentTimeMillis()
                )
                ackCount++
            } else {
                logger.warn("Acknowledge failed for event {}: not found, wrong store, or wrong merchant", eventId)
            }
        }
        return Result.success(ackCount)
    }

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

    private fun idempotencyScope(merchantId: String, idempotencyKey: String): String =
        "$merchantId:$idempotencyKey"
}
