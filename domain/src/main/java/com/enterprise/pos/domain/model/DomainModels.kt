package com.enterprise.pos.domain.model

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.Quantity
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.core.VariantId
import kotlinx.serialization.Serializable

/** Dining modes — the foundation of the restaurant support. */
@Serializable
enum class DiningMode {
    /** Customer seated by host/hostess at a traditional table — full service. */
    DINE_IN_HOST_SEATED,
    /** Customer seats themselves (fast-casual, café) — order at counter, food delivered. */
    DINE_IN_SELF_SEATED,
    /** Take-out / pickup — paid in advance or on pickup. */
    TO_GO,
    /** Delivery — fulfilled by store staff or third party. */
    DELIVERY,
    /** Catering / large pre-order. */
    CATERING,
    /** Retail (non-restaurant) — no table, no dining. */
    RETAIL
}

@Serializable
enum class OrderStatus {
    DRAFT,
    OPEN,
    SENT_TO_KITCHEN,
    IN_PREPARATION,
    READY,
    SERVED,
    AWAITING_PAYMENT,
    PAID,
    REFUNDED,
    CANCELLED,
    VOIDED
}

@Serializable
enum class OrderLineType { ITEM, MODIFIER, DISCOUNT, SURCHARGE, TIP, REFUND }

@Serializable
data class OrderLine(
    val id: OrderLineId,
    val lineType: OrderLineType,
    val productId: ProductId? = null,
    val variantId: VariantId? = null,
    val name: String,
    val quantity: Quantity = Quantity.ONE,
    val unitPrice: Money,
    val modifiers: List<OrderLine> = emptyList(),
    val discount: Money = Money.ZERO,
    val notes: String? = null,
    val kitchenRoutingKey: String? = null,
    val sentToKitchen: Boolean = false,
    val parentLineId: OrderLineId? = null,
    /** Tax category captured at sale time — protects historical orders from later product changes. */
    val taxCategory: TaxCategory = TaxCategory.STANDARD,
    /** Per-line tax amount computed by TaxEngine. */
    val taxAmount: Money = Money.ZERO
) {
    /** Gross line total before line discount: unitPrice × quantity (fractional OK) + modifiers. */
    val grossTotal: Money
        get() {
            val base = if (quantity.isPositive) unitPrice * quantity else Money.ZERO
            val modifiersTotal = modifiers.fold(Money.ZERO) { acc, m -> acc + m.lineTotal }
            return base + modifiersTotal
        }

    /** Net line total after line discount. */
    val lineTotal: Money
        get() = (grossTotal - discount).atLeastZero()

    /** Total with per-line tax included. */
    val lineTotalWithTax: Money
        get() = lineTotal + taxAmount
}

@Serializable
data class Discount(
    val id: String,
    val name: String,
    val type: DiscountType,
    val value: Money? = null,
    val percent: Percent = Percent.ZERO,
    val requiresManagerApproval: Boolean = false,
    /** Manager who approved the discount, if manager override was required. */
    val approvedBy: EmployeeId? = null,
    val approvalReason: String? = null
)

@Serializable
enum class DiscountType { FIXED, PERCENTAGE, BOGO }

@Serializable
data class Order(
    val id: OrderId,
    val storeId: StoreId,
    val registerId: RegisterId,
    val employeeId: EmployeeId,
    val customerId: CustomerId? = null,
    val diningMode: DiningMode = DiningMode.RETAIL,
    val tableId: TableId? = null,
    val tableName: String? = null,
    val guestCount: Int = 0,
    val status: OrderStatus = OrderStatus.DRAFT,
    val lines: List<OrderLine> = emptyList(),
    val discounts: List<Discount> = emptyList(),
    val orderLevelDiscount: Money = Money.ZERO,
    val taxLines: List<TaxLine> = emptyList(),
    val tip: Money = Money.ZERO,
    val serviceCharges: Money = Money.ZERO,
    val payments: List<com.enterprise.pos.domain.model.Payment> = emptyList(),
    val refunds: List<com.enterprise.pos.domain.model.Payment> = emptyList(),
    val taxExempt: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val closedAt: Long? = null,
    val notes: String? = null,
    val deliveryAddress: String? = null,
    val deliveryProvider: String? = null
) {
    /** Sum of all ITEM lines' gross totals (before discounts). */
    val subtotal: Money
        get() = lines.filter { it.lineType == OrderLineType.ITEM }.fold(Money.ZERO) { acc, l -> acc + l.grossTotal }

    /** Sum of all per-line discounts. */
    val lineDiscountTotal: Money
        get() = lines.filter { it.lineType == OrderLineType.ITEM }.fold(Money.ZERO) { acc, l -> acc + l.discount }

    /** Total discounts = line discounts + order-level discount. Not double-counted. */
    val totalDiscount: Money
        get() = lineDiscountTotal + orderLevelDiscount

    /** Subtotal minus all discounts. */
    val taxableAmount: Money
        get() = (subtotal - totalDiscount).atLeastZero()

    val taxTotal: Money
        get() = if (taxExempt) Money.ZERO else taxLines.fold(Money.ZERO) { acc, t -> acc + t.amount }

    /** Grand total = (subtotal − discounts) + tax + tip + service charges. */
    val grandTotal: Money
        get() = taxableAmount + taxTotal + tip + serviceCharges

    /** Sum of all successful payments recorded against this order. */
    val amountPaid: Money
        get() = payments.filter { it.capturedAt > 0 }.fold(Money.ZERO) { acc, p -> acc + p.amount }

    /** Sum of all refunds issued against this order. */
    val amountRefunded: Money
        get() = refunds.fold(Money.ZERO) { acc, p -> acc + p.amount }

    /** Remaining amount the customer must pay. */
    val amountDue: Money
        get() = (grandTotal - amountPaid + amountRefunded).atLeastZero()

    val isOpen: Boolean get() = status in setOf(OrderStatus.DRAFT, OrderStatus.OPEN, OrderStatus.SENT_TO_KITCHEN, OrderStatus.IN_PREPARATION, OrderStatus.READY, OrderStatus.SERVED, OrderStatus.AWAITING_PAYMENT)

    val isFullyPaid: Boolean get() = amountDue.isZero() && !grandTotal.isZero()
}

