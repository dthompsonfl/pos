package com.enterprise.pos.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reservations", indices = [Index("storeId"), Index("requestedAt"), Index("status")])
data class ReservationEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val customerName: String,
    val customerId: String?,
    val phone: String,
    val email: String?,
    val partySize: Int,
    val requestedAt: Long,
    val tableId: String?,
    val status: String,
    val notes: String?,
    val dietaryRestrictions: String,
    val createdAt: Long,
    val confirmedAt: Long?,
    val seatedAt: Long?,
    val cancelledAt: Long?,
    val reminderSent: Boolean
)

@Entity(tableName = "gift_cards", indices = [Index("code", unique = true), Index("storeId"), Index("customerId")])
data class GiftCardEntity(
    @PrimaryKey val id: String,
    val code: String,
    val storeId: String,
    val balanceMinor: Long,
    val initialBalanceMinor: Long,
    val issuedAt: Long,
    val expiresAt: Long?,
    val customerId: String?,
    val notes: String?,
    val active: Boolean
)

@Entity(tableName = "gift_card_transactions", indices = [Index("giftCardId"), Index("orderId")])
data class GiftCardTransactionEntity(
    @PrimaryKey val id: String,
    val giftCardId: String,
    val orderId: String?,
    val amountMinor: Long,
    val balanceAfterMinor: Long,
    val timestamp: Long,
    val employeeId: String,
    val type: String
)

@Entity(tableName = "audit_log", indices = [Index("storeId"), Index("entityType", "entityId"), Index("action"), Index("timestamp")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val registerId: String?,
    val employeeId: String,
    val employeeName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val beforeJson: String?,
    val afterJson: String?,
    val reason: String?,
    val timestamp: Long,
    val ipAddress: String?,
    val deviceIdentifier: String?
)

@Entity(tableName = "promotions", indices = [Index("active"), Index("code")])
data class PromotionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val scope: String,
    val valueMinor: Long?,
    val percent: Int,
    val buyQty: Int,
    val getQty: Int,
    val freeItemProductId: String?,
    val categoryIdsCsv: String,
    val productIdsCsv: String,
    val startTime: Long,
    val endTime: Long,
    val daysOfWeekCsv: String,
    val priority: Int,
    val requiresCode: Boolean,
    val code: String?,
    val active: Boolean,
    val maxRedemptions: Int?,
    val redemptionCount: Int,
    val maxRedemptionsPerCustomer: Int?
)

@Entity(tableName = "loyalty_rewards")
data class LoyaltyRewardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pointsCost: Int,
    val rewardType: String,
    val valueMinor: Long?,
    val freeItemProductId: String?,
    val active: Boolean
)

@Entity(tableName = "shifts", indices = [Index("storeId"), Index("registerId"), Index("employeeId"), Index("status")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val registerId: String,
    val employeeId: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val startingCashMinor: Long,
    val expectedCashMinor: Long,
    val countedCashMinor: Long?,
    val cashVarianceMinor: Long,
    val startingFloatJson: String,
    val countedFloatJson: String,
    val notes: String?,
    val salesTotalMinor: Long,
    val tipsCollectedMinor: Long,
    val refundsTotalMinor: Long,
    val payoutsTotalMinor: Long,
    val transactionCount: Int
)

@Entity(tableName = "z_reports")
data class ZReportEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val registerId: String,
    val shiftId: String,
    val generatedAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val grossSalesMinor: Long,
    val returnsMinor: Long,
    val discountsMinor: Long,
    val netSalesMinor: Long,
    val taxCollectedJson: String,
    val tipsMinor: Long,
    val cashTotalMinor: Long,
    val cardTotalMinor: Long,
    val otherTendersJson: String,
    val overShortMinor: Long,
    val transactionCount: Int,
    val refundCount: Int,
    val voidCount: Int,
    val noSaleCount: Int,
    val employeeBreakdownJson: String
)

@Entity(tableName = "inventory_adjustments", indices = [Index("storeId"), Index("variantId"), Index("timestamp")])
data class InventoryAdjustmentEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val storeId: String,
    val delta: Int,
    val reason: String,
    val notes: String?,
    val employeeId: String,
    val timestamp: Long,
    val unitCostMinor: Long?
)

@Entity(tableName = "inventory_transfers", indices = [Index("fromStoreId"), Index("toStoreId"), Index("status")])
data class InventoryTransferEntity(
    @PrimaryKey val id: String,
    val fromStoreId: String,
    val toStoreId: String,
    val status: String,
    val itemsJson: String,
    val createdBy: String,
    val createdAt: Long,
    val shippedAt: Long?,
    val receivedAt: Long?,
    val notes: String?
)

@Entity(tableName = "returns", indices = [Index("originalOrderId"), Index("storeId")])
data class ReturnEntity(
    @PrimaryKey val id: String,
    val originalOrderId: String,
    val storeId: String,
    val employeeId: String,
    val linesJson: String,
    val totalRefundMinor: Long,
    val reason: String,
    val timestamp: Long,
    val refundTendersJson: String,
    val status: String
)

@Entity(tableName = "migration_jobs", indices = [Index("source"), Index("type"), Index("status")])
data class MigrationJobEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,
    val status: String,
    val totalRecords: Int,
    val processedRecords: Int,
    val failedRecords: Int,
    val startedAt: Long?,
    val completedAt: Long?,
    val errorMessage: String?,
    val configJson: String,
    val createdBy: String,
    val createdAt: Long,
    val conflictsJson: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val valueJson: String,
    val updatedAt: Long,
    val updatedBy: String?
)

@Entity(tableName = "tips_log", indices = [Index("orderId"), Index("employeeId")])
data class TipLogEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val employeeId: String,
    val amountMinor: Long,
    val tipType: String, // CREDIT_CARD, CASH, POOLED
    val timestamp: Long
)
