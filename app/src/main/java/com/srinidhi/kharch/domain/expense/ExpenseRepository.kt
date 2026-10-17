package com.srinidhi.kharch.domain.expense

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * The read/write seam the expense UI uses to reach expenses.
 *
 * Phase 5 owns this interface and nothing else about persistence. Sync (plan §6) is
 * deliberately **absent** from it: a write marks the row pending in the local outbox and the
 * sync layer Phase 2/3 owns picks it up from there. The UI therefore never waits on the
 * network, and every save is offline-first by construction rather than by convention.
 *
 * Implementations must hold three invariants the UI depends on:
 *
 *  1. a delete is a **tombstone**, never a removed row, so it can still be restored inside the
 *     undo window and can still propagate to the user's other devices (plan §6.4);
 *  2. `linkedExpenseId` on a refund points at a real expense of the same category, or is null;
 *  3. amounts are non-negative magnitudes, with the direction carried by `kind` (D5).
 */
interface ExpenseRepository {

    /** Every live expense, newest first. Tombstones are never returned here. */
    fun observeAll(): Flow<List<Expense>>

    /**
     * Live expenses in a **half-open** epoch-day window `[fromEpochDay, toEpochDayExclusive)`.
     *
     * Half-open is the same convention `ExpenseDao` uses, so consecutive months chain without
     * an off-by-one at the boundary.
     */
    fun observeBetween(fromEpochDay: Long, toEpochDayExclusive: Long): Flow<List<Expense>>

    /** A single row including tombstones, so the undo window can read back what it deleted. */
    suspend fun findById(id: String): Expense?

    /**
     * The most recent expenses in [categoryId], newest first.
     *
     * This is the candidate list behind the "refund for…" row (D5): a refund links to an
     * original of the same category, and the user picks from what they actually spent rather
     * than typing an id.
     */
    suspend fun recentInCategory(categoryId: String, limit: Int): List<Expense>

    /** Inserts or replaces [expense]. The id decides which, so add and edit share one path. */
    suspend fun save(expense: Expense)

    /** Tombstones [id] at [deletedAtMillis]. The row is retained so the delete can propagate. */
    suspend fun softDelete(id: String, deletedAtMillis: Long)

    /** Clears the tombstone on [id]. The other half of the undo window. */
    suspend fun restore(id: String)

    /** Re-files a single expense under [categoryId], leaving its amount and date untouched. */
    suspend fun moveToCategory(id: String, categoryId: String, updatedAtMillis: Long)
}
