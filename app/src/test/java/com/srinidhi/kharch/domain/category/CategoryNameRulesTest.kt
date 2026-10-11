package com.srinidhi.kharch.domain.category

import com.srinidhi.kharch.domain.seededCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Naming rules for a user-owned category (§5.2, §13.2).
 *
 * "Rename rejects empty and >40-char names, and case-insensitive duplicates" — plus the two
 * cases that decide whether the rule is annoying or correct: a category must be allowed to
 * keep its own name, and a hidden category must stop reserving it.
 */
class CategoryNameRulesTest {

    private val seeded = seededCategories()

    @Test
    fun `a fresh unused name is accepted and trimmed`() {
        val result = CategoryRules.validateName(seeded, null, "  Household  ")

        assertEquals(NameValidation.Valid("Household"), result)
    }

    @Test
    fun `an empty or whitespace only name is rejected`() {
        assertEquals(
            NameValidation.Invalid(NameRejection.BLANK),
            CategoryRules.validateName(seeded, null, "   "),
        )
        assertEquals(
            NameValidation.Invalid(NameRejection.BLANK),
            CategoryRules.validateName(seeded, null, ""),
        )
    }

    @Test
    fun `a name longer than forty characters is rejected`() {
        val exactly = "x".repeat(CategoryRules.NAME_MAX_LENGTH)
        val tooLong = "x".repeat(CategoryRules.NAME_MAX_LENGTH + 1)

        assertTrue(CategoryRules.validateName(seeded, null, exactly) is NameValidation.Valid)
        assertEquals(
            NameValidation.Invalid(NameRejection.TOO_LONG),
            CategoryRules.validateName(seeded, null, tooLong),
        )
    }

    @Test
    fun `length is measured after trimming`() {
        val padded = "   ${"x".repeat(CategoryRules.NAME_MAX_LENGTH)}   "

        assertTrue(CategoryRules.validateName(seeded, null, padded) is NameValidation.Valid)
    }

    @Test
    fun `a duplicate name differing only by case is rejected`() {
        val result = CategoryRules.validateName(seeded, "sys_gifts", "food & drink")

        assertEquals(NameValidation.Invalid(NameRejection.DUPLICATE_NAME), result)
    }

    @Test
    fun `a category may keep the name it already has`() {
        val result = CategoryRules.validateName(seeded, "sys_food", "Food & Drink")

        assertTrue(result is NameValidation.Valid)
    }

    @Test
    fun `a hidden category frees its name for reuse`() {
        val withHiddenFood = seeded.map { row ->
            if (row.id == "sys_food") row.copy(isHidden = true) else row
        }

        val result = CategoryRules.validateName(withHiddenFood, "sys_gifts", "Food & Drink")

        assertTrue(result is NameValidation.Valid)
    }

    @Test
    fun `a duplicate name is still refused against a hidden sibling`() {
        val withHiddenFood = seeded.map { row ->
            if (row.id == "sys_food") row.copy(isHidden = true) else row
        }

        val result = CategoryRules.validateName(withHiddenFood, null, "Food & Drink")

        assertEquals(NameValidation.Invalid(NameRejection.DUPLICATE_NAME), result)
    }

    @Test
    fun `renaming returns the category with the trimmed name`() {
        val result = CategoryRules.rename(seeded, seeded.first(), "  Eating Out  ")

        check(result is RenameResult.Renamed)
        assertEquals("Eating Out", result.category.name)
        assertEquals("sys_food", result.category.id)
    }

    @Test
    fun `a refused rename leaves the category untouched`() {
        val result = CategoryRules.rename(seeded, seeded.first(), "transport")

        assertEquals(RenameResult.Refused(NameRejection.DUPLICATE_NAME), result)
    }
}
