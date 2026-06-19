package com.enterprise.pos.domain.service

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.ShiftId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.PromotionScope
import com.enterprise.pos.domain.model.PromotionType
import com.enterprise.pos.domain.model.AbcAnalysis
import com.enterprise.pos.domain.model.AbcClass
import com.enterprise.pos.domain.model.TipPoolType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class PromotionEngineTest {

    private val zone = ZoneId.systemDefault()

    private fun newOrder(vararg items: Pair<String, Money>): Order {
        val now = System.currentTimeMillis()
        val lines = items.mapIndexed { i, (name, price) ->
            OrderLine(
                id = OrderLineId("line-$i"), lineType = OrderLineType.ITEM,
                productId = ProductId("p-$i"), variantId = VariantId("v-$i"),
                name = name, quantity = com.enterprise.pos.core.Quantity.of(1), unitPrice = price
            )
        }
        return Order(
            id = OrderId("o1"), storeId = StoreId("s"), registerId = RegisterId("r"),
            employeeId = EmployeeId("e"), diningMode = DiningMode.RETAIL,
            status = com.enterprise.pos.domain.model.OrderStatus.OPEN,
            lines = lines, createdAt = now, updatedAt = now
        )
    }

    private fun promo(
        type: PromotionType, scope: PromotionScope, percent: Int = 0,
        value: Money? = null, buyQty: Int = 0, getQty: Int = 0,
        productIds: List<ProductId> = emptyList(),
        startHour: Int = 0, endHour: Int = 24
    ): Promotion {
        val today = java.time.LocalDate.now(zone)
        val start = today.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
        val end = today.atTime(endHour, 0).atZone(zone).toInstant().toEpochMilli()
        return Promotion(
            id = Id("promo"), name = "Test", description = "", type = type, scope = scope,
            value = value, percent = percent, buyQty = buyQty, getQty = getQty,
            productIds = productIds, startTime = start, endTime = end, active = true
        )
    }

    @Test
    fun `percentage off order applies to subtotal`() {
        val p = promo(PromotionType.PERCENT_OFF, PromotionScope.ORDER, percent = 10)
        val engine = PromotionEngine(listOf(p))
        val order = newOrder("Pizza" to Money.of(20.0))
        val discount = engine.computeDiscount(p, order)
        assertThat(discount.minorUnits).isEqualTo(200L) // 10% of $20 = $2.00
    }

    @Test
    fun `fixed off is capped at subtotal`() {
        val p = promo(PromotionType.FIXED_OFF, PromotionScope.ORDER, value = Money.of(50.0))
        val engine = PromotionEngine(listOf(p))
        val order = newOrder("Pizza" to Money.of(20.0))
        val discount = engine.computeDiscount(p, order)
        assertThat(discount.minorUnits).isEqualTo(2000L) // capped at $20
    }

    @Test
    fun `bogo gives cheapest items free`() {
        val p = promo(
            PromotionType.BUY_X_GET_Y, PromotionScope.PRODUCT,
            buyQty = 1, getQty = 1,
            productIds = listOf(ProductId("p-0"), ProductId("p-1"))
        )
        val engine = PromotionEngine(listOf(p))
        val order = newOrder(
            "Cheap" to Money.of(5.0),
            "Expensive" to Money.of(20.0)
        )
        val discount = engine.computeDiscount(p, order)
        // Buy 2, get 1 free → cheapest ($5) is free
        assertThat(discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `happy hour promotion gives percentage off`() {
        val p = promo(PromotionType.HAPPY_HOUR, PromotionScope.CATEGORY, percent = 20)
        val engine = PromotionEngine(listOf(p))
        val order = newOrder("Cocktail" to Money.of(10.0))
        val discount = engine.computeDiscount(p, order)
        assertThat(discount.minorUnits).isEqualTo(200L) // 20% of $10 = $2
    }

    @Test
    fun `promotion only applies during its time window`() {
        val now = java.time.LocalTime.now(zone)
        // If it's currently 10am, a 2pm-5pm promotion should not apply
        val p = promo(
            PromotionType.HAPPY_HOUR, PromotionScope.ORDER, percent = 20,
            startHour = 14, endHour = 17
        )
        val engine = PromotionEngine(listOf(p))
        val order = newOrder("Drink" to Money.of(10.0))
        val applicable = engine.applicable(order, System.currentTimeMillis(), zone)
        val inWindow = now.hour in 14..16
        assertThat(applicable.isEmpty()).isEqualTo(!inWindow)
    }
}

class TipPoolEngineTest {

    @Test
    fun `even split divides tips equally among employees`() {
        val engine = TipPoolEngine()
        val shiftId = ShiftId("shift-1")
        val employees = listOf(
            EmployeeId("emp-1") to 8.0,
            EmployeeId("emp-2") to 8.0,
            EmployeeId("emp-3") to 8.0
        )
        val pool = engine.compute(shiftId, Money.of(120.0), employees, TipPoolType.EVEN_SPLIT)
        assertThat(pool.entries).hasSize(3)
        // Each gets $40, but remainder handling may shift a cent
        pool.entries.forEach { entry ->
            assertThat(entry.totalTakeHome.minorUnits).isIn(listOf(4000L, 4001L))
        }
    }

    @Test
    fun `hours weighted splits proportionally`() {
        val engine = TipPoolEngine()
        val shiftId = ShiftId("shift-1")
        val employees = listOf(
            EmployeeId("emp-1") to 8.0,
            EmployeeId("emp-2") to 4.0
        )
        val pool = engine.compute(shiftId, Money.of(120.0), employees, TipPoolType.HOURS_WEIGHTED)
        // 8h = $80, 4h = $40
        assertThat(pool.entries[0].totalTakeHome.minorUnits).isEqualTo(8000L)
        assertThat(pool.entries[1].totalTakeHome.minorUnits).isEqualTo(4000L)
    }

    @Test
    fun `none pool keeps zero distribution`() {
        val engine = TipPoolEngine()
        val shiftId = ShiftId("shift-1")
        val employees = listOf(EmployeeId("emp-1") to 8.0)
        val pool = engine.compute(shiftId, Money.of(120.0), employees, TipPoolType.NONE)
        assertThat(pool.entries[0].pooledTips.minorUnits).isEqualTo(0L)
    }
}

class AbcAnalysisEngineTest {

    @Test
    fun `classifies items into ABC buckets`() {
        val engine = AbcAnalysisEngine()
        val sales = listOf(
            abcItem("A1", "Item A", 10000), // 100.00
            abcItem("A2", "Item B", 8000),  // 80.00 -> cumulative 80%, A
            abcItem("B1", "Item C", 5000),  // 50.00
            abcItem("B2", "Item D", 3000),  // 30.00 -> cumulative ~95%, B
            abcItem("C1", "Item E", 1000)   // 10.00 -> cumulative 100%, C
        )
        val classified = engine.classify(sales)
        assertThat(classified[0].classification).isEqualTo(AbcClass.A)
        assertThat(classified[1].classification).isEqualTo(AbcClass.A)
        // C/D land in B
        assertThat(classified[2].classification).isEqualTo(AbcClass.B)
        assertThat(classified[3].classification).isEqualTo(AbcClass.B)
        // E is C
        assertThat(classified[4].classification).isEqualTo(AbcClass.C)
    }

    private fun abcItem(id: String, name: String, revenueMinor: Long): AbcAnalysis = AbcAnalysis(
        productId = ProductId(id), productName = name,
        unitsSold = 10, revenue = Money.ofMinor(revenueMinor),
        revenueContribution = 0.0, cumulativeContribution = 0.0,
        classification = AbcClass.C
    )
}

class SplitTenderEngineTest {

    @Test
    fun `allocates remainder to default provider when not fully covered`() {
        val engine = SplitTenderEngine()
        val total = Money.of(100.0)
        val requested = listOf("STRIPE" to Money.of(60.0))
        val splits = engine.allocate(total, requested, "CASH").getOrThrow()
        assertThat(splits).hasSize(2)
        assertThat(splits[0].provider).isEqualTo("STRIPE")
        assertThat(splits[0].amount.minorUnits).isEqualTo(6000L)
        assertThat(splits[1].provider).isEqualTo("CASH")
        assertThat(splits[1].amount.minorUnits).isEqualTo(4000L)
    }

    @Test
    fun `fails when over-allocated`() {
        val engine = SplitTenderEngine()
        val total = Money.of(50.0)
        val requested = listOf("STRIPE" to Money.of(60.0))
        val result = engine.allocate(total, requested, "CASH")
        assertThat(result).isInstanceOf(com.enterprise.pos.core.Result.Failure::class.java)
    }
}
