package com.enterprise.pos.core

import kotlinx.serialization.Serializable
import java.io.Serializable as JSerializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private fun fallbackCurrency(): Currency = Currency.getInstance("USD")

private fun defaultMoneyCurrency(): Currency =
    runCatching { Currency.getInstance(Locale.getDefault()) }.getOrDefault(fallbackCurrency())

private fun moneyFormatLocale(): Locale =
    Locale.getDefault().takeIf { it.country.isNotBlank() } ?: Locale.US

/**
 * Type-safe monetary value. All money in the entire POS flows through this class — never
 * use Double or Float for currency. Backed by Long minor-units (e.g. cents) — exact, fast,
 * serializable.
 *
 * Arithmetic rules:
 *  - All operations use BigDecimal internally for rounding determinism.
 *  - Rounding mode is HALF_UP (standard for retail).
 *  - Scale is fixed at 2 decimal places (minor units = major × 100).
 *  - Negative values are allowed (e.g. refunds, adjustments).
 *
 * Immutable and arithmetic-safe.
 */
@Serializable(with = MoneySerializer::class)
@JvmInline
value class Money private constructor(
    val minorUnits: Long
) : JSerializable, Comparable<Money> {

    val asBigDecimal: BigDecimal
        get() = BigDecimal.valueOf(minorUnits).movePointLeft(2)

    operator fun plus(other: Money): Money = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money): Money = Money(minorUnits - other.minorUnits)

    /** Multiply by an integer scalar (e.g. unit price × quantity). */
    operator fun times(factor: Int): Money = Money(minorUnits * factor)

    /** Multiply by a BigDecimal factor with HALF_UP rounding to 2 decimals.
     *  Use this for tax (price × 0.0825) and fractional quantities (price × 1.25). */
    operator fun times(factor: BigDecimal): Money {
        val raw = BigDecimal.valueOf(minorUnits).times(factor)
        val rounded = raw.setScale(0, RoundingMode.HALF_UP)
        return Money(rounded.longValueExact())
    }

    /** Multiply by a [Quantity] — supports fractional quantities (weighted items). */
    operator fun times(quantity: Quantity): Money = this * quantity.value

    operator fun div(divisor: Int): Money {
        require(divisor != 0) { "Cannot divide money by zero" }
        // Use HALF_UP rounding so $10 / 3 = $3.33 (not $3.34 truncated)
        val raw = BigDecimal.valueOf(minorUnits).divide(BigDecimal(divisor), 0, RoundingMode.HALF_UP)
        return Money(raw.longValueExact())
    }

    /** Divide by another Money, returning the BigDecimal ratio. Useful for percentage calcs. */
    operator fun div(other: Money): BigDecimal {
        require(!other.isZero()) { "Cannot divide by zero money" }
        return BigDecimal.valueOf(minorUnits).divide(BigDecimal.valueOf(other.minorUnits), 10, RoundingMode.HALF_UP)
    }

    fun isPositive(): Boolean = minorUnits > 0
    fun isNegative(): Boolean = minorUnits < 0
    fun isZero(): Boolean = minorUnits == 0L

    /** Return this if positive, else ZERO. */
    fun atLeastZero(): Money = if (minorUnits < 0) ZERO else this

    /** Cap at the given maximum. */
    fun cappedAt(max: Money): Money = if (this > max) max else this

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    fun format(currency: Currency = defaultMoneyCurrency()): String {
        val fmt = NumberFormat.getCurrencyInstance(moneyFormatLocale())
        fmt.currency = currency
        fmt.minimumFractionDigits = 2
        fmt.maximumFractionDigits = 2
        return fmt.format(asBigDecimal)
    }

    /** Format with a fixed currency code (ISO 4217). */
    fun format(currencyCode: String): String =
        format(runCatching { Currency.getInstance(currencyCode) }.getOrDefault(fallbackCurrency()))

    companion object {
        val ZERO = Money(0L)

        /** Construct from a major-unit Double (e.g. 12.50 = $12.50). */
        fun of(major: Double): Money {
            val bd = BigDecimal.valueOf(major).setScale(2, RoundingMode.HALF_UP)
            return Money(bd.movePointRight(2).longValueExact())
        }

        /** Construct from a major-unit BigDecimal. Preferred for parsing strings. */
        fun of(major: BigDecimal): Money {
            val scaled = major.setScale(2, RoundingMode.HALF_UP)
            return Money(scaled.movePointRight(2).longValueExact())
        }

        /** Parse a decimal string like "12.50" into Money. */
        fun parse(value: String): Money {
            val trimmed = value.trim().removePrefix("$").trim()
            return of(BigDecimal(trimmed))
        }

        /** Construct from minor units (cents) directly. */
        fun ofMinor(minor: Long): Money = Money(minor)

        /** Safe constructor that returns null on invalid input. */
        fun safe(major: Double): Money? = runCatching { of(major) }.getOrNull()

        /** Allocate this money into [parts] buckets without losing pennies.
         *  E.g. $0.05 split into 3 = [0.02, 0.02, 0.01] (pennies distributed to first buckets). */
        fun allocate(total: Money, parts: Int): List<Money> {
            require(parts > 0) { "parts must be > 0" }
            if (parts == 1) return listOf(total)
            val base = total.minorUnits / parts
            val remainder = total.minorUnits - base * parts
            return (0 until parts).map { i ->
                Money(base + if (i < remainder) 1L else 0L)
            }
        }
    }
}

