package com.srinidhi.kharch.data.local

import com.srinidhi.kharch.core.model.ExpenseKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Column encoding. The enum-as-name choice is the one that matters: storing an ordinal would
 * let a reordered enum silently reinterpret every existing row.
 */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `an expense kind is stored by name`() {
        assertEquals("EXPENSE", converters.fromExpenseKind(ExpenseKind.EXPENSE))
        assertEquals("REFUND", converters.fromExpenseKind(ExpenseKind.REFUND))
    }

    @Test
    fun `an expense kind round trips`() {
        ExpenseKind.entries.forEach { kind ->
            assertEquals(kind, converters.toExpenseKind(converters.fromExpenseKind(kind)))
        }
    }

    @Test
    fun `a sync state round trips`() {
        SyncState.entries.forEach { state ->
            assertEquals(state, converters.toSyncState(converters.fromSyncState(state)))
        }
    }

    @Test
    fun `an unrecognised expense kind fails loudly rather than guessing`() {
        assertThrows(IllegalStateException::class.java) { converters.toExpenseKind("CHARGE") }
    }

    @Test
    fun `an unrecognised sync state fails loudly rather than guessing`() {
        assertThrows(IllegalStateException::class.java) { converters.toSyncState("QUEUED") }
    }

    @Test
    fun `a member uid list round trips as json`() {
        val uids = listOf("uid-one", "uid-two")

        assertEquals(uids, converters.toStringList(converters.fromStringList(uids)))
    }

    @Test
    fun `an empty member uid list round trips`() {
        assertEquals(emptyList<String>(), converters.toStringList(converters.fromStringList(emptyList())))
    }

    @Test
    fun `a member uid list round trips in its original order`() {
        val uids = listOf("c", "a", "b")

        assertEquals(uids, converters.toStringList(converters.fromStringList(uids)))
    }

    @Test
    fun `an instant round trips through epoch millis`() {
        val instant = Instant.ofEpochMilli(1_700_000_000_123L)

        assertEquals(instant, converters.toInstant(converters.fromInstant(instant)))
    }

    @Test
    fun `a null instant converts to null in both directions`() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }
}
