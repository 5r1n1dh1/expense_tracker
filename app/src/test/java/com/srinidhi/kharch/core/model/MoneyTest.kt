package com.srinidhi.kharch.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money model (D5). These are the rules the whole app's arithmetic rests on, so they are
 * pinned individually rather than only through the aggregation tests.
 */
class MoneyTest {
    @Test
    fun `an expense of six hundred rupees is stored as a positive amount`() {
        val money = Money.of(MagnitudePaise(640_00), ExpenseKind.EXPENSE)

        assertEquals(640_00L, money.paise)
        assertFalse(money.isCredit)
    }

    @Test
    fun `a refund of six hundred rupees is stored as a negative amount`() {
        val money = Money.of(MagnitudePaise(640_00), ExpenseKind.REFUND)

        assertEquals(-640_00L, money.paise)
        assertTrue(money.isCredit)
    }

    @Test
    fun `isCredit always agrees with the kind it was built from`() {
        val magnitude = MagnitudePaise(1L)

        assertFalse(Money.of(magnitude, ExpenseKind.EXPENSE).isCredit)
        assertTrue(Money.of(magnitude, ExpenseKind.REFUND).isCredit)
    }

    @Test
    fun `magnitude drops the sign and keeps the rupees`() {
        val money = Money.of(MagnitudePaise(12_345_67), ExpenseKind.REFUND)

        assertEquals(MagnitudePaise(12_345_67), money.magnitude)
    }

    @Test
    fun `zero rupees is neither a charge nor a refund`() {
        val money = Money.of(MagnitudePaise(0L), ExpenseKind.EXPENSE)

        assertEquals(0L, money.paise)
        assertFalse(money.isCredit)
        assertEquals(MagnitudePaise(0L), money.magnitude)
    }

    @Test
    fun `a refund of a refund nets out to the right sign`() {
        val original = Money.of(MagnitudePaise(700_00), ExpenseKind.REFUND)
        val reversal = Money.of(original.magnitude, ExpenseKind.EXPENSE)

        assertEquals(0L, original.paise + reversal.paise)
        assertFalse(reversal.isCredit)
    }

    @Test
    fun `five hundred spent against seven hundred refunded is a net credit of two hundred`() {
        val charge = Money.of(MagnitudePaise(500_00), ExpenseKind.EXPENSE)
        val refund = Money.of(MagnitudePaise(700_00), ExpenseKind.REFUND)

        assertEquals(-200_00L, charge.paise + refund.paise)
    }

    @Test
    fun `a negative magnitude is rejected outright`() {
        assertThrows(IllegalArgumentException::class.java) { MagnitudePaise(-1L) }
    }

    @Test
    fun `converting paise to rupees is exact`() {
        assertEquals("1234.56", MagnitudePaise(123_456L).toRupees().toPlainString())
    }

    @Test
    fun `converting zero paise yields zero rupees`() {
        assertEquals("0.00", MagnitudePaise(0L).toRupees().toPlainString())
    }

    @Test
    fun `a large amount keeps every digit`() {
        assertEquals("92233720368547758.07", MagnitudePaise(Long.MAX_VALUE).toRupees().toPlainString())
    }
}
