package com.enterprise.pos.domain.model

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.IdTag
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.PaymentId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.ShiftTag
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import kotlinx.serialization.Serializable

// ============================================================
// RESERVATIONS
// ============================================================

@Serializable
enum class ReservationStatus { REQUESTED, CONFIRMED, SEATED, CANCELLED, NO_SHOW, COMPLETED }

@Serializable
data class Reservation(
    val id: Id<ReservationTag>,
    val storeId: StoreId,
    val customerName: String,
    val customerId: CustomerId? = null,
    val phone: String,
    val email: String? = null,
    val partySize: Int,
    val requestedAt: Long, // epoch millis of reservation time
    val tableId: TableId? = null,
    val status: ReservationStatus = ReservationStatus.REQUESTED,
    val notes: String? = null,
    val dietaryRestrictions: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val confirmedAt: Long? = null,
    val seatedAt: Long? = null,
    val cancelledAt: Long? = null,
    val reminderSent: Boolean = false
)

interface ReservationTag : IdTag

// ============================================================
// GIFT CARDS
// ============================================================

@Serializable
data class GiftCard(
    val id: Id<GiftCardTag>,
    val code: String, // 16-digit code
    val storeId: StoreId,
    val balance: Money,
    val initialBalance: Money,
    val issuedAt: Long,
    val expiresAt: Long? = null,
    val customerId: CustomerId? = null,
    val notes: String? = null,
    val active: Boolean = true
)

@Serializable
data class GiftCardTransaction(
    val id: Id<GiftCardTransactionTag>,
    val giftCardId: Id<GiftCardTag>,
    val orderId: OrderId? = null,
    val amount: Money, // positive = reload, negative = redemption
    val balanceAfter: Money,
    val timestamp: Long,
    val employeeId: EmployeeId,
    val type: GiftCardTxType
)

@Serializable
enum class GiftCardTxType { ISSUED, RELOAD, REDEEM, REFUND, ADJUST }

interface GiftCardTag : IdTag
interface GiftCardTransactionTag : IdTag

// ============================================================
// AUDIT LOG — every void, refund, discount, comp, manual override
// ============================================================

@Serializable
enum class AuditAction {
    ORDER_CREATED, ORDER_VOIDED, ORDER_REFUNDED, ORDER_REOPENED, ORDER_PAID,
    ITEM_ADDED, ITEM_REMOVED, QUANTITY_CHANGED,
    LINE_ADDED, LINE_REMOVED, LINE_COMPED, LINE_PRICE_OVERRIDE,
    DISCOUNT_APPLIED, DISCOUNT_REMOVED, MANAGER_OVERRIDE_GRANTED,
    TIP_ADDED, TIP_REMOVED,
    CHECKOUT_STARTED, PAYMENT_STARTED, PAYMENT_AUTHORIZED,
    PAYMENT_CAPTURED, PAYMENT_VOIDED, PAYMENT_REFUNDED, PAYMENT_FAILED,
    RECEIPT_PRINTED, RECEIPT_QUEUED,
    INVENTORY_DECREMENTED, INVENTORY_ADJUSTED, INVENTORY_TRANSFER,
    ORDER_SYNC_QUEUED,
    DRAWER_OPENED, DRAWER_COUNT, NO_SALE, PAID_IN, PAID_OUT,
    EMPLOYEE_LOGIN, EMPLOYEE_LOGOUT, EMPLOYEE_SWITCH,
    PRICE_OVERRIDE, TAX_OVERRIDE,
    CONFIG_CHANGED, SETTINGS_CHANGED,
    SYNC_FORCE, SYNC_CONFLICT_RESOLVED,
    SHIFT_OPENED, SHIFT_CLOSED,
    GIFT_CARD_ISSUED, GIFT_CARD_RELOADED, GIFT_CARD_REDEEMED,
    REFUND_ISSUED, REFUND_REVERSED,
    MIGRATION_STARTED, MIGRATION_COMPLETED, MIGRATION_CONFLICT_RESOLVED
}

@Serializable
data class AuditLogEntry(
    val id: Id<AuditLogTag>,
    val storeId: StoreId,
    val registerId: RegisterId?,
    val employeeId: EmployeeId,
    val employeeName: String,
    val action: AuditAction,
    val entityType: String,
    val entityId: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val reason: String? = null,
    val timestamp: Long,
    val ipAddress: String? = null,
    val deviceIdentifier: String? = null
)

interface AuditLogTag : IdTag

// ============================================================
// PROMOTIONS & LOYALTY
// ============================================================

@Serializable
enum class PromotionType {
    PERCENT_OFF, FIXED_OFF, BUY_X_GET_Y, FREE_ITEM, HAPPY_HOUR, COMBO, LOYALTY_REWARD
}

