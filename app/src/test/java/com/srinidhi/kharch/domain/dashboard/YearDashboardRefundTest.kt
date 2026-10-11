package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Expense
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

/**
 * The refund rules of D5, asserted end to end through the yearly aggregate (§13.2).
 *
 * A month can be net negative. Everything here exists because that case is the one most
 * likely to be quietly clamped, rescaled or double-negated somewhere between the database and
 * the bar chart.
 */
class YearDashboardRefundTest {

    private val useCase = GetYearlyDashboard(FakeClock(), ZoneId.of("Asia/Kolkata"))

    @Test
    fun `a month with 500 spent and 700 refunded is net negative`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("charge", date(2026, 5, 2), 500),
                expense("refund", date(2026, 5, 20), 700, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(-200 * PAISE_PER_RUPEE, dashboard.netForMonth(5).paise)
        assertEquals(-200 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertTrue(dashboard.netForMonth(5).isCredit)
        assertTrue(dashboard.hasNegativeMonth)
    }

    @Test
    fun `a month that refunds itself to zero is neither positive nor negative`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("charge", date(2026, 5, 2), 700),
                expense("refund", date(2026, 5, 20), 700, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(0L, dashboard.netForMonth(5).paise)
        assertFalse(dashboard.netForMonth(5).isCredit)
        assertFalse(dashboard.hasNegativeMonth)
    }

    @Test
    fun `the year total is the sum of the twelve monthly nets with negatives included`() {
        val dashboard = useCase.calculate(fullYearLedger(), 2026, date(2026, 12, 31))

        val monthlySum = dashboard.monthlyNet.sumOf { money -> money.paise }
        assertEquals(monthlySum, dashboard.netTotal.paise)
        assertEquals(dashboard.grossSpend.paise + dashboard.totalRefunds.paise, dashboard.netTotal.paise)
        // Ten charges total 19,900 and two refunds total 3,700, so the year nets 16,200.
        assertEquals(19_900 * PAISE_PER_RUPEE, dashboard.grossSpend.paise)
        assertEquals(-3_700 * PAISE_PER_RUPEE, dashboard.totalRefunds.paise)
        assertEquals(16_200 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
    }

    @Test
    fun `a net negative month does not rescale the shared chart baseline`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("small", date(2026, 1, 5), 1_000),
                expense("huge-refund", date(2026, 6, 5), 4_000, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        // One scale for the whole year: the largest absolute month, not the largest positive one.
        assertEquals(4_000 * PAISE_PER_RUPEE, dashboard.chartScalePaise)
        assertEquals(250, dashboard.barPermille(1))
        assertEquals(-1_000, dashboard.barPermille(6))
    }

    @Test
    fun `the chart scale is the largest absolute monthly net`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 2, 1), 3_000),
                expense("b", date(2026, 8, 1), 7_500),
                expense("c", date(2026, 9, 1), 9_000, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 12, 31),
        )

        assertEquals(9_000 * PAISE_PER_RUPEE, dashboard.chartScalePaise)
        assertEquals(333, dashboard.barPermille(2))
        assertEquals(833, dashboard.barPermille(8))
        assertEquals(-1_000, dashboard.barPermille(9))
    }

    @Test
    fun `gross spend and refunds are reported as separate figures`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 4, 2), 2_000),
                expense("b", date(2026, 4, 3), 750),
                expense("c", date(2026, 4, 4), 250, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(2_750 * PAISE_PER_RUPEE, dashboard.grossSpend.paise)
        assertEquals(-250 * PAISE_PER_RUPEE, dashboard.totalRefunds.paise)
        assertEquals(2_500 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertFalse(dashboard.grossSpend.isCredit)
        assertTrue(dashboard.totalRefunds.isCredit)
    }

    @Test
    fun `category nets use the same sign rule as the month totals`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 4, 2), 1_000, categoryId = "sys_food"),
                expense("b", date(2026, 4, 3), 400, categoryId = "sys_travel"),
                expense("c", date(2026, 4, 4), 1_600, categoryId = "sys_travel", kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.categoryNet.getValue("sys_food").paise)
        assertEquals(-1_200 * PAISE_PER_RUPEE, dashboard.categoryNet.getValue("sys_travel").paise)
        assertEquals(dashboard.netTotal.paise, dashboard.categoryNet.values.sumOf { money -> money.paise })
    }

    @Test
    fun `year over year compares the same period of both years`() {
        val expenses = listOf(
            expense("prev-a", date(2025, 2, 10), 1_000),
            expense("prev-b", date(2025, 6, 10), 5_000),
            expense("this-a", date(2026, 2, 10), 2_000),
            expense("this-b", date(2026, 6, 10), 5_000),
        )

        // Living through 10 March 2026: this year has contributed 2 000, last year had 1 000.
        val dashboard = useCase.calculate(expenses, 2026, date(2026, 3, 10))

        assertEquals(7_000 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertEquals(2_000 * PAISE_PER_RUPEE, dashboard.currentPeriodNet.paise)
        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.previousYearSamePeriodNet.paise)
        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.yoyDelta.paise)
    }

    @Test
    fun `a leap day in the current year compares against 28 February`() {
        val expenses = listOf(
            expense("prev", date(2027, 2, 28), 400),
            expense("this", date(2028, 2, 29), 900),
        )

        val dashboard = useCase.calculate(expenses, 2028, date(2028, 2, 29))

        assertEquals(400 * PAISE_PER_RUPEE, dashboard.previousYearSamePeriodNet.paise)
        assertEquals(900 * PAISE_PER_RUPEE, dashboard.currentPeriodNet.paise)
        assertEquals(500 * PAISE_PER_RUPEE, dashboard.yoyDelta.paise)
    }

    @Test
    fun `viewing a finished year compares whole years`() {
        val expenses = listOf(
            expense("prev-a", date(2024, 1, 5), 1_000),
            expense("prev-b", date(2024, 12, 31), 2_000),
            expense("this", date(2025, 3, 5), 4_000),
        )

        val dashboard = useCase.calculate(expenses, 2025, date(2026, 6, 15))

        assertEquals(4_000 * PAISE_PER_RUPEE, dashboard.currentPeriodNet.paise)
        assertEquals(3_000 * PAISE_PER_RUPEE, dashboard.previousYearSamePeriodNet.paise)
        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.yoyDelta.paise)
    }

    /** Twelve months of traffic with a negative November, to prove sums do not drift. */
    private fun fullYearLedger(): List<Expense> = listOf(
        expense("a", date(2026, 1, 5), 1_000),
        expense("b", date(2026, 2, 5), 2_500),
        expense("c", date(2026, 3, 5), 900),
        expense("d", date(2026, 4, 5), 1_800),
        expense("e", date(2026, 5, 5), 500),
        expense("f", date(2026, 5, 6), 700, kind = ExpenseKind.REFUND),
        expense("g", date(2026, 6, 5), 2_200),
        expense("h", date(2026, 7, 5), 4_400),
        expense("i", date(2026, 8, 5), 1_100),
        expense("j", date(2026, 9, 5), 3_300),
        expense("k", date(2026, 10, 5), 2_200),
        expense("l", date(2026, 11, 9), 3_000, kind = ExpenseKind.REFUND),
    )
}
