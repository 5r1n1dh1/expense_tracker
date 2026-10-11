package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.domain.PAISE_PER_RUPEE
import com.srinidhi.kharch.domain.date
import com.srinidhi.kharch.domain.expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendar arithmetic in the yearly aggregate (§9, §13.2 "Domain").
 *
 * The dates here are the ones that break hand-written month maths: the year boundary, a leap
 * day, and a month nobody tracked anything in.
 */
class GetYearlyDashboardTest {

    private val clock = FakeClock()
    private val useCase = GetYearlyDashboard(clock, ZoneId.of("Asia/Kolkata"))

    @Test
    fun `an empty year still renders twelve bars`() {
        val dashboard = useCase.calculate(emptyList(), 2026, date(2026, 6, 15))

        assertEquals(MONTHS_IN_YEAR, dashboard.monthlyNet.size)
        assertEquals(0L, dashboard.netTotal.paise)
        assertEquals(0L, dashboard.grossSpend.paise)
        assertEquals(0L, dashboard.totalRefunds.paise)
        assertEquals(0, dashboard.entryCount)
        assertEquals(0, dashboard.dayCount)
        assertTrue(dashboard.isEmpty)
        assertFalse(dashboard.hasNegativeMonth)
    }

    @Test
    fun `an empty year still has a usable chart scale`() {
        val dashboard = useCase.calculate(emptyList(), 2026, date(2026, 6, 15))

        // A zero scale would make every bar an undefined division in the UI.
        assertTrue(dashboard.chartScalePaise >= 1L)
    }

    @Test
    fun `a single month sums into both the month and the year`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 3, 4), 500),
                expense("b", date(2026, 3, 9), 250),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(750 * PAISE_PER_RUPEE, dashboard.netForMonth(3).paise)
        assertEquals(750 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertEquals(2, dashboard.entryCount)
    }

    @Test
    fun `months with no entries read as zero rather than missing`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 1, 31), 100),
                expense("b", date(2026, 12, 1), 400),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(MONTHS_IN_YEAR, dashboard.monthlyNet.size)
        assertEquals(0L, dashboard.netForMonth(7).paise)
        assertEquals(100 * PAISE_PER_RUPEE, dashboard.netForMonth(1).paise)
        assertEquals(400 * PAISE_PER_RUPEE, dashboard.netForMonth(12).paise)
    }

    @Test
    fun `31 December and 1 January belong to different years`() {
        val expenses = listOf(
            expense("dec", date(2025, 12, 31), 700),
            expense("jan", date(2026, 1, 1), 300),
        )

        val previous = useCase.calculate(expenses, 2025, date(2026, 6, 15))
        val current = useCase.calculate(expenses, 2026, date(2026, 6, 15))

        assertEquals(700 * PAISE_PER_RUPEE, previous.netTotal.paise)
        assertEquals(300 * PAISE_PER_RUPEE, current.netTotal.paise)
        assertEquals(700 * PAISE_PER_RUPEE, previous.netForMonth(12).paise)
        assertEquals(300 * PAISE_PER_RUPEE, current.netForMonth(1).paise)
    }

    @Test
    fun `29 February of a leap year lands in February`() {
        val dashboard = useCase.calculate(
            listOf(expense("leap", date(2028, 2, 29), 123)),
            2028,
            date(2028, 3, 1),
        )

        assertEquals(123 * PAISE_PER_RUPEE, dashboard.netForMonth(2).paise)
        assertEquals(123 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
    }

    @Test
    fun `28 February is the last day of a non leap February`() {
        val dashboard = useCase.calculate(
            listOf(expense("last", date(2027, 2, 28), 90)),
            2027,
            date(2027, 3, 1),
        )

        assertEquals(90 * PAISE_PER_RUPEE, dashboard.netForMonth(2).paise)
        assertEquals(1, dashboard.dayCount)
    }

    @Test
    fun `day count tracks days with entries even when the day nets to zero`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("charge", date(2026, 3, 2), 900),
                expense("refund", date(2026, 3, 2), 900, kind = ExpenseKind.REFUND),
                expense("other", date(2026, 3, 16), 100),
            ),
            2026,
            date(2026, 6, 15),
        )

        // 3/2 nets to zero (900 charge fully refunded) but still counts as a tracked day,
        // so dayCount is 2 even though only one day carries money. The 'other' row puts
        // 100 rupees into the year total.
        assertEquals(100 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertEquals(2, dashboard.dayCount)
        assertEquals(3, dashboard.entryCount)
    }

    @Test
    fun `average per active day divides the net by the tracked days`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("a", date(2026, 3, 2), 1_000),
                expense("b", date(2026, 3, 9), 1_000),
                expense("c", date(2026, 3, 16), 1_000),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(3, dashboard.dayCount)
        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.avgPerActiveDay.paise)
        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.maxDay.paise)
    }

    @Test
    fun `an empty year averages to zero rather than dividing by zero`() {
        val dashboard = useCase.calculate(emptyList(), 2026, date(2026, 6, 15))

        assertEquals(0, dashboard.dayCount)
        assertEquals(0L, dashboard.avgPerActiveDay.paise)
        assertEquals(0L, dashboard.maxDay.paise)
    }

    @Test
    fun `tombstones are excluded from every figure`() {
        val dashboard = useCase.calculate(
            listOf(
                expense("live", date(2026, 7, 4), 300),
                expense("gone", date(2026, 7, 5), 9_999, deletedAt = 1L),
                expense("gone-too", date(2026, 7, 6), 9_999, deletedAt = 2L, kind = ExpenseKind.REFUND),
            ),
            2026,
            date(2026, 6, 15),
        )

        assertEquals(300 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertEquals(1, dashboard.entryCount)
        assertEquals(1, dashboard.dayCount)
        assertEquals(1, dashboard.categoryNet.size)
    }

    @Test
    fun `the use case reads today from the injected clock`() {
        clock.setTo(LocalDate.of(2026, 4, 20))

        val dashboard = useCase(listOf(expense("a", date(2026, 2, 1), 1_000)), 2026)

        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.currentPeriodNet.paise)
        assertEquals(0L, dashboard.previousYearSamePeriodNet.paise)
    }

    @Test
    fun `the stream aggregates the ledger it is given`() = runTest {
        clock.setTo(LocalDate.of(2026, 6, 1))
        val ledger = MutableStateFlow<List<Expense>>(listOf(expense("a", date(2026, 2, 1), 1_000)))

        val dashboard = useCase.stream(ledger, 2026).first()

        assertEquals(1_000 * PAISE_PER_RUPEE, dashboard.netTotal.paise)
        assertEquals(1, dashboard.entryCount)
    }
}