@Serializable
enum class PromotionScope { ORDER, CATEGORY, PRODUCT, CHEAPEST_ITEM, ALL_ITEMS }

@Serializable
data class Promotion(
    val id: Id<PromotionTag>,
    val name: String,
    val description: String,
    val type: PromotionType,
    val scope: PromotionScope,
    val value: Money? = null, // for FIXED_OFF
    val percent: Int = 0, // for PERCENT_OFF
    val buyQty: Int = 0, // for BUY_X_GET_Y
    val getQty: Int = 0, // for BUY_X_GET_Y
    val freeItemProductId: com.enterprise.pos.core.ProductId? = null,
    val categoryIds: List<com.enterprise.pos.core.CategoryId> = emptyList(),
    val productIds: List<com.enterprise.pos.core.ProductId> = emptyList(),
    val startTime: Long, // happy hour start (epoch)
    val endTime: Long, // happy hour end
    val daysOfWeek: Set<Int> = emptySet(), // 1=Mon..7=Sun
    val priority: Int = 0, // higher = applied first
    val requiresCode: Boolean = false,
    val code: String? = null,
    val active: Boolean = true,
    val maxRedemptions: Int? = null,
    val redemptionCount: Int = 0,
    val maxRedemptionsPerCustomer: Int? = null
)

interface PromotionTag : IdTag

@Serializable
data class LoyaltyReward(
    val id: Id<LoyaltyRewardTag>,
    val name: String,
    val pointsCost: Int,
    val rewardType: LoyaltyRewardType,
    val value: Money? = null,
    val freeItemProductId: com.enterprise.pos.core.ProductId? = null,
    val active: Boolean = true
)

@Serializable
enum class LoyaltyRewardType { STORE_CREDIT, FREE_ITEM, PERCENT_OFF_NEXT, FREE_DRINK }

interface LoyaltyRewardTag : IdTag

// ============================================================
// SHIFTS & END OF DAY
// ============================================================

@Serializable
enum class ShiftStatus { OPEN, CLOSED, REOPENED, FORCE_CLOSED }

@Serializable
data class Shift(
    val id: Id<ShiftTag>,
    val storeId: StoreId,
    val registerId: RegisterId,
    val employeeId: EmployeeId,
    val status: ShiftStatus,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startingCash: Money,
    val expectedCash: Money = Money.ZERO,
    val countedCash: Money? = null,
    val cashVariance: Money = Money.ZERO,
    val startingFloat: Map<Int, Int> = emptyMap(), // denomination -> count
    val countedFloat: Map<Int, Int> = emptyMap(),
    val notes: String? = null,
    val salesTotal: Money = Money.ZERO,
    val tipsCollected: Money = Money.ZERO,
    val refundsTotal: Money = Money.ZERO,
    val payoutsTotal: Money = Money.ZERO,
    val transactionCount: Int = 0
)

@Serializable
data class ZReport(
    val id: Id<ZReportTag>,
    val storeId: StoreId,
    val registerId: RegisterId,
    val shiftId: Id<ShiftTag>,
    val generatedAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val grossSales: Money,
    val returns: Money,
    val discounts: Money,
    val netSales: Money,
    val taxCollected: Map<String, Money>, // tax name -> amount
    val tips: Money,
    val cashTotal: Money,
    val cardTotal: Money,
    val otherTenders: Map<String, Money>,
    val overShort: Money,
    val transactionCount: Int,
    val refundCount: Int,
    val voidCount: Int,
    val noSaleCount: Int,
    val employeeBreakdown: Map<EmployeeId, Money>
)

interface ZReportTag : IdTag

@Serializable
data class TipPoolEntry(
    val employeeId: EmployeeId,
    val hoursWorked: Double,
    val tipsCollected: Money,
    val pooledTips: Money,
    val totalTakeHome: Money
)

@Serializable
data class TipPoolSummary(
    val shiftId: Id<ShiftTag>,
    val totalTips: Money,
    val totalHours: Double,
    val entries: List<TipPoolEntry>,
    val poolType: TipPoolType
)

@Serializable
enum class TipPoolType { NONE, EVEN_SPLIT, HOURS_WEIGHTED, ROLE_WEIGHTED }

@Serializable
sealed class TipSuggestion {
    @Serializable
    data class Percentage(val percent: Int) : TipSuggestion()

    @Serializable
    data class Fixed(val amount: Money) : TipSuggestion()

    @Serializable
    data object Custom : TipSuggestion()

    @Serializable
    data object NoTip : TipSuggestion()
}

// ============================================================
// INVENTORY ADJUSTMENTS & TRANSFERS
// ============================================================

