package com.enterprise.pos.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

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
        assertThat(result.minorUnits).isEqualTo(83L)
    }

    @Test
    fun `multiplication by fractional quantity preserves exactness`() {
        val price = Money.of(4.00)
        val qty = Quantity.of(1.25)
        assertThat((price * qty).minorUnits).isEqualTo(500L)
    }

    @Test
    fun `format produces localized currency string`() {
        val m = Money.of(12.50)
        val s = m.format(Currency.getInstance("USD"))
        assertThat(s).contains("12.50")
    }

    @Test
    fun `format with currency code fallback`() {
        val m = Money.of(12.50)
        val s = m.format("XXX")
        assertThat(s).isNotEmpty()
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
        val allocated = Money.allocate(Money.of(0.05), 3)
        assertThat(allocated).hasSize(3)
        assertThat(allocated.sumOf { it.minorUnits }).isEqualTo(5L)
        assertThat(allocated[0].minorUnits).isEqualTo(2L)
        assertThat(allocated[1].minorUnits).isEqualTo(2L)
        assertThat(allocated[2].minorUnits).isEqualTo(1L)
    }

    @Test
    fun `allocate single part returns total`() {
        val total = Money.of(100.0)
        val allocated = Money.allocate(total, 1)
        assertThat(allocated).hasSize(1)
        assertThat(allocated[0].minorUnits).isEqualTo(10000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `allocate rejects zero parts`() {
        Money.allocate(Money.of(1.0), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `allocate rejects negative parts`() {
        Money.allocate(Money.of(1.0), -1)
    }

    @Test
    fun `allocate large amounts preserves sum`() {
        val total = Money.of(999999.99)
        val allocated = Money.allocate(total, 100)
        assertThat(allocated.sumOf { it.minorUnits }).isEqualTo(total.minorUnits)
    }

    @Test
    fun `parse strips dollar sign`() {
        assertThat(Money.parse("$12.50").minorUnits).isEqualTo(1250L)
        assertThat(Money.parse("  $  12.50  ").minorUnits).isEqualTo(1250L)
    }

    @Test
    fun `parse handles plain decimal`() {
        assertThat(Money.parse("12.50").minorUnits).isEqualTo(1250L)
    }

    @Test
    fun `safe returns null on invalid input`() {
        assertThat(Money.safe(Double.NaN)).isNull()
        assertThat(Money.safe(Double.POSITIVE_INFINITY)).isNull()
    }

    @Test
    fun `safe returns value on valid input`() {
        assertThat(Money.safe(12.50)).isEqualTo(Money.of(12.50))
    }

    @Test
    fun `division by int rounds half-up`() {
        assertThat(Money.of(10.00).div(3).minorUnits).isEqualTo(333L)
        assertThat(Money.of(10.00).div(2).minorUnits).isEqualTo(500L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `division by int throws on zero`() {
        Money.of(10.00).div(0)
    }

    @Test
    fun `division by money returns ratio`() {
        val ratio = Money.of(10.00).div(Money.of(2.00))
        assertThat(ratio.setScale(2, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("5.00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `division by money throws on zero`() {
        Money.of(10.00).div(Money.ZERO)
    }

    @Test
    fun `comparison operators work`() {
        assertThat(Money.of(10.00) > Money.of(5.00)).isTrue()
        assertThat(Money.of(10.00) < Money.of(15.00)).isTrue()
        assertThat(Money.of(10.00) == Money.of(10.00)).isTrue()
    }

    @Test
    fun `compareTo returns correct sign`() {
        assertThat(Money.of(10.00).compareTo(Money.of(5.00))).isGreaterThan(0)
        assertThat(Money.of(5.00).compareTo(Money.of(10.00))).isLessThan(0)
        assertThat(Money.of(10.00).compareTo(Money.of(10.00))).isEqualTo(0)
    }

    @Test
    fun `of from BigDecimal handles precision correctly`() {
        val bd = BigDecimal("12.505")
        assertThat(Money.of(bd).minorUnits).isEqualTo(1251L)
    }

    @Test
    fun `multiplication by BigDecimal handles very small values`() {
        val price = Money.of(1.00)
        val result = price * BigDecimal("0.001")
        assertThat(result.minorUnits).isEqualTo(0L)
    }

    @Test
    fun `negative money operations`() {
        val negative = Money.of(-10.00)
        assertThat(negative.isNegative()).isTrue()
        assertThat(negative.atLeastZero().isZero()).isTrue()
    }

    @Test
    fun `overflow is not a concern with Long minor units`() {
        val large = Money.ofMinor(Long.MAX_VALUE)
        assertThat(large.isPositive()).isTrue()
    }

    @Test
    fun `deterministic rounding for tax edge cases`() {
        val price = Money.of(0.01)
        val tax = BigDecimal("0.0825")
        val result = price * tax
        assertThat(result.minorUnits).isEqualTo(0L)
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
        val tax = Percent.of(8.25)
        val result = tax.of(Money.of(100.0))
        assertThat(result.minorUnits).isEqualTo(825L)
    }

    @Test
    fun `GOLDEN TEST - 2_5 percent discount on 10 dollars equals 0_25 dollars`() {
        val discount = Percent.of(2.5)
        val result = discount.of(Money.of(10.0))
        assertThat(result.minorUnits).isEqualTo(25L)
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
        val p = Percent.fromFraction(0.0825)
        assertThat(p.basisPoints).isEqualTo(825)
        assertThat(p.of(Money.of(100.0)).minorUnits).isEqualTo(825L)
    }

    @Test
    fun `ofBasisPoints constructs correctly`() {
        val p = Percent.ofBasisPoints(825)
        assertThat(p.asDouble).isEqualTo(8.25)
    }

    @Test
    fun `of with BigDecimal precision`() {
        val p = Percent.of(BigDecimal("8.255"))
        assertThat(p.basisPoints).isEqualTo(826)
    }

    @Test
    fun `addition clamps to max`() {
        val a = Percent.of(60.0)
        val b = Percent.of(50.0)
        assertThat((a + b).asDouble).isEqualTo(100.0)
    }

    @Test
    fun `subtraction clamps to min`() {
        val a = Percent.of(5.0)
        val b = Percent.of(10.0)
        assertThat((a - b).asDouble).isEqualTo(0.0)
    }

    @Test
    fun `zero percent of anything is zero`() {
        assertThat(Percent.ZERO.of(Money.of(100.0)).isZero()).isTrue()
    }

    @Test
    fun `hundred percent returns same amount`() {
        assertThat(Percent.HUNDRED.of(Money.of(100.0))).isEqualTo(Money.of(100.0))
    }

    @Test
    fun `asFraction is correct`() {
        val p = Percent.of(8.25)
        assertThat(p.asFraction.setScale(4, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("0.0825"))
    }

    @Test
    fun `applying percent to zero returns zero`() {
        assertThat(Percent.of(50.0).of(Money.ZERO).isZero()).isTrue()
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

    @Test
    fun `format with unit`() {
        val q = Quantity.of(1.5)
        assertThat(q.format("lb")).isEqualTo("1.5 lb")
    }

    @Test
    fun `format without unit strips trailing zeros`() {
        val q = Quantity.of(2.0)
        assertThat(q.format()).isEqualTo("2")
    }

    @Test
    fun `parse from string`() {
        val q = Quantity.parse("1.25")
        assertThat(q.asDouble).isEqualTo(1.25)
    }

    @Test
    fun `zero quantity predicates`() {
        assertThat(Quantity.ZERO.isZero).isTrue()
        assertThat(Quantity.ZERO.isPositive).isFalse()
        assertThat(Quantity.ZERO.isNegative).isFalse()
    }

    @Test
    fun `negative quantity`() {
        val q = Quantity.parse("-1.5")
        assertThat(q.isNegative).isTrue()
    }

    @Test
    fun `whole unit detection for exact integers`() {
        assertThat(Quantity.of(10).isWhole).isTrue()
        assertThat(Quantity.of(10.0).isWhole).isTrue()
        assertThat(Quantity.of(10.1).isWhole).isFalse()
    }

    @Test
    fun `times by int`() {
        assertThat((Quantity.of(1.5) * 2).asDouble).isEqualTo(3.0)
    }

    @Test
    fun `asInt rounds half-up`() {
        assertThat(Quantity.of(1.4).asInt).isEqualTo(1)
        assertThat(Quantity.of(1.5).asInt).isEqualTo(2)
    }

    @Test
    fun `wholeUnits truncates down`() {
        assertThat(Quantity.of(1.9).wholeUnits).isEqualTo(1)
        assertThat(Quantity.of(2.1).wholeUnits).isEqualTo(2)
    }

    @Test
    fun `BigDecimal constructor preserves scale`() {
        val bd = BigDecimal("1.00005")
        val q = Quantity.of(bd)
        assertThat(q.asDouble).isEqualTo(1.0001)
    }
}
