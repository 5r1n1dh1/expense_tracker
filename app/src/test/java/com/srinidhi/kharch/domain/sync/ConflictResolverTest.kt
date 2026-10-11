package com.srinidhi.kharch.domain.sync

import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.time.MILLIS_PER_DAY
import com.srinidhi.kharch.domain.date
import com.srinidhi.kharch.domain.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last-write-wins matrix of §6.4, row by row.
 *
 * The pairwise cases are asserted against a table rather than a prose list, because the whole
 * point of this resolver is that *every* combination lands on exactly one row — including the
 * ones nobody thought about, such as two devices disagreeing about who wrote last.
 */
class ConflictResolverTest {

    @Test
    fun `a row that only one device has wins`() {
        val local = row(updatedAt = 100L, authorUid = "alice")
        val remote = row(updatedAt = 200L, authorUid = "bob")

        assertSame(remote, ConflictResolver.resolve(local, null))
        assertSame(local, ConflictResolver.resolve(null, remote))
    }

    @Test
    fun `an expense neither device holds resolves to nothing`() {
        assertNull(ConflictResolver.resolve(null, null))
    }

    @Test
    fun `remote newer wins`() {
        val local = row(updatedAt = 100L, authorUid = "alice")
        val remote = row(updatedAt = 200L, authorUid = "alice")

        assertEquals(remote, ConflictResolver.resolve(local, remote))
    }

    @Test
    fun `local newer wins`() {
        val local = row(updatedAt = 200L, authorUid = "alice")
        val remote = row(updatedAt = 100L, authorUid = "alice")

        assertEquals(local, ConflictResolver.resolve(local, remote))
    }

    @Test
    fun `delete beats update at equal timestamps`() {
        val local = row(updatedAt = 300L, authorUid = "alice")
        val remote = row(updatedAt = 300L, authorUid = "alice", deletedAt = 300L)

        assertEquals(remote, ConflictResolver.resolve(local, remote))
        assertEquals(remote, ConflictResolver.resolve(remote, local))
    }

    @Test
    fun `an update beats a delete only when it is strictly newer`() {
        val tombstone = row(updatedAt = 300L, authorUid = "alice", deletedAt = 300L)
        val laterEdit = row(updatedAt = 301L, authorUid = "alice")

        assertEquals(laterEdit, ConflictResolver.resolve(tombstone, laterEdit))
    }

    @Test
    fun `a tombstone propagates to the other device even when it arrives late`() {
        val deletedElsewhere = row(updatedAt = 900L, authorUid = "bob", deletedAt = 900L)
        val staleLocalCopy = row(updatedAt = 100L, authorUid = "alice")

        val merged = ConflictResolver.resolve(staleLocalCopy, deletedElsewhere)

        check(merged != null)
        assertTrue(merged.isTombstone)
        assertEquals(900L, merged.updatedAt)
    }

    @Test
    fun `equal timestamps from two members converge on the lexicographically smaller uid`() {
        val aliceEdit = row(updatedAt = 500L, authorUid = "alice", amountPaise = 50_000L)
        val bobEdit = row(updatedAt = 500L, authorUid = "bob", amountPaise = 70_000L)

        val onAliceDevice = ConflictResolver.resolve(aliceEdit, bobEdit)
        val onBobDevice = ConflictResolver.resolve(bobEdit, aliceEdit)

        check(onAliceDevice != null)
        check(onBobDevice != null)
        assertEquals(onAliceDevice, onBobDevice)
        assertEquals("alice", onAliceDevice.authorUid)
        assertEquals(50_000L, onAliceDevice.expense.amount.paise)
    }

    @Test
    fun `identical rows from the same member resolve to that row`() {
        val only = row(updatedAt = 42L, authorUid = "alice")

        assertSame(only, ConflictResolver.resolve(only, only))
    }

    @Test
    fun `resolving is repeatable and idempotent`() {
        val alice = row(updatedAt = 500L, authorUid = "alice")
        val bob = row(updatedAt = 500L, authorUid = "bob")

        val once = ConflictResolver.resolve(alice, bob)
        val twice = ConflictResolver.resolve(once, bob)

        assertEquals(once, twice)
        assertEquals(once, ConflictResolver.resolve(bob, once))
    }

    @Test
    fun `resolution is symmetric in its arguments`() {
        for ((left, right) in PAIRWISE_CASES) {
            assertEquals(
                "resolve(left, right) must equal resolve(right, left)",
                ConflictResolver.resolve(left, right),
                ConflictResolver.resolve(right, left),
            )
        }
    }

