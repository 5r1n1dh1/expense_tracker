package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.domain.PAISE_PER_RUPEE
import com.srinidhi.kharch.domain.date
import com.srinidhi.kharch.domain.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The drill-down sheet's month detail (§9). */
class GetMonthlyBreakdownTest {

    private val useCase = GetMonthlyBreakdown()

    @Test
    fun `an empty month returns a zero breakdown with one slot per day`() {
        val breakdown = useCase(emptyList(), 2026, 4)

        assertTrue(breakdown.isEmpty)
        assertEquals(0L, breakdown.netTotal.paise)
        assertEquals(0, breakdown.entryCount)
        assertEquals(30, breakdown.dailyNet.size)
        assertTrue(breakdown.dailyNet.all { money -> money.paise == 0L })
    }

    @Test
    fun `February of a leap year has twenty nine daily slots`() {
        val leap = useCase(listOf(expense("a", date(2028, 2, 29), 100)), 2028, 2)
        val common = useCase(listOf(expense("b", date(2027, 2, 28), 100)), 2027, 2)

        assertEquals(29, leap.dailyNet.size)
        assertEquals(28, common.dailyNet.size)
        assertEquals(100 * PAISE_PER_RUPEE, leap.dailyNet.last().paise)
        assertEquals(100 * PAISE_PER_RUPEE, common.dailyNet.last().paise)
    }

    @Test
    fun `the month splits gross spend from refunds`() {
        val breakdown = useCase(
            listOf(
                expense("a", date(2026, 4, 2), 2_000),
                expense("b", date(2026, 4, 3), 1_000, kind = ExpenseKind.REFUND),
            ),
            2026,
            4,
        )

        assertEquals(2_000 * PAISE_PER_RUPEE, breakdown.grossSpend.paise)
        assertEquals(-1_000 * PAISE_PER_RUPEE, breakdown.totalRefunds.paise)
        assertEquals(1_000 * PAISE_PER_RUPEE, breakdown.netTotal.paise)
        assertFalse(breakdown.isNetNegative)
    }

    @Test
    fun `a month with more refunded than spent reports a negative net`() {
        val breakdown = useCase(
            listOf(
                expense("a", date(2026, 4, 2), 500),
                expense("b", date(2026, 4, 3), 700, kind = ExpenseKind.REFUND),
            ),
            2026,
            4,
        )

        assertEquals(-200 * PAISE_PER_RUPEE, breakdown.netTotal.paise)
        assertTrue(breakdown.isNetNegative)
    }

    @Test
    fun `category totals are ordered by net descending with a stable tiebreak`() {
        val breakdown = useCase(
            listOf(
                expense("a", date(2026, 4, 2), 300, categoryId = "sys_bills"),
                expense("b", date(2026, 4, 3), 300, categoryId = "sys_food"),
                expense("c", date(2026, 4, 4), 100, categoryId = "sys_rent"),
                expense("d", date(2026, 4, 5), 900, categoryId = "sys_rent", kind = ExpenseKind.REFUND),
            ),
            2026,
            4,
        )

        val ordered = breakdown.categoryTotals.map { total -> total.categoryId }
        assertEquals(listOf("sys_bills", "sys_food", "sys_rent"), ordered)
        assertEquals(-800 * PAISE_PER_RUPEE, breakdown.categoryTotals.last().net.paise)
        assertEquals(2, breakdown.categoryTotals.last().entryCount)
        assertEquals(breakdown.netTotal.paise, breakdown.categoryTotals.sumOf { total -> total.net.paise })
    }

    @Test
    fun `top categories are the leading slice of the ordered totals`() {
        val breakdown = useCase(
            listOf(
                expense("a", date(2026, 4, 2), 100, categoryId = "sys_a"),
                expense("b", date(2026, 4, 3), 200, categoryId = "sys_b"),
                expense("c", date(2026, 4, 4), 300, categoryId = "sys_c"),
            ),
            2026,
            4,
        )

        assertEquals(3, breakdown.categoryTotals.size)
        assertEquals(2, breakdown.topCategories(2).size)
        assertEquals(listOf("sys_c", "sys_b"), breakdown.topCategories(2).map { total -> total.categoryId })
        assertEquals(3, breakdown.topCategories().size)
    }

    @Test
    fun `the refunds recorded that month come back oldest first`() {
        val breakdown = useCase(
            listOf(
                expense("late", date(2026, 4, 20), 100, kind = ExpenseKind.REFUND),
                expense("early", date(2026, 4, 2), 200, kind = ExpenseKind.REFUND),
                expense("charge", date(2026, 4, 3), 400),
                expense("elsewhere", date(2026, 5, 3), 999, kind = ExpenseKind.REFUND),
            ),
            2026,
            4,
        )

        assertEquals(listOf("early", "late"), breakdown.refunds.map { row -> row.id })
        assertEquals(3, breakdown.entryCount)
    }

    @Test
    fun `rows from other months and tombstones stay out of the breakdown`() {
        val breakdown = useCase(
            listOf(
                expense("here", date(2026, 4, 2), 100),
                expense("other-month", date(2026, 5, 2), 5_000),
                expense("other-year", date(2025, 4, 2), 5_000),
                expense("deleted", date(2026, 4, 3), 7_000, deletedAt = 1L),
            ),
            2026,
            4,
        )

        assertEquals(100 * PAISE_PER_RUPEE, breakdown.netTotal.paise)
        assertEquals(1, breakdown.entryCount)
        assertEquals(1, breakdown.dayCount)
    }

    @Test
    fun `the heaviest day is the tracked day with the largest net`() {
        val breakdown = useCase(
            listOf(
                expense("a", date(2026, 4, 2), 500),
                expense("b", date(2026, 4, 9), 1_500),
                expense("c", date(2026, 4, 16), 100),
            ),
            2026,
            4,
        )

        assertEquals(1_500 * PAISE_PER_RUPEE, breakdown.maxDay.paise)
        assertEquals(1_500 * PAISE_PER_RUPEE, breakdown.dailyNet[8].paise)
        assertEquals(3, breakdown.dayCount)
    }

    @Test
    fun `the monthly net agrees with the yearly net for the same rows`() {
        val rows = listOf(
            expense("a", date(2026, 4, 2), 900),
            expense("b", date(2026, 4, 9), 400, kind = ExpenseKind.REFUND),
            expense("c", date(2026, 9, 2), 5_000),
        )

        val month = useCase(rows, 2026, 4)
        val year = GetYearlyDashboard(FakeClock(), ZoneId.of("Asia/Kolkata"))
            .calculate(rows, 2026, date(2026, 12, 31))

        // The month is one slice of the year, so compare against that month's net and the
        // year's per-month category view -- never against the year's own gross/refunds,
        // which by definition cover every month, not just April.
        assertEquals(year.netForMonth(4).paise, month.netTotal.paise)
        assertEquals(900 * PAISE_PER_RUPEE, month.grossSpend.paise)
        assertEquals(-400 * PAISE_PER_RUPEE, month.totalRefunds.paise)
        assertEquals(500 * PAISE_PER_RUPEE, month.netTotal.paise)
        // The year's own totals must still include April AND September.
        assertEquals(5_900 * PAISE_PER_RUPEE, year.grossSpend.paise)
        assertEquals(-400 * PAISE_PER_RUPEE, year.totalRefunds.paise)
    }
}
