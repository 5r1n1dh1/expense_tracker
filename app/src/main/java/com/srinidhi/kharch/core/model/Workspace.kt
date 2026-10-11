package com.srinidhi.kharch.core.model

/**
 * The shared ledger every linked Google account reads and writes (decision D1).
 *
 * Google accounts are **members**, not data containers: no query in the codebase filters by
 * account, and no data is owned by a member. Removing the last member is blocked.
 *
 * @property id workspace id, mirrored as the Firestore document id under `workspaces/{id}`.
 * @property memberUids every linked Google account uid. The server caps this at five.
 * @property createdAt epoch millis, UTC.
 * @property schemaVersion cloud-side schema guard (plan §18.2.6), so a version mismatch is
 *   detectable and reportable rather than silently corrupting data.
 */
data class Workspace(
    val id: String,
    val name: String,
    val memberUids: List<String>,
    val createdAt: Long,
    val schemaVersion: Int,
) {
    val memberCount: Int get() = memberUids.size

    fun isMember(uid: String): Boolean = uid in memberUids
}
