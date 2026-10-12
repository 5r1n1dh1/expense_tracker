package com.srinidhi.kharch.domain.expense

/**
 * Why an Add-sheet draft cannot be saved.
 *
 * Every rejection is a separate value rather than a boolean, because the sheet has to name the
 * problem on the field that caused it. A single "invalid" flag would force the UI to guess
 * which of three fields to talk about, and guessing wrong is how a form ends up telling someone
 * their amount is missing when they never touched it.
 */
enum class ExpenseDraftError {
    /** The name is blank, or nothing but whitespace once trimmed. */
    NAME_REQUIRED,

    /** The name is longer than [ExpenseDraftRules.NAME_MAX_LENGTH] characters after trimming. */
    NAME_TOO_LONG,

    /** No amount, or an amount of exactly zero. Money that did not move is not an expense. */
    AMOUNT_REQUIRED,

    /** No category chosen. */
    CATEGORY_REQUIRED,
}

/**
 * The outcome of checking a draft against [ExpenseDraftRules].
 *
 * @property errors every problem found, so a form can show all of them at once instead of
 *   revealing the next one only after the first is fixed.
 */
data class ExpenseDraftValidation(val errors: Set<ExpenseDraftError>) {

    /** `true` when nothing is wrong and the draft may be saved. */
    val isValid: Boolean get() = errors.isEmpty()

    /** `true` when [error] is among the problems. */
    fun has(error: ExpenseDraftError): Boolean = error in errors

    companion object {
        /** The validation of a draft with nothing wrong with it. */
        val Valid: ExpenseDraftValidation = ExpenseDraftValidation(emptySet())
    }
}

/**
 * The rules a draft must satisfy before it becomes an [com.srinidhi.kharch.core.model.Expense].
 *
 * Pure functions over immutable inputs: the repository persists whatever the sheet hands it, so
 * the gate has to live somewhere it can be tested without a ViewModel, a database or a clock.
 */
object ExpenseDraftRules {

    /** A name is a label. Sixty characters is already a sentence. */
    const val NAME_MAX_LENGTH: Int = 60

    /**
     * Checks a draft.
     *
     * @param name the raw, untrimmed name text. Trimmed here so a name that is only spaces is
     *   reported as missing rather than as too short.
     * @param amount the keypad buffer. A zero or empty buffer is a missing amount, never a
     *   valid one — "₹0" is not an expense.
     * @param categoryId the selected category, or `null` when none is chosen.
     * @param nameRequired `false` for the quick-add sheet, which has no name field at all and
     *   files the expense under the category's name instead. A name that *is* typed there is
     *   still length-checked.
     */
    fun validate(
        name: String,
        amount: AmountBuffer,
        categoryId: String?,
        nameRequired: Boolean = true,
    ): ExpenseDraftValidation {
        val trimmed = name.trim()
        val errors = LinkedHashSet<ExpenseDraftError>()
        val missingName = trimmed.isEmpty() && nameRequired
        when {
            missingName -> errors += ExpenseDraftError.NAME_REQUIRED
            trimmed.length > NAME_MAX_LENGTH -> errors += ExpenseDraftError.NAME_TOO_LONG
        }
        if (amount.isZero) {
            errors += ExpenseDraftError.AMOUNT_REQUIRED
        }
        if (categoryId.isNullOrBlank()) {
            errors += ExpenseDraftError.CATEGORY_REQUIRED
        }
        return if (errors.isEmpty()) ExpenseDraftValidation.Valid else ExpenseDraftValidation(errors)
    }
}
