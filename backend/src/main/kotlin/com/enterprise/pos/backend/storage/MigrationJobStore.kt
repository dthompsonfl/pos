package com.enterprise.pos.backend.storage

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Status of a migration job.
 *
 * PENDING   — created but not yet started
 * RUNNING   — worker is actively processing records
 * COMPLETED — all records processed, no unresolved conflicts
 * FAILED    — unrecoverable error stopped the job
 * CANCELLED — operator cancelled before completion
 */
enum class JobStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

/**
 * A conflict detected during migration where the external record cannot be
 * automatically merged with existing data.
 */
@Serializable
data class MigrationConflict(
    val id: String,
    val externalId: String,
    val reason: String,
    var resolution: String? = null
)

/**
 * Represents a migration job from an external provider (Shopify, Square, etc.)
 * into the Enterprise POS catalog and customer database.
 */
@Serializable
data class MigrationJob(
    val id: String,
    val provider: String,
    val status: JobStatus,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val totalRecords: Int = 0,
    val processedRecords: Int = 0,
    val failedRecords: Int = 0,
    val conflicts: List<MigrationConflict> = emptyList(),
    val errorMessage: String? = null
)

/**
 * In-memory store for migration jobs. Thread-safe via ConcurrentHashMap.
 *
 * **Design note for production:**
 * Replace with a database table (e.g., migration_jobs) that supports optimistic
 * locking or row-level updates so the worker and API can mutate jobs safely.
 */
class MigrationJobStore {

    private val jobs = ConcurrentHashMap<String, MigrationJob>()
    private val logger = org.slf4j.LoggerFactory.getLogger(MigrationJobStore::class.java)

    /** Create a new migration job and store it in PENDING status. */
    fun createJob(provider: String, totalRecords: Int): MigrationJob {
        val job = MigrationJob(
            id = java.util.UUID.randomUUID().toString(),
            provider = provider,
            status = JobStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            totalRecords = totalRecords
        )
        jobs[job.id] = job
        logger.info("Created migration job {} for provider {} ({} records)", job.id, provider, totalRecords)
        return job
    }

    /** Retrieve a job by ID. */
    fun getJob(id: String): MigrationJob? = jobs[id]

    /** List all jobs, newest first. */
    fun listJobs(): List<MigrationJob> =
        jobs.values.sortedByDescending { it.createdAt }

    /** Replace the stored job with an updated copy. */
    fun updateJob(job: MigrationJob) {
        jobs[job.id] = job
    }

    /** Cancel a pending or running job. Returns the updated job or a failure. */
    fun cancelJob(id: String): Result<MigrationJob> {
        val job = jobs[id]
            ?: return Result.failure(NoSuchElementException("Job $id not found"))

        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED) {
            return Result.failure(IllegalStateException("Cannot cancel a job that is already ${job.status}"))
        }

        val updated = job.copy(status = JobStatus.CANCELLED, completedAt = System.currentTimeMillis())
        jobs[id] = updated
        logger.info("Cancelled migration job {}", id)
        return Result.success(updated)
    }

    /** Apply a resolution choice to a specific conflict within a job. */
    fun resolveConflict(
        jobId: String,
        conflictId: String,
        resolution: String
    ): Result<MigrationJob> {
        val job = jobs[jobId]
            ?: return Result.failure(NoSuchElementException("Job $jobId not found"))

        val conflictIndex = job.conflicts.indexOfFirst { it.id == conflictId }
        if (conflictIndex < 0) {
            return Result.failure(NoSuchElementException("Conflict $conflictId not found in job $jobId"))
        }

        val updatedConflicts = job.conflicts.toMutableList()
        updatedConflicts[conflictIndex] = updatedConflicts[conflictIndex].copy(resolution = resolution)

        // If all conflicts are resolved and job was running, mark completed
        val allResolved = updatedConflicts.all { it.resolution != null }
        val newStatus = when {
            job.status == JobStatus.RUNNING && allResolved && job.processedRecords >= job.totalRecords ->
                JobStatus.COMPLETED
            else -> job.status
        }
        val completedAt = if (newStatus == JobStatus.COMPLETED) System.currentTimeMillis() else job.completedAt

        val updated = job.copy(
            conflicts = updatedConflicts,
            status = newStatus,
            completedAt = completedAt
        )
        jobs[jobId] = updated
        logger.info("Resolved conflict {} in job {} with resolution {}", conflictId, jobId, resolution)
        return Result.success(updated)
    }

    /** Total count of jobs currently in the store. */
    fun count(): Int = jobs.size
}
