package com.srinidhi.kharch.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Outbox DAO — tracks pending sync items (plan §6.3).
 *
 * Mirrors [ExpenseDao.pendingForSync] semantics but exposes a unified view across
 * both expenses and categories. For the initial implementation, expenses are the
 * primary outbox item; categories follow the same pattern.
 */
@Dao
abstract class OutboxDao {
    @Query("SELECT COUNT(*) FROM expenses WHERE syncState = 'PENDING_UPSERT'")
    abstract suspend fun pendingUpsertCount(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE syncState = 'PENDING_DELETE'")
    abstract suspend fun pendingDeleteCount(): Int

    @Query(
        """
        SELECT * FROM expenses
        WHERE syncState = 'PENDING_UPSERT'
        ORDER BY updatedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun queryPendingUpserts(limit: Int): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE syncState = 'PENDING_DELETE'
        ORDER BY updatedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun queryPendingDeletes(limit: Int): List<ExpenseEntity>

    @Query("UPDATE expenses SET syncState = 'SYNCED' WHERE id IN (:ids)")
    abstract suspend fun markSynced(ids: List<String>)
}