@Serializable
enum class AdjustmentReason {
    RECEIVED, DAMAGED, SPOILED, THEFT, COUNT_CORRECTION, SAMPLE, COMP, RETURN_TO_VENDOR, RECIPE_TEST, OTHER
}

@Serializable
data class InventoryAdjustment(
    val id: Id<InventoryAdjustmentTag>,
    val variantId: com.enterprise.pos.core.VariantId,
    val storeId: StoreId,
    val delta: Int,
    val reason: AdjustmentReason,
    val notes: String? = null,
    val employeeId: EmployeeId,
    val timestamp: Long,
    val unitCost: Money? = null
)

interface InventoryAdjustmentTag : IdTag

@Serializable
enum class TransferStatus { DRAFT, IN_TRANSIT, RECEIVED, CANCELLED }

@Serializable
data class InventoryTransfer(
    val id: Id<InventoryTransferTag>,
    val fromStoreId: StoreId,
    val toStoreId: StoreId,
    val status: TransferStatus,
    val items: List<TransferLine>,
    val createdBy: EmployeeId,
    val createdAt: Long,
    val shippedAt: Long? = null,
    val receivedAt: Long? = null,
    val notes: String? = null
)

@Serializable
data class TransferLine(
    val variantId: com.enterprise.pos.core.VariantId,
    val quantity: Int,
    val receivedQuantity: Int = 0
)

interface InventoryTransferTag : IdTag

// ============================================================
// SPLITS & RETURNS
// ============================================================

@Serializable
data class TenderSplit(
    val provider: String, // PaymentProviderId name
    val amount: Money,
    val paymentId: PaymentId? = null,
    val reference: String? = null
)

@Serializable
data class ReturnLine(
    val originalOrderLineId: com.enterprise.pos.core.OrderLineId,
    val productId: com.enterprise.pos.core.ProductId?,
    val name: String,
    val quantity: Double,
    val unitPrice: Money,
    val refundAmount: Money
)

@Serializable
data class ReturnRequest(
    val id: Id<ReturnTag>,
    val originalOrderId: OrderId,
    val storeId: StoreId,
    val employeeId: EmployeeId,
    val lines: List<ReturnLine>,
    val totalRefund: Money,
    val reason: String,
    val timestamp: Long,
    val refundTenders: List<TenderSplit>,
    val status: ReturnStatus
)

@Serializable
enum class ReturnStatus { DRAFT, COMPLETED, PARTIAL, CANCELLED }

interface ReturnTag : IdTag

// ============================================================
// MIGRATION JOBS
// ============================================================

@Serializable
enum class MigrationSource { SHOPIFY, SQUARE, STRIPE, CSV, OTHER }

@Serializable
enum class MigrationType { PRODUCTS, CUSTOMERS, ORDERS, PAYMENTS, INVENTORY, ALL }

@Serializable
enum class MigrationStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, PARTIAL, CANCELLED }

@Serializable
data class MigrationJob(
    val id: Id<MigrationJobTag>,
    val source: MigrationSource,
    val type: MigrationType,
    val status: MigrationStatus,
    val totalRecords: Int = 0,
    val processedRecords: Int = 0,
    val failedRecords: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val configJson: String, // provider credentials, filters, etc.
    val createdBy: EmployeeId,
    val createdAt: Long,
    val conflicts: List<MigrationConflict> = emptyList()
)

@Serializable
data class MigrationConflict(
    val externalId: String,
    val localId: String?,
    val reason: String,
    val resolution: ConflictResolution? = null
)

@Serializable
enum class ConflictResolution { KEEP_LOCAL, KEEP_REMOTE, MERGE, SKIP, MANUAL }

interface MigrationJobTag : IdTag

// ============================================================
// ANALYTICS
// ============================================================

@Serializable
data class SalesByHour(
    val hour: Int,
    val grossSales: Money,
    val netSales: Money,
    val transactionCount: Int,
    val averageOrderValue: Money,
    val itemsSold: Int
)

@Serializable
data class SalesByCategory(
    val categoryId: com.enterprise.pos.core.CategoryId,
    val categoryName: String,
    val grossSales: Money,
    val itemsSold: Int,
    val transactionCount: Int,
    val percentage: Double
)

@Serializable
data class SalesByEmployee(
    val employeeId: EmployeeId,
    val employeeName: String,
    val grossSales: Money,
    val transactionCount: Int,
    val tipsCollected: Money,
    val itemsSold: Int,
    val averageOrderValue: Money,
    val hoursWorked: Double
)

@Serializable
data class TaxLiabilityReport(
    val period: String, // YYYY-MM or YYYY-MM-DD..YYYY-MM-DD
    val taxCollected: Map<String, Money>,
    val taxCollectedTotal: Money,
    val taxableSales: Money,
    val exemptSales: Money,
    val nonTaxableSales: Money,
    val grossSales: Money
)

