package com.srinidhi.kharch.domain.category

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.domain.category
import com.srinidhi.kharch.domain.date
import com.srinidhi.kharch.domain.expense
import com.srinidhi.kharch.domain.seededCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering, hiding and deletion of categories (D4, §5.2, §13.2).
 *
 * The rule that protects the user's data is the delete one: a category that expenses still
 * point at is hidden, never deleted, and nothing is ever silently reassigned.
 */
class CategoryOrderRulesTest {

    private val seeded = seededCategories()

    @Test
    fun `reorder produces contiguous sort orders from zero`() {
        val order = listOf("sys_cash", "sys_food", "sys_rent", "sys_bills")

        val result = CategoryRules.reorder(
            listOf(
                category("sys_food", "Food & Drink", sortOrder = 0),
                category("sys_bills", "Bills & Utilities", sortOrder = 1),
                category("sys_rent", "Rent", sortOrder = 2),
                category("sys_cash", "Cash", sortOrder = 3),
            ),
            order,
        )

        check(result is ReorderResult.Applied)
        val sortOrders = result.sortOrders
        assertEquals(listOf(0, 1, 2, 3), order.map { id -> sortOrders.getValue(id) })
        assertEquals(order.size, sortOrders.size)
    }

    @Test
    fun `a reorder of every category travels as one map for one batched write`() {
        val ids = seeded.shuffled().map { row -> row.id }

        val result = CategoryRules.reorder(seeded, ids)

        check(result is ReorderResult.Applied)
        // One payload for the caller to send as a single Firestore batch (§6.8).
        assertEquals(seeded.size, result.sortOrders.size)
        assertEquals((0 until seeded.size).toList(), result.sortOrders.values.toList())
    }

    @Test
    fun `reorder reports how many rows actually change`() {
        val rows = listOf(
            category("a", "A", sortOrder = 0),
            category("b", "B", sortOrder = 1),
            category("c", "C", sortOrder = 2),
        )

        val unchanged = CategoryRules.reorder(rows, listOf("a", "b", "c"))
        val swapped = CategoryRules.reorder(rows, listOf("c", "b", "a"))

        check(unchanged is ReorderResult.Applied)
        check(swapped is ReorderResult.Applied)
        assertEquals(0, unchanged.changedCount)
        assertEquals(2, swapped.changedCount)
    }

    @Test
    fun `reorder rejects an order that omits a category`() {
        val result = CategoryRules.reorder(seeded, listOf("sys_food", "sys_rent"))

        assertEquals(ReorderResult.Rejected(ReorderRejection.MISSING_IDS), result)
    }

    @Test
    fun `reorder rejects an order naming a category that does not exist`() {
        val order = seeded.map { row -> row.id } + "sys_ghost"

        val result = CategoryRules.reorder(seeded, order)

        assertEquals(ReorderResult.Rejected(ReorderRejection.UNKNOWN_IDS), result)
    }

    @Test
    fun `reorder rejects an order that repeats a category`() {
        val order = seeded.map { row -> row.id }.dropLast(1) + "sys_food"

        val result = CategoryRules.reorder(seeded, order)

        assertEquals(ReorderResult.Rejected(ReorderRejection.DUPLICATE_IDS), result)
    }

    @Test
    fun `reordered returns renumbered rows or nothing when the order is invalid`() {
        val rows = listOf(
            category("a", "A", sortOrder = 0),
            category("b", "B", sortOrder = 1),
        )

        val renumbered = CategoryRules.reordered(rows, listOf("b", "a"))
        val refused = CategoryRules.reordered(rows, listOf("a"))

        assertEquals(listOf(1, 0), renumbered?.map { row -> row.sortOrder })
        assertNull(refused)
    }

    @Test
    fun `hiding keeps the row so historical expenses still render it`() {
        val hidden = CategoryRules.setHidden(seeded.first(), hidden = true)
        val withHiddenFood = listOf(hidden) + seeded.drop(1)

        assertTrue(hidden.isHidden)
        assertEquals("sys_food", hidden.id)
        assertEquals(seeded.size - 1, CategoryRules.visibleCategories(withHiddenFood).size)
        // Still resolvable for a historical expense — hiding is not deletion.
        assertEquals(hidden, withHiddenFood.first { row -> row.id == "sys_food" })
    }

    @Test
    fun `showing a hidden category again puts it back in order`() {
        val hidden = CategoryRules.setHidden(seeded.first(), hidden = true)
        val withHiddenFood = listOf(hidden) + seeded.drop(1)

        val shown = CategoryRules.setHidden(hidden, hidden = false)
        val withShownFood = listOf(shown) + seeded.drop(1)

        assertFalse(shown.isHidden)
        assertEquals(seeded.size, CategoryRules.visibleCategories(withShownFood).size)
    }

    @Test
    fun `an unused user category may be deleted`() {
        val spare = category("user_mortgage", "Mortgage", sortOrder = 3)

        assertEquals(DeleteDecision.Allowed, CategoryRules.deleteDecision(spare, usageCount = 0))
    }

    @Test
    fun `a category in use is hidden instead of deleted`() {
        val decision = CategoryRules.deleteDecision(seeded.first(), usageCount = 4)

        assertEquals(DeleteDecision.HideInstead(DeleteRejection.IN_USE), decision)
    }

    @Test
    fun `a seeded category is never deleted`() {
        val decision = CategoryRules.deleteDecision(seeded.first(), usageCount = 0)

        assertEquals(DeleteDecision.Rejected(DeleteRejection.SYSTEM_CATEGORY), decision)
    }

    @Test
    fun `usage counts ignore deleted expenses`() {
        val expenses = listOf(
            expense("a", date(2026, 4, 2), 100, categoryId = "sys_food"),
            expense("b", date(2026, 4, 3), 200, categoryId = "sys_food"),
            expense("c", date(2026, 4, 4), 300, categoryId = "sys_rent"),
            expense("d", date(2026, 4, 5), 400, categoryId = "sys_rent", deletedAt = 1L),
            expense("e", date(2026, 4, 6), 500, categoryId = "sys_cash", kind = ExpenseKind.REFUND),
        )

        val counts = CategoryRules.usageCounts(expenses)

        assertEquals(2, counts.getValue("sys_food"))
        assertEquals(1, counts.getValue("sys_rent"))
        assertEquals(1, counts.getValue("sys_cash"))
    }

    @Test
    fun `a new category takes the sort order after the current maximum`() {
        assertEquals(0, CategoryRules.nextSortOrder(emptyList()))
        assertEquals(13, CategoryRules.nextSortOrder(seeded))
    }
}
