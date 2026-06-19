package com.enterprise.pos.data.repository

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Clock
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.SystemClock
import com.enterprise.pos.core.TableId
import com.enterprise.pos.data.db.dao.AuditLogDao
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.EmployeeDao
import com.enterprise.pos.data.db.dao.GiftCardDao
import com.enterprise.pos.data.db.dao.InventoryAdjustmentDao
import com.enterprise.pos.data.db.dao.InventoryTransferDao
import com.enterprise.pos.data.db.dao.LoyaltyRewardDao
import com.enterprise.pos.data.db.dao.MigrationJobDao
import com.enterprise.pos.data.db.dao.OrderDao
import com.enterprise.pos.data.db.dao.PaymentDao
import com.enterprise.pos.data.db.dao.PromotionDao
import com.enterprise.pos.data.db.dao.ReservationDao
import com.enterprise.pos.data.db.dao.ReturnDao
import com.enterprise.pos.data.db.dao.SettingDao
import com.enterprise.pos.data.db.dao.ShiftDao
import com.enterprise.pos.data.db.dao.SyncQueueDao
import com.enterprise.pos.data.db.dao.TableDao
import com.enterprise.pos.data.db.dao.TipLogDao
import com.enterprise.pos.data.db.dao.ZReportDao
import com.enterprise.pos.data.db.entity.GiftCardTransactionEntity
import com.enterprise.pos.data.db.entity.InventoryAdjustmentEntity
import com.enterprise.pos.data.db.entity.SettingEntity
import com.enterprise.pos.data.db.entity.TipLogEntity
import com.enterprise.pos.data.repository.EnterpriseMappers.toDomain
import com.enterprise.pos.data.repository.EnterpriseMappers.toEntity
import com.enterprise.pos.data.repository.Mappers.toDomain
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AbcClass
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.AuditLogEntry
import com.enterprise.pos.domain.model.ConflictResolution
import com.enterprise.pos.domain.model.DashboardAlert
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.model.AlertSeverity
import com.enterprise.pos.domain.model.AlertCategory
import com.enterprise.pos.domain.model.GiftCard
import com.enterprise.pos.domain.model.GiftCardTxType
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.InventoryTransfer
import com.enterprise.pos.domain.model.MigrationConflict
import com.enterprise.pos.domain.model.MigrationJob
import com.enterprise.pos.domain.model.MigrationSource
import com.enterprise.pos.domain.model.MigrationStatus
import com.enterprise.pos.domain.model.MigrationType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.ReturnRequest
import com.enterprise.pos.domain.model.SalesByCategory
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.domain.model.Shift
import com.enterprise.pos.domain.model.ShiftStatus
import com.enterprise.pos.domain.model.TipPoolSummary
import com.enterprise.pos.domain.model.TipPoolType
import com.enterprise.pos.domain.model.TransferLine
import com.enterprise.pos.domain.model.TransferStatus
import com.enterprise.pos.domain.model.ZReport
import com.enterprise.pos.domain.repository.AnalyticsRepository
import com.enterprise.pos.domain.repository.AuditLogRepository
import com.enterprise.pos.domain.repository.GiftCardRepository
import com.enterprise.pos.domain.repository.InventoryManagementRepository
import com.enterprise.pos.domain.repository.MigrationRepository
import com.enterprise.pos.domain.repository.PromotionRepository
import com.enterprise.pos.domain.repository.ReservationRepository
import com.enterprise.pos.domain.repository.ReturnsRepository
import com.enterprise.pos.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReservationRepositoryImpl(
    private val dao: ReservationDao,
    private val tableDao: TableDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : ReservationRepository {
    override fun observeReservations(storeId: StoreId, date: Long): Flow<List<Reservation>> {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeForDay(storeId.value, start, end).map { it.map { e -> e.toDomain() } }
    }

    override fun observeUpcoming(storeId: StoreId, hours: Int): Flow<List<Reservation>> {
        val from = clock.now()
        return dao.observeUpcoming(storeId.value, from, hours * 4).map { it.map { e -> e.toDomain() } }
    }

    override suspend fun get(id: Id<com.enterprise.pos.domain.model.ReservationTag>): Result<Reservation?> = Result.catching {
        dao.get(id.value)?.toDomain()
    }

    override suspend fun upsert(r: Reservation): Result<Reservation> = Result.catching {
        dao.upsert(r.toEntity())
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "reservations", recordId = r.id.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = clock.now()))
        r
    }

    override suspend fun setStatus(id: Id<com.enterprise.pos.domain.model.ReservationTag>, status: ReservationStatus): Result<Unit> = Result.catching {
        val confirmedAt = if (status == ReservationStatus.CONFIRMED) clock.now() else null
        dao.setStatus(id.value, status.name, confirmedAt)
    }

    override suspend fun seat(id: Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: TableId): Result<Unit> = Result.catching {
        dao.seat(id.value, tableId.value, clock.now())
    }

    override suspend fun cancel(id: Id<com.enterprise.pos.domain.model.ReservationTag>, reason: String): Result<Unit> = Result.catching {
        dao.cancel(id.value, clock.now(), reason)
    }
}

