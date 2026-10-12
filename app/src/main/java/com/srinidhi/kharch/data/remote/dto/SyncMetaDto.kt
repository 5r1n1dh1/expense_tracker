package com.srinidhi.kharch.data.remote.dto

/**
 * `workspaces/{wid}/meta/sync` — the sync watermarks and the last error (plan §6.5).
 *
 * This document is the only place the watermark is persisted, and it is the reason the
 * tombstone sweep is gated across devices: `lastSweepAt` is read here before any sweep runs,
 * so one device a day performs it rather than all of them (plan §6.4).
 *
 * @property lastPulledAt the `updatedAt` watermark, exclusive lower bound for the next pull.
 *   A pull is `where("updatedAt", > lastPulledAt)`, so a steady ledger costs **0 reads**.
 * @property lastPushAt when this workspace last completed an expense push.
 * @property lastCategoryPushAt when this workspace last completed a category push. Categories
 *   carry no local outbox flag, so a watermark is the only way to avoid re-writing the whole
 *   category table on every sync — and a reorder must be one batched write, not 30 (plan
 *   §5.2, §6.8). It is an addition to the §6.5 field list, justified by [com.srinidhi.kharch
 *   .data.local.CategoryEntity] having no `syncState` column; older clients ignore unknown
 *   fields rather than failing to sync.
 * @property lastSweepAt when a device last swept tombstones, epoch millis.
 * @property lastSyncError the most recent sync error message, shown in Settings → Sync.
 */
data class SyncMetaDto(
    val lastPulledAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val lastCategoryPushAt: Long = 0L,
    val lastSweepAt: Long = 0L,
    val lastSyncError: String? = null,
)
