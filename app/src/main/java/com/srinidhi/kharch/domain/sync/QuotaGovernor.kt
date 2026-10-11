package com.srinidhi.kharch.domain.sync

import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.domain.epochMillisNow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A per-process budget tracker that makes the Firebase Spark free tier safe (D2, §6.8).
 *
 * Spark **hard-stops** when the daily quota runs out: there is no overage, no graceful
 * degradation and no partial credit. A naive sync loop can therefore spend the whole day's
 * 20 000 writes in a single sitting and then be dead until midnight Pacific, which is the one
 * failure mode this class exists to prevent.
 *
 * Two independent brakes:
 *
 * - A **self-imposed ceiling at 60 %** of the published quota (12 000 writes, 30 000 reads).
 *   The last 40 % is headroom for the retries, the widget and a device syncing that has not
 *   run for a week. Crossing it refuses the request instead of letting the loop burn the day.
 * - A **lock on `RESOURCE_EXHAUSTED`** until the next midnight-Pacific reset. Once the real
 *   quota is gone, every subsequent request is refused without touching the network, and the
 *   reset time is exposed so the UI can say when it resumes. There is no backoff path out of
 *   this state: retrying is the failure, not the cure.
 *
 * Everything is driven by the injected [clock] and plain counters, so a test can simulate a
 * day of traffic in microseconds and assert that **zero** requests are issued between the
 * exhaustion and the reset.
 *
 * Not thread-safe by design: it is owned by the workspace-level sync mutex (§6.3), which is
 * the only thing allowed to talk to Firestore.
 */
