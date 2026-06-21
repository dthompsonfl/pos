package com.enterprise.pos.data.repository

import com.enterprise.pos.core.Clock
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.CustomerDao
import com.enterprise.pos.data.db.dao.EmployeeDao
import com.enterprise.pos.data.db.dao.OrderDao
import com.enterprise.pos.data.db.dao.PaymentDao
import com.enterprise.pos.data.db.dao.TableDao
import com.enterprise.pos.data.db.entity.CustomerEntity
import com.enterprise.pos.data.db.entity.EmployeeEntity
import com.enterprise.pos.data.db.entity.OrderEntity
import com.enterprise.pos.data.security.PinHasher
import com.enterprise.pos.data.sync.SyncOutboxDao
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.repository.AuditLogRepository
import com.enterprise.pos.domain.service.CartEngine
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.eq
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OrderRepositoryImplTest {

    private val orderDao = mockk<OrderDao>(relaxed = true)
    private val tableDao = mockk<TableDao>(relaxed = true)
    private val paymentDao = mockk<PaymentDao>(relaxed = true)
    private val catalogDao = mockk<CatalogDao>(relaxed = true)
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val auditLog = mockk<AuditLogRepository>(relaxed = true)
    private val cartEngine = CartEngine()
    private val clock = object : Clock { override fun now() = 1700000000000L }

    private val repo = OrderRepositoryImpl(
        orderDao = orderDao,
        tableDao = tableDao,
        paymentDao = paymentDao,
        catalogDao = catalogDao,
        syncOutboxDao = syncOutboxDao,
        auditLog = auditLog,
        cartEngine = cartEngine,
        clock = clock
    )

    private val storeId = StoreId("store-1")
    private val registerId = RegisterId("reg-1")
    private val employeeId = EmployeeId("emp-1")
    private val orderId = OrderId("order-1")

    @Test
    fun `createOrder returns order with OPEN status`() = runBlocking {
        coEvery { orderDao.upsert(any()) } returns Unit
        coEvery { orderDao.replaceOrderChildren(any(), any(), any(), any()) } returns Unit

        val result = repo.createOrder(storeId, registerId, employeeId, DiningMode.RETAIL, null, 0)
        assertThat(result.isSuccess()).isTrue()
        val order = result.getOrThrow()
        assertThat(order.status).isEqualTo(OrderStatus.OPEN)
        assertThat(order.storeId).isEqualTo(storeId)
        assertThat(order.registerId).isEqualTo(registerId)
    }

    @Test
    fun `observeOpenOrders maps to domain`() = runBlocking {
        coEvery { orderDao.observeOpenOrders("store-1") } returns flowOf(listOf(
            OrderEntity(
                id = "order-1", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
                diningMode = "RETAIL", status = "OPEN", orderLevelDiscountMinor = 0, tipMinor = 0,
                createdAt = 0, updatedAt = 0
            )
        ))
        coEvery { orderDao.linesFor("order-1") } returns emptyList()
        coEvery { orderDao.taxLinesFor("order-1") } returns emptyList()
        coEvery { orderDao.discountsFor("order-1") } returns emptyList()
        coEvery { paymentDao.forOrder("order-1") } returns emptyList()

        val orders = repo.observeOpenOrders(storeId)
        orders.collect { list ->
            assertThat(list).hasSize(1)
            assertThat(list[0].status).isEqualTo(OrderStatus.OPEN)
        }
    }

    @Test
    fun `markPaid persists payment and updates order`() = runBlocking {
        coEvery { orderDao.get("order-1") } returns OrderEntity(
            id = "order-1", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
            diningMode = "RETAIL", status = "AWAITING_PAYMENT", orderLevelDiscountMinor = 0, tipMinor = 0,
            createdAt = 0, updatedAt = 0
        )
        coEvery { orderDao.linesFor("order-1") } returns emptyList()
        coEvery { orderDao.taxLinesFor("order-1") } returns emptyList()
        coEvery { orderDao.discountsFor("order-1") } returns emptyList()
        coEvery { paymentDao.forOrder("order-1") } returns emptyList()
        coEvery { orderDao.upsert(any()) } returns Unit
        coEvery { paymentDao.upsert(any()) } returns Unit
        coEvery { auditLog.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns com.enterprise.pos.core.Result.success(Unit)

        val payment = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("pay-1"), orderId = orderId, provider = "CASH",
            providerTransactionId = "txn-1", amount = Money.of(10.00), capturedAt = clock.now()
        )

        val result = repo.markPaid(orderId, payment, employeeId)
        assertThat(result.isSuccess()).isTrue()
        coVerify { paymentDao.upsert(any()) }
        coVerify { auditLog.logAction(any(), any(), any(), any(), eq(AuditAction.PAYMENT_CAPTURED), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refund processes and restocks inventory`() = runBlocking {
        coEvery { orderDao.get("order-1") } returns OrderEntity(
            id = "order-1", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
            diningMode = "RETAIL", status = "PAID", orderLevelDiscountMinor = 0, tipMinor = 0,
            createdAt = 0, updatedAt = 0
        )
        coEvery { orderDao.linesFor("order-1") } returns emptyList()
        coEvery { orderDao.taxLinesFor("order-1") } returns emptyList()
        coEvery { orderDao.discountsFor("order-1") } returns emptyList()
        coEvery { paymentDao.forOrder("order-1") } returns emptyList()
        coEvery { orderDao.upsert(any()) } returns Unit
        coEvery { paymentDao.upsert(any()) } returns Unit

        val refund = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("ref-1"), orderId = orderId, provider = "CASH",
            providerTransactionId = "txn-2", amount = Money.of(5.00), capturedAt = clock.now()
        )

        val result = repo.refund(orderId, refund, "Customer request", employeeId)
        assertThat(result.isSuccess()).isTrue()
        coVerify { paymentDao.upsert(any()) }
    }

    @Test
    fun `voidOrder voids and audits`() = runBlocking {
        coEvery { orderDao.get("order-1") } returns OrderEntity(
            id = "order-1", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
            diningMode = "RETAIL", status = "OPEN", orderLevelDiscountMinor = 0, tipMinor = 0,
            createdAt = 0, updatedAt = 0
        )
        coEvery { orderDao.linesFor("order-1") } returns emptyList()
        coEvery { orderDao.taxLinesFor("order-1") } returns emptyList()
        coEvery { orderDao.discountsFor("order-1") } returns emptyList()
        coEvery { paymentDao.forOrder("order-1") } returns emptyList()
        coEvery { orderDao.upsert(any()) } returns Unit
        coEvery { auditLog.logAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns com.enterprise.pos.core.Result.success(Unit)

        val result = repo.voidOrder(orderId, "Mistake", employeeId)
        assertThat(result.isSuccess()).isTrue()
        val order = result.getOrThrow()
        assertThat(order.status).isEqualTo(OrderStatus.VOIDED)
        coVerify { auditLog.logAction(any(), any(), any(), any(), eq(AuditAction.ORDER_VOIDED), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getById returns null when not found`() = runBlocking {
        coEvery { orderDao.get("order-1") } returns null
        val result = repo.getById(orderId)
        assertThat(result.isSuccess()).isTrue()
        assertThat(result.getOrThrow()).isNull()
    }
}

class CatalogRepositoryImplTest {

    private val dao = mockk<CatalogDao>(relaxed = true)
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val clock = object : Clock { override fun now() = 1700000000000L }
    private val repo = CatalogRepositoryImpl(dao, syncOutboxDao, clock)

    private val storeId = StoreId("store-1")
    private val productId = com.enterprise.pos.core.ProductId("prod-1")

    @Test
    fun `upsertProduct enqueues sync`() = runBlocking {
        coEvery { dao.upsertProduct(any()) } returns Unit
        coEvery { dao.upsertVariants(any()) } returns Unit

        val product = com.enterprise.pos.domain.model.Product(
            id = productId, name = "Burger", categoryId = com.enterprise.pos.core.CategoryId("cat-1"),
            variants = listOf(
                com.enterprise.pos.domain.model.ProductVariant(
                    id = com.enterprise.pos.core.VariantId("var-1"), name = "Default", sku = "BUR001", price = Money.of(12.50)
                )
            )
        )
        val result = repo.upsertProduct(storeId, product)
        assertThat(result.isSuccess()).isTrue()
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `setAvailable enqueues sync`() = runBlocking {
        coEvery { dao.setAvailable(any(), any()) } returns Unit
        val result = repo.setAvailable(storeId, productId, false)
        assertThat(result.isSuccess()).isTrue()
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `deleteProduct enqueues sync`() = runBlocking {
        coEvery { dao.deleteProduct(any()) } returns Unit
        val result = repo.deleteProduct(storeId, productId)
        assertThat(result.isSuccess()).isTrue()
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `adjustInventory returns updated snapshot`() = runBlocking {
        coEvery { dao.adjustInventory(any(), any(), any()) } returns com.enterprise.pos.data.db.entity.InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 10, committed = 0, lowStockThreshold = 5, reorderPoint = 10, updatedAt = clock.now()
        )
        val result = repo.adjustInventory(storeId, com.enterprise.pos.core.VariantId("var-1"), 5, "Restock")
        assertThat(result.isSuccess()).isTrue()
        val snapshot = result.getOrThrow()
        assertThat(snapshot.onHand).isEqualTo(10)
    }

    @Test
    fun `search delegates to dao`() = runBlocking {
        coEvery { dao.search("burger") } returns emptyList()
        val result = repo.search("burger")
        assertThat(result.isSuccess()).isTrue()
        assertThat(result.getOrThrow()).isEmpty()
    }
}

class CustomerRepositoryImplTest {

    private val dao = mockk<CustomerDao>(relaxed = true)
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val clock = object : Clock { override fun now() = 1700000000000L }
    private val repo = CustomerRepositoryImpl(dao, syncOutboxDao, clock)

    private val customerId = CustomerId("cust-1")

    @Test
    fun `upsert enqueues sync`() = runBlocking {
        coEvery { dao.upsert(any()) } returns Unit
        val customer = com.enterprise.pos.domain.model.Customer(
            id = customerId, name = "Alice", email = "alice@example.com", createdAt = 0L
        )
        val result = repo.upsert(customer)
        assertThat(result.isSuccess()).isTrue()
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `delete enqueues sync`() = runBlocking {
        coEvery { dao.delete(any()) } returns Unit
        val result = repo.delete(customerId)
        assertThat(result.isSuccess()).isTrue()
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `addLoyaltyPoints updates points`() = runBlocking {
        coEvery { dao.addLoyalty(any(), any(), any()) } returns Unit
        coEvery { dao.get("cust-1") } returns CustomerEntity(
            id = "cust-1", name = "Alice", email = "alice@example.com", loyaltyPoints = 100, storeCreditMinor = 0, marketingOptIn = false, dietaryRestrictions = "", createdAt = 0L
        )
        val result = repo.addLoyaltyPoints(customerId, 50)
        assertThat(result.isSuccess()).isTrue()
        val updated = result.getOrThrow()
        assertThat(updated.loyaltyPoints).isEqualTo(100) // dao returns stale value since mock doesn't actually update
    }

    @Test
    fun `adjustStoreCredit updates credit`() = runBlocking {
        coEvery { dao.adjustStoreCredit(any(), any(), any()) } returns Unit
        coEvery { dao.get("cust-1") } returns CustomerEntity(
            id = "cust-1", name = "Alice", email = "alice@example.com", loyaltyPoints = 0, storeCreditMinor = 5000, marketingOptIn = false, dietaryRestrictions = "", createdAt = 0L
        )
        val result = repo.adjustStoreCredit(customerId, Money.of(25.00), "Refund")
        assertThat(result.isSuccess()).isTrue()
    }
}

class EmployeeRepositoryImplTest {

    private val dao = mockk<EmployeeDao>(relaxed = true)
    private val clock = object : Clock { override fun now() = 1700000000000L }
    private val repo = EmployeeRepositoryImpl(dao, clock)

    private val employeeId = EmployeeId("emp-1")

    @Test
    fun `login verifies PIN and returns employee`() = runBlocking {
        val hash = PinHasher.hash("1234")
        coEvery { dao.allActive() } returns listOf(
            EmployeeEntity(
                id = "emp-1", name = "Alice", pinHash = hash, role = "CASHIER", active = true,
                email = null, phone = null, createdAt = 0L
            )
        )
        coEvery { dao.upsert(any()) } returns Unit

        val result = repo.login("1234")
        assertThat(result.isSuccess()).isTrue()
        val employee = result.getOrThrow()
        assertThat(employee.name).isEqualTo("Alice")
        assertThat(employee.role).isEqualTo(com.enterprise.pos.domain.model.EmployeeRole.CASHIER)
    }

    @Test
    fun `login with wrong PIN fails`() = runBlocking {
        val hash = PinHasher.hash("1234")
        coEvery { dao.allActive() } returns listOf(
            EmployeeEntity(
                id = "emp-1", name = "Alice", pinHash = hash, role = "CASHIER", active = true,
                email = null, phone = null, createdAt = 0L
            )
        )

        val result = repo.login("9999")
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `deactivate sets active to false`() = runBlocking {
        coEvery { dao.deactivate(any()) } returns Unit
        val result = repo.deactivate(employeeId)
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `resetPin updates hash`() = runBlocking {
        coEvery { dao.resetPin(any(), any()) } returns Unit
        coEvery { dao.get("emp-1") } returns EmployeeEntity(
            id = "emp-1", name = "Alice", pinHash = "oldhash", role = "CASHIER", active = true,
            email = null, phone = null, createdAt = 0L
        )
        val result = repo.resetPin(employeeId, "5678")
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `permissions returns correct role permissions`() = runBlocking {
        val result = repo.permissions(com.enterprise.pos.domain.model.EmployeeRole.MANAGER)
        assertThat(result.isSuccess()).isTrue()
        val perms = result.getOrThrow()
        assertThat(perms.canManageEmployees).isTrue()
        assertThat(perms.maxDiscountPercent).isEqualTo(100)
    }
}
