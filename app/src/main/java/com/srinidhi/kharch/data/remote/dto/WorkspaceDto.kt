package com.srinidhi.kharch.data.remote.dto

/**
 * `workspaces/{wid}` — the shared-ledger document (plan §6.5, decision D1).
 *
 * The one place membership is stored. `memberUids` is authorisation, never partitioning: no
 * query anywhere in the app filters by uid, and every member sees the same expenses.
 */
data class WorkspaceDto(
    val id: String,
    val name: String,
    val memberUids: List<String>,
    val createdAt: Long,
    val schemaVersion: Int,
)
