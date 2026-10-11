package com.srinidhi.kharch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A category together with how many live expenses still reference it.
 *
 * Drives the "N expenses" label and the delete-vs-hide decision in the category manager: a
 * category may only be deleted when [usageCount] is zero (D4).
 */
data class CategoryUsage(
    val id: String,
    val name: String,
    val usageCount: Int,
)

/**
 * Every read and write of the `categories` table (decision D4).
 *
 * An abstract class rather than an interface, so [applyOrder] can be a real `@Transaction`
 * method: a drag-to-reorder writes contiguous `sortOrder` values inside one transaction,
 * which is also what lets the sync layer turn the whole reorder into a **single** batched
 * Firestore write (plan §5.2, §6.8).
 *
 * Inserts use `OnConflictStrategy.IGNORE`, which is what makes re-seeding idempotent.
 */
@Dao
abstract class CategoryDao {
    /** Inserts only when the id is new. Returns `-1` when the row already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(category: CategoryEntity): Long

    /** Bulk insert used by [DatabaseSeeder]'s programmatic path and by import. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAllIfAbsent(categories: List<CategoryEntity>): List<Long>

    /** Every row, tombstones included — the sync pull needs those. */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    abstract fun observeAll(): Flow<List<CategoryEntity>>

    /** What the Add sheet and the filter chips offer. */
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    abstract fun observeVisible(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    abstract suspend fun findByIds(ids: List<String>): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    abstract suspend fun activeCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    abstract suspend fun countAll(): Int

    /** `-1` when there are no categories yet, so the first user category lands at `sortOrder 0`. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE deletedAt IS NULL")
    abstract suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId AND deletedAt IS NULL")
    abstract suspend fun usageCount(categoryId: String): Int

    @Update
    abstract suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setName(id: String, name: String, updatedAt: Long)

    /** Hiding keeps history renderable; it is the alternative to deleting a category in use. */
    @Query("UPDATE categories SET isHidden = :isHidden, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setHidden(id: String, isHidden: Boolean, updatedAt: Long)

    @Query("UPDATE categories SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    protected abstract suspend fun writeSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    /** Soft delete, the only kind the app performs on a category (never in use). */
    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    abstract suspend fun softDelete(id: String, deletedAt: Long)

    /** Physical delete, only ever for a category with `usageCount == 0`. */
    @Query("DELETE FROM categories WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query(
        """
        SELECT c.id AS id, c.name AS name, COUNT(e.id) AS usageCount
        FROM categories c
        LEFT JOIN expenses e ON e.categoryId = c.id AND e.deletedAt IS NULL
        WHERE c.deletedAt IS NULL
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.name ASC
        """,
    )
    abstract fun observeUsageCounts(): Flow<List<CategoryUsage>>

    /**
     * Renumbers [orderedIds] to `0..n-1` in one transaction, oldest-known position first.
     *
     * Batching matters: 14 separate writes would become 14 Firestore writes against a 20 000
     * per-day Spark quota, whereas one transaction becomes one batched write (§6.8).
     */
    @Transaction
    open suspend fun applyOrder(orderedIds: List<String>, updatedAt: Long) {
        orderedIds.forEachIndexed { index, id -> writeSortOrder(id, index, updatedAt) }
    }

    /**
     * Case-insensitive duplicate check for rename and create (plan §5.2: uniqueness is enforced
     * case-insensitively against visible categories).
     *
     * SQLite's `lower()` folds ASCII only, which would let `"Café"` and `"CAFÉ"` both through,
     * so the comparison happens in Kotlin with Unicode-aware case folding. The category list is
     * tiny — a couple of dozen rows — so this costs nothing.
     *
     * @return the conflicting category, or `null` when [name] is free.
     */
    suspend fun findByNameIgnoringCase(name: String, excludeId: String): CategoryEntity? {
        val target = name.trim()
        return activeCategories().firstOrNull { category ->
            category.id != excludeId && category.name.trim().equals(target, ignoreCase = true)
        }
    }
}
