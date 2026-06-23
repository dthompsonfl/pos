package com.enterprise.pos.domain.service

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.Percent
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.PromotionScope
import com.enterprise.pos.domain.model.PromotionType
import com.enterprise.pos.domain.model.TaxCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class PromotionEngineTest {

    private fun line(price: Double, qty: Int = 1, productId: String = "prod-1", categoryId: String = "cat-1"): OrderLine {
        return OrderLine(
            id = com.enterprise.pos.core.OrderLineId("line-${System.nanoTime()}"),
            lineType = OrderLineType.ITEM,
            productId = com.enterprise.pos.core.ProductId(productId),
            variantId = com.enterprise.pos.core.VariantId("var-1"),
            name = "Test Item",
            quantity = com.enterprise.pos.core.Quantity.of(qty),
            unitPrice = Money.of(price),
            taxCategory = TaxCategory.STANDARD
        )
    }

    private fun order(vararg lines: OrderLine): Order {
        return Order(
            id = com.enterprise.pos.core.OrderId("order-1"),
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            lines = lines.toList(),
            createdAt = 0L
        )
    }

    private fun promotion(
        type: PromotionType,
        scope: PromotionScope = PromotionScope.ORDER,
        percent: Int = 0,
        value: Money? = null,
        buyQty: Int = 0,
        getQty: Int = 0,
        active: Boolean = true,
        startTime: Long = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(),
        endTime: Long = Instant.parse("2024-12-31T23:59:59Z").toEpochMilli(),
        daysOfWeek: Set<Int> = emptySet(),
        priority: Int = 0,
        productIds: List<String> = emptyList(),
        categoryIds: List<String> = emptyList(),
        requiresCode: Boolean = false,
        code: String? = null
    ): Promotion {
        return Promotion(
            id = com.enterprise.pos.core.Id.random(),
            name = "Test Promo",
            description = "",
            type = type,
            scope = scope,
            percent = percent,
            value = value,
            buyQty = buyQty,
            getQty = getQty,
            active = active,
            startTime = startTime,
            endTime = endTime,
            daysOfWeek = daysOfWeek,
            priority = priority,
            productIds = productIds.map { com.enterprise.pos.core.ProductId(it) },
            categoryIds = categoryIds.map { com.enterprise.pos.core.CategoryId(it) },
            requiresCode = requiresCode,
            code = code
        )
    }

    private val now = Instant.parse("2024-06-15T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")

    @Test
    fun `percentage off order`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10)
        ))
        val o = order(line(100.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(1000L) // 10% of 100 = 10
    }

    @Test
    fun `fixed off order`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.FIXED_OFF, value = Money.of(15.00))
        ))
        val o = order(line(100.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(1500L)
    }

    @Test
    fun `fixed off capped at subtotal`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.FIXED_OFF, value = Money.of(150.00))
        ))
        val o = order(line(100.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(10000L) // capped at 100
    }

    @Test
    fun `BOGO discount`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.BUY_X_GET_Y, buyQty = 2, getQty = 1)
        ))
        val o = order(line(10.0, qty = 4))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        // 4 items, buy 2 get 1 free = 2 free items (10 * 2 = 20)
        assertThat(discount.minorUnits).isEqualTo(2000L)
    }

    @Test
    fun `free item discount`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.FREE_ITEM)
        ))
        val o = order(line(10.0), line(5.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        // cheapest item is free
        assertThat(discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `happy hour discount`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.HAPPY_HOUR, percent = 50)
        ))
        val o = order(line(20.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(1000L)
    }

    @Test
    fun `combo discount`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.COMBO, percent = 20)
        ))
        val o = order(line(50.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(1000L)
    }

    @Test
    fun `loyalty reward discount`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.LOYALTY_REWARD, value = Money.of(5.00))
        ))
        val o = order(line(100.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `time based filtering excludes outside window`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                startTime = Instant.parse("2024-06-15T20:00:00Z").toEpochMilli(),
                endTime = Instant.parse("2024-06-15T22:00:00Z").toEpochMilli()
            )
        ))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now) // now is 12:00
        assertThat(applicable).isEmpty()
    }

    @Test
    fun `time based filtering includes inside window`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                startTime = Instant.parse("2024-06-15T10:00:00Z").toEpochMilli(),
                endTime = Instant.parse("2024-06-15T14:00:00Z").toEpochMilli()
            )
        ))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now)
        assertThat(applicable).hasSize(1)
    }

    @Test
    fun `day of week filtering`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                daysOfWeek = setOf(6) // Saturday, June 15 2024 is Saturday
            )
        ))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now)
        assertThat(applicable).hasSize(1)
    }

    @Test
    fun `day of week filtering excludes wrong day`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                daysOfWeek = setOf(1) // Monday
            )
        ))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now)
        assertThat(applicable).isEmpty()
    }

    @Test
    fun `product scope filtering`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                scope = PromotionScope.PRODUCT,
                productIds = listOf("prod-1")
            )
        ))
        val o = order(line(50.0, productId = "prod-1"), line(50.0, productId = "prod-2"))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(500L) // only prod-1
    }

    @Test
    fun `category scope filtering`() {
        val engine = PromotionEngine(listOf(
            promotion(
                PromotionType.PERCENT_OFF,
                percent = 10,
                scope = PromotionScope.CATEGORY,
                categoryIds = listOf("cat-1")
            )
        ))
        val o = order(line(50.0, categoryId = "cat-1"), line(50.0, categoryId = "cat-2"))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `inactive promotion excluded`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10, active = false)
        ))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now)
        assertThat(applicable).isEmpty()
    }

    @Test
    fun `applyBest returns best promotion`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10, priority = 1),
            promotion(PromotionType.PERCENT_OFF, percent = 20, priority = 2)
        ))
        val o = order(line(100.0))
        val (best, discount) = engine.applyBest(o, now).getOrThrow()
        assertThat(best).isNotNull()
        assertThat(discount.minorUnits).isEqualTo(2000L)
    }

    @Test
    fun `applyBest with code`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10, requiresCode = true, code = "SAVE10"),
            promotion(PromotionType.PERCENT_OFF, percent = 20, requiresCode = true, code = "SAVE20")
        ))
        val o = order(line(100.0))
        val (best, discount) = engine.applyBest(o, now, code = "SAVE20").getOrThrow()
        assertThat(best).isNotNull()
        assertThat(discount.minorUnits).isEqualTo(2000L)
    }

    @Test
    fun `applyBest returns zero when no promotions`() {
        val engine = PromotionEngine(emptyList())
        val o = order(line(100.0))
        val (best, discount) = engine.applyBest(o, now).getOrThrow()
        assertThat(best).isNull()
        assertThat(discount.isZero()).isTrue()
    }

    @Test
    fun `priority sorting highest first`() {
        val low = promotion(PromotionType.PERCENT_OFF, percent = 10, priority = 1)
        val high = promotion(PromotionType.PERCENT_OFF, percent = 20, priority = 2)
        val engine = PromotionEngine(listOf(low, high))
        val o = order(line(100.0))
        val applicable = engine.applicable(o, now)
        assertThat(applicable[0].priority).isEqualTo(2)
        assertThat(applicable[1].priority).isEqualTo(1)
    }

    @Test
    fun `cheapest item scope`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10, scope = PromotionScope.CHEAPEST_ITEM)
        ))
        val o = order(line(100.0), line(50.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        // 10% of cheapest item (50) = 5
        assertThat(discount.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `all items scope`() {
        val engine = PromotionEngine(listOf(
            promotion(PromotionType.PERCENT_OFF, percent = 10, scope = PromotionScope.ALL_ITEMS)
        ))
        val o = order(line(100.0), line(50.0))
        val discount = engine.computeDiscount(engine.applicable(o, now).first(), o)
        assertThat(discount.minorUnits).isEqualTo(1500L) // 10% of 150
    }
}

class SplitTenderEngineTest {

    private val engine = SplitTenderEngine()

    @Test
    fun `exact match allocation`() {
        val result = engine.allocate(
            Money.of(100.0),
            listOf("CASH" to Money.of(100.0)),
            "CASH"
        ).getOrThrow()
        assertThat(result).hasSize(1)
        assertThat(result[0].amount.minorUnits).isEqualTo(10000L)
    }

    @Test
    fun `underpayment adds remainder to default provider`() {
        val result = engine.allocate(
            Money.of(100.0),
            listOf("CASH" to Money.of(60.0)),
            "CARD"
        ).getOrThrow()
        assertThat(result).hasSize(2)
        assertThat(result[0].amount.minorUnits).isEqualTo(6000L)
        assertThat(result[1].amount.minorUnits).isEqualTo(4000L)
        assertThat(result[1].provider).isEqualTo("CARD")
    }

    @Test
    fun `overpayment fails`() {
        val result = engine.allocate(
            Money.of(100.0),
            listOf("CASH" to Money.of(120.0)),
            "CARD"
        )
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun `multiple tenders`() {
        val result = engine.allocate(
            Money.of(100.0),
            listOf("CASH" to Money.of(30.0), "CARD" to Money.of(70.0)),
            "CASH"
        ).getOrThrow()
        assertThat(result).hasSize(2)
        assertThat(result.sumOf { it.amount.minorUnits }).isEqualTo(10000L)
    }

    @Test
    fun `empty requested adds full amount to default`() {
        val result = engine.allocate(
            Money.of(50.0),
            emptyList(),
            "CASH"
        ).getOrThrow()
        assertThat(result).hasSize(1)
        assertThat(result[0].amount.minorUnits).isEqualTo(5000L)
    }
}

class TipPoolEngineTest {

    private val engine = TipPoolEngine()
    private val shiftId = com.enterprise.pos.core.Id<com.enterprise.pos.core.ShiftTag>("shift-1")

    @Test
    fun `none pool type returns zero for everyone`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 8.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.NONE
        )
        assertThat(result.totalTips.minorUnits).isEqualTo(10000L)
        assertThat(result.entries).hasSize(2)
        assertThat(result.entries.all { it.pooledTips.isZero() }).isTrue()
    }

    @Test
    fun `even split divides equally`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 8.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.EVEN_SPLIT
        )
        assertThat(result.entries).hasSize(2)
        assertThat(result.entries[0].pooledTips.minorUnits).isEqualTo(5000L)
        assertThat(result.entries[1].pooledTips.minorUnits).isEqualTo(5000L)
    }

    @Test
    fun `even split with odd total assigns remainder to first`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.01),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 8.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.EVEN_SPLIT
        )
        val totalDistributed = result.entries.sumOf { it.pooledTips.minorUnits }
        assertThat(totalDistributed).isEqualTo(10001L)
        // Remainder of 1 goes to first entry
        assertThat(result.entries[0].pooledTips.minorUnits - result.entries[1].pooledTips.minorUnits).isAtMost(1L)
    }

    @Test
    fun `even split with empty employees returns empty`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            emptyList(),
            com.enterprise.pos.domain.model.TipPoolType.EVEN_SPLIT
        )
        assertThat(result.entries).isEmpty()
    }

    @Test
    fun `hours weighted split`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 4.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.HOURS_WEIGHTED
        )
        val totalHours = 12.0
        val emp1Share = (8.0 / totalHours) * 10000L
        assertThat(result.entries[0].pooledTips.minorUnits).isEqualTo(emp1Share.toLong())
    }

    @Test
    fun `hours weighted with zero hours returns empty`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 0.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.HOURS_WEIGHTED
        )
        assertThat(result.entries).isEmpty()
    }

    @Test
    fun `role weighted behaves like hours weighted`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 4.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.ROLE_WEIGHTED
        )
        assertThat(result.entries).hasSize(2)
        assertThat(result.entries.sumOf { it.pooledTips.minorUnits }).isEqualTo(10000L)
    }

    @Test
    fun `total hours and total tips are correct`() {
        val result = engine.compute(
            shiftId,
            Money.of(100.0),
            listOf(
                com.enterprise.pos.core.EmployeeId("emp-1") to 8.0,
                com.enterprise.pos.core.EmployeeId("emp-2") to 4.0
            ),
            com.enterprise.pos.domain.model.TipPoolType.HOURS_WEIGHTED
        )
        assertThat(result.totalHours).isEqualTo(12.0)
        assertThat(result.totalTips).isEqualTo(Money.of(100.0))
    }
}

