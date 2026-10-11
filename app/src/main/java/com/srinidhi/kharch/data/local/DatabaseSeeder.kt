package com.srinidhi.kharch.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.srinidhi.kharch.core.time.Clock

/**
 * One of the categories that ships with the app, before it becomes a [CategoryEntity].
 *
 * Kept separate from the entity so the seed data can be listed without a timestamp, and so it
 * can be turned into entities (or raw SQL) by whatever path needs it.
 *
 * @property id stable id such as `sys_food`. User categories use UUIDs instead.
 * @property iconKey key into the bundled symbol grid.
 * @property colorToken a semantic token, never a raw hex — see [CategoryEntity.colorToken].
 */
data class SeedCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorToken: String,
    val sortOrder: Int,
)

/**
 * The twelve seeded categories, in display order (plan §5.2, decision D4).
 *
 * Each is renameable and hideable like any other category; a user who would rather call Rent
 * "Mortgage", or who never wants to see it at all, can do both without losing their history.
 */
val SYSTEM_CATEGORIES: List<SeedCategory> = listOf(
    SeedCategory("sys_food", "Food & Drink", "restaurant", "category.food", 0),
    SeedCategory("sys_transport", "Transport", "directions_bus", "category.transport", 1),
    SeedCategory("sys_shopping", "Shopping", "shopping_bag", "category.shopping", 2),
    SeedCategory("sys_bills", "Bills & Utilities", "receipt_long", "category.bills", 3),
    SeedCategory("sys_rent", "Rent", "home", "category.rent", 4),
    SeedCategory("sys_health", "Health", "favorite", "category.health", 5),
    SeedCategory("sys_education", "Education", "school", "category.education", 6),
    SeedCategory("sys_entertainment", "Entertainment", "movie", "category.entertainment", 7),
    SeedCategory("sys_travel", "Travel", "flight", "category.travel", 8),
    SeedCategory("sys_groceries", "Groceries", "local_grocery_store", "category.groceries", 9),
    SeedCategory("sys_cash", "Cash", "payments", "category.cash", 10),
    SeedCategory("sys_other", "Other", "more_horiz", "category.other", 11),
)

/**
 * Inserts the twelve system categories the moment the database file is created (plan §5.2).
 *
 * Why raw SQL rather than a DAO call: `onCreate` runs inside the transaction that creates the
 * schema, so a coroutine launched from here could race the first real query and a DAO call
 * would need a scope nobody owns yet. `execSQL` is synchronous and inside the same transaction,
 * so the first reader always sees the categories already there.
 *
 * Why it is safe on every launch: `onCreate` fires exactly once per database file, and every
 * statement is `INSERT OR IGNORE` against a primary key, so calling [seed] again — after a
 * failed create, in a migration test, or from a second device — is a no-op.
 */
class DatabaseSeeder(private val clock: Clock) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        seed(db)
    }

    /**
     * Inserts any missing system category. Idempotent: existing rows are left exactly as the
     * user has them, so a rename or a hide is never reverted by a re-seed.
     */
    fun seed(db: SupportSQLiteDatabase) {
        val timestamp = clock.nowMillis()
        SYSTEM_CATEGORIES.forEach { category ->
            db.execSQL(
                INSERT_SQL,
                arrayOf(
                    category.id,
                    category.name,
                    category.iconKey,
                    category.colorToken,
                    category.sortOrder,
                    timestamp,
                ),
            )
        }
    }

    /** The same twelve rows as entities, for callers that prefer the DAO path. */
    fun seedEntities(): List<CategoryEntity> {
        val timestamp = clock.nowMillis()
        return SYSTEM_CATEGORIES.map { category ->
            CategoryEntity(
                id = category.id,
                name = category.name,
                iconKey = category.iconKey,
                colorToken = category.colorToken,
                sortOrder = category.sortOrder,
                isSystem = true,
                isHidden = false,
                deletedAt = null,
                updatedAt = timestamp,
            )
        }
    }

    companion object {
        /** How many categories the app ships with. Asserted by tests on every schema change. */
        const val SEEDED_COUNT: Int = 12

        /**
         * `INSERT OR IGNORE` on the primary key is what makes re-seeding a no-op.
         *
         * The column list is written out rather than relying on ordinal positions, so a future
         * column can be added without silently landing in the wrong slot. A schema change to
         * `categories` must update this statement and ship a migration test with it.
         */
        private val INSERT_SQL: String = """
            INSERT OR IGNORE INTO categories
                (id, name, iconKey, colorToken, sortOrder, isSystem, isHidden, deletedAt, updatedAt)
            VALUES (?, ?, ?, ?, ?, 1, 0, NULL, ?)
        """.trimIndent()
    }
}
