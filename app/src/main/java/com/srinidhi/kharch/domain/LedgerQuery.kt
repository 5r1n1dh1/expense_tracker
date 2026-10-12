package com.srinidhi.kharch.domain

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * The two reads the dashboard needs from the shared ledger (§9).
 *
 * Deliberately narrow: the dashboard aggregates in Kotlin over the rows it is handed
 * (`GetYearlyDashboard` / `GetMonthlyBreakdown`), so it needs a plain reactive list and
 * nothing else. No account filter appears here and none ever will — there is exactly
 * one workspace (D1), and a query that could scope to an account would be the bug.
 *
 * This interface is the seam the Phase 6 dashboard owns. The implementation lives in the
 * data layer (a Room-backed repository) and is wired wherever the app composes its object
 * graph; the ViewModel depends on this interface, so the aggregation stays testable with a
 * fake and the storage engine stays replaceable.
 *
 * Every emission is the full live row set, tombstones included. The aggregation use cases
 * skip `deletedAt != null` rows themselves, which is why a delete that lands from another
 * device disappears from the dashboard the moment it does (§6.4).
 */
interface LedgerQuery {

    /** Every tracked row of the shared ledger, oldest change last is not implied. */
    fun observeExpenses(): Flow<List<Expense>>

    /** Every category, including hidden ones — history keeps rendering them (D4). */
    fun observeCategories(): Flow<List<Category>>
}
