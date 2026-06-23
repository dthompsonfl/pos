package com.enterprise.pos.domain.security

import com.google.common.truth.Truth.assertThat
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.AuditLogEntry
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.repository.AuditLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class AuditLoggerTest {

    private val fakeRepo = object : AuditLogRepository {
        val entries = mutableListOf<AuditLogEntry>()
        override fun observe(storeId: StoreId, action: AuditAction?, limit: Int): Flow<List<AuditLogEntry>> = flowOf(entries)
        override fun observeForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntry>> = flowOf(entries)
        override suspend fun log(entry: AuditLogEntry): com.enterprise.pos.core.Result<Unit> {
            entries.add(entry)
            return com.enterprise.pos.core.Result.success(Unit)
        }
        override suspend fun logAction(
            storeId: StoreId, registerId: RegisterId?, employeeId: EmployeeId, employeeName: String,
            action: AuditAction, entityType: String, entityId: String,
            beforeJson: String?, afterJson: String?, reason: String?
        ): com.enterprise.pos.core.Result<Unit> {
            entries.add(
                AuditLogEntry(
                    id = com.enterprise.pos.core.Id.random(), storeId = storeId, registerId = registerId,
                    employeeId = employeeId, employeeName = employeeName, action = action,
                    entityType = entityType, entityId = entityId, beforeJson = beforeJson,
                    afterJson = afterJson, reason = reason, timestamp = System.currentTimeMillis()
                )
            )
            return com.enterprise.pos.core.Result.success(Unit)
        }
        override suspend fun export(storeId: StoreId, from: Long, to: Long): com.enterprise.pos.core.Result<List<AuditLogEntry>> =
            com.enterprise.pos.core.Result.success(entries)
    }

    private val logger = AuditLogger(fakeRepo)
    private val storeId = StoreId("store-1")
    private val registerId = RegisterId("reg-1")
    private val employeeId = EmployeeId("emp-1")

    @Test
    fun logOrderCreatedAddsEntry() {
        runBlocking {
            logger.logOrderAction(OrderId("order-1"), "ORDER_CREATED", employeeId, storeId)
            assertThat(fakeRepo.entries).hasSize(1)
            assertThat(fakeRepo.entries[0].action).isEqualTo(AuditAction.ORDER_CREATED)
        }
    }

    @Test
    fun logPaymentCapturedAddsEntryWithAmount() {
        runBlocking {
            logger.logPaymentAction(PaymentId("pay-1"), "PAYMENT_CAPTURED", Money.ofMinor(1250), employeeId, storeId)
            assertThat(fakeRepo.entries).hasSize(1)
            assertThat(fakeRepo.entries[0].action).isEqualTo(AuditAction.PAYMENT_CAPTURED)
            assertThat(fakeRepo.entries[0].afterJson).contains("1250")
        }
    }

    @Test
    fun logOrderVoidedIncludesReason() {
        runBlocking {
            logger.logVoidAction(OrderId("order-1"), "Customer request", employeeId, storeId)
            assertThat(fakeRepo.entries[0].action).isEqualTo(AuditAction.ORDER_VOIDED)
            // Note: AuditLogger currently maps details to afterJson, and reason parameter is null in toAuditLogEntry
            // We should check afterJson if we really want to verify the reason is logged
            assertThat(fakeRepo.entries[0].afterJson).contains("Customer request")
        }
    }

    @Test
    fun logDiscountAppliedRecordsAmount() {
        runBlocking {
            logger.logDiscountApplied(OrderId("order-1"), Money.of(5.0), employeeId = employeeId, storeId = storeId)
            assertThat(fakeRepo.entries[0].afterJson).contains("5.00")
        }
    }

    @Test
    fun logLoginAddsEntry() {
        runBlocking {
            logger.logLogin(employeeId, true, storeId = storeId, registerId = registerId)
            assertThat(fakeRepo.entries).hasSize(1)
            assertThat(fakeRepo.entries[0].action).isEqualTo(AuditAction.EMPLOYEE_LOGIN)
        }
    }

    @Test
    fun logBatchInsertsMultipleEntries() {
        runBlocking {
            val entry1 = AuditLogEntry(
                id = com.enterprise.pos.core.Id.random(), storeId = storeId, registerId = registerId,
                employeeId = employeeId, employeeName = "", action = AuditAction.ORDER_CREATED,
                entityType = "Order", entityId = "o1", timestamp = System.currentTimeMillis()
            )
            val entry2 = AuditLogEntry(
                id = com.enterprise.pos.core.Id.random(), storeId = storeId, registerId = registerId,
                employeeId = employeeId, employeeName = "", action = AuditAction.ORDER_PAID,
                entityType = "Order", entityId = "o2", timestamp = System.currentTimeMillis()
            )
            logger.logBatch(entry1)
            logger.logBatch(entry2)
            logger.flushBatch()
            assertThat(fakeRepo.entries).hasSize(2)
        }
    }

    @Test
    fun actionsAreMappedCorrectly() {
        runBlocking {
            logger.logOrderAction(OrderId("o1"), "ORDER_CREATED", employeeId, storeId)
            logger.logVoidAction(OrderId("o1"), "Reason", employeeId, storeId)
            logger.logPaymentAction(PaymentId("p1"), "PAYMENT_CAPTURED", Money.of(10.0), employeeId, storeId)

            val actions = fakeRepo.entries.map { it.action }
            assertThat(actions).containsExactly(
                AuditAction.ORDER_CREATED,
                AuditAction.ORDER_VOIDED,
                AuditAction.PAYMENT_CAPTURED
            )
        }
    }
}

