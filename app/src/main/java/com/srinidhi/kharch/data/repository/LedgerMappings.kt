package com.srinidhi.kharch.data.repository

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.data.local.CategoryEntity
import com.srinidhi.kharch.data.local.ExpenseEntity
import com.srinidhi.kharch.data.local.SyncState

/*
 * The only place the persisted twins (`data.local`) and the clean domain models
 * (`core.model`) are translated into each other.
 *
 * The two shapes are deliberately kept apart: `ExpenseEntity` carries `syncState`, which is a
 * persistence concern the domain must never see, and `Expense` carries a typed
 * `MagnitudePaise`, which is a money invariant the table cannot express. Everything the
 * translation has to get right lives here, in one file, where a test can read it.
 */

/** Room row to domain model. */
fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    name = name,
    amount = MagnitudePaise(amountPaise),
    kind = kind,
    currencyCode = currencyCode,
    categoryId = categoryId,
    dateEpochDay = dateEpochDay,
    note = note,
    linkedExpenseId = linkedExpenseId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * Domain model to Room row.
 *
 * [syncState] defaults to [SyncState.PENDING_UPSERT] because every write the expense UI makes is
 * a local change the sync layer has not seen yet. A caller that is writing a row it just pulled
 * from the server passes [SyncState.SYNCED] explicitly.
 */
fun Expense.toEntity(syncState: SyncState = SyncState.PENDING_UPSERT): ExpenseEntity = ExpenseEntity(
    id = id,
    name = name,
    amountPaise = amount.paise,
    kind = kind,
    currencyCode = currencyCode,
    categoryId = categoryId,
    dateEpochDay = dateEpochDay,
    note = note,
    linkedExpenseId = linkedExpenseId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState,
)

/** Room row to domain model. Tombstones are filtered out by the caller, not here. */
fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconKey = iconKey,
    colorToken = colorToken,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isHidden = isHidden,
    updatedAt = updatedAt,
)

/** Domain model to Room row. */
fun Category.toEntity(updatedAt: Long, deletedAt: Long? = null): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorToken = colorToken,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isHidden = isHidden,
    deletedAt = deletedAt,
    updatedAt = updatedAt,
)
