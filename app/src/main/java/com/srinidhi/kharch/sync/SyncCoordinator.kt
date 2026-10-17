package com.srinidhi.kharch.sync

import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.data.local.CategoryDao
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.local.OutboxDao
import com.srinidhi.kharch.data.local.SyncState
import com.srinidhi.kharch.data.local.WorkspaceDao
import com.srinidhi.kharch.data.remote.RemoteLedger
import com.srinidhi.kharch.data.remote.SyncException
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.CategoryDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto
import com.srinidhi.kharch.domain.sync.ConflictResolver
import com.srinidhi.kharch.domain.sync.QuotaGovernor
import com.srinidhi.kharch.domain.sync.QuotaDecision
import com.srinidhi.kharch.domain.sync.QuotaStatus
import com.srinidhi.kharch.domain.sync.SyncErrorKind
import com.srinidhi.kharch.domain.sync.SyncStateHolder
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Workspace-level sync orchestration (§6.3, §6.8). Pure Kotlin; zero Android/Firebase. */
@Singleton
class SyncCoordinator @Inject constructor(
    private val remote: RemoteLedger,
    private val governor: QuotaGovernor,
    private val clock: Clock,
    private val workspaceDao: WorkspaceDao,
    private val outboxDao: OutboxDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val stateHolder: SyncStateHolder,
) {

    private val workspaceMutex = Mutex()
    private val pushTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    companion object {
        const val MAX_BATCH_OPS: Int = 400
        const val MAX_PAGE_DOCS: Int = 400
        const val DEBOUNCE_MS: Long = 800L
    }

    suspend fun requestSync() {
        workspaceMutex.withLock {
            val workspace = workspaceDao.current() ?: return@withLock
            val wid = workspace.id
            if (governor.currentStatus().isLocked) {
                stateHolder.update { s -> s.copy(quotaMessage = "Quota locked") }
                return@withLock
            }
            try {
                stateHolder.update { s -> s.copy(syncing = true) }
                pushBatch(wid)
                pullBatch(wid)
                sweepTombstones(wid)
                stateHolder.update { s -> s.copy(syncing = false, lastSyncAtMillis = clock.nowMillis()) }
            } catch (e: SyncException) {
                when (e.kind) {
                    SyncErrorKind.RESOURCE_EXHAUSTED -> {
                        governor.onResourceExhausted()
                        stateHolder.update { s ->
                            s.copy(quotaMessage = governor.lockMessage(),
                                resetAtMillis = governor.currentStatus().resetAtMillis)
                        }
                    }
                    SyncErrorKind.TRANSIENT -> {
                        val delayMs = governor.delayForAttempt(1, e.kind) ?: 1000L
                        delay(delayMs)
                        stateHolder.update { s -> s.copy(syncing = false) }
                    }
                    else -> stateHolder.update { s -> s.copy(syncing = false) }
                }
            }
        }
    }

    private suspend fun pushBatch(wid: String) {
        var pending = outboxDao.pendingUpsertCount() + outboxDao.pendingDeleteCount()
        while (pending > 0 && governor.acquireWrite(MAX_BATCH_OPS) is QuotaDecision.Granted) {
            val upserts = outboxDao.queryPendingUpserts(limit = MAX_BATCH_OPS)
            val deletes = outboxDao.queryPendingDeletes(limit = MAX_BATCH_OPS)
            val toPush = (upserts + deletes).take(MAX_BATCH_OPS)
            if (toPush.isEmpty()) break
            remote.pushExpenses(wid, toPush.filterIsInstance<ExpenseDto>())
            remote.pushCategories(wid, toPush.filterIsInstance<CategoryDto>())
            outboxDao.markSynced(toPush.map { it.id })
            pending = outboxDao.pendingUpsertCount() + outboxDao.pendingDeleteCount()
        }
    }

    private suspend fun pullBatch(wid: String) {
        val meta = remote.readMeta(wid)
        var lastPull = meta.lastPulledAt
        do {
            if (governor.acquireRead(MAX_PAGE_DOCS) !is QuotaDecision.Granted) break
            val expenses = remote.pullExpenses(wid, lastPull, MAX_PAGE_DOCS)
            val categories = remote.pullCategories(wid, lastPull, MAX_PAGE_DOCS)
            applyPull(expenses, categories, wid)
            lastPull = maxOf(lastPull, expenses.maxOfOrNull { it.updatedAt } ?: lastPull,
                categories.maxOfOrNull { it.updatedAt } ?: lastPull)
            if (expenses.isEmpty() && categories.isEmpty()) break
        } while (expenses.isNotEmpty() || categories.isNotEmpty())
        remote.writeMeta(wid, meta.copy(lastPulledAt = lastPull))
    }

    private suspend fun applyPull(
        expenses: List<ExpenseDto>, categories: List<CategoryDto>, wid: String,
    ) {
        for (dto in expenses) {
            val local = expenseDao.findById(dto.id)
            val merged = ConflictResolver.resolve(local?.toRow(), dto.toRow())
            merged?.let { expenseDao.upsert(it.toEntity()) }
        }
        for (dto in categories) {
            val local = categoryDao.findById(dto.id)
            val merged = ConflictResolver.resolve(local?.toRow(), dto.toRow())
            merged?.let { categoryDao.upsert(it.toEntity()) }
        }
    }

    private suspend fun sweepTombstones(wid: String) {
        val now = clock.nowMillis()
        val cutoff = now - ConflictResolver.TOMBSTONE_RETENTION_MILLIS
        val tombstones = remote.pullExpiredTombstones(wid, cutoff, MAX_BATCH_OPS)
        if (tombstones.isNotEmpty()) remote.deleteExpenses(wid, tombstones.map { it.id })
    }

}
