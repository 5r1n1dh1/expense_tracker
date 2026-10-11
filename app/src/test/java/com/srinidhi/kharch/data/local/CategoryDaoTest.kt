package com.srinidhi.kharch.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.srinidhi.kharch.core.model.ExpenseKind
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
 * Category CRUD, visibility, the batched reorder and the usage count that decides between hide
 * and delete (decision D4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
        expenseDao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an inserted category can be read back unchanged`() = runBlocking {
        val category = sampleCategory()
        categoryDao.insertIfAbsent(category)

        assertEquals(category, categoryDao.findById("sys_food"))
    }

    @Test
    fun `inserting the same id twice leaves one row`() = runBlocking {
        val firstInsert = categoryDao.insertIfAbsent(sampleCategory())
        val secondInsert = categoryDao.insertIfAbsent(sampleCategory(name = "Renamed"))

        assertTrue(firstInsert >= 0L)
        assertEquals(-1L, secondInsert)
        assertEquals(1, categoryDao.countAll())
        assertEquals("Food & Drink", categoryDao.findById("sys_food")?.name)
    }

    @Test
    fun `a bulk insert stores every category`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(sampleCategory(), sampleCategory(id = "sys_rent", name = "Rent", sortOrder = 1)),
        )

        assertEquals(2, categoryDao.countAll())
    }

    @Test
    fun `renaming updates only the name and the timestamp`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        categoryDao.setName("sys_food", "Eating out", updatedAt = 9L)

        val renamed = categoryDao.findById("sys_food")
        assertEquals("Eating out", renamed?.name)
        assertEquals(9L, renamed?.updatedAt)
        assertTrue(renamed?.isSystem == true)
    }

    @Test
    fun `hiding a category removes it from the visible list but keeps the row`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(sampleCategory(), sampleCategory(id = "sys_rent", name = "Rent", sortOrder = 1)),
        )
        categoryDao.setHidden("sys_food", true, updatedAt = 3L)

        assertEquals(listOf("sys_rent"), categoryDao.observeVisible().first().map { it.id })
        assertEquals(2, categoryDao.observeAll().first().size)
    }

    @Test
    fun `a hidden category still renders on historical expenses`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        categoryDao.setHidden("sys_food", true, updatedAt = 3L)
        expenseDao.upsert(sampleExpense())

        assertEquals("sys_food", expenseDao.observeAllLive().first().single().categoryId)
        assertTrue(categoryDao.findById("sys_food")?.isHidden == true)
    }

    @Test
    fun `visible categories come back in sort order`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(
                sampleCategory(id = "c", name = "Cash", sortOrder = 2),
                sampleCategory(id = "a", name = "Food", sortOrder = 0),
                sampleCategory(id = "b", name = "Rent", sortOrder = 1),
            ),
        )

        assertEquals(listOf("a", "b", "c"), categoryDao.observeVisible().first().map { it.id })
    }

    @Test
    fun `a soft deleted category leaves the visible list and is excluded from active counts`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        categoryDao.softDelete("sys_food", deletedAt = 7L)

        assertTrue(categoryDao.observeVisible().first().isEmpty())
        assertTrue(categoryDao.activeCategories().isEmpty())
        assertEquals(1, categoryDao.observeAll().first().size)
    }

    @Test
    fun `an unused category can be hard deleted`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())

        categoryDao.deleteById("sys_food")

        assertNull(categoryDao.findById("sys_food"))
        assertEquals(0, categoryDao.countAll())
    }

    @Test
    fun `a category in use reports its usage count and must be hidden instead`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        expenseDao.upsertAll(listOf(sampleExpense(id = "a"), sampleExpense(id = "b")))

        assertEquals(2, categoryDao.usageCount("sys_food"))
    }

    @Test
    fun `a tombstoned expense does not count towards category usage`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        expenseDao.upsert(sampleExpense())
        expenseDao.softDelete("exp-1", 5_000L, SyncState.PENDING_DELETE)

        assertEquals(0, categoryDao.usageCount("sys_food"))
    }

    @Test
    fun `usage counts are reported for every active category`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(
                sampleCategory(id = "sys_food", name = "Food", sortOrder = 0),
                sampleCategory(id = "sys_rent", name = "Rent", sortOrder = 1),
            ),
        )
        expenseDao.upsert(sampleExpense(id = "a", categoryId = "sys_food"))
        expenseDao.upsert(sampleExpense(id = "b", categoryId = "sys_food"))
        expenseDao.upsert(sampleExpense(id = "c", categoryId = "sys_rent"))

        val usage = categoryDao.observeUsageCounts().first()

        assertEquals(2, usage.size)
        assertEquals(2, usage.first { it.id == "sys_food" }.usageCount)
        assertEquals(1, usage.first { it.id == "sys_rent" }.usageCount)
    }

    @Test
    fun `a reorder writes contiguous sort orders starting at zero`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(
                sampleCategory(id = "a", name = "A", sortOrder = 0),
                sampleCategory(id = "b", name = "B", sortOrder = 1),
                sampleCategory(id = "c", name = "C", sortOrder = 2),
            ),
        )

        categoryDao.applyOrder(listOf("c", "a", "b"), updatedAt = 4L)

        assertEquals(listOf("c", "a", "b"), categoryDao.observeVisible().first().map { it.id })
        assertEquals(listOf(0, 1, 2), categoryDao.activeCategories().map { it.sortOrder })
    }

    @Test
    fun `a reorder stamps every touched row with the same timestamp`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(sampleCategory(id = "a", name = "A"), sampleCategory(id = "b", name = "B", sortOrder = 1)),
        )

        categoryDao.applyOrder(listOf("b", "a"), updatedAt = 4_242L)

        assertTrue(categoryDao.activeCategories().all { it.updatedAt == 4_242L })
    }

    @Test
    fun `renaming to a name that only differs in case is rejected`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        categoryDao.setName("sys_food", "FOOD & DRINK", updatedAt = 1L)

        val conflict = categoryDao.findByNameIgnoringCase("food & drink", excludeId = "sys_other")

        assertEquals("sys_food", conflict?.id)
    }

    @Test
    fun `a rename is allowed to keep its own name`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())

        assertNull(categoryDao.findByNameIgnoringCase("food & drink", excludeId = "sys_food"))
    }

    @Test
    fun `surrounding whitespace is ignored when checking for duplicates`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())

        assertEquals("sys_food", categoryDao.findByNameIgnoringCase("   Food & Drink  ", excludeId = "")?.id)
    }

    @Test
    fun `an unused ledger reports minus one as its highest sort order`() = runBlocking {
        assertEquals(-1, categoryDao.maxSortOrder())

        categoryDao.insertIfAbsent(sampleCategory(sortOrder = 7))

        assertEquals(7, categoryDao.maxSortOrder())
    }

    @Test
    fun `categories can be looked up by id in bulk`() = runBlocking {
        categoryDao.insertAllIfAbsent(
            listOf(sampleCategory(id = "a", name = "A"), sampleCategory(id = "b", name = "B", sortOrder = 1)),
        )

        val found = categoryDao.findByIds(listOf("a", "b"))

        assertEquals(listOf("a", "b"), found.map { it.id })
    }

    @Test
    fun `an update writes the whole row`() = runBlocking {
        categoryDao.insertIfAbsent(sampleCategory())
        val renamed = sampleCategory(name = "Chai", iconKey = "coffee", colorToken = "category.other", sortOrder = 3)

        categoryDao.update(renamed.copy(updatedAt = 11L))

        val stored = categoryDao.findById("sys_food")
        assertEquals("Chai", stored?.name)
        assertEquals("coffee", stored?.iconKey)
        assertEquals(11L, stored?.updatedAt)
    }

    @Test
    fun `a missing category reads back as null`() = runBlocking {
        assertNull(categoryDao.findById("nope"))
        assertFalse(categoryDao.observeAll().first().any { it.id == "nope" })
    }

    private fun sampleCategory(
        id: String = "sys_food",
        name: String = "Food & Drink",
        sortOrder: Int = 0,
    ) = CategoryEntity(
        id = id,
        name = name,
        iconKey = "restaurant",
        colorToken = "category.food",
        sortOrder = sortOrder,
        isSystem = true,
        isHidden = false,
        deletedAt = null,
        updatedAt = 1L,
    )

    private fun sampleExpense(
        id: String = "exp-1",
        categoryId: String = "sys_food",
    ) = ExpenseEntity(
        id = id,
        name = "Filter coffee",
        amountPaise = 500_00L,
        kind = ExpenseKind.EXPENSE,
        categoryId = categoryId,
        dateEpochDay = 20_710L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )
}
