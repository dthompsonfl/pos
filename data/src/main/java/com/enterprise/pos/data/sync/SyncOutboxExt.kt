package com.enterprise.pos.data.sync

import com.enterprise.pos.core.StoreId

/**
 * Helper extension to enqueue a sync outbox event with minimal boilerplate.
 * Repositories should call this after every state-changing DB operation.
 */
suspend fun SyncOutboxDao.enqueue(
    storeId: StoreId,
    entityType: String,
    entityId: String,
    operation: String = "UPSERT",
    schemaVersion: Int = 1,
    payloadJson: String = "{}",
    registerId: String? = null,
    employeeId: String? = null,
    createdAt: Long = System.currentTimeMillis()
) {
    upsert(
        SyncOutboxEntity.create(
            storeId = storeId,
            registerId = registerId,
            employeeId = employeeId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            schemaVersion = schemaVersion,
            payloadJson = payloadJson,
            createdAt = createdAt
        )
    )
}
