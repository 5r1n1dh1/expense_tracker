package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.Money
import java.time.LocalDate

/**
 * Per-month detail behind the dashboard's drill-down sheet (§9).
 *
 * Pure and clock-free: a month is a closed window, so unlike [GetYearlyDashboard] there is
 * nothing "today"-dependent to inject and nothing for two devices to disagree about.
 */
class GetMonthlyBreakdown {

    /**
     * Aggregates every tracked row of [month] (1-based) in [year].
     *
     * Tombstoned rows are skipped here for the same reason as in the yearly aggregate: a
     * delete has to disappear from every surface the moment it lands (§6.4).
     */
    operator fun invoke(expenses: List<Expense>, year: Int, month: Int): MonthlyBreakdown {
        require(month in 1..MONTHS_IN_YEAR) { "month must be 1..$MONTHS_IN_YEAR, got $month" }

        val daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth()
        val daily = LongArray(daysInMonth)
        val dailyTracked = BooleanArray(daysInMonth)
        val categoryPaise = LinkedHashMap<String, Long>()
        val categoryEntries = LinkedHashMap<String, Int>()
        val refunds = ArrayList<Expense>()
        var grossPaise = 0L
        var refundPaise = 0L
        var entryCount = 0

        for (expense in expenses) {
            if (expense.deletedAt != null) continue
            val date = LocalDate.ofEpochDay(expense.dateEpochDay)
            if (date.year != year || date.monthValue != month) continue
            val signed = expense.money.paise
            entryCount++
            val dayIndex = date.dayOfMonth - 1
            daily[dayIndex] += signed
            dailyTracked[dayIndex] = true
            categoryPaise.merge(expense.categoryId, signed) { left, right -> left + right }
            categoryEntries.merge(expense.categoryId, 1) { left, right -> left + right }
            if (expense.kind == ExpenseKind.REFUND) {
                refundPaise += signed
                refunds += expense
            } else {
                grossPaise += signed
            }
        }

        val totals = categoryPaise.map { (categoryId, paise) ->
            CategoryTotal(categoryId, Money(paise), categoryEntries.getValue(categoryId))
        }.sortedWith(TOTAL_ORDER)

        return MonthlyBreakdown(
            year = year,
            month = month,
            totals = MonthTotals(
                grossSpend = Money(grossPaise),
                totalRefunds = Money(refundPaise),
                netTotal = Money(grossPaise + refundPaise),
            ),
            entryCount = entryCount,
            dayCount = dailyTracked.count { tracked -> tracked },
            maxDay = Money(maxTrackedDay(daily, dailyTracked)),
            dailyNet = daily.map { paise -> Money(paise) },
            categoryTotals = totals,
            refunds = refunds.sortedWith(REFUND_ORDER),
        )
    }

    /** Heaviest single day that actually carried entries — untouched days must not win. */
    private fun maxTrackedDay(daily: LongArray, tracked: BooleanArray): Long {
        var max = 0L
        for (index in daily.indices) {
            if (tracked[index] && daily[index] > max) max = daily[index]
        }
        return max
    }

    private companion object {
        /** Net descending, then category id ascending — a total order two devices agree on. */
        val TOTAL_ORDER: Comparator<CategoryTotal> =
            compareByDescending<CategoryTotal> { total -> total.net.paise }
                .thenBy { total -> total.categoryId }

        /** Refunds read oldest first, with the id as a stable tiebreak. */
        val REFUND_ORDER: Comparator<Expense> =
            compareBy<Expense> { expense -> expense.dateEpochDay }.thenBy { expense -> expense.id }
    }
}