class SessionManagerTest {

    private val manager = SessionManager()
    private val employeeId = EmployeeId("emp-1")
    private val employeeName = "Test"
    private val deviceId = "device-1"

    @Test
    fun startSessionReturnsActiveSession() {
        val session = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, deviceId)
        assertThat(session.employeeId).isEqualTo(employeeId)
        assertThat(manager.isSessionValid(session.sessionId)).isTrue()
    }

    @Test
    fun getSessionReturnsNullForInvalidId() {
        assertThat(manager.getSession("invalid")).isNull()
    }

    @Test
    fun refreshSessionUpdatesLastActivity() {
        val session = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, deviceId)
        val originalActivity = session.lastActivityAt
        
        // Wait a bit to ensure timestamp changes
        Thread.sleep(10)
        
        val refreshed = manager.refreshSession(session.sessionId)
        assertThat(refreshed).isNotNull()
        assertThat(refreshed!!.lastActivityAt).isGreaterThan(originalActivity)
    }

    @Test
    fun endSessionInvalidatesId() {
        val session = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, deviceId)
        manager.endSession(session.sessionId)
        assertThat(manager.isSessionValid(session.sessionId)).isFalse()
    }

    @Test
    fun invalidateEmployeeSessionsClearsAll() {
        manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, "d1")
        manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, "d2")
        
        assertThat(manager.activeSessionCount(employeeId)).isEqualTo(2)
        
        manager.invalidateEmployeeSessions(employeeId)
        assertThat(manager.activeSessionCount(employeeId)).isEqualTo(0)
    }

    @Test
    fun concurrentLimitEnforced() {
        // Max concurrent is 2
        val s1 = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, "d1")
        val s2 = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, "d2")
        val s3 = manager.startSession(employeeId, employeeName, EmployeeRole.CASHIER, "d3")
        
        assertThat(manager.isSessionValid(s1.sessionId)).isFalse() // Oldest should be gone
        assertThat(manager.isSessionValid(s2.sessionId)).isTrue()
        assertThat(manager.isSessionValid(s3.sessionId)).isTrue()
        assertThat(manager.activeSessionCount(employeeId)).isEqualTo(2)
    }
}
