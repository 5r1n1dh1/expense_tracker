package com.srinidhi.kharch.ui.addedit

/**
 * Test tags for the Add/Edit sheet.
 *
 * Kept in one place because the sheet is exercised from two directions: the keypad tests find
 * keys by tag and never by text, and the refund-toggle tests assert on the semantic state
 * rather than on a colour. Sharing one object means a rename is a one-line change rather than a
 * hunt through two test source roots.
 */
object AddEditTestTags {
    const val AMOUNT_HERO: String = "add-edit-amount-hero"
    const val AMOUNT_HINT: String = "add-edit-amount-hint"
    const val KEY_DONE: String = "add-edit-key-done"
    const val KEY_BACKSPACE: String = "add-edit-key-backspace"
    const val KEY_DECIMAL: String = "add-edit-key-decimal"

    const val KEY_DIGIT_ZERO: String = "add-edit-key-digit-0"
    const val KEY_DIGIT_ONE: String = "add-edit-key-digit-1"
    const val KEY_DIGIT_TWO: String = "add-edit-key-digit-2"
    const val KEY_DIGIT_THREE: String = "add-edit-key-digit-3"
    const val KEY_DIGIT_FOUR: String = "add-edit-key-digit-4"
    const val KEY_DIGIT_FIVE: String = "add-edit-key-digit-5"
    const val KEY_DIGIT_SIX: String = "add-edit-key-digit-6"
    const val KEY_DIGIT_SEVEN: String = "add-edit-key-digit-7"
    const val KEY_DIGIT_EIGHT: String = "add-edit-key-digit-8"
    const val KEY_DIGIT_NINE: String = "add-edit-key-digit-9"

    const val KIND_EXPENSE: String = "add-edit-kind-expense"
    const val KIND_REFUND: String = "add-edit-kind-refund"
    const val REFUND_LINK_ROW: String = "add-edit-refund-link-row"
    const val CATEGORY_CHIP_PREFIX: String = "add-edit-category-"
    const val MANAGE_CATEGORIES_CHIP: String = "add-edit-manage-categories-chip"
    const val DATE_CHIP: String = "add-edit-date-chip"
    const val DATE_PICKER_SHEET: String = "add-edit-date-picker-sheet"
    const val DATE_PICKER_PREVIOUS: String = "add-edit-date-picker-previous"
    const val DATE_PICKER_NEXT: String = "add-edit-date-picker-next"
    const val DATE_PICKER_TODAY: String = "add-edit-date-picker-today"
    const val DATE_PICKER_CONFIRM: String = "add-edit-date-picker-confirm"
    const val DATE_PICKER_FUTURE_FLAG: String = "add-edit-date-picker-future-flag"
    const val NAME_FIELD: String = "add-edit-name-field"
    const val NOTE_FIELD: String = "add-edit-note-field"
    const val SAVE_PROBLEM: String = "add-edit-save-problem"
}

/** The tag for the chip of a category, so a test can select one by id. */
fun categoryChipTag(categoryId: String): String = AddEditTestTags.CATEGORY_CHIP_PREFIX + categoryId
