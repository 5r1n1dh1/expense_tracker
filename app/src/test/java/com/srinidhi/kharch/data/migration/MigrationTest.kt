package com.srinidhi.kharch.data.migration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.data.local.AppDatabase
import com.srinidhi.kharch.data.local.DatabaseSeeder
import com.srinidhi.kharch.data.local.ExpenseEntity
import com.srinidhi.kharch.data.local.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration smoke test (plan §18.2.5).
 *
 * Opens a database at the current schema, seeds it, and exercises the DAO surface so that a
 * regression in the schema or the seeder fails here rather than on a user's upgraded install.
 * When a real migration is added it must extend this test to build the previous schema, run the
 * migration, and assert every row survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private lateinit var database: AppDatabase
    private val clock = FakeClock()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(DatabaseSeeder(clock))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun currentSchema_opensAndSeedsCategories() = runBlocking {
        val count = database.categoryDao().countAll()
        assertEquals(DatabaseSeeder.SEEDED_COUNT, count)
    }

    @Test
    fun currentSchema_roundTripsAnExpense() = runBlocking {
        val expense = ExpenseEntity(
            id = "exp-1",
            name = "Tea",
            amountPaise = 2500L,
            kind = ExpenseKind.EXPENSE,
            categoryId = "sys_food",
            dateEpochDay = 20000L,
            createdAt = clock.nowMillis(),
            updatedAt = clock.nowMillis(),
            syncState = SyncState.PENDING_UPSERT,
        )
        database.expenseDao().upsert(expense)
        val loaded = database.expenseDao().findById("exp-1")
        assertTrue(loaded != null)
        assertEquals("Tea", loaded?.name)
        assertEquals(2500L, loaded?.amountPaise)
    }

    @Test
    fun schemaVersion_matchesAppDatabase() {
        assertEquals(AppDatabase.SCHEMA_VERSION, MigrationTestHelper.LATEST_VERSION)
    }
}
