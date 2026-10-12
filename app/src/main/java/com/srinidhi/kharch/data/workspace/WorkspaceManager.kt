package com.srinidhi.kharch.data.workspace

import com.srinidhi.kharch.core.model.Workspace
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.data.local.CategoryDao
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.local.SyncState
import com.srinidhi.kharch.data.local.WorkspaceDao
import com.srinidhi.kharch.data.local.WorkspaceEntity
import com.srinidhi.kharch.data.prefs.SettingsStore
import com.srinidhi.kharch.data.remote.dto.WorkspaceDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared ledger, and the heart of decision D1: **one workspace, many Google accounts.**
 *
 * Google accounts are *members*, never containers. There is exactly one Room database and,
 * normally, exactly one [WorkspaceEntity], and **no query in this class — or anywhere else in
 * the codebase — filters by account**. What follows from that:
 *
 *  - "Switching account" changes which identity is *authenticated*, never what data is shown.
 *  - Adding a member is a credential change, so it cannot lose or split anyone's expenses.
 *  - Removing a member cannot orphan data, because no row is owned by a member.
 *
 * The member list is the one piece of state that must be transactional. Two devices linking
 * simultaneously would otherwise read the same list, append their own uid, and let one write
 * win — silently dropping a member with no error anywhere. Every mutation here therefore goes
 * through [MembershipStore], which performs it on the server in a transaction first, and only
 * then mirrors it into Room. If the server write fails the app still works offline and the
 * account simply stays outside the ledger until a sync succeeds, which is the safe direction:
 * a member who cannot reach the ledger is a temporary inconvenience, whereas a member added
 * everywhere at once is a lost update.
 */