class TipSuggestionsEngineTest {

    private val engine = TipSuggestionsEngine()

    @Test
    fun `percentage suggestions`() {
        val result = engine.compute(Money.of(50.0), listOf(
            com.enterprise.pos.domain.model.TipSuggestion.Percentage(15),
            com.enterprise.pos.domain.model.TipSuggestion.Percentage(20)
        ))
        assertThat(result).hasSize(2)
        assertThat(result[0].first).isEqualTo("15%")
        assertThat(result[0].second.minorUnits).isEqualTo(750L)
        assertThat(result[1].second.minorUnits).isEqualTo(1000L)
    }

    @Test
    fun `fixed suggestion`() {
        val result = engine.compute(Money.of(50.0), listOf(
            com.enterprise.pos.domain.model.TipSuggestion.Fixed(Money.of(5.00))
        ))
        assertThat(result[0].first).isEqualTo("Fixed")
        assertThat(result[0].second.minorUnits).isEqualTo(500L)
    }

    @Test
    fun `custom and no tip suggestions`() {
        val result = engine.compute(Money.of(50.0), listOf(
            com.enterprise.pos.domain.model.TipSuggestion.Custom,
            com.enterprise.pos.domain.model.TipSuggestion.NoTip
        ))
        assertThat(result[0].first).isEqualTo("Custom")
        assertThat(result[0].second.isZero()).isTrue()
        assertThat(result[1].first).isEqualTo("No Tip")
        assertThat(result[1].second.isZero()).isTrue()
    }
}

