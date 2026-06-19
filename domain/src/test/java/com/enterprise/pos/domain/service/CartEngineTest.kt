package com.enterprise.pos.domain.service

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.Quantity
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductType
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.model.ProductVariant
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CartEngineTest {

    private val engine = CartEngine()
    private val storeId = StoreId("s")
    private val registerId = RegisterId("r")
    private val employeeId = EmployeeId("e")
    private val now = 1000L

    private fun newOrder(): Order = engine.createOrder(
        orderId = OrderId("o1"), storeId = storeId, registerId = registerId,
        employeeId = employeeId, diningMode = DiningMode.DINE_IN_HOST_SEATED,
        tableId = null, now = now
    )

    private fun product(price: Double, name: String = "Item"): Pair<Product, ProductVariant> {
        val v = ProductVariant(VariantId("v"), name, "SKU", null, Money.of(price))
        val p = Product(
            id = ProductId("p"), name = name, categoryId = CategoryId("c"),
            type = ProductType.PHYSICAL, taxCategory = TaxCategory.PREPARED_FOOD,
            defaultVariantId = v.id, variants = listOf(v)
        )
        return p to v
    }

    @Test
    fun `addItem adds line and updates timestamp`() {
        val order = newOrder()
        val (p, v) = product(10.0)
        val updated = engine.addItem(order, p, v, quantity = Quantity.of(2), now = 2000L).getOrThrow()
        assertThat(updated.lines).hasSize(1)
        assertThat(updated.lines[0].name).isEqualTo("Item")
        assertThat(updated.lines[0].quantity.asInt).isEqualTo(2)
        assertThat(updated.lines[0].lineTotal.minorUnits).isEqualTo(2000L)
        assertThat(updated.updatedAt).isEqualTo(2000L)
    }

    @Test
    fun `fractional quantity is preserved exactly`() {
        val order = newOrder()
        val (p, v) = product(4.0, "Salmon")
        // 1.25 lb × $4.00/lb = $5.00
        val updated = engine.addItem(order, p, v, quantity = Quantity.of(1.25), now = now).getOrThrow()
        assertThat(updated.lines[0].quantity.asDouble).isEqualTo(1.25)
        assertThat(updated.lines[0].lineTotal.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `subtotal sums all item lines`() {
        val order = newOrder()
        val (p1, v1) = product(10.0, "A")
        val (p2, v2) = product(5.0, "B")
        val o1 = engine.addItem(order, p1, v1, Quantity.of(1), now = now).getOrThrow()
        val o2 = engine.addItem(o1, p2, v2, Quantity.of(2), now = now).getOrThrow()
        assertThat(o2.subtotal.minorUnits).isEqualTo(2000L) // 10 + 5*2
    }

    @Test
    fun `changeQuantity to zero removes the line`() {
        val order = newOrder()
        val (p, v) = product(10.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(2), now = now).getOrThrow()
        val o2 = engine.changeQuantity(o1, o1.lines[0].id, Quantity.ZERO, now = now).getOrThrow()
        assertThat(o2.lines).isEmpty()
    }

    @Test
    fun `order-level discount is not double counted`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        val disc = Discount(id = "d", name = "10% off", type = DiscountType.PERCENTAGE, percent = Percent.of(10.0))
        val o2 = engine.addOrderLevelDiscount(o1, disc, now = now).getOrThrow()
        assertThat(o2.orderLevelDiscount.minorUnits).isEqualTo(1000L) // 10% of $100 = $10
        assertThat(o2.totalDiscount.minorUnits).isEqualTo(1000L) // not double-counted
        assertThat(o2.taxableAmount.minorUnits).isEqualTo(9000L) // 100 - 10
    }

    @Test
    fun `line discount and order discount both apply without double counting`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        // Add a $10 line discount
        val lineDisc = Discount(id = "ld", name = "$10 off", type = DiscountType.FIXED, value = Money.of(10.0))
        val o2 = engine.addDiscountToLine(o1, o1.lines[0].id, lineDisc, now = now).getOrThrow()
        // Add a 10% order discount (applies to subtotal BEFORE line discount? No — line discount already deducted from line; subtotal is gross)
        // subtotal = $100 (gross), line discount = $10, order-level discount = 10% of subtotal = $10
        val orderDisc = Discount(id = "od", name = "10% order", type = DiscountType.PERCENTAGE, percent = Percent.of(10.0))
        val o3 = engine.addOrderLevelDiscount(o2, orderDisc, now = now).getOrThrow()
        // totalDiscount = line discount + order discount = $10 + $10 = $20
        assertThat(o3.totalDiscount.minorUnits).isEqualTo(2000L)
        // taxableAmount = subtotal - totalDiscount = 100 - 20 = 80
        assertThat(o3.taxableAmount.minorUnits).isEqualTo(8000L)
    }

    @Test
    fun `sendToKitchen marks status and sets sent flag on kitchen lines`() {
        val order = newOrder()
        val (p, v) = product(10.0)
        val productWithKitchen = p.copy(kitchenRoutingKey = "grill")
        val o1 = engine.addItem(order, productWithKitchen, v, Quantity.of(1), now = now).getOrThrow()
        val o2 = engine.sendToKitchen(o1, now = 2000L).getOrThrow()
        assertThat(o2.status).isEqualTo(OrderStatus.SENT_TO_KITCHEN)
        assertThat(o2.lines.first().sentToKitchen).isTrue()
    }

    @Test
    fun `sendToKitchen fails on empty order`() {
        val order = newOrder()
        val r = engine.sendToKitchen(order, now = now)
        assertThat(r).isInstanceOf(com.enterprise.pos.core.Result.Failure::class.java)
    }

    @Test
    fun `voidOrder requires reason`() {
        val order = newOrder()
        val r = engine.voidOrder(order, reason = "", now = now)
        assertThat(r).isInstanceOf(com.enterprise.pos.core.Result.Failure::class.java)
    }

    @Test
    fun `voidOrder marks status VOIDED with closedAt`() {
        val order = newOrder()
        val r = engine.voidOrder(order, reason = "Customer cancelled", now = 5000L).getOrThrow()
        assertThat(r.status).isEqualTo(OrderStatus.VOIDED)
        assertThat(r.closedAt).isEqualTo(5000L)
    }

    @Test
    fun `grandTotal includes tax and tip`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        val o2 = engine.setTip(o1, Money.of(15.0), now = now)
        val o3 = o2.copy(taxLines = listOf(com.enterprise.pos.domain.model.TaxLine("Sales", Percent.of(8.25), Money.of(8.25))))
        // subtotal 100, tax 8.25, tip 15 → 123.25
        assertThat(o3.grandTotal.minorUnits).isEqualTo(12325L)
    }

    @Test
    fun `tax exempt order has zero tax`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        val o2 = engine.setTaxExempt(o1, true, now = now)
        val o3 = o2.copy(taxLines = listOf(com.enterprise.pos.domain.model.TaxLine("Sales", Percent.of(8.25), Money.of(8.25))))
        assertThat(o3.taxTotal.minorUnits).isEqualTo(0L)
        assertThat(o3.grandTotal.minorUnits).isEqualTo(10000L)
    }

    @Test
    fun `amountDue decreases after payment recorded`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        val o2 = o1.copy(taxLines = listOf(com.enterprise.pos.domain.model.TaxLine("Sales", Percent.of(8.25), Money.of(8.25))))
        // Total = 100 + 8.25 = 108.25
        assertThat(o2.grandTotal.minorUnits).isEqualTo(10825L)
        // Pay $50
        val partialPayment = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("pay-1"),
            orderId = o2.id,
            provider = "CASH",
            providerTransactionId = "cash-1",
            amount = Money.of(50.0),
            capturedAt = now
        )
        val o3 = engine.recordPayment(o2, partialPayment, now)
        assertThat(o3.amountPaid.minorUnits).isEqualTo(5000L)
        assertThat(o3.amountDue.minorUnits).isEqualTo(5825L)
        assertThat(o3.isFullyPaid).isFalse()
        assertThat(o3.status).isNotEqualTo(OrderStatus.PAID)
    }

    @Test
    fun `order becomes PAID only after amountDue reaches zero`() {
        val order = newOrder()
        val (p, v) = product(100.0)
        val o1 = engine.addItem(order, p, v, Quantity.of(1), now = now).getOrThrow()
        val o2 = o1.copy(taxLines = listOf(com.enterprise.pos.domain.model.TaxLine("Sales", Percent.of(8.25), Money.of(8.25))))
        // Total = 108.25; pay exactly $108.25
        val fullPayment = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("pay-2"),
            orderId = o2.id,
            provider = "STRIPE",
            providerTransactionId = "pi_test",
            amount = Money.ofMinor(10825L),
            capturedAt = now
        )
        val o3 = engine.recordPayment(o2, fullPayment, now)
        assertThat(o3.amountDue.minorUnits).isEqualTo(0L)
        assertThat(o3.isFullyPaid).isTrue()
        assertThat(o3.status).isEqualTo(OrderStatus.PAID)
        assertThat(o3.closedAt).isEqualTo(now)
    }
}
