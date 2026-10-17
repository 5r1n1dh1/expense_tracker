package com.srinidhi.kharch.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.srinidhi.kharch.data.awaitTask
import com.srinidhi.kharch.data.remote.FirestoreMapper.toMap
import com.srinidhi.kharch.data.remote.dto.CategoryDto
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto
import com.srinidhi.kharch.data.remote.dto.WorkspaceDto
import com.srinidhi.kharch.data.workspace.MembershipStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Firestore implementation of the price-sensitive half of the app.
 *
 * Every decision in this class is a quota decision (plan §6.8, decision D2):
 *
 *  - **Watermark pulls, never listeners.** `where("updatedAt", > watermark)`. A steady-state
 *    sync returns one empty page and costs **0 reads**, not a collection scan.
 *  - **Paged at 400 documents.** A first sync of a 5 000-expense ledger is 13 requests, not
 *    one request carrying 5 000 documents.
 *  - **Batches capped at 400 operations.** Firestore's hard cap is 500; the headroom is
 *    deliberate so a batch split for retry never lands exactly on the limit.
 *  - **No snapshot listener is ever registered**, here or anywhere else in the codebase. The
 *    contract is stated on [RemoteLedger] too, so both sides of the seam say it.
 *
 * Writes are full-document `set()` calls: the map is always complete, so a partial write
 * cannot leave a server-side row missing `amountPaise`.
 */
@Singleton
class FirestoreRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteLedger, MembershipStore {

    // ── Reads ─────────────────────────────────────────────────────────────────

