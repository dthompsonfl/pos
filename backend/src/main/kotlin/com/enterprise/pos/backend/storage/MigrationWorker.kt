package com.enterprise.pos.backend.storage

import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Background worker that simulates migration job progress for development and demo.
 *
 * In production this would be replaced by a real worker that:
 *   1. Fetches paginated data from the external provider API using the stored token
 *   2. Transforms and validates each record
 *   3. Upserts into the POS database inside a transaction
 *   4. Emits progress events to a message queue (Redis, RabbitMQ, etc.)
 *   5. Handles rate limits and retries with exponential backoff
 */
class MigrationWorker(
    private val jobStore: MigrationJobStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(MigrationWorker::class.java)

    /**
     * Start processing a migration job asynchronously. The job status is updated
     * in the store as the worker progresses.
     */
    fun startJob(jobId: String) {
        scope.launch {
            val job = jobStore.getJob(jobId)
            if (job == null) {
                logger.error("Cannot start job {}: not found", jobId)
                return@launch
            }
            if (job.status != JobStatus.PENDING) {
                logger.warn("Job {} is not pending (status: {}), skipping", jobId, job.status)
                return@launch
            }

            jobStore.updateJob(
                job.copy(status = JobStatus.RUNNING, startedAt = System.currentTimeMillis())
            )
            logger.info("Started migration job {} ({} records)", jobId, job.totalRecords)

            val steps = job.totalRecords.coerceAtLeast(1)
            for (i in 1..steps) {
                delay(300) // Simulate work per record

                val currentJob = jobStore.getJob(jobId) ?: break
                if (currentJob.status == JobStatus.CANCELLED) {
                    logger.info("Job {} was cancelled at step {}", jobId, i)
                    break
                }

                // Simulate occasional failures and conflicts
                val isConflict = Random.nextInt(100) < 5  // 5% conflict
                val isFailure = Random.nextInt(100) < 2   // 2% failure

                when {
                    isFailure -> {
                        jobStore.updateJob(
                            currentJob.copy(
                                failedRecords = currentJob.failedRecords + 1,
                                processedRecords = i
                            )
                        )
                    }
                    isConflict -> {
                        val conflict = MigrationConflict(
                            id = java.util.UUID.randomUUID().toString(),
                            externalId = "ext-$i",
                            reason = listOf(
                                "Duplicate SKU detected",
                                "Category mapping missing",
                                "Tax rate mismatch",
                                "Customer email already exists"
                            ).random()
                        )
                        val updatedConflicts = currentJob.conflicts.toMutableList().apply { add(conflict) }
                        jobStore.updateJob(
                            currentJob.copy(
                                conflicts = updatedConflicts,
                                processedRecords = i
                            )
                        )
                    }
                    else -> {
                        jobStore.updateJob(currentJob.copy(processedRecords = i))
                    }
                }
            }

            val finalJob = jobStore.getJob(jobId) ?: return@launch
            if (finalJob.status == JobStatus.CANCELLED) return@launch

            val allResolved = finalJob.conflicts.all { it.resolution != null }
            val newStatus = if (allResolved && finalJob.processedRecords >= finalJob.totalRecords) {
                JobStatus.COMPLETED
            } else if (finalJob.conflicts.isNotEmpty() && !allResolved) {
                JobStatus.RUNNING // Awaiting manual resolution
            } else {
                JobStatus.COMPLETED
            }

            jobStore.updateJob(
                finalJob.copy(
                    status = newStatus,
                    completedAt = if (newStatus == JobStatus.COMPLETED) System.currentTimeMillis() else finalJob.completedAt
                )
            )
            logger.info("Migration job {} finished with status {}", jobId, newStatus)
        }
    }

    fun shutdown() {
        scope.cancel()
        logger.info("Migration worker shut down")
    }
}
