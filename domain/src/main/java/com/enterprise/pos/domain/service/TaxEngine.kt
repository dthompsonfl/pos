package com.enterprise.pos.domain.service

import com.enterprise.pos.core.Money
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.model.TaxLine
import kotlinx.serialization.Serializable
import java.math.RoundingMode

/**
 * Tax engine — supports per-category tax rates, compound taxes (e.g. Quebec QST + GST),
 * inclusive vs. exclusive pricing, tax-exempt orders, and deterministic rounding.
 *
 * Configuration is per-store; the engine itself is stateless and thread-safe.
 */
interface TaxEngine {
    /** Compute the tax lines for an order. Per-line tax category is read from OrderLine.taxCategory
     *  which is captured at sale time so historical orders are not affected by later product changes. */
    fun calculate(order: Order): Result<List<TaxLine>>

    /** Compute and return a new Order with taxLines populated and per-line taxAmount populated. */
    fun apply(order: Order): Result<Order>
}

@Serializable
data class TaxRule(
    val name: String,
    val rate: Percent,
    val appliesTo: Set<TaxCategory>,
    val compoundOn: List<String> = emptyList(), // names of taxes this rule compounds on
    val isInclusive: Boolean = false // if true, the rate is already included in the item price
)

@Serializable
data class TaxConfiguration(
    val rules: List<TaxRule>,
    val defaultCategory: TaxCategory = TaxCategory.STANDARD
) {
    companion object {
        val DEFAULT = TaxConfiguration(
            rules = listOf(
                TaxRule(
                    name = "Sales Tax",
                    rate = Percent.of(8.25),
                    appliesTo = TaxCategory.values().toSet() - TaxCategory.EXEMPT - TaxCategory.ZERO_RATED
                )
            )
        )

        val RESTAURANT = TaxConfiguration(
            rules = listOf(
                TaxRule(
                    name = "Food Tax",
                    rate = Percent.of(2.5),
                    appliesTo = setOf(TaxCategory.FOOD_GROCERY)
                ),
                TaxRule(
                    name = "Sales Tax",
                    rate = Percent.of(8.25),
                    appliesTo = setOf(TaxCategory.STANDARD, TaxCategory.PREPARED_FOOD, TaxCategory.CLOTHING)
                )
            )
        )
    }
}

class DefaultTaxEngine(private val config: TaxConfiguration) : TaxEngine {

    override fun calculate(order: Order): Result<List<TaxLine>> = Result.catching {
        if (order.taxExempt) return@catching emptyList()
        val itemLines = order.lines.filter { it.lineType == OrderLineType.ITEM }
        if (itemLines.isEmpty()) return@catching emptyList()

        // Group line totals by their captured tax category.
        val taxableByCategory = HashMap<TaxCategory, Money>()
        for (line in itemLines) {
            val category = line.taxCategory
            val lineTaxable = line.lineTotal
            taxableByCategory[category] = (taxableByCategory[category] ?: Money.ZERO) + lineTaxable
        }

        // Apply order-level discount proportionally across categories.
        if (!order.orderLevelDiscount.isZero()) {
            val proportionBase = order.subtotal
            if (!proportionBase.isZero()) {
                val scale = order.orderLevelDiscount.asBigDecimal
                    .divide(proportionBase.asBigDecimal, 10, RoundingMode.HALF_UP)
                val scaled = taxableByCategory.toMutableMap()
                for ((k, v) in taxableByCategory) {
                    val reduced = v - (v * scale)
                    scaled[k] = reduced.atLeastZero()
                }
                taxableByCategory.clear()
                taxableByCategory.putAll(scaled)
            }
        }

        val results = mutableListOf<TaxLine>()

        for (rule in config.rules) {
            val baseForRule = taxableByCategory.entries
                .filter { it.key in rule.appliesTo }
                .fold(Money.ZERO) { acc, e -> acc + e.value }

            if (rule.compoundOn.isNotEmpty()) {
                val compoundBase = results
                    .filter { it.name in rule.compoundOn }
                    .fold(Money.ZERO) { acc, t -> acc + t.amount }
                val taxableWithCompound = baseForRule + compoundBase
                val amount = rule.rate.of(taxableWithCompound)
                if (!amount.isZero()) {
                    results.add(TaxLine(rule.name, rule.rate, amount, config.defaultCategory))
                }
            } else {
                val amount = rule.rate.of(baseForRule)
                if (!amount.isZero()) {
                    results.add(TaxLine(rule.name, rule.rate, amount, config.defaultCategory))
                }
            }
        }
        results
    }

    override fun apply(order: Order): Result<Order> = Result.catching {
        val taxLines = calculate(order).getOrThrow()
        // Distribute tax back to lines proportionally so each line carries its own tax amount
        // for receipt-level display and refunds.
        val totalTaxable = order.taxableAmount
        val updatedLines = order.lines.map { line ->
            if (line.lineType != OrderLineType.ITEM || totalTaxable.isZero()) {
                line
            } else {
                val lineShare = line.lineTotal.asBigDecimal.divide(totalTaxable.asBigDecimal, 10, RoundingMode.HALF_UP)
                val lineTax = order.taxTotal.asBigDecimal.multiply(lineShare).setScale(0, RoundingMode.HALF_UP)
                line.copy(taxAmount = Money.ofMinor(lineTax.longValueExact()))
            }
        }
        order.copy(lines = updatedLines, taxLines = taxLines)
    }
}
