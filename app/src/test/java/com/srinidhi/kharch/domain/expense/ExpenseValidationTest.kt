package com.srinidhi.kharch.domain.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate between a typed draft and a stored [com.srinidhi.kharch.core.model.Expense]
 * (§5.1, §7.2C, §13.2).
 *
 * Every rejection is its own value so the sheet can name the field that caused it; these tests
 * pin which combinations produce which values, including the quick-add sheet's relaxed rule.
 */
class ExpenseValidationTest {

    @Test
    fun `a fully populated draft is valid`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.ofPaise(12_500L),
            categoryId = "sys_food",
        )

        assertEquals(ExpenseDraftValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `a blank name is required by default`() {
        val result = ExpenseDraftRules.validate(
            name = "   ",
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.NAME_REQUIRED))
        assertFalse(result.isValid)
    }

    @Test
    fun `an empty name is required by default`() {
        val result = ExpenseDraftRules.validate(
            name = "",
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.NAME_REQUIRED))
    }

    @Test
    fun `a blank name is allowed for the quick-add sheet`() {
        val result = ExpenseDraftRules.validate(
            name = "",
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
            nameRequired = false,
        )

        assertFalse(result.has(ExpenseDraftError.NAME_REQUIRED))
        assertTrue(result.isValid)
    }

    @Test
    fun `a typed name in quick-add is still length-checked`() {
        val tooLong = "x".repeat(ExpenseDraftRules.NAME_MAX_LENGTH + 1)
        val result = ExpenseDraftRules.validate(
            name = tooLong,
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
            nameRequired = false,
        )

        assertTrue(result.has(ExpenseDraftError.NAME_TOO_LONG))
        assertFalse(result.has(ExpenseDraftError.NAME_REQUIRED))
    }

    @Test
    fun `a name longer than the limit is rejected`() {
        val tooLong = "x".repeat(ExpenseDraftRules.NAME_MAX_LENGTH + 1)
        val result = ExpenseDraftRules.validate(
            name = tooLong,
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.NAME_TOO_LONG))
        assertFalse(result.has(ExpenseDraftError.NAME_REQUIRED))
    }

    @Test
    fun `a name exactly at the limit is accepted`() {
        val exact = "x".repeat(ExpenseDraftRules.NAME_MAX_LENGTH)
        val result = ExpenseDraftRules.validate(
            name = exact,
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertFalse(result.has(ExpenseDraftError.NAME_TOO_LONG))
        assertTrue(result.isValid)
    }

    @Test
    fun `length is measured after trimming`() {
        val padded = "   ${"x".repeat(ExpenseDraftRules.NAME_MAX_LENGTH)}   "
        val result = ExpenseDraftRules.validate(
            name = padded,
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `whitespace around a name is trimmed before the length check`() {
        val paddedTooLong = "   ${"x".repeat(ExpenseDraftRules.NAME_MAX_LENGTH + 1)}   "
        val result = ExpenseDraftRules.validate(
            name = paddedTooLong,
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.NAME_TOO_LONG))
    }

    @Test
    fun `a zero amount is required`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.EMPTY,
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.AMOUNT_REQUIRED))
    }

    @Test
    fun `an amount of exactly zero paise is required`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.ofPaise(0L),
            categoryId = "sys_food",
        )

        assertTrue(result.has(ExpenseDraftError.AMOUNT_REQUIRED))
    }

    @Test
    fun `a non-zero amount is accepted`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.ofPaise(1L),
            categoryId = "sys_food",
        )

        assertFalse(result.has(ExpenseDraftError.AMOUNT_REQUIRED))
    }

    @Test
    fun `a missing category is required`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.ofPaise(100L),
            categoryId = null,
        )

        assertTrue(result.has(ExpenseDraftError.CATEGORY_REQUIRED))
    }

    @Test
    fun `a blank category is required`() {
        val result = ExpenseDraftRules.validate(
            name = "Lunch",
            amount = AmountBuffer.ofPaise(100L),
            categoryId = "   ",
        )

        assertTrue(result.has(ExpenseDraftError.CATEGORY_REQUIRED))
    }

    @Test
    fun `every problem is reported at once`() {
        val result = ExpenseDraftRules.validate(
            name = "",
            amount = AmountBuffer.EMPTY,
            categoryId = null,
        )

        assertTrue(result.has(ExpenseDraftError.NAME_REQUIRED))
        assertTrue(result.has(ExpenseDraftError.AMOUNT_REQUIRED))
        assertTrue(result.has(ExpenseDraftError.CATEGORY_REQUIRED))
        assertEquals(3, result.errors.size)
    }

    @Test
    fun `validation errors are a set so duplicates collapse`() {
        val validation = ExpenseDraftValidation(
            setOf(ExpenseDraftError.AMOUNT_REQUIRED, ExpenseDraftError.AMOUNT_REQUIRED),
        )

        assertEquals(1, validation.errors.size)
        assertFalse(validation.isValid)
    }

    @Test
    fun `valid validation has an empty error set`() {
        assertEquals(emptySet<ExpenseDraftError>(), ExpenseDraftValidation.Valid.errors)
        assertTrue(ExpenseDraftValidation.Valid.isValid)
    }

    @Test
    fun `has returns false for an absent error`() {
        assertFalse(ExpenseDraftValidation.Valid.has(ExpenseDraftError.NAME_REQUIRED))
    }

    @Test
    fun `name max length constant is sixty`() {
        assertEquals(60, ExpenseDraftRules.NAME_MAX_LENGTH)
    }
}
EOF
