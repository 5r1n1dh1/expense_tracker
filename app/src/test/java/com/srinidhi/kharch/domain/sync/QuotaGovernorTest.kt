package com.srinidhi.kharch.domain.sync

import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.core.time.MILLIS_PER_DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The free-tier safety governor of D2 (§6.8, §13.2).
 *
 * The assertion that matters is not "the governor locks" but "after it locks, **zero** further
 * requests are issued until the reset" — which is why the loop below goes through a fake
 * endpoint that counts what actually reached the network.
 */
class QuotaGovernorTest {

    @Test
    fun `requests inside the sixty percent ceiling are granted`() {
        val governor = QuotaGovernor(pacificClock(), writeCeiling = 10L, readCeiling = 10L)

        repeat(10) { attempt ->
            assertEquals("attempt $attempt", QuotaDecision.Granted, governor.acquireWrite())
        }
        assertEquals(0L, governor.currentStatus().writesRemaining)
    }

    @Test
    fun `the request that would cross the ceiling is refused instead of sent`() {
        val governor = QuotaGovernor(pacificClock(), writeCeiling = 3L, readCeiling = 3L)

        repeat(3) { governor.acquireWrite() }
        val decision = governor.acquireWrite()

        check(decision is QuotaDecision.Refused)
        assertEquals(QuotaRefusal.WRITE_CEILING_REACHED, decision.reason)
        assertEquals(3L, governor.currentStatus().writesUsed)
    }

    @Test
    fun `a batch larger than the remaining budget is refused whole`() {
        val governor = QuotaGovernor(pacificClock(), writeCeiling = 10L, readCeiling = 10L)

        governor.acquireWrite(8)

        val refused = governor.acquireWrite(3)

        assertEquals(QuotaDecision.Refused(QuotaRefusal.WRITE_CEILING_REACHED, pacificMidnight()), refused)
        assertEquals(8L, governor.currentStatus().writesUsed)
    }

    @Test
    fun `writes and reads are budgeted separately`() {
        val governor = QuotaGovernor(pacificClock(), writeCeiling = 2L, readCeiling = 2L)

        repeat(2) { governor.acquireWrite() }
        governor.acquireRead()
        governor.acquireRead()

        assertTrue(governor.acquireWrite() is QuotaDecision.Refused)
        assertTrue(governor.acquireRead() is QuotaDecision.Refused)
        assertEquals(2L, governor.currentStatus().writesUsed)
        assertEquals(2L, governor.currentStatus().readsUsed)
    }