class GiftCardRepositoryImpl(
    private val dao: GiftCardDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : GiftCardRepository {

    override fun observe(code: String): Flow<GiftCard?> = dao.observeByCode(code).map { it?.toDomain() }

    override suspend fun get(id: Id<com.enterprise.pos.domain.model.GiftCardTag>): Result<GiftCard?> = Result.catching {
        dao.get(id.value)?.toDomain()
    }

    override suspend fun getByCode(code: String): Result<GiftCard?> = Result.catching {
        dao.getByCode(code)?.toDomain()
    }

    override suspend fun issue(storeId: StoreId, initialBalance: Money, customerId: CustomerId?, notes: String?): Result<GiftCard> = Result.catching {
        val id = UUID.randomUUID().toString()
        val code = generateCode()
        val now = clock.now()
        val card = GiftCard(
            id = Id(id), code = code, storeId = storeId, balance = initialBalance,
            initialBalance = initialBalance, issuedAt = now, expiresAt = null,
            customerId = customerId, notes = notes, active = true
        )
        dao.upsert(card.let {
            com.enterprise.pos.data.db.entity.GiftCardEntity(
                id = it.id.value, code = it.code, storeId = it.storeId.value,
                balanceMinor = it.balance.minorUnits, initialBalanceMinor = it.initialBalance.minorUnits,
                issuedAt = it.issuedAt, expiresAt = it.expiresAt, customerId = it.customerId?.value,
                notes = it.notes, active = it.active
            )
        })
        val tx = GiftCardTransactionEntity(
            id = UUID.randomUUID().toString(), giftCardId = id, orderId = null,
            amountMinor = initialBalance.minorUnits, balanceAfterMinor = initialBalance.minorUnits,
            timestamp = now, employeeId = "system", type = GiftCardTxType.ISSUED.name
        )
        dao.logTransaction(tx)
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "gift_cards", recordId = id, operation = "UPSERT", payloadJson = "{}", enqueuedAt = now))
        card
    }

    override suspend fun reload(id: Id<com.enterprise.pos.domain.model.GiftCardTag>, amount: Money, employeeId: EmployeeId): Result<GiftCard> = Result.catching {
        val now = clock.now()
        val tx = GiftCardTransactionEntity(
            id = UUID.randomUUID().toString(), giftCardId = id.value, orderId = null,
            amountMinor = amount.minorUnits, balanceAfterMinor = 0,
            timestamp = now, employeeId = employeeId.value, type = GiftCardTxType.RELOAD.name
        )
        val updated = dao.adjustBalance(id.value, amount.minorUnits, tx)
        updated.toDomain()
    }

    override suspend fun redeem(code: String, amount: Money, orderId: OrderId?, employeeId: EmployeeId): Result<GiftCard> = Result.catching {
        val card = dao.getByCode(code) ?: throw IllegalArgumentException("Gift card $code not found")
        require(card.active) { "Gift card is not active" }
        require(card.balanceMinor >= amount.minorUnits) { "Insufficient balance (${Money.ofMinor(card.balanceMinor).format()} < ${amount.format()})" }
        val now = clock.now()
        val tx = GiftCardTransactionEntity(
            id = UUID.randomUUID().toString(), giftCardId = card.id, orderId = orderId?.value,
            amountMinor = -amount.minorUnits, balanceAfterMinor = 0,
            timestamp = now, employeeId = employeeId.value, type = GiftCardTxType.REDEEM.name
        )
        val updated = dao.adjustBalance(card.id, -amount.minorUnits, tx)
        updated.toDomain()
    }

    override suspend fun transactions(giftCardId: Id<com.enterprise.pos.domain.model.GiftCardTag>): Result<List<com.enterprise.pos.domain.model.GiftCardTransaction>> = Result.catching {
        dao.transactions(giftCardId.value).map { it.toDomain() }
    }

    override suspend fun adjustBalance(id: Id<com.enterprise.pos.domain.model.GiftCardTag>, newBalance: Money, reason: String, employeeId: EmployeeId): Result<GiftCard> = Result.catching {
        val current = dao.get(id.value) ?: throw IllegalArgumentException("Gift card not found")
        val delta = newBalance.minorUnits - current.balanceMinor
        val now = clock.now()
        val tx = GiftCardTransactionEntity(
            id = UUID.randomUUID().toString(), giftCardId = id.value, orderId = null,
            amountMinor = delta, balanceAfterMinor = newBalance.minorUnits,
            timestamp = now, employeeId = employeeId.value, type = GiftCardTxType.ADJUST.name
        )
        val updated = dao.adjustBalance(id.value, delta, tx)
        updated.toDomain()
    }

    private fun generateCode(): String {
        // 16-digit code: 4 groups of 4 digits
        return (1..4).joinToString("-") {
            (1..4).joinToString("") { (0..9).random().toString() }
        }
    }
}

