package com.enterprise.pos.backend.storage

import java.util.concurrent.ConcurrentHashMap

data class IdempotencyRecord(
    val key: String,
    val operation: String, // e.g., "create-payment-intent"
    val paramsHash: String,
    val responseJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

class IdempotencyStore(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val records = ConcurrentHashMap<String, IdempotencyRecord>()

    fun get(key: String): IdempotencyRecord? {
        purgeExpired()
        return records[key]?.takeUnless { it.isExpired(System.currentTimeMillis()) }
            ?: run {
                records.remove(key)
                null
            }
    }

    fun put(record: IdempotencyRecord) {
        purgeExpired()
        records[record.key] = record
    }

    fun contains(key: String): Boolean = get(key) != null

    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        records.entries.removeIf { it.value.isExpired(now) }
    }

    private fun IdempotencyRecord.isExpired(now: Long): Boolean =
        now - createdAt > ttlMillis

    companion object {
        private const val DEFAULT_TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}
