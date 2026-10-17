package com.srinidhi.kharch.data.export

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Writer

/**
 * Lossless JSON backup format (plan §18.4). Versioned so an importer can reject or migrate
 * an export written by a newer app.
 */
@Serializable
data class JsonExpense(
    val id: String,
    val name: String,
    val amountPaise: Long,
    val kind: String,
    val currencyCode: String,
    val categoryId: String,
    val dateEpochDay: Long,
    val note: String? = null,
    val linkedExpenseId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class JsonCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorToken: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    val isHidden: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class JsonExport(
    val schemaVersion: Int = JsonExporter.SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val expenses: List<JsonExpense> = emptyList(),
    val categories: List<JsonCategory> = emptyList(),
)

/**
 * Writes the ledger to a versioned JSON document. Pure: no Android, no storage, no network.
 * The caller owns the destination (plan §18.4 — `ACTION_CREATE_DOCUMENT`).
 */
class JsonExporter(private val includeDeleted: Boolean = false) {

    fun toJson(
        expenses: List<Expense>,
        categories: List<Category> = emptyList(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
        exportedAt: Long = 0L,
    ): String {
        val selectedExpenses = expenses
            .filter { includeDeleted || it.deletedAt == null }
            .filter { fromEpochDay == null || it.dateEpochDay >= fromEpochDay }
            .filter { toEpochDayExclusive == null || it.dateEpochDay < toEpochDayExclusive }
            .sortedWith(compareBy(Expense::dateEpochDay, Expense::createdAt, Expense::id))
        val selectedCategories = categories
            .sortedWith(compareBy(Category::sortOrder, Category::name, Category::id))
        val document = JsonExport(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = exportedAt,
            expenses = selectedExpenses.map { expense -> expense.toJson() },
            categories = selectedCategories.map { category -> category.toJson() },
        )
        return JSON.encodeToString(document)
    }

    fun toByteArray(
        expenses: List<Expense>,
        categories: List<Category> = emptyList(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
        exportedAt: Long = 0L,
    ): ByteArray = toJson(
        expenses = expenses,
        categories = categories,
        fromEpochDay = fromEpochDay,
        toEpochDayExclusive = toEpochDayExclusive,
        exportedAt = exportedAt,
    ).toByteArray(Charsets.UTF_8)

    fun write(
        writer: Writer,
        expenses: List<Expense>,
        categories: List<Category> = emptyList(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
        exportedAt: Long = 0L,
    ) {
        writer.write(
            toJson(
                expenses = expenses,
                categories = categories,
                fromEpochDay = fromEpochDay,
                toEpochDayExclusive = toEpochDayExclusive,
                exportedAt = exportedAt,
            ),
        )
    }

    fun export(
        expenses: List<Expense>,
        categories: List<Category> = emptyList(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
        exportedAt: Long = 0L,
    ): String = toJson(
        expenses = expenses,
        categories = categories,
        fromEpochDay = fromEpochDay,
        toEpochDayExclusive = toEpochDayExclusive,
        exportedAt = exportedAt,
    )

    fun exportJson(
        expenses: List<Expense>,
        categories: List<Category> = emptyList(),
        fromEpochDay: Long? = null,
        toEpochDayExclusive: Long? = null,
        exportedAt: Long = 0L,
    ): String = toJson(
        expenses = expenses,
        categories = categories,
        fromEpochDay = fromEpochDay,
        toEpochDayExclusive = toEpochDayExclusive,
        exportedAt = exportedAt,
    )

    private fun Expense.toJson(): JsonExpense = JsonExpense(
        id = id,
        name = name,
        amountPaise = amount.paise,
        kind = kind.name,
        currencyCode = currencyCode,
        categoryId = categoryId,
        dateEpochDay = dateEpochDay,
        note = note,
        linkedExpenseId = linkedExpenseId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun Category.toJson(): JsonCategory = JsonCategory(
        id = id,
        name = name,
        iconKey = iconKey,
        colorToken = colorToken,
        sortOrder = sortOrder,
        isSystem = isSystem,
        isHidden = isHidden,

    )

    companion object {
        const val SCHEMA_VERSION: Int = 1

        private val JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
