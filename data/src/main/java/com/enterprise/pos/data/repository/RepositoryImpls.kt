package com.enterprise.pos.data.repository

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.Clock
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.TableId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.CustomerDao
import com.enterprise.pos.data.db.dao.EmployeeDao
import com.enterprise.pos.data.db.dao.OrderDao
import com.enterprise.pos.data.db.dao.PaymentDao
import com.enterprise.pos.data.db.dao.StoreDao
import com.enterprise.pos.data.db.dao.SyncQueueDao
import com.enterprise.pos.data.db.dao.TableDao
import com.enterprise.pos.data.db.entity.SyncQueueEntity
import com.enterprise.pos.data.repository.Mappers.rolePermissions
import com.enterprise.pos.data.repository.Mappers.toDomain
import com.enterprise.pos.data.repository.Mappers.toEntity
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class CatalogRepositoryImpl(
    private val dao: CatalogDao,
    private val syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao,
    private val clock: Clock = SystemClock
) : CatalogRepository {

    override fun observeCategories(): Flow<List<com.enterprise.pos.domain.model.Category>> =
        dao.observeCategories().map { it.map { e -> e.toDomain() } }

    override fun observeProducts(categoryId: CategoryId?): Flow<List<com.enterprise.pos.domain.model.Product>> =
        dao.observeProducts(categoryId?.value).map { list ->
            list.map { e -> e.toDomain(dao.variantsFor(e.id).map { v -> v.toDomain() }) }
        }

    override fun observeProduct(productId: ProductId): Flow<com.enterprise.pos.domain.model.Product?> =
        dao.observeProduct(productId.value).map { e ->
            e?.let { it.toDomain(dao.variantsFor(it.id).map { v -> v.toDomain() }) }
        }

    override fun observeInventory(storeId: StoreId, variantId: VariantId): Flow<com.enterprise.pos.domain.model.InventorySnapshot?> =
        dao.observeInventory(variantId.value, storeId.value).map { it?.toDomain() }

    override suspend fun getProduct(productId: ProductId): Result<com.enterprise.pos.domain.model.Product?> = Result.catching {
        val e = dao.getProduct(productId.value) ?: return@catching null
        e.toDomain(dao.variantsFor(e.id).map { v -> v.toDomain() })
    }

    override suspend fun getProductByBarcode(barcode: String): Result<com.enterprise.pos.domain.model.Product?> = Result.catching {
        val v = dao.findByBarcode(barcode) ?: return@catching null
        val e = dao.getProduct(v.productId) ?: return@catching null
        e.toDomain(dao.variantsFor(e.id).map { vv -> vv.toDomain() })
    }

    override suspend fun search(query: String): Result<List<com.enterprise.pos.domain.model.Product>> = Result.catching {
        dao.search(query).map { e -> e.toDomain(dao.variantsFor(e.id).map { v -> v.toDomain() }) }
    }

    override suspend fun upsertProduct(storeId: StoreId, product: com.enterprise.pos.domain.model.Product): Result<com.enterprise.pos.domain.model.Product> = Result.catching {
        val now = clock.now()
        dao.upsertProduct(product.toEntity(now))
        dao.upsertVariants(product.variants.map { it.toEntity(product.id) })
        syncOutboxDao.enqueue(storeId = storeId, entityType = "products", entityId = product.id.value, operation = "UPSERT", createdAt = now)
        product
    }

    override suspend fun adjustInventory(storeId: StoreId, variantId: VariantId, delta: Int, reason: String): Result<com.enterprise.pos.domain.model.InventorySnapshot> = Result.catching {
        val updated = dao.adjustInventory(variantId.value, storeId.value, delta)
        syncOutboxDao.enqueue(storeId = storeId, entityType = "inventory", entityId = "${variantId.value}|${storeId.value}", operation = "UPSERT", createdAt = clock.now())
        updated.toDomain()
    }

    override suspend fun setAvailable(storeId: StoreId, productId: ProductId, available: Boolean): Result<Unit> = Result.catching {
        dao.setAvailable(productId.value, available)
        syncOutboxDao.enqueue(storeId = storeId, entityType = "products", entityId = productId.value, operation = "UPSERT", createdAt = clock.now())
    }

    override fun observeCategory(categoryId: CategoryId): Flow<com.enterprise.pos.domain.model.Category?> =
        dao.observeCategory(categoryId.value).map { it?.toDomain() }

    override suspend fun getCategory(categoryId: CategoryId): Result<com.enterprise.pos.domain.model.Category?> = Result.catching {
        dao.getCategory(categoryId.value)?.toDomain()
    }

    override suspend fun upsertCategory(category: com.enterprise.pos.domain.model.Category): Result<com.enterprise.pos.domain.model.Category> = Result.catching {
        dao.upsertCategory(category.toEntity())
        category
    }

    override suspend fun deleteCategory(categoryId: CategoryId): Result<Unit> = Result.catching {
        dao.deleteCategory(categoryId.value)
    }

    override suspend fun deleteProduct(storeId: StoreId, productId: ProductId): Result<Unit> = Result.catching {
        dao.deleteProduct(productId.value)
        syncOutboxDao.enqueue(storeId = storeId, entityType = "products", entityId = productId.value, operation = "DELETE", createdAt = clock.now())
    }

    override fun observeModifierGroups(): Flow<List<com.enterprise.pos.domain.model.ModifierGroup>> =
        dao.observeModifierGroups().map { it.map { e -> e.toDomain() } }

    override fun observeModifierGroup(id: com.enterprise.pos.core.ModifierGroupId): Flow<com.enterprise.pos.domain.model.ModifierGroup?> =
        dao.observeModifierGroup(id.value).map { it?.toDomain() }

    override suspend fun getModifierGroup(id: com.enterprise.pos.core.ModifierGroupId): Result<com.enterprise.pos.domain.model.ModifierGroup?> = Result.catching {
        dao.getModifierGroup(id.value)?.toDomain()
    }

    override suspend fun upsertModifierGroup(storeId: StoreId, modifierGroup: com.enterprise.pos.domain.model.ModifierGroup): Result<com.enterprise.pos.domain.model.ModifierGroup> = Result.catching {
        val now = clock.now()
        dao.upsertModifierGroup(modifierGroup.toEntity(now))
        syncOutboxDao.enqueue(storeId = storeId, entityType = "modifier_groups", entityId = modifierGroup.id.value, operation = "UPSERT", createdAt = now)
        modifierGroup
    }

    override suspend fun deleteModifierGroup(storeId: StoreId, id: com.enterprise.pos.core.ModifierGroupId): Result<Unit> = Result.catching {
        dao.deleteModifierGroup(id.value)
        syncOutboxDao.enqueue(storeId = storeId, entityType = "modifier_groups", entityId = id.value, operation = "DELETE", createdAt = clock.now())
    }

    override suspend fun upsertInventory(storeId: StoreId, inventory: com.enterprise.pos.domain.model.InventorySnapshot): Result<com.enterprise.pos.domain.model.InventorySnapshot> = Result.catching {
        val entity = com.enterprise.pos.data.db.entity.InventoryEntity(
            variantId = inventory.variantId.value,
            storeId = storeId.value,
            onHand = inventory.onHand,
            committed = inventory.committed,
            lowStockThreshold = inventory.lowStockThreshold,
            reorderPoint = inventory.reorderPoint,
            updatedAt = clock.now()
        )
        dao.upsertInventory(entity)
        syncOutboxDao.enqueue(storeId = storeId, entityType = "inventory", entityId = "${inventory.variantId.value}|${storeId.value}", operation = "UPSERT", createdAt = clock.now())
        entity.toDomain()
    }
}

