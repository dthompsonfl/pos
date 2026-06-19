package com.enterprise.pos.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable sync outbox — every state-changing DB transaction enqueues a [SyncOutboxEntity]
 * in the SAME Room transaction. A WorkManager job drains the outbox and POSTs each event
 * to the backend with idempotency keys; failures are retried with exponential backoff.
 *
 * Status transitions:
 *   PENDING → IN_FLIGHT → ACKNOWLEDGED (deleted) | FAILED (retryable) | CONFLICT (manual)
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index("status"), Index("nextAttemptAt"), Index("entityType"), Index("idempotencyKey", unique = true)]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,                // UUID
    val storeId: String,
    val registerId: String?,
    val employeeId: String?,
    val entityType: String,                    // orders | order_lines | payments | inventory | customers | etc.
    val entityId: String,
    val operation: String,                     // UPSERT | DELETE
    val schemaVersion: Int = 1,
    val idempotencyKey: String,                // server-side dedup key
    val payloadJson: String,                   // serialized snapshot
    val createdAt: Long,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long,
    val lastError: String? = null,
    val status: String = "PENDING"             // PENDING | IN_FLIGHT | ACKNOWLEDGED | FAILED | CONFLICT
)
