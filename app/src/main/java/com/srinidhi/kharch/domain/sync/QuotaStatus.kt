package com.srinidhi.kharch.domain.sync

/** Which daily budget a request would spend. */
enum class QuotaKind {
    /** Firestore writes — 20 000/day on Spark, of which Kharch spends at most 60 %. */
    WRITES,

    /** Firestore reads — 50 000/day on Spark, of which Kharch spends at most 60 %. */
    READS,
}

/** Why the governor refused to let a request through. */
enum class QuotaRefusal {
    /** The quota is spent; every request is refused until the daily reset (§6.8). */
    QUOTA_LOCKED,

    /** Issuing this request would cross the 60 % self-imposed ceiling. */
    WRITE_CEILING_REACHED,

    /** Issuing this request would cross the 60 % self-imposed ceiling. */
    READ_CEILING_REACHED,
}

/** The answer to "may I issue this request?". */
sealed interface QuotaDecision {
    /** Spend the budget and go ahead. */
    data object Granted : QuotaDecision

    /**
     * Do not issue the request. [resetAtMillis] is the next Pacific midnight, which is what
     * the sync pill shows the user; [reason] is what decides the wording.
     */
    data class Refused(val reason: QuotaRefusal, val resetAtMillis: Long) : QuotaDecision
}

/**
 * An immutable read of the governor, for the sync status pill (§6.7).
 *
 * @property lockedKind which budget blew its real quota; `null` while the day is healthy.
 * @property resetAtMillis the next Pacific midnight, always populated so the UI can show a
 *   countdown even when the refusal was the self-imposed ceiling rather than exhaustion.
 */
data class QuotaStatus(
    val writesUsed: Long,
    val writeCeiling: Long,
    val readsUsed: Long,
    val readCeiling: Long,
    val lockedKind: QuotaKind?,
    val resetAtMillis: Long,
) {
    /** True once a `RESOURCE_EXHAUSTED` has been seen; no request is issued until reset. */
    val isLocked: Boolean
        get() = lockedKind != null

    val writesRemaining: Long
        get() = (writeCeiling - writesUsed).coerceAtLeast(0L)

    val readsRemaining: Long
        get() = (readCeiling - readsUsed).coerceAtLeast(0L)

    val writesExhausted: Boolean
        get() = writesRemaining == 0L

    val readsExhausted: Boolean
        get() = readsRemaining == 0L
}