/**
 * Percentage value 0..100 stored as basis points (1% = 100 bp, 0.01% = 1 bp).
 *
 * CRITICAL: `Percent.of(8.25)` means "8.25 percent" — applying it to $100 must yield $8.25,
 * NOT $825. Internally `basisPoints = 825`, `asFraction = 0.0825`, `asDouble = 8.25`.
 *
 * When applying a percent to a Money amount, use [of] which multiplies by `asFraction`:
 *   Percent.of(8.25).of(Money.of(100.0)) == Money.of(8.25)
 *
 * Do NOT multiply Money directly by `asDouble` — that treats the percent as a 1x multiplier.
 */
@Serializable(with = PercentSerializer::class)
@JvmInline
value class Percent private constructor(val basisPoints: Int) {
    /** 0.0 .. 1.0 (e.g. 8.25% → 0.0825). Use this as the multiplier for money. */
    val asFraction: BigDecimal
        get() = BigDecimal.valueOf(basisPoints.toLong()).divide(BigDecimal(10_000), 10, RoundingMode.HALF_UP)

    /** 0.0 .. 100.0 (e.g. 8.25% → 8.25). Display only — never multiply money by this. */
    val asDouble: Double get() = basisPoints / 100.0

    /** Apply this percentage to a money amount, returning the computed amount (not the total).
     *  e.g. Percent.of(8.25).of($100) == $8.25
     *  e.g. Percent.of(15).of($50) == $7.50
     */
    fun of(money: Money): Money = money * asFraction

    operator fun plus(other: Percent): Percent = Percent((basisPoints + other.basisPoints).coerceIn(0, 1_000_000))
    operator fun minus(other: Percent): Percent = Percent((basisPoints - other.basisPoints).coerceIn(0, 1_000_000))

    companion object {
        val ZERO = Percent(0)
        val HUNDRED = Percent(10_000) // 100%

        /** Construct from a percentage value 0..100. e.g. Percent.of(8.25) = 8.25%. */
        fun of(value: Double): Percent = Percent((value * 100).toInt().coerceIn(0, 1_000_000))

        /** Construct from a percentage value 0..100 using BigDecimal for precision. */
        fun of(value: BigDecimal): Percent =
            Percent(value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt().coerceIn(0, 1_000_000))

        /** Construct from basis points (1% = 100 bp). */
        fun ofBasisPoints(bp: Int): Percent = Percent(bp.coerceIn(0, 1_000_000))

        /** Construct from a fraction 0..1. e.g. fromFraction(0.0825) = 8.25%. */
        fun fromFraction(fraction: Double): Percent = of(fraction * 100.0)
    }
}

/**
 * Quantity value type — supports fractional quantities (weighted items like produce,
 * deli meats, bulk foods) without floating-point error.
 *
 * Stored as a BigDecimal with scale up to 4 decimal places (0.0001 precision).
 */
@Serializable(with = QuantitySerializer::class)
@JvmInline
value class Quantity private constructor(val value: BigDecimal) : Comparable<Quantity> {
    val asInt: Int get() = value.setScale(0, RoundingMode.HALF_UP).toInt()
    val asDouble: Double get() = value.toDouble()
    val wholeUnits: Int get() = value.setScale(0, RoundingMode.DOWN).toInt()
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0
    val isPositive: Boolean get() = value > BigDecimal.ZERO
    val isNegative: Boolean get() = value < BigDecimal.ZERO
    val isWhole: Boolean get() = value.scale() <= 0 || value.stripTrailingZeros().scale() <= 0

    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)
    operator fun minus(other: Quantity): Quantity = Quantity(value - other.value)
    operator fun plus(delta: Int): Quantity = Quantity(value + BigDecimal(delta))
    operator fun minus(delta: Int): Quantity = Quantity(value - BigDecimal(delta))
    operator fun times(factor: Int): Quantity = Quantity(value * BigDecimal(factor))
    operator fun compareTo(other: Int): Int = value.compareTo(BigDecimal(other))

    override fun compareTo(other: Quantity): Int = value.compareTo(other.value)

    fun format(unit: String? = null): String {
        val displayValue = if (isWhole) value.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
            else value.stripTrailingZeros().toPlainString()
        return if (unit != null) "$displayValue $unit" else displayValue
    }

    companion object {
        val ZERO = Quantity(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
        val ONE = Quantity(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP))

        fun of(value: Int): Quantity = Quantity(BigDecimal(value).setScale(4, RoundingMode.HALF_UP))
        fun of(value: Double): Quantity = Quantity(BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP))
        fun of(value: BigDecimal): Quantity = Quantity(value.setScale(4, RoundingMode.HALF_UP))
        fun parse(value: String): Quantity = Quantity(BigDecimal(value.trim()).setScale(4, RoundingMode.HALF_UP))
    }
}
