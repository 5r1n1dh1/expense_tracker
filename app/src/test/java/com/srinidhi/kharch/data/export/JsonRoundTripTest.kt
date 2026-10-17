package com.srinidhi.kharch.data.export

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.domain.category
import com.srinidhi.kharch.domain.date
import com.srinidhi.kharch.domain.expense
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Export -> wipe -> import is byte-identical (plan §13.1, P2).
 */
class JsonRoundTripTest {

    @Test
    fun `export then import round trips byte identical`() {
        val expenses = listOf(
            expense("e1", date(2026, 8, 10), 500L),
            expense("e2", date(2026, 8, 11), 250L, kind = ExpenseKind.REFUND),
        )
        val categories = listOf(
            category("c1", "Food", sortOrder = 0),
        )
        val exporter = JsonExporter(includeDeleted = true)
        val original = exporter.toByteArray(
            expenses = expenses,
            categories = categories,
            exportedAt = 42L,
        )
        val importer = JsonImporter()
        val parsed = importer.parse(String(original, Charsets.UTF_8))
        val reExported = exporter.toByteArray(
            expenses = importer.toExpenses(parsed),
            categories = importer.toCategories(parsed),
            exportedAt = 42L,
        )

        assertEquals(original.toList(), reExported.toList())
    }

    @Test
    fun `round trip preserves a deleted tombstone`() {
        val expenses = listOf(
            expense("e1", date(2026, 8, 10), 500L, deletedAt = 999L),
        )
        val exporter = JsonExporter(includeDeleted = true)
        val original = exporter.toJson(expenses = expenses, exportedAt = 7L)
        val parsed = JsonImporter().parse(original)
        val reExported = exporter.toJson(
            expenses = JsonImporter().toExpenses(parsed),
            exportedAt = 7L,
        )

        assertEquals(original, reExported)
    }

    @Test
    fun `empty ledger round trips`() {
        val exporter = JsonExporter()
        val original = exporter.toJson(expenses = emptyList(), categories = emptyList(), exportedAt = 0L)
        val parsed = JsonImporter().parse(original)
        val reExported = exporter.toJson(
            expenses = JsonImporter().toExpenses(parsed),
            categories = JsonImporter().toCategories(parsed),
            exportedAt = 0L,
        )

        assertEquals(original, reExported)
    }
}
