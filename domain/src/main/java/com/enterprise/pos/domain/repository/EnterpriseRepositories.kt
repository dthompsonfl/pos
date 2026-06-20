package com.enterprise.pos.domain.repository

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AuditAction
import com.enterprise.pos.domain.model.AuditLogEntry
import com.enterprise.pos.domain.model.DashboardSnapshot
import com.enterprise.pos.domain.model.GiftCard
import com.enterprise.pos.domain.model.GiftCardTransaction
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.enterprise.pos.domain.model.InventoryTransfer
import com.enterprise.pos.domain.model.LoyaltyReward
import com.enterprise.pos.domain.model.MigrationConflict
import com.enterprise.pos.domain.model.MigrationJob
import com.enterprise.pos.domain.model.MigrationSource
import com.enterprise.pos.domain.model.MigrationType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.ReturnRequest
import com.enterprise.pos.domain.model.SalesByCategory
import com.enterprise.pos.domain.model.SalesByEmployee
import com.enterprise.pos.domain.model.SalesByHour
import com.enterprise.pos.domain.model.Shift
import com.enterprise.pos.domain.model.TipPoolSummary
import com.enterprise.pos.domain.model.ZReport
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    fun observeReservations(storeId: StoreId, date: Long): Flow<List<Reservation>>
    fun observeUpcoming(storeId: StoreId, hours: Int): Flow<List<Reservation>>
    suspend fun get(id: Id<com.enterprise.pos.domain.model.ReservationTag>): Result<Reservation?>
    suspend fun upsert(r: Reservation): Result<Reservation>
    suspend fun setStatus(id: Id<com.enterprise.pos.domain.model.ReservationTag>, status: ReservationStatus): Result<Unit>
    suspend fun seat(id: Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: TableId): Result<Unit>
    suspend fun cancel(id: Id<com.enterprise.pos.domain.model.ReservationTag>, reason: String): Result<Unit>
    suspend fun checkTableAvailability(storeId: StoreId, date: Long, partySize: Int): Result<List<com.enterprise.pos.domain.model.RestaurantTable>>
}

interface GiftCardRepository {
    fun observe(code: String): Flow<GiftCard?>
    suspend fun get(id: Id<com.enterprise.pos.domain.model.GiftCardTag>): Result<GiftCard?>
    suspend fun getByCode(code: String): Result<GiftCard?>
    suspend fun issue(storeId: StoreId, initialBalance: Money, customerId: com.enterprise.pos.core.CustomerId?, notes: String?): Result<GiftCard>
    suspend fun reload(id: Id<com.enterprise.pos.domain.model.GiftCardTag>, amount: Money, employeeId: EmployeeId): Result<GiftCard>
    suspend fun redeem(code: String, amount: Money, orderId: OrderId?, employeeId: EmployeeId): Result<GiftCard>
    suspend fun transactions(giftCardId: Id<com.enterprise.pos.domain.model.GiftCardTag>): Result<List<GiftCardTransaction>>
    suspend fun adjustBalance(id: Id<com.enterprise.pos.domain.model.GiftCardTag>, newBalance: Money, reason: String, employeeId: EmployeeId): Result<GiftCard>
}

interface PromotionRepository {
    fun observePromotions(): Flow<List<Promotion>>
    fun observeLoyaltyRewards(): Flow<List<LoyaltyReward>>
    suspend fun upsert(p: Promotion): Result<Promotion>
    suspend fun delete(id: Id<com.enterprise.pos.domain.model.PromotionTag>): Result<Unit>
    suspend fun activeFor(storeId: StoreId, now: Long): Result<List<Promotion>>
    suspend fun validateCode(code: String): Result<Promotion?>
    suspend fun recordRedemption(id: Id<com.enterprise.pos.domain.model.PromotionTag>): Result<Unit>
}

