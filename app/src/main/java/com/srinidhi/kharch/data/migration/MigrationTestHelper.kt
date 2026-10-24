package com.srinidhi.kharch.data.migration

import android.content.Context
import androidx.room.Room
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.data.local.AppDatabase

/**
 * Harness for exercising Room migrations without data loss (P9, plan §18.2).
 *
 * Migrations are hand-written and registered on [AppDatabase.builder]. This helper keeps the
 * test-facing entry points in one place so a migration smoke test builds the database through
 * the production builder rather than a private copy that could drift.
 *
 * Rules enforced by the surrounding tooling:
 *  - `destructive-migration-fallback` is banned by `scripts/check-no-destructive-migration.sh`.
 *  - Every schema version exports its JSON under `app/schemas/` (`room.schemaLocation`).
 */
object MigrationTestHelper {
    /** The schema version the app ships at. Kept in step with `AppDatabase.SCHEMA_VERSION`. */
    const val LATEST_VERSION: Int = AppDatabase.SCHEMA_VERSION

    /** Production database file name used by the smoke test. */
    const val DATABASE_NAME: String = AppDatabase.DATABASE_NAME

    /**
     * Returns the production builder so a test exercises the same wiring as a real install,
     * including the seeder callback and the registered migrations.
     */
    fun builder(context: Context, clock: Clock): androidx.room.RoomDatabase.Builder<AppDatabase> =
        AppDatabase.builder(context, clock)

    /**
     * Convenience for tests that only need an in-memory database at the current schema: it
     * avoids a second copy of the builder call sites in every test class.
     */
    fun inMemoryBuilder(
        context: Context,
        clock: Clock,
    ): androidx.room.RoomDatabase.Builder<AppDatabase> =
        Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
            .addCallback(com.srinidhi.kharch.data.local.DatabaseSeeder(clock))
}
