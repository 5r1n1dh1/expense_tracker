package com.srinidhi.kharch.domain

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise

/**
 * Everything the quick-add flow knows once the user presses Save (plan §7.2G).
 *
 * The date travels as an epoch day rather than a `LocalDate` so this seam stays pure Kotlin: the
 * caller resolves "today" from the clock it already owns, and the store never imports a date type
 * it would have to test.
 *
 * @property name free text. May be blank; the row still means something.
 * @property amount direction-free magnitude in paise, always `>= 0` (D5).
 * @property kind which way the money went (D5).
 * @property categoryId the chosen category; never blank once the sheet is usable.
 * @property dateEpochDay `LocalDate.toEpochDay()` of the day the money was spent.
 */
data class QuickAddDraft(
    val name: String,
    val amount: MagnitudePaise,
    val kind: ExpenseKind,
    val categoryId: String,
    val dateEpochDay: Long,
)

/** A category as the quick-add sheet needs it: an id to store and a name to show (D4). */
data class QuickAddCategory(
    val id: String,
    val name: String,
)

/**
 * The seam the widget's quick add talks to (plan §7.2G, §10.3).
 *
 * Deliberately the *whole* surface, and deliberately small. Quick add only ever creates one row,
 * so there is no update, delete or sync method here to keep honest, and no read beyond the
 * categories the sheet has to offer. The widget never reads through this seam at all: it computes
 * its figures straight from Room, so a widget can never show a total that disagrees with the rows
 * behind it (plan §10.3).
 *
 * Implementations live in `data/` and own the "the widget must refresh after a write" step. That
 * keeps the reactive wiring in the layer that can see the write, rather than duplicated in every
 * caller that happens to save something.
 */
interface QuickAddStore {
    /**
     * Persists [draft] and returns the row exactly as it now exists.
     *
     * The save is expected to leave the home-screen widget stale only for as long as the call
     * takes: implementations trigger `KharchWidget.updateAll` before returning (D1 means there is
     * one ledger, so every widget instance shows the same figure).
     */
    suspend fun save(draft: QuickAddDraft): Expense

    /**
     * The categories the quick-add sheet offers, in display order.
     *
     * Hidden categories are excluded — they are not offered for new rows, though existing history
     * keeps rendering them (D4).
     */
    suspend fun categories(): List<QuickAddCategory>
}
