package com.srinidhi.kharch.ui.dashboard

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.domain.category.CategoryRules
import com.srinidhi.kharch.domain.dashboard.MonthlyBreakdown
import com.srinidhi.kharch.domain.dashboard.YearDashboard

/**
 * Everything the dashboard draws, as one immutable value.
 *
 * The state always carries a [YearDashboard], even when the ledger is empty. That is the
 * whole of D9: an empty year still aggregates into twelve bar slots and a shared scale, so
 * the populated and the empty dashboard occupy identical bounds and nothing reflows when
 * the first expense lands.
 *
 * @property year the year on screen.
 * @property currentYear the year the device is living through.
 * @property currentMonth the month being lived through, or `null` when [year] is not the
 *   current year. Null years draw no current-month dot.
 * @property dashboard the year's aggregate; always exactly twelve bar slots.
 * @property categories the categories in display order (D4), used to name rows.
 * @property selectableYears every year on offer, newest first.
 * @property monthDetail the open month's drill-down, or `null` when no sheet is showing.
 * @property isSyncing `true` while a requested sync is in flight (drives pull-to-refresh).
 */
data class DashboardUiState(
    val year: Int,
    val currentYear: Int,
    val currentMonth: Int?,
    val dashboard: YearDashboard,
    val categories: List<Category> = emptyList(),
    val selectableYears: List<Int> = emptyList(),
    val monthDetail: MonthlyBreakdown? = null,
    val isSyncing: Boolean = false,
) {
    /** True when the year holds no tracked entry at all (D9). */
    val isEmpty: Boolean get() = dashboard.isEmpty

    /** True when refunds pushed at least one month below zero (D5). */
    val hasNegativeMonth: Boolean get() = dashboard.hasNegativeMonth

    /** The month whose detail sheet is open, or `null`. */
    val selectedMonth: Int? get() = monthDetail?.month

    /** True when the viewer is looking at the year being lived through. */
    val isCurrentYear: Boolean get() = year == currentYear

    /** Categories by id, for naming the donut's slices and the sheet's rows. */
    val categoriesById: Map<String, Category> get() = categories.associateBy { category -> category.id }

    /** True when a swipe towards newer years has somewhere to go. */
    val canStepNewer: Boolean get() = selectableYears.any { it > year }

    /** True when a swipe towards older years has somewhere to go. */
    val canStepOlder: Boolean get() = selectableYears.any { it < year }

    /** Categories in display order, hidden ones excluded from the pickers (D4). */
    val displayCategories: List<Category> get() = CategoryRules.visibleCategories(categories)
}
