@file:Suppress("all")
package com.srinidhi.kharch.data.repository

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.data.local.CategoryDao
import com.srinidhi.kharch.data.local.CategoryEntity
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.local.SyncState
import com.srinidhi.kharch.domain.expense.CategoryCatalog
import com.srinidhi.kharch.domain.expense.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
 * Phase 5's provisional repository, and the only place the expense UI currently gets data.
 *
 * It is deliberately *local only*. It writes to Room and marks every row pending in the outbox,
 * which is exactly the hand-off point the sync layer (plan §6) is designed to pick up from: a
 * `PENDING_UPSERT` row left here is pushed by Phase 3's coordinator, and nothing in this class
 * needs to change for that to start happening. No Firestore call, no listener, no listener
 * lifecycle — Phase 2/3 own all of that and will replace this binding with the real
 * `ExpenseRepository` when they land.
 *
 * The undo window is the one place that is not a straight pass-through: `softDelete` writes a
 * tombstone and `restore` clears it again, rather than removing the row, because a delete has to
 * be reversible for eight seconds and has to propagate to the user's other devices afterwards
 * (§6.4). Removing the row outright would break both.
 */
class LocalExpenseRepository(
    private val dao: ExpenseDao,
    private val clock: Clock,
) : ExpenseRepository {

    override fun observeAll(): Flow<List<Expense>> =
        dao.observeAllLive().map { rows -> rows.map { row -> row.toDomain() } }

    override fun observeBetween(fromEpochDay: Long, toEpochDayExclusive: Long): Flow<List<Expense>> =
        dao.observeLiveBetween(fromEpochDay, toEpochDayExclusive)
            .map { rows -> rows.map { row -> row.toDomain() } }

    override suspend fun findById(id: String): Expense? = dao.findById(id)?.toDomain()

    override suspend fun recentInCategory(categoryId: String, limit: Int): List<Expense> =
        dao.observeLiveInCategories(listOf(categoryId))
            .first()
            .map { row -> row.toDomain() }
            .take(limit)

    override suspend fun save(expense: Expense) {
        dao.upsert(expense.toEntity())
    }

    override suspend fun softDelete(id: String, deletedAtMillis: Long) {
        dao.softDelete(id, deletedAtMillis, SyncState.PENDING_DELETE)
    }

    /**
     * Clears the tombstone on [id], restoring the row the user just deleted.
     *
     * The row is re-upserted rather than un-deleted by SQL because that keeps the write on the
     * same `upsert` path every other write uses, and `OnConflictStrategy.REPLACE` makes the
     * whole row land again in one statement.
     */
    override suspend fun restore(id: String) {
        val row = dao.findById(id) ?: return
        val revived = row.copy(
            deletedAt = null,
            updatedAt = clock.nowMillis(),
            syncState = SyncState.PENDING_UPSERT,
        )
        dao.upsert(revived)
    }

    /** Re-files one expense under another category, leaving its amount and date untouched. */
    override suspend fun moveToCategory(id: String, categoryId: String, updatedAtMillis: Long) {
        val row = dao.findById(id) ?: return
        dao.upsert(
            row.copy(
                categoryId = categoryId,
                updatedAt = updatedAtMillis,
                syncState = SyncState.PENDING_UPSERT,
            ),
        )
    }
}

/**
 * Phase 5's provisional category read model, backed by the same Room database.
 *
 * Tombstones are dropped in [observeAll] as well as in [observeVisible]: a soft-deleted
 * category must disappear from every surface, including the one that exists to show hidden
 * rows, otherwise the category manager and the Add sheet would disagree about what exists.
 */
class RoomCategoryCatalog(private val dao: CategoryDao) : CategoryCatalog {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.filterAlive().map(CategoryEntity::toDomain) }

    override fun observeVisible(): Flow<List<Category>> =
        dao.observeVisible().map { rows -> rows.filterAlive().map(CategoryEntity::toDomain) }

    private fun List<CategoryEntity>.filterAlive(): List<CategoryEntity> =
        filter { row -> row.deletedAt == null }
}
