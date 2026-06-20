package com.enterprise.pos.domain.service

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.Quantity
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.randomOrderLineId
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductVariant
import com.enterprise.pos.domain.model.TaxCategory
import kotlinx.serialization.Serializable

/**
 * Pure-functional cart operations. No side effects, no I/O.
 * Single source of truth for how an [Order] is mutated.
 *
 * All quantity operations use [Quantity] (BigDecimal-backed) so fractional
 * quantities (e.g. 1.25 lb of salmon) are exact.
 */
class CartEngine {

    fun createOrder(
        orderId: OrderId,
        storeId: com.enterprise.pos.core.StoreId,
        registerId: com.enterprise.pos.core.RegisterId,
        employeeId: com.enterprise.pos.core.EmployeeId,
        diningMode: DiningMode = DiningMode.RETAIL,
        tableId: com.enterprise.pos.core.TableId? = null,
        tableName: String? = null,
        guestCount: Int = 0,
        now: Long
    ): Order = Order(
        id = orderId,
        storeId = storeId,
        registerId = registerId,
        employeeId = employeeId,
        diningMode = diningMode,
        tableId = tableId,
        tableName = tableName,
        guestCount = guestCount,
        status = OrderStatus.OPEN,
        createdAt = now,
        updatedAt = now
    )

    fun addItem(
        order: Order,
        product: Product,
        variant: ProductVariant,
        quantity: Quantity = Quantity.ONE,
        notes: String? = null,
        modifiers: List<OrderLine> = emptyList(),
        now: Long
    ): Result<Order> = Result.catching {
        require(quantity.isPositive) { "Quantity must be positive" }
        require(product.isAvailable) { "Product ${product.name} is not available" }
        val line = OrderLine(
            id = OrderLineId(product.id.value + "-" + variant.id.value + "-" + now),
            lineType = OrderLineType.ITEM,
            productId = product.id,
            variantId = variant.id,
            name = if (variant.name == product.name || variant.name == "Default") product.name else "${product.name} — ${variant.name}",
            quantity = quantity,
            unitPrice = variant.price,
            modifiers = modifiers,
            notes = notes,
            kitchenRoutingKey = product.kitchenRoutingKey,
            // Capture tax category AT SALE TIME so historical orders are not affected by later changes.
            taxCategory = product.taxCategory
        )
        order.copy(lines = order.lines + line, updatedAt = now)
    }

    fun addModifier(
        order: Order,
        parentLineId: OrderLineId,
        name: String,
        price: Money,
        now: Long
    ): Result<Order> = Result.catching {
        val parentIdx = order.lines.indexOfFirst { it.id == parentLineId }
        require(parentIdx >= 0) { "Parent line not found" }
        val parent = order.lines[parentIdx]
        val modifier = OrderLine(
            id = OrderLineId(parentLineId.value + "-mod-" + now),
            lineType = OrderLineType.MODIFIER,
            name = name,
            quantity = Quantity.ONE,
            unitPrice = price,
            parentLineId = parentLineId
        )
        val updated = parent.copy(modifiers = parent.modifiers + modifier)
        order.copy(
            lines = order.lines.toMutableList().also { it[parentIdx] = updated },
            updatedAt = now
        )
    }

    fun changeQuantity(order: Order, lineId: OrderLineId, newQuantity: Quantity, now: Long): Result<Order> = Result.catching {
        require(!newQuantity.isNegative) { "Quantity must be non-negative" }
        val idx = order.lines.indexOfFirst { it.id == lineId }
        if (idx < 0) return@catching order
        if (newQuantity.isZero) {
            order.copy(lines = order.lines.filterNot { it.id == lineId || it.parentLineId == lineId }, updatedAt = now)
        } else {
            val updated = order.lines[idx].copy(quantity = newQuantity)
            order.copy(lines = order.lines.toMutableList().also { it[idx] = updated }, updatedAt = now)
        }
    }

    fun removeLine(order: Order, lineId: OrderLineId, now: Long): Order =
        order.copy(lines = order.lines.filterNot { it.id == lineId || it.parentLineId == lineId }, updatedAt = now)

    fun addDiscountToLine(
        order: Order,
        lineId: OrderLineId,
        discount: Discount,
        now: Long
    ): Result<Order> = Result.catching {
        val idx = order.lines.indexOfFirst { it.id == lineId }
        require(idx >= 0) { "Line not found" }
        val line = order.lines[idx]
        val amount = when (discount.type) {
            DiscountType.FIXED -> (discount.value ?: Money.ZERO).cappedAt(line.lineTotal)
            DiscountType.PERCENTAGE -> discount.percent.of(line.grossTotal).cappedAt(line.lineTotal)
            DiscountType.BOGO -> {
                // BOGO: every second unit of the same item is free.
                val freeQty = line.quantity.wholeUnits / 2
                line.unitPrice.times(freeQty)
            }
        }
        val cappedAmount = amount.cappedAt(line.lineTotal)
        // Do NOT accumulate — replace line discount with new value (prevents double-counting).
        val updated = line.copy(discount = cappedAmount)
        order.copy(lines = order.lines.toMutableList().also { it[idx] = updated }, updatedAt = now)
    }

