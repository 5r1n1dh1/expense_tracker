package com.srinidhi.kharch.core.money

import com.srinidhi.kharch.core.model.Expense
import java.io.Writer
import java.time.LocalDate

/**
 * Writes expenses to CSV for Excel and Google Sheets (plan §18.4).
 *
 * Two details matter more than they look:
 *
 *  1. **The file starts with a UTF-8 BOM.** Without it Excel on Windows assumes the system
 *     codepage and renders `₹` as mojibake, and mangles Indian names. Three invisible bytes
 *     (EF BB BF) are the whole fix.
 *  2. **The amount column is the unsigned magnitude with two decimals**, and direction lives in
 *     the separate `kind` column (D5) — exactly as it is stored. A refund is therefore
 *     `500.00,REFUND`, not `-500.00`, so re-importing never has to guess a sign.
 *
 * Rows are ordered by date, then creation time, then id, so exporting the same ledger twice
 * produces byte-identical output.
 *
 * @property includeDeleted when `false` (the default) tombstoned rows are left out of the export.
 */
class CsvExporter(private val includeDeleted: Boolean = false) {
    /**
     * Renders [expenses] as a complete CSV document, BOM included.
     *
     * @param categoryNames id to display name, so the sheet is readable without the app. Ids that
     *   are absent from the map fall back to the raw id, never to an empty cell.
     */
    fun toCsv(
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(BYTE_ORDER_MARK)
        append(HEADER.joinToString(COMMA))
        append(LINE_FEED)
        selectedRows(expenses).forEach { expense ->
            append(rowOf(expense, categoryNames).joinToString(COMMA) { cell -> escape(cell) })
            append(LINE_FEED)
        }
    }

    /** [toCsv] encoded as UTF-8. The BOM becomes the three bytes `EF BB BF`. */
    fun toByteArray(
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
    ): ByteArray = toCsv(expenses, categoryNames).toByteArray(Charsets.UTF_8)

    /** Streams [toCsv] into [writer]; the caller owns and closes it. */
    fun write(
        writer: Writer,
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
    ) {
        writer.write(toCsv(expenses, categoryNames))
    }

    private fun selectedRows(expenses: List<Expense>): List<Expense> = expenses
        .filter { includeDeleted || it.deletedAt == null }
        .sortedWith(compareBy(Expense::dateEpochDay, Expense::createdAt, Expense::id))

    private fun rowOf(expense: Expense, categoryNames: Map<String, String>): List<String> = listOf(
        expense.id,
        LocalDate.ofEpochDay(expense.dateEpochDay).toString(),
        expense.name,
        categoryNames[expense.categoryId] ?: expense.categoryId,
        expense.kind.name,
        expense.amount.toRupees().toPlainString(),
        expense.note.orEmpty(),
        expense.linkedExpenseId.orEmpty(),
        expense.createdAt.toString(),
        expense.updatedAt.toString(),
    )

    /** RFC 4180 quoting: wrap on a comma, quote, newline or a stray edge space; double quotes. */
    private fun escape(value: String): String {
        val needsQuoting = value.any { it in RESERVED_CHARACTERS } || value != value.trim()
        if (!needsQuoting) {
            return value
        }
        val doubled = value.replace(QUOTE, ESCAPED_QUOTE)
        return QUOTE + doubled + QUOTE
    }

    companion object {
        /** U+FEFF, written as the first characters of every export so Excel reads UTF-8. */
        const val BYTE_ORDER_MARK: String = "\uFEFF"

        /** The column order, verbatim from plan §18.4. */
        val HEADER: List<String> = listOf(
            "id",
            "date",
            "name",
            "category",
            "kind",
            "amount_inr",
            "note",
            "linked_expense_id",
            "created_at",
            "updated_at",
        )

        private const val COMMA: String = ","
        private const val LINE_FEED: String = "\n"
        private const val QUOTE: String = "\""
        private const val ESCAPED_QUOTE: String = "\"\""
        private val RESERVED_CHARACTERS: Set<Char> = setOf(',', '"', '\n', '\r')
    }
}
