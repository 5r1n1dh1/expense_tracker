package com.srinidhi.kharch.domain.expense

/**
 * Why the expense list is empty (D9, plan §7.2B).
 *
 * The empty state is a designed surface, not a placeholder, and the whole point of the design
 * is that it names the *reason*: "no expenses here" and "no results for 'coffee'" are different
 * problems with different fixes, and a generic "nothing to show" would leave the user guessing
 * which one they have.
 */
enum class EmptyStateReason {

    /** The ledger has no expenses at all. The very first run, and a fresh install. */
    LEDGER_EMPTY,

    /** The ledger has expenses, but none fall inside the month or range on screen. */
    NOTHING_IN_RANGE,

    /** The search text matched nothing. */
    NO_RESULTS_FOR_QUERY,

    /** A filter — date window, categories, amount range — matched nothing. */
    NO_FILTER_MATCHES,
}

/**
 * Picks the reason an empty list is empty.
 *
 * Precedence is deliberate. The search text wins over the filters, because it is the narrower,
 * more recent thing the user did: if they just typed "coffee" into a filtered list, the sentence
 * they need is about "coffee", not about the filters they set an hour ago. The filters win over
 * the range, for the same reason. Only once both are clear does the question become "is the
 * ledger empty, or is this month empty?" — and that answer depends on whether the ledger has any
 * expenses at all.
 *
 * @param hasAnyLedgerData `true` when the ledger contains at least one live expense, whether or
 *   not it is inside the current window.
 * @param query the search text.
 * @param filters everything the user has narrowed the list by.
 */
fun emptyStateReason(
    hasAnyLedgerData: Boolean,
    query: String,
    filters: ExpenseFilter,
): EmptyStateReason = when {
    filters.hasQuery -> EmptyStateReason.NO_RESULTS_FOR_QUERY
    filters.hasStructuredFilters -> EmptyStateReason.NO_FILTER_MATCHES
    hasAnyLedgerData -> EmptyStateReason.NOTHING_IN_RANGE
    else -> EmptyStateReason.LEDGER_EMPTY
}
