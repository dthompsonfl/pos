package com.enterprise.pos.domain.service

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.Quantity
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductVariant
import com.enterprise.pos.domain.model.TaxCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class CartEngineTest {

    private val engine = CartEngine()
    private val now = 1700000000000L

    private val storeId = StoreId("store-1")
    private val registerId = RegisterId("reg-1")
    private val employeeId = EmployeeId("emp-1")
    private val tableId = TableId("table-1")

    private val product = Product(
        id = com.enterprise.pos.core.ProductId("prod-1"),
        name = "Burger",
        categoryId = com.enterprise.pos.core.CategoryId("cat-1"),
        isAvailable = true,
        taxCategory = TaxCategory.STANDARD,
        variants = listOf(
            ProductVariant(
                id = com.enterprise.pos.core.VariantId("var-1"),
                name = "Default",
                sku = "BUR001",
                price = Money.of(12.50)
            )
        )
    )

    private val variant = product.variants.first()

    private fun createOrder() = engine.createOrder(
        orderId = OrderId(UUID.randomUUID().toString()),
        storeId = storeId,
        registerId = registerId,
        employeeId = employeeId,
        diningMode = DiningMode.DINE_IN_HOST_SEATED,
        tableId = tableId,
        guestCount = 2,
        now = now
    )

    @Test
    fun `create order sets correct initial state`() {
        val order = createOrder()
        assertThat(order.status).isEqualTo(OrderStatus.OPEN)
        assertThat(order.lines).isEmpty()
        assertThat(order.storeId).isEqualTo(storeId)
        assertThat(order.registerId).isEqualTo(registerId)
        assertThat(order.employeeId).isEqualTo(employeeId)
        assertThat(order.diningMode).isEqualTo(DiningMode.DINE_IN_HOST_SEATED)
        assertThat(order.tableId).isEqualTo(tableId)
        assertThat(order.guestCount).isEqualTo(2)
        assertThat(order.createdAt).isEqualTo(now)
        assertThat(order.updatedAt).isEqualTo(now)
    }

    @Test
    fun `add item increases line count and updates timestamp`() {
        val order = createOrder()
        val result = engine.addItem(order, product, variant, Quantity.ONE, now = now + 1)
        assertThat(result.isSuccess()).isTrue()
        val updated = result.getOrThrow()
        assertThat(updated.lines).hasSize(1)
        assertThat(updated.lines[0].name).contains("Burger")
        assertThat(updated.updatedAt).isGreaterThan(order.updatedAt)
    }

    @Test
    fun `add item with quantity`() {
        val order = createOrder()
        val result = engine.addItem(order, product, variant, Quantity.of(3), now = now)
        val updated = result.getOrThrow()
        assertThat(updated.lines[0].quantity).isEqualTo(Quantity.of(3))
        assertThat(updated.lines[0].lineTotal.minorUnits).isEqualTo(3750L) // 12.50 * 3
    }

    @Test
    fun `add item rejects unavailable product`() {
        val unavailable = product.copy(isAvailable = false)
        val order = createOrder()
        val result = engine.addItem(order, unavailable, variant, now = now)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `add item rejects non-positive quantity`() {
        val order = createOrder()
        val result = engine.addItem(order, product, variant, Quantity.ZERO, now = now)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `add modifier to line`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val result = engine.addModifier(withItem, lineId, "Extra Cheese", Money.of(1.50), now = now + 1)
        assertThat(result.isSuccess()).isTrue()
        val updated = result.getOrThrow()
        assertThat(updated.lines[0].modifiers).hasSize(1)
        assertThat(updated.lines[0].modifiers[0].name).isEqualTo("Extra Cheese")
    }

    @Test
    fun `add modifier fails for nonexistent parent`() {
        val order = createOrder()
        val result = engine.addModifier(order, com.enterprise.pos.core.OrderLineId("fake"), "Extra Cheese", Money.of(1.50), now = now)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `change quantity updates line`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, Quantity.of(2), now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val result = engine.changeQuantity(withItem, lineId, Quantity.of(5), now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.lines[0].quantity).isEqualTo(Quantity.of(5))
    }

    @Test
    fun `change quantity to zero removes line`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val result = engine.changeQuantity(withItem, lineId, Quantity.ZERO, now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.lines).isEmpty()
    }

    @Test
    fun `change quantity removes modifiers when parent removed`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val withMod = engine.addModifier(withItem, lineId, "Extra Cheese", Money.of(1.50), now = now).getOrThrow()
        val result = engine.changeQuantity(withMod, lineId, Quantity.ZERO, now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.lines).isEmpty()
    }

    @Test
    fun `remove line removes line and modifiers`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val withMod = engine.addModifier(withItem, lineId, "Extra Cheese", Money.of(1.50), now = now).getOrThrow()
        val updated = engine.removeLine(withMod, lineId, now = now + 1)
        assertThat(updated.lines).isEmpty()
    }

    @Test
    fun `add discount to line`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val discount = Discount(
            id = "disc-1",
            name = "10% Off",
            type = DiscountType.PERCENTAGE,
            percent = com.enterprise.pos.core.Percent.of(10.0)
        )
        val result = engine.addDiscountToLine(withItem, lineId, discount, now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.lines[0].discount.minorUnits).isEqualTo(125L) // 10% of 12.50 = 1.25
    }

    @Test
    fun `add fixed discount to line`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val lineId = withItem.lines[0].id
        val discount = Discount(
            id = "disc-1",
            name = "$5 Off",
            type = DiscountType.FIXED,
            value = Money.of(5.00)
        )
        val result = engine.addDiscountToLine(withItem, lineId, discount, now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.lines[0].discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `add order level discount`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val discount = Discount(
            id = "disc-1",
            name = "10% Off",
            type = DiscountType.PERCENTAGE,
            percent = com.enterprise.pos.core.Percent.of(10.0)
        )
        val result = engine.addOrderLevelDiscount(withItem, discount, now = now + 1)
        val updated = result.getOrThrow()
        assertThat(updated.orderLevelDiscount.minorUnits).isEqualTo(125L)
    }

    @Test
    fun `set tip`() {
        val order = createOrder()
        val updated = engine.setTip(order, Money.of(5.00), now = now + 1)
        assertThat(updated.tip.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `set tip clamps negative to zero`() {
        val order = createOrder()
        val updated = engine.setTip(order, Money.of(-5.00), now = now + 1)
        assertThat(updated.tip.isZero()).isTrue()
    }

    @Test
    fun `set service charges`() {
        val order = createOrder()
        val updated = engine.setServiceCharges(order, Money.of(2.50), now = now + 1)
        assertThat(updated.serviceCharges.minorUnits).isEqualTo(250L)
    }

    @Test
    fun `set dining mode`() {
        val order = createOrder()
        val updated = engine.setDiningMode(order, DiningMode.TO_GO, now = now + 1)
        assertThat(updated.diningMode).isEqualTo(DiningMode.TO_GO)
    }

    @Test
    fun `set guest count`() {
        val order = createOrder()
        val updated = engine.setGuestCount(order, 4, now = now + 1)
        assertThat(updated.guestCount).isEqualTo(4)
    }

    @Test
    fun `set guest count clamps negative to zero`() {
        val order = createOrder()
        val updated = engine.setGuestCount(order, -1, now = now + 1)
        assertThat(updated.guestCount).isEqualTo(0)
    }

    @Test
    fun `set tax exempt`() {
        val order = createOrder()
        val updated = engine.setTaxExempt(order, true, now = now + 1)
        assertThat(updated.taxExempt).isTrue()
    }

    @Test
    fun `attach customer`() {
        val customerId = com.enterprise.pos.core.CustomerId("cust-1")
        val order = createOrder()
        val updated = engine.attachCustomer(order, customerId, now = now + 1)
        assertThat(updated.customerId).isEqualTo(customerId)
    }

    @Test
    fun `send to kitchen fails for empty order`() {
        val order = createOrder()
        val result = engine.sendToKitchen(order, now = now + 1)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `send to kitchen succeeds with items`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val result = engine.sendToKitchen(withItem, now = now + 1)
        assertThat(result.isSuccess()).isTrue()
        val updated = result.getOrThrow()
        assertThat(updated.status).isEqualTo(OrderStatus.SENT_TO_KITCHEN)
        assertThat(updated.lines[0].sentToKitchen).isTrue()
    }

    @Test
    fun `mark awaiting payment`() {
        val order = createOrder()
        val updated = engine.markAwaitingPayment(order, now = now + 1)
        assertThat(updated.status).isEqualTo(OrderStatus.AWAITING_PAYMENT)
    }

    @Test
    fun `void order`() {
        val order = createOrder()
        val result = engine.voidOrder(order, "Customer changed mind", now = now + 1)
        assertThat(result.isSuccess()).isTrue()
        val updated = result.getOrThrow()
        assertThat(updated.status).isEqualTo(OrderStatus.VOIDED)
        assertThat(updated.notes).contains("Customer changed mind")
        assertThat(updated.closedAt).isEqualTo(now + 1)
    }

    @Test
    fun `void order requires non-blank reason`() {
        val order = createOrder()
        val result = engine.voidOrder(order, "   ", now = now + 1)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `void paid order fails`() {
        val order = createOrder().copy(status = OrderStatus.PAID)
        val result = engine.voidOrder(order, "Mistake", now = now + 1)
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `record payment updates status when fully paid`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val payment = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("pay-1"),
            orderId = order.id,
            provider = "CASH",
            providerTransactionId = "txn-1",
            amount = Money.of(12.50),
            capturedAt = now + 1
        )
        val updated = engine.recordPayment(withItem, payment, now = now + 1)
        assertThat(updated.status).isEqualTo(OrderStatus.PAID)
        assertThat(updated.isFullyPaid).isTrue()
        assertThat(updated.closedAt).isEqualTo(now + 1)
    }

    @Test
    fun `record partial payment does not mark paid`() {
        val order = createOrder()
        val withItem = engine.addItem(order, product, variant, now = now).getOrThrow()
        val payment = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("pay-1"),
            orderId = order.id,
            provider = "CASH",
            providerTransactionId = "txn-1",
            amount = Money.of(5.00),
            capturedAt = now + 1
        )
        val updated = engine.recordPayment(withItem, payment, now = now + 1)
        assertThat(updated.status).isEqualTo(OrderStatus.OPEN)
        assertThat(updated.isFullyPaid).isFalse()
    }

    @Test
    fun `record refund`() {
        val order = createOrder()
        val refund = com.enterprise.pos.domain.model.Payment(
            id = com.enterprise.pos.core.PaymentId("ref-1"),
            orderId = order.id,
            provider = "CASH",
            providerTransactionId = "txn-2",
            amount = Money.of(5.00),
            capturedAt = now + 1
        )
        val updated = engine.recordRefund(order, refund, now = now + 1)
        assertThat(updated.amountRefunded.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `empty order has zero totals`() {
        val order = createOrder()
        assertThat(order.subtotal.isZero()).isTrue()
        assertThat(order.grandTotal.isZero()).isTrue()
        assertThat(order.amountDue.isZero()).isTrue()
    }

    @Test
    fun `duplicate items create separate lines`() {
        val order = createOrder()
        val withItem1 = engine.addItem(order, product, variant, now = now).getOrThrow()
        val withItem2 = engine.addItem(withItem1, product, variant, now = now + 1).getOrThrow()
        assertThat(withItem2.lines).hasSize(2)
    }
}
