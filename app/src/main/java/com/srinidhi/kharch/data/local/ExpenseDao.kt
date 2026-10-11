package com.srinidhi.kharch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.srinidhi.kharch.core.model.ExpenseKind
import kotlinx.coroutines.flow.Flow

/**
 * Per-day signed totals, already split by direction.
 *
 * Returned by [ExpenseDao.observeDailyNet]. [netPaise] is `expensePaise - refundPaise` and can
 * legitimately be negative (D5).
 */
data class DailyNet(
    val epochDay: Long,
    val expensePaise: Long,
    val refundPaise: Long,
    val netPaise: Long,
)

/**
 * Every read and write of the `expenses` table.
 *
 * Declared as an **abstract class** rather than an interface so it can hold `@Transaction`
 * helpers later while staying under the `TooManyFunctions` threshold detekt applies to
 * interfaces.
 *
 * Range convention: date ranges are **half-open**, `>= fromEpochDay AND < toEpochDayExclusive`.
 * That lines up exactly with `YearMonth.endEpochDayExclusive`, so consecutive months chain
 * without an off-by-one at the boundary. Result ordering is newest-first.
 *
 * Every list query that is not explicitly a sync query filters out tombstones, so a deleted
 * expense can never leak into a total. Every aggregate is done in SQL with `Long` arithmetic —
 * no `Double` ever touches an amount.
 */
@Dao
abstract class ExpenseDao {
    /** Insert or replace. The id is a UUID, so a replace only ever happens on a re-push. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ExpenseEntity)

    /** Bulk insert used by sync and by JSON import. One transaction, one round trip. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<ExpenseEntity>)

    /** Hard delete, used only by the tombstone sweep. Normal deletes go through [softDelete]. */
    @Query("DELETE FROM expenses WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    abstract fun observeById(id: String): Flow<ExpenseEntity?>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAt IS NULL
        ORDER BY dateEpochDay DESC, createdAt DESC
        """,
    )
    abstract fun observeAllLive(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAt IS NULL
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
        ORDER BY dateEpochDay DESC, createdAt DESC
        """,
    )
    abstract fun observeLiveBetween(fromEpochDay: Long, toEpochDayExclusive: Long): Flow<List<ExpenseEntity>>

    /** Multi-select category filter. [categoryIds] must not be empty. */
    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAt IS NULL
          AND categoryId IN (:categoryIds)
        ORDER BY dateEpochDay DESC, createdAt DESC
        """,
    )
    abstract fun observeLiveInCategories(categoryIds: List<String>): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAt IS NULL
          AND categoryId = :categoryId
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
        ORDER BY dateEpochDay DESC, createdAt DESC
        """,
    )
    abstract fun observeLiveForCategoryBetween(
        categoryId: String,
        fromEpochDay: Long,
        toEpochDayExclusive: Long,
    ): Flow<List<ExpenseEntity>>

    /**
     * The next page of rows to push, oldest change first.
     *
     * [states] must not be empty. Ordering by `updatedAt` means the oldest edit is pushed first,
     * so if a push is interrupted the prefix that already landed stays consistent.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE syncState IN (:states)
        ORDER BY updatedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun pendingForSync(states: List<SyncState>, limit: Int): List<ExpenseEntity>

    /** Drives the "Offline — N pending" badge (§6.7). [states] must not be empty. */
    @Query("SELECT COUNT(*) FROM expenses WHERE syncState IN (:states)")
    abstract fun observePendingCount(states: List<SyncState>): Flow<Int>

    /** Marks a batch after the server accepts it. [ids] must not be empty. */
    @Query("UPDATE expenses SET syncState = :state WHERE id IN (:ids)")
    abstract suspend fun setSyncStates(ids: List<String>, state: SyncState)

    /** A delete is a tombstone so it can propagate to other devices (plan §6.4). */
    @Query(
        """
        UPDATE expenses
        SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncState = :syncState
        WHERE id = :id
        """,
    )
    abstract suspend fun softDelete(id: String, deletedAt: Long, syncState: SyncState)

    /** The client-side tombstone sweep (plan §6.4). Firestore TTL is Blaze-only, so we sweep. */
    @Query("DELETE FROM expenses WHERE deletedAt IS NOT NULL AND deletedAt <= :olderThanMillis")
    abstract suspend fun purgeTombstones(olderThanMillis: Long): Int

    /** Reclassifying a category never touches the amount or the date, only the classification. */
    @Query(
        """
        UPDATE expenses
        SET categoryId = :toCategoryId, updatedAt = :updatedAt, syncState = :syncState
        WHERE categoryId = :fromCategoryId AND deletedAt IS NULL
        """,
    )
    abstract suspend fun reassignCategory(
        fromCategoryId: String,
        toCategoryId: String,
        updatedAt: Long,
        syncState: SyncState,
    ): Int

    /** Gross spend for a range and one direction, summed in SQL to avoid a table scan. */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM expenses
        WHERE deletedAt IS NULL
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
          AND kind = :kind
        """,
    )
    abstract suspend fun sumPaise(
        fromEpochDay: Long,
        toEpochDayExclusive: Long,
        kind: ExpenseKind,
    ): Long

    /** Charges minus refunds over a range. Negative when refunds outweigh spend (D5). */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = :expenseKind THEN amountPaise ELSE -amountPaise END), 0)
        FROM expenses
        WHERE deletedAt IS NULL
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
        """,
    )
    abstract suspend fun netPaise(
        fromEpochDay: Long,
        toEpochDayExclusive: Long,
        expenseKind: ExpenseKind,
    ): Long

    /** Reactive net for a range — used by the widget, which must never load the rows. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = :expenseKind THEN amountPaise ELSE -amountPaise END), 0)
        FROM expenses
        WHERE deletedAt IS NULL
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
        """,
    )
    abstract fun observeNetPaise(
        fromEpochDay: Long,
        toEpochDayExclusive: Long,
        expenseKind: ExpenseKind,
    ): Flow<Long>

    /**
     * Per-day net totals for a range, grouped in SQL.
     *
     * The dashboard still aggregates in Kotlin on top of this (plan §9) so month buckets stay a
     * pure function of the days, but summing here avoids shipping every row across the cursor.
     */
    @Query(
        """
        SELECT
          dateEpochDay AS epochDay,
          COALESCE(SUM(CASE WHEN kind = :expenseKind THEN amountPaise ELSE 0 END), 0) AS expensePaise,
          COALESCE(SUM(CASE WHEN kind = :refundKind THEN amountPaise ELSE 0 END), 0) AS refundPaise,
          COALESCE(SUM(CASE WHEN kind = :expenseKind THEN amountPaise ELSE -amountPaise END), 0) AS netPaise
        FROM expenses
        WHERE deletedAt IS NULL
          AND dateEpochDay >= :fromEpochDay
          AND dateEpochDay < :toEpochDayExclusive
        GROUP BY dateEpochDay
        ORDER BY dateEpochDay ASC
        """,
    )
    abstract fun observeDailyNet(
        fromEpochDay: Long,
        toEpochDayExclusive: Long,
        expenseKind: ExpenseKind,
        refundKind: ExpenseKind,
    ): Flow<List<DailyNet>>
}
