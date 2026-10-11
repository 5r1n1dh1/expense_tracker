package com.srinidhi.kharch.core.money

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Indian digit grouping is a first-class requirement (plan §5.1, §13.2), not a nicety:
 * `₹1,23,456.78`, never `₹123,456.78`.
 */
class InrFormatterTest {
    @Test
    fun `zero renders as a bare rupee sign with no decimals`() {
        assertEquals("₹0", InrFormatter.Default.format(0L))
    }

    @Test
    fun `nine rupees ninety nine renders with two decimals`() {
        assertEquals("₹9.99", InrFormatter.Default.format(999L))
    }

    @Test
    fun `one lakh groups as one then two digits`() {
        assertEquals("₹1,00,000", InrFormatter.Default.format(10_000_000L))
    }

    @Test
    fun `one lakh twenty three thousand four hundred fifty six groups the indian way`() {
        assertEquals("₹1,23,456.78", InrFormatter.Default.format(12_345_678L))
    }

    @Test
    fun `one crore twenty three lakh forty five thousand six hundred seventy eight groups the indian way`() {
        assertEquals("₹12,34,567.89", InrFormatter.Default.format(123_456_789L))
    }

    @Test
    fun `a whole rupee amount omits the decimal part`() {
        assertEquals("₹640", InrFormatter.Default.format(640_00L))
    }

    @Test
    fun `three digit amounts are never grouped`() {
        assertEquals("₹999", InrFormatter.Default.format(99_900L))
    }

    @Test
    fun `four digit amounts get a single separator`() {
        assertEquals("₹1,000", InrFormatter.Default.format(100_000L))
    }

    @Test
    fun `a single paise keeps its leading zero`() {
        assertEquals("₹0.01", InrFormatter.Default.format(1L))
    }

    @Test
    fun `nine paise keeps its leading zero`() {
        assertEquals("₹0.09", InrFormatter.Default.format(9L))
    }

    @Test
    fun `a negative amount puts the minus sign before the rupee sign`() {
        assertEquals("-₹640", InrFormatter.Default.format(-640_00L))
    }

    @Test
    fun `a negative fractional amount puts the minus sign before the rupee sign`() {
        assertEquals("-₹1,23,456.78", InrFormatter.Default.format(-12_345_678L))
    }

    @Test
    fun `forced decimals always render two digits`() {
        assertEquals("₹640.00", InrFormatter.Default.formatWithDecimals(640_00L))
        assertEquals("-₹640.00", InrFormatter.Default.formatWithDecimals(-640_00L))
    }

    @Test
    fun `plain output drops the symbol but keeps the sign`() {
        assertEquals("1,23,456.78", InrFormatter.Default.formatPlain(12_345_678L))
        assertEquals("-1,23,456.78", InrFormatter.Default.formatPlain(-12_345_678L))
    }

    @Test
    fun `a formatter configured for always decimals overrides the whole rupee shortcut`() {
        val ledger = InrFormatter(alwaysShowDecimals = true)

        assertEquals("₹640.00", ledger.format(640_00L))
        assertEquals("₹0.00", ledger.format(0L))
        assertEquals("-₹640.00", ledger.format(-640_00L))
    }

    @Test
    fun `the money and magnitude overloads agree with the raw paise overload`() {
        val magnitude = MagnitudePaise(12_345_678L)
        val money = Money.of(magnitude, ExpenseKind.REFUND)

        assertEquals("₹1,23,456.78", InrFormatter.Default.format(magnitude))
        assertEquals("-₹1,23,456.78", InrFormatter.Default.format(money))
        assertEquals(InrFormatter.Default.format(-12_345_678L), InrFormatter.Default.format(money))
    }

    @Test
    fun `grouping never depends on the default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("₹1,23,456.78", InrFormatter.Default.format(12_345_678L))
            Locale.setDefault(Locale.US)
            assertEquals("₹1,23,456.78", InrFormatter.Default.format(12_345_678L))
            Locale.setDefault(Locale.JAPAN)
            assertEquals("₹1,23,456.78", InrFormatter.Default.format(12_345_678L))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `a very large amount groups without losing a digit`() {
        val paise = MagnitudePaise(Long.MAX_VALUE).toRupees().toPlainString()

        assertEquals("₹92,23,37,20,36,85,47,758.07", InrFormatter.Default.format(Long.MAX_VALUE))
        assertEquals("9223372036854775807", paise.replace(".", ""))
    }

    @Test
    fun `grouping is applied to plain digit runs`() {
        assertEquals("1", InrFormatter.groupIndianDigits("1"))
        assertEquals("999", InrFormatter.groupIndianDigits("999"))
        assertEquals("1,000", InrFormatter.groupIndianDigits("1000"))
        assertEquals("10,000", InrFormatter.groupIndianDigits("10000"))
        assertEquals("1,00,000", InrFormatter.groupIndianDigits("100000"))
        assertEquals("12,34,567", InrFormatter.groupIndianDigits("1234567"))
        assertEquals("1,23,45,67,890", InrFormatter.groupIndianDigits("1234567890"))
    }
}