@Singleton
class WorkspaceManager @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val settingsStore: SettingsStore,
    private val membershipStore: MembershipStore,
    private val clock: Clock,
) {
    /** The active ledger row, or `null` until one exists. */
    suspend fun current(): WorkspaceEntity? = workspaceDao.current()

    /** The active ledger as a domain model, or `null` before the first sign-in. */
    suspend fun currentWorkspace(): Workspace? = workspaceDao.current()?.toModel()

    /** Emits the active ledger, so the "Synced as …" label and Accounts screen stay live. */
    fun observeWorkspace(): Flow<Workspace?> = workspaceDao.observeCurrent().map { it?.toModel() }

    /** Which Google identity is signed in. Display only — it filters nothing (§5.3). */
    val activeAuthUid: Flow<String?> = settingsStore.activeAuthUid

    /** The ledger id this device points at, for the "Switch ledger" control (§6.2). */
    suspend fun workspaceId(): String? = workspaceDao.current()?.id

    /**
     * Resolves the ledger for a freshly signed-in account (§6.1 step 3).
     *
     *  1. No ledger on this device → create one with [uid] as its only member.
     *  2. A ledger exists → join it, because D1 wants one shared dataset.
     *
     * This is also where the §6.2 "join vs start" question is answered *from the UI*: the
     * onboarding sheet calls either this (join, the default) or [createWorkspace] (start a
     * separate ledger). The manager itself never guesses.
     */
    suspend fun ensureWorkspace(uid: String): Workspace {
        val existing = workspaceDao.current()
            ?: return createWorkspace(uid, WorkspaceEntity.DEFAULT_NAME)
        return if (existing.isMember(uid)) existing.toModel() else linkAccount(existing, uid)
    }

    /**
     * Creates a fresh, empty ledger with [uid] as its only member.
     *
     * The id is a random UUID rather than anything derived from the account: two accounts must
     * never derive the same ledger id, or signing in on a second device would silently join the
     * wrong ledger. This is the "Start a separate ledger" path (§6.2).
     */
    suspend fun createWorkspace(uid: String, name: String = WorkspaceEntity.DEFAULT_NAME): Workspace {
        val workspace = WorkspaceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            memberUids = listOf(uid),
            createdAt = clock.nowMillis(),
            schemaVersion = CLOUD_SCHEMA_VERSION,
        )
        membershipStore.createWorkspace(workspace.toDto())
        workspaceDao.upsert(workspace)
        settingsStore.setActiveAuth(uid, workspace.id)
        return workspace.toModel()
    }

    /**
     * Appends [uid] to an existing ledger's member list (§6.2, "link another Google account").
     *
     * This is D1's core question answered: a second Google account is another way *into* the
     * same expenses, not a second dataset.
     */
    suspend fun linkAccount(workspaceId: String, uid: String): Workspace {
        val workspace = workspaceDao.findById(workspaceId) ?: workspaceDao.current()
            ?: error("Cannot link $uid: there is no ledger to link it to")
        return linkAccount(workspace, uid)
    }

    /**
     * Removes a member from the ledger (§6.2).
     *
     * Blocked, with a reason, when it would leave the ledger memberless: after that no
     * security rule in §6.6 would let anyone back in, so the data would be unreachable rather
     * than merely inaccessible from this device.
     */
    suspend fun removeAccount(uid: String): RemovalOutcome {
        val workspace = workspaceDao.current() ?: return RemovalOutcome.NoWorkspace
        val remaining = workspace.memberUids - uid
        if (remaining.isEmpty()) {
            return RemovalOutcome.LastMember(uid)
        }
        val updated = workspace.copy(memberUids = remaining)
        membershipStore.removeMember(updated.id, uid)
        workspaceDao.upsert(updated)
        if (settingsStore.activeAuthUid.first() == uid) {
            settingsStore.clearActiveAuth()
        }
        return RemovalOutcome.Removed(updated.toModel())
    }

    /**
     * Tombstones every expense and category for a full wipe (§6.2, "Erase this ledger").
     *
     * The wipe is a **tombstone marker, not a hard delete**: every live row is marked deleted
     * and pushed through the ordinary outbox, so the deletes reach every other device before
     * anything physically disappears. A device that was offline at erase time therefore loses
     * the rows at its next sync instead of resurrecting them.
     *
     * The local rows are kept rather than dropped, precisely so there is something left to
     * push. They disappear physically in the 30-day sweep, which is the only place this app
     * issues a Firestore delete at all (plan §6.4).
     *
     * The workspace row itself is *not* removed: the security rules forbid deleting the
     * document (§6.6), because erasure is tombstone-then-sweep by design.
     */
    suspend fun eraseLedger(): EraseOutcome {
        val workspace = workspaceDao.current() ?: return EraseOutcome.NoWorkspace
        val deletedAt = clock.nowMillis()
        val live = expenseDao.observeAllLive().first()
        live.forEach { expenseDao.softDelete(it.id, deletedAt, SyncState.PENDING_DELETE) }
        val liveCategories = categoryDao.observeAll().first().filter { it.deletedAt == null }
        liveCategories.forEach { categoryDao.softDelete(it.id, deletedAt) }
        return EraseOutcome.Erased(workspace.id, live.size, liveCategories.size)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private suspend fun linkAccount(workspace: WorkspaceEntity, uid: String): Workspace {
        if (workspace.isMember(uid)) return workspace.toModel()
        val updated = workspace.copy(memberUids = workspace.memberUids + uid)
        membershipStore.addMember(updated.id, uid)
        workspaceDao.upsert(updated)
        settingsStore.setActiveAuth(uid, updated.id)
        return updated.toModel()
    }

    private fun WorkspaceEntity.toModel(): Workspace = Workspace(
        id = id,
        name = name,
        memberUids = memberUids,
        createdAt = createdAt,
        schemaVersion = schemaVersion,
    )

    private fun WorkspaceEntity.toDto(): WorkspaceDto = WorkspaceDto(
        id = id,
        name = name,
        memberUids = memberUids,
        createdAt = createdAt,
        schemaVersion = schemaVersion,
    )

    private fun WorkspaceEntity.isMember(uid: String): Boolean = uid in memberUids

    companion object {
        /**
         * The cloud-side schema guard written into every workspace document (plan §18.2.6).
         *
         * Stored so a future server-side change can be detected and reported rather than
         * silently mis-read. It is deliberately *not* `AppDatabase.SCHEMA_VERSION`: those two
         * version different things and bumping one must never imply bumping the other.
         */
        const val CLOUD_SCHEMA_VERSION: Int = 1
    }
}

/** The result of attempting to remove a member (§5.3). */
sealed interface RemovalOutcome {

    /** The member was removed. Everything they ever entered survives, because nothing owned it. */
    data class Removed(val workspace: Workspace) : RemovalOutcome

    /** Refused: this is the last member, and an empty ledger is unreachable, not just unused. */
    data class LastMember(val uid: String) : RemovalOutcome

    /** There is no ledger on this device, so there is nothing to remove. */
    data object NoWorkspace : RemovalOutcome
}

/** The result of a full ledger wipe (§6.2). */
sealed interface EraseOutcome {

    /** The ledger was tombstoned. The counts are what this device wiped. */
    data class Erased(
        val workspaceId: String,
        val expenses: Int,
        val categories: Int,
    ) : EraseOutcome

    /** There was no ledger to erase. */
    data object NoWorkspace : EraseOutcome
}
