package com.srinidhi.kharch.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared workspace row (decision D1). Members are authorisation, not partitioning, so the
 * interesting assertions are about there being exactly one ledger and about signing in twice
 * not duplicating it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkspaceDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var workspaceDao: WorkspaceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        workspaceDao = database.workspaceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an empty database has no workspace`() = runBlocking {
        assertNull(workspaceDao.current())
        assertEquals(0, workspaceDao.count())
    }

    @Test
    fun `the observed workspace is null until one is created`() = runBlocking {
        assertNull(workspaceDao.observeCurrent().first())

        workspaceDao.upsert(sampleWorkspace())

        assertEquals("ws-1", workspaceDao.observeCurrent().first()?.id)
    }

    @Test
    fun `a workspace round trips including its member uids`() = runBlocking {
        val workspace = sampleWorkspace(memberUids = listOf("uid-a", "uid-b"))
        workspaceDao.upsert(workspace)

        assertEquals(workspace, workspaceDao.current())
        assertEquals(workspace, workspaceDao.findById("ws-1"))
    }

    @Test
    fun `signing in again replaces the row rather than duplicating the ledger`() = runBlocking {
        workspaceDao.upsert(sampleWorkspace(memberUids = listOf("uid-a")))
        workspaceDao.upsert(sampleWorkspace(memberUids = listOf("uid-a", "uid-b")))

        assertEquals(1, workspaceDao.count())
        assertEquals(listOf("uid-a", "uid-b"), workspaceDao.current()?.memberUids)
    }

    @Test
    fun `an empty member list is stored rather than treated as absent`() = runBlocking {
        workspaceDao.upsert(sampleWorkspace(memberUids = emptyList()))

        assertEquals(emptyList<String>(), workspaceDao.current()?.memberUids)
    }

    @Test
    fun `the newest ledger wins when more than one exists`() = runBlocking {
        workspaceDao.upsert(sampleWorkspace(id = "ws-old", createdAt = 1_000L))
        workspaceDao.upsert(sampleWorkspace(id = "ws-new", createdAt = 2_000L))

        assertEquals("ws-new", workspaceDao.current()?.id)
        assertEquals(2, workspaceDao.count())
    }

    @Test
    fun `a workspace can be found by id even when it is not the newest`() = runBlocking {
        workspaceDao.upsert(sampleWorkspace(id = "ws-old", createdAt = 1_000L))
        workspaceDao.upsert(sampleWorkspace(id = "ws-new", createdAt = 2_000L))

        assertEquals("ws-old", workspaceDao.findById("ws-old")?.id)
    }

    @Test
    fun `a missing workspace reads back as null`() = runBlocking {
        assertNull(workspaceDao.findById("nope"))
    }

    @Test
    fun `clearing removes every workspace`() = runBlocking {
        workspaceDao.upsert(sampleWorkspace())
        workspaceDao.clear()

        assertEquals(0, workspaceDao.count())
        assertNull(workspaceDao.current())
    }

    private fun sampleWorkspace(
        id: String = "ws-1",
        memberUids: List<String> = listOf("uid-a"),
        createdAt: Long = 1_700_000_000_000L,
    ) = WorkspaceEntity(
        id = id,
        name = "Kharch",
        memberUids = memberUids,
        createdAt = createdAt,
        schemaVersion = 1,
    )
}
