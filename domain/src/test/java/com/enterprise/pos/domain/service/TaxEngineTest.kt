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
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductType
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.model.ProductVariant
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaxEngineTest {

    private val engine = DefaultTaxEngine(TaxConfiguration.RESTAURANT) as TaxEngine

    private fun orderWithItem(price: Double, taxCategory: TaxCategory = TaxCategory.PREPARED_FOOD, qty: Int = 1): Order {
        val now = System.currentTimeMillis()
        val line = OrderLine(
            id = OrderLineId("l1"),
            lineType = OrderLineType.ITEM,
            productId = ProductId("p1"),
            variantId = VariantId("v1"),
            name = "Item",
            quantity = Quantity.of(qy(qty)),
            unitPrice = Money.of(price),
            taxCategory = taxCategory
        )
        return Order(
            id = OrderId("o1"),
            storeId = StoreId("s1"),
            registerId = RegisterId("r1"),
            employeeId = EmployeeId("e1"),
            diningMode = DiningMode.RETAIL,
            lines = listOf(line),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun qy(v: Int): Double = v.toDouble()

    @Test
    fun `GOLDEN TEST - 100 dollars at 8_25 percent tax equals 8_25 dollars tax`() {
        val order = orderWithItem(100.0, TaxCategory.PREPARED_FOOD)
        val taxes = engine.calculate(order).getOrThrow()
        val totalTax = taxes.fold(Money.ZERO) { acc, t -> acc + t.amount }
        // $100 × 8.25% = $8.25
        assertThat(totalTax.minorUnits).isEqualTo(825L)
        assertThat(totalTax.format("USD")).contains("8.25")
    }

    @Test
    fun `GOLDEN TEST - total with tax is 108_25 for 100 at 8_25 percent`() {
        val order = orderWithItem(100.0, TaxCategory.PREPARED_FOOD)
        val withTax = engine.apply(order).getOrThrow()
        // Total = subtotal + tax = 100 + 8.25 = 108.25
        assertThat(withTax.grandTotal.minorUnits).isEqualTo(10825L)
    }

    @Test
    fun `food_grocery category uses 2_5 percent rate`() {
        val order = orderWithItem(100.0, TaxCategory.FOOD_GROCERY)
        val taxes = engine.calculate(order).getOrThrow()
        val totalTax = taxes.fold(Money.ZERO) { acc, t -> acc + t.amount }
        // $100 × 2.5% = $2.50
        assertThat(totalTax.minorUnits).isEqualTo(250L)
    }

    @Test
    fun `exempt category has zero tax`() {
        val order = orderWithItem(100.0, TaxCategory.EXEMPT)
        val taxes = engine.calculate(order).getOrThrow()
        val totalTax = taxes.fold(Money.ZERO) { acc, t -> acc + t.amount }
        assertThat(totalTax.minorUnits).isEqualTo(0L)
    }

    @Test
    fun `tax exempt order has zero tax even for taxable categories`() {
        val order = orderWithItem(100.0, TaxCategory.PREPARED_FOOD).copy(taxExempt = true)
        val taxes = engine.calculate(order).getOrThrow()
        assertThat(taxes).isEmpty()
    }

    @Test
    fun `order-level discount reduces taxable base before tax`() {
        val now = System.currentTimeMillis()
        val line = OrderLine(
            id = OrderLineId("l1"), lineType = OrderLineType.ITEM,
            productId = ProductId("p1"), variantId = VariantId("v1"),
            name = "Item", quantity = Quantity.of(1), unitPrice = Money.of(100.0),
            taxCategory = TaxCategory.PREPARED_FOOD
        )
        val order = Order(
            id = OrderId("o1"), storeId = StoreId("s1"), registerId = RegisterId("r1"),
            employeeId = EmployeeId("e1"), diningMode = DiningMode.RETAIL,
            lines = listOf(line),
            orderLevelDiscount = Money.of(10.0), // $10 off
            createdAt = now, updatedAt = now
        )
        val taxes = engine.calculate(order).getOrThrow()
        val totalTax = taxes.fold(Money.ZERO) { acc, t -> acc + t.amount }
        // Taxable = 100 - 10 = 90 → tax = 90 × 8.25% = 7.425 → 7.43 (HALF_UP)
        assertThat(totalTax.minorUnits).isEqualTo(743L)
    }

    @Test
    fun `apply populates per-line taxAmount proportionally`() {
        val now = System.currentTimeMillis()
        val line1 = OrderLine(
            id = OrderLineId("l1"), lineType = OrderLineType.ITEM,
            productId = ProductId("p1"), variantId = VariantId("v1"),
            name = "Item1", quantity = Quantity.of(1), unitPrice = Money.of(80.0),
            taxCategory = TaxCategory.PREPARED_FOOD
        )
        val line2 = OrderLine(
            id = OrderLineId("l2"), lineType = OrderLineType.ITEM,
            productId = ProductId("p2"), variantId = VariantId("v2"),
            name = "Item2", quantity = Quantity.of(1), unitPrice = Money.of(20.0),
            taxCategory = TaxCategory.PREPARED_FOOD
        )
        val order = Order(
            id = OrderId("o1"), storeId = StoreId("s1"), registerId = RegisterId("r1"),
            employeeId = EmployeeId("e1"), diningMode = DiningMode.RETAIL,
            lines = listOf(line1, line2),
            createdAt = now, updatedAt = now
        )
        val applied = engine.apply(order).getOrThrow()
        // Total tax = 100 × 8.25% = 8.25
        assertThat(applied.taxTotal.minorUnits).isEqualTo(825L)
        // Line1 (80% of subtotal) should bear 80% of tax = 6.60
        val line1Tax = applied.lines[0].taxAmount.minorUnits
        val line2Tax = applied.lines[1].taxAmount.minorUnits
        assertThat(line1Tax + line2Tax).isEqualTo(825L)
        assertThat(line1Tax).isAtLeast(640L) // ~6.60, allowing for rounding
        assertThat(line1Tax).isAtMost(680L)
    }
}
