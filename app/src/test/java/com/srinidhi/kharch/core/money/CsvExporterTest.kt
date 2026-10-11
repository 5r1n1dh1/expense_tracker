package com.srinidhi.kharch.core.money

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDate

/**
 * CSV export (plan §18.4). The BOM is the small detail that matters most in practice: without
 * it Excel renders `₹` as mojibake and mangles Indian names.
 */
class CsvExporterTest {
    @Test
    fun `the document starts with a utf-8 byte order mark`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense()))

        assertTrue(csv.startsWith(CsvExporter.BYTE_ORDER_MARK))
        assertEquals('\uFEFF', csv.first())
    }

    @Test
    fun `the encoded bytes start with the utf-8 encoding of the byte order mark`() {
        val bytes = CsvExporter().toByteArray(listOf(sampleExpense()))

        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }

    @Test
    fun `the header row lists the documented columns in order`() {
        val csv = CsvExporter().toCsv(emptyList())
        val header = csv.removePrefix(CsvExporter.BYTE_ORDER_MARK).substringBefore('\n')

        assertEquals(
            "id,date,name,category,kind,amount_inr,note,linked_expense_id,created_at,updated_at",
            header,
        )
    }

    @Test
    fun `every row has one cell per header column`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense()))
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        assertEquals(CsvExporter.HEADER.size, lines[1].split(',').size)
    }

    @Test
    fun `the amount column carries two decimals with no grouping and no symbol`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(amountPaise = 12_345_678L)))

        assertTrue(csv.contains(",123456.78,"))
    }

    @Test
    fun `the date column is iso formatted`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(dateEpochDay = LocalDate.of(2026, 8, 12).toEpochDay())))

        assertTrue(csv.contains(",2026-08-12,"))
    }

    @Test
    fun `the category column uses the display name when one is known`() {
        val csv = CsvExporter()
            .toCsv(listOf(sampleExpense(categoryId = "sys_food")), mapOf("sys_food" to "Food & Drink"))

        assertTrue(csv.contains(",Food & Drink,"))
    }

    @Test
    fun `an unknown category id falls back to the id rather than an empty cell`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(categoryId = "custom")))

        assertTrue(csv.contains(",custom,"))
    }

    @Test
    fun `a refund row keeps a positive magnitude and records the refund kind`() {
        val refund = sampleExpense(kind = ExpenseKind.REFUND, linkedExpenseId = "exp-original")
        val row = CsvExporter().toCsv(listOf(refund)).trim().lines()[1]

        assertTrue(row.contains(",REFUND,"))
        assertTrue(row.contains(",500.00,"))
        assertTrue(row.contains(",exp-original,"))
        assertFalse(row.contains(",-"))
    }

    @Test
    fun `a null note and link become empty cells`() {
        val row = CsvExporter().toCsv(listOf(sampleExpense())).trim().lines()[1]

        assertTrue(row.endsWith(",1700000000000,1700000000000"))
        assertEquals(CsvExporter.HEADER.size, row.split(',').size)
    }

    @Test
    fun `a value containing a comma is quoted`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(name = "Tea, samosa")))

        assertTrue(csv.contains("\"Tea, samosa\""))
    }

    @Test
    fun `a value containing quotes is quoted and doubled`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(name = "The \"big\" one")))

        assertTrue(csv.contains("\"The \"\"big\"\" one\""))
    }

    @Test
    fun `a value with leading or trailing space is quoted`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(name = " Padded ")))

        assertTrue(csv.contains("\" Padded \""))
    }

    @Test
    fun `a value containing a newline is quoted`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(note = "line one\nline two")))

        assertTrue(csv.contains("\"line one\nline two\""))
    }

    @Test
    fun `a rupee amount survives the export unescaped`() {
        val csv = CsvExporter().toCsv(listOf(sampleExpense(name = "Chai ₹20")))

        assertTrue(csv.contains("Chai ₹20"))
    }

    @Test
    fun `tombstoned expenses are excluded by default`() {
        val deleted = sampleExpense(id = "exp-deleted", deletedAt = 1L)

        assertFalse(CsvExporter().toCsv(listOf(deleted)).contains("exp-deleted"))
    }

    @Test
    fun `tombstoned expenses can be included when explicitly asked for`() {
        val deleted = sampleExpense(id = "exp-deleted", deletedAt = 1L)

        assertTrue(CsvExporter(includeDeleted = true).toCsv(listOf(deleted)).contains("exp-deleted"))
    }

    @Test
    fun `rows are ordered by date then creation time so exports are reproducible`() {
        val later = sampleExpense(id = "b", dateEpochDay = LocalDate.of(2026, 8, 13).toEpochDay())
        val earlier = sampleExpense(id = "a", dateEpochDay = LocalDate.of(2026, 8, 12).toEpochDay())

        val ids = CsvExporter().toCsv(listOf(later, earlier)).trim().lines().drop(1).map { it.substringBefore(',') }

        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun `writing to a writer produces the same document`() {
        val writer = StringWriter()
        CsvExporter().write(writer, listOf(sampleExpense()))

        assertEquals(CsvExporter().toCsv(listOf(sampleExpense())), writer.toString())
    }

    @Test
    fun `an empty ledger still produces a header`() {
        val csv = CsvExporter().toCsv(emptyList())

        assertEquals(1, csv.trim().lines().size)
        assertTrue(csv.endsWith("\n"))
    }

    private fun sampleExpense(
        id: String = "exp-1",
        name: String = "Filter coffee",
        amountPaise: Long = 500_00L,
        kind: ExpenseKind = ExpenseKind.EXPENSE,
        categoryId: String = "sys_food",
        dateEpochDay: Long = LocalDate.of(2026, 8, 12).toEpochDay(),
        note: String? = null,
        linkedExpenseId: String? = null,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L,
        deletedAt: Long? = null,
    ) = Expense(
        id = id,
        name = name,
        amount = MagnitudePaise(amountPaise),
        kind = kind,
        categoryId = categoryId,
        dateEpochDay = dateEpochDay,
        note = note,
        linkedExpenseId = linkedExpenseId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
