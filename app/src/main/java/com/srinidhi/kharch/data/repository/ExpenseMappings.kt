package com.srinidhi.kharch.data.repository

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.data.local.ExpenseEntity
import com.srinidhi.kharch.data.local.SyncState

/**
 * The `expenses` row as the domain sees it.
 *
 * Tombstones map across as tombstones, so a deleted row never silently reappears as live (plan
 * §6.4). `syncState` is deliberately dropped here: it is a local outbox concern, not a fact about
 * the money.
 */
fun ExpenseEntity.toExpense(): Expense = Expense(
    id = id,
    name = name,
    amountPaise = amountPaise,
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
 * The domain row as the `expenses` table stores it.
 *
 * Every write starts `PENDING_UPSERT`, which is what puts it in the outbox for sync. A row that is
 * already tombstoned keeps its tombstone when it is re-mapped, so a delete can never be undone by
 * round-tripping.
 */
fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
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
    syncState = SyncState.PENDING_UPSERT,
)