class PromotionRepositoryImpl(
    private val promoDao: PromotionDao,
    private val loyaltyDao: LoyaltyRewardDao,
    private val clock: Clock = SystemClock
) : PromotionRepository {

    override fun observePromotions(): Flow<List<Promotion>> =
        promoDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeLoyaltyRewards(): Flow<List<com.enterprise.pos.domain.model.LoyaltyReward>> =
        loyaltyDao.observeActive().map { it.map { e -> e.toDomain() } }

    override suspend fun upsert(p: Promotion): Result<Promotion> = Result.catching {
        promoDao.upsert(p.toEntity())
        p
    }

    override suspend fun delete(id: Id<com.enterprise.pos.domain.model.PromotionTag>): Result<Unit> = Result.catching {
        promoDao.delete(id.value)
    }

    override suspend fun activeFor(storeId: StoreId, now: Long): Result<List<Promotion>> = Result.catching {
        promoDao.observeAll().first().map { it.toDomain() }.filter { it.active }
    }

    override suspend fun validateCode(code: String): Result<Promotion?> = Result.catching {
        promoDao.byCode(code)?.toDomain()
    }

    override suspend fun recordRedemption(id: Id<com.enterprise.pos.domain.model.PromotionTag>): Result<Unit> = Result.catching {
        promoDao.recordRedemption(id.value)
    }
}

