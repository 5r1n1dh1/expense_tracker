package com.srinidhi.kharch.domain.category

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import java.util.Locale

/**
 * Why a proposed category name was refused.
 *
 * A refusal is always a value, never an exception: the manager screen has to explain the
 * problem in words, and it has to do so without guessing what went wrong.
 */
enum class NameRejection {
    /** Empty, or nothing but whitespace once trimmed. */
    BLANK,

    /** Longer than [CategoryRules.NAME_MAX_LENGTH] characters after trimming. */
    TOO_LONG,

    /** Already used by another visible category, ignoring case (§5.2). */
    DUPLICATE_NAME,
}

/** The outcome of validating a proposed name. */
sealed interface NameValidation {
    /** The trimmed name that should be stored. */
    data class Valid(val name: String) : NameValidation

    /** The name cannot be used; [reason] is what the screen shows. */
    data class Invalid(val reason: NameRejection) : NameValidation
}

/** The outcome of a rename attempt. */
sealed interface RenameResult {
    /** [category] carries the new, trimmed name. */
    data class Renamed(val category: Category) : RenameResult

    /** The name was refused; [reason] is what the screen shows. */
    data class Refused(val reason: NameRejection) : RenameResult
}

/** Why a reorder was refused — a drag that is not a permutation is a bug, not a hint. */
enum class ReorderRejection {
    /** The requested order leaves a category out. */
    MISSING_IDS,

    /** The requested order names a category that does not exist. */
    UNKNOWN_IDS,

    /** The requested order repeats a category, so it cannot produce distinct sort orders. */
    DUPLICATE_IDS,
}

/** The outcome of a reorder. */
sealed interface ReorderResult {
    /**
     * [sortOrders] is a contiguous `0..n-1` map covering **every** category, hidden ones
     * included. It is the single payload the caller writes as one batched Firestore batch
     * (§5.2, §6.8): a reorder of every category must cost one write, not fourteen.
     */
    data class Applied(val sortOrders: Map<String, Int>, val changedCount: Int) : ReorderResult

    /** The requested order is not a permutation of the current categories. */
    data class Rejected(val reason: ReorderRejection) : ReorderResult
}

/** Why a delete was refused. */
enum class DeleteRejection {
    /** Seeded categories are part of the app: hideable, renameable, never deletable (§5.2). */
    SYSTEM_CATEGORY,

    /** Expenses still point at it — hiding keeps the user's history attached. */
    IN_USE,
}

/** What to do about a category the user asked to delete. */
sealed interface DeleteDecision {
    /** Nothing references it; the caller may soft-delete the row. */
    data object Allowed : DeleteDecision

    /** Refused outright — nothing the user can do about this row. */
    data class Rejected(val reason: DeleteRejection) : DeleteDecision

    /** Refused, but the category can be hidden instead so history survives. */
    data class HideInstead(val reason: DeleteRejection) : DeleteDecision
}

/**
 * The rules that govern categories once the twelve seeded ones belong to the user (D4, §5.2).
 *
 * Pure functions over immutable inputs: the repository persists whatever these return, and
 * nothing here can touch storage, the clock or the network.
 *
 * These functions deliberately do **not** stamp `updatedAt`. Sync metadata is not part of the
 * domain category, so the writer adds it from its own clock on the way to Room and Firestore
 * — the same split [com.srinidhi.kharch.domain.sync.LedgerRow] makes for authorship.
 */
object CategoryRules {

    /** A category name is free text, but it is still a label — 40 characters is the ceiling. */
    const val NAME_MAX_LENGTH: Int = 40

    /** Anything shorter than this is blank once trimmed. */
    const val NAME_MIN_LENGTH: Int = 1

    /**
     * Validates [rawName] against the other visible categories.
     *
     * [candidateId] is the category being renamed, so a category may keep the name it
     * already has but never collide with a sibling. Uniqueness is case-insensitive, which is
     * what stops "food" and "Food" from both sitting in the Add sheet.
     */
    fun validateName(existing: List<Category>, candidateId: String?, rawName: String): NameValidation {
        val trimmed = rawName.trim()
        if (trimmed.length < NAME_MIN_LENGTH) return NameValidation.Invalid(NameRejection.BLANK)
        if (trimmed.length > NAME_MAX_LENGTH) return NameValidation.Invalid(NameRejection.TOO_LONG)
        val folded = foldCase(trimmed)
        val clash = existing.any { category ->
            category.id != candidateId && !category.isHidden && foldCase(category.name.trim()) == folded
        }
        return if (clash) {
            NameValidation.Invalid(NameRejection.DUPLICATE_NAME)
        } else {
            NameValidation.Valid(trimmed)
        }
    }

