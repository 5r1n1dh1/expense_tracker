package com.srinidhi.kharch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The shared ledger (decision D1), mirroring `workspaces/{wid}` in Firestore.
 *
 * There is exactly **one** of these in the common case, and it is the root of the whole dataset:
 * Google accounts are members of it, not containers, so no query in the codebase carries an
 * account filter. Plan §6.2's "start a separate ledger" choice can add a second row, which is
 * why the table is keyed by [id] rather than being a singleton; `SettingsStore.workspaceId`
 * holds the pointer to whichever one is active.
 *
 * @property memberUids every linked Google account. Stored as a JSON array via [Converters] and
 *   capped at five by the server rules.
 * @property schemaVersion cloud-side migration guard (plan §18.2.6), so a version mismatch is
 *   reported instead of silently corrupting the ledger.
 */
@Entity(tableName = "workspace")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'Kharch'") val name: String = DEFAULT_NAME,
    val memberUids: List<String>,
    val createdAt: Long,
    val schemaVersion: Int,
) {
    companion object {
        /** Every workspace is called this until the user renames it. */
        const val DEFAULT_NAME: String = "Kharch"

        /** Server rule (§6.6): a ledger may never have more than five members. */
        const val MAX_MEMBERS: Int = 5
    }
}