@Serializable
data class TaxLine(
    val name: String,
    val rate: Percent,
    val amount: Money,
    val taxCategory: TaxCategory = TaxCategory.STANDARD
)

@Serializable
data class Customer(
    val id: CustomerId,
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val loyaltyPoints: Int = 0,
    val storeCredit: Money = Money.ZERO,
    val createdAt: Long = 0L,
    val marketingOptIn: Boolean = false,
    val notes: String? = null,
    val birthday: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val country: String? = null,
    val tags: List<String> = emptyList(),
    val group: String? = null,
    val loyaltyNumber: String? = null,
    val dietaryRestrictions: List<String> = emptyList()
)

@Serializable
data class Employee(
    val id: EmployeeId,
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    /** PBKDF2 hash of the PIN — NEVER the raw PIN. Format: `pbkdf2$iterations$saltHex$hashHex`. */
    val pinHash: String,
    val role: EmployeeRole,
    val active: Boolean = true,
    val email: String? = null,
    val phone: String? = null,
    val hourlyRate: Money = Money.ZERO,
    val hireDate: Long? = null,
    val notes: String? = null,
    val customPermissions: List<String> = emptyList(),
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null
)

@Serializable
enum class EmployeeRole {
    CASHIER, SERVER, HOST, BARTENDER, LINE_COOK, KITCHEN_LEAD, SHIFT_LEAD, MANAGER, ADMIN
}

@Serializable
data class RolePermissions(
    val role: EmployeeRole,
    val canProcessRefunds: Boolean = false,
    val canApplyDiscounts: Boolean = true,
    val maxDiscountPercent: Int = 0,
    val canVoidOrders: Boolean = false,
    val canOpenDrawer: Boolean = false,
    val canManageEmployees: Boolean = false,
    val canViewReports: Boolean = false,
    val canManageInventory: Boolean = false,
    val canCompItems: Boolean = false,
    val maxCompValue: Money = Money.ZERO
)

@Serializable
data class Store(
    val id: StoreId,
    val name: String,
    val address: String,
    val phone: String,
    val taxIdentifier: String? = null,
    val currency: String = "USD",
    val timezone: String = "America/Chicago"
)

@Serializable
data class Register(
    val id: RegisterId,
    val storeId: StoreId,
    val name: String,
    val deviceIdentifier: String,
    val active: Boolean = true
)

@Serializable
data class Payment(
    val id: com.enterprise.pos.core.PaymentId,
    val orderId: com.enterprise.pos.core.OrderId,
    val provider: String,
    val providerTransactionId: String,
    val amount: Money,
    val currency: String = "USD",
    val cardBrand: String? = null,
    val last4: String? = null,
    val entryMode: String? = null,
    val receiptUrl: String? = null,
    val capturedAt: Long = 0L,
    val refundedAmount: Money = Money.ZERO
)

@Serializable
data class RestaurantTable(
    val id: TableId,
    val storeId: StoreId,
    val name: String,
    val section: String,
    val capacity: Int,
    val shape: TableShape = TableShape.ROUND,
    val x: Float = 0f,
    val y: Float = 0f,
    val status: TableStatus = TableStatus.AVAILABLE,
    val currentOrderId: OrderId? = null,
    val currentGuestCount: Int = 0,
    val serverId: EmployeeId? = null
)

@Serializable
enum class TableShape { ROUND, SQUARE, RECTANGLE, BOOTH }

@Serializable
enum class TableStatus { AVAILABLE, SEATED, ORDERED, DINING, BILL_REQUESTED, CLEANING, RESERVED }
