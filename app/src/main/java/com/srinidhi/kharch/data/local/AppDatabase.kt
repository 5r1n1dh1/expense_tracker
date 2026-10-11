package com.srinidhi.kharch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.srinidhi.kharch.core.time.Clock

/**
 * The single Room database (decision D1).
 *
 * There is exactly **one** of these per install. Google accounts are credentials *into* the
 * workspace, never containers of it, so no query in the codebase carries an account filter and
 * there is no code path that could split or merge two users' data by accident.
 *
 * Migration policy (P9, plan §18.2):
 *  - `exportSchema = true`. Every version's schema JSON is committed under `app/schemas/`,
 *    which is the only way `MigrationTestHelper` can test anything at all.
 *  - Migrations are hand-written and registered explicitly on [builder]. Automatic migration is
 *    not used, because this schema needs data transformation, not just DDL.
 *  - There is no destructive fallback anywhere in this class, by design: silently deleting a
 *    user's ledger is the one failure mode an expense app must never have. A missing migration
 *    crashes loudly, in development, rather than quietly wiping a decade of records.
 */
@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        WorkspaceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        /**
         * Schema version. Keep this in step with the literal in the `@Database` annotation
         * above — the annotation needs a compile-time constant, so the two cannot be linked.
         */
        const val SCHEMA_VERSION: Int = 1

        const val DATABASE_NAME: String = "kharch.db"

        /**
         * Production builder. Seeds the twelve system categories on first create and registers
         * no migrations yet, because there is nothing to migrate from at version 1.
         *
         * @param clock supplies the seed timestamp; injected so the seeder is deterministic
         *   under test.
         */
        fun builder(context: Context, clock: Clock): Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(DatabaseSeeder(clock))
    }
}
