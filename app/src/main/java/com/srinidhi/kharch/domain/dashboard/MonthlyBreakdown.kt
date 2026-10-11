package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.Money

/**
 * One category's contribution to a month, signed the same way as every other figure (D5).
 *
 * A negative [net] is meaningful, not corrupt: refunds against this category exceeded the
 * spend. The donut gives such a slice a ghosted treatment and the legend spells it out in
 * words rather than colour alone.
 */
data class CategoryTotal(
    val categoryId: String,
    val net: Money,
    val entryCount: Int,
)

/**
 * The gross / refunds / net trio of a month, kept together because they are always read
 * together — the header shows net, the secondary line shows the other two (D5).
 */
data class MonthTotals(
    val grossSpend: Money,
    val totalRefunds: Money,
    val netTotal: Money,
)

/**
 * Detail for one month of the drill-down sheet (§9).
 *
 * @property dailyNet one slot per calendar day of the month — 28, 29, 30 or 31 — so a
 *   February sheet never silently loses a bar.
 * @property categoryTotals every category present that month, net descending, ties broken by
 *   category id so two devices render the same order.
 * @property refunds the refund rows recorded in the month, oldest first, so the sheet can
 *   list what came back instead of only netting it away.
 */
data class MonthlyBreakdown(
    val year: Int,
    val month: Int,
    val totals: MonthTotals,
    val entryCount: Int,
    val dayCount: Int,
    val maxDay: Money,
    val dailyNet: List<Money>,
    val categoryTotals: List<CategoryTotal>,
    val refunds: List<Expense>,
) {
    /** Sum of expense magnitudes in the month — never negative. */
    val grossSpend: Money
        get() = totals.grossSpend

    /** Sum of refund magnitudes in the month — a negative number (D5). */
    val totalRefunds: Money
        get() = totals.totalRefunds

    /** What the sheet headlines: charges minus refunds, negative months included. */
    val netTotal: Money
        get() = totals.netTotal

    /** True when nothing at all was tracked in the month. */
    val isEmpty: Boolean
        get() = entryCount == 0

    /** True when refunds pushed the whole month below zero (D5). */
    val isNetNegative: Boolean
        get() = netTotal.isCredit

    /** The running "top spenders" slice — the leading [count] categories by net. */
    fun topCategories(count: Int = DEFAULT_TOP_CATEGORY_COUNT): List<CategoryTotal> {
        require(count >= 0) { "count must not be negative, got $count" }
        return categoryTotals.take(count)
    }

    companion object {
        /** How many categories the drill-down sheet lists before "and N more". */
        const val DEFAULT_TOP_CATEGORY_COUNT: Int = 5
    }
}