    /**
     * The renamed category, or the refusal that stopped it.
     *
     * The name comes back trimmed: a category called " Food " and one called "Food" are the
     * same category as far as the Add sheet is concerned.
     */
    fun rename(existing: List<Category>, category: Category, rawName: String): RenameResult =
        when (val validation = validateName(existing, category.id, rawName)) {
            is NameValidation.Invalid -> RenameResult.Refused(validation.reason)
            is NameValidation.Valid -> RenameResult.Renamed(category.copy(name = validation.name))
        }

    /**
     * Turns a drag-and-drop order into contiguous sort orders.
     *
     * [orderedIds] must be a permutation of [categories]: every category exactly once. Hidden
     * categories must be included in that list — they keep their row, so they keep a place in
     * the order; dropping them would renumber the visible ones around them forever.
     */
    fun reorder(categories: List<Category>, orderedIds: List<String>): ReorderResult {
        val known = categories.map { category -> category.id }.toSet()
        if (orderedIds.distinct().size != orderedIds.size) {
            return ReorderResult.Rejected(ReorderRejection.DUPLICATE_IDS)
        }
        if (orderedIds.any { id -> id !in known }) {
            return ReorderResult.Rejected(ReorderRejection.UNKNOWN_IDS)
        }
        if (orderedIds.size != known.size) return ReorderResult.Rejected(ReorderRejection.MISSING_IDS)

        val sortOrders = LinkedHashMap<String, Int>(orderedIds.size)
        orderedIds.forEachIndexed { index, id -> sortOrders[id] = index }
        val changed = categories.count { category -> sortOrders[category.id] != category.sortOrder }
        return ReorderResult.Applied(sortOrders, changed)
    }

    /**
     * [categories] carrying the new sort orders, or `null` when [orderedIds] is not a
     * permutation. Convenience over [reorder] for callers that want rows instead of a map.
     */
    fun reordered(categories: List<Category>, orderedIds: List<String>): List<Category>? =
        when (val result = reorder(categories, orderedIds)) {
            is ReorderResult.Rejected -> null
            is ReorderResult.Applied -> categories.map { category ->
                category.copy(sortOrder = result.sortOrders.getValue(category.id))
            }
        }

    /**
     * Hiding keeps the row and its history; the category simply stops being offered in the
     * Add sheet and the filter chips (§5.2). Showing it again is the same call with [hidden]
     * set back to `false`.
     */
    fun setHidden(category: Category, hidden: Boolean): Category = category.copy(isHidden = hidden)

    /**
     * Delete only when nothing references the category.
     *
     * A category in use is hidden instead and the reason travels back, so the UI can say
     * why. The user is never silently reassigned their history (§5.2).
     */
    fun deleteDecision(category: Category, usageCount: Int): DeleteDecision = when {
        usageCount > 0 -> DeleteDecision.HideInstead(DeleteRejection.IN_USE)
        category.isSystem -> DeleteDecision.Rejected(DeleteRejection.SYSTEM_CATEGORY)
        else -> DeleteDecision.Allowed
    }

    /**
     * How many live expenses point at each category. Tombstoned rows are excluded: a deleted
     * expense is not history, so it must not pin a category open forever.
     */
    fun usageCounts(expenses: List<Expense>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (expense in expenses) {
            if (expense.deletedAt != null) continue
            counts.merge(expense.categoryId, 1) { left, right -> left + right }
        }
        return counts
    }

    /** The sort order a freshly created category should take: one past the current maximum. */
    fun nextSortOrder(categories: List<Category>): Int =
        (categories.maxOfOrNull { category -> category.sortOrder } ?: -1) + 1

    /** Categories the user may still see in the Add sheet and filter chips, in display order. */
    fun visibleCategories(categories: List<Category>): List<Category> =
        categories.filterNot { category -> category.isHidden }.sortedBy { category -> category.sortOrder }

    /**
     * Case folding for duplicate detection. [Locale.ROOT] rather than the default locale, so
     * a Turkish device cannot treat "I" and "ı" as the same letter here and then store two
     * names that collide everywhere else.
     */
    private fun foldCase(value: String): String = value.lowercase(Locale.ROOT)
}