class OrderRepositoryImpl(
    private val orderDao: OrderDao,
    private val tableDao: TableDao,
    private val paymentDao: com.enterprise.pos.data.db.dao.PaymentDao,
    private val syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao,
    private val auditLog: com.enterprise.pos.domain.repository.AuditLogRepository,
    private val cartEngine: com.enterprise.pos.domain.service.CartEngine,
    private val clock: Clock = SystemClock
) : OrderRepository {

    override fun observeOpenOrders(storeId: StoreId): Flow<List<Order>> =
        orderDao.observeOpenOrders(storeId.value).map { list -> list.map { hydrate(it) } }

    override fun observeOrder(orderId: OrderId): Flow<Order?> =
        orderDao.observeOrder(orderId.value).map { it?.let { hydrate(it) } }

    override fun observeOrdersByTable(tableId: TableId): Flow<List<Order>> =
        orderDao.observeOrdersByTable(tableId.value).map { list -> list.map { hydrate(it) } }

    override fun observeTables(storeId: StoreId): Flow<List<RestaurantTable>> =
        tableDao.observeForStore(storeId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun createOrder(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId,
        diningMode: DiningMode,
        tableId: TableId?,
        guestCount: Int
    ): Result<Order> = Result.catching {
        val now = clock.now()
        val orderId = OrderId(com.enterprise.pos.core.Id.random<Any>().value)
        val order = cartEngine.createOrder(
            orderId = orderId,
            storeId = storeId,
            registerId = registerId,
            employeeId = employeeId,
            diningMode = diningMode,
            tableId = tableId,
            guestCount = guestCount,
            now = now
        )
        persist(order)
        order
    }

    override suspend fun updateOrder(order: Order): Result<Order> = Result.catching {
        val updated = order.copy(updatedAt = clock.now())
        persist(updated)
        updated
    }

    override suspend fun setStatus(orderId: OrderId, status: OrderStatus): Result<Order> = Result.catching {
        val now = clock.now()
        val closedAt = if (status in setOf(OrderStatus.PAID, OrderStatus.REFUNDED, OrderStatus.VOIDED, OrderStatus.CANCELLED)) now else null
        orderDao.setStatus(orderId.value, status.name, now, closedAt)
        hydrate(orderDao.get(orderId.value)!!)
    }

    override suspend fun assignTable(orderId: OrderId, tableId: TableId?): Result<Order> = Result.catching {
        orderDao.setTable(orderId.value, tableId?.value, clock.now())
        hydrate(orderDao.get(orderId.value)!!)
    }

    override suspend fun assignServer(tableId: TableId, serverId: EmployeeId?): Result<Unit> = Result.catching {
        tableDao.updateServer(tableId.value, serverId?.value)
    }

    override suspend fun setTableStatus(tableId: TableId, status: TableStatus): Result<Unit> = Result.catching {
        tableDao.updateStatusOnly(tableId.value, status.name)
    }

    override suspend fun getById(orderId: OrderId): Result<Order?> = Result.catching {
        orderDao.get(orderId.value)?.let { hydrate(it) }
    }

    override suspend fun closeOrder(orderId: OrderId): Result<Order> =
        setStatus(orderId, OrderStatus.PAID)

    override suspend fun recentOrders(storeId: StoreId, limit: Int): Result<List<Order>> = Result.catching {
        orderDao.recentOrders(storeId.value, limit).map { hydrate(it) }
    }

    /**
     * Transactional mark-paid:
     *   1. Persist the PaymentEntity.
     *   2. Decrement inventory per line.
     *   3. Write audit log (PAYMENT_CAPTURED, ORDER_PAID, INVENTORY_DECREMENTED).
     *   4. Enqueue sync outbox events for payment + order + inventory changes.
     *   5. Update order status to PAID if amountDue == 0.
     */
    override suspend fun markPaid(
        orderId: OrderId,
        payment: com.enterprise.pos.domain.model.Payment,
        employeeId: EmployeeId
    ): Result<Order> = Result.catching {
        val now = clock.now()
        val existing = orderDao.get(orderId.value)
            ?: throw IllegalArgumentException("Order ${orderId.value} not found")
        val current = hydrate(existing)

        // 1. Persist payment
        val paymentEntity = com.enterprise.pos.data.db.entity.PaymentEntity(
            id = payment.id.value,
            orderId = orderId.value,
            provider = payment.provider,
            providerTransactionId = payment.providerTransactionId,
            amountMinor = payment.amount.minorUnits,
            currency = payment.currency,
            cardBrand = payment.cardBrand,
            last4 = payment.last4,
            entryMode = payment.entryMode,
            receiptUrl = payment.receiptUrl,
            capturedAt = if (payment.capturedAt > 0) payment.capturedAt else now,
            refundedAmountMinor = 0L,
            syncState = "PENDING"
        )
        paymentDao.upsert(paymentEntity)

        // 2. Update order with payment, mark PAID if fully paid
        val updatedOrder = cartEngine.recordPayment(current, payment, now)
        orderDao.upsert(updatedOrder.toEntity())

        // 3. Decrement inventory for each line
        for (line in updatedOrder.lines.filter { it.lineType == com.enterprise.pos.domain.model.OrderLineType.ITEM }) {
            val variantId = line.variantId ?: continue
            try {
                dao.adjustInventory(variantId.value, current.storeId.value, -line.quantity.asInt)
                syncOutboxDao.enqueue(storeId = current.storeId, entityType = "inventory", entityId = "${variantId.value}|${current.storeId.value}", operation = "UPSERT", createdAt = now)
            } catch (t: Throwable) {
                // Inventory decrement failure does NOT fail the payment — it's a separate concern.
                // Log to audit so a manager can reconcile.
                auditLog.logAction(
                    storeId = updatedOrder.storeId,
                    registerId = updatedOrder.registerId,
                    employeeId = employeeId,
                    employeeName = "",
                    action = com.enterprise.pos.domain.model.AuditAction.INVENTORY_ADJUSTED,
                    entityType = "Variant",
                    entityId = variantId.value,
                    reason = "Auto-decrement failed for order ${orderId.value}: ${t.message}"
                )
            }
        }

        // 4. Audit log
        auditLog.logAction(
            storeId = updatedOrder.storeId,
            registerId = updatedOrder.registerId,
            employeeId = employeeId,
            employeeName = "",
            action = com.enterprise.pos.domain.model.AuditAction.PAYMENT_CAPTURED,
            entityType = "Payment",
            entityId = payment.id.value,
            afterJson = "{\"provider\":\"${payment.provider}\",\"amount_minor\":${payment.amount.minorUnits},\"provider_txn\":\"${payment.providerTransactionId}\"}"
        )
        if (updatedOrder.status == OrderStatus.PAID) {
            auditLog.logAction(
                storeId = updatedOrder.storeId,
                registerId = updatedOrder.registerId,
                employeeId = employeeId,
                employeeName = "",
                action = com.enterprise.pos.domain.model.AuditAction.ORDER_PAID,
                entityType = "Order",
                entityId = orderId.value,
                afterJson = "{\"grand_total_minor\":${updatedOrder.grandTotal.minorUnits},\"amount_paid_minor\":${updatedOrder.amountPaid.minorUnits}}"
            )
        }

        // 5. Enqueue sync events
        syncOutboxDao.enqueue(storeId = current.storeId, registerId = current.registerId.value, employeeId = employeeId.value, entityType = "payments", entityId = payment.id.value, operation = "UPSERT", createdAt = now)
        syncOutboxDao.enqueue(storeId = current.storeId, registerId = current.registerId.value, employeeId = employeeId.value, entityType = "orders", entityId = orderId.value, operation = "UPSERT", createdAt = now)

        updatedOrder
    }

    override suspend fun refund(
        orderId: OrderId,
        refund: com.enterprise.pos.domain.model.Payment,
        reason: String,
        employeeId: EmployeeId
    ): Result<Order> = Result.catching {
        val now = clock.now()
        val existing = orderDao.get(orderId.value)
            ?: throw IllegalArgumentException("Order ${orderId.value} not found")
        val current = hydrate(existing)

        // Persist refund as a PaymentEntity with refundedAmountMinor populated.
        val refundEntity = com.enterprise.pos.data.db.entity.PaymentEntity(
            id = refund.id.value,
            orderId = orderId.value,
            provider = refund.provider,
            providerTransactionId = refund.providerTransactionId,
            amountMinor = refund.amount.minorUnits,
            currency = refund.currency,
            cardBrand = refund.cardBrand,
            last4 = refund.last4,
            entryMode = refund.entryMode,
            receiptUrl = refund.receiptUrl,
            capturedAt = now,
            refundedAmountMinor = refund.amount.minorUnits,
            syncState = "PENDING"
        )
        paymentDao.upsert(refundEntity)

        val updatedOrder = cartEngine.recordRefund(current, refund, now)
        orderDao.upsert(updatedOrder.toEntity())

        // Restock inventory for refunded items
        for (line in updatedOrder.lines.filter { it.lineType == com.enterprise.pos.domain.model.OrderLineType.ITEM }) {
            val variantId = line.variantId ?: continue
            try {
                dao.adjustInventory(variantId.value, current.storeId.value, line.quantity.asInt)
                syncOutboxDao.enqueue(storeId = current.storeId, entityType = "inventory", entityId = "${variantId.value}|${current.storeId.value}", operation = "UPSERT", createdAt = now)
            } catch (t: Throwable) {
                auditLog.logAction(
                    storeId = updatedOrder.storeId,
                    registerId = updatedOrder.registerId,
                    employeeId = employeeId,
                    employeeName = "",
                    action = com.enterprise.pos.domain.model.AuditAction.INVENTORY_ADJUSTED,
                    entityType = "Variant",
                    entityId = variantId.value,
                    reason = "Auto-restock failed for refund ${orderId.value}: ${t.message}"
                )
            }
        }

        auditLog.logAction(
            storeId = updatedOrder.storeId,
            registerId = updatedOrder.registerId,
            employeeId = employeeId,
            employeeName = "",
            action = com.enterprise.pos.domain.model.AuditAction.PAYMENT_REFUNDED,
            entityType = "Payment",
            entityId = refund.id.value,
            reason = reason,
            afterJson = "{\"amount_minor\":${refund.amount.minorUnits},\"reason\":\"$reason\"}"
        )

        syncOutboxDao.enqueue(storeId = current.storeId, registerId = current.registerId.value, employeeId = employeeId.value, entityType = "payments", entityId = refund.id.value, operation = "UPSERT", createdAt = now)
        syncOutboxDao.enqueue(storeId = current.storeId, registerId = current.registerId.value, employeeId = employeeId.value, entityType = "orders", entityId = orderId.value, operation = "UPSERT", createdAt = now)

        updatedOrder
    }

    override suspend fun voidOrder(orderId: OrderId, reason: String, employeeId: EmployeeId): Result<Order> = Result.catching {
        val now = clock.now()
        val existing = orderDao.get(orderId.value)
            ?: throw IllegalArgumentException("Order ${orderId.value} not found")
        val current = hydrate(existing)
        val voided = cartEngine.voidOrder(current, reason, now).getOrThrow()
        orderDao.upsert(voided.toEntity())
        auditLog.logAction(
            storeId = voided.storeId,
            registerId = voided.registerId,
            employeeId = employeeId,
            employeeName = "",
            action = com.enterprise.pos.domain.model.AuditAction.ORDER_VOIDED,
            entityType = "Order",
            entityId = orderId.value,
            reason = reason
        )
        syncOutboxDao.enqueue(storeId = current.storeId, registerId = current.registerId.value, employeeId = employeeId.value, entityType = "orders", entityId = orderId.value, operation = "UPSERT", createdAt = now)
        voided
    }

    private suspend fun persist(order: Order) {
        orderDao.upsert(order.toEntity())
        val lines = order.lines.mapIndexed { idx, l -> l.toEntity(order.id, idx) }
        val taxLines = order.taxLines.map { it.toEntity(order.id) }
        val discounts = order.discounts.map { it.toEntity(order.id) }
        orderDao.replaceOrderChildren(order.id.value, lines, taxLines, discounts)
        syncOutboxDao.enqueue(storeId = order.storeId, registerId = order.registerId.value, employeeId = order.employeeId.value, entityType = "orders", entityId = order.id.value, operation = "UPSERT", createdAt = clock.now())
    }

    private suspend fun hydrate(e: com.enterprise.pos.data.db.entity.OrderEntity): Order {
        val lines = orderDao.linesFor(e.id)
        val taxLines = orderDao.taxLinesFor(e.id)
        val discounts = orderDao.discountsFor(e.id)
        val payments = paymentDao.forOrder(e.id)
        return e.toDomain(lines, taxLines, discounts, payments)
    }
}

class CustomerRepositoryImpl(
    private val dao: CustomerDao,
    private val syncOutboxDao: com.enterprise.pos.data.sync.SyncOutboxDao,
    private val clock: Clock = SystemClock
) : CustomerRepository {

    override fun observeCustomers(query: String): Flow<List<com.enterprise.pos.domain.model.Customer>> =
        dao.observe(query).map { list -> list.map { it.toDomain() } }

    override fun observeCustomer(id: CustomerId): Flow<com.enterprise.pos.domain.model.Customer?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override suspend fun get(id: CustomerId): Result<com.enterprise.pos.domain.model.Customer?> = Result.catching {
        dao.get(id.value)?.toDomain()
    }

    override suspend fun search(query: String): Result<List<com.enterprise.pos.domain.model.Customer>> = Result.catching {
        dao.search(query).map { it.toDomain() }
    }

    override suspend fun upsert(customer: com.enterprise.pos.domain.model.Customer): Result<com.enterprise.pos.domain.model.Customer> = Result.catching {
        dao.upsert(customer.toEntity(clock.now()))
        syncOutboxDao.enqueue(storeId = StoreId("global"), entityType = "customers", entityId = customer.id.value, operation = "UPSERT", createdAt = clock.now())
        customer
    }

    override suspend fun delete(id: CustomerId): Result<Unit> = Result.catching {
        dao.delete(id.value)
        syncOutboxDao.enqueue(storeId = StoreId("global"), entityType = "customers", entityId = id.value, operation = "DELETE", createdAt = clock.now())
    }

    override suspend fun addLoyaltyPoints(id: CustomerId, points: Int): Result<com.enterprise.pos.domain.model.Customer> = Result.catching {
        dao.addLoyalty(id.value, points, clock.now())
        dao.get(id.value)!!.toDomain()
    }

    override suspend fun adjustStoreCredit(id: CustomerId, delta: Money, reason: String): Result<com.enterprise.pos.domain.model.Customer> = Result.catching {
        dao.adjustStoreCredit(id.value, delta.minorUnits, clock.now())
        dao.get(id.value)!!.toDomain()
    }

    override suspend fun purchaseHistory(id: CustomerId): Result<List<Order>> = Result.success(emptyList()) // implemented via OrderRepository
}

class EmployeeRepositoryImpl(
    private val dao: EmployeeDao,
    private val clock: Clock = SystemClock
) : EmployeeRepository {

    override fun observeEmployees(): Flow<List<com.enterprise.pos.domain.model.Employee>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeEmployee(id: EmployeeId): Flow<com.enterprise.pos.domain.model.Employee?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override suspend fun login(pin: String): Result<com.enterprise.pos.domain.model.Employee> = Result.catching {
        val candidates = dao.allActive()
        val now = clock.now()
        var matched: com.enterprise.pos.data.db.entity.EmployeeEntity? = null
        for (e in candidates) {
            if (e.lockedUntil != null && e.lockedUntil > now) continue
            if (com.enterprise.pos.domain.security.PinHasher.verify(pin, e.pinHash)) {
                matched = e
                break
            }
        }
        if (matched == null) throw SecurityException("Invalid PIN")
        val updated = matched.copy(failedLoginAttempts = 0, lockedUntil = null, lastLoginAt = now)
        dao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun get(id: EmployeeId): Result<com.enterprise.pos.domain.model.Employee?> = Result.catching {
        dao.get(id.value)?.toDomain()
    }

    override suspend fun upsert(employee: com.enterprise.pos.domain.model.Employee): Result<com.enterprise.pos.domain.model.Employee> = Result.catching {
        dao.upsert(employee.toEntity(clock.now()))
        employee
    }

    override suspend fun deactivate(id: EmployeeId): Result<Unit> = Result.catching {
        dao.deactivate(id.value)
    }

    override suspend fun resetPin(id: EmployeeId, newPin: String): Result<com.enterprise.pos.domain.model.Employee> = Result.catching {
        val newHash = com.enterprise.pos.domain.security.PinHasher.hash(newPin)
        dao.resetPin(id.value, newHash)
        dao.get(id.value)!!.toDomain()
    }

    override suspend fun permissions(role: com.enterprise.pos.domain.model.EmployeeRole): Result<com.enterprise.pos.domain.model.RolePermissions> =
        Result.success(rolePermissions(role))
}

class StoreRepositoryImpl(
    private val dao: StoreDao
) : StoreRepository {
    override fun observeStores(): Flow<List<com.enterprise.pos.domain.model.Store>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun current(): Result<com.enterprise.pos.domain.model.Store> = Result.catching {
        dao.first()?.toDomain() ?: throw IllegalStateException("No store configured")
    }

    override suspend fun registers(storeId: StoreId): Result<List<com.enterprise.pos.domain.model.Register>> = Result.catching {
        dao.registersFor(storeId.value).map { it.toDomain() }
    }

    override suspend fun upsertStore(store: com.enterprise.pos.domain.model.Store): Result<com.enterprise.pos.domain.model.Store> = Result.catching {
        dao.upsertStore(store.toEntity())
        store
    }

    override suspend fun upsertRegister(register: com.enterprise.pos.domain.model.Register): Result<com.enterprise.pos.domain.model.Register> = Result.catching {
        dao.upsertRegisters(listOf(register.toEntity()))
        register
    }
}

private fun com.enterprise.pos.domain.model.Store.toEntity(): com.enterprise.pos.data.db.entity.StoreEntity =
    com.enterprise.pos.data.db.entity.StoreEntity(
        id = id.value,
        name = name,
        address = address,
        phone = phone,
        taxIdentifier = taxIdentifier,
        currency = currency,
        timezone = timezone
    )

private fun com.enterprise.pos.domain.model.Register.toEntity(): com.enterprise.pos.data.db.entity.RegisterEntity =
    com.enterprise.pos.data.db.entity.RegisterEntity(
        id = id.value,
        storeId = storeId.value,
        name = name,
        deviceIdentifier = deviceIdentifier,
        active = active
    )

// Helper extension to map ProductVariant -> VariantEntity
private fun com.enterprise.pos.domain.model.ProductVariant.toEntity(productId: ProductId): com.enterprise.pos.data.db.entity.VariantEntity {
    val json = Json { ignoreUnknownKeys = true }
    val attrs = if (attributes.isEmpty()) "" else json.encodeToString(MapSerializer(String.serializer(), String.serializer()), attributes)
    return com.enterprise.pos.data.db.entity.VariantEntity(
        id = id.value,
        productId = productId.value,
        name = name,
        sku = sku,
        barcode = barcode,
        priceMinor = price.minorUnits,
        costPriceMinor = costPrice?.minorUnits,
        attributesJson = attrs
    )
}

// Helper extension to map Product -> ProductEntity
private fun com.enterprise.pos.domain.model.Product.toEntity(now: Long): com.enterprise.pos.data.db.entity.ProductEntity =
    com.enterprise.pos.data.db.entity.ProductEntity(
        id = id.value,
        name = name,
        description = description,
        categoryId = categoryId.value,
        type = type.name,
        taxCategory = taxCategory.name,
        ageRestriction = ageRestriction.name,
        imageUrl = imageUrl,
        defaultVariantId = defaultVariantId?.value,
        tags = tags.joinToString(","),
        trackInventory = trackInventory,
        isAvailable = isAvailable,
        displayOrder = displayOrder,
        modifierGroupsJson = modifierGroupIds.joinToString(",") { it.value },
        kitchenRoutingKey = kitchenRoutingKey,
        prepTimeMinutes = prepTimeMinutes,
        updatedAt = now
    )