class AbcAnalysisEngineTest {

    private val engine = AbcAnalysisEngine()

    @Test
    fun `classify A items top 80 percent`() {
        val sales = listOf(
            com.enterprise.pos.domain.model.AbcAnalysis(
                productId = com.enterprise.pos.core.ProductId("p1"), productName = "A", unitsSold = 1,
                revenue = Money.of(800.0), revenueContribution = 0.0, cumulativeContribution = 0.0, classification = com.enterprise.pos.domain.model.AbcClass.C
            ),
            com.enterprise.pos.domain.model.AbcAnalysis(
                productId = com.enterprise.pos.core.ProductId("p2"), productName = "B", unitsSold = 1,
                revenue = Money.of(150.0), revenueContribution = 0.0, cumulativeContribution = 0.0, classification = com.enterprise.pos.domain.model.AbcClass.C
            ),
            com.enterprise.pos.domain.model.AbcAnalysis(
                productId = com.enterprise.pos.core.ProductId("p3"), productName = "C", unitsSold = 1,
                revenue = Money.of(50.0), revenueContribution = 0.0, cumulativeContribution = 0.0, classification = com.enterprise.pos.domain.model.AbcClass.C
            )
        )
        val result = engine.classify(sales)
        assertThat(result[0].classification).isEqualTo(com.enterprise.pos.domain.model.AbcClass.A)
        assertThat(result[1].classification).isEqualTo(com.enterprise.pos.domain.model.AbcClass.B)
        assertThat(result[2].classification).isEqualTo(com.enterprise.pos.domain.model.AbcClass.C)
    }

    @Test
    fun `empty list returns empty`() {
        assertThat(engine.classify(emptyList())).isEmpty()
    }
}
