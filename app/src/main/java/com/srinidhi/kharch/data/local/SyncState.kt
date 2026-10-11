package com.srinidhi.kharch.data.local

/**
 * Where a row stands relative to Firestore (plan §6.3, the outbox pattern).
 *
 * Every local write starts as `PENDING_*`; the sync coordinator flips it to [SYNCED] once the
 * row has been acknowledged by the server. This is what makes the app offline-first: nothing
 * waits on the network.
 */
enum class SyncState {
    /** Confirmed on the server. */
    SYNCED,

    /** Local create or edit not yet pushed. */
    PENDING_UPSERT,

    /** Local delete not yet pushed. The row is a tombstone, not a hard delete. */
    PENDING_DELETE,
    ;

    /** `true` for either pending state — drives the "Offline — N pending" badge (§6.7). */
    val isPending: Boolean get() = this != SYNCED
}
