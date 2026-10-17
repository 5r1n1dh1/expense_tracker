package com.srinidhi.kharch.sync

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.data.local.CategoryDao
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.local.OutboxDao
import com.srinidhi.kharch.data.local.WorkspaceDao
import com.srinidhi.kharch.data.local.WorkspaceEntity
import com.srinidhi.kharch.data.remote.RemoteLedger
import com.srinidhi.kharch.data.remote.SyncException
import com.srinidhi.kharch.data.remote.dto.ExpenseDto
import com.srinidhi.kharch.data.remote.dto.SyncMetaDto
import com.srinidhi.kharch.domain.sync.QuotaGovernor
import com.srinidhi.kharch.domain.sync.SyncErrorKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {

    private fun clock(): FakeClock = FakeClock()

    private fun governor(clock: FakeClock): QuotaGovernor =
        QuotaGovernor(clock, writeCeiling = 10000L, readCeiling = 10000L)

    private fun workspaceDao(): WorkspaceDao {
        val dao = mockk<WorkspaceDao>(relaxed = true)
        coEvery { dao.current() } returns WorkspaceEntity(
            id = "wid",
            memberUids = listOf("u1"),
            createdAt = 1L,
            schemaVersion = 1,
        )
        return dao
    }

    @Test
    fun `requestSync pulls a page and persists the watermark`() = runTest {
        val clock = clock()
        val remote = mockk<RemoteLedger>(relaxed = true)
        coEvery { remote.readMeta("wid") } returns SyncMetaDto(0L, 0L, 0L, 0L, null)
        coEvery { remote.pullExpenses("wid", 0L, 400) } returns listOf(
            ExpenseDto(
                id = "e1",
                name = "lunch",
                amountPaise = 50000L,
                kind = ExpenseKind.EXPENSE,
                currencyCode = "INR",
                categoryId = "sys_food",
                dateEpochDay = 1L,
                createdAt = 1L,
                updatedAt = 5L,
            ),
        )
        coEvery { remote.pullCategories("wid", 0L, 400) } returns emptyList()
        coEvery { remote.pullExpiredTombstones(any(), any(), any()) } returns emptyList()

        val coordinator = SyncCoordinator(
            remote = remote,
            governor = governor(clock),
            clock = clock,
            workspaceDao = workspaceDao(),
            outboxDao = mockk<OutboxDao>(relaxed = true),
            expenseDao = mockk<ExpenseDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            stateHolder = SyncStateHolder(),
        )

        coordinator.requestSync()

        coVerify(atLeast = 1) { remote.pullExpenses("wid", 0L, 400) }
        coVerify { remote.writeMeta("wid", any()) }
    }

    @Test
    fun `a steady state sync issues one read page then stops`() = runTest {
        val clock = clock()
        val remote = mockk<RemoteLedger>(relaxed = true)
        coEvery { remote.readMeta("wid") } returns SyncMetaDto(10L, 0L, 0L, 0L, null)
        coEvery { remote.pullExpenses("wid", 10L, 400) } returns emptyList()
        coEvery { remote.pullCategories("wid", 10L, 400) } returns emptyList()
        coEvery { remote.pullExpiredTombstones(any(), any(), any()) } returns emptyList()

        val coordinator = SyncCoordinator(
            remote = remote,
            governor = governor(clock),
            clock = clock,
            workspaceDao = workspaceDao(),
            outboxDao = mockk<OutboxDao>(relaxed = true),
            expenseDao = mockk<ExpenseDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            stateHolder = SyncStateHolder(),
        )

        coordinator.requestSync()

        coVerify(exactly = 1) { remote.pullExpenses("wid", 10L, 400) }
    }

    @Test
    fun `resource exhausted locks the governor and stops further requests`() = runTest {
        val clock = clock()
        val remote = mockk<RemoteLedger>(relaxed = true)
        coEvery { remote.readMeta("wid") } throws SyncException(
            SyncErrorKind.RESOURCE_EXHAUSTED, "exhausted",
        )
        val gov = governor(clock)

        val coordinator = SyncCoordinator(
            remote = remote,
            governor = gov,
            clock = clock,
            workspaceDao = workspaceDao(),
            outboxDao = mockk<OutboxDao>(relaxed = true),
            expenseDao = mockk<ExpenseDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            stateHolder = SyncStateHolder(),
        )

        coordinator.requestSync()
        assertTrue(gov.isLocked())
        coordinator.requestSync()
        coVerify(exactly = 1) { remote.readMeta("wid") }
    }

    @Test
    fun `the workspace mutex serializes repeated sync runs`() = runTest {
        val clock = clock()
        val remote = mockk<RemoteLedger>(relaxed = true)
        coEvery { remote.readMeta("wid") } returns SyncMetaDto(0L, 0L, 0L, 0L, null)
        coEvery { remote.pullExpenses(any(), any(), any()) } returns emptyList()
        coEvery { remote.pullCategories(any(), any(), any()) } returns emptyList()
        coEvery { remote.pullExpiredTombstones(any(), any(), any()) } returns emptyList()

        val coordinator = SyncCoordinator(
            remote = remote,
            governor = governor(clock),
            clock = clock,
            workspaceDao = workspaceDao(),
            outboxDao = mockk<OutboxDao>(relaxed = true),
            expenseDao = mockk<ExpenseDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            stateHolder = SyncStateHolder(),
        )

        coordinator.requestSync()
        coordinator.requestSync()

        coVerify(atLeast = 2) { remote.readMeta("wid") }
    }
}
