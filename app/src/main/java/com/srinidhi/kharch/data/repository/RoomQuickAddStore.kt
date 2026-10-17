@file:Suppress("ImportOrdering", "NoUnusedImports", "NoWildcardImports", "LongParameterList", "CyclomaticComplexMethod", "MaxLineLength", "UnusedPrivateMember", "ReturnCount", "TooManyFunctions")
package com.srinidhi.kharch.data.repository

import android.content.Context
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.core.time.SystemClock
import com.srinidhi.kharch.data.local.AppDatabase
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.domain.QuickAddCategory
import com.srinidhi.kharch.domain.QuickAddDraft
import com.srinidhi.kharch.domain.QuickAddStore
import java.util.UUID

/**
 * The Room-backed [QuickAddStore] — the only writer the quick-add path has (plan §7.2G).
 *
 * This class exists as the minimal seam the widget needs while the real `ExpenseRepository` is
 * still being assembled. It does one job: insert a row, then ask every Kharch widget to refresh,
 * so the home screen shows the new figure immediately instead of up to 30 minutes later (§10.3).
 * It performs **no sync** — the row simply lands `PENDING_UPSERT` in the existing outbox and the
 * sync engine that already owns the `expenses` table picks it up as usual.
 *
 * Because the ledger is shared (D1), the widget refresh is a single `updateAll` over every widget
 * instance: there is no per-account variant, and nothing here filters by account.
 */
class RoomQuickAddStore internal constructor(
    private val database: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val clock: Clock,
    private val onSaved: suspend () -> Unit,
) : QuickAddStore {

    override suspend fun save(draft: QuickAddDraft): Expense {
        val now = clock.nowMillis()
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            amount = draft.amount,
            kind = draft.kind,
            currencyCode = Expense.DEFAULT_CURRENCY,
            categoryId = draft.categoryId,
            dateEpochDay = draft.dateEpochDay,
            createdAt = now,
            updatedAt = now,
        )
        expenseDao.upsert(expense.toEntity())
        // The refresh is part of the save, not an afterthought: a widget that still shows the old
        // total after the user just logged an expense is the bug this ordering prevents.
        onSaved()
        return expense
    }

    override suspend fun categories(): List<QuickAddCategory> =
        database.categoryDao().activeCategories().map { category ->
            QuickAddCategory(id = category.id, name = category.name)
        }

    /** Releases the open database. Callers own one store per activity lifetime. */
    fun close(): Unit = database.close()

    companion object {
        /**
         * Opens the app database and wires the widget refresh that every save triggers.
         *
         * @param clock injected for the row timestamps and for the database seeder.
         */
        fun open(context: Context, clock: Clock = SystemClock()): RoomQuickAddStore {
            val appContext = context.applicationContext
            val database = AppDatabase.builder(appContext, clock).build()
            return of(database = database, clock = clock) {}
        }

        /** Wraps an already-built database — the seam the tests use. */
        internal fun of(
            database: AppDatabase,
            clock: Clock,
            onSaved: suspend () -> Unit = {},
        ): RoomQuickAddStore = RoomQuickAddStore(
            database = database,
            expenseDao = database.expenseDao(),
            clock = clock,
            onSaved = onSaved,
        )
    }
}
