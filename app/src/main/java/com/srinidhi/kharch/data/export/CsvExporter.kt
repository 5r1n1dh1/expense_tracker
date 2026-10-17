package com.srinidhi.kharch.data.export

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.money.CsvExporter as CoreCsvExporter
import java.io.Writer

/**
 * CSV export (plan §18.4), exposing the same document format as
 * [com.srinidhi.kharch.core.money.CsvExporter] with an optional date-range filter.
 */
class CsvExporter(private val includeDeleted: Boolean = false) {

    private val delegate: CoreCsvExporter = CoreCsvExporter(includeDeleted = includeDeleted)

    fun toCsv(
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
    ): String {
        val filtered = expenses.filter { expense ->
            (includeDeleted || expense.deletedAt == null) &&
                (fromEpochDay == null || expense.dateEpochDay >= fromEpochDay) &&
                (toEpochDayExclusive == null ||
                    expense.dateEpochDay < toEpochDayExclusive)
        }
        return delegate.toCsv(filtered, categoryNames)
    }

    fun toByteArray(
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
    ): ByteArray = toCsv(
        expenses = expenses,
        categoryNames = categoryNames,
        fromEpochDay = fromEpochDay,
        toEpochDayExclusive = toEpochDayExclusive,
    ).toByteArray(Charsets.UTF_8)

    fun write(
        writer: Writer,
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
    ) {
        writer.write(
            toCsv(
                expenses = expenses,
                categoryNames = categoryNames,
                fromEpochDay = fromEpochDay,
                toEpochDayExclusive = toEpochDayExclusive,
            ),
        )
    }

    fun export(
        expenses: List<Expense>,
        categoryNames: Map<String, String> = emptyMap(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
    ): String = toCsv(
        expenses = expenses,
        categoryNames = categoryNames,
        fromEpochDay = fromEpochDay,
        toEpochDayExclusive = toEpochDayExclusive,
    )

    companion object {
        const val BYTE_ORDER_MARK: String = CoreCsvExporter.BYTE_ORDER_MARK
        val HEADER: List<String> = CoreCsvExporter.HEADER
    }
}
