package com.srinidhi.kharch.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.YearMonth
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.domain.expense.CategoryCatalog
import com.srinidhi.kharch.domain.expense.EmptyStateReason
import com.srinidhi.kharch.domain.expense.ExpenseFilter
import com.srinidhi.kharch.domain.expense.ExpenseGrouping
import com.srinidhi.kharch.domain.expense.ExpenseRepository
import com.srinidhi.kharch.domain.expense.MonthSection
import com.srinidhi.kharch.domain.expense.emptyStateReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * The expenses list (plan §7.2B, §7.3).
 *
 * One flow in, one state out. The month window, the search text, the filter and the undo
 * snapshot are all folded into a single immutable [ExpensesUiState] by the `combine` below, so
 * the screen has exactly one thing to render and no way to end up showing last week's list
 * under this week's header.
 */

/** The row that was just deleted and is waiting for its Undo tap. */
data class PendingDelete(
    val expense: Expense,
)

/** The reason the list is empty, plus the words the empty state should use. */
data class EmptyNotice(
    val reason: EmptyStateReason,
    val quotedQuery: String,
)

/**
 * Everything the expenses screen renders.
 *
 * @property loading `true` until the first emission arrives.
 * @property month the month on screen. The `null` case is a custom date range from the filter
 *   sheet, which is why it is nullable rather than defaulted.
 * @property monthsWithData every month that has expenses, newest first — what the month pager
 *   offers.
 * @property sections the filtered, grouped list.
 * @property activeFilterCount how many filter constraints are on, for the badge on the filter
 *   button.
 */
data class ExpensesUiState(
    val loading: Boolean = true,
    val month: YearMonth? = null,
    val monthsWithData: List<YearMonth> = emptyList(),
    val sections: List<MonthSection> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryNames: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val filter: ExpenseFilter = ExpenseFilter(),
    val ledgerHasData: Boolean = false,
    val emptyNotice: EmptyNotice? = null,
    val pendingDelete: PendingDelete? = null,
    val longPressedExpenseId: String? = null,
) {
    /** `true` when the filter is doing something other than narrowing the search text. */
    val hasStructuredFilters: Boolean get() = filter.hasStructuredFilters

    /** The month shown, or the most recent month with data when a custom range is on screen. */
    val displayMonth: YearMonth?
        get() = month ?: sections.firstOrNull()?.yearMonth ?: monthsWithData.firstOrNull()

    /** `true` when the list can be scrolled rather than showing the empty state. */
    val hasRows: Boolean get() = sections.isNotEmpty()
}

