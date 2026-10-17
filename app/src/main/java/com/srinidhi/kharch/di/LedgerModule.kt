package com.srinidhi.kharch.di

import android.content.Context
import com.srinidhi.kharch.core.time.Clock
import com.srinidhi.kharch.core.time.SystemClock
import com.srinidhi.kharch.data.local.AppDatabase
import com.srinidhi.kharch.data.local.CategoryDao
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.local.WorkspaceDao
import com.srinidhi.kharch.data.prefs.SettingsStore
import com.srinidhi.kharch.data.repository.LocalExpenseRepository
import com.srinidhi.kharch.data.repository.RoomCategoryCatalog
import com.srinidhi.kharch.domain.expense.CategoryCatalog
import com.srinidhi.kharch.domain.expense.ExpenseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/*
 * Phase 5's dependency graph.
 *
 * NOTE FOR PHASE 2/3 — this is the seam, and it is the whole reason it is written this way:
 *
 *  - `domain.expense.ExpenseRepository` and `domain.expense.CategoryCatalog` are interfaces
 *    Phase 5 owns. Replace the two `@Provides` functions below with the real Room + Firestore
 *    implementations and delete the two provisional classes in `data/repository/`. Nothing in
 *    the UI, the ViewModels or the tests needs to change: they only ever see the interfaces.
 *  - `Clock` is provided here because nothing else was. Domain code never reads the machine
 *    clock directly (plan §13.2), so a binding has to exist for it to be injectable at all.
 *  - The Room bindings exist for the same reason. If Phase 2/3 add their own database module,
 *    keep exactly one of the two — a duplicate `@Provides` for the same type is a Hilt
 *    compile error, not a runtime fallback.
 *
 * `Room.databaseBuilder(...).build()` does not open the database; the first DAO query does, on
 * Room's own executor. So providing it here costs nothing on the startup path.
 */
@Module
@InstallIn(SingletonComponent::class)
object LedgerModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context, clock: Clock): AppDatabase =
        AppDatabase.builder(context, clock).build()

    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    @Singleton
    fun provideExpenseRepository(dao: ExpenseDao, clock: Clock): ExpenseRepository =
        LocalExpenseRepository(dao, clock)

    @Provides
    @Singleton
    fun provideCategoryCatalog(dao: CategoryDao): CategoryCatalog = RoomCategoryCatalog(dao)

    /**
     * Settings and the workspace pointer. Added here so sync (Phase 2/3) has one
     * place to resolve both, instead of pulling `Context` down into a ViewModel.
     */
    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore.create(context)

    @Provides
    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDao = database.workspaceDao()
}
