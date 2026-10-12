package com.srinidhi.kharch.data.remote

import com.srinidhi.kharch.data.remote.dto.CategoryDto
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto

/**
 * What the sync coordinator needs from the cloud.
 *
 * An abstraction rather than a direct Firestore dependency so the coordinator's tests drive a
 * fake in-memory remote (plan §13.2: "a fake in-memory Firestore-like remote datastore for the
 * coordinator (no production calls)"). Nothing in this interface leaks a Firebase type, so a
 * test with no Google Play services and no `google-services.json` can exercise push ordering,
 * watermark paging, backoff and quota lock end to end.
 *
 * Every method takes the workspace id explicitly. That is not ceremony: it is the D1 invariant
 * made visible. The coordinator resolves the ledger once per run and passes it down, so no
 * method in this interface has an opportunity to filter by account.
 *
 * **There is deliberately no `addSnapshotListener` equivalent**, and there never will be:
 * live listeners bill a read for every changed document forever, which on Spark's free tier
 * is a slow, invisible quota leak (plan §6.8). Every read here is a watermark-bounded `get()`.
 */
interface RemoteLedger {

    /**
     * The next page of expenses updated strictly after [afterUpdatedAt], oldest first.
     *
     * @param afterUpdatedAt exclusive watermark lower bound, so a steady ledger returns an
     *   **empty page and costs 0 reads** rather than re-reading everything.
     * @param limit page size, capped at [FirestoreRemoteDataSource.MAX_PAGE_DOCS].
     */
    suspend fun pullExpenses(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<ExpenseDto>

    /** The next page of categories updated strictly after [afterUpdatedAt]. */
    suspend fun pullCategories(
        workspaceId: String,
        afterUpdatedAt: Long,
        limit: Int,
    ): List<CategoryDto>

    /**
     * Tombstones whose `deletedAt` precedes [deletedBeforeMillis], oldest first.
     *
     * Queries by `deletedAt` rather than by the sync watermark, and that difference is the
     * whole point: an expired tombstone's `updatedAt` is far behind the watermark, so a
     * watermark pull would never return it and would never clean it either. This is what the
     * 30-day sweep reads.
     */
    suspend fun pullExpiredTombstones(
        workspaceId: String,
        deletedBeforeMillis: Long,
        limit: Int,
    ): List<ExpenseDto>

    /** The category half of [pullExpiredTombstones]; a soft-deleted category is a tombstone too. */
    suspend fun pullExpiredTombstonedCategories(
        workspaceId: String,
        deletedBeforeMillis: Long,
        limit: Int,
    ): List<CategoryDto>

    /**
     * Writes [dtos] in Firestore batches of at most [FirestoreRemoteDataSource.MAX_BATCH_OPS].
     *
     * The order of [dtos] is preserved and callers pass them oldest-change-first, so an
     * interrupted push leaves a consistent prefix on the server.
     */
    suspend fun pushExpenses(workspaceId: String, dtos: List<ExpenseDto>)

    /** Writes [dtos] in batches of at most [FirestoreRemoteDataSource.MAX_BATCH_OPS]. */
    suspend fun pushCategories(workspaceId: String, dtos: List<CategoryDto>)

    /** Hard-deletes expired tombstones in batches of at most [FirestoreRemoteDataSource.MAX_BATCH_OPS]. */
    suspend fun deleteExpenses(workspaceId: String, ids: List<String>)

    /** Hard-deletes soft-deleted categories, used by the same sweep. */
    suspend fun deleteCategories(workspaceId: String, ids: List<String>)

    suspend fun readMeta(workspaceId: String): SyncMetaDto

    /** Persists the watermarks. A no-op when nothing changed, to save a write. */
    suspend fun writeMeta(workspaceId: String, meta: SyncMetaDto)
}
