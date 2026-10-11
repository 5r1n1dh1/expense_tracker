package com.srinidhi.kharch.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The clock itself, because everything downstream trusts it. Plan §13.2 requires `YearMonth`
 * maths to run against a fake clock rather than the wall clock — and that the system clock is
 * only ever read in one place.
 */
class ClockTest {
    @Test
    fun `a fake clock reports the instant it was given`() {
        val instant = Instant.parse("2026-08-12T09:30:00Z")
        val clock = FakeClock(instant)

        assertEquals(instant.toEpochMilli(), clock.nowMillis())
    }

    @Test
    fun `a fake clock resolves today in its own zone not in utc`() {
        val clock = FakeClock(Instant.parse("2026-08-12T18:45:00Z"))

        assertEquals(LocalDate.of(2026, 8, 13), clock.today())
    }

    @Test
    fun `setting a fake clock to a date lands on local midnight`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 12))

        assertEquals(
            LocalDate.of(2026, 8, 12).atStartOfDay(FakeClock.KOLKATA).toInstant().toEpochMilli(),
            clock.nowMillis(),
        )
    }

    @Test
    fun `advancing a fake clock moves today by whole days`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 12))

        clock.advanceDays(1)

        assertEquals(LocalDate.of(2026, 8, 13), clock.today())
    }

    @Test
    fun `advancing across a leap day lands on the twenty ninth of february`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2028, 2, 28))

        clock.advanceDays(1)

        assertEquals(LocalDate.of(2028, 2, 29), clock.today())
    }

    @Test
    fun `advancing a fake clock by milliseconds keeps the instant exact`() {
        val clock = FakeClock()
        val before = clock.nowMillis()

        clock.advanceMillis(250L)

        assertEquals(before + 250L, clock.nowMillis())
    }

    @Test
    fun `the epoch day of an instant is its utc day`() {
        val millis = Instant.parse("2026-08-12T00:00:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 12).toEpochDay(), epochDayOf(millis))
    }

    @Test
    fun `the local date of an instant respects the requested zone`() {
        val millis = Instant.parse("2026-08-12T18:45:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 13), localDateOf(millis, FakeClock.KOLKATA))
        assertEquals(LocalDate.of(2026, 8, 12), localDateOf(millis, ZoneId.of("UTC")))
    }

    @Test
    fun `a millisecond epoch day is the floor of the millisecond count`() {
        assertEquals(0L, epochDayOf(0L))
        assertEquals(0L, epochDayOf(MILLIS_PER_DAY - 1L))
        assertEquals(1L, epochDayOf(MILLIS_PER_DAY))
        assertEquals(-1L, epochDayOf(-1L))
    }
}