class ShiftRepositoryImpl(
    private val shiftDao: ShiftDao,
    private val orderDao: OrderDao,
    private val paymentDao: PaymentDao,
    private val tipLogDao: TipLogDao,
    private val zReportDao: ZReportDao,
    private val auditDao: AuditLogDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : ShiftRepository {

    override fun observeOpenShifts(storeId: StoreId): Flow<List<Shift>> =
        shiftDao.observeOpen(storeId.value).map { it.map { e -> e.toDomain() } }

    override suspend fun open(storeId: StoreId, registerId: RegisterId, employeeId: EmployeeId, startingCash: Money): Result<Shift> = Result.catching {
        // Close any open shift for this register first
        shiftDao.openForRegister(registerId.value)?.let { existing ->
            shiftDao.close(existing.id, clock.now(), existing.startingCashMinor, 0L, "Auto-closed by new shift")
        }
        val now = clock.now()
        val shift = Shift(
            id = Id(UUID.randomUUID().toString()),
            storeId = storeId, registerId = registerId, employeeId = employeeId,
            status = ShiftStatus.OPEN, startedAt = now, endedAt = null,
            startingCash = startingCash, expectedCash = Money.ZERO, countedCash = null,
            cashVariance = Money.ZERO, startingFloat = emptyMap(), countedFloat = emptyMap(),
            notes = null, salesTotal = Money.ZERO, tipsCollected = Money.ZERO,
            refundsTotal = Money.ZERO, payoutsTotal = Money.ZERO, transactionCount = 0
        )
        shiftDao.upsert(shift.toEntity())
        auditDao.insert(com.enterprise.pos.data.db.entity.AuditLogEntity(
            id = UUID.randomUUID().toString(), storeId = storeId.value, registerId = registerId.value,
            employeeId = employeeId.value, employeeName = "", action = AuditAction.SHIFT_OPENED.name,
            entityType = "Shift", entityId = shift.id.value, beforeJson = null, afterJson = null,
            reason = "Starting cash: ${startingCash.format()}", timestamp = now,
            ipAddress = null, deviceIdentifier = null
        ))
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "shifts", recordId = shift.id.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = now))
        shift
    }

    override suspend fun close(shiftId: Id<com.enterprise.pos.core.ShiftTag>, countedCash: Money, notes: String?): Result<Shift> = Result.catching {
        val now = clock.now()
        val current = shiftDao.get(shiftId.value)?.toDomain() ?: throw IllegalArgumentException("Shift not found")
        // Compute expected cash from payments
        val payments = paymentDao.between(current.startedAt, now)
        val cashPayments = payments.filter { it.provider == "CASH" }.sumOf { it.amountMinor }
        val cashOut = payments.filter { it.provider == "CASH" && it.refundedAmountMinor > 0 }.sumOf { it.refundedAmountMinor }
        val expected = current.startingCash.minorUnits + cashPayments - cashOut
        val variance = countedCash.minorUnits - expected
        shiftDao.close(shiftId.value, now, countedCash.minorUnits, variance, notes)
        val closed = current.copy(
            status = ShiftStatus.CLOSED, endedAt = now, countedCash = countedCash,
            expectedCash = Money.ofMinor(expected), cashVariance = Money.ofMinor(variance),
            notes = notes
        )
        auditDao.insert(com.enterprise.pos.data.db.entity.AuditLogEntity(
            id = UUID.randomUUID().toString(), storeId = current.storeId.value, registerId = current.registerId.value,
            employeeId = current.employeeId.value, employeeName = "", action = AuditAction.SHIFT_CLOSED.name,
            entityType = "Shift", entityId = shiftId.value, beforeJson = null, afterJson = null,
            reason = "Expected ${Money.ofMinor(expected).format()}, counted ${countedCash.format()}, variance ${Money.ofMinor(variance).format()}",
            timestamp = now, ipAddress = null, deviceIdentifier = null
        ))
        closed
    }

    override suspend fun get(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<Shift?> = Result.catching {
        shiftDao.get(shiftId.value)?.toDomain()
    }

    override suspend fun currentForRegister(registerId: RegisterId): Result<Shift?> = Result.catching {
        shiftDao.openForRegister(registerId.value)?.toDomain()
    }

    override suspend fun currentForEmployee(employeeId: EmployeeId): Result<Shift?> = Result.catching {
        shiftDao.openForEmployee(employeeId.value)?.toDomain()
    }

    override suspend fun generateZReport(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<ZReport> = Result.catching {
        val shift = shiftDao.get(shiftId.value)?.toDomain() ?: throw IllegalArgumentException("Shift not found")
        val now = clock.now()
        // Pull all orders for this register between shift start and now
        val recent = orderDao.recentOrders(shift.storeId.value, 500)
        val periodOrders = recent.filter { it.createdAt >= shift.startedAt && it.createdAt <= now && it.registerId == shift.registerId.value }
        val paid = periodOrders.filter { it.status == OrderStatus.PAID.name }.map { order ->
            order.toDomain(
                orderDao.linesFor(order.id),
                orderDao.taxLinesFor(order.id),
                orderDao.discountsFor(order.id),
                paymentDao.forOrder(order.id)
            )
        }
        val grossSales = paid.fold(Money.ZERO) { acc, o -> acc + o.subtotal }
        val refunds = Money.ZERO // would need to query returns table
        val discounts = paid.fold(Money.ZERO) { acc, o -> acc + o.totalDiscount }
        val net = grossSales - discounts
        val tips = paid.fold(Money.ZERO) { acc, o -> acc + o.tip }
        val taxByCode = paid
            .flatMap { it.taxLines }
            .groupBy { it.name }
            .mapValues { (_, lines) -> lines.fold(Money.ZERO) { acc, line -> acc + line.amount } }
        val payments = paymentDao.between(shift.startedAt, now)
        val cashTotal = payments.filter { it.provider == "CASH" }.sumOf { it.amountMinor }
        val cardTotal = payments.filter { it.provider in setOf("STRIPE","SQUARE","SHOPIFY","MANUAL") }.sumOf { it.amountMinor }
        val z = ZReport(
            id = Id(UUID.randomUUID().toString()),
            storeId = shift.storeId, registerId = shift.registerId, shiftId = shift.id,
            generatedAt = now, periodStart = shift.startedAt, periodEnd = now,
            grossSales = grossSales, returns = refunds, discounts = discounts, netSales = net,
            taxCollected = taxByCode,
            tips = tips,
            cashTotal = Money.ofMinor(cashTotal),
            cardTotal = Money.ofMinor(cardTotal),
            otherTenders = emptyMap(),
            overShort = shift.cashVariance,
            transactionCount = paid.size,
            refundCount = 0, voidCount = periodOrders.count { it.status == OrderStatus.VOIDED.name },
            noSaleCount = 0,
            employeeBreakdown = emptyMap()
        )
        zReportDao.upsert(z.toEntity())
        z
    }

    override suspend fun computeTipPool(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<TipPoolSummary> = Result.catching {
        val shift = shiftDao.get(shiftId.value)?.toDomain() ?: throw IllegalArgumentException("Shift not found")
        val totalTips = tipLogDao.sumBetween(shift.startedAt, shift.endedAt ?: clock.now())
        // Simplified: single-employee tip pool
        val entries = listOf(com.enterprise.pos.domain.model.TipPoolEntry(
            employeeId = shift.employeeId, hoursWorked = 8.0,
            tipsCollected = Money.ofMinor(totalTips),
            pooledTips = Money.ofMinor(totalTips),
            totalTakeHome = Money.ofMinor(totalTips)
        ))
        TipPoolSummary(shiftId, Money.ofMinor(totalTips), 8.0, entries, TipPoolType.NONE)
    }
}

class InventoryManagementRepositoryImpl(
    private val catalogDao: CatalogDao,
    private val adjustmentDao: InventoryAdjustmentDao,
    private val transferDao: InventoryTransferDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : InventoryManagementRepository {

    override fun observeLowStock(storeId: StoreId): Flow<List<InventorySnapshot>> = flow {
        emit(catalogDao.lowStockFor(storeId.value).map { it.toDomain() })
    }

    override fun observeAdjustments(storeId: StoreId, variantId: com.enterprise.pos.core.VariantId?): Flow<List<InventoryAdjustment>> =
        adjustmentDao.observe(storeId.value, variantId?.value).map { it.map { e -> e.toDomain() } }

    override fun observeTransfers(storeId: StoreId): Flow<List<InventoryTransfer>> =
        transferDao.observe(storeId.value).map { it.map { e -> e.toDomain() } }

    override suspend fun adjust(adjustment: InventoryAdjustment): Result<InventoryAdjustment> = Result.catching {
        adjustmentDao.insert(
            InventoryAdjustmentEntity(
                id = adjustment.id.value, variantId = adjustment.variantId.value, storeId = adjustment.storeId.value,
                delta = adjustment.delta, reason = adjustment.reason.name, notes = adjustment.notes,
                employeeId = adjustment.employeeId.value, timestamp = adjustment.timestamp,
                unitCostMinor = adjustment.unitCost?.minorUnits
            )
        )
        catalogDao.adjustInventory(adjustment.variantId.value, adjustment.storeId.value, adjustment.delta)
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "inventory_adjustments", recordId = adjustment.id.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = clock.now()))
        adjustment
    }

    override suspend fun createTransfer(transfer: InventoryTransfer): Result<InventoryTransfer> = Result.catching {
        transferDao.upsert(transfer.toEntity())
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "inventory_transfers", recordId = transfer.id.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = clock.now()))
        transfer
    }

    override suspend fun receiveTransfer(transferId: Id<com.enterprise.pos.domain.model.InventoryTransferTag>, receivedLines: List<TransferLine>): Result<InventoryTransfer> = Result.catching {
        val transfer = transferDao.get(transferId.value)?.toDomain() ?: throw IllegalArgumentException("Transfer not found")
        val now = clock.now()
        val updatedItems = transfer.items.map { line ->
            val recv = receivedLines.firstOrNull { it.variantId == line.variantId }?.receivedQuantity ?: 0
            line.copy(receivedQuantity = line.receivedQuantity + recv)
        }
        val updated = transfer.copy(items = updatedItems, status = TransferStatus.RECEIVED, receivedAt = now)
        transferDao.receive(transferId.value, TransferStatus.RECEIVED.name, now, Json.encodeToString(ListSerializer(TransferLine.serializer()), updatedItems))
        // Add inventory to receiving store
        updatedItems.forEach { line ->
            catalogDao.adjustInventory(line.variantId.value, transfer.toStoreId.value, line.receivedQuantity)
        }
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "inventory_transfers", recordId = transferId.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = now))
        updated
    }

    override suspend fun reorder(variantId: com.enterprise.pos.core.VariantId, storeId: StoreId, qty: Int): Result<Unit> = Result.catching {
        // Real: POST to PO system / vendor API
        // For now, log an audit entry and bump on-hand by qty
        catalogDao.adjustInventory(variantId.value, storeId.value, qty)
    }

    override suspend fun reorderAll(storeId: StoreId): Result<Int> = Result.catching {
        // Real: iterate all variants at or below reorderPoint and create POs
        0
    }

    override suspend fun valuation(storeId: StoreId): Result<Money> = Result.catching {
        // Real: SUM(onHand * costPrice) for all variants in store
        Money.ZERO
    }
}

