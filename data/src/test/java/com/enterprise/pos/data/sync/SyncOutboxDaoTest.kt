package com.enterprise.pos.data.sync

import com.enterprise.pos.core.StoreId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class SyncOutboxDaoTest {

    private fun fakeDao() = object : SyncOutboxDao {
        private val store = mutableMapOf<String, SyncOutboxEntity>()

        override suspend fun pendingDue(now: Long, batchSize: Int): List<SyncOutboxEntity> {
            return store.values.filter { it.status in setOf("PENDING", "FAILED") && it.nextAttemptAt <= now }
                .sortedBy { it.createdAt }
                .take(batchSize)
        }

        override fun observePendingCount() = flowOf(store.count { it.value.status in setOf("PENDING", "FAILED", "IN_FLIGHT") })
        override fun observeConflictCount() = flowOf(store.count { it.value.status == "CONFLICT" })
        override fun observeConflicts() = flowOf(store.values.filter { it.status == "CONFLICT" }.sortedBy { it.createdAt })
        override suspend fun get(id: String) = store[id]

        override suspend fun upsert(event: SyncOutboxEntity) {
            store[event.id] = event
        }

        override suspend fun markFailed(id: String, status: String, err: String, nextAt: Long) {
            store[id] = store.getValue(id).copy(status = status, attemptCount = store.getValue(id).attemptCount + 1, lastError = err, nextAttemptAt = nextAt)
        }

        override suspend fun markStatus(id: String, status: String) {
            store[id] = store.getValue(id).copy(status = status)
        }

        override suspend fun delete(id: String) {
            store.remove(id)
        }

        override suspend fun lastAcknowledgedAt(): Long? {
            return store.values.filter { it.status == "ACKNOWLEDGED" }.maxOfOrNull { it.createdAt }
        }

        override suspend fun acknowledgedSinceCount(since: Long): Int {
            return store.count { it.value.status == "ACKNOWLEDGED" && it.value.createdAt >= since }
        }
    }

    private val storeId = StoreId("store-1")
    private val now = 1700000000000L

    @Test
    fun `enqueue stores entity with PENDING status`() = runBlocking {
        val dao = fakeDao()
        val entity = SyncOutboxEntity.create(
            storeId = storeId,
            entityType = "orders",
            entityId = "order-1",
            operation = "UPSERT",
            createdAt = now
        )
        dao.upsert(entity)
        assertThat(dao.get(entity.id)).isNotNull()
        assertThat(dao.get(entity.id)?.status).isEqualTo("PENDING")
    }

    @Test
    fun `pendingDue returns only due pending items`() = runBlocking {
        val dao = fakeDao()
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now).copy(nextAttemptAt = now - 1))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o2", createdAt = now).copy(nextAttemptAt = now + 1))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o3", createdAt = now).copy(nextAttemptAt = now - 1, status = "ACKNOWLEDGED"))
        val pending = dao.pendingDue(now, 50)
        assertThat(pending).hasSize(1)
        assertThat(pending[0].entityId).isEqualTo("o1")
    }

    @Test
    fun `markFailed increments attempt count and updates status`() = runBlocking {
        val dao = fakeDao()
        val entity = SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now)
        dao.upsert(entity)
        dao.markFailed(entity.id, "FAILED", "Network error", now + 1000)
        val updated = dao.get(entity.id)!!
        assertThat(updated.status).isEqualTo("FAILED")
        assertThat(updated.attemptCount).isEqualTo(1)
        assertThat(updated.lastError).isEqualTo("Network error")
        assertThat(updated.nextAttemptAt).isEqualTo(now + 1000)
    }

    @Test
    fun `markStatus transitions to ACKNOWLEDGED`() = runBlocking {
        val dao = fakeDao()
        val entity = SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now)
        dao.upsert(entity)
        dao.markStatus(entity.id, "ACKNOWLEDGED")
        assertThat(dao.get(entity.id)?.status).isEqualTo("ACKNOWLEDGED")
    }

    @Test
    fun `delete removes entity`() = runBlocking {
        val dao = fakeDao()
        val entity = SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now)
        dao.upsert(entity)
        dao.delete(entity.id)
        assertThat(dao.get(entity.id)).isNull()
    }

    @Test
    fun `lastAcknowledgedAt returns latest timestamp`() = runBlocking {
        val dao = fakeDao()
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now).copy(status = "ACKNOWLEDGED"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o2", createdAt = now + 1000).copy(status = "ACKNOWLEDGED"))
        assertThat(dao.lastAcknowledgedAt()).isEqualTo(now + 1000)
    }

    @Test
    fun `acknowledgedSinceCount returns correct count`() = runBlocking {
        val dao = fakeDao()
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now).copy(status = "ACKNOWLEDGED"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o2", createdAt = now + 1000).copy(status = "ACKNOWLEDGED"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o3", createdAt = now).copy(status = "PENDING"))
        assertThat(dao.acknowledgedSinceCount(now)).isEqualTo(2)
        assertThat(dao.acknowledgedSinceCount(now + 500)).isEqualTo(1)
    }

    @Test
    fun `observePendingCount reflects pending and failed and in-flight`() = runBlocking {
        val dao = fakeDao()
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now).copy(status = "PENDING"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o2", createdAt = now).copy(status = "FAILED"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o3", createdAt = now).copy(status = "IN_FLIGHT"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o4", createdAt = now).copy(status = "ACKNOWLEDGED"))
        dao.observePendingCount().collect { count ->
            assertThat(count).isEqualTo(3)
        }
    }

    @Test
    fun `observeConflictCount returns only conflicts`() = runBlocking {
        val dao = fakeDao()
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", createdAt = now).copy(status = "CONFLICT"))
        dao.upsert(SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o2", createdAt = now).copy(status = "PENDING"))
        dao.observeConflictCount().collect { count ->
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    fun `idempotencyKey is unique per entity`() = runBlocking {
        val e1 = SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", operation = "UPSERT", createdAt = now)
        val e2 = SyncOutboxEntity.create(storeId, entityType = "orders", entityId = "o1", operation = "UPSERT", createdAt = now)
        assertThat(e1.idempotencyKey).isNotEqualTo(e2.idempotencyKey)
    }
}
