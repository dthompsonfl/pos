package com.enterprise.pos.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class MoneyTest {

    @Test
    fun `of_major stores correctly as minor units`() {
        assertThat(Money.of(12.50).minorUnits).isEqualTo(1250L)
        assertThat(Money.of(0.01).minorUnits).isEqualTo(1L)
        assertThat(Money.of(0.0).minorUnits).isEqualTo(0L)
    }

    @Test
    fun `of_minor round-trips`() {
        val m = Money.ofMinor(9999)
        assertThat(m.asBigDecimal).isEqualTo(BigDecimal("99.99"))
    }

    @Test
    fun `addition and subtraction are exact`() {
        val a = Money.of(10.00)
        val b = Money.of(2.50)
        assertThat((a + b).minorUnits).isEqualTo(1250L)
        assertThat((a - b).minorUnits).isEqualTo(750L)
    }

    @Test
    fun `multiplication by int scales correctly`() {
        val price = Money.of(7.50)
        assertThat((price * 3).minorUnits).isEqualTo(2250L)
    }

    @Test
    fun `multiplication by BigDecimal rounds half-up`() {
        val price = Money.of(10.00)
        val tax = BigDecimal("0.0825")
        val result = price * tax
        // 10.00 * 0.0825 = 0.825 → 0.83 (HALF_UP at 2 decimals)
        assertThat(result.minorUnits).isEqualTo(83L)
    }

    @Test
    fun `multiplication by fractional quantity preserves exactness`() {
        // 1.25 lb × $4.00/lb = $5.00
        val price = Money.of(4.00)
        val qty = Quantity.of(1.25)
        assertThat((price * qty).minorUnits).isEqualTo(500L)
    }

    @Test
    fun `format produces localized currency string`() {
        val m = Money.of(12.50)
        val s = m.format(java.util.Currency.getInstance("USD"))
        assertThat(s).contains("12.50")
    }

    @Test
    fun `zero predicates`() {
        assertThat(Money.ZERO.isZero()).isTrue()
        assertThat(Money.of(1.0).isPositive()).isTrue()
        assertThat(Money.of(-1.0).isNegative()).isTrue()
    }

    @Test
    fun `atLeastZero clamps negative to zero`() {
        assertThat(Money.ofMinor(-100L).atLeastZero().minorUnits).isEqualTo(0L)
        assertThat(Money.ofMinor(100L).atLeastZero().minorUnits).isEqualTo(100L)
    }

    @Test
    fun `cappedAt limits to maximum`() {
        assertThat(Money.of(150.0).cappedAt(Money.of(100.0)).minorUnits).isEqualTo(10000L)
        assertThat(Money.of(50.0).cappedAt(Money.of(100.0)).minorUnits).isEqualTo(5000L)
    }

    @Test
    fun `allocate distributes pennies without loss`() {
        // $0.05 into 3 buckets = [0.02, 0.02, 0.01]
        val allocated = Money.allocate(Money.of(0.05), 3)
        assertThat(allocated).hasSize(3)
        assertThat(allocated.sumOf { it.minorUnits }).isEqualTo(5L) // no penny lost
        assertThat(allocated[0].minorUnits).isEqualTo(2L)
        assertThat(allocated[1].minorUnits).isEqualTo(2L)
        assertThat(allocated[2].minorUnits).isEqualTo(1L)
    }
}

class PercentTest {

    @Test
    fun `Percent of 8_25 represents 8_25 percent`() {
        val p = Percent.of(8.25)
        assertThat(p.basisPoints).isEqualTo(825)
        assertThat(p.asDouble).isEqualTo(8.25)
    }

    @Test
    fun `GOLDEN TEST - 8_25 percent of 100 dollars equals 8_25 dollars`() {
        // CRITICAL: This is the financial correctness test that v1 failed.
        val tax = Percent.of(8.25)
        val result = tax.of(Money.of(100.0))
        assertThat(result.minorUnits).isEqualTo(825L) // $8.25 = 825 cents
    }

    @Test
    fun `GOLDEN TEST - 2_5 percent discount on 10 dollars equals 0_25 dollars`() {
        val discount = Percent.of(2.5)
        val result = discount.of(Money.of(10.0))
        assertThat(result.minorUnits).isEqualTo(25L) // $0.25 = 25 cents
    }

    @Test
    fun `GOLDEN TEST - 15 percent tip on 50 dollars equals 7_50 dollars`() {
        val tip = Percent.of(15.0)
        val result = tip.of(Money.of(50.0))
        assertThat(result.minorUnits).isEqualTo(750L)
    }

    @Test
    fun `percent clamps to valid range`() {
        assertThat(Percent.of(150.0).asDouble).isAtMost(100.0)
        assertThat(Percent.of(-5.0).asDouble).isAtLeast(0.0)
    }

    @Test
    fun `fromFraction works correctly`() {
        val p = Percent.fromFraction(0.0825) // 8.25%
        assertThat(p.basisPoints).isEqualTo(825)
        assertThat(p.of(Money.of(100.0)).minorUnits).isEqualTo(825L)
    }
}

class QuantityTest {

    @Test
    fun `integer quantity preserves whole number`() {
        val q = Quantity.of(3)
        assertThat(q.asInt).isEqualTo(3)
        assertThat(q.isWhole).isTrue()
    }

    @Test
    fun `fractional quantity preserves decimals`() {
        val q = Quantity.of(1.25)
        assertThat(q.asDouble).isEqualTo(1.25)
        assertThat(q.isWhole).isFalse()
    }

    @Test
    fun `quantity arithmetic is exact`() {
        val a = Quantity.of(1.5)
        val b = Quantity.of(2.25)
        assertThat((a + b).asDouble).isEqualTo(3.75)
        assertThat((b - a).asDouble).isEqualTo(0.75)
    }

    @Test
    fun `quantity plus and minus int operators work`() {
        val q = Quantity.of(2)
        assertThat((q + 1).asInt).isEqualTo(3)
        assertThat((q - 1).asInt).isEqualTo(1)
    }

    @Test
    fun `quantity comparison works`() {
        assertThat(Quantity.of(2) > Quantity.of(1)).isTrue()
        assertThat(Quantity.of(2) > 1).isTrue()
        assertThat(Quantity.of(1) > 2).isFalse()
    }
}