class QuotaGovernor(
    private val clock: Clock,
    private val zone: ZoneId = PACIFIC,
    private val writeCeiling: Long = DEFAULT_WRITE_CEILING,
    private val readCeiling: Long = DEFAULT_READ_CEILING,
) {
    private var writesUsed = 0L
    private var readsUsed = 0L
    private var lockedKind: QuotaKind? = null
    private var lockedUntilMillis = 0L
    private var budgetDate: LocalDate? = null
    private var lastNowMillis = Long.MIN_VALUE

    /**
     * Spends [count] writes from the budget.
     *
     * A refusal costs nothing: the counters are untouched, so a refused call cannot slowly
     * drain the day, and while [isLocked][QuotaStatus.isLocked] every call is refused.
     */
    fun acquireWrite(count: Int = 1): QuotaDecision = acquire(count, QuotaKind.WRITES)

    /** Spends [count] reads from the budget; see [acquireWrite]. */
    fun acquireRead(count: Int = 1): QuotaDecision = acquire(count, QuotaKind.READS)

    /**
     * Reports that the backend refused a request because the daily quota is gone.
     *
     * The budget is then locked until the next midnight-Pacific reset — the same reset window
     * Spark itself uses. Calling this again while already locked simply re-anchors the lock,
     * which keeps the behaviour correct if the device clock moved while locked.
     */
    fun onResourceExhausted(kind: QuotaKind) {
        val now = clock.epochMillisNow
        refresh(now)
        lockedKind = kind
        lockedUntilMillis = nextResetMillis(now, zone)
    }

    /**
     * A snapshot for the sync pill (§6.7).
     *
     * Reading the status also rolls the budget over if the Pacific day changed, so the
     * numbers on screen are never from yesterday.
     */
    fun currentStatus(): QuotaStatus {
        val now = clock.epochMillisNow
        refresh(now)
        return QuotaStatus(
            writesUsed = writesUsed,
            writeCeiling = writeCeiling,
            readsUsed = readsUsed,
            readCeiling = readCeiling,
            lockedKind = lockedKind,
            resetAtMillis = nextResetMillis(now, zone),
        )
    }

    /**
     * The user-facing lock message, or `null` while the governor is healthy.
     *
     * The wording is the sync pill's, and it names the reset time because "try again later"
     * is not actionable at 9am when the retry is at midnight.
     */
    fun lockMessage(): String? {
        val status = currentStatus()
        return if (status.isLocked) lockMessage(status.resetAtMillis) else null
    }

    /** The same message for an explicit reset time — pure, so the format is unit-testable. */
    fun lockMessage(resetAtMillis: Long): String {
        val hhmm = RESET_FORMAT.format(Instant.ofEpochMilli(resetAtMillis).atZone(zone))
        return "Free-tier limit reached — resumes $hhmm PT"
    }

    /**
     * How long to wait before attempt number [attempt] (1-based) of an operation that failed
     * with [kind].
     *
     * Ordinary transient failures back off exponentially and cap out at five minutes.
     * `RESOURCE_EXHAUSTED` returns `null`: there is nothing to wait for, because the answer
     * is the lock, not a retry.
     */
    fun delayForAttempt(attempt: Int, kind: SyncErrorKind): Long? =
        if (kind == SyncErrorKind.RESOURCE_EXHAUSTED) null else backoffDelayMillis(attempt)

    /** Exponential backoff for transient failures: 1s, 2s, 4s … capped at [MAX_BACKOFF_MILLIS]. */
    fun backoffDelayMillis(attempt: Int): Long {
        val shift = (attempt.coerceAtLeast(1) - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (BASE_BACKOFF_MILLIS shl shift).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    /** True while the budget is locked and no request may be issued. */
    fun isLocked(): Boolean = currentStatus().isLocked

    private fun acquire(count: Int, kind: QuotaKind): QuotaDecision {
        require(count >= 0) { "a quota request must not be negative, got $count" }
        val now = clock.epochMillisNow
        refresh(now)
        val locked = lockedKind
        if (locked != null) return QuotaDecision.Refused(QuotaRefusal.QUOTA_LOCKED, lockedUntilMillis)
        val used = if (kind == QuotaKind.WRITES) writesUsed else readsUsed
        val ceiling = if (kind == QuotaKind.WRITES) writeCeiling else readCeiling
        if (used + count > ceiling) return QuotaDecision.Refused(refusalFor(kind), nextResetMillis(now, zone))
        if (kind == QuotaKind.WRITES) writesUsed += count else readsUsed += count
        return QuotaDecision.Granted
    }

    private fun refusalFor(kind: QuotaKind): QuotaRefusal =
        if (kind == QuotaKind.WRITES) QuotaRefusal.WRITE_CEILING_REACHED else QuotaRefusal.READ_CEILING_REACHED

    /**
     * Brings the counters and the lock up to date with [now].
     *
     * The daily budget rolls over on the Pacific date, not on a 24-hour timer, so it lines up
     * with the reset the backend actually applies. A clock that jumps *backwards* never rolls
     * anything over: otherwise moving the device clock would hand the sync loop a fresh budget
     * every few minutes, which is the same runaway this class is here to stop.
     */
    private fun refresh(now: Long) {
        val wentBackwards = now < lastNowMillis
        if (!wentBackwards) lastNowMillis = now
        if (lockedUntilMillis != 0L && now >= lockedUntilMillis) {
            lockedKind = null
            lockedUntilMillis = 0L
        }
        if (wentBackwards) return
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val anchor = budgetDate
        if (anchor == null) {
            budgetDate = today
        } else if (anchor != today) {
            budgetDate = today
            writesUsed = 0L
            readsUsed = 0L
        }
    }

    companion object {
        /** 60 % of the published 20 000 writes/day on Spark (§6.8). */
        const val DEFAULT_WRITE_CEILING: Long = 12_000L

        /** 60 % of the published 50 000 reads/day on Spark (§6.8). */
        const val DEFAULT_READ_CEILING: Long = 30_000L

        /** First transient retry waits a second. */
        const val BASE_BACKOFF_MILLIS: Long = 1_000L

        /** Backoff never exceeds five minutes. */
        const val MAX_BACKOFF_MILLIS: Long = 300_000L

        /** 2^9 × 1 s is past the cap, so the shift is bounded and can never overflow. */
        private const val MAX_BACKOFF_SHIFT = 9

        /** Firestore's daily reset is midnight Pacific, year round. */
        val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

        private val RESET_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        /** The next midnight in [zone] strictly after [fromMillis]. */
        fun nextResetMillis(fromMillis: Long, zone: ZoneId = PACIFIC): Long =
            Instant.ofEpochMilli(fromMillis)
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
    }
}