interface ShiftRepository {
    fun observeOpenShifts(storeId: StoreId): Flow<List<Shift>>
    suspend fun open(storeId: StoreId, registerId: RegisterId, employeeId: EmployeeId, startingCash: Money): Result<Shift>
    suspend fun close(shiftId: Id<com.enterprise.pos.core.ShiftTag>, countedCash: Money, notes: String?): Result<Shift>
    suspend fun get(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<Shift?>
    suspend fun currentForRegister(registerId: RegisterId): Result<Shift?>
    suspend fun currentForEmployee(employeeId: EmployeeId): Result<Shift?>
    suspend fun generateZReport(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<ZReport>
    suspend fun computeTipPool(shiftId: Id<com.enterprise.pos.core.ShiftTag>): Result<TipPoolSummary>
}

interface InventoryManagementRepository {
    fun observeLowStock(storeId: StoreId): Flow<List<com.enterprise.pos.domain.model.InventorySnapshot>>
    fun observeAdjustments(storeId: StoreId, variantId: com.enterprise.pos.core.VariantId?): Flow<List<InventoryAdjustment>>
    fun observeTransfers(storeId: StoreId): Flow<List<InventoryTransfer>>
    suspend fun adjust(adjustment: InventoryAdjustment): Result<InventoryAdjustment>
    suspend fun createTransfer(transfer: InventoryTransfer): Result<InventoryTransfer>
    suspend fun receiveTransfer(transferId: Id<com.enterprise.pos.domain.model.InventoryTransferTag>, receivedLines: List<com.enterprise.pos.domain.model.TransferLine>): Result<InventoryTransfer>
    suspend fun reorder(variantId: com.enterprise.pos.core.VariantId, storeId: StoreId, qty: Int): Result<Unit>
    suspend fun reorderAll(storeId: StoreId): Result<Int> // returns # items reordered
    suspend fun valuation(storeId: StoreId): Result<Money>

    // Detail & history
    suspend fun getInventoryItem(variantId: com.enterprise.pos.core.VariantId, storeId: StoreId): Result<com.enterprise.pos.domain.model.InventoryItem?>
    fun observeStockMovements(storeId: StoreId, variantId: com.enterprise.pos.core.VariantId): Flow<List<com.enterprise.pos.domain.model.StockMovement>>
    suspend fun setReorderPoint(variantId: com.enterprise.pos.core.VariantId, storeId: StoreId, point: Int, qty: Int): Result<Unit>

    // Suppliers & purchase orders
    suspend fun getSupplier(id: Id<com.enterprise.pos.domain.model.SupplierTag>): Result<com.enterprise.pos.domain.model.Supplier?>
    fun observeSuppliers(storeId: StoreId): Flow<List<com.enterprise.pos.domain.model.Supplier>>
    suspend fun getSupplierPerformance(supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>): Result<com.enterprise.pos.domain.model.SupplierPerformance>
    suspend fun observePurchaseOrders(storeId: StoreId, supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>?): Flow<List<com.enterprise.pos.domain.model.PurchaseOrder>>
    suspend fun getPurchaseOrder(id: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>): Result<com.enterprise.pos.domain.model.PurchaseOrder?>
    suspend fun upsertPurchaseOrder(po: com.enterprise.pos.domain.model.PurchaseOrder): Result<com.enterprise.pos.domain.model.PurchaseOrder>
    suspend fun sendPurchaseOrder(id: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>): Result<com.enterprise.pos.domain.model.PurchaseOrder>
    suspend fun receivePurchaseOrder(id: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>): Result<com.enterprise.pos.domain.model.PurchaseOrder>
    suspend fun cancelPurchaseOrder(id: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>): Result<com.enterprise.pos.domain.model.PurchaseOrder>
}

interface ReturnsRepository {
    suspend fun initiate(originalOrderId: OrderId, lines: List<com.enterprise.pos.domain.model.ReturnLine>, reason: String, employeeId: EmployeeId): Result<ReturnRequest>
    suspend fun processRefund(returnId: Id<com.enterprise.pos.domain.model.ReturnTag>, tenders: List<com.enterprise.pos.domain.model.TenderSplit>): Result<ReturnRequest>
    suspend fun get(id: Id<com.enterprise.pos.domain.model.ReturnTag>): Result<ReturnRequest?>
    suspend fun history(storeId: StoreId, limit: Int): Result<List<ReturnRequest>>
}

interface AuditLogRepository {
    fun observe(storeId: StoreId, action: AuditAction?, limit: Int): Flow<List<AuditLogEntry>>
    fun observeForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntry>>
    suspend fun log(entry: AuditLogEntry): Result<Unit>
    suspend fun logAction(
        storeId: StoreId, registerId: RegisterId?, employeeId: EmployeeId, employeeName: String,
        action: AuditAction, entityType: String, entityId: String,
        beforeJson: String? = null, afterJson: String? = null, reason: String? = null
    ): Result<Unit>
    suspend fun export(storeId: StoreId, from: Long, to: Long): Result<List<AuditLogEntry>>
}

interface AnalyticsRepository {
    suspend fun dashboard(storeId: StoreId, now: Long): Result<DashboardSnapshot>
    suspend fun salesByHour(storeId: StoreId, from: Long, to: Long): Result<List<SalesByHour>>
    suspend fun salesByCategory(storeId: StoreId, from: Long, to: Long): Result<List<SalesByCategory>>
    suspend fun salesByEmployee(storeId: StoreId, from: Long, to: Long): Result<List<SalesByEmployee>>
    suspend fun taxLiability(storeId: StoreId, from: Long, to: Long): Result<com.enterprise.pos.domain.model.TaxLiabilityReport>
    suspend fun abcAnalysis(storeId: StoreId, from: Long, to: Long): Result<List<AbcAnalysis>>
    suspend fun hourlyHeatmap(storeId: StoreId, daysBack: Int): Result<Map<Int, Double>> // hour -> avg revenue
}

interface MigrationRepository {
    fun observeJobs(): Flow<List<MigrationJob>>
    suspend fun createJob(source: MigrationSource, type: MigrationType, configJson: String, createdBy: EmployeeId): Result<MigrationJob>
    suspend fun startJob(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>): Result<MigrationJob>
    suspend fun cancelJob(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>): Result<Unit>
    suspend fun resolveConflict(jobId: Id<com.enterprise.pos.domain.model.MigrationJobTag>, conflict: MigrationConflict): Result<Unit>
    suspend fun importFromShopify(configJson: String, createdBy: EmployeeId): Result<MigrationJob>
    suspend fun importFromSquare(configJson: String, createdBy: EmployeeId): Result<MigrationJob>
    suspend fun importFromStripe(configJson: String, createdBy: EmployeeId): Result<MigrationJob>
    suspend fun importFromCsv(configJson: String, createdBy: EmployeeId): Result<MigrationJob>
}
