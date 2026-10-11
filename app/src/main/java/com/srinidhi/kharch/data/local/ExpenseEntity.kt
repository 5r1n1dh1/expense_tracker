package com.srinidhi.kharch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.srinidhi.kharch.core.model.ExpenseKind

/**
 * The persisted twin of `core.model.Expense` (plan §5.1).
 *
 * Mirrors the Firestore document at `workspaces/{wid}/expenses/{id}` one-for-one, plus the
 * local-only [syncState] outbox flag.
 *
 * Invariants held here and asserted by tests:
 *  - [amountPaise] is a **non-negative magnitude**; direction lives in [kind] (D5). This is the
 *    same rule the Firestore security rules enforce server-side.
 *  - `Money.isCredit` is equivalent to `kind == REFUND`.
 *  - [linkedExpenseId], when set, points at the expense a refund reverses. Deleting that
 *    original never cascades into the refund — the money still came back.
 *
 * @property dateEpochDay `LocalDate.toEpochDay()`; indexed because every read is a date range.
 * @property createdAt epoch millis, UTC.
 * @property deletedAt tombstone timestamp. Non-null means deleted, but the row is retained so
 *   the delete can propagate to other devices (plan §6.4).
 */
@Entity(
    tableName = "expenses",
    indices = [Index("dateEpochDay"), Index("categoryId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountPaise: Long,
    val kind: ExpenseKind,
    @ColumnInfo(defaultValue = "INR") val currencyCode: String = "INR",
    val categoryId: String,
    val dateEpochDay: Long,
    val note: String? = null,
    val linkedExpenseId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** New rows have never reached the server, so they start pending. */
    @ColumnInfo(defaultValue = "PENDING_UPSERT")
    val syncState: SyncState = SyncState.PENDING_UPSERT,
)
