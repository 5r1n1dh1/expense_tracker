package com.srinidhi.kharch.domain.expense

import com.srinidhi.kharch.core.model.Expense

/**
 * What the user has asked the expense list to show.
 *
 * A single immutable value rather than six loose parameters, so "is anything filtered?" and
 * "does this row survive?" are questions with one answer each — and so the FilterSheet can hand
 * the whole thing back and forth without a builder.
 *
 * The date window is **inclusive at both ends** here, unlike the half-open convention the
 * repository uses: this value is read by `matches`, not passed to a DAO, and "between 1 Aug and
 * 31 Aug inclusive" is what a user means by a range.
 */
data class ExpenseFilter(
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val categoryIds: Set<String> = emptySet(),
    val minPaise: Long? = null,
    val maxPaise: Long? = null,
    val query: String = "",
) {

    /** `true` when the search field holds something other than whitespace. */
    val hasQuery: Boolean get() = query.isNotBlank()

    /** `true` when a date window, a category selection or an amount range is set. */
    val hasStructuredFilters: Boolean
        get() = fromEpochDay != null || toEpochDay != null ||
            categoryIds.isNotEmpty() || minPaise != null || maxPaise != null

    /** `true` when anything at all narrows the list, the search text included. */
    val isActive: Boolean get() = hasQuery || hasStructuredFilters

    /** How many constraints are set, counting a date window as one. */
    val activeConstraintCount: Int
        get() = listOf(hasDateWindow, hasCategories, hasAmountRange, hasQuery).count { active -> active }

    /** `true` when at least one end of the date window is set. */
    val hasDateWindow: Boolean get() = fromEpochDay != null || toEpochDay != null

    /** `true` when at least one category is selected. */
    val hasCategories: Boolean get() = categoryIds.isNotEmpty()

    /** `true` when at least one end of the amount range is set. */
    val hasAmountRange: Boolean get() = minPaise != null || maxPaise != null

    /**
     * Whether [expense] survives this filter.
     *
     * A tombstone never survives, even before any other test: a deleted expense must vanish from
     * every surface the moment the delete lands (§6.4), and this is a second line of defence
     * behind the repository.
     *
     * The search text matches the name and the note, case-insensitively. Both, because a note
     * reading "refund for the delhi flight" is exactly what someone searches for three months
     * later.
     */
    fun matches(expense: Expense): Boolean {
        if (expense.deletedAt != null) return false
        val from = fromEpochDay
        if (from != null && expense.dateEpochDay < from) return false
        val to = toEpochDay
        if (to != null && expense.dateEpochDay > to) return false
        if (categoryIds.isNotEmpty() && expense.categoryId !in categoryIds) return false
        val minimum = minPaise
        if (minimum != null && expense.amount.paise < minimum) return false
        val maximum = maxPaise
        if (maximum != null && expense.amount.paise > maximum) return false
        if (hasQuery && !matchesQuery(expense, query.trim())) return false
        return true
    }

    /** The same filter with [newQuery] in the search box, everything else untouched. */
    fun withQuery(newQuery: String): ExpenseFilter = copy(query = newQuery)

    /** The same filter with no constraints at all — the "reset filters" action. */
    fun cleared(): ExpenseFilter = ExpenseFilter()

    private fun matchesQuery(expense: Expense, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return expense.name.contains(needle, ignoreCase = true) ||
            expense.note?.contains(needle, ignoreCase = true) == true
    }

    companion object {

        /**
         * Reads an amount typed into one of the FilterSheet's rupee boxes.
         *
         * Accepts what a person actually types — `"500"`, `"500.5"`, `" 500 "`, `""` — and
         * returns `null` for anything that is not a non-negative amount, which the caller reads
         * as "this end of the range is unset". It never routes through `Double`, so a value like
         * `0.07` cannot be lost on the way to paise.
         */
        fun parseAmountInput(text: String): Long? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val rupees = trimmed.toBigDecimalOrNull() ?: return null
            if (rupees.signum() < 0) return null
            val paise = rupees.movePointRight(2).toBigInteger().toLong()
            return if (paise >= 0L) paise else null
        }
    }
}