    override suspend fun pullExpenses(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<ExpenseDto> = runCatching {
        expensesSince(workspaceId, afterUpdatedAt, limit).get().awaitTask()
    }
        .map { snapshot -> snapshot.documents.mapNotNull(::readExpense) }
        .getOrElse { failure -> throw failure.toSyncException() }

    override suspend fun pullCategories(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<CategoryDto> = runCatching {
        FirestoreLayout.categories(firestore, workspaceId)
            .whereGreaterThan(FirestoreLayout.FIELD_UPDATED_AT, afterUpdatedAt)
            .orderBy(FirestoreLayout.FIELD_UPDATED_AT, Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .awaitTask()
    }
        .map { snapshot -> snapshot.documents.mapNotNull(::readCategory) }
        .getOrElse { failure -> throw failure.toSyncException() }

    /**
     * Tombstones whose `deletedAt` precedes [deletedBeforeMillis], oldest first.
     *
     * Used only by the sweep, which runs once a day per workspace, and deliberately queries by
     * `deletedAt` rather than by the sync watermark: an expired tombstone's `updatedAt` is
     * far behind the watermark, so a watermark pull would never return it and never clean it.
     *
     * A `whereLessThan` on `deletedAt` already excludes documents where the field is absent —
     * Firestore comparison operators only match documents that have the field — so this is one
     * single-field-index query and needs no composite index (plan §12).
     */
    override suspend fun pullExpiredTombstones(
        workspaceId: String,
        deletedBeforeMillis: Long,
        limit: Int,
    ): List<ExpenseDto> = runCatching {
        FirestoreLayout.expenses(firestore, workspaceId)
            .whereLessThan(FirestoreLayout.FIELD_DELETED_AT, deletedBeforeMillis)
            .orderBy(FirestoreLayout.FIELD_DELETED_AT, Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .awaitTask()
    }
        .map { snapshot -> snapshot.documents.mapNotNull(::readExpense) }
        .getOrElse { failure -> throw failure.toSyncException() }

    /** The category half of [pullExpiredTombstones]; see there for why it queries by `deletedAt`. */
    override suspend fun pullExpiredTombstonedCategories(
        workspaceId: String,
        deletedBeforeMillis: Long,
        limit: Int,
    ): List<CategoryDto> = runCatching {
        FirestoreLayout.categories(firestore, workspaceId)
            .whereLessThan(FirestoreLayout.FIELD_DELETED_AT, deletedBeforeMillis)
            .orderBy(FirestoreLayout.FIELD_DELETED_AT, Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .awaitTask()
    }
        .map { snapshot -> snapshot.documents.mapNotNull(::readCategory) }
        .getOrElse { failure -> throw failure.toSyncException() }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun pushExpenses(workspaceId: String, dtos: List<ExpenseDto>) {
        write(dtos) { batch, dto ->
            batch.set(FirestoreLayout.expense(firestore, workspaceId, dto.id), dto.toMap())
        }
    }

    override suspend fun pushCategories(workspaceId: String, dtos: List<CategoryDto>) {
        write(dtos) { batch, dto ->
            batch.set(FirestoreLayout.category(firestore, workspaceId, dto.id), dto.toMap())
        }
    }

    override suspend fun deleteExpenses(workspaceId: String, ids: List<String>) {
        write(ids) { batch, id ->
            batch.delete(FirestoreLayout.expense(firestore, workspaceId, id))
        }
    }

    override suspend fun deleteCategories(workspaceId: String, ids: List<String>) {
        write(ids) { batch, id ->
            batch.delete(FirestoreLayout.category(firestore, workspaceId, id))
        }
    }

    override suspend fun readMeta(workspaceId: String): SyncMetaDto = runCatching {
        FirestoreLayout.meta(firestore, workspaceId).get().awaitTask()
    }
        .mapCatching { snapshot ->
            if (snapshot.exists()) {
                FirestoreMapper.mapToSyncMetaDto(snapshot.data.orEmpty())
            } else {
                SyncMetaDto()
            }
        }
        .getOrElse { failure -> throw failure.toSyncException() }

    override suspend fun writeMeta(workspaceId: String, meta: SyncMetaDto) {
        runCatching {
            FirestoreLayout.meta(firestore, workspaceId).set(meta.toMap()).awaitTask()
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    // ── Workspace membership (D1) ─────────────────────────────────────────────

    override suspend fun readWorkspace(workspaceId: String): WorkspaceDto? = runCatching {
        FirestoreLayout.workspace(firestore, workspaceId).get().awaitTask()
    }
        .mapCatching { snapshot ->
            if (!snapshot.exists()) {
                null
            } else {
                WorkspaceDto(
                    id = snapshot.id,
                    name = snapshot.getString(FirestoreLayout.FIELD_NAME) ?: WorkspaceDefaults.NAME,
                    memberUids = FirestoreMapper.readMemberUids(snapshot),
                    createdAt = snapshot.getLong(FirestoreLayout.FIELD_CREATED_AT) ?: 0L,
                    schemaVersion = snapshot.getLong(FirestoreLayout.FIELD_SCHEMA_VERSION)?.toInt()
                        ?: WorkspaceDefaults.SCHEMA_VERSION,
                )
            }
        }
        .getOrElse { failure -> throw failure.toSyncException() }

    override suspend fun createWorkspace(document: WorkspaceDto) {
        runCatching {
            firestore.runTransaction { transaction ->
                val reference = FirestoreLayout.workspace(firestore, document.id)
                val existing = transaction.get(reference)
                if (existing.exists()) {
                    // A concurrent creator won the race. Merge rather than overwrite so the
                    // member list that just arrived from the other device is not dropped.
                    val merged = (FirestoreMapper.readMemberUids(existing) + document.memberUids).distinct()
                    transaction.set(reference, document.copy(memberUids = merged).toMap())
                } else {
                    transaction.set(reference, document.toMap())
                }
            }.awaitTask()
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    override suspend fun addMember(workspaceId: String, uid: String) {
        runCatching {
            firestore.runTransaction { transaction ->
                val reference = FirestoreLayout.workspace(firestore, workspaceId)
                val snapshot = transaction.get(reference)
                if (!snapshot.exists()) throw MissingWorkspaceException(workspaceId)
                val members = FirestoreMapper.readMemberUids(snapshot)
                if (uid !in members) {
                    transaction.update(reference, FirestoreLayout.FIELD_MEMBER_UIDS, members + uid)
                }
            }.awaitTask()
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    override suspend fun removeMember(workspaceId: String, uid: String) {
        runCatching {
            firestore.runTransaction { transaction ->
                val reference = FirestoreLayout.workspace(firestore, workspaceId)
                val snapshot = transaction.get(reference)
                if (!snapshot.exists()) throw MissingWorkspaceException(workspaceId)
                val remaining = FirestoreMapper.readMemberUids(snapshot) - uid
                if (remaining.isEmpty()) throw LastMemberException(uid)
                transaction.update(reference, FirestoreLayout.FIELD_MEMBER_UIDS, remaining)
            }.awaitTask()
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun expensesSince(workspaceId: String, afterUpdatedAt: Long, limit: Int): Query =
        FirestoreLayout.expenses(firestore, workspaceId)
            .whereGreaterThan(FirestoreLayout.FIELD_UPDATED_AT, afterUpdatedAt)
            .orderBy(FirestoreLayout.FIELD_UPDATED_AT, Query.Direction.ASCENDING)
            .limit(limit.toLong())

    private fun readExpense(document: DocumentSnapshot): ExpenseDto? =
        FirestoreMapper.mapToExpenseDto(document.id, document.data.orEmpty())

    private fun readCategory(document: DocumentSnapshot): CategoryDto? =
        FirestoreMapper.mapToCategoryDto(document.id, document.data.orEmpty())

    /**
     * Runs [items] through Firestore write batches of at most [MAX_BATCH_OPS].
     *
     * Order is preserved, and the callers hand them oldest-change-first, so an interrupted
     * push leaves a consistent prefix on the server rather than a hole in the middle.
     */
    private suspend fun <T> write(items: List<T>, operation: (WriteBatch, T) -> Unit) {
        if (items.isEmpty()) return
        runCatching {
            items.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { item -> operation(batch, item) }
                batch.commit().awaitTask()
            }
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    companion object {
        /**
         * Page size for pulls: 400 documents per request (plan §6.8).
         *
         * A first sync of a 5 000-expense ledger is therefore 13 requests rather than one
         * request carrying 5 000 documents.
         */
        const val MAX_PAGE_DOCS: Int = 400

        /**
         * Operations per `WriteBatch`: 400, safely under Firestore's hard cap of 500.
         *
         * The headroom is intentional (plan §6.8): it leaves room for a batch that is split
         * for retry to stay under the limit instead of tripping a different error.
         */
        const val MAX_BATCH_OPS: Int = 400
    }
}