    @Test
    fun `every pairwise case elects exactly one of the two rows`() {
        for ((left, right) in PAIRWISE_CASES) {
            val winner = ConflictResolver.survivor(left, right)
            val mirrored = ConflictResolver.survivor(right, left)

            assertEquals("symmetric winner for $left vs $right", winner, mirrored)
            assertTrue("winner must be one of the two rows", winner == left || winner == right)
        }
    }

    @Test
    fun `a backwards clock cannot win because the guard raises the timestamp`() {
        val local = row(updatedAt = 5_000L, authorUid = "alice")
        val remote = row(updatedAt = 9_000L, authorUid = "bob")

        // The device clock says 4 000 ms, behind everything it has already seen.
        val guarded = ConflictResolver.applyMonotonicGuard(local, nowMillis = 4_000L, lastSeenUpdatedAt = 9_000L)

        assertEquals(9_000L, guarded.updatedAt)
        assertEquals(remote, ConflictResolver.resolve(guarded, remote))
    }

    @Test
    fun `the guard uses the clock when it is ahead`() {
        val local = row(updatedAt = 1_000L, authorUid = "alice")

        val guarded = ConflictResolver.applyMonotonicGuard(local, nowMillis = 7_000L, lastSeenUpdatedAt = 2_000L)

        assertEquals(7_000L, guarded.updatedAt)
    }

    @Test
    fun `the guard never rewinds a row that is already ahead`() {
        val local = row(updatedAt = 9_000L, authorUid = "alice")

        val guarded = ConflictResolver.applyMonotonicGuard(local, nowMillis = 1_000L, lastSeenUpdatedAt = 1_000L)

        assertEquals(9_000L, guarded.updatedAt)
        assertSame(local, guarded)
    }

    @Test
    fun `the guard is the maximum of the clock and the newest row the ledger has seen`() {
        assertEquals(9_000L, ConflictResolver.monotonicTimestamp(4_000L, 9_000L))
        assertEquals(7_000L, ConflictResolver.monotonicTimestamp(7_000L, 2_000L))

        val rows = listOf(
            row(updatedAt = 100L, authorUid = "alice"),
            row(updatedAt = 900L, authorUid = "bob"),
            row(updatedAt = 400L, authorUid = "alice"),
        )
        assertEquals(900L, ConflictResolver.latestUpdatedAt(rows))
        assertNull(ConflictResolver.latestUpdatedAt(emptyList()))
    }

    @Test
    fun `only tombstones older than thirty days are purgeable`() {
        val tombstone = row(updatedAt = 0L, authorUid = "alice", deletedAt = 1_000L)
        val live = row(updatedAt = 0L, authorUid = "alice")
        val future = row(updatedAt = 0L, authorUid = "alice", deletedAt = MILLIS_PER_DAY)

        assertFalse(ConflictResolver.isPurgeable(tombstone, nowMillis = 1_000L))
        assertFalse(ConflictResolver.isPurgeable(tombstone, nowMillis = 1_000L + MILLIS_PER_DAY * 29))
        assertTrue(ConflictResolver.isPurgeable(tombstone, nowMillis = 1_000L + MILLIS_PER_DAY * 30))
        assertFalse(ConflictResolver.isPurgeable(live, nowMillis = MILLIS_PER_DAY * 365))
        assertFalse(ConflictResolver.isPurgeable(future, nowMillis = 0L))
    }

    private companion object {
        private val base = expense("shared", date(2026, 4, 10), 500)

        private fun row(updatedAt: Long, authorUid: String, deletedAt: Long? = null, amountPaise: Long = 50_000L) =
            LedgerRow(
                base.copy(updatedAt = updatedAt, deletedAt = deletedAt, amount = MagnitudePaise(amountPaise)),
                authorUid,
            )

        /** Every combination the resolver has to give a total, symmetric answer for. */
        val PAIRWISE_CASES: List<Pair<LedgerRow, LedgerRow>> = listOf(
            row(100L, "alice") to row(200L, "alice"),
            row(200L, "alice") to row(100L, "bob"),
            row(300L, "alice") to row(300L, "bob"),
            row(300L, "alice") to row(300L, "alice", deletedAt = 300L),
            row(300L, "bob") to row(300L, "alice", deletedAt = 300L),
            row(400L, "alice", deletedAt = 400L) to row(300L, "bob"),
            row(300L, "alice") to row(400L, "bob", deletedAt = 400L),
            row(400L, "alice", deletedAt = 400L) to row(400L, "bob", deletedAt = 400L),
            row(500L, "alice") to row(500L, "alice"),
            row(500L, "bob") to row(500L, "carol"),
        )
    }
}
