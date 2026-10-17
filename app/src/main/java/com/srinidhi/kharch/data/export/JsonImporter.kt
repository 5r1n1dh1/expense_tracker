package com.srinidhi.kharch.data.export

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Reads a Kharch JSON export back into domain models (plan §18.4).
 */
enum class ConflictStrategy {
    SKIP,
    OVERWRITE,
    MERGE,
}

data class ImportPreview(
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictCount: Int = 0,
) {
    val new: Int get() = newCount
    val updated: Int get() = updatedCount
    val skipped: Int get() = skippedCount
    val conflicts: Int get() = conflictCount
}

data class ImportResult(
    val expenses: List<Expense>,
    val categories: List<Category>,
    val preview: ImportPreview,
    val upsertedExpenses: List<Expense> = emptyList(),
    val upsertedCategories: List<Category> = emptyList(),
)

class JsonImporter {

    fun parse(json: String): JsonExport {
        val parsed = PARSER.decodeFromString<JsonExport>(json)
        require(parsed.schemaVersion == JsonExporter.SCHEMA_VERSION) {
            "Unsupported export schema version: ${parsed.schemaVersion}"
        }
        return parsed
    }

    fun toExpenses(parsed: JsonExport): List<Expense> =
        parsed.expenses.map { entry -> entry.toExpense() }

    fun toCategories(parsed: JsonExport): List<Category> =
        parsed.categories.map { entry -> entry.toCategory() }

    fun preview(
        json: String,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
        strategy: ConflictStrategy = ConflictStrategy.MERGE,
    ): ImportPreview = preview(
        parsed = parse(json),
        existingExpenses = existingExpenses,
        existingCategories = existingCategories,
        strategy = strategy,
    )

    fun preview(
        parsed: JsonExport,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
        strategy: ConflictStrategy = ConflictStrategy.MERGE,
    ): ImportPreview {
        val expensePreview = computePreview(
            imported = toExpenses(parsed),
            existing = existingExpenses,
            strategy = strategy,
        )
        return expensePreview
    }

    fun import(
        json: String,
        strategy: ConflictStrategy,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
    ): ImportResult = import(
        parsed = parse(json),
        strategy = strategy,
        existingExpenses = existingExpenses,
        existingCategories = existingCategories,
    )

    fun import(
        parsed: JsonExport,
        strategy: ConflictStrategy,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
    ): ImportResult {
        val importedExpenses = toExpenses(parsed)
        val importedCategories = toCategories(parsed)
        val preview = computePreview(
            imported = importedExpenses,
            existing = existingExpenses,
            strategy = strategy,
        )
        val resolvedExpenses = mergeList(
            existing = existingExpenses,
            imported = importedExpenses,
            strategy = strategy,
            idOf = { expense -> expense.id },
        ).sortedWith(compareBy(Expense::dateEpochDay, Expense::createdAt, Expense::id))
        val resolvedCategories = mergeList(
            existing = existingCategories,
            imported = importedCategories,
            strategy = strategy,
            idOf = { category -> category.id },
        ).sortedWith(compareBy(Category::sortOrder, Category::name, Category::id))
        val upsertedExpenses = importedExpenses.filter { imported ->
            val existing = existingExpenses.firstOrNull { it.id == imported.id }
            existing == null || shouldUpdate(existing, imported, strategy)
        }
        val upsertedCategories = importedCategories.filter { imported ->
            val existing = existingCategories.firstOrNull { it.id == imported.id }
            existing == null || shouldUpdateCategory(existing, imported, strategy)
        }
        return ImportResult(
            expenses = resolvedExpenses,
            categories = resolvedCategories,
            preview = preview,
            upsertedExpenses = upsertedExpenses,
            upsertedCategories = upsertedCategories,
        )
    }

    fun apply(
        json: String,
        strategy: ConflictStrategy,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
    ): ImportResult = import(
        json = json,
        strategy = strategy,
        existingExpenses = existingExpenses,
        existingCategories = existingCategories,
    )

    fun applyImport(
        json: String,
        strategy: ConflictStrategy,
        existingExpenses: List<Expense> = emptyList(),
        existingCategories: List<Category> = emptyList(),
    ): ImportResult = import(
        json = json,
        strategy = strategy,
        existingExpenses = existingExpenses,
        existingCategories = existingCategories,
    )

    fun importExpenses(
        json: String,
        strategy: ConflictStrategy,
        existingExpenses: List<Expense> = emptyList(),
    ): List<Expense> = import(
        json = json,
        strategy = strategy,
        existingExpenses = existingExpenses,
    ).expenses

    private fun computePreview(
        imported: List<Expense>,
        existing: List<Expense>,
        strategy: ConflictStrategy,
    ): ImportPreview {
        val existingById = existing.associateBy { expense -> expense.id }
        var newCount = 0
        var conflictCount = 0
        var updatedCount = 0
        var skippedCount = 0
        for (importedExpense in imported) {
            val existingExpense = existingById[importedExpense.id]
            if (existingExpense == null) {
                newCount++
            } else {
                conflictCount++
                val willUpdate = shouldUpdate(existingExpense, importedExpense, strategy)
                if (willUpdate) {
                    updatedCount++
                } else {
                    skippedCount++
                }
            }
        }
        return ImportPreview(
            newCount = newCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            conflictCount = conflictCount,
        )
    }

    private fun shouldUpdate(
        existing: Expense,
        imported: Expense,
        strategy: ConflictStrategy,
    ): Boolean = when (strategy) {
        ConflictStrategy.SKIP -> false
        ConflictStrategy.OVERWRITE -> true
        ConflictStrategy.MERGE -> imported.updatedAt > existing.updatedAt
    }

    private fun shouldUpdateCategory(
        existing: Category,
        imported: Category,
        strategy: ConflictStrategy,
    ): Boolean = when (strategy) {
        ConflictStrategy.SKIP -> false
        ConflictStrategy.OVERWRITE -> true
        ConflictStrategy.MERGE -> imported.updatedAt > existing.updatedAt
    }

    private fun <T> mergeList(
        existing: List<T>,
        imported: List<T>,
        strategy: ConflictStrategy,
        idOf: (T) -> String,
    ): List<T> {
        val result = existing.associateBy(idOf).toMutableMap()
        for (importedItem in imported) {
            val id = idOf(importedItem)
            val existingItem = result[id]
            if (existingItem == null) {
                result[id] = importedItem
            } else {
                val update = when (strategy) {
                    ConflictStrategy.SKIP -> false
                    ConflictStrategy.OVERWRITE -> true
                    ConflictStrategy.MERGE -> run {
                        val importedUpdatedAt = (importedItem as? Expense)?.updatedAt
                            ?: (importedItem as? Category)?.updatedAt ?: 0L
                        val existingUpdatedAt = (existingItem as? Expense)?.updatedAt
                            ?: (existingItem as? Category)?.updatedAt ?: 0L
                        importedUpdatedAt > existingUpdatedAt
                    }
                }
                if (update) {
                    result[id] = importedItem
                }
            }
        }
        return result.values.toList()
    }

    private fun JsonExpense.toExpense(): Expense = Expense(
        id = id,
        name = name,
        amount = MagnitudePaise(amountPaise),
        kind = ExpenseKind.valueOf(kind),
        currencyCode = currencyCode,
        categoryId = categoryId,
        dateEpochDay = dateEpochDay,
        note = note,
        linkedExpenseId = linkedExpenseId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun JsonCategory.toCategory(): Category = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        colorToken = colorToken,
        sortOrder = sortOrder,
        isSystem = isSystem,
        isHidden = isHidden,
    )

    companion object {
        private val PARSER = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
