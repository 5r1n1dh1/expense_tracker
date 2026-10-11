package com.srinidhi.kharch.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.srinidhi.kharch.core.time.FakeClock
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
 * The twelve seeded categories (plan §5.2).
 *
 * The properties that matter are not "does it insert" but "does re-seeding ever clobber what
 * the user did" — a rename or a hide must survive, because those are the changes a user made
 * and a silent revert would look like data loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseSeederTest {
    private lateinit var database: AppDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(DatabaseSeeder(FakeClock()))
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating the database seeds exactly twelve categories`() = runBlocking {
        assertEquals(DatabaseSeeder.SEEDED_COUNT, categoryDao.countAll())
        assertEquals(12, DatabaseSeeder.SEEDED_COUNT)
        assertEquals(12, SYSTEM_CATEGORIES.size)
    }

    @Test
    fun `the seeded ids match the ids the plan pins`() = runBlocking {
        val ids = categoryDao.activeCategories().map { it.id }

        assertEquals(SYSTEM_CATEGORIES.map { it.id }, ids)
        assertTrue(ids.contains("sys_food"))
        assertTrue(ids.contains("sys_rent"))
        assertTrue(ids.contains("sys_other"))
    }

    @Test
    fun `seeded categories arrive in display order with contiguous sort orders`() = runBlocking {
        val categories = categoryDao.activeCategories()

        assertEquals(SYSTEM_CATEGORIES.map { it.name }, categories.map { it.name })
        assertEquals(List(12) { it }, categories.map { it.sortOrder })
    }

    @Test
    fun `seeded categories are system categories that are visible and undeleted`() = runBlocking {
        val categories = categoryDao.activeCategories()

        assertTrue(categories.all { it.isSystem })
        assertTrue(categories.none { it.isHidden })
        assertTrue(categories.all { it.deletedAt == null })
    }

    @Test
    fun `every seeded category carries an icon key and a semantic colour token`() = runBlocking {
        assertTrue(categoryDao.activeCategories().all { it.iconKey.isNotBlank() })
        assertTrue(categoryDao.activeCategories().all { it.colorToken.startsWith("category.") })
    }

    @Test
    fun `seeding a second time inserts nothing new`() = runBlocking {
        val before = categoryDao.activeCategories()

        DatabaseSeeder(FakeClock()).seed(database.openHelper.writableDatabase)

        assertEquals(before, categoryDao.activeCategories())
        assertEquals(DatabaseSeeder.SEEDED_COUNT, categoryDao.countAll())
    }

    @Test
    fun `seeding repeatedly never duplicates a category`() = runBlocking {
        repeat(3) {
            DatabaseSeeder(FakeClock()).seed(database.openHelper.writableDatabase)
        }

        assertEquals(DatabaseSeeder.SEEDED_COUNT, categoryDao.countAll())
    }

    @Test
    fun `re-seeding does not revert a user rename`() = runBlocking {
        categoryDao.setName("sys_food", "Chai", updatedAt = 99L)

        DatabaseSeeder(FakeClock()).seed(database.openHelper.writableDatabase)

        assertEquals("Chai", categoryDao.findById("sys_food")?.name)
    }

    @Test
    fun `re-seeding does not unhide a hidden category`() = runBlocking {
        categoryDao.setHidden("sys_rent", true, updatedAt = 99L)

        DatabaseSeeder(FakeClock()).seed(database.openHelper.writableDatabase)

        assertTrue(categoryDao.findById("sys_rent")?.isHidden == true)
        assertEquals(DatabaseSeeder.SEEDED_COUNT - 1, categoryDao.observeVisible().first().size)
    }

    @Test
    fun `the programmatic seed path produces the same rows`() = runBlocking {
        val entities = DatabaseSeeder(FakeClock()).seedEntities()

        assertEquals(DatabaseSeeder.SEEDED_COUNT, entities.size)
        assertEquals(SYSTEM_CATEGORIES.map { it.id }, entities.map { it.id })
        assertTrue(entities.all { it.isSystem })
        assertFalse(entities.any { it.isHidden })
        assertNull(entities.first().deletedAt)
    }

    @Test
    fun `the programmatic seed path is insertable without touching existing rows`() = runBlocking {
        val rows = DatabaseSeeder(FakeClock()).seedEntities()

        categoryDao.insertAllIfAbsent(rows)

        assertEquals(DatabaseSeeder.SEEDED_COUNT, categoryDao.countAll())
    }
}