class ReturnsRepositoryImpl(
    private val returnDao: ReturnDao,
    private val orderDao: OrderDao,
    private val paymentDao: PaymentDao,
    private val auditDao: AuditLogDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : ReturnsRepository {

    override suspend fun initiate(originalOrderId: OrderId, lines: List<com.enterprise.pos.domain.model.ReturnLine>, reason: String, employeeId: EmployeeId): Result<ReturnRequest> = Result.catching {
        val now = clock.now()
        val order = orderDao.get(originalOrderId.value) ?: throw IllegalArgumentException("Original order not found")
        val total = lines.fold(Money.ZERO) { acc, l -> acc + l.refundAmount }
        val ret = ReturnRequest(
            id = Id(UUID.randomUUID().toString()), originalOrderId = originalOrderId,
            storeId = StoreId(order.storeId), employeeId = employeeId, lines = lines,
            totalRefund = total, reason = reason, timestamp = now,
            refundTenders = emptyList(), status = com.enterprise.pos.domain.model.ReturnStatus.DRAFT
        )
        returnDao.upsert(ret.toEntity())
        auditDao.insert(com.enterprise.pos.data.db.entity.AuditLogEntity(
            id = UUID.randomUUID().toString(), storeId = order.storeId, registerId = order.registerId,
            employeeId = employeeId.value, employeeName = "", action = AuditAction.ORDER_REFUNDED.name,
            entityType = "Order", entityId = originalOrderId.value, beforeJson = null, afterJson = null,
            reason = reason, timestamp = now, ipAddress = null, deviceIdentifier = null
        ))
        ret
    }

    override suspend fun processRefund(returnId: Id<com.enterprise.pos.domain.model.ReturnTag>, tenders: List<com.enterprise.pos.domain.model.TenderSplit>): Result<ReturnRequest> = Result.catching {
        val ret = returnDao.get(returnId.value)?.toDomain() ?: throw IllegalArgumentException("Return not found")
        val updated = ret.copy(refundTenders = tenders, status = com.enterprise.pos.domain.model.ReturnStatus.COMPLETED)
        returnDao.upsert(updated.toEntity())
        syncDao.enqueue(com.enterprise.pos.data.db.entity.SyncQueueEntity(tableName = "returns", recordId = returnId.value, operation = "UPSERT", payloadJson = "{}", enqueuedAt = clock.now()))
        updated
    }

    override suspend fun get(id: Id<com.enterprise.pos.domain.model.ReturnTag>): Result<ReturnRequest?> = Result.catching {
        returnDao.get(id.value)?.toDomain()
    }

    override suspend fun history(storeId: StoreId, limit: Int): Result<List<ReturnRequest>> = Result.catching {
        returnDao.history(storeId.value, limit).map { it.toDomain() }
    }
}

class AuditLogRepositoryImpl(
    private val dao: AuditLogDao,
    private val clock: Clock = SystemClock
) : AuditLogRepository {
    override fun observe(storeId: StoreId, action: AuditAction?, limit: Int): Flow<List<AuditLogEntry>> =
        dao.observe(storeId.value, action?.name, limit).map { it.map { e -> e.toDomain() } }

    override fun observeForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntry>> =
        dao.observeForEntity(entityType, entityId).map { it.map { e -> e.toDomain() } }

    override suspend fun log(entry: AuditLogEntry): Result<Unit> = Result.catching {
        dao.insert(entry.toEntity())
    }

    override suspend fun logAction(
        storeId: StoreId, registerId: RegisterId?, employeeId: EmployeeId, employeeName: String,
        action: AuditAction, entityType: String, entityId: String,
        beforeJson: String?, afterJson: String?, reason: String?
    ): Result<Unit> = Result.catching {
        dao.insert(com.enterprise.pos.data.db.entity.AuditLogEntity(
            id = UUID.randomUUID().toString(), storeId = storeId.value, registerId = registerId?.value,
            employeeId = employeeId.value, employeeName = employeeName, action = action.name,
            entityType = entityType, entityId = entityId, beforeJson = beforeJson, afterJson = afterJson,
            reason = reason, timestamp = clock.now(), ipAddress = null, deviceIdentifier = null
        ))
    }

    override suspend fun export(storeId: StoreId, from: Long, to: Long): Result<List<AuditLogEntry>> = Result.catching {
        dao.range(storeId.value, from, to).map { it.toDomain() }
    }
}

class AnalyticsRepositoryImpl(
    private val orderDao: OrderDao,
    private val catalogDao: CatalogDao,
    private val employeeDao: EmployeeDao,
    private val paymentDao: PaymentDao,
    private val syncDao: SyncQueueDao,
    private val clock: Clock = SystemClock
) : AnalyticsRepository {

    override suspend fun dashboard(storeId: StoreId, now: Long): Result<DashboardSnapshot> = Result.catching {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val last7Start = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val last30Start = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

        val allOrders = orderDao.recentOrders(storeId.value, 500).map { e ->
            Mappers.run {
                val lines = orderDao.linesFor(e.id)
                val taxLines = orderDao.taxLinesFor(e.id)
                val discounts = orderDao.discountsFor(e.id)
                e.toDomain(lines, taxLines, discounts)
            }
        }
        val todayOrders = allOrders.filter { it.createdAt >= todayStart && it.status == OrderStatus.PAID }
        val yesterdayOrders = allOrders.filter { it.createdAt in yesterdayStart until todayStart && it.status == OrderStatus.PAID }
        val last7Orders = allOrders.filter { it.createdAt >= last7Start && it.status == OrderStatus.PAID }
        val last30Orders = allOrders.filter { it.createdAt >= last30Start && it.status == OrderStatus.PAID }
        val activeOrders = allOrders.count { it.isOpen }
        val todaySales = todayOrders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
        val yesterdaySales = yesterdayOrders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
        val last7Sales = last7Orders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
        val last30Sales = last30Orders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
        val todayTips = todayOrders.fold(Money.ZERO) { a, o -> a + o.tip }
        val todayPayments = paymentDao.between(todayStart, now)
        val todayRefunds = Money.ofMinor(todayPayments.sumOf { it.refundedAmountMinor })
        val todayAov = if (todayOrders.isEmpty()) Money.ZERO else Money.ofMinor(todaySales.minorUnits / todayOrders.size)

        val hourly = (0..23).map { h ->
            val hourOrders = todayOrders.filter { Instant.ofEpochMilli(it.closedAt ?: it.createdAt).atZone(zone).hour == h }
            val gross = hourOrders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
            SalesByHour(
                hour = h, grossSales = gross, netSales = gross,
                transactionCount = hourOrders.size,
                averageOrderValue = if (hourOrders.isEmpty()) Money.ZERO else Money.ofMinor(gross.minorUnits / hourOrders.size),
                itemsSold = hourOrders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }.sumOf { it.quantity.asInt }
            )
        }
        val topProducts = todayOrders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }
            .groupBy { it.name }
            .map { (n, ls) -> n to ls.sumOf { it.quantity.asInt } }
            .sortedByDescending { it.second }
            .take(10)
        val topEmployees = todayOrders.groupBy { it.employeeId }
            .map { (empId, orders) ->
                val total = orders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
                SalesByEmployee(empId, "", total, orders.size, orders.fold(Money.ZERO) { a, o -> a + o.tip }, orders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }.sumOf { it.quantity.asInt }, if (orders.isEmpty()) Money.ZERO else Money.ofMinor(total.minorUnits / orders.size), 0.0)
            }
            .sortedByDescending { it.grossSales.minorUnits }
            .take(5)
        val paymentMix = todayPayments
            .groupBy { it.provider }
            .mapValues { (_, payments) -> Money.ofMinor(payments.sumOf { it.amountMinor }) }
        val diningMix = todayOrders.groupBy { it.diningMode.name }.mapValues { it.value.size }
        val lowStockCount = 0 // would query inventory
        val pendingSyncCount = syncDao.observeCount().first()
        val alerts = mutableListOf<DashboardAlert>()
        if (todaySales < yesterdaySales * BigDecimal.valueOf(0.8)) {
            alerts.add(DashboardAlert(AlertSeverity.WARNING, AlertCategory.REVENUE, "Sales Drop", "Today's sales are ${(1 - todaySales.minorUnits.toDouble() / yesterdaySales.minorUnits.coerceAtLeast(1)) * 100}% below yesterday", null, now))
        }
        DashboardSnapshot(
            todaySales = todaySales, todayTransactions = todayOrders.size,
            todayAverageOrder = todayAov, todayTips = todayTips, todayRefunds = todayRefunds,
            yesterdaySales = yesterdaySales, last7DaysSales = last7Sales, last30DaysSales = last30Sales,
            activeOrders = activeOrders, openTables = 0, lowStockItems = lowStockCount,
            pendingSyncCount = pendingSyncCount, hourlySales = hourly,
            topProducts = topProducts, topEmployees = topEmployees,
            paymentMix = paymentMix, diningMix = diningMix, alerts = alerts
        )
    }

    override suspend fun salesByHour(storeId: StoreId, from: Long, to: Long): Result<List<SalesByHour>> = Result.catching {
        val zone = ZoneId.systemDefault()
        val orders = orderDao.recentOrders(storeId.value, 1000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        (0..23).map { h ->
            val hourOrders = orders.filter { Instant.ofEpochMilli(it.closedAt ?: it.createdAt).atZone(zone).hour == h }
            val gross = hourOrders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
            SalesByHour(h, gross, gross, hourOrders.size, if (hourOrders.isEmpty()) Money.ZERO else Money.ofMinor(gross.minorUnits / hourOrders.size), hourOrders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }.sumOf { it.quantity.asInt })
        }
    }

    override suspend fun salesByCategory(storeId: StoreId, from: Long, to: Long): Result<List<SalesByCategory>> = Result.catching {
        val orders = orderDao.recentOrders(storeId.value, 1000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        val totalRev = orders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }.sumOf { it.lineTotal.minorUnits }.coerceAtLeast(1)
        // For simplicity, group by product name; real impl joins to categories via catalogDao
        orders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }
            .groupBy { it.productId?.value ?: "unknown" }
            .map { (pid, ls) ->
                val revenue = ls.fold(Money.ZERO) { a, l -> a + l.lineTotal }
                SalesByCategory(com.enterprise.pos.core.CategoryId(pid), pid, revenue, ls.sumOf { it.quantity.asInt }, ls.size, revenue.minorUnits.toDouble() / totalRev * 100)
            }
            .sortedByDescending { it.grossSales.minorUnits }
    }

    override suspend fun salesByEmployee(storeId: StoreId, from: Long, to: Long): Result<List<SalesByEmployee>> = Result.catching {
        val orders = orderDao.recentOrders(storeId.value, 1000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        val employees = employeeDao.observeActive().first().associateBy { it.id }
        orders.groupBy { it.employeeId }.map { (empId, os) ->
            val total = os.fold(Money.ZERO) { a, o -> a + o.grandTotal }
            val tips = os.fold(Money.ZERO) { a, o -> a + o.tip }
            SalesByEmployee(
                employeeId = empId,
                employeeName = employees[empId.value]?.name ?: empId.value.takeLast(6),
                grossSales = total, transactionCount = os.size, tipsCollected = tips,
                itemsSold = os.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }.sumOf { it.quantity.asInt },
                averageOrderValue = if (os.isEmpty()) Money.ZERO else Money.ofMinor(total.minorUnits / os.size),
                hoursWorked = 0.0
            )
        }.sortedByDescending { it.grossSales.minorUnits }
    }

    override suspend fun taxLiability(storeId: StoreId, from: Long, to: Long): Result<com.enterprise.pos.domain.model.TaxLiabilityReport> = Result.catching {
        val orders = orderDao.recentOrders(storeId.value, 1000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        val taxByCode = mutableMapOf<String, Long>()
        var taxable = 0L
        var exempt = 0L
        var gross = 0L
        for (o in orders) {
            gross += o.grandTotal.minorUnits
            taxable += o.taxableAmount.minorUnits
            for (t in o.taxLines) taxByCode[t.name] = (taxByCode[t.name] ?: 0L) + t.amount.minorUnits
        }
        com.enterprise.pos.domain.model.TaxLiabilityReport(
            period = "$from..$to",
            taxCollected = taxByCode.mapValues { Money.ofMinor(it.value) },
            taxCollectedTotal = Money.ofMinor(taxByCode.values.sum()),
            taxableSales = Money.ofMinor(taxable),
            exemptSales = Money.ZERO,
            nonTaxableSales = Money.ZERO,
            grossSales = Money.ofMinor(gross)
        )
    }

    override suspend fun abcAnalysis(storeId: StoreId, from: Long, to: Long): Result<List<AbcAnalysis>> = Result.catching {
        val orders = orderDao.recentOrders(storeId.value, 1000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        val raw = orders.flatMap { it.lines.filter { l -> l.lineType == OrderLineType.ITEM } }
            .groupBy { it.productId?.value ?: "unknown" }
            .map { (pid, ls) ->
                AbcAnalysis(
                    productId = com.enterprise.pos.core.ProductId(pid),
                    productName = ls.first().name,
                    unitsSold = ls.sumOf { it.quantity.asInt },
                    revenue = ls.fold(Money.ZERO) { a, l -> a + l.lineTotal },
                    revenueContribution = 0.0, cumulativeContribution = 0.0,
                    classification = AbcClass.C
                )
            }
        com.enterprise.pos.domain.service.AbcAnalysisEngine().classify(raw)
    }

    override suspend fun hourlyHeatmap(storeId: StoreId, daysBack: Int): Result<Map<Int, Double>> = Result.catching {
        val zone = ZoneId.systemDefault()
        val to = clock.now()
        val from = Instant.ofEpochMilli(to).atZone(zone).toLocalDate().minusDays(daysBack.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val orders = orderDao.recentOrders(storeId.value, 2000)
            .map { e -> e.toDomain(orderDao.linesFor(e.id), orderDao.taxLinesFor(e.id), orderDao.discountsFor(e.id)) }
            .filter { it.createdAt in from..to && it.status == OrderStatus.PAID }
        val byHour = (0..23).associateWith { h ->
            val hourOrders = orders.filter { Instant.ofEpochMilli(it.closedAt ?: it.createdAt).atZone(zone).hour == h }
            if (hourOrders.isEmpty()) 0.0
            else hourOrders.sumOf { it.grandTotal.minorUnits } / daysBack.toDouble() / 100.0
        }
        byHour
    }
}

class MigrationRepositoryImpl(
    private val dao: MigrationJobDao,
    private val catalogDao: CatalogDao,
    private val customerDao: com.enterprise.pos.data.db.dao.CustomerDao,
    private val auditDao: AuditLogDao,
    private val clock: Clock = SystemClock
) : MigrationRepository {
    override fun observeJobs(): Flow<List<MigrationJob>> = dao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun createJob(source: MigrationSource, type: MigrationType, configJson: String, createdBy: EmployeeId): Result<MigrationJob> = Result.catching {
        val job = MigrationJob(
            id = Id(UUID.randomUUID().toString()), source = source, type = type,
            status = MigrationStatus.PENDING, totalRecords = 0, processedRecords = 0,
            failedRecords = 0, startedAt = null, completedAt = null, errorMessage = null,
            configJson = configJson, createdBy = createdBy, createdAt = clock.now(), conflicts = emptyList()
        )
        dao.upsert(job.toEntity())
        job
    }

    override suspend fun startJob(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>): Result<MigrationJob> = Result.catching {
        val now = clock.now()
        val job = dao.get(jobId.value)?.toDomain() ?: throw IllegalArgumentException("Job not found")
        val updated = job.copy(status = MigrationStatus.IN_PROGRESS, startedAt = now)
        dao.upsert(updated.toEntity())
        updated
    }

    override suspend fun cancelJob(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>): Result<Unit> = Result.catching {
        dao.complete(jobId.value, MigrationStatus.CANCELLED.name, clock.now())
    }

    override suspend fun resolveConflict(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>, conflict: MigrationConflict): Result<Unit> = Result.catching {
        throw UnsupportedOperationException("Migration conflict resolution requires the backend migration worker")
    }

    override suspend fun importFromShopify(configJson: String, createdBy: EmployeeId): Result<MigrationJob> =
        createJob(MigrationSource.SHOPIFY, MigrationType.ALL, configJson, createdBy)

    override suspend fun importFromSquare(configJson: String, createdBy: EmployeeId): Result<MigrationJob> =
        createJob(MigrationSource.SQUARE, MigrationType.ALL, configJson, createdBy)

    override suspend fun importFromStripe(configJson: String, createdBy: EmployeeId): Result<MigrationJob> =
        createJob(MigrationSource.STRIPE, MigrationType.PAYMENTS, configJson, createdBy)

    override suspend fun importFromCsv(configJson: String, createdBy: EmployeeId): Result<MigrationJob> =
        createJob(MigrationSource.OTHER, MigrationType.PRODUCTS, configJson, createdBy)
}

class SettingRepositoryImpl(private val dao: SettingDao, private val clock: Clock = SystemClock) {
    suspend fun get(key: String): Result<String?> = Result.catching {
        dao.get(key)?.valueJson
    }
    suspend fun set(key: String, valueJson: String, updatedBy: String?): Result<Unit> = Result.catching {
        dao.upsert(SettingEntity(key = key, valueJson = valueJson, updatedAt = clock.now(), updatedBy = updatedBy))
    }
    fun observeAll() = dao.observeAll()
}