    fun addOrderLevelDiscount(order: Order, discount: Discount, now: Long): Result<Order> = Result.catching {
        val amount = when (discount.type) {
            DiscountType.FIXED -> (discount.value ?: Money.ZERO).cappedAt(order.subtotal)
            DiscountType.PERCENTAGE -> discount.percent.of(order.subtotal).cappedAt(order.subtotal)
            DiscountType.BOGO -> Money.ZERO
        }
        // Replace, not accumulate — prevents double-counting on re-apply.
        order.copy(orderLevelDiscount = amount, discounts = order.discounts + discount, updatedAt = now)
    }

    fun setTip(order: Order, tip: Money, now: Long): Order =
        order.copy(tip = tip.atLeastZero(), updatedAt = now)

    fun setServiceCharges(order: Order, charges: Money, now: Long): Order =
        order.copy(serviceCharges = charges.atLeastZero(), updatedAt = now)

    fun setTable(order: Order, tableId: com.enterprise.pos.core.TableId?, tableName: String?, now: Long): Order =
        order.copy(tableId = tableId, tableName = tableName, updatedAt = now)

    fun setDiningMode(order: Order, mode: DiningMode, now: Long): Order =
        order.copy(diningMode = mode, updatedAt = now)

    fun setGuestCount(order: Order, count: Int, now: Long): Order =
        order.copy(guestCount = count.coerceAtLeast(0), updatedAt = now)

    fun setTaxExempt(order: Order, exempt: Boolean, now: Long): Order =
        order.copy(taxExempt = exempt, updatedAt = now)

    fun attachCustomer(order: Order, customerId: com.enterprise.pos.core.CustomerId?, now: Long): Order =
        order.copy(customerId = customerId, updatedAt = now)

    fun sendToKitchen(order: Order, now: Long): Result<Order> = Result.catching {
        if (order.lines.isEmpty()) throw IllegalArgumentException("Cannot send empty order to kitchen")
        val updated = order.lines.map { if (it.kitchenRoutingKey != null) it.copy(sentToKitchen = true) else it }
        order.copy(lines = updated, status = OrderStatus.SENT_TO_KITCHEN, updatedAt = now)
    }

    fun markAwaitingPayment(order: Order, now: Long): Order =
        order.copy(status = OrderStatus.AWAITING_PAYMENT, updatedAt = now)

    fun voidOrder(order: Order, reason: String, now: Long): Result<Order> = Result.catching {
        require(reason.isNotBlank()) { "Void reason required" }
        require(order.status != OrderStatus.PAID) { "Cannot void a paid order — refund instead" }
        order.copy(
            status = OrderStatus.VOIDED,
            notes = (order.notes?.let { "$it\n" } ?: "") + "Voided: $reason",
            closedAt = now,
            updatedAt = now
        )
    }

    /** Record a successful payment against the order and mark it PAID if fully paid. */
    fun recordPayment(order: Order, payment: com.enterprise.pos.domain.model.Payment, now: Long): Order {
        val updatedPayments = order.payments + payment
        val updated = order.copy(payments = updatedPayments, updatedAt = now)
        return if (updated.isFullyPaid && updated.status != OrderStatus.PAID) {
            updated.copy(status = OrderStatus.PAID, closedAt = now, updatedAt = now)
        } else updated
    }

    fun recordRefund(order: Order, refund: com.enterprise.pos.domain.model.Payment, now: Long): Order {
        val updatedRefunds = order.refunds + refund
        return order.copy(refunds = updatedRefunds, updatedAt = now)
    }
}

@Serializable
data class OrderTotalBreakdown(
    val subtotal: Money,
    val lineDiscounts: Money,
    val orderDiscount: Money,
    val totalDiscount: Money,
    val taxableAmount: Money,
    val taxes: Money,
    val tip: Money,
    val serviceCharges: Money,
    val grandTotal: Money,
    val amountPaid: Money,
    val amountRefunded: Money,
    val amountDue: Money
) {
    companion object {
        fun of(order: Order): OrderTotalBreakdown = OrderTotalBreakdown(
            subtotal = order.subtotal,
            lineDiscounts = order.lineDiscountTotal,
            orderDiscount = order.orderLevelDiscount,
            totalDiscount = order.totalDiscount,
            taxableAmount = order.taxableAmount,
            taxes = order.taxTotal,
            tip = order.tip,
            serviceCharges = order.serviceCharges,
            grandTotal = order.grandTotal,
            amountPaid = order.amountPaid,
            amountRefunded = order.amountRefunded,
            amountDue = order.amountDue
        )
    }
}
