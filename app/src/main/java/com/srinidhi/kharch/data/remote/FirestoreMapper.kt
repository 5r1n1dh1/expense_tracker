@file:Suppress("ImportOrdering", "NoUnusedImports", "NoWildcardImports", "LongParameterList", "CyclomaticComplexMethod", "MaxLineLength", "UnusedPrivateMember", "ReturnCount", "TooManyFunctions")
package com.srinidhi.kharch.data.remote

import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.google.firebase.firestore.DocumentSnapshot
import com.srinidhi.kharch.data.local.CategoryEntity
import com.srinidhi.kharch.data.local.ExpenseEntity
import com.srinidhi.kharch.data.local.SyncState
import com.srinidhi.kharch.data.remote.dto.CategoryDto
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto
import com.srinidhi.kharch.data.remote.dto.WorkspaceDto
import kotlin.math.max

/**
 * The boundary between Room and Firestore.
 *
 * Everything here is a pure function over `Map<String, Any?>` and the DTO/entity types, which
 * is what makes the mapping itself unit-testable on a plain JVM with no Firebase initialised
 * (plan §13.2). A malformed remote document is **dropped**, not coerced: a document with a
 * missing `amountPaise` or a `kind` outside `EXPENSE|REFUND` is a corrupted row, and silently
 * guessing at it would put a wrong number into someone's ledger.
 */
object FirestoreMapper {

    // ── Expenses ──────────────────────────────────────────────────────────────

    /** Room → wire. `syncState` is a local outbox flag and is intentionally not written. */
    fun ExpenseEntity.toExpenseDto(authorUid: String): ExpenseDto = ExpenseDto(
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
        authorUid = authorUid,
    )

    /**
     * Wire → Room.
     *
     * @param syncState which outbox state the row lands in. A pulled row always arrives
     *   already-confirmed, so the caller passes [SyncState.SYNCED] unless the local row is
     *   still pending, in which case the pending flag is deliberately preserved.
     */
    fun ExpenseDto.toEntity(syncState: SyncState): ExpenseEntity = ExpenseEntity(
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
        syncState = syncState,
    )

    /** The document body, ready for `WriteBatch.set()`. */
    fun ExpenseDto.toMap(): Map<String, Any?> = mapOf(
        FirestoreLayout.FIELD_ID to id,
        FirestoreLayout.FIELD_NAME to name,
        FirestoreLayout.FIELD_AMOUNT_PAISE to amountPaise,
        FirestoreLayout.FIELD_KIND to kind.name,
        FirestoreLayout.FIELD_CURRENCY_CODE to currencyCode,
        FirestoreLayout.FIELD_CATEGORY_ID to categoryId,
        FirestoreLayout.FIELD_DATE_EPOCH_DAY to dateEpochDay,
        FirestoreLayout.FIELD_NOTE to note,
        FirestoreLayout.FIELD_LINKED_EXPENSE_ID to linkedExpenseId,
        FirestoreLayout.FIELD_CREATED_AT to createdAt,
        FirestoreLayout.FIELD_UPDATED_AT to updatedAt,
        FirestoreLayout.FIELD_DELETED_AT to deletedAt,
        FirestoreLayout.FIELD_AUTHOR_UID to authorUid,
    )

    /**
     * Reads an expense document back, or `null` when it does not round-trip.
     *
     * Every field is validated rather than defaulted, because a missing `name` or a
     * nonsensical `dateEpochDay` is a corrupted document and must not become a live expense.
     */
    @Suppress("CyclomaticComplexMethod")
    fun mapToExpenseDto(id: String, fields: Map<String, Any?>): ExpenseDto? {
        val name = fields[FirestoreLayout.FIELD_NAME] as? String ?: return null
        val amountPaise = fields[FirestoreLayout.FIELD_AMOUNT_PAISE] as? Long ?: return null
        val kindName = fields[FirestoreLayout.FIELD_KIND] as? String ?: return null
        val kind = ExpenseKind.entries.firstOrNull { it.name == kindName } ?: return null
        val categoryId = fields[FirestoreLayout.FIELD_CATEGORY_ID] as? String ?: return null
        val dateEpochDay = fields[FirestoreLayout.FIELD_DATE_EPOCH_DAY] as? Long ?: return null
        val createdAt = fields[FirestoreLayout.FIELD_CREATED_AT] as? Long ?: return null
        val updatedAt = fields[FirestoreLayout.FIELD_UPDATED_AT] as? Long ?: return null
        val storedId = fields[FirestoreLayout.FIELD_ID] as? String
        return ExpenseDto(
            // The document id wins over a stored id: they can only diverge if the writer
            // put the wrong one there, and the path is what every other device addresses.
            id = storedId ?: id,
            name = name,
            amountPaise = amountPaise,
            kind = kind,
            currencyCode = fields[FirestoreLayout.FIELD_CURRENCY_CODE] as? String ?: Expense.DEFAULT_CURRENCY,
            categoryId = categoryId,
            dateEpochDay = dateEpochDay,
            note = fields[FirestoreLayout.FIELD_NOTE] as? String,
            linkedExpenseId = fields[FirestoreLayout.FIELD_LINKED_EXPENSE_ID] as? String,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = fields[FirestoreLayout.FIELD_DELETED_AT] as? Long,
            authorUid = fields[FirestoreLayout.FIELD_AUTHOR_UID] as? String ?: "",
        )
    }

    // ── Categories ────────────────────────────────────────────────────────────

