package com.srinidhi.kharch.domain.sync

import com.srinidhi.kharch.core.model.Expense

/**
 * One row of the shared ledger as the resolver sees it: the domain [Expense] plus the
 * authorship metadata Firestore carries beside it (§6.5, `authorUid`).
 *
 * Authorship lives here rather than inside the expense because it is *sync* metadata. It is
 * informational only — "edited by <name>" in the row — and is never used for access control;
 * that is `memberUids` on the workspace doc (§6.5).
 *
 * @property authorUid the member whose device produced this version. An unknown uid is the
 *   empty string, which sorts first and therefore wins a tie — the conservative choice, since
 *   it is deterministic on every device.
 */
data class LedgerRow(
    val expense: Expense,
    val authorUid: String,
) {
    /** Last-write-wins clock, epoch millis. */
    val updatedAt: Long
        get() = expense.updatedAt

    /** Epoch millis of the delete, or `null` while the row is live. */
    val deletedAt: Long?
        get() = expense.deletedAt

    /**
     * A tombstone is a delete that is waiting for the rest of the workspace to hear about it.
     * It keeps the id, so a delete made offline on one device still wins on another (§6.4.1).
     */
    val isTombstone: Boolean
        get() = expense.deletedAt != null
}
