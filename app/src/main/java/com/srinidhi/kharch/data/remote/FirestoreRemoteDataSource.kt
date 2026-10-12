package com.srinidhi.kharch.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.data.awaitTask
import com.srinidhi.kharch.data.remote.dto.CategoryDto
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto
import com.srinidhi.kharch.data.remote.dto.WorkspaceDto
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
 *    one request that pulls 5 000 documents in a single payload.
 *  - **Batches capped at 400 operations.** Firestore's hard cap is 500; the headroom is
 *    deliberate so a batch split for retry never lands exactly on the limit.
 *  - **No snapshot listener is ever registered**, here or anywhere else in the codebase. The
 *    contract is stated on [RemoteLedger] too, so both sides of the seam say it.
 *
 * Writes are full-document `set()` calls: the map is always complete, so a partial write
 * cannot leave a server-side row missing `amountPaise`.
 *
 * @param clock supplies the tombstone timestamp for [eraseLedger]. Injected so a delete is a
 *   decision the caller owns, never a bare `System.currentTimeMillis()`.
 */
@Singleton
class FirestoreRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : RemoteLedger {

    override suspend fun pullExpenses(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<ExpenseDto> = runCatching {
        expensesQuery(workspaceId, afterUpdatedAt, limit).get().awaitTask()
    }
        .map { snapshot ->
            snapshot.documents.mapNotNull { document ->
                FirestoreMapper.mapToExpenseDto(document.id, document.data.orEmpty())
            }
        }
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
        .map { snapshot ->
            snapshot.documents.mapNotNull { document ->
                FirestoreMapper.mapToCategoryDto(document.id, document.data.orEmpty())
            }
        }
        .getOrElse { failure -> throw failure.toSyncException() }

    override suspend fun pullTombstones(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<ExpenseDto> = runCatching {
        // A tombstone is an expense with `deletedAt` set. It is pulled by `updatedAt`, so a
        // delete made offline on one device still reaches every other device (§6.4.1).
        expensesQuery(workspaceId, afterUpdatedAt, limit)
            .whereNotNull(FirestoreLayout.FIELD_DELETED_AT)
            .get()
            .awaitTask()
    }
        .map { snapshot ->
            snapshot.documents.mapNotNull { document ->
                FirestoreMapper.mapToExpenseDto(document.id, document.data.orEmpty())
            }
        }
        .getOrElse { failure -> throw failure.toSyncException() }

    override suspend fun pushExpenses(workspaceId: String, dtos: List<ExpenseDto>) {
        runCatching {
            dtos.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { dto ->
                    batch.set(FirestoreLayout.expense(firestore, workspaceId, dto.id), dto.toMap())
                }
                batch.commit().awaitTask()
            }
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    override suspend fun pushCategories(workspaceId: String, dtos: List<CategoryDto>) {
        runCatching {
            dtos.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { dto ->
                    batch.set(FirestoreLayout.category(firestore, workspaceId, dto.id), dto.toMap())
                }
                batch.commit().awaitTask()
            }
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    override suspend fun deleteExpenses(workspaceId: String, ids: List<String>) {
        runCatching {
            ids.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { id -> batch.delete(FirestoreLayout.expense(firestore, workspaceId, id)) }
                batch.commit().awaitTask()
            }
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    override suspend fun deleteCategories(workspaceId: String, ids: List<String>) {
        runCatching {
            ids.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { id -> batch.delete(FirestoreLayout.category(firestore, workspaceId, id)) }
                batch.commit().awaitTask()
            }
        }.getOrElse { failure -> throw failure.toSyncException() }
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

    /**
     * Reads the membership document, or `null` when no ledger exists yet.
     *
     * "No document" is a normal state, not an error: it is the first-run case that
     * [com.srinidhi.kharch.data.workspace.WorkspaceManager.ensureWorkspace] answers by
     * creating the ledger.
     */
    suspend fun readWorkspace(workspaceId: String): WorkspaceDto? = runCatching {
        FirestoreLayout.workspace(firestore, workspaceId).get().awaitTask()
    }
        .mapCatching { snapshot ->
            if (!snapshot.exists()) {
                null
            } else {
                WorkspaceDto(
                    id = snapshot.id,
                    name = snapshot.getString(FirestoreLayout.FIELD_NAME) ?: WorkspaceDefaults.NAME,
                    memberUids = snapshot.readMemberUids(),
                    createdAt = snapshot.getLong(FirestoreLayout.FIELD_CREATED_AT) ?: 0L,
                    schemaVersion = snapshot.getLong(FirestoreLayout.FIELD_SCHEMA_VERSION)?.toInt()
                        ?: WorkspaceDefaults.SCHEMA_VERSION,
                )
            }
        }
        .getOrElse { failure -> throw failure.toSyncException() }

    /**
     * Creates the workspace document inside a Firestore **transaction**.
     *
     * This is what stops two devices linking at the same moment from losing an update (§6.2):
     * the read and the write are one atomic unit, so a concurrent creator's commit is seen on
     * re-read and the members are merged instead of overwritten.
     */
    suspend fun createWorkspace(document: WorkspaceDto) {
        runCatching {
            firestore.runTransaction { transaction ->
                val reference = FirestoreLayout.workspace(firestore, document.id)
                val existing = transaction.get(reference)
                if (existing.exists()) {
                    val merged = (FirestoreMapper.readMemberUids(existing) + document.memberUids).distinct()
                    transaction.set(reference, document.copy(memberUids = merged).toMap())
                } else {
                    transaction.set(reference, document.toMap())
                }
            }.awaitTask()
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    /**
     * Appends [uid] to the member list inside a transaction.
     *
     * The read-modify-write is atomic, so two devices linking simultaneously converge on the
     * union of their members rather than one silently clobbering the other's addition.
     */
    suspend fun addMember(workspaceId: String, uid: String) {
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

    /**
     * Removes [uid] from the member list inside a transaction.
     *
     * Refuses to leave the ledger memberless. An empty membership list is a data-loss state
     * rather than a tidy one, because no rule in §6.6 would then let anyone back in — so the
     * guard is checked on both sides of the wire, not just in the UI.
     */
    suspend fun removeMember(workspaceId: String, uid: String) {
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

    /**
     * Tombstones every expense and category in the ledger (§6.2, "Erase this ledger").
     *
     * A full wipe is a **tombstone, never a hard delete**: the deletes are pushed so every
     * other device sees them, and the physical purge happens later in the 30-day sweep. The
     * only delete the app ever issues directly is that sweep (plan §6.4).
     */
    suspend fun eraseLedger(workspaceId: String) {
        runCatching {
            val deletedAt = clock.nowMillis()
            var expenses: List<ExpenseDto> = emptyList()
            var cursor = 0L
            while (expenses.size < DRAIN_CEILING) {
                val page = pullExpenses(workspaceId, cursor, MAX_PAGE_DOCS)
                if (page.isEmpty()) break
                expenses += page
                cursor = FirestoreMapper.latestUpdatedAt(page)
                if (page.size < MAX_PAGE_DOCS) break
            }
            pushExpenses(workspaceId, expenses.map { it.copy(deletedAt = deletedAt, updatedAt = deletedAt) })

            var categories: List<CategoryDto> = emptyList()
            var categoryCursor = 0L
            while (categories.size < DRAIN_CEILING) {
                val page = pullCategories(workspaceId, categoryCursor, MAX_PAGE_DOCS)
                if (page.isEmpty()) break
                categories += page
                categoryCursor = FirestoreMapper.latestCategoryUpdatedAt(page)
                if (page.size < MAX_PAGE_DOCS) break
            }
            pushCategories(
                workspaceId,
                categories.map { it.copy(deletedAt = deletedAt, updatedAt = deletedAt) },
            )
        }.getOrElse { failure -> throw failure.toSyncException() }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private suspend fun expensesQuery(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ) = FirestoreLayout.expenses(firestore, workspaceId)
        .whereGreaterThan(FirestoreLayout.FIELD_UPDATED_AT, afterUpdatedAt)
        .orderBy(FirestoreLayout.FIELD_UPDATED_AT, Query.Direction.ASCENDING)
        .limit(limit.toLong())

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

        /**
         * Safety ceiling for the erasure drain, in documents.
         *
         * Erasure is user-initiated and rare, so this exists purely so a pathological ledger
         * cannot turn into an unbounded loop: ~60 full pages is far beyond any plausible
         * personal ledger, and stopping there is better than never stopping.
         */
        private const val DRAIN_CEILING: Int = 24_000
    }
}