    /** Room → wire. */
    fun CategoryEntity.toCategoryDto(): CategoryDto = CategoryDto(
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

    /** Wire → Room. Categories carry no outbox flag; a pulled row is confirmed. */
    fun CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
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

    /** The document body, ready for `WriteBatch.set()`. */
    fun CategoryDto.toMap(): Map<String, Any?> = mapOf(
        FirestoreLayout.FIELD_ID to id,
        FirestoreLayout.FIELD_NAME to name,
        FirestoreLayout.FIELD_ICON_KEY to iconKey,
        FirestoreLayout.FIELD_COLOR_TOKEN to colorToken,
        FirestoreLayout.FIELD_SORT_ORDER to sortOrder,
        FirestoreLayout.FIELD_IS_SYSTEM to isSystem,
        FirestoreLayout.FIELD_IS_HIDDEN to isHidden,
        FirestoreLayout.FIELD_DELETED_AT to deletedAt,
        FirestoreLayout.FIELD_UPDATED_AT to updatedAt,
    )

    /** Reads a category document back, or `null` when it does not round-trip. */
    fun mapToCategoryDto(id: String, fields: Map<String, Any?>): CategoryDto? {
        val name = fields[FirestoreLayout.FIELD_NAME] as? String ?: return null
        val iconKey = fields[FirestoreLayout.FIELD_ICON_KEY] as? String ?: return null
        val colorToken = fields[FirestoreLayout.FIELD_COLOR_TOKEN] as? String ?: return null
        val updatedAt = fields[FirestoreLayout.FIELD_UPDATED_AT] as? Long ?: return null
        val storedId = fields[FirestoreLayout.FIELD_ID] as? String
        return CategoryDto(
            id = storedId ?: id,
            name = name,
            iconKey = iconKey,
            colorToken = colorToken,
            sortOrder = (fields[FirestoreLayout.FIELD_SORT_ORDER] as? Long)?.toInt() ?: 0,
            isSystem = fields[FirestoreLayout.FIELD_IS_SYSTEM] as? Boolean ?: false,
            isHidden = fields[FirestoreLayout.FIELD_IS_HIDDEN] as? Boolean ?: false,
            deletedAt = fields[FirestoreLayout.FIELD_DELETED_AT] as? Long,
            updatedAt = updatedAt,
        )
    }

    /**
     * Reads the workspace document's member list.
     *
     * Filtered to strings rather than cast: a coerced or truncated list would silently lock
     * the user out of their own ledger, which is the one failure this must not have.
     */
    fun readMemberUids(snapshot: DocumentSnapshot): List<String> =
        (snapshot.get(FirestoreLayout.FIELD_MEMBER_UIDS) as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()

    // ── Workspace document ────────────────────────────────────────────────────

    /** The workspace document body, ready for a transactional `set()`. */
    fun WorkspaceDto.toMap(): Map<String, Any?> = mapOf(
        FirestoreLayout.FIELD_ID to id,
        FirestoreLayout.FIELD_NAME to name,
        FirestoreLayout.FIELD_MEMBER_UIDS to memberUids,
        FirestoreLayout.FIELD_CREATED_AT to createdAt,
        FirestoreLayout.FIELD_SCHEMA_VERSION to schemaVersion,
    )

    /** The highest `updatedAt` seen in [dtos]; `0L` when the page is empty. */
    fun latestCategoryUpdatedAt(dtos: List<CategoryDto>): Long =
        dtos.fold(0L) { highest, dto -> max(highest, dto.updatedAt) }

    // ── Sync meta ─────────────────────────────────────────────────────────────

    fun SyncMetaDto.toMap(): Map<String, Any?> = mapOf(
        FirestoreLayout.FIELD_LAST_PULLED_AT to lastPulledAt,
        FirestoreLayout.FIELD_LAST_PUSH_AT to lastPushAt,
        FirestoreLayout.FIELD_LAST_CATEGORY_PUSH_AT to lastCategoryPushAt,
        FirestoreLayout.FIELD_LAST_SWEEP_AT to lastSweepAt,
        FirestoreLayout.FIELD_LAST_SYNC_ERROR to lastSyncError,
    )

    /**
     * Reads the meta document back.
     *
     * Unknown fields are ignored rather than rejected: this document is a free-form record of
     * sync bookkeeping, so a field written by a newer build must not stop a current one from
     * syncing — and a missing one falls back to "pull everything", which is always safe.
     */
    fun mapToSyncMetaDto(fields: Map<String, Any?>): SyncMetaDto = SyncMetaDto(
        lastPulledAt = fields[FirestoreLayout.FIELD_LAST_PULLED_AT] as? Long ?: 0L,
        lastPushAt = fields[FirestoreLayout.FIELD_LAST_PUSH_AT] as? Long ?: 0L,
        lastCategoryPushAt = fields[FirestoreLayout.FIELD_LAST_CATEGORY_PUSH_AT] as? Long ?: 0L,
        lastSweepAt = fields[FirestoreLayout.FIELD_LAST_SWEEP_AT] as? Long ?: 0L,
        lastSyncError = fields[FirestoreLayout.FIELD_LAST_SYNC_ERROR] as? String,
    )

    /**
     * The domain twin of a wire expense, carrying the author metadata Firestore adds.
     *
     * The magnitude is wrapped on read, and [MagnitudePaise] rejects a negative, so a
     * corrupted negative `amountPaise` throws here rather than becoming a live expense — the
     * same "crash loudly, never store a wrong number" rule the Room converters follow.
     */
    fun ExpenseDto.toExpense(): Expense = Expense(
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

    /** The highest `updatedAt` seen in [dtos]; `0L` when the page is empty. */
    fun latestUpdatedAt(dtos: List<ExpenseDto>): Long =
        dtos.fold(0L) { highest, dto -> max(highest, dto.updatedAt) }
}