@Serializable
data class AbcAnalysis(
    val productId: com.enterprise.pos.core.ProductId,
    val productName: String,
    val unitsSold: Int,
    val revenue: Money,
    val revenueContribution: Double, // 0..100
    val cumulativeContribution: Double,
    val classification: AbcClass
)

@Serializable
enum class AbcClass { A, B, C }

@Serializable
data class DashboardSnapshot(
    val todaySales: Money,
    val todayTransactions: Int,
    val todayAverageOrder: Money,
    val todayTips: Money,
    val todayRefunds: Money,
    val yesterdaySales: Money,
    val last7DaysSales: Money,
    val last30DaysSales: Money,
    val activeOrders: Int,
    val openTables: Int,
    val lowStockItems: Int,
    val pendingSyncCount: Int,
    val hourlySales: List<SalesByHour>,
    val topProducts: List<Pair<String, Int>>,
    val topEmployees: List<SalesByEmployee>,
    val paymentMix: Map<String, Money>,
    val diningMix: Map<String, Int>,
    val alerts: List<DashboardAlert>
)

@Serializable
data class DashboardAlert(
    val severity: AlertSeverity,
    val category: AlertCategory,
    val title: String,
    val message: String,
    val action: String? = null,
    val timestamp: Long
)

@Serializable
enum class AlertSeverity { INFO, WARNING, ERROR, CRITICAL }

@Serializable
enum class AlertCategory { INVENTORY, PAYMENT, SYNC, SECURITY, REVENUE, OPERATIONS }

// ============================================================
// SUPPLIERS & PURCHASE ORDERS
// ============================================================

@Serializable
enum class PurchaseOrderStatus { DRAFT, SENT, PARTIAL, RECEIVED, CANCELLED }

@Serializable
data class PurchaseOrderLine(
    val id: String,
    val productId: com.enterprise.pos.core.ProductId,
    val productName: String,
    val quantity: Int,
    val unitCost: Money,
    val receivedQuantity: Int = 0
) {
    val total: Money get() = unitCost * quantity
    val received: Boolean get() = receivedQuantity >= quantity
}

interface PurchaseOrderTag : IdTag

@Serializable
data class PurchaseOrder(
    val id: Id<PurchaseOrderTag>,
    val storeId: StoreId,
    val supplierId: Id<SupplierTag>,
    val supplierName: String,
    val orderDate: Long,
    val expectedDelivery: Long? = null,
    val status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,
    val lines: List<PurchaseOrderLine> = emptyList(),
    val notes: String? = null,
    val shippingCost: Money = Money.ZERO,
    val taxPercent: Percent = Percent.ZERO
) {
    val subtotal: Money get() = lines.fold(Money.ZERO) { acc, line -> acc + line.total }
    val taxAmount: Money get() = taxPercent.of(subtotal)
    val total: Money get() = subtotal + shippingCost + taxAmount
}

interface SupplierTag : IdTag

@Serializable
data class Supplier(
    val id: Id<SupplierTag>,
    val name: String,
    val contactPerson: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val paymentTerms: String? = null,
    val leadTimeDays: Int = 0,
    val active: Boolean = true
)

@Serializable
data class SupplierPerformance(
    val supplierId: Id<SupplierTag>,
    val totalOrders: Int = 0,
    val onTimeDeliveryRate: Double = 0.0,
    val qualityRating: Double = 0.0,
    val averageLeadTimeDays: Double = 0.0
)

// ============================================================
// INVENTORY DETAIL & STOCK MOVEMENTS
// ============================================================

@Serializable
data class InventoryItem(
    val variantId: com.enterprise.pos.core.VariantId,
    val storeId: StoreId,
    val productName: String,
    val sku: String,
    val onHand: Int = 0,
    val committed: Int = 0,
    val available: Int = 0,
    val reorderPoint: Int = 10,
    val reorderQuantity: Int = 20,
    val location: String? = null,
    val supplierId: Id<SupplierTag>? = null,
    val supplierName: String? = null,
    val unitCost: Money? = null,
    val lastCountedAt: Long? = null
) {
    val isLow: Boolean get() = available <= reorderPoint
}

@Serializable
enum class StockMovementType { ADJUSTMENT, SALE, RECEIPT, RETURN, TRANSFER }

@Serializable
data class StockMovement(
    val id: String,
    val variantId: com.enterprise.pos.core.VariantId,
    val storeId: StoreId,
    val type: StockMovementType,
    val quantity: Int,
    val reason: String? = null,
    val notes: String? = null,
    val employeeName: String? = null,
    val timestamp: Long
)
