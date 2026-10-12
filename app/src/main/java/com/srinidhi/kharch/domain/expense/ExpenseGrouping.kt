package com.srinidhi.kharch.domain.expense

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.YearMonth

/**
 * One day's expenses, already grouped and ordered for the list.
 *
 * @property epochDay `LocalDate.toEpochDay()`, the day this group covers.
 * @property expenses that day's rows, newest first.
 */
data class DayGroup(
    val epochDay: Long,
    val expenses: List<Expense>,
) {
    /** Charges minus refunds for the day. Negative when the day came out ahead (D5). */
    val netPaise: Long get() = expenses.sumOf { expense -> expense.money.paise }

    /** How many of the day's rows are refunds. */
    val refundCount: Int get() = expenses.count { expense -> expense.isRefund }

    /** `true` when the day contains at least one refund, so the header can say so. */
    val hasRefunds: Boolean get() = refundCount > 0
}

/**
 * A month of [DayGroup]s, for the sticky month header (plan §7.2B).
 *
 * @property yearMonth the month this section covers.
 * @property days that month's days with expenses, newest first.
 */
data class MonthSection(
    val yearMonth: YearMonth,
    val days: List<DayGroup>,
) {
    /** Charges minus refunds for the month. A month may be net negative (D5). */
    val netPaise: Long get() = days.sumOf { day -> day.netPaise }

    /** Every row in the section, in display order. */
    val expenses: List<Expense> get() = days.flatMap { day -> day.expenses }
}

/**
 * Groups a flat list of expenses into the day/month structure the list renders.
 *
 * Pure and clock-free. Labels ("Today · Tue, 12 Aug", "August 2026") are a presentation
 * concern and stay in the UI, so this function can be tested by handing it a list and an
 * expected shape.
 */
object ExpenseGrouping {

    /**
     * Groups [expenses] by day, newest day first.
     *
     * Tombstones are dropped here even though a repository should already have done so: a
     * deleted expense must disappear from every surface the moment it lands (§6.4), and this is
     * the last place that can be true.
     */
    fun byDay(expenses: List<Expense>): List<DayGroup> {
        val ordered = expenses
            .filter { expense -> expense.deletedAt == null }
            .sortedWith(ENTRY_ORDER)
        val days = LinkedHashMap<Long, MutableList<Expense>>()
        for (expense in ordered) {
            days.getOrPut(expense.dateEpochDay) { ArrayList() } += expense
        }
        return days.entries
            .sortedByDescending { entry -> entry.key }
            .map { entry -> DayGroup(epochDay = entry.key, expenses = entry.value.toList()) }
    }

    /**
     * Groups [expenses] into [MonthSection]s, newest month first.
     *
     * Months with no expenses produce no section at all: the list shows the months the user
     * actually spent in, and the month pager offers the rest.
     */
    fun byMonth(expenses: List<Expense>): List<MonthSection> {
        val months = LinkedHashMap<YearMonth, MutableList<Expense>>()
        for (expense in expenses) {
            if (expense.deletedAt != null) continue
            val month = YearMonth.fromEpochDay(expense.dateEpochDay)
            months.getOrPut(month) { ArrayList() } += expense
        }
        return months.entries
            .sortedByDescending { entry -> entry.key }
            .map { entry -> MonthSection(entry.key, byDay(entry.value)) }
    }

    /** Newest entry first, with the id as a stable tiebreak so two devices render alike. */
    private val ENTRY_ORDER: Comparator<Expense> =
        compareByDescending<Expense> { expense -> expense.createdAt }
            .thenByDescending { expense -> expense.dateEpochDay }
            .thenBy { expense -> expense.id }
}
