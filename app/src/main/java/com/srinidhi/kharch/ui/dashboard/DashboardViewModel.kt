package com.srinidhi.kharch.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.core.time.SystemClock
import com.srinidhi.kharch.domain.LedgerQuery
import com.srinidhi.kharch.domain.dashboard.GetMonthlyBreakdown
import com.srinidhi.kharch.domain.dashboard.GetYearlyDashboard
import com.srinidhi.kharch.domain.todayIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * The dashboard's ViewModel.
 *
 * It holds no aggregation logic of its own: everything numeric comes from the domain use
 * cases, which take a plain `List<Expense>` and an injected [Clock] (§9, §13). What lives
 * here is the year the user is looking at, which month's sheet is open, and the sync
 * handshake — the three things that are state rather than data.
 *
 * The ledger arrives through [LedgerQuery], so a unit test drives this class with a fake
 * and never needs Room, an emulator, or the system clock.
 *
 * @param ledger the reactive reads of the shared ledger (D1: one workspace, no account scoping).
 * @param clock injected so "today" — which decides the current year, the current-month dot
 *   and the year-over-year window — is a test input rather than a machine read.
 * @param zone the calendar zone the user's "today" is expressed in.
 */
class DashboardViewModel(
    private val ledger: LedgerQuery,
    private val clock: Clock = SystemClock(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val yearlyDashboard = GetYearlyDashboard(clock, zone)
    private val monthlyBreakdown = GetMonthlyBreakdown()

    private val selectedYear = MutableStateFlow(clock.todayIn(zone).year)
    private val selectedMonth = MutableStateFlow<Int?>(null)
    private val syncing = MutableStateFlow(false)
    private val mutableSyncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * One emission per requested sync.
     *
     * The app root collects this and hands it to the `SyncCoordinator`, which owns
     * everything on the wire. When the coordinator settles, the root calls
     * [onSyncSettled] so pull-to-refresh stops spinning.
     */
    val syncRequests: SharedFlow<Unit> = mutableSyncRequests.asSharedFlow()

    /** The dashboard's state. Recomputes on every ledger emission for the open year. */
    val state: StateFlow<DashboardUiState> = combine(
        ledger.observeExpenses(),
        ledger.observeCategories(),
        selectedYear,
        selectedMonth,
        syncing,
    ) { expenses, categories, year, month, isSyncing ->
        buildState(expenses, categories, year, month, isSyncing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = buildState(emptyList(), emptyList(), selectedYear.value, null, false),
    )

    /** Steps to [year]; a year with no data behind it is still a valid destination. */
    fun onYearSelected(year: Int) {
        if (year == selectedYear.value) return
        selectedYear.value = year
        selectedMonth.value = null
    }

    /** Opens the month's detail sheet, or closes it with `null`. */
    fun onMonthSelected(month: Int?) {
        selectedMonth.value = month
    }

    /** Asks for a sync and shows the pull-to-refresh indicator until [onSyncSettled]. */
    fun requestSync() {
        if (!mutableSyncRequests.tryEmit(Unit)) return
        syncing.value = true
    }

    /** Called by the app root once the sync coordinator has finished. */
    fun onSyncSettled() {
        syncing.value = false
    }

    private fun buildState(
        expenses: List<Expense>,
        categories: List<Category>,
        year: Int,
        month: Int?,
        isSyncing: Boolean,
    ): DashboardUiState {
        val today = clock.todayIn(zone)
        val detail = if (month == null) null else monthlyBreakdown(expenses, year, month)
        return DashboardUiState(
            year = year,
            currentYear = today.year,
            currentMonth = if (year == today.year) today.monthValue else null,
            dashboard = yearlyDashboard.calculate(expenses, year, today),
            categories = categories,
            selectableYears = selectableYears(expenses, today),
            monthDetail = detail,
            isSyncing = isSyncing,
        )
    }

    /**
     * Every year worth offering: the year being lived through plus every year with rows.
     *
     * Newest first, so the year sheet reads like a timeline and the swipe gesture has a
     * well-defined "newer" end.
     */
    private fun selectableYears(expenses: List<Expense>, today: LocalDate): List<Int> {
        val years = LinkedHashSet<Int>()
        years.add(today.year)
        expenses.forEach { expense ->
            years.add(LocalDate.ofEpochDay(expense.dateEpochDay).year)
        }
        return years.sortedDescending()
    }

    private companion object {
        /** How long the state flow stays subscribed while nothing is collecting it. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
