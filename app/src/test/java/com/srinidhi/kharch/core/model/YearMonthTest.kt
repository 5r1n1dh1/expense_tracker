package com.srinidhi.kharch.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calendar arithmetic (plan §13.2: leap year 2028 Feb 29, month lengths, the 31 Dec / 1 Jan
 * year boundary).
 *
 * These are exactly the cases that produce off-by-one-day bugs in a date-range query, so each
 * is pinned on its own rather than only through an aggregation test.
 */
class YearMonthTest {
    @Test
    fun `february twenty twenty eight has twenty nine days`() {
        assertEquals(29, YearMonth(2028, 2).daysInMonth)
    }

    @Test
    fun `february twenty twenty seven has twenty eight days`() {
        assertEquals(28, YearMonth(2027, 2).daysInMonth)
    }

    @Test
    fun `two thousand is a leap year but nineteen hundred is not`() {
        assertTrue(YearMonth(2000, 2).isLeapYear)
        assertFalse(YearMonth(1900, 2).isLeapYear)
    }

    @Test
    fun `twenty thirty days long months report thirty days`() {
        val monthLengths = YearMonth.ofYear(2026).associate { it.month to it.daysInMonth }

        assertEquals(31, monthLengths[1])
        assertEquals(28, monthLengths[2])
        assertEquals(30, monthLengths[4])
        assertEquals(31, monthLengths[12])
    }

    @Test
    fun `a year always has exactly twelve months`() {
        assertEquals(12, YearMonth.ofYear(2026).size)
    }

    @Test
    fun `december plus one month rolls into january of the next year`() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).plusMonths(1))
    }

    @Test
    fun `january minus one month rolls into december of the previous year`() {
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).minusMonths(1))
    }

    @Test
    fun `adding twenty five months crosses two years and one month`() {
        assertEquals(YearMonth(2028, 3), YearMonth(2026, 2).plusMonths(25))
    }

    @Test
    fun `subtracting more months than the year holds stays negative-safe`() {
        assertEquals(YearMonth(2024, 11), YearMonth(2026, 2).plusMonths(-15))
    }

    @Test
    fun `thirty one december and the first of january land in different months`() {
        val december = YearMonth(2026, 12)
        val january = YearMonth(2027, 1)
        val lastDayOfDecember = LocalDate.of(2026, 12, 31).toEpochDay()
        val firstDayOfJanuary = LocalDate.of(2027, 1, 1).toEpochDay()

        assertEquals(YearMonth.fromEpochDay(lastDayOfDecember), december)
        assertEquals(YearMonth.fromEpochDay(firstDayOfJanuary), january)
        assertEquals(january.firstEpochDay, lastDayOfDecember + 1)
        assertFalse(december.contains(firstDayOfJanuary))
        assertTrue(january.contains(firstDayOfJanuary))
    }

    @Test
    fun `the epoch day range covers every day of the month exactly once`() {
        val februaryTwentyTwentyEight = YearMonth(2028, 2)

        assertEquals(29, februaryTwentyTwentyEight.epochDayRange.count())
        assertEquals(februaryTwentyTwentyEight.firstEpochDay, februaryTwentyTwentyEight.epochDayRange.first)
        assertEquals(februaryTwentyTwentyEight.lastEpochDay, februaryTwentyTwentyEight.epochDayRange.last)
        assertEquals(februaryTwentyTwentyEight.endEpochDayExclusive, februaryTwentyTwentyEight.lastEpochDay + 1)
    }

    @Test
    fun `consecutive months chain without an overlap`() {
        val january = YearMonth(2028, 1)
        val february = YearMonth(2028, 2)

        assertEquals(january.endEpochDayExclusive, february.firstEpochDay)
    }

    @Test
    fun `twenty nine february is a real date in a leap year`() {
        val date = YearMonth(2028, 2).atDay(29)

        assertEquals(LocalDate.of(2028, 2, 29), date)
    }

    @Test
    fun `a day outside the month is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { YearMonth(2027, 2).atDay(29) }
    }

    @Test
    fun `a month outside one to twelve is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { YearMonth(2026, 0) }
        assertThrows(IllegalArgumentException::class.java) { YearMonth(2026, 13) }
    }

    @Test
    fun `calendar ordering matches chronological ordering`() {
        val ordered = listOf(YearMonth(2027, 1), YearMonth(2026, 12), YearMonth(2026, 2))
            .sorted()

        assertEquals(listOf(YearMonth(2026, 2), YearMonth(2026, 12), YearMonth(2027, 1)), ordered)
    }

    @Test
    fun `the month index round trips`() {
        val months = YearMonth.ofYear(2026)

        months.forEach { month ->
            assertEquals(month, YearMonth.ofMonthIndex(month.monthIndex))
        }
    }

    @Test
    fun `toString renders an iso style year and month`() {
        assertEquals("2026-08", YearMonth(2026, 8).toString())
        assertEquals("2026-01", YearMonth(2026, 1).toString())
    }
}