/**
 * Drives the expense list.
 *
 * The month window is expressed *as* the filter's date range rather than as a separate scope:
 * one means "keep the visible rows", and it means the FilterSheet's date presets and the month
 * pager are the same control rather than two that can contradict each other. Switching month
 * clears a custom range, because the two are alternatives.
 */
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val categories: CategoryCatalog,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow<YearMonth?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ExpenseFilter())
    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)
    private val longPressed = MutableStateFlow<String?>(null)

    private val todayMonth: YearMonth
        get() = clock.today().let { today -> YearMonth.of(today.year, today.monthValue) }

    /** The renderable state. */
    val uiState: StateFlow<ExpensesUiState> =
        combine(
            expenses.observeAll(),
            categories.observeAll(),
            combine(month, query, filter, pendingDelete, longPressed) { window, text, filters, undo, pressed ->
                WindowBundle(window, text, filters, undo, pressed)
            },
        ) { rows, categoryRows, bundle ->
            buildState(rows, categoryRows, bundle)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ExpensesUiState(),
        )

    /** Opens the screen on [target], or on the newest month with data. */
    fun open(target: YearMonth? = null) {
        viewModelScope.launch {
            month.value = target ?: newestMonthWithData()
        }
    }

    /** Moves to [target] and drops any custom date range, so the two cannot disagree. */
    fun selectMonth(target: YearMonth) {
        filter.update { current -> current.copy(fromEpochDay = null, toEpochDay = null) }
        month.value = target
    }

    /** Steps one month back, staying inside the months that have data. */
    fun previousMonth() {
        val current = month.value ?: return
        selectMonth(current.minusMonths(1))
    }

    /** Steps one month forward, never past the current calendar month. */
    fun nextMonth() {
        val current = month.value ?: return
        val limit = todayMonth
        val next = current.plusMonths(1)
        if (next <= limit) {
            selectMonth(next)
        }
    }

    /** The search field. Writes through to the filter so one code path owns matching. */
    fun onSearchQueryChange(value: String) {
        query.value = value
        filter.update { current -> current.copy(query = value) }
    }

    /** The search field's clear affordance. */
    fun onSearchQueryCleared() = onSearchQueryChange("")

    /** A category chip in the filter sheet. */
    fun onFilterCategoryToggled(categoryId: String) {
        filter.update { current ->
            val selected = current.categoryIds
            val next = if (categoryId in selected) selected - categoryId else selected + categoryId
            current.copy(categoryIds = next)
        }
    }

    /** One end of the amount range, in paise. `null` clears that end. */
    fun onFilterAmountRangeChanged(minPaise: Long?, maxPaise: Long?) {
        filter.update { current -> current.copy(minPaise = minPaise, maxPaise = maxPaise) }
    }

    /** A date-range preset from the filter sheet, replacing the month window. */
    fun onFilterDateRangeChanged(fromEpochDay: Long?, toEpochDay: Long?) {
        month.value = null
        filter.update { current -> current.copy(fromEpochDay = fromEpochDay, toEpochDay = toEpochDay) }
    }

    /** Clears every constraint and returns to the newest month that has data. */
    fun onFiltersCleared() {
        filter.value = ExpenseFilter()
        query.value = ""
        viewModelScope.launch { month.value = newestMonthWithData() }
    }

    /** Long-press on a row opens its context actions (plan §7.3). */
    fun onRowLongPressed(expenseId: String) {
        longPressed.value = expenseId
    }

    /** Dismisses the context actions. */
    fun onRowLongPressDismissed() {
        longPressed.value = null
    }

    /**
     * Swipe right → delete (plan §7.3).
     *
     * The row is tombstoned immediately and the [PendingDelete] is what buys the eight-second
     * Undo window: the list must not show a row the user has already swiped away, and a delete
     * that has not been committed yet would leave the list lying about what is on disk.
     */
    fun onDeleteRequested(expenseId: String) {
        viewModelScope.launch {
            val row = expenses.findById(expenseId) ?: return@launch
            expenses.softDelete(expenseId, clock.nowMillis())
            pendingDelete.value = PendingDelete(row)
        }
    }

    /** The Undo tap: clears the tombstone and the snackbar in one step. */
    fun onUndoDelete() {
        val pending = pendingDelete.value ?: return
        viewModelScope.launch {
            expenses.restore(pending.expense.id)
            pendingDelete.value = null
        }
    }

    /** The snackbar's eight seconds are up. The delete stands. */
    fun onUndoWindowExpired() {
        pendingDelete.value = null
    }

    /** Re-files one expense under another category, from the long-press context row (D4). */
    fun onChangeCategory(expenseId: String, categoryId: String) {
        viewModelScope.launch {
            expenses.moveToCategory(expenseId, categoryId, clock.nowMillis())
            longPressed.value = null
        }
    }

    private suspend fun newestMonthWithData(): YearMonth? {
        val rows = expenses.observeAll()
        var newest: YearMonth? = null
        rows.collect { list ->
            newest = list.maxByOrNull { row -> row.dateEpochDay }
                ?.let { row -> YearMonth.fromEpochDay(row.dateEpochDay) }
            return@collect
        }
        return newest
    }

    private fun buildState(
        rows: List<Expense>,
        categoryRows: List<Category>,
        bundle: WindowBundle,
    ): ExpensesUiState {
        val live = rows.filter { row -> row.deletedAt == null }
        val months = live.map { row -> YearMonth.fromEpochDay(row.dateEpochDay) }
            .distinct()
            .sortedDescending()
        val effective = bundle.filters.effectiveWindow(bundle.window)
        val visible = live.filter { row -> effective.matches(row) }
        val sections = ExpenseGrouping.byMonth(visible)
        val notice = if (sections.isEmpty()) {
            EmptyNotice(
                reason = emptyStateReason(
                    hasAnyLedgerData = live.isNotEmpty(),
                    query = bundle.searchQuery,
                    filters = effective,
                ),
                quotedQuery = bundle.searchQuery.trim(),
            )
        } else {
            null
        }
        return ExpensesUiState(
            loading = false,
            month = bundle.window,
            monthsWithData = months,
            sections = sections,
            categories = categoryRows.filter { category -> !category.isHidden },
            categoryNames = categoryRows.associate { category -> category.id to category.name },
            searchQuery = bundle.searchQuery,
            filter = effective,
            ledgerHasData = live.isNotEmpty(),
            emptyNotice = notice,
            pendingDelete = bundle.pendingDelete,
            longPressedExpenseId = bundle.longPressed,
        )
    }

    /** The month window folded into the filter's date range, when the filter has no range. */
    private fun ExpenseFilter.effectiveWindow(window: YearMonth?): ExpenseFilter =
        if (hasDateWindow || window == null) {
            this
        } else {
            copy(fromEpochDay = window.firstEpochDay, toEpochDay = window.lastEpochDay)
        }

    /** The five narrowing inputs, carried as one value through `combine`. */
    private data class WindowBundle(
        val window: YearMonth?,
        val searchQuery: String,
        val filters: ExpenseFilter,
        val pendingDelete: PendingDelete?,
        val longPressed: String?,
    )

    private companion object {
        /** Keeps the repositories hot across a configuration change, but not forever. */
        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
