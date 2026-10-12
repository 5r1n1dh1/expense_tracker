package com.srinidhi.kharch.data.remote.dto

import com.srinidhi.kharch.core.model.ExpenseKind

/**
 * `workspaces/{wid}/expenses/{id}` exactly as plan §6.5 specifies it.
 *
 * Deliberately a **plain Kotlin data class** and not a Firestore-mapped POJO: the conversion
 * to and from `Map<String, Any?>` is written by hand in `FirestoreMapper`. Firestore's
 * reflective POJO mapper silently writes `null` for any field it cannot reach, and a null
 * `amountPaise` on a financial record is exactly the kind of bug that never gets noticed.
 * Hand mapping turns every field into an explicit decision.
 *
 * There is no `syncState` here: that is the local outbox flag and never reaches the server.
 * `authorUid` is the one field the plan adds that Room does not store.
 */
data class ExpenseDto(
    val id: String,
    val name: String,
    val amountPaise: Long,
    val kind: ExpenseKind,
    val currencyCode: String,
    val categoryId: String,
    val dateEpochDay: Long,
    val note: String? = null,
    val linkedExpenseId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val authorUid: String = "",
)
