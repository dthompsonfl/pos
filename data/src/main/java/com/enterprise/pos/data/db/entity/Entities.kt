package com.enterprise.pos.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val displayOrder: Int,
    val iconKey: String?,
    val color: Long
)

@Entity(tableName = "products", indices = [Index("categoryId"), Index("barcode", unique = false)])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val categoryId: String,
    val type: String,
    val taxCategory: String,
    val ageRestriction: String,
    val imageUrl: String?,
    val defaultVariantId: String?,
    val tags: String, // CSV
    val trackInventory: Boolean,
    val isAvailable: Boolean,
    val kitchenRoutingKey: String?,
    val prepTimeMinutes: Int,
    val updatedAt: Long,
    val syncState: String = "SYNCED" // SYNCED | PENDING | CONFLICT
)

@Entity(tableName = "variants", indices = [Index("productId"), Index("sku", unique = true), Index("barcode")])
data class VariantEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val name: String,
    val sku: String,
    val barcode: String?,
    val priceMinor: Long,
    val costPriceMinor: Long?,
    val attributesJson: String
)

@Entity(tableName = "inventory", primaryKeys = ["variantId", "storeId"])
data class InventoryEntity(
    val variantId: String,
    val storeId: String,
    val onHand: Int,
    val committed: Int,
    val lowStockThreshold: Int,
    val reorderPoint: Int,
    val updatedAt: Long
)

@Entity(tableName = "orders", indices = [Index("storeId"), Index("tableId"), Index("status"), Index("updatedAt")])
data class OrderEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val registerId: String,
    val employeeId: String,
    val customerId: String?,
    val diningMode: String,
    val tableId: String?,
    val tableName: String?,
    val guestCount: Int,
    val status: String,
    val orderLevelDiscountMinor: Long,
    val tipMinor: Long,
    val serviceChargesMinor: Long = 0L,
    val taxExempt: Boolean = false,
    val notes: String?,
    val deliveryAddress: String?,
    val deliveryProvider: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long?,
    val syncState: String = "SYNCED"
)

@Entity(tableName = "order_lines", indices = [Index("orderId"), Index("parentLineId")])
data class OrderLineEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val parentLineId: String?,
    val lineType: String,
    val productId: String?,
    val variantId: String?,
    val name: String,
    val quantity: Double,
    val unitPriceMinor: Long,
    val discountMinor: Long,
    val notes: String?,
    val kitchenRoutingKey: String?,
    val sentToKitchen: Boolean,
    val displayOrder: Int,
    val taxCategory: String = "STANDARD",
    val taxAmountMinor: Long = 0L
)

@Entity(tableName = "tax_lines", primaryKeys = ["orderId", "name"])
data class TaxLineEntity(
    val orderId: String,
    val name: String,
    val rateBasisPoints: Int,
    val amountMinor: Long,
    val taxCategory: String
)

@Entity(tableName = "discounts", primaryKeys = ["orderId", "discountId"])
data class DiscountEntity(
    val orderId: String,
    val discountId: String,
    val name: String,
    val type: String,
    val valueMinor: Long?,
    val percentBasisPoints: Int,
    val requiresManagerApproval: Boolean
)

@Entity(tableName = "customers", indices = [Index("email", unique = false), Index("phone", unique = false)])
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val loyaltyPoints: Int,
    val storeCreditMinor: Long,
    val marketingOptIn: Boolean,
    val notes: String?,
    val birthday: String?,
    val address: String?,
    val dietaryRestrictions: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String = "SYNCED"
)

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** PBKDF2 hash, never the raw PIN. */
    val pinHash: String,
    val role: String,
    val active: Boolean,
    val email: String?,
    val phone: String?,
    val createdAt: Long,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val taxIdentifier: String?,
    val currency: String,
    val timezone: String
)

@Entity(tableName = "registers")
data class RegisterEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val deviceIdentifier: String,
    val active: Boolean
)

@Entity(tableName = "tables", indices = [Index("storeId"), Index("status")])
data class TableEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val section: String,
    val capacity: Int,
    val shape: String,
    val x: Float,
    val y: Float,
    val status: String,
    val currentOrderId: String?,
    val currentGuestCount: Int,
    val serverId: String?
)

@Entity(tableName = "payments", indices = [Index("orderId"), Index("provider")])
data class PaymentEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val provider: String,
    val providerTransactionId: String,
    val amountMinor: Long,
    val currency: String,
    val cardBrand: String?,
    val last4: String?,
    val entryMode: String?,
    val receiptUrl: String?,
    val capturedAt: Long,
    val refundedAmountMinor: Long = 0,
    val syncState: String = "SYNCED"
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val recordId: String,
    val operation: String, // INSERT | UPDATE | DELETE
    val payloadJson: String,
    val enqueuedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null
)