    @Test
    fun `resource exhausted locks the governor until the next pacific midnight`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock)

        governor.onResourceExhausted(QuotaKind.WRITES)
        val status = governor.currentStatus()

        assertTrue(status.isLocked)
        assertEquals(QuotaKind.WRITES, status.lockedKind)
        assertEquals(pacificMidnight(), status.resetAtMillis)
        assertEquals(
            0,
            Instant.ofEpochMilli(status.resetAtMillis).atZone(QuotaGovernor.PACIFIC).hour,
        )
    }

    @Test
    fun `no request is issued between the lock and the reset`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock, writeCeiling = 1_000L, readCeiling = 1_000L)
        val endpoint = FakeEndpoint(governor, mutableListOf(SyncErrorKind.RESOURCE_EXHAUSTED))

        assertEquals(SyncErrorKind.RESOURCE_EXHAUSTED, endpoint.push())
        val issuedBeforeLock = endpoint.requestsIssued

        // A runaway sync loop keeps trying all afternoon. None of it may reach the network.
        repeat(500) { endpoint.push() }

        assertEquals(issuedBeforeLock, endpoint.requestsIssued)
        assertEquals(1, issuedBeforeLock)
        assertTrue(governor.isLocked())
        assertTrue(governor.acquireWrite() is QuotaDecision.Refused)
        assertTrue(governor.acquireRead() is QuotaDecision.Refused)
        assertEquals(1L, governor.currentStatus().writesUsed)
    }

    @Test
    fun `the lock still holds one millisecond before the reset`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock)

        governor.onResourceExhausted(QuotaKind.READS)
        clock.jumpTo(pacificMidnight() - 1L)

        assertTrue(governor.isLocked())
        assertTrue(governor.acquireWrite() is QuotaDecision.Refused)
    }

    @Test
    fun `the lock lifts exactly at the pacific reset`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock)

        governor.onResourceExhausted(QuotaKind.WRITES)
        clock.jumpTo(pacificMidnight())

        assertFalse(governor.isLocked())
        assertEquals(QuotaDecision.Granted, governor.acquireWrite())
        assertEquals(1L, governor.currentStatus().writesUsed)
        assertNull(governor.lockMessage())
    }

    @Test
    fun `a refused request never spends budget`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock, writeCeiling = 2L, readCeiling = 2L)
        repeat(2) { governor.acquireWrite() }

        repeat(100) { attempt ->
            assertTrue("refusal $attempt", governor.acquireWrite() is QuotaDecision.Refused)
        }
        assertEquals(2L, governor.currentStatus().writesUsed)

        clock.jumpTo(pacificMidnight())
        val fresh = governor.currentStatus()
        assertEquals(0L, fresh.writesUsed)
        assertEquals(2L, fresh.writesRemaining)
        assertEquals(QuotaDecision.Granted, governor.acquireWrite())
    }

    @Test
    fun `the budget rolls over on the next pacific day`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock, writeCeiling = 5L, readCeiling = 5L)
        repeat(5) { governor.acquireWrite() }

        clock.advanceMillis(MILLIS_PER_DAY)

        val status = governor.currentStatus()
        assertEquals(0L, status.writesUsed)
        assertEquals(5L, status.writesRemaining)
        assertEquals(QuotaDecision.Granted, governor.acquireWrite())
    }

    @Test
    fun `a clock moved backwards cannot hand the loop a fresh budget`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock, writeCeiling = 3L, readCeiling = 3L)
        repeat(3) { governor.acquireWrite() }

        // Three days into the past, the Pacific date changes but the budget must not reset.
        clock.advanceMillis(-MILLIS_PER_DAY * 3)
        repeat(10) { governor.acquireWrite() }

        assertEquals(3L, governor.currentStatus().writesUsed)
        assertTrue(governor.acquireWrite() is QuotaDecision.Refused)
    }

    @Test
    fun `a transient failure backs off exponentially and then the operation succeeds`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock)
        val transcript = mutableListOf(SyncErrorKind.TRANSIENT, SyncErrorKind.TRANSIENT)
        val delays = mutableListOf<Long>()
        val start = clock.nowMillis()

        var attempt = 1
        while (transcript.isNotEmpty()) {
            val failure = transcript.removeAt(0)
            val delay = governor.delayForAttempt(attempt, failure) ?: 0L
            delays += delay
            clock.advanceMillis(delay)
            attempt++
        }

        assertEquals(listOf(1_000L, 2_000L), delays)
        assertEquals(3_000L, clock.nowMillis() - start)
        assertEquals(3, attempt)
        assertEquals(QuotaDecision.Granted, governor.acquireWrite())
    }

    @Test
    fun `backoff grows exponentially and then stops at the cap`() {
        val governor = QuotaGovernor(pacificClock())

        assertEquals(1_000L, governor.backoffDelayMillis(1))
        assertEquals(2_000L, governor.backoffDelayMillis(2))
        assertEquals(4_000L, governor.backoffDelayMillis(3))
        assertEquals(8_000L, governor.backoffDelayMillis(4))
        assertEquals(128_000L, governor.backoffDelayMillis(8))
        assertEquals(256_000L, governor.backoffDelayMillis(9))
        assertEquals(QuotaGovernor.MAX_BACKOFF_MILLIS, governor.backoffDelayMillis(10))
        assertEquals(QuotaGovernor.MAX_BACKOFF_MILLIS, governor.backoffDelayMillis(500))
        assertEquals(QuotaGovernor.BASE_BACKOFF_MILLIS, governor.backoffDelayMillis(0))
    }

    @Test
    fun `resource exhausted bypasses backoff entirely`() {
        val governor = QuotaGovernor(pacificClock())

        assertNull(governor.delayForAttempt(1, SyncErrorKind.RESOURCE_EXHAUSTED))
        assertNull(governor.delayForAttempt(9, SyncErrorKind.RESOURCE_EXHAUSTED))
        assertNotNull(governor.delayForAttempt(1, SyncErrorKind.TRANSIENT))
        assertNotNull(governor.delayForAttempt(1, SyncErrorKind.UNKNOWN))
    }

    @Test
    fun `the lock message names the exact time sync resumes`() {
        val clock = pacificClock()
        val governor = QuotaGovernor(clock)

        assertNull(governor.lockMessage())

        governor.onResourceExhausted(QuotaKind.WRITES)
        val message = governor.lockMessage()

        assertNotNull(message)
        assertTrue(message.orEmpty().contains("00:00 PT"))
        assertTrue(message.orEmpty().contains("Free-tier limit reached"))
    }

    @Test
    fun `the default ceilings are sixty percent of the published quota`() {
        assertEquals(12_000L, QuotaGovernor.DEFAULT_WRITE_CEILING)
        assertEquals(30_000L, QuotaGovernor.DEFAULT_READ_CEILING)
    }

    @Test
    fun `the reset is the next midnight in the pacific zone`() {
        val from = nineAmPacific()

        val reset = QuotaGovernor.nextResetMillis(from)

        assertTrue(reset > from)
        val zoned = Instant.ofEpochMilli(reset).atZone(QuotaGovernor.PACIFIC)
        assertEquals(0, zoned.hour)
        assertEquals(0, zoned.minute)
        assertEquals(0, zoned.second)
    }

    /** A clock parked at nine in the morning on a Pacific summer day, when sync is most likely. */
    private fun pacificClock(): FakeClock = FakeClock(Instant.ofEpochMilli(nineAmPacific()))

    /** Nine in the morning on a Pacific summer day, when a device is most likely to sync. */
    private fun nineAmPacific(): Long =
        LocalDateTime.of(2026, 6, 15, 9, 0).toInstant(ZoneOffset.ofHours(-7)).toEpochMilli()

    /** Midnight Pacific the following day — when Spark's quota comes back. */
    private fun pacificMidnight(): Long =
        LocalDateTime.of(2026, 6, 16, 0, 0).toInstant(ZoneOffset.ofHours(-7)).toEpochMilli()

    /** Jumps the fake to an absolute instant, forwards or backwards. */
    private fun FakeClock.jumpTo(millis: Long) = advanceMillis(millis - nowMillis())

    /**
     * Stands in for the sync loop: it asks the governor first and only counts a request when
     * the governor says yes, so `requestsIssued` is the number of calls that reached the wire.
     */
    private class FakeEndpoint(
        private val governor: QuotaGovernor,
        private val failures: MutableList<SyncErrorKind>,
    ) {
        var requestsIssued: Int = 0
            private set

        /** Returns the failure the backend reported, or `null` when the request succeeded. */
        fun push(): SyncErrorKind? {
            if (governor.acquireWrite() !is QuotaDecision.Granted) return null
            requestsIssued++
            val failure = if (failures.isEmpty()) null else failures.removeAt(0)
            if (failure == SyncErrorKind.RESOURCE_EXHAUSTED) governor.onResourceExhausted(QuotaKind.WRITES)
            return failure
        }
    }
}
