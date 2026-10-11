package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.domain.PaiseMath
import com.srinidhi.kharch.domain.todayIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregates one calendar year of the shared ledger into a [YearDashboard] (§9).
 *
 * There is exactly one ledger (D1), so no query and no aggregate is ever scoped to an
 * account. The class is pure: it reads a plain `List<Expense>` and an injected [Clock], and
 * it touches no Room, no Firebase and no Android type.
 *
 * @param clock injected so "today" — which decides the year-over-year comparison window — is
 *   a test input rather than a machine read (§13.2).
 * @param zone the calendar zone the user's "today" is expressed in.
 */
class GetYearlyDashboard(
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Aggregates [expenses] for [year] using the injected clock for the current date. */
    operator fun invoke(expenses: List<Expense>, year: Int): YearDashboard =
        calculate(expenses, year, clock.todayIn(zone))

    /**
     * The same aggregation with [today] supplied explicitly — the seam every test uses.
     *
     * A single pass over [expenses] fills twelve monthly totals, one signed total per
     * category, one net per calendar day, the gross/refund split and both year-over-year
     * windows. Tombstoned rows (`deletedAt != null`) are skipped everywhere: a delete made on
     * another device must vanish from the dashboard the moment it lands (§6.4).
     */
    fun calculate(expenses: List<Expense>, year: Int, today: LocalDate): YearDashboard {
        val monthly = LongArray(MONTHS_IN_YEAR)
        val daily = LongArray(DAYS_IN_MAX_YEAR)
        val dailyTracked = BooleanArray(DAYS_IN_MAX_YEAR)
        val categoryPaise = LinkedHashMap<String, Long>()
        var grossPaise = 0L
        var refundPaise = 0L
        var entryCount = 0
        var periodPaise = 0L
        var previousPeriodPaise = 0L

        // Comparing a part-year against a whole year is the classic year-over-year lie, so
        // the window is only clipped when the requested year is the one being lived through.
        val previousYear = year - 1
        val isCurrentYear = year == today.year
        val periodCutoff = if (isCurrentYear) today.toEpochDay() else Long.MAX_VALUE
        val previousCutoff = if (isCurrentYear) samePeriodEpochDay(previousYear, today) else Long.MAX_VALUE

        for (expense in expenses) {
            if (expense.deletedAt != null) continue
            val date = LocalDate.ofEpochDay(expense.dateEpochDay)
            // `Expense.money` is the model's own sign rule: +amount for a charge, −amount for a
            // refund (D5). Aggregating through it means the dashboard can never disagree with
            // the row list about which way a number points.
            val signed = expense.money.paise
            when (date.year) {
                year -> {
                    entryCount++
                    monthly[date.monthValue - 1] += signed
                    val dayIndex = date.dayOfYear - 1
                    daily[dayIndex] += signed
                    dailyTracked[dayIndex] = true
                    categoryPaise.merge(expense.categoryId, signed) { left, right -> left + right }
                    if (expense.kind == ExpenseKind.REFUND) refundPaise += signed else grossPaise += signed
                    if (expense.dateEpochDay <= periodCutoff) periodPaise += signed
                }
                previousYear -> if (expense.dateEpochDay <= previousCutoff) previousPeriodPaise += signed
                else -> Unit
            }
        }

        val dayCount = dailyTracked.count { tracked -> tracked }
        val netPaise = grossPaise + refundPaise
        return YearDashboard(
            year = year,
            monthlyNet = monthly.map { paise -> Money(paise) },
            categoryNet = categoryPaise.mapValues { (_, paise) -> Money(paise) },
            grossSpend = Money(grossPaise),
            totalRefunds = Money(refundPaise),
            netTotal = Money(netPaise),
            yearOverYear = YearOverYear(
                currentPeriod = Money(periodPaise),
                previousPeriod = Money(previousPeriodPaise),
                delta = Money(periodPaise - previousPeriodPaise),
            ),
            stats = YearStats(
                entryCount = entryCount,
                dayCount = dayCount,
                maxDay = Money(maxTrackedDay(daily, dailyTracked)),
                avgPerActiveDay = Money(PaiseMath.roundedDivide(netPaise, dayCount)),
            ),
            chartScalePaise = PaiseMath.absMax(*monthly),
        )
    }

    /**
     * The reactive shape of §9: recompute on every emission of the ledger, and re-emit only
     * when the aggregate actually changed, so a ticker-driven Room flow cannot re-animate the
     * chart for a no-op.
     */
    fun stream(expenses: Flow<List<Expense>>, year: Int): Flow<YearDashboard> =
        expenses.map { rows -> calculate(rows, year, clock.todayIn(zone)) }.distinctUntilChanged()

    /**
     * The same month and day one year back, clamped to that month's real length so that
     * 29 February 2028 compares against 28 February 2027 instead of throwing.
     */
    private fun samePeriodEpochDay(year: Int, today: LocalDate): Long {
        val firstOfMonth = LocalDate.of(year, today.monthValue, 1)
        val day = minOf(today.dayOfMonth, firstOfMonth.lengthOfMonth())
        return firstOfMonth.withDayOfMonth(day).toEpochDay()
    }

    /** Heaviest single day that actually carried entries — untouched days must not win. */
    private fun maxTrackedDay(daily: LongArray, tracked: BooleanArray): Long {
        var max = 0L
        for (index in daily.indices) {
            if (tracked[index] && daily[index] > max) max = daily[index]
        }
        return max
    }
}
