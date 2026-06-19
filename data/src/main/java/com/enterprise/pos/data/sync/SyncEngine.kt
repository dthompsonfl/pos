package com.enterprise.pos.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.enterprise.pos.core.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Sync engine — drains the [SyncOutboxEntity] queue and POSTs each event to the backend.
 *
 * Architecture:
 *  - Every state-changing Room transaction enqueues a SyncOutboxEntity atomically.
 *  - [SyncWorker] (WorkManager, periodic 15 min + on-demand) drains pending events.
 *  - Each event is sent with its idempotencyKey so the server can dedup.
 *  - On success, the event is deleted.
 *  - On failure, attemptCount increments and nextAttemptAt is set with exponential backoff.
 *  - On conflict, the event is marked CONFLICT and surfaces to a conflict-resolution UI.
 *
 * The actual HTTP transport is provided by [SyncBackend] which is implemented against
 * your real backend. NoopSyncBackend is bound only in debug; release binds HttpSyncBackend.
 */
class SyncEngine(
    private val outboxDao: SyncOutboxDao,
    private val logger: Logger,
    private val backend: SyncBackend,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    val pendingCount: Flow<Int> = outboxDao.observePendingCount()
    val conflictCount: Flow<Int> = outboxDao.observeConflictCount()
    val conflicts: Flow<List<SyncOutboxEntity>> = outboxDao.observeConflicts()

    suspend fun lastSyncAt(): Long? = outboxDao.lastAcknowledgedAt()

    /** Drain a batch of pending events. Returns the number successfully processed. */
    suspend fun drainBatch(maxBatchSize: Int = 50): DrainResult {
        val due = outboxDao.pendingDue(now(), maxBatchSize)
        if (due.isEmpty()) return DrainResult(0, 0, 0)
        var succeeded = 0
        var failed = 0
        var conflicts = 0
        for (event in due) {
            // Mark in-flight so a parallel worker doesn't pick it up.
            outboxDao.markStatus(event.id, "IN_FLIGHT")
            try {
                val response = backend.send(event)
                when (response) {
                    is SyncBackendResponse.Accepted, is SyncBackendResponse.Duplicate -> {
                        outboxDao.delete(event.id)
                        succeeded++
                    }
                    is SyncBackendResponse.Rejected -> {
                        // Permanent failure — record error but keep in queue for inspection.
                        outboxDao.markFailed(event.id, "FAILED", response.reason, nextBackoff(event.attemptCount))
                        failed++
                    }
                    is SyncBackendResponse.Conflict -> {
                        outboxDao.markStatus(event.id, "CONFLICT")
                        conflicts++
                    }
                    is SyncBackendResponse.RequiresManualResolution -> {
                        outboxDao.markStatus(event.id, "CONFLICT")
                        conflicts++
                    }
                }
            } catch (t: Throwable) {
                logger.w(TAG, "Sync send failed for ${event.id}", t)
                outboxDao.markFailed(event.id, "FAILED", t.message ?: t::class.java.simpleName, nextBackoff(event.attemptCount))
                failed++
            }
        }
        return DrainResult(succeeded, failed, conflicts)
    }

    /** Resolve a conflict by sending the user's resolution to the backend. */
    suspend fun resolveConflict(eventId: String, resolution: ConflictResolution): Boolean {
        val event = outboxDao.get(eventId) ?: return false
        return try {
            val response = backend.resolve(event, resolution)
            if (response is SyncBackendResponse.Accepted) {
                outboxDao.delete(eventId)
                true
            } else false
        } catch (t: Throwable) {
            logger.w(TAG, "Conflict resolution failed for $eventId", t)
            false
        }
    }

    private fun nextBackoff(attempt: Int): Long {
        // Exponential backoff: 30s, 1m, 2m, 4m, 8m, 16m, 30m cap
        val seconds = (30L * (1L shl attempt.coerceAtMost(6)))
        return now() + seconds.coerceAtMost(1800L) * 1000L
    }

    data class DrainResult(val succeeded: Int, val failed: Int, val conflicts: Int)

    companion object {
        private const val TAG = "SyncEngine"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pos-sync", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val result = syncEngine.drainBatch()
        return if (result.failed > 0) Result.retry() else Result.success()
    }
}

/** Backend contract for sending outbox events. Implementations live in :data. */
interface SyncBackend {
    suspend fun send(event: SyncOutboxEntity): SyncBackendResponse
    suspend fun resolve(event: SyncOutboxEntity, resolution: ConflictResolution): SyncBackendResponse
}

sealed class SyncBackendResponse {
    data object Accepted : SyncBackendResponse()
    data class Duplicate(val existingId: String) : SyncBackendResponse()
    data class Rejected(val reason: String) : SyncBackendResponse()
    data class Conflict(val serverVersionJson: String) : SyncBackendResponse()
    data class RequiresManualResolution(val reason: String) : SyncBackendResponse()
}

enum class ConflictResolution { KEEP_LOCAL, KEEP_REMOTE, MERGE, SKIP }

/** No-op backend for unit tests and offline-only deployments. */
object NoopSyncBackend : SyncBackend {
    override suspend fun send(event: SyncOutboxEntity) = SyncBackendResponse.Accepted
    override suspend fun resolve(event: SyncOutboxEntity, resolution: ConflictResolution) = SyncBackendResponse.Accepted
}
