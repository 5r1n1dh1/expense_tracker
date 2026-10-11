package com.srinidhi.kharch.domain.sync

import com.srinidhi.kharch.core.time.MILLIS_PER_DAY
import kotlin.math.max

/**
 * Decides which of two versions of the same expense survives (§6.4).
 *
 * Last-write-wins on `updatedAt`, with the four refinements that matter once a single ledger
 * is edited by several devices at once (D1):
 *
 * 1. **Tombstones propagate.** A delete sets `deletedAt` instead of removing the row, so a
 *    delete made on a phone that was offline still wins everywhere (§6.4.1).
 * 2. **Delete beats update at equal timestamps.** When `updatedAt` ties and one side is a
 *    tombstone, the tombstone wins: destructive intent is explicit, and the alternative is a
 *    resurrected expense (§6.4.2).
 * 3. **Monotonic clock guard.** A device whose clock jumps backwards would otherwise lose
 *    every comparison forever; [monotonicTimestamp] keeps `updatedAt` at
 *    `max(local, lastSeen)` (§6.4.3).
 * 4. **Shared-workspace convergence.** On a tie between two different members, the
 *    lexicographically smaller `authorUid` wins. Without it, two members editing the same
 *    expense offline ping-pong on every sync (§6.4.4).
 *
 * Every function here is pure and total: [survivor] is symmetric, so both devices compute
 * the same winner from the same pair of rows regardless of which one they happen to hold.
 */
object ConflictResolver {

    /** How long a tombstone is kept before a device may sweep it (§6.4, no TTL on Blaze data). */
    const val TOMBSTONE_RETENTION_DAYS: Long = 30L

    /** Tombstones are swept after this many days. */
    const val TOMBSTONE_RETENTION_MILLIS: Long = TOMBSTONE_RETENTION_DAYS * MILLIS_PER_DAY

    /**
     * The row that should be stored after merging [local] with [remote].
     *
     * Either side may be `null` — a first sync, or an id the other device has never seen —
     * in which case the side that exists simply wins. Both `null` means the id is gone from
     * the ledger and there is nothing to store.
     */
    fun resolve(local: LedgerRow?, remote: LedgerRow?): LedgerRow? = when {
        local == null -> remote
        remote == null -> local
        else -> survivor(local, remote)
    }

    /**
     * The winner between two rows that are both present.
     *
     * The ordering is a total order and is symmetric: equal `updatedAt` falls through to
     * tombstone-dominance, then to the `authorUid` tiebreak, and finally to an identity that
     * means the two rows are the same version anyway. That symmetry is the property the
     * convergence test pins down.
     */
    fun survivor(left: LedgerRow, right: LedgerRow): LedgerRow = when {
        left.updatedAt != right.updatedAt -> if (left.updatedAt > right.updatedAt) left else right
        left.isTombstone != right.isTombstone -> if (left.isTombstone) left else right
        left.authorUid != right.authorUid -> if (left.authorUid < right.authorUid) left else right
        else -> left
    }

    /**
     * `updatedAt = max(local, lastSeen)` — the monotonic guard of §6.4.3.
     *
     * A device whose wall clock jumped backwards would otherwise stamp every edit with an
     * earlier time than rows it has already seen, and lose every merge from then on. Passing
     * the highest `updatedAt` this device has observed keeps its writes competitive without
     * inventing a future timestamp.
     */
    fun monotonicTimestamp(nowMillis: Long, lastSeenUpdatedAt: Long): Long =
        max(nowMillis, lastSeenUpdatedAt)

    /**
     * [row] with its `updatedAt` raised to [monotonicTimestamp] against [lastSeenUpdatedAt].
     *
     * Never lowers a timestamp: a row that is already ahead of the clock stays ahead, so a
     * backwards clock cannot rewind the ledger it is part of.
     */
    fun applyMonotonicGuard(row: LedgerRow, nowMillis: Long, lastSeenUpdatedAt: Long): LedgerRow {
        val guarded = monotonicTimestamp(nowMillis, lastSeenUpdatedAt)
        if (guarded == row.updatedAt) return row
        return row.copy(expense = row.expense.copy(updatedAt = guarded))
    }

    /**
     * Whether a tombstone is old enough for this device to sweep (§6.4).
     *
     * A tombstone whose `deletedAt` lies in the future — which a backwards clock can produce —
     * is never purgeable, so the sweep cannot delete history that has not happened yet.
     */
    fun isPurgeable(row: LedgerRow, nowMillis: Long): Boolean {
        val deletedAt = row.deletedAt ?: return false
        val age = nowMillis - deletedAt
        return age >= TOMBSTONE_RETENTION_MILLIS
    }

    /** The highest `updatedAt` in [rows], or `null` when the ledger has none. */
    fun latestUpdatedAt(rows: Iterable<LedgerRow>): Long? {
        var latest = 0L
        var found = false
        for (row in rows) {
            val seen = row.updatedAt
            if (!found || seen > latest) {
                latest = seen
                found = true
            }
        }
        return if (found) latest else null
    }
}
