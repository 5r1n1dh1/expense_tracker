package com.srinidhi.kharch.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A [Clock] the test drives by hand.
 *
 * The zone is pinned to Asia/Kolkata rather than the machine's: IST has no daylight saving, so
 * every instant maps to exactly one local date and a test asserting "today" cannot become flaky
 * because CI runs in a different region.
 */
class FakeClock(
    instant: Instant = DEFAULT_INSTANT,
    private val zone: ZoneId = KOLKATA,
) : Clock {
    var instant: Instant = instant
        private set

    override fun nowMillis(): Long = instant.toEpochMilli()

    override fun today(): LocalDate = localDateOf(nowMillis(), zone)

    /** Jumps to local midnight of [date], which is what "today" means to the app. */
    fun setTo(date: LocalDate) {
        instant = date.atStartOfDay(zone).toInstant()
    }

    /** Jumps to [hour]:00 local time on [date] — for exercising created/updated timestamps. */
    fun setTo(date: LocalDate, hour: Int) {
        instant = date.atTime(hour, 0).atZone(zone).toInstant()
    }

    fun advanceDays(days: Long) {
        instant = instant.plus(days, ChronoUnit.DAYS)
    }

    fun advanceMillis(millis: Long) {
        instant = instant.plusMillis(millis)
    }

    companion object {
        /** 12 Aug 2026 at 15:00 IST. Arbitrary but fixed. */
        val DEFAULT_INSTANT: Instant = Instant.parse("2026-08-12T09:30:00Z")

        val KOLKATA: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
