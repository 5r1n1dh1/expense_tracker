package com.srinidhi.kharch.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room CRUD, date-range queries and the SQL aggregates (plan §13.2).
 *
 * Robolectric supplies a real SQLite engine, so the half-open range queries and the
 * `CASE WHEN kind = ...` sums are exercised by the same engine that ships in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExpenseDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        expenseDao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an inserted expense can be read back unchanged`() = runBlocking {
        val expense = sampleExpense()
        expenseDao.upsert(expense)

        assertEquals(expense, expenseDao.findById("exp-1"))
    }

    @Test
    fun `a missing id reads back as null`() = runBlocking {
        assertNull(expenseDao.findById("nope"))
    }

    @Test
    fun `re-inserting the same id replaces the row`() = runBlocking {
        expenseDao.upsert(sampleExpense(name = "Old name"))
        expenseDao.upsert(sampleExpense(name = "New name"))

        assertEquals(1, expenseDao.observeAllLive().first().size)
        assertEquals("New name", expenseDao.findById("exp-1")?.name)
    }

    @Test
    fun `a bulk insert stores every row`() = runBlocking {
        expenseDao.upsertAll(listOf(sampleExpense(id = "a"), sampleExpense(id = "b"), sampleExpense(id = "c")))

        assertEquals(3, expenseDao.observeAllLive().first().size)
    }

    @Test
    fun `a hard delete removes the row`() = runBlocking {
        expenseDao.upsert(sampleExpense())
        expenseDao.deleteById("exp-1")

        assertNull(expenseDao.findById("exp-1"))
    }

    @Test
    fun `a soft delete leaves a tombstone that no list query returns`() = runBlocking {
        expenseDao.upsert(sampleExpense())
        expenseDao.softDelete("exp-1", deletedAt = 5_000L, syncState = SyncState.PENDING_DELETE)

        val tombstone = expenseDao.findById("exp-1")
        assertEquals(5_000L, tombstone?.deletedAt)
        assertEquals(SyncState.PENDING_DELETE, tombstone?.syncState)
        assertTrue(expenseDao.observeAllLive().first().isEmpty())
    }

    @Test
    fun `a tombstone older than the cutoff is swept`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "old", dateEpochDay = epochDayOf(AUGUST_2026, 2)))
        expenseDao.softDelete("old", deletedAt = 1_000L, syncState = SyncState.PENDING_DELETE)
        expenseDao.upsert(sampleExpense(id = "recent", dateEpochDay = epochDayOf(AUGUST_2026, 3)))
        expenseDao.softDelete("recent", deletedAt = 9_000L, syncState = SyncState.PENDING_DELETE)

        val swept = expenseDao.purgeTombstones(olderThanMillis = 5_000L)

        assertEquals(1, swept)
        assertNull(expenseDao.findById("old"))
        assertEquals(9_000L, expenseDao.findById("recent")?.deletedAt)
    }

    @Test
    fun `a date range query is half open so consecutive months never overlap`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "jul", dateEpochDay = JULY_2026.lastEpochDay))
        expenseDao.upsert(sampleExpense(id = "aug", dateEpochDay = AUGUST_2026.firstEpochDay))

        val august = expenseDao.observeLiveBetween(AUGUST_2026.firstEpochDay, AUGUST_2026.endEpochDayExclusive).first()

        assertEquals(listOf("aug"), august.map { it.id })
    }

    @Test
    fun `a date range query includes the last day of the month`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "last", dateEpochDay = AUGUST_2026.lastEpochDay))

        val rows = expenseDao.observeLiveBetween(AUGUST_2026.firstEpochDay, AUGUST_2026.endEpochDayExclusive).first()

        assertEquals(listOf("last"), rows.map { it.id })
    }

    @Test
    fun `a category filter returns only that category`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "food", categoryId = "sys_food"))
        expenseDao.upsert(sampleExpense(id = "rent", categoryId = "sys_rent"))

        val rows = expenseDao.observeLiveInCategories(listOf("sys_food")).first()

        assertEquals(listOf("food"), rows.map { it.id })
    }

    @Test
    fun `a multi category filter returns every selected category`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "food", categoryId = "sys_food"))
        expenseDao.upsert(sampleExpense(id = "rent", categoryId = "sys_rent"))
        expenseDao.upsert(sampleExpense(id = "cash", categoryId = "sys_cash"))

        val rows = expenseDao.observeLiveInCategories(listOf("sys_food", "sys_rent")).first()

        assertEquals(setOf("food", "rent"), rows.map { it.id }.toSet())
    }

    @Test
    fun `a category and date filter narrows both ways`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "in", categoryId = "sys_food", dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(
            sampleExpense(id = "out-month", categoryId = "sys_food", dateEpochDay = JULY_2026.firstEpochDay),
        )
        expenseDao.upsert(
            sampleExpense(id = "out-cat", categoryId = "sys_rent", dateEpochDay = AUGUST_2026.firstEpochDay),
        )

        val rows = expenseDao.observeLiveForCategoryBetween(
            categoryId = "sys_food",
            fromEpochDay = AUGUST_2026.firstEpochDay,
            toEpochDayExclusive = AUGUST_2026.endEpochDayExclusive,
        ).first()

        assertEquals(listOf("in"), rows.map { it.id })
    }

    @Test
    fun `pending rows come back oldest change first`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "newer", updatedAt = 200L))
        expenseDao.upsert(sampleExpense(id = "older", updatedAt = 100L))
        expenseDao.upsert(sampleExpense(id = "synced", updatedAt = 50L, syncState = SyncState.SYNCED))

        val pending = expenseDao.pendingForSync(listOf(SyncState.PENDING_UPSERT, SyncState.PENDING_DELETE), limit = 10)

        assertEquals(listOf("older", "newer"), pending.map { it.id })
    }

    @Test
    fun `the pending count drives the offline badge`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "a"))
        expenseDao.upsert(sampleExpense(id = "b", syncState = SyncState.SYNCED))
        expenseDao.upsert(sampleExpense(id = "c", syncState = SyncState.PENDING_DELETE))

        val pending = expenseDao.observePendingCount(listOf(SyncState.PENDING_UPSERT, SyncState.PENDING_DELETE)).first()

        assertEquals(2, pending)
    }

    @Test
    fun `marking a batch synced clears the pending flag`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "a"))
        expenseDao.upsert(sampleExpense(id = "b"))
        expenseDao.setSyncStates(listOf("a", "b"), SyncState.SYNCED)

        assertEquals(0, expenseDao.observePendingCount(listOf(SyncState.PENDING_UPSERT)).first())
        assertEquals(2, expenseDao.observeAllLive().first().size)
    }

    @Test
    fun `reassigning a category moves live rows and marks them pending`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "a", categoryId = "sys_food", syncState = SyncState.SYNCED))
        expenseDao.upsert(sampleExpense(id = "b", categoryId = "sys_food", syncState = SyncState.SYNCED))

        val moved = expenseDao.reassignCategory("sys_food", "sys_other", 7L, SyncState.PENDING_UPSERT)

        assertEquals(2, moved)
        assertEquals(2, expenseDao.observeLiveInCategories(listOf("sys_other")).first().size)
        assertEquals(2, expenseDao.observePendingCount(listOf(SyncState.PENDING_UPSERT)).first())
    }

    @Test
    fun `summing one direction over a range uses long arithmetic`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "a", amountPaise = 100_00L, dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(sampleExpense(id = "b", amountPaise = 250_50L, dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(sampleExpense(id = "july", amountPaise = 999_00L, dateEpochDay = JULY_2026.firstEpochDay))

        val gross = expenseDao.sumPaise(
            AUGUST_2026.firstEpochDay,
            AUGUST_2026.endEpochDayExclusive,
            ExpenseKind.EXPENSE,
        )

        assertEquals(350_50L, gross)
    }

    @Test
    fun `the net of a month nets off refunds from charges`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "charge", amountPaise = 500_00L, dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(
            sampleExpense(
                id = "refund",
                amountPaise = 700_00L,
                kind = ExpenseKind.REFUND,
                dateEpochDay = AUGUST_2026.firstEpochDay,
            ),
        )

        val net = expenseDao.netPaise(AUGUST_2026.firstEpochDay, AUGUST_2026.endEpochDayExclusive, ExpenseKind.EXPENSE)

        assertEquals(-200_00L, net)
    }

    @Test
    fun `a net negative month is reported as negative rather than clamped`() = runBlocking {
        expenseDao.upsert(
            sampleExpense(id = "refund", amountPaise = 900_00L, kind = ExpenseKind.REFUND),
        )

        val net = expenseDao.observeNetPaise(
            AUGUST_2026.firstEpochDay,
            AUGUST_2026.endEpochDayExclusive,
            ExpenseKind.EXPENSE,
        ).first()

        assertEquals(-900_00L, net)
        assertTrue(net < 0L)
    }

    @Test
    fun `an empty range sums to zero rather than failing`() = runBlocking {
        assertEquals(
            0L,
            expenseDao.netPaise(
                AUGUST_2026.firstEpochDay,
                AUGUST_2026.endEpochDayExclusive,
                ExpenseKind.EXPENSE,
            ),
        )
    }

    @Test
    fun `a tombstone never contributes to a total`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "gone", amountPaise = 400_00L))
        expenseDao.softDelete("gone", 5_000L, SyncState.PENDING_DELETE)

        val gross = expenseDao.sumPaise(
            AUGUST_2026.firstEpochDay,
            AUGUST_2026.endEpochDayExclusive,
            ExpenseKind.EXPENSE,
        )

        assertEquals(0L, gross)
    }

    @Test
    fun `daily nets group by day and split charges from refunds`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "a", amountPaise = 100_00L, dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(sampleExpense(id = "b", amountPaise = 50_00L, dateEpochDay = AUGUST_2026.firstEpochDay))
        expenseDao.upsert(
            sampleExpense(
                id = "c",
                amountPaise = 30_00L,
                kind = ExpenseKind.REFUND,
                dateEpochDay = AUGUST_2026.lastEpochDay,
            ),
        )

        val daily = expenseDao.observeDailyNet(
            AUGUST_2026.firstEpochDay,
            AUGUST_2026.endEpochDayExclusive,
            ExpenseKind.EXPENSE,
            ExpenseKind.REFUND,
        ).first()

        assertEquals(2, daily.size)
        assertEquals(DailyNet(AUGUST_2026.firstEpochDay, 150_00L, 0L, 150_00L), daily.first())
        assertEquals(DailyNet(AUGUST_2026.lastEpochDay, 0L, 30_00L, -30_00L), daily.last())
    }

    @Test
    fun `observing a single expense emits null then the row`() = runBlocking {
        assertNull(expenseDao.observeById("exp-1").first())

        expenseDao.upsert(sampleExpense())

        assertEquals("Filter coffee", expenseDao.observeById("exp-1").first()?.name)
    }

    @Test
    fun `a linked refund keeps its pointer through storage`() = runBlocking {
        expenseDao.upsert(sampleExpense(id = "refund", kind = ExpenseKind.REFUND, linkedExpenseId = "exp-original"))

        assertEquals("exp-original", expenseDao.findById("refund")?.linkedExpenseId)
        assertEquals(ExpenseKind.REFUND, expenseDao.findById("refund")?.kind)
    }

    @Test
    fun `a new expense starts pending and is not yet synced`() = runBlocking {
        expenseDao.upsert(sampleExpense())

        val stored = expenseDao.findById("exp-1")
        assertEquals(SyncState.PENDING_UPSERT, stored?.syncState)
        assertTrue(stored?.syncState?.isPending == true)
        assertFalse(SyncState.SYNCED.isPending)
    }

    private fun sampleExpense(
        id: String = "exp-1",
        name: String = "Filter coffee",
        amountPaise: Long = 500_00L,
        kind: ExpenseKind = ExpenseKind.EXPENSE,
        categoryId: String = "sys_food",
        dateEpochDay: Long = AUGUST_2026.firstEpochDay,
        linkedExpenseId: String? = null,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L,
        syncState: SyncState = SyncState.PENDING_UPSERT,
    ) = ExpenseEntity(
        id = id,
        name = name,
        amountPaise = amountPaise,
        kind = kind,
        categoryId = categoryId,
        dateEpochDay = dateEpochDay,
        linkedExpenseId = linkedExpenseId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncState = syncState,
    )

    private companion object {
        val JULY_2026: YearMonth = YearMonth(2026, 7)
        val AUGUST_2026: YearMonth = YearMonth(2026, 8)

        fun epochDayOf(month: YearMonth, day: Int): Long = month.atDay(day).toEpochDay()
    }
}
