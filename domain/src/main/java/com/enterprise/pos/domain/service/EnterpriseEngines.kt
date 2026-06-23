package com.enterprise.pos.domain.service

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Promotion
import com.enterprise.pos.domain.model.PromotionScope
import com.enterprise.pos.domain.model.PromotionType
import com.enterprise.pos.domain.model.TipSuggestion
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Promotion engine — evaluates which promotions apply to a given order at the current time and
 * computes the resulting discount. Designed to handle:
 *  - Time-based promotions (happy hour)
 *  - Day-of-week filters
 *  - Category / product scope
 *  - Buy X Get Y logic
 *  - Stacking rules (priority-sorted)
 *  - Coupon codes
 */
class PromotionEngine(
    private val promotions: List<Promotion>
) {

    /** Returns the list of promotions applicable to this order at the current time. */
    fun applicable(order: Order, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Promotion> {
        val instant = Instant.ofEpochMilli(now)
        val zoned = instant.atZone(zone)
        val dayOfWeek = zoned.dayOfWeek.value
        val timeOfDay = zoned.toLocalTime()

        return promotions
            .filter { it.active }
            .filter { isWithinTimeWindow(it, timeOfDay) }
            .filter { it.daysOfWeek.isEmpty() || dayOfWeek in it.daysOfWeek }
            .filter { matchesScope(it, order) }
            .sortedByDescending { it.priority }
    }

    /** Apply the best applicable promotion (or stack if allowed by future extension). */
    fun applyBest(order: Order, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), code: String? = null): Result<Pair<Promotion?, Money>> = Result.catching {
        val eligible = applicable(order, now, zone)
        if (eligible.isEmpty()) return@catching null to Money.ZERO

        val byCode = code?.let { c -> eligible.firstOrNull { it.requiresCode && it.code == c } }
        val chosen = byCode ?: eligible.first()

        val discount = computeDiscount(chosen, order)
        chosen to discount
    }

    fun computeDiscount(promo: Promotion, order: Order): Money = when (promo.type) {
        PromotionType.PERCENT_OFF -> {
            val base = scopedBase(promo, order)
            Money.ofMinor((base.minorUnits * promo.percent / 100L))
        }
        PromotionType.FIXED_OFF -> {
            val base = scopedBase(promo, order)
            (promo.value ?: Money.ZERO).let { if (it > base) base else it }
        }
        PromotionType.BUY_X_GET_Y -> {
            val eligibleLines = eligibleLines(promo, order)
            val totalQty = eligibleLines.sumOf { it.quantity.asInt }
            val freeQty = (totalQty / promo.buyQty.coerceAtLeast(1)) * promo.getQty.coerceAtLeast(1)
            if (freeQty == 0 || eligibleLines.isEmpty()) Money.ZERO
            else {
                // Cheapest items are free
                val sortedByPrice = eligibleLines.sortedBy { it.unitPrice }
                var remaining = freeQty
                var discount = Money.ZERO
                for (line in sortedByPrice) {
                    if (remaining <= 0) break
                    val free = minOf(remaining, line.quantity.asInt)
                    discount = discount + line.unitPrice.times(free)
                    remaining -= free
                }
                discount
            }
        }
        PromotionType.FREE_ITEM -> {
            val eligibleLines = eligibleLines(promo, order)
            eligibleLines.minByOrNull { it.unitPrice }?.unitPrice ?: Money.ZERO
        }
        PromotionType.HAPPY_HOUR -> {
            val base = scopedBase(promo, order)
            Money.ofMinor(base.minorUnits * promo.percent / 100L)
        }
        PromotionType.COMBO -> {
            val base = scopedBase(promo, order)
            Money.ofMinor(base.minorUnits * promo.percent / 100L)
        }
        PromotionType.LOYALTY_REWARD -> {
            promo.value ?: Money.ZERO
        }
    }

    private fun scopedBase(promo: Promotion, order: Order): Money = when (promo.scope) {
        PromotionScope.ORDER, PromotionScope.ALL_ITEMS -> order.subtotal
        PromotionScope.CHEAPEST_ITEM -> order.lines.filter { it.lineType == OrderLineType.ITEM }.minByOrNull { it.unitPrice }?.unitPrice ?: Money.ZERO
        PromotionScope.CATEGORY -> order.lines.filter { it.lineType == OrderLineType.ITEM && promo.categoryIds.isNotEmpty() }.fold(Money.ZERO) { a, l -> a + l.lineTotal }
        PromotionScope.PRODUCT -> order.lines.filter { it.lineType == OrderLineType.ITEM && it.productId in promo.productIds }.fold(Money.ZERO) { a, l -> a + l.lineTotal }
    }

    private fun eligibleLines(promo: Promotion, order: Order): List<OrderLine> {
        val items = order.lines.filter { it.lineType == OrderLineType.ITEM }
        return when {
            promo.productIds.isNotEmpty() -> items.filter { it.productId in promo.productIds }
            else -> items
        }
    }

    private fun isWithinTimeWindow(promo: Promotion, now: LocalTime): Boolean {
        val start = Instant.ofEpochMilli(promo.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
        val end = Instant.ofEpochMilli(promo.endTime).atZone(ZoneId.systemDefault()).toLocalTime()
        return if (start <= end) now in start..end
        else now >= start || now <= end // overnight
    }

    private fun matchesScope(promo: Promotion, order: Order): Boolean = when (promo.scope) {
        PromotionScope.ORDER, PromotionScope.ALL_ITEMS -> order.lines.any { it.lineType == OrderLineType.ITEM }
        PromotionScope.CHEAPEST_ITEM -> order.lines.any { it.lineType == OrderLineType.ITEM }
        PromotionScope.CATEGORY -> order.lines.any { it.lineType == OrderLineType.ITEM }
        PromotionScope.PRODUCT -> order.lines.any { it.productId in promo.productIds }
    }
}

/**
 * Tip pool calculator. Supports three pool types: none (keep own tips), even split, hours-weighted.
 */
class TipPoolEngine {

    fun compute(
        shiftId: com.enterprise.pos.core.Id<com.enterprise.pos.core.ShiftTag>,
        totalTips: Money,
        employees: List<Pair<com.enterprise.pos.core.EmployeeId, Double>>, // id, hours worked
        poolType: com.enterprise.pos.domain.model.TipPoolType
    ): com.enterprise.pos.domain.model.TipPoolSummary {
        val totalHours = employees.sumOf { it.second }
        val entries = when (poolType) {
            com.enterprise.pos.domain.model.TipPoolType.NONE -> employees.map { (id, h) ->
                com.enterprise.pos.domain.model.TipPoolEntry(id, h, Money.ZERO, Money.ZERO, Money.ZERO)
            }
            com.enterprise.pos.domain.model.TipPoolType.EVEN_SPLIT -> {
                if (employees.isEmpty()) emptyList()
                else {
                    val per = Money.ofMinor(totalTips.minorUnits / employees.size)
                    val remainder = Money.ofMinor(totalTips.minorUnits - per.minorUnits * employees.size)
                    employees.mapIndexed { idx, (id, h) ->
                        val takeHome = if (idx == 0) per + remainder else per
                        com.enterprise.pos.domain.model.TipPoolEntry(id, h, Money.ZERO, takeHome, takeHome)
                    }
                }
            }
            com.enterprise.pos.domain.model.TipPoolType.HOURS_WEIGHTED -> {
                if (totalHours <= 0.0) emptyList()
                else employees.map { (id, h) ->
                    val share = (h / totalHours)
                    val pooled = Money.ofMinor((totalTips.minorUnits * share).toLong())
                    com.enterprise.pos.domain.model.TipPoolEntry(id, h, Money.ZERO, pooled, pooled)
                }
            }
            com.enterprise.pos.domain.model.TipPoolType.ROLE_WEIGHTED -> {
                // Simplified: same as hours-weighted for now; production would weight by role.
                if (totalHours <= 0.0) emptyList()
                else employees.map { (id, h) ->
                    val share = (h / totalHours)
                    val pooled = Money.ofMinor((totalTips.minorUnits * share).toLong())
                    com.enterprise.pos.domain.model.TipPoolEntry(id, h, Money.ZERO, pooled, pooled)
                }
            }
        }
        return com.enterprise.pos.domain.model.TipPoolSummary(shiftId, totalTips, totalHours, entries, poolType)
    }
}

/**
 * ABC analysis — classifies products as A (top 80% of revenue), B (next 15%), C (bottom 5%).
 */
class AbcAnalysisEngine {
    fun classify(
        sales: List<com.enterprise.pos.domain.model.AbcAnalysis>
    ): List<com.enterprise.pos.domain.model.AbcAnalysis> {
        if (sales.isEmpty()) return emptyList()
        val sorted = sales.sortedByDescending { it.revenue.minorUnits }
        val totalRevenue = sorted.sumOf { it.revenue.minorUnits }.coerceAtLeast(1L)
        var cumulative = 0.0
        return sorted.map { item ->
            val contribution = (item.revenue.minorUnits.toDouble() / totalRevenue) * 100.0
            cumulative += contribution
            val cls = when {
                cumulative <= 80.0 -> com.enterprise.pos.domain.model.AbcClass.A
                cumulative <= 95.0 -> com.enterprise.pos.domain.model.AbcClass.B
                else -> com.enterprise.pos.domain.model.AbcClass.C
            }
            item.copy(revenueContribution = contribution, cumulativeContribution = cumulative, classification = cls)
        }
    }
}

/**
 * Suggests tip amounts based on configurable suggestions.
 */
class TipSuggestionsEngine {
    fun compute(subtotal: Money, suggestions: List<TipSuggestion>): List<Pair<String, Money>> {
        return suggestions.map { s ->
            when (s) {
                is TipSuggestion.Percentage -> {
                    val amount = Percent.of(s.percent.toDouble()).of(subtotal)
                    "${s.percent}%" to amount
                }
                is TipSuggestion.Fixed -> "Fixed" to s.amount
                TipSuggestion.Custom -> "Custom" to Money.ZERO
                TipSuggestion.NoTip -> "No Tip" to Money.ZERO
            }
        }
    }
}

/**
 * Split tender engine — divides a payment across multiple tenders.
 */
class SplitTenderEngine {
    fun allocate(
        total: Money,
        requested: List<Pair<String, Money>>, // provider name -> requested amount
        defaultProvider: String
    ): Result<List<com.enterprise.pos.domain.model.TenderSplit>> = Result.catching {
        val allocated = requested.sumOf { it.second.minorUnits }
        val remaining = total.minorUnits - allocated
        val splits = requested.map { (provider, amount) ->
            com.enterprise.pos.domain.model.TenderSplit(provider = provider, amount = amount)
        }.toMutableList()
        if (remaining > 0) {
            splits.add(com.enterprise.pos.domain.model.TenderSplit(provider = defaultProvider, amount = Money.ofMinor(remaining)))
        } else if (remaining < 0) {
            throw IllegalArgumentException("Over-allocated by ${Money.ofMinor(-remaining).format()}")
        }
        splits
    }
}
