package com.enterprise.pos.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING','FAILED') AND nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT :batchSize")
    suspend fun pendingDue(now: Long, batchSize: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING','FAILED','IN_FLIGHT')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE status = 'CONFLICT' ORDER BY createdAt ASC")
    fun observeConflicts(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE id = :id")
    suspend fun get(id: String): SyncOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: SyncOutboxEntity)

    @Query("UPDATE sync_outbox SET status = :status, attemptCount = attemptCount + 1, lastError = :err, nextAttemptAt = :nextAt WHERE id = :id")
    suspend fun markFailed(id: String, status: String, err: String, nextAt: Long)

    @Query("UPDATE sync_outbox SET status = :status WHERE id = :id")
    suspend fun markStatus(id: String, status: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT MAX(createdAt) FROM sync_outbox WHERE status = 'ACKNOWLEDGED'")
    suspend fun lastAcknowledgedAt(): Long?

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'ACKNOWLEDGED' AND createdAt >= :since")
    suspend fun acknowledgedSinceCount(since: Long): Int
}
