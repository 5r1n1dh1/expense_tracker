package com.srinidhi.kharch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Access to the shared ledger row (decision D1).
 *
 * There is exactly one workspace in the common case. Plan §6.2's "start a separate ledger"
 * choice can leave a second row behind, so the table is keyed by id and the *active* one is
 * chosen by [current] — newest first — with `SettingsStore.workspaceId` as the authoritative
 * pointer when the user switches back.
 *
 * Nothing here filters expenses: members are authorisation, not partitioning.
 */
@Dao
abstract class WorkspaceDao {
    /** Emits `null` until a workspace has been created, then the active row. */
    @Query("SELECT * FROM workspace ORDER BY createdAt DESC, id ASC LIMIT 1")
    abstract fun observeCurrent(): Flow<WorkspaceEntity?>

    /** Suspend form of [observeCurrent]. */
    @Query("SELECT * FROM workspace ORDER BY createdAt DESC, id ASC LIMIT 1")
    abstract suspend fun current(): WorkspaceEntity?

    @Query("SELECT * FROM workspace WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): WorkspaceEntity?

    /** Idempotent: re-signing in rewrites the same row rather than duplicating the ledger. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(workspace: WorkspaceEntity)

    @Query("SELECT COUNT(*) FROM workspace")
    abstract suspend fun count(): Int

    /** Only used by "Erase this ledger", which tombstones and sweeps (§6.4) rather than dropping. */
    @Query("DELETE FROM workspace")
    abstract suspend fun clear()
}
