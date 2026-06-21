package com.enterprise.pos.backend.storage

import java.util.concurrent.ConcurrentHashMap

data class IdempotencyRecord(
    val key: String,
    val operation: String, // e.g., "create-payment-intent"
    val paramsHash: String,
    val responseJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

class IdempotencyStore {
    private val records = ConcurrentHashMap<String, IdempotencyRecord>()

    fun get(key: String): IdempotencyRecord? = records[key]
    fun put(record: IdempotencyRecord) { records[record.key] = record }
    fun contains(key: String): Boolean = records.containsKey(key)
